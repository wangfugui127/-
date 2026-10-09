# MiniMall 实施与验收报告

日期：2026-10-09。地点：用户 Windows 11 本机；项目路径 `F:\Project Practice\限量商品商城与订单系统`。

基础范围已完成：Java/Vue前后端、12张业务表、34个业务接口、MySQL/Redis/RocketMQ依赖、后台任务、Windows本地启动和完整Docker运行。源码与详细开发指南位于项目目录；Java/Maven/Node/浏览器工具及镜像归档放在F:\app。

## 已执行结果

| 验收项 | 实际结果 | 证据 |
|---|---|---|
| 本机Java代码运行 | Java21.0.12.1输出HelloJava环境检查结果 | scripts/run-java-example.ps1；examples/HelloJava.java |
| 后端Maven verify | 6项单测 + 21项真实MySQL/Redis集成测试；0失败、0错误、0跳过 | backend-test-results.json；backend/target测试报告 |
| 前端类型检查/lint | 通过，最终lint无错误无警告 | scripts/test-frontend.ps1 |
| 前端单元测试 | 7项通过 | frontend/tests/commerce.test.ts、reliable.test.ts |
| 前端生产构建 | vue-tsc + vite build成功 | frontend/dist；Docker构建日志 |
| npm audit | 0 vulnerabilities | 本机npm audit实际输出，2026-10-09 |
| 本地订单API闭环 | 下单、重复请求、重复支付、MQ支付、发货、收货、取消全部通过 | local-smoke.json |
| 本地浏览器 | 5项通过（18.1秒） | frontend/e2e/commerce.spec.ts；此前本地执行日志 |
| 后端源码Docker构建 | Linux Maven+Java21编译并打包成功，Temurin21非root运行镜像生成 | deploy/Dockerfile.backend；images.json |
| 前端源码Docker构建 | Linux Node24.21.0 typecheck/build成功，Nginx镜像生成 | deploy/Dockerfile.frontend；images.json |
| 整套Docker首次运行 | 6个持续服务healthy、2个初始化服务退出0；health=UP | Compose运行状态，后续snapshot见runtime-status.json |
| Docker订单API闭环 | 34个业务接口被OpenAPI导出；重复下单/支付与履约通过 | docker-smoke.json、openapi.json、api-index.md |
| Docker浏览器 | 5项通过（16.9秒） | 前端Playwright最终执行日志 |
| 本地并发库存 | 40请求/12工作线程争抢10件，恰好10成功、30库存不足；库存守恒 | local-concurrency.json |
| Docker并发库存 | 同样40请求/12工作线程，恰好10成功、30库存不足；取消/回收守恒 | docker-concurrency.json |
| 本地Broker故障 | Broker停止时订单/支付持久保存，恢复后Outbox重试支付成功 | local-failure-drill.json |
| DockerBroker故障 | 同上，恢复成功；数据库扫描超时关闭成功 | docker-failure-drill.json |
| Docker整套重启 | 全套stop再up，已完成订单及明细保持不变 | docker-restart-drill.json |
| 镜像导出 | 前后端镜像保存为F:\app\images\minimall-0.1.0.tar，207316992字节 | image-export.json |

初始化服务mq-init与mq-volume-init本来就是一次性任务，Exited(0)表示成功；不能把它们的退出视为应用故障。

## 后端测试证明了什么

事务测试注入Outbox插入失败，验证订单、明细、库存一起回滚；多商品库存不足不留部分订单。同键12线程并发只产生一单，30个普通购买请求争抢10库存不会超卖；20用户竞争5个活动库存只成功5单。

支付覆盖重复发起、重复回调、错误金额/签名、晚到成功退款，以及支付与取消并发竞争。履约重复发货/确认不会再次扣库存。活动覆盖时间窗口、普通/活动库存隔离、取消不恢复资格和回收前必须没有待付款订单。

消息消费注入失败，验证业务更新和消费去重标记一起回滚。其他用例验证购物车归属及事务清理、库存调整幂等/非负边界、HTTP401/403/404/405/400、ID字符串、UTC转换、正/空值缓存及失效。

Testcontainers创建隔离容器，测试删除/清理动作仅作用于临时测试数据库和Redis。没有清理用户已有的演示数据库或Notebook项目。

## 浏览器实际覆盖

