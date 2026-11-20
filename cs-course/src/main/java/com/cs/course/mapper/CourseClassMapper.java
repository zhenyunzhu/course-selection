package com.cs.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.course.entity.CourseClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 容量扣减用「条件更新」，一条 SQL 完成判断 + 修改，靠影响行数判断成败。
 * 绝对不能写成 select 再 update —— 那中间有窗口，并发下会超卖。
 */
@Mapper
public interface CourseClassMapper extends BaseMapper<CourseClass> {

    @Update("UPDATE t_class SET remain = remain - 1 " +
            "WHERE id = #{classId} AND remain > 0 AND status = 1")
    int deduct(@Param("classId") Long classId);

    @Update("UPDATE t_class SET remain = remain + 1 " +
            "WHERE id = #{classId} AND remain < capacity")
    int restore(@Param("classId") Long classId);
}
