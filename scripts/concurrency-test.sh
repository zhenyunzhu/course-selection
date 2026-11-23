#!/bin/bash
# Day8：并发正确性验证（不是压测）
# 20 个不同学生，并发抢 1 个名额的教学班，验证「不超卖」
GW=http://localhost:8088
MYSQL="docker exec cs-mysql mysql -uroot -p123456 -N -e"
CLASS=8
N=20

echo "=== 准备：重置教学班 $CLASS 容量，清理旧数据 ==="
$MYSQL "update cs_course.t_class set remain=capacity where id=$CLASS;" 2>/dev/null
$MYSQL "delete from cs_enroll.t_enrollment where class_id=$CLASS;" 2>/dev/null
$MYSQL "delete from cs_course.t_capacity_reserve where class_id=$CLASS;" 2>/dev/null
$MYSQL "select id,capacity,remain from cs_course.t_class where id=$CLASS;" 2>/dev/null

echo "=== 并发发起 $N 个选课请求 ==="
rm -f /tmp/r_*.json
for i in $(seq 1 $N); do
  sid=$((80000 + i))
  (
    TOKEN=$(curl -s -X POST $GW/api/auth/login -H 'Content-Type: application/json' \
      -d "{\"studentId\":$sid,\"name\":\"c$i\"}" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    curl -s -X POST $GW/api/enrollments \
      -H "Authorization: Bearer $TOKEN" \
      -H "X-Idempotent-Token: $(cat /proc/sys/kernel/random/uuid)" \
      -H 'Content-Type: application/json' \
      -d "{\"classId\":$CLASS}" > /tmp/r_$i.json
  ) &
done
wait

echo "=== 结果统计 ==="
OK=$(grep -l '"code":0' /tmp/r_*.json 2>/dev/null | wc -l)
FULL=$(grep -l '2003' /tmp/r_*.json 2>/dev/null | wc -l)
echo "成功: $OK   容量不足: $FULL   总数: $N"

echo "=== 数据库核对 ==="
echo -n "最终 remain: "; $MYSQL "select remain from cs_course.t_class where id=$CLASS;" 2>/dev/null
echo -n "选课单数量: "; $MYSQL "select count(*) from cs_enroll.t_enrollment where class_id=$CLASS and status=1;" 2>/dev/null
echo -n "预占记录(已确认): "; $MYSQL "select count(*) from cs_course.t_capacity_reserve where class_id=$CLASS and status=1;" 2>/dev/null
