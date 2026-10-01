package com.shumamall.video.config;

import io.minio.MinioClient;
import lombok.Data;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储配置。
 * <p>
 * 从 bootstrap.yml / Nacos 配置读取连接参数，创建 {@link MinioClient} Bean。
 * 三个 bucket（raw / hls / thumb）在 {@link MinioBucketInitRunner} 中初始化。
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /** MinIO 服务端点，如 http://localhost:9000 */
    private String endpoint;

    /**
     * 浏览器播放/预览用的公网端点（签名 URL 的 Host）。
     * 容器内 {@link #endpoint} 常为 {@code http://minio:9000}，若未设置则与 endpoint 相同。
     */
    private String publicEndpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 秘密密钥 */
    private String secretKey;

    /** 原始视频存储桶 */
    private String rawBucket = "shumamall-raw";

    /** HLS 切片存储桶 */
    private String hlsBucket = "video-hls";

    /** 缩略图存储桶 */
    private String thumbBucket = "video-thumb";

    MinioClient buildClient(String targetEndpoint, VideoProperties videoProperties) {
        return buildClient(targetEndpoint, videoProperties, null);
    }

    /**
     * 预签名客户端：SDK 的 endpoint 使用 {@code publicEndpoint}（签名 URL 的 Host 与浏览器一致），
     * 实际 HTTP 走 {@code internalEndpoint}（Docker 内通常为 {@code http://minio:9000}）。
     */
    MinioClient buildPresignClient(String publicEndpoint, String internalEndpoint, VideoProperties videoProperties) {
        return buildClient(publicEndpoint, videoProperties, internalEndpoint);
    }

    private MinioClient buildClient(String targetEndpoint, VideoProperties videoProperties, String internalEndpoint) {
        VideoProperties.Io io = videoProperties.getIo();
        OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder()
                .connectTimeout(io.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(io.getReadIdleTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(io.getWriteIdleTimeoutSeconds(), TimeUnit.SECONDS);
        if (StringUtils.hasText(internalEndpoint)) {
            URI publicUri = URI.create(normalizeEndpoint(targetEndpoint));
            URI internalUri = URI.create(normalizeEndpoint(internalEndpoint));
            if (!endpointHostPortEquals(publicUri, internalUri)) {
                httpBuilder.addInterceptor(chain -> {
                    HttpUrl url = chain.request().url();
                    if (!matchesEndpointHost(url, publicUri)) {
                        return chain.proceed(chain.request());
                    }
                    HttpUrl rewritten = url.newBuilder()
                            .scheme(internalUri.getScheme())
                            .host(internalUri.getHost())
                            .port(effectivePort(internalUri))
                            .build();
                    return chain.proceed(chain.request().newBuilder().url(rewritten).build());
                });
            }
        }
        return MinioClient.builder()
                .endpoint(targetEndpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpBuilder.build())
                .build();
    }

    private static String normalizeEndpoint(String endpoint) {
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private static boolean endpointHostPortEquals(URI a, URI b) {
        return a.getHost().equalsIgnoreCase(b.getHost()) && effectivePort(a) == effectivePort(b);
    }

    private static boolean matchesEndpointHost(HttpUrl url, URI endpointUri) {
        if (!url.host().equalsIgnoreCase(endpointUri.getHost())) {
            return false;
        }
        return url.port() == effectivePort(endpointUri);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
