# GenChat-X 项目独立说明

> 本文是仓库拆分记录，不是日常运行指南。当前启动方式与版本以根 [`README.md`](../README.md)、`settings.gradle` 和 `build.gradle` 为准。

## 来源与仓库边界

- 来源：GenChat 的 `refactor/agent-foundation` 分支，拆分基线提交 `0abd53d`（`feat: migrate to Spring AI 2 and harden agent workflows`）。
- 新项目目录：`GenChat-X/`，独立 `.git`，主分支 `main`；保留源分支的祖先提交历史，不使用 worktree、符号链接或共享 Git 对象。
- 原 `GenChat/` 的分支、工作区和远程配置保持不变。
- 拆分时新仓库未配置远程地址，以避免误推到原仓库；后续首次推送前应使用 `git remote -v` 核对目标。历史提交中的名称、作者和来源链接保留。
- 仅迁移分支中 Git 已跟踪的文件；原目录的 `.env`、`application-local.yml`、IDE 配置、依赖缓存和未跟踪的生成文件没有复制。

## 项目关系

```text
AI/
├── GenChat/             # 原项目，可独立维护
├── GenChat-X/           # 新应用项目，不依赖 GenChat 目录
└── agent-foundation/    # 独立框架，通过 Gradle composite build 引入
```

`settings.gradle` 引入 `../agent-foundation`；拆分基线使用 Foundation BOM + `agent-foundation-core`，具体版本以当前构建文件为准。这里的“独立”指业务项目和 Git 仓库独立，不表示把框架源码复制到应用内，也不表示可直接删除同级框架目录。

## 名称与兼容性

| 范围 | 名称 |
|------|------|
| 项目、Gradle 根项目、Spring 应用、界面 | `GenChat-X` |
| 前端 npm 包 | `genchat-x-frontend` |
| 后端可执行产物 | `build/libs/GenChat-X-<version>.jar`（版本以 `build.gradle` 为准） |
| Docker Compose 项目和网络 | `genchat-x` |
| Docker 容器前缀 | `genchat-x-` |

为避免无关的兼容性变更，保留 `com.genchat` Java 包及 Maven group、`GenChatApplication` 入口类、`genchat.*` 配置键、已有环境变量和协议字段。浏览器持久化键也保留；如果在相同浏览器源（协议、主机、端口）部署两个前端，它们仍可能共享本地偏好和文件会话映射，应使用不同源。

## 开发和数据迁移

1. 在 IDE 中将 `GenChat-X/` 作为独立 Gradle 项目打开，Gradle JVM 使用 JDK 25；前端使用 Node.js 20+。
2. 参考根 README，将 `src/main/resources/application-local.yml.example` 复制为 `application-local.yml`，配置自己的服务和密钥。若复用旧本地配置，务必检查数据源和绝对路径，特别是 `skills.directory`、`ppt.python-script`，不要继续指向 `GenChat/`。
3. Docker 使用新的项目名和默认卷前缀（例如 `genchat-x_mysql-data`），不会自动复用或迁移旧卷。旧数据需通过备份/恢复迁入；不要删除旧卷。
4. 两套环境并行运行时，必须调整 MySQL、Redis、MinIO、PgVector、后端和前端的宿主机端口，并同步后端连接配置和 Vite 代理。容器名独立并不能避免端口冲突。
5. `.env` 由 Docker Compose 读取，不会自动注入直接执行的 `./gradlew bootRun`。后端请使用导出的环境变量或本地 YAML 配置。不要让两个业务应用意外连接同一套数据服务。
6. 在新项目根目录运行 `./gradlew test bootJar`，在 `frontend/` 运行 `npm ci`、`npm run build` 验证。

`refactor/` 下的架构方案保留历史规划背景，不是当前功能全部已实现的承诺；涉及原 `GenChat v1.0` 的诊断属于历史基线。实际版本以构建文件为准。
