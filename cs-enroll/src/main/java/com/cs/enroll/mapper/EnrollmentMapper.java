package com.cs.enroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.enroll.entity.Enrollment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EnrollmentMapper extends BaseMapper<Enrollment> {

    /** 本学期已选学分（用于学分上限校验） */
    @Select("SELECT COALESCE(SUM(credit),0) FROM t_enrollment WHERE student_id = #{studentId} AND status = 1")
    int sumCredit(@Param("studentId") Long studentId);
}
