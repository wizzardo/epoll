package com.wizzardo.epoll;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * Created by wizzardo on 24/04/16.
 */
public class EpollCoreTest {

    @Test
    public void test_copy() {
        int limit = 256;
        ByteBuffer src = ByteBuffer.allocateDirect(limit);
        ByteBuffer dst = ByteBuffer.allocateDirect(limit);
        for (int i = 0; i < limit; i++) {
            src.put((byte) i);
        }

        EpollCore.arraycopy(src, 0, dst, 0, limit);
        dst.limit(limit);
        for (int i = 0; i < limit; i++) {
            assertEquals((byte) i, dst.get());
        }
    }

    @Test
    public void test_copy_2() {
        int limit = 256;
        ByteBuffer src = ByteBuffer.allocateDirect(limit);
        ByteBuffer dst = ByteBuffer.allocateDirect(limit);
        for (int i = 0; i < limit; i++) {
            src.put((byte) i);
        }

        EpollCore.arraycopy(src, 0, dst, 0, limit);
        dst.limit(limit);
        for (int i = 0; i < limit; i++) {
            assertEquals((byte) i, dst.get());
        }
        for (int i = 0; i < limit; i++) {
            EpollCore.arraycopy(src, i, dst, i, 1);
            dst.position(i);
            dst.limit(i + 1);
            assertEquals((byte) i, dst.get());
        }
    }
}
