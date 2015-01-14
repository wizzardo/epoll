package com.wizzardo.epoll.sized;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public interface Writable {
    public void write(byte[] bytes, int offset, int length);
}
