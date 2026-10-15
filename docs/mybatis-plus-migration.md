# MyBatis-Plus 数据访问迁移

更新日期：2026-10-14。

## 当前调用链

前端请求 → Controller 绑定参数 → Service 执行业务规则和事务 → 对应表的 Mapper → MySQL → MapperRows 整理 Map → ApiResponse → JSON 响应。

生产代码不再使用 Db / JdbcTemplate，也不把 SQL 字符串传入 Service 的通用执行器。SQL 参数通过 QueryWrapper 或 Mapper 的 `#{参数名}` 绑定。

## 代码位置

- `backend/src/main/java/com/lsy/minimall/product/Product.java`、`ProductMapper.java`：商品实体与 Mapper。
- `backend/src/main/java/com/lsy/minimall/persistence/entity/`：其余 12 张业务表实体。
- `backend/src/main/java/com/lsy/minimall/persistence/mapper/`：用户、购物车、订单、明细、支付、活动、限购、库存账本、Outbox、消费记录、操作日志、下单锁 Mapper，以及数据库时间 ClockMapper。
- `backend/src/main/java/com/lsy/minimall/persistence/MapperRows.java`：响应转换和必需记录检查，不执行数据库操作。
- `backend/src/main/java/com/lsy/minimall/persistence/MybatisConfiguration.java`：MySQL 分页插件。

## 普通操作和特殊操作

普通插入：Mapper 创建表实体，调用继承的 `insert(entity)`，MyBatis-Plus 回填数据库生成的 `entity.id`。未指定的状态、库存、创建时间等继续使用表默认值。

普通查询：Mapper 使用 `selectMaps(new QueryWrapper<实体>().eq(...))`，再由 MapperRows 转换为原 API 的 Map 格式。商品、订单列表使用 `selectMapsPage`；活动联表分页使用 Mapper 的绑定参数动态 SQL。

特殊查询和写入：`FOR UPDATE`、`SKIP LOCKED`、条件扣库存、状态更新、购物车 upsert、库存审计和 Outbox 租约由 Mapper 方法封装。库存更新保留 signed 非负检查；Outbox 发布和重试保留 owner/status 条件；普通与限量下单继续共用用户下单锁。

没有配置 MyBatis 乐观锁插件，现有 `version=version+1` 与业务版本校验继续显式执行，避免插件改变既有状态机语义。

## 返回格式与事务

- MyBatis `call-setters-on-nulls: true`：返回 Map 保留数据库 NULL 字段。
- `return-instance-for-empty-row: true`：仅选出 NULL 列时仍返回带空字段的记录，保持原 JDBC 查询语义。
- `local-cache-scope: STATEMENT`：保持锁查询、数据库时钟和重复读取的实时执行语义。
- MapperRows：`stock_available → stockAvailable`，ID 转字符串，Timestamp / LocalDateTime 转 UTC 文本。
- Transactions 与 InventoryService 的事务要求继续有效。Mapper 使用同一个数据源和 Spring 管理的 SqlSession，异常使相关写入一并回滚。
- 商品详情仍保持一次 Mapper SQL 返回商品和数据库时间；Redis 正值/空值缓存、加载锁、代次与 owner 校验保持原有行为。

## 验证

2026-10-14 已执行 `mvn -B -ntp verify`：6 项单元测试、33 项 MySQL / Redis 集成测试，共 39 项通过，失败/错误/跳过均为 0。另已静态核对生产源码无 Db / JdbcTemplate 调用、业务 SQL 全部位于 Mapper、SQL 命名参数与方法参数一致，业务 JAR 不包含测试 JDBC 工具。

运行 `scripts/test-backend.ps1`（或在 backend 执行 `mvn -B -ntp verify`）。集成测试使用独立 MySQL / Redis Testcontainers，不操作演示环境数据。

原有集成用例覆盖库存并发、重复请求/消费、支付和取消竞争、事务回滚、缓存失效竞态及用户配额。迁移新增用例检查分类搜索与分页、管理员/用户可见性、活动联表分页、生成主键、注册登录、NULL/ID/时间格式和 Outbox 过期租约及旧 owner 拒绝发布。

另检查仅选择 NULL 列仍保留记录，以及同一事务内两次数据库时钟查询取得新时间，避免 MyBatis 本地缓存改变业务语义。

`backend/src/test/java/com/lsy/minimall/support/DatabaseFixture.java` 是测试专用 JDBC 工具，用于独立准备和检查数据库状态，不是业务数据访问路径。

此次迁移不修改 Flyway 已应用的建表脚本，不重建数据库，不改变 API 请求/响应协议。已运行的应用或已导出的旧镜像需另行重新打包和启动后才使用新源码。
