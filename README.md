# ShuMaMall 数码商城

微服务架构的数码商城系统，覆盖商品、订单、支付、搜索、评论、权限、AI 导购、视频等电商链路。

## 技术栈

| 分类 | 选型 |
|---|---|
| 基础框架 | JDK 21 · Spring Boot 3.5 · Spring Cloud 2025 · Spring Cloud Alibaba 2025 |
| 注册与配置 | Nacos |
| 存储 | MySQL · Redis · MongoDB · Elasticsearch · MinIO |
| 消息与事务 | RabbitMQ · Seata（AT 模式） |
| AI | Spring AI + DeepSeek（RAG / Tool Calling / SSE 流式） |
| 前端 | Vue 3 · Element Plus · Vite |

## 服务一览

| 模块 | 端口 | 职责 |
|---|---|---|
| shumamall-gateway | 8080 | 统一网关：路由转发、限流、客户端 IP 透传 |
| shumamall-auth | 8081 | JWT 登录认证 |
| shumamall-user | 8082 | 用户与收货地址（敏感字段加密存储） |
| shumamall-product | 8083 | 商品 / 分类 / 品牌 / SKU，读缓存三防 |
| shumamall-order | 8084 | 购物车与订单，状态机 |
| shumamall-payment | 8085 | 支付与退款 |
| shumamall-permission | 8086 | RBAC 权限与操作审计 |
| shumamall-search | 8087 | ES 全文检索（IK 中文分词） |
| shumamall-agent | 8088 | AI 导购：混合检索 + 工具调用 + 流式回复 |
| shumamall-comment | 8089 | 评论区（MongoDB） |
| shumamall-admin | 8090 | 管理端接口 |
| shumamall-video | 8092 | 视频上传、HLS 切片与播放 |
| shumamall-dict | 8093 | 数据字典 |

## 主要特性

- **AI Agent 导购**：BM25 + KNN 混合检索（RRF 融合）、LLM 规划并调用工具、高风险操作人工确认
- **分布式事务**：下单链路的跨服务扣库存采用 Seata AT，保证订单与库存一致
- **消息可靠性**：生产者 confirm/returns 回调确认投递结果；消费者手动 ack + 失败重试 + 死信队列，并用 Redis 做消息级幂等去重
- **支付并发幂等**：同一订单并发支付只成功一笔（应用层前置校验 + 数据库唯一约束兜底），订单状态用条件更新避免并发重复改写
- **秒杀**：库存预扣与一人一单占位由单个 Lua 脚本原子完成，杜绝「先判断后扣减」的超卖窗口；RabbitMQ 异步建单，手动 ack 配合 Redis 幂等去重与死信队列，对账定时任务兜底重投超时消息、回补活动额度；秒杀路由豁免网关限流，超量请求由 Lua 预扣原子拒绝
- **搜索**：Elasticsearch + IK 中文分词，支持过滤与排序；ES 查询异常时降级调用商品服务（MySQL）列表接口，保证前台搜索不中断
- **权限与审计**：RBAC 动态权限、按钮级权限、操作审计日志
- **安全**：JWT 认证、敏感字段 AES 加密、请求签名与防重放、网关用户维度限流
- **接口文档**：每个业务服务集成 springdoc，自带 Swagger UI 与 OpenAPI JSON
- **视频**：分片上传、ffprobe 校验、FFmpeg HLS 切片、签名播放与断点续播
- **容错**：Feign 统一 connect 2s / read 5s（默认 read 60s 会把调用方线程拖死）+ Resilience4j 熔断（最近 10 次滑窗、失败率 50% 开路、10s 后自动半开）；关键写路径 fail-closed 不降级（下单扣库存宁可显式失败也不吞异常，避免超卖），仅评论数回写等非关键路径配降级兜底；并禁用熔断的线程池隔离，避免 traceId / Seata XID 等 ThreadLocal 跨线程丢失

## 项目结构

```
shumamall-backend/        Maven 多模块后端（网关 + 13 个服务）
shumamall-frontend/       pc-user 用户端 / pc-admin 管理端
nacos-config/             Nacos 配置模板与发布脚本
mysql-init/ mongo-init/   数据库初始化脚本
es-plugins/               IK 分词插件下载脚本（插件本体约 9.5MB，属第三方二进制不入库）
```

## 快速开始

### 方式一：本地起服务（开发调试）

```bash
# 1. 下载 ES 的 IK 分词插件（首次克隆后执行一次；插件是第三方二进制，不入库）
powershell -ExecutionPolicy Bypass -File es-plugins\fetch-ik.ps1

# 2. 启动基础设施（MySQL / Redis / Nacos / RabbitMQ / MinIO / MongoDB / ES / Seata）
docker compose up -d

# 3. 发布 Nacos 配置：nacos-config/ 下是模板，填入真实值后用 publish.ps1 发布
powershell -ExecutionPolicy Bypass -File nacos-config\publish.ps1 -Source <配置好的文件> -DataId shumamall-common.yaml

# 4. 打包并启动后端
cd shumamall-backend && mvn install -DskipTests

# 5. 启动前端
cd shumamall-frontend/pc-user && npm i && npm run dev
```

### 方式二：容器化部署（一个 Dockerfile 构建全部服务）

后端只有一个通用 Dockerfile，用 `--build-arg MODULE` 指定模块，13 个服务共用同一份构建逻辑。

```bash
# 基础设施（应用容器要靠 shumamall-net 网络找 mysql / redis / ...）
# 首次克隆后先执行 es-plugins\fetch-ik.ps1 下载 IK 分词插件，否则 ES 会因插件目录为空启动失败
docker compose up -d

# 只起「网关 + 认证 + 用户 + 商品 + 订单 + 支付」这条最小可下单链路
docker compose -f docker-compose.app.yml --profile core up -d --build

# 再加搜索链路（需要基础设施里的 elasticsearch、rabbitmq）
docker compose -f docker-compose.app.yml --profile core --profile search up -d --build

# 全量 13 个服务
docker compose -f docker-compose.app.yml --profile all up -d --build

# 改完代码只重建受影响的镜像
docker compose -f docker-compose.app.yml --profile core build order payment
docker compose -f docker-compose.app.yml --profile core up -d --no-deps order
```

profile 划分：`core`（下单主链路）、`search`、`agent`、`admin`（权限/管理端/视频/字典）、`comment`、`all`。
完整命令与端口对照见 `docker-compose.app.yml` 头部注释。

### 接口文档

业务服务启动后可直接访问 Swagger UI 与 OpenAPI JSON，例如商品服务：

- `http://localhost:8083/swagger-ui/index.html`
- `http://localhost:8083/v3/api-docs`

（网关是 WebFlux 应用，不提供 Swagger UI，接口文档按具体服务端口访问。）

各服务的端口与依赖见模块内 `bootstrap.yml`。
