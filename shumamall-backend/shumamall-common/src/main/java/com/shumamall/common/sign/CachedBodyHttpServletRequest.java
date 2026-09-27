package com.shumamall.common.sign;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 可重复读请求体的包装器。
 * <p>
 * Servlet 的 {@code getInputStream()} 是一次性的：验签必须先读一遍 body，
 * 读完再交给 Controller 的话，{@code @RequestBody} 反序列化会拿到空流，报「Required request body is missing」。
 * 因此在过滤器入口就把 body 全量读进内存，之后 {@link #getInputStream()} 每次都从缓存字节数组重建流。
 * <p>
 * 只对**需要验签的写接口**使用（{@code RequestSignFilter} 里对写方法才包装），
 * 评论/视频上传等大 body 接口不会走这里，不担心内存放大。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /**
     * 缓存的原始请求体字节。
     */
    public byte[] getCachedBody() {
        return cachedBody;
    }

    /**
     * 缓存的原始请求体字符串（UTF-8），用于参与签名计算。
     */
    public String getBodyAsString() {
        return new String(cachedBody, StandardCharsets.UTF_8);
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * 基于字节数组的可重复读输入流。
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        CachedBodyServletInputStream(byte[] body) {
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // 同步读取，无需异步回调
        }

        @Override
        public int read() {
            return inputStream.read();
        }
    }
}
