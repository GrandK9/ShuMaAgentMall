# syntax=docker/dockerfile:1.7
#
# =============================================================================
# ShuMaMall 后端通用镜像构建文件（多阶段构建）
#
# 一个 Dockerfile 构建全部 13 个服务，用 --build-arg MODULE 指定要打哪个模块：
#   docker build -t shumamall/product:1.0.0 --build-arg MODULE=shumamall-product --build-arg PORT=8083 -f Dockerfile .
#
# 「按需构建、按需启动子集」的编排与命令见仓库根目录的 docker-compose.app.yml。
#
# 为什么用「通用 Dockerfile + 参数」而不是每个模块一份 Dockerfile：
# 13 个服务只有模块名和端口不同。复制 13 份内容雷同的构建脚本，任何一处调整
# （换基础镜像、改 JVM 参数）都要同步 13 个文件，漏一个就出现行为不一致的镜像。
# =============================================================================

ARG MODULE=shumamall-product
# 构建期 Maven 仓库地址。默认走阿里云公共仓库：容器内直连 Maven Central 会被限速到
# 几十 kB/s，首次构建（空 m2 缓存）要下几百 MB，实测不可用。需要还原成官方中央仓库
# 时传 --build-arg MAVEN_MIRROR=（空值即跳过镜像配置，走 settings 默认的 central）。
ARG MAVEN_MIRROR=https://maven.aliyun.com/repository/public

# ------------------------------ 构建阶段 ------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder
ARG MODULE
ARG MAVEN_MIRROR
WORKDIR /build

# 只拷源码与 pom（.dockerignore 已排除 target/、.git/ 等），
# 一步 COPY 让缓存失效范围最小：改代码只影响这一层往后的步骤。
COPY . .

# -pl 指定目标模块，-am 连带构建它依赖的 shumamall-common；
# m2 本地仓库用 BuildKit cache mount 持久化，重复构建不再重新下载依赖。
# settings.xml 必须写在 cache mount 目录内：mount 会盖掉镜像里同路径的已有文件，
# 写在外面等于没写。写在挂载点内则随缓存一起留存，后续构建直接命中。
# cp 到固定路径，避免运行阶段用通配符去猜 jar 名（repackage 还会留下 .jar.original）。
RUN --mount=type=cache,target=/root/.m2 \
    if [ -n "${MAVEN_MIRROR}" ]; then \
        mkdir -p /root/.m2 && { \
            echo '<settings>'; \
            echo '  <mirrors>'; \
            echo '    <mirror>'; \
            echo '      <id>build-mirror</id>'; \
            echo '      <name>build mirror</name>'; \
            echo '      <url>'"${MAVEN_MIRROR}"'</url>'; \
            echo '      <mirrorOf>central</mirrorOf>'; \
            echo '    </mirror>'; \
            echo '  </mirrors>'; \
            echo '</settings>'; \
        } > /root/.m2/settings.xml; \
    fi \
    && mvn -B -DskipTests -pl ${MODULE} -am package \
    && cp ${MODULE}/target/*.jar /build/app.jar

# ------------------------------ 运行阶段 ------------------------------
FROM eclipse-temurin:21-jre
ARG MODULE
ARG PORT=8080

LABEL org.opencontainers.image.title="shumamall-${MODULE}" \
      org.opencontainers.image.description="数码商城 ${MODULE} 服务"

WORKDIR /app

# 以非 root 运行：容器被攻破时降低影响面
RUN groupadd --system --gid 1001 appuser \
    && useradd --system --uid 1001 --gid appuser --home-dir /app appuser

# /app 的属主必须是 appuser：Nacos 客户端自带的 logback（nacos-logback.xml）会往
# ${user.home}/logs/nacos/*.log 写 config/naming/remote 等日志，user.home 即上面
# --home-dir 指定的 /app；本地配置缓存也落在 user.home 下。
# WORKDIR 建出来的 /app 属主是 root，不改属主时每次启动都会刷一屏
# "Failed to create parent directories for [/app/logs/nacos/config.log]" 与
# "[NA] failed to write cache for ..." 的 ERROR —— 日志文件写不出去，噪声盖住真问题。
RUN chown appuser:appuser /app

COPY --from=builder --chown=appuser:appuser /build/app.jar /app/app.jar

USER appuser

EXPOSE ${PORT}

# MaxRAMPercentage 让 JVM 跟随容器内存上限（配合 compose 的 mem_limit），
# 否则 JVM 按宿主机总内存推算堆大小，容器很容易被 OOM Kill。
# 时区显式指定：容器默认 UTC，会让 createdAt / 支付时间等字段整体偏 8 小时。
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

# 用 exec 让 java 成为 PID 1：docker stop 的 SIGTERM 能直接送达 JVM，
# Spring Boot 的优雅停机（等在途请求结束）才会真正生效。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
