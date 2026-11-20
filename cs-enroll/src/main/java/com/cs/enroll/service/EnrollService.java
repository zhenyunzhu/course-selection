package com.cs.enroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.common.BizException;
import com.cs.common.Result;
import com.cs.common.ResultCode;
import com.cs.enroll.dto.ClassDetailVO;
import com.cs.enroll.dto.EnrollRequest;
import com.cs.enroll.dto.ReserveRequest;
import com.cs.enroll.entity.Enrollment;
import com.cs.enroll.entity.OutboxMessage;
import com.cs.enroll.feign.CourseClient;
import com.cs.enroll.mapper.EnrollmentMapper;
import com.cs.enroll.mapper.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 选课编排：规则校验 → 预占(Try) → 本地落单 → 确认(Confirm)。
 *
 * 为什么用 TransactionTemplate 而不是 @Transactional：
 * 远程调用不能包在本地事务里（事务会被网络耗时拖长，连接和行锁白占）。
 * 所以事务只包「本地落单 + Outbox 写入」这一小段，用编程式事务精确框住。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollService {

    private final EnrollmentMapper enrollmentMapper;
    private final OutboxMapper outboxMapper;
    private final CourseClient courseClient;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${cs.enroll.credit-limit:30}")
    private int creditLimit;

    // ===================== 选课 =====================
    public Map<String, Object> enroll(Long studentId, String idemToken, EnrollRequest req) {
        // ① 幂等闸门：同一个凭证只处理一次，挡住连点与网络重发
        String idemKey = "idem:enroll:" + idemToken;
        Boolean first = redis.opsForValue().setIfAbsent(idemKey, "1", Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException(ResultCode.DUPLICATE_ENROLL, "请勿重复提交（幂等拦截）");
        }

        // ② 取教学班信息（一次远程调用拿全，避免 N+1）
        ClassDetailVO detail = unwrap(courseClient.classDetail(req.getClassId()), "查询教学班");

        // ③ 规则校验：全部在本地做，查的是自己的库
        checkRules(studentId, detail);

        // ④ Try：预占容量
        String reserveId = UUID.randomUUID().toString().replace("-", "");
        ReserveRequest rr = new ReserveRequest();
        rr.setReserveId(reserveId);
        rr.setClassId(req.getClassId());
        rr.setStudentId(studentId);
        rr.setTtlSeconds(120);
        try {
            unwrap(courseClient.reserve(rr), "预占容量");
        } catch (BizException e) {
            throw e; // 容量不足这类业务失败，原样上抛
        } catch (Exception e) {
            log.error("[选课] 预占失败 classId={}", req.getClassId(), e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "课程服务不可用，请稍后重试");
        }

        // ⑤ 本地落单 + Outbox（同一事务：要么都给，要么都不给）
        String enrollNo = "E" + System.currentTimeMillis() + (1000 + (int) (Math.random() * 9000));
        String snapshot = detail.getSchedules() == null ? "" : String.join("|", detail.getSchedules());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Enrollment row = new Enrollment();
                row.setEnrollNo(enrollNo);
                row.setStudentId(studentId);
                row.setClassId(detail.getClassId());
                row.setCourseId(detail.getCourseId());
                row.setCourseName(detail.getCourseName());
                row.setCredit(detail.getCredit());
                row.setReserveId(reserveId);
                row.setScheduleSnapshot(snapshot);
                row.setStatus(1);
                enrollmentMapper.insert(row);

                OutboxMessage m = new OutboxMessage();
                m.setBizType("ENROLL_SUCCESS");
                m.setBizKey(enrollNo);
                m.setPayload("{\"enrollNo\":\"" + enrollNo + "\",\"courseName\":\""
                        + detail.getCourseName() + "\"}");
                m.setStatus(0);
                m.setRetryCount(0);
                m.setNextRetryTime(LocalDateTime.now());
                outboxMapper.insert(m);
            });
        } catch (Exception e) {
            log.error("[选课] 本地落单失败，回滚预占 reserveId={}", reserveId, e);
            safeCancel(reserveId);
            throw new BizException(ResultCode.SYSTEM_ERROR, "选课失败，已回滚");
        }

        // ⑥ Confirm：预占转正式占用
        try {
            unwrap(courseClient.confirm(reserveId), "确认预占");
        } catch (Exception e) {
            // 补偿：本地取消 + 远程 Cancel，两边都回到「没选上」
            log.error("[选课] 确认失败，开始补偿 reserveId={}", reserveId, e);
            transactionTemplate.executeWithoutResult(s -> {
                Enrollment row = new Enrollment();
                row.setStatus(0);
                enrollmentMapper.update(row, new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getEnrollNo, enrollNo));
            });
            safeCancel(reserveId);
            throw new BizException(ResultCode.SYSTEM_ERROR, "选课确认失败，已回滚");
        }

        log.info("[选课] 成功 studentId={} classId={} enrollNo={}", studentId, req.getClassId(), enrollNo);
        return Map.of(
                "enrollNo", enrollNo,
                "courseName", detail.getCourseName(),
                "credit", detail.getCredit(),
                "teacher", detail.getTeacher() == null ? "" : detail.getTeacher()
        );
    }

    // ===================== 退课 =====================
    public void drop(Long studentId, String enrollNo) {
        Enrollment e = enrollmentMapper.selectOne(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getEnrollNo, enrollNo)
                .eq(Enrollment::getStudentId, studentId)
                .eq(Enrollment::getStatus, 1));
        if (e == null) {
            throw new BizException(ResultCode.ENROLL_NOT_FOUND);
        }

        // 先改本地 + 落补偿消息（同一事务），再去释放远程容量。
        // 反过来做的话，远程成功、本地失败会导致「容量放了但选课单还在」。
        Long outboxId = transactionTemplate.execute(status -> {
            Enrollment upd = new Enrollment();
            upd.setStatus(0);
            enrollmentMapper.update(upd, new LambdaQueryWrapper<Enrollment>()
                    .eq(Enrollment::getEnrollNo, enrollNo));

            OutboxMessage m = new OutboxMessage();
            m.setBizType("ENROLL_CANCEL");
            m.setBizKey(enrollNo);
            m.setPayload(e.getReserveId());
            m.setStatus(0);
            m.setRetryCount(0);
            m.setNextRetryTime(LocalDateTime.now());
            outboxMapper.insert(m);
            return m.getId();
        });

        // 立即尝试一次；失败就留给中继任务重试，不影响用户操作结果
        try {
            unwrap(courseClient.release(e.getReserveId()), "释放容量");
            markOutboxDone(outboxId);
        } catch (Exception ex) {
            log.warn("[退课] 立即释放容量失败，交给 Outbox 重试 enrollNo={}", enrollNo, ex);
        }
        log.info("[退课] 成功 studentId={} enrollNo={}", studentId, enrollNo);
    }

    // ===================== 查询 =====================
    public List<Enrollment> myEnrollments(Long studentId) {
        return enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId)
                .eq(Enrollment::getStatus, 1)
                .orderByDesc(Enrollment::getId));
    }

    // ===================== 规则 =====================
    private void checkRules(Long studentId, ClassDetailVO d) {
        // 规则 1：不能重复选同一门课（换教学班也算重复）
        Long dup = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId)
                .eq(Enrollment::getCourseId, d.getCourseId())
                .eq(Enrollment::getStatus, 1));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.DUPLICATE_ENROLL);
        }

        // 规则 2：学分上限
        int used = enrollmentMapper.sumCredit(studentId);
        if (used + d.getCredit() > creditLimit) {
            throw new BizException(ResultCode.CREDIT_LIMIT_EXCEEDED,
                    "已选 " + used + " 学分，加上本课 " + d.getCredit() + " 超过上限 " + creditLimit);
        }

        // 规则 3：先修课
        if (d.getPrerequisiteId() != null) {
            Long ok = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                    .eq(Enrollment::getStudentId, studentId)
                    .eq(Enrollment::getCourseId, d.getPrerequisiteId())
                    .eq(Enrollment::getStatus, 1));
            if (ok == null || ok == 0) {
                throw new BizException(ResultCode.PREREQUISITE_NOT_MET,
                        "需先修完课程 id=" + d.getPrerequisiteId());
            }
        }

        // 规则 4：时间冲突（用本地快照比，不远程查）
        if (d.getSchedules() != null && !d.getSchedules().isEmpty()) {
            List<Enrollment> actives = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                    .eq(Enrollment::getStudentId, studentId)
                    .eq(Enrollment::getStatus, 1));
            for (Enrollment e : actives) {
                if (e.getScheduleSnapshot() == null || e.getScheduleSnapshot().isBlank()) {
                    continue;
                }
                for (String mine : e.getScheduleSnapshot().split("\\|")) {
                    for (String target : d.getSchedules()) {
                        if (conflict(mine, target)) {
                            throw new BizException(ResultCode.SCHEDULE_CONFLICT,
                                    "与《" + e.getCourseName() + "》时间冲突（" + target + "）");
                        }
                    }
                }
            }
        }
    }

    /** 格式 "weekday:start-end"，例如 "1:3-4"；同一天且区间相交即冲突 */
    private boolean conflict(String a, String b) {
        try {
            String[] pa = a.split("[:\\-]");
            String[] pb = b.split("[:\\-]");
            int wa = Integer.parseInt(pa[0]);
            int wb = Integer.parseInt(pb[0]);
            if (wa != wb) {
                return false;
            }
            int sa = Integer.parseInt(pa[1]);
            int ea = Integer.parseInt(pa[2]);
            int sb = Integer.parseInt(pb[1]);
            int eb = Integer.parseInt(pb[2]);
            return sa <= eb && sb <= ea;
        } catch (Exception e) {
            return false;
        }
    }

    // ===================== 工具 =====================
    private <T> T unwrap(Result<T> r, String action) {
        if (r == null) {
            throw new BizException(ResultCode.SYSTEM_ERROR, action + "失败：无响应");
        }
        if (r.getCode() != 0) {
            throw new BizException(r.getCode(), r.getMessage());
        }
        return r.getData();
    }

    private void safeCancel(String reserveId) {
        try {
            courseClient.cancel(reserveId);
        } catch (Exception e) {
            // 取消失败不能吞掉用户请求的异常；容量由对账任务兜底释放
            log.warn("[补偿] Cancel 失败，等待对账释放 reserveId={}", reserveId, e);
        }
    }

    private void markOutboxDone(Long outboxId) {
        OutboxMessage upd = new OutboxMessage();
        upd.setId(outboxId);
        upd.setStatus(1);
        outboxMapper.updateById(upd);
    }
}
