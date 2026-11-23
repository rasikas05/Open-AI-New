package com.ai.openai_api_service.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.IOException;

/**
 * Counts bytes read from a delegate stream and aborts when {@code maxBytes} is exceeded.
 * Used so chunked / unknown Content-Length requests still enforce a total body limit.
 */
final class LimitedServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maxBytes;
    private long bytesRead;

    LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
        this.delegate = delegate;
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int value = delegate.read();
        if (value >= 0) {
            account(1);
        }
        return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = delegate.read(b, off, len);
        if (n > 0) {
            account(n);
        }
        return n;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int n = delegate.read(b);
        if (n > 0) {
            account(n);
        }
        return n;
    }

    @Override
    public boolean isFinished() {
        return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        delegate.setReadListener(readListener);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private void account(int n) throws RequestBodyTooLargeException {
        bytesRead += n;
        if (bytesRead > maxBytes) {
            throw new RequestBodyTooLargeException();
        }
    }
}
