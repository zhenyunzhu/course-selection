-- ============ 课程库（course-service 独占） ============
CREATE DATABASE IF NOT EXISTS cs_course DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE cs_course;

DROP TABLE IF EXISTS t_capacity_reserve;
DROP TABLE IF EXISTS t_class_schedule;
DROP TABLE IF EXISTS t_class;
DROP TABLE IF EXISTS t_course;

CREATE TABLE t_course (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  code            VARCHAR(32)  NOT NULL COMMENT '课程代码',
  name            VARCHAR(64)  NOT NULL,
  credit          INT          NOT NULL COMMENT '学分',
  prerequisite_id BIGINT       NULL COMMENT '先修课 id',
  status          TINYINT      NOT NULL DEFAULT 1,
  UNIQUE KEY uk_code (code)
) COMMENT '课程';

CREATE TABLE t_class (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT       NOT NULL,
  name      VARCHAR(64)  NOT NULL COMMENT '教学班名，如 01 班',
  teacher   VARCHAR(32)  NOT NULL,
  capacity  INT          NOT NULL COMMENT '总容量',
  remain    INT          NOT NULL COMMENT '剩余容量（条件更新扣减）',
  status    TINYINT      NOT NULL DEFAULT 1,
  KEY idx_course (course_id),
  CONSTRAINT ck_remain CHECK (remain >= 0)
) COMMENT '教学班（容量在这里）';

CREATE TABLE t_class_schedule (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  class_id      BIGINT NOT NULL,
  weekday       INT    NOT NULL COMMENT '1-7',
  start_section INT    NOT NULL,
  end_section   INT    NOT NULL,
  KEY idx_class (class_id)
) COMMENT '排课时间';

-- 预占记录：TCC 的事务凭证。主键由上游生成 → 上游重试天然幂等。
CREATE TABLE t_capacity_reserve (
  reserve_id  VARCHAR(64) PRIMARY KEY,
  class_id    BIGINT   NOT NULL,
  student_id  BIGINT   NOT NULL,
  status      TINYINT  NOT NULL COMMENT '0 预占中 1 已确认 2 已取消 3 超时释放',
  expire_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status_expire (status, expire_time)
) COMMENT '容量预占记录';

INSERT INTO t_course (id, code, name, credit, prerequisite_id, status) VALUES
 (1,'MATH101','高等数学',4,NULL,1),
 (2,'MATH102','线性代数',3,1,1),
 (3,'CS201','数据结构',4,2,1),
 (4,'CS202','算法设计',3,3,1),
 (5,'CS203','操作系统',4,3,1),
 (6,'CS204','计算机网络',3,NULL,1);

INSERT INTO t_class (id, course_id, name, teacher, capacity, remain, status) VALUES
 (1,1,'01 班','张老师',50,50,1),
 (2,2,'01 班','李老师',40,40,1),
 (3,3,'01 班','王老师',2,2,1),   -- 容量 2，演示抢满
 (4,3,'02 班','王老师',30,30,1),
 (5,4,'01 班','赵老师',30,30,1),
 (6,5,'01 班','陈老师',30,30,1),
 (7,6,'01 班','刘老师',40,40,1),
 (8,1,'03 班','测试老师',1,1,1),  -- 容量 1，演示「一个人占住后别人选不上」
 (9,6,'02 班','刘老师',5,5,1);   -- 排课与 2 号线代冲突，演示时间冲突

INSERT INTO t_class_schedule (class_id, weekday, start_section, end_section) VALUES
 (1,1,1,2),(1,3,1,2),
 (2,2,3,4),(2,4,3,4),
 (3,1,3,4),(3,3,3,4),
 (4,1,5,6),(4,3,5,6),
 (5,2,1,2),(5,5,1,2),
 (6,4,5,6),(6,5,5,6),
 (7,3,7,8),(7,5,7,8),
 (9,2,3,4),(9,4,3,4);   -- 与 2 号线代完全相同 → 时间冲突