1. 新注册用户浏览商品、下单、模拟支付，经实际RocketMQ变为待发货；管理员API发货；用户页面确认收货。
2. 管理员从页面新建商品、调整库存，列表显示正确余额。
3. 1440px桌面和390px手机商品页截图，手机没有横向溢出。
4. 购物车加商品、修改数量、选中结算，结算后清理购物车，取消订单。
5. 限量活动从页面成功创建订单，取消后重新打开活动，按钮显示资格已使用且不可再抢。

两个模式分别运行同一组5项用例；不是拿本地测试结果替代容器测试。截图见screenshots/catalog-desktop.png和catalog-mobile.png。

## 并发指标及含义

| 环境 | 请求/工作线程 | 成功/库存不足 | 耗时 | P50 | P95 | 本批次吞吐 |
|---|---|---|---|---|---|---|
| Windows Java本地 + 容器依赖 | 40 / 12 | 10 / 30 | 1696ms | 426ms | 635ms | 23.59请求/秒 |
| 全部Docker + Nginx代理 | 40 / 12 | 10 / 30 | 790ms | 196ms | 368ms | 50.61请求/秒 |

这不是稳定性能对照实验：运行时刻、预热、JVM与网络路径不同，不能据此得出Docker一定更快。统计仅包含这40笔抢购接口请求，注册、登录和后续取消/回收未计入。样本少、没有持续压测、没有生产资源限制，所以不应写成生产容量或“万级QPS”。

正确性结果更有意义：10件库存归零，没有多于10个成功订单；逐个取消后活动库存10；停用并关闭活动后普通库存恢复到最初20。失败脚本会抛错，不把HTTP错误计成成功。

## 故障和恢复

Broker停止后可以完成订单数据库事务与创建支付意图，订单仍处于待付款。恢复Broker后Outbox自动重发，模拟支付成功。另一个新建测试订单被专门缩短期限，由30秒数据库扫描关闭，证明不依赖长延时消息才能释放库存。

整套Docker停止和启动使用同一命名卷。重启后重新登录管理员，核对同一已完成订单的订单号、状态和全部明细不变。这个验证没有删除卷或直接重建数据库。

## 本次排障修复

- 修复Windows与数据库时间转换不一致：连接会话、JDBC连接时区与查询统一UTC，追加回归用例。
- 修正商品/购物车与商品/活动的锁顺序，避免出现相反持锁路径。
- 后台库存调整网络未知结果保留原始body和幂等键，当前调整被确认前锁定输入；防止用户重试又执行一次。
- 回调模拟数据使用稳定创建时间，避免重试时同ID不同payloadHash。
- 修复Windows中文构建目录的BuildKit头错误，使用英文junction。
- 为RocketMQ专用卷初始化UID3000权限，区分本地和容器advertise地址。
- PowerShell脚本使用UTF-8 BOM与UseBasicParsing，实际通过Windows PowerShell5.1启动验收。
- 保持TLS验证，官方仓库网络错误重试后成功；没有用关闭证书校验换取构建通过。

## 仍需准确区分的边界

支付/退款为模拟，物流为录入物流号；不含真实支付、售后、优惠券、多SKU与外部通知。Vue已具备主要用户与管理流程，抢购页面在同页保留未知结果尝试，但跨刷新自动恢复提交键的UI体验尚未补齐，数据库唯一资格仍防止重复订单。

缓存是最终一致，下单以数据库再次校验为准。FAILED消息20次后的普通人工重放需结合错误原因与审计；退款修复任务单独覆盖未完成退款。当前库存审计输出日志，没有接入监控告警平台。

Element Plus整体引入使前端主JS约1.09MB，gzip约363KB，构建存在chunk大小提示；类型检查和构建仍通过。Flyway日志有MySQL8.4版本支持提示，实际迁移和集成测试通过；测试关闭Redis临时容器时也会有客户端重连警告，测试统计为0错误。

VS Code的JDK、Maven、终端、插件、任务和Java envFile已配置，并通过命令行执行脚本和Java；没有把“配置准备完成”宣称为F5 UI交互已测试。GitHub Actions配置已保存，但未在远程运行，所以不宣称云端CI已经通过。

本项目具备可展示的开发实习工程证据；不等于生产级商城或保证获得大厂面试。投递时应能独立讲清这些实际实现、SQL及测试，不添加未经验证的规模指标。
