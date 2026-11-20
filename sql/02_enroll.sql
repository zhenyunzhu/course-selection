-- ============ 选课库（enroll-service 独占） ============
CREATE DATABASE IF NOT EXISTS cs_enroll DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE cs_enroll;

DROP TABLE IF EXISTS t_enrollment;
DROP TABLE IF EXISTS t_outbox_message;

CREATE TABLE t_enrollment (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  enroll_no         VARCHAR(32) NOT NULL COMMENT '选课单号',
  student_id        BIGINT      NOT NULL,
  class_id          BIGINT      NOT NULL,
  course_id         BIGINT      NOT NULL,
  course_name       VARCHAR(64) NOT NULL COMMENT '快照',
  credit            INT         NOT NULL COMMENT '快照',
  reserve_id        VARCHAR(64) NOT NULL COMMENT 'course 侧预占 id，退课时用来 Cancel',
  schedule_snapshot VARCHAR(128) NULL COMMENT '快照，如 1:3-4|3:3-4，用于冲突检测',
  status            TINYINT     NOT NULL DEFAULT 1 COMMENT '1 已选 0 已退',
  create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_enroll_no (enroll_no),
  -- 硬约束：同一个人同一门课只能有一条有效记录（应用层也查，DB 兜底并发）
  UNIQUE KEY uk_student_course (student_id, course_id, status),
  KEY idx_student (student_id, status)
) COMMENT '选课单';

CREATE TABLE t_outbox_message (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_type        VARCHAR(32)  NOT NULL COMMENT 'ENROLL_SUCCESS / ENROLL_CANCEL',
  biz_key         VARCHAR(64)  NOT NULL COMMENT '幂等键：enrollNo',
  payload         VARCHAR(512) NULL,
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待投递 1 已投递 2 死信',
  retry_count     INT          NOT NULL DEFAULT 0,
  next_retry_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_type_key (biz_type, biz_key),
  KEY idx_status_retry (status, next_retry_time)
) COMMENT '本地消息表';
