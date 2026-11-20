# 选课系统 · 微服务（course-selection）

一个**能跑、能讲、能被追问**的微服务项目。不是 CRUD 脚手架，整套代码围绕一个问题展开：

> **容量在 A 服务、选课单在 B 服务，跨服务怎么保证「选了就真的选上了」？**

答案不是「上 Seata」，而是 **预占 → 确认 → 取消 + 定时对账**，并且把每一步的失败路径都想清楚。

---

## 一句话架构

```
浏览器 ──▶ cs-gateway(8088)  ──路由 + token 校验 + 身份透传
              │
              ├─▶ cs-enroll(8082)  选课单 / 规则校验 / Outbox
              │        │ OpenFeign（超时 1s/3s）
              │        ▼
              └─▶ cs-course(8081)  课程 / 教学班 / 容量预占
                       │
                  MySQL(cs_course) + Redis
```

三个服务各自独立部署、各自一个库：`cs_course` / `cs_enroll`。

---

## 30 秒跑起来

前提：JDK 17、Maven、MySQL 8、Redis（Windows 上 Memurai 也行）。

```bash
# 1. 建库建表（导入两个 SQL）
mysql -uroot -p --default-character-set=utf8mb4 -e "source sql/01_course.sql"
mysql -uroot -p --default-character-set=utf8mb4 -e "source sql/02_enroll.sql"

# 2. 打包
mvn -DskipTests package

# 3. 起三个服务（三个窗口，或直接运行 start.ps1）
java -jar cs-course/target/cs-course-1.0.0.jar
java -jar cs-enroll/target/cs-enroll-1.0.0.jar
java -jar cs-gateway/target/cs-gateway-1.0.0.jar
```

验证：

```bash
curl -X POST http://localhost:8088/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"studentId\":1001,\"name\":\"张三\"}"
# → {"code":0,"data":{"token":"...","studentId":1001}}
```

详细步骤、测试顺序、常见报错见 [docs/05-启动与调试.md](docs/05-启动与调试.md)。

---

## 文档（按这个顺序读）

| # | 文档 | 读完你会知道 |
|---|---|---|
| 0 | [docs/00-怎么读这个项目](docs/00-怎么读这个项目.md) | 三条阅读路线，按问题找代码，不用从头读 |
| 1 | [docs/01-架构与拆分](docs/01-架构与拆分.md) | 为什么是这 3 个服务、边界怎么划、一致性方案怎么选 |
| 2 | [docs/02-核心链路时序](docs/02-核心链路时序.md) | 选课/退课/超时释放的每一步，以及每步失败会怎样 |
| 3 | [docs/03-接口清单](docs/03-接口清单.md) | 6 个对外接口 + 5 个内部接口，谁调、改哪张表 |
| 4 | [docs/04-面试问答-20题](docs/04-面试问答-20题.md) | 面试官会从哪几个角度挖，怎么答 |
| 5 | [docs/05-启动与调试](docs/05-启动与调试.md) | 命令、账号、冒烟顺序、常见错误 |

---

## 目录结构

```
course-selection/
├── cs-common/          统一响应、错误码、全局异常
├── cs-course/          课程 + 容量预占（TCC 的 T 和 C）
│   ├── entity/         Course / CourseClass / CapacityReserve / CourseSchedule
│   ├── mapper/         条件更新扣减、CAS 改状态、超时扫描
│   ├── service/        CapacityService（Try/Confirm/Cancel/Release/对账）
│   └── controller/     CourseController（对外）、InternalCapacityController（对内）
├── cs-enroll/          选课编排 + 规则 + Outbox
│   ├── service/        EnrollService（编排）、OutboxRelay（中继）、AuthService
│   ├── feign/          CourseClient
│   └── web/            AuthInterceptor + WebConfig
├── cs-gateway/         路由 + 全局鉴权过滤器
├── sql/                两个库的建表与种子数据
└── docs/               六份文档
```

---

## 这个项目里刻意做的取舍（面试就讲这些）

| 决定 | 为什么 | 代价 |
|---|---|---|
| 不引入 Seata，手写预占/确认/取消 | 想清楚补偿逻辑本身，且不锁全局行 | 要自己处理悬挂、空回滚 |
| 容量用条件更新扣减 | 判断和修改在一条 SQL 里，无窗口 | 热点行会成为瓶颈（可上 Redis 预扣） |
| 退课走「本地事务 + Outbox 补偿」 | 先保证用户操作成功，远程失败可重试 | 有一小段时间容量未回补 |
| 规则校验放在 enroll 侧 | 规则要读选课表，放 course 会双向依赖 | enroll 要多存几个快照字段 |
| 本地用 static discovery 代替 Nacos | 不装注册中心也能跑 | 换机器要改配置（生产用 Nacos） |
