package com.wizzardo.epoll;

/**
 * @author: wizzardo
 * Date: 22.11.14
 */
public interface ByteBufferProvider {
    ByteBufferWrapper getBuffer();

    /**
     * @return result of casting Thread.currentThread() to {@link ByteBufferProvider}
     * @throws ClassCastException if current thread doesn't implement {@link ByteBufferProvider}
     **/
    static ByteBufferProvider current() {
        return (ByteBufferProvider) Thread.currentThread();
    }
}
