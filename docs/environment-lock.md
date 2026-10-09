# 本机环境与版本锁定

2026-10-09 实际检查。此处记录已安装和已验证的工具，不把网站最新版本当作本机版本。

| 工具 | 实际版本 / 位置 |
|---|---|
| JDK | Eclipse Temurin 21.0.12.1+1，`F:\app\java\jdk-21.0.12.1+1` |
| Maven | 3.9.11，`F:\app\maven\apache-maven-3.9.11` |
| Node | 24.21.0，`F:\app\node-minimall\node-v24.21.0-win-x64` |
| npm | 11.19.0，随上述Node安装 |
| npm缓存 | `F:\app\npm-cache-minimall` |
| Playwright浏览器 | `F:\app\playwright`，Chromium v1248；由当前Playwright锁定 |
| Docker Engine | 29.6.1，Docker Desktop Linux containers |
| Docker Compose | 5.3.0 |
| 下载归档 | `F:\app\downloads`，保留JDK、Maven、Node安装包 |
| 英文构建入口 | `F:\app\workspaces\minimall`，junction指向项目原目录 |

JDK下载经SHA256核对，Maven经SHA512核对，Node经SHA256核对。已有的Java25和Node路径未删除；User级JAVA_HOME、MAVEN_HOME及PATH已添加项目版本。Windows合并PATH时，机器级旧路径可能先命中；`.vscode/settings.json` 和 `scripts/env.ps1` 都显式把正确版本放到项目终端PATH最前。

官方来源：[Adoptium JDK](https://adoptium.net/temurin/releases/?version=21)、[Maven下载](https://maven.apache.org/download.cgi)、[Node官方归档](https://nodejs.org/dist/v24.21.0/)、[Docker Desktop Windows](https://docs.docker.com/desktop/setup/install/windows-install/)。

## 后端依赖

| 组件 | 版本 |
|---|---|
| Spring Boot | 3.5.16 |
| MyBatis-Plus | 3.5.17 |
| RocketMQ Spring Starter | 2.3.6 |
| RocketMQ client / Broker | 5.3.2 |
| Springdoc | 2.8.17 |
| MySQL | 8.4 |
| Redis | 7.4 |
| Flyway | 11.7.2，由Boot BOM管理 |
| Testcontainers | 1.21.4，由Boot BOM管理 |
| Maven Wrapper | 3.3.4，Maven distribution=3.9.11 |

其余Spring/Security/Jackson/JDBC等版本以 `backend/pom.xml` 的Boot依赖管理和当前解析树为准。核心业务SQL由Spring JDBC执行；MyBatis-Plus用于商品实体查询，共用同一数据源与事务。

MySQL8.4迁移与实际SQL通过本机和Testcontainers验收。Flyway11.7.2日志提示其测试支持上限低于MySQL8.4；本次运行成功不代表所有未来MySQL版本都兼容。后续升级应同步进行迁移和回归验证。

## 前端锁文件的实际版本

| 包 | package-lock.json实际版本 |
|---|---|
| Vue | 3.5.43 |
| Vue Router | 4.6.4 |
| Pinia | 3.0.4 |
| Vite | 7.3.7 |
| TypeScript | 5.9.3 |
| Axios | 1.20.0 |
| Element Plus | 2.14.7 |
| Vitest | 4.1.11 |
| Playwright | 1.64.0 |

package.json使用语义版本范围，但本项目提交lock文件，CI/Docker使用npm ci，避免每次安装自动升级。当前npm audit输出0 vulnerabilities；它不等价于完整源代码安全审计。

## VS Code

已安装并检查Java相关扩展：Java Extension Pack、Language Support for Java、Debugger for Java、Test Runner for Java、Maven for Java、Project Manager for Java，以及Spring Boot Extension Pack。已有Vue/Docker扩展继续使用。

`launch.json`指定Java主类和本机环境文件；已核对安装的Debugger for Java支持envFile。插件导入和F5交互未用UI自动化验证，实际Java编译、运行和业务验证由命令行完成。

## 容器镜像与网络

源码构建：后端Maven+Temurin21多阶段，前端Node24.21.0+Nginx1.28多阶段。后端运行使用非root用户。业务口端口只映射127.0.0.1。

首次构建曾遇到Docker Hub EOF、Maven TLS握手中断；没有关闭TLS验证，重试后官方仓库下载成功。后端改为BuildKit Maven缓存，并直接执行package，避免go-offline下载无关报告插件；后续层和依赖可复用。start-docker.ps1最多重试构建3次，失败会明确退出，不会把旧镜像的启动冒充新构建通过。

RocketMQ官方镜像使用UID3000，命名卷初始化由专用mq-volume-init服务修正本项目卷的所有权。两个模式分别配置advertise地址：本地127.0.0.1、全容器broker。topic初始化直接连接broker:10911，不依赖容器内访问本地advertise地址。

## 私密配置

`deploy/.env`保存当前数据库/Redis/模拟回调密钥和管理员初始化密码；`deploy/local.env`提供VS Code调试环境。两者都被gitignore和dockerignore排除，不写入镜像。

首次克隆缺少这两个文件时执行 `scripts/init-env.ps1`，然后检查工具路径。如果修改已有数据库的账号密码，应先正确更新数据库，再更新文件，不能仅替换.env期待旧数据卷自动重新初始化。
