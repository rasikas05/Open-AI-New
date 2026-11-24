package com.ai.openai_api_service.config;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Wraps the request body with a counting stream that enforces {@code maxBodyBytes}.
 */
final class LimitedContentLengthRequestWrapper extends HttpServletRequestWrapper {

    private final long maxBodyBytes;
    private ServletInputStream limitedStream;
    private BufferedReader reader;

    LimitedContentLengthRequestWrapper(HttpServletRequest request, long maxBodyBytes) {
        super(request);
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (limitedStream == null) {
            limitedStream = new LimitedServletInputStream(super.getInputStream(), maxBodyBytes);
        }
        return limitedStream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
        return reader;
    }
}
