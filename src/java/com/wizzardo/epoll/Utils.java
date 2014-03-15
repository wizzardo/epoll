package com.wizzardo.epoll;

/**
 * @author: wizzardo
 * Date: 3/15/14
 */
class Utils {
    public static int readInt(byte[] b, int offset) {
        if (b.length - offset < 4 || offset < 0)
            throw new IndexOutOfBoundsException("byte[].length = " + b.length + ", but offset = " + offset + " and we need 4 bytes");

        return ((b[offset] & 0xff) << 24) + ((b[offset + 1] & 0xff) << 16) + ((b[offset + 2] & 0xff) << 8) + ((b[offset + 3] & 0xff));
    }

    public static int readShort(byte[] b, int offset) {
        if (b.length - offset < 2 || offset < 0)
            throw new IndexOutOfBoundsException("byte[].length = " + b.length + ", but offset = " + offset + " and we need 2 bytes");

        return ((b[offset] & 0xff) << 8) + ((b[offset + 1] & 0xff));
    }
}
