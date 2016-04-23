package com.wizzardo.epoll.readable;

import com.wizzardo.epoll.ByteBufferWrapper;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

/**
 * Created by wizzardo on 24.04.16.
 */
public class ReadableByteBufferTest {

    @Test
    public void test_1() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(256);
        ReadableByteBuffer readable = new ReadableByteBuffer(buffer);

        assertSame(buffer, readable.getByteBuffer(null).buffer());
    }

    @Test
    public void test_2() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(256);
        ReadableByteBuffer readable = new ReadableByteBuffer(buffer);
        assertEquals(256, readable.read(readable.getByteBuffer(null).buffer()));
    }

}