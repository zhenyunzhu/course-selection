package com.cs.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.common.BizException;
import com.cs.common.ResultCode;
import com.cs.course.dto.ClassDetailVO;
import com.cs.course.dto.ReserveRequest;
import com.cs.course.dto.ReserveResult;
import com.cs.course.entity.CapacityReserve;
import com.cs.course.entity.Course;
import com.cs.course.entity.CourseClass;
import com.cs.course.entity.CourseSchedule;
import com.cs.course.mapper.CapacityReserveMapper;
import com.cs.course.mapper.CourseClassMapper;
import com.cs.course.mapper.CourseMapper;
import com.cs.course.mapper.CourseScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 容量预占：TCC 的 Try / Confirm / Cancel 全部落在这一层。
 *
 * 三个方法都必须是「幂等 + 可重试」的，因为上游会超时重试：
 *  - Try    ：以 reserveId 为主键，重复请求直接返回已有状态
 *  - Confirm：CAS 0→1，已经是 1 就返回成功
 *  - Cancel ：CAS 0→2 并回补容量；记录不存在时落「墓碑」，防止 Try 晚到后再扣
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapacityService {

    public static final int ST_RESERVED = 0;
    public static final int ST_CONFIRMED = 1;
    public static final int ST_CANCELLED = 2;
    public static final int ST_EXPIRED = 3;

    private final CourseClassMapper classMapper;
    private final CourseMapper courseMapper;
    private final CourseScheduleMapper scheduleMapper;
    private final CapacityReserveMapper reserveMapper;

    @Value("${cs.reserve.ttl-seconds:120}")
    private int defaultTtlSeconds;

    // ===================== Try =====================
    @Transactional(rollbackFor = Exception.class)
    public ReserveResult reserve(ReserveRequest req) {
        // 幂等闸门：同一个 reserveId 重放，直接返回已有状态，不再扣容量
        CapacityReserve exists = reserveMapper.selectById(req.getReserveId());
        if (exists != null) {
            log.info("[预占] 幂等命中 reserveId={} status={}", exists.getReserveId(), exists.getStatus());
            return new ReserveResult(exists.getReserveId(), exists.getStatus(), null);
        }

        CourseClass clazz = classMapper.selectById(req.getClassId());
        if (clazz == null || clazz.getStatus() == null || clazz.getStatus() == 0) {
            throw new BizException(ResultCode.CLASS_NOT_FOUND);
        }

        // 条件更新：判断和扣减在同一条 SQL 里，没有窗口
        int rows = classMapper.deduct(req.getClassId());
        if (rows == 0) {
            throw new BizException(ResultCode.CAPACITY_NOT_ENOUGH);
        }

        int ttl = req.getTtlSeconds() != null ? req.getTtlSeconds() : defaultTtlSeconds;
        CapacityReserve r = new CapacityReserve();
        r.setReserveId(req.getReserveId());
        r.setClassId(req.getClassId());
        r.setStudentId(req.getStudentId());
        r.setStatus(ST_RESERVED);
        r.setExpireTime(LocalDateTime.now().plusSeconds(ttl));
        try {
            reserveMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 并发重放：另一个线程刚插入同 id。抛出回滚，让本次扣减一起撤销
            throw new BizException(ResultCode.INVALID_PARAM, "重复的预占请求，已回滚");
        }
        log.info("[预占] 成功 reserveId={} classId={} remain={}", r.getReserveId(), r.getClassId(),
                clazz.getRemain() - 1);
        return new ReserveResult(r.getReserveId(), ST_RESERVED, clazz.getRemain() - 1);
    }

    // ===================== Confirm =====================
    @Transactional(rollbackFor = Exception.class)
    public void confirm(String reserveId) {
        int rows = reserveMapper.casStatus(reserveId, ST_RESERVED, ST_CONFIRMED);
        if (rows == 1) {
            log.info("[预占] 确认 reserveId={}", reserveId);
            return;
        }
        CapacityReserve r = reserveMapper.selectById(reserveId);
        if (r == null) {
            // 空回滚场景：上游 Cancel 没到、这里 Confirm 也没记录 —— 直接报错让上游补偿
            throw new BizException(ResultCode.RESERVE_NOT_FOUND, "预占不存在，无法确认");
        }
        if (r.getStatus() == ST_CONFIRMED) {
            return; // 重复确认，幂等
        }
        throw new BizException(ResultCode.RESERVE_STATE_ILLEGAL,
                "预占已取消或已释放，无法确认");
    }

    // ===================== Cancel =====================
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String reserveId) {
        CapacityReserve r = reserveMapper.selectById(reserveId);
        if (r == null) {
            // 空回滚：Try 还没到就收到 Cancel。落一条墓碑（status=2），
            // 之后 Try 再来时会读到它并直接返回「已取消」，不会真的扣容量 —— 这就是防悬挂。
            CapacityReserve tomb = new CapacityReserve();
            tomb.setReserveId(reserveId);
            tomb.setClassId(-1L);
            tomb.setStudentId(-1L);
            tomb.setStatus(ST_CANCELLED);
            tomb.setExpireTime(LocalDateTime.now());
            try {
                reserveMapper.insert(tomb);
            } catch (DuplicateKeyException ignore) {
                // 并发下已被别的线程落了墓碑，忽略
            }
            log.info("[预占] 空回滚，落墓碑 reserveId={}", reserveId);
            return;
        }

        int rows = reserveMapper.casStatus(reserveId, ST_RESERVED, ST_CANCELLED);
        if (rows == 1) {
            classMapper.restore(r.getClassId());
            log.info("[预占] 取消并回补 capacity reserveId={} classId={}", reserveId, r.getClassId());
            return;
        }
        if (r.getStatus() == ST_CANCELLED || r.getStatus() == ST_EXPIRED) {
            return; // 重复取消，幂等
        }
        throw new BizException(ResultCode.RESERVE_STATE_ILLEGAL,
                "预占已确认，不能直接取消，请走退课流程");
    }

    // ===================== 退课释放 =====================
    /**
     * 退课：把「已确认」的预占释放掉并回补容量。
     *
     * 注意它和 cancel 的区别：
     *  - cancel 是 TCC 的补偿动作，只处理「预占中」的
     *  - release 是业务动作，处理「已确认」的
     * 两者分开，状态机才不会被补偿逻辑污染。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(String reserveId) {
        CapacityReserve r = reserveMapper.selectById(reserveId);
        if (r == null) {
            return; // 没记录 = 已经释放过，幂等
        }
        if (r.getStatus() == ST_CANCELLED || r.getStatus() == ST_EXPIRED) {
            return; // 已释放，幂等
        }
        int rows = r.getStatus() == ST_CONFIRMED
                ? reserveMapper.casStatus(reserveId, ST_CONFIRMED, ST_CANCELLED)
                : reserveMapper.casStatus(reserveId, ST_RESERVED, ST_CANCELLED);
        if (rows == 1) {
            classMapper.restore(r.getClassId());
            log.info("[退课] 释放容量 reserveId={} classId={}", reserveId, r.getClassId());
        }
    }

    // ===================== 对账：释放超时未确认的预占 =====================
    @Transactional(rollbackFor = Exception.class)
    public int releaseExpired(int limit) {
        List<String> ids = reserveMapper.selectExpired(limit);
        int released = 0;
        for (String id : ids) {
            CapacityReserve r = reserveMapper.selectById(id);
            if (r == null) {
                continue;
            }
            if (reserveMapper.casStatus(id, ST_RESERVED, ST_EXPIRED) == 1) {
                classMapper.restore(r.getClassId());
                released++;
            }
        }
        if (released > 0) {
            log.info("[对账] 释放超时预占 {} 条", released);
        }
        return released;
    }

    /** 本地兜底：每 30 秒扫一次，防止上游对账任务挂了导致容量永久泄漏 */
    @Scheduled(fixedDelay = 30_000)
    public void scheduledRelease() {
        try {
            releaseExpired(200);
        } catch (Exception e) {
            log.warn("[对账] 定时释放失败: {}", e.getMessage());
        }
    }

    // ===================== 查询 =====================
    public ClassDetailVO classDetail(Long classId) {
        CourseClass clazz = classMapper.selectById(classId);
        if (clazz == null) {
            throw new BizException(ResultCode.CLASS_NOT_FOUND);
        }
        Course course = courseMapper.selectById(clazz.getCourseId());
        if (course == null) {
            throw new BizException(ResultCode.COURSE_NOT_FOUND);
        }
        List<CourseSchedule> list = scheduleMapper.selectList(
                new LambdaQueryWrapper<CourseSchedule>().eq(CourseSchedule::getClassId, classId));
        ClassDetailVO vo = new ClassDetailVO();
        vo.setClassId(classId);
        vo.setCourseId(course.getId());
        vo.setCourseName(course.getName());
        vo.setCredit(course.getCredit());
        vo.setPrerequisiteId(course.getPrerequisiteId());
        vo.setTeacher(clazz.getTeacher());
        vo.setCapacity(clazz.getCapacity());
        vo.setRemain(clazz.getRemain());
        vo.setSchedules(list.stream()
                .map(s -> s.getWeekday() + ":" + s.getStartSection() + "-" + s.getEndSection())
                .toList());
        return vo;
    }

    public List<CourseClass> listClasses(Long courseId) {
        return classMapper.selectList(new LambdaQueryWrapper<CourseClass>()
                .eq(CourseClass::getCourseId, courseId)
                .eq(CourseClass::getStatus, 1));
    }

    public List<Course> listCourses() {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, 1));
    }

    public Course getCourse(Long id) {
        Course c = courseMapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.COURSE_NOT_FOUND);
        }
        return c;
    }
}
