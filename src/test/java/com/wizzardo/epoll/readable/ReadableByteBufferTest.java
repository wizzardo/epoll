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
    public void test_sameBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(256);
        ReadableByteBuffer readable = new ReadableByteBuffer(buffer);

        assertSame(buffer, readable.getByteBuffer(null).buffer());
    }

    @Test
    public void test_readAll() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(256);
        ReadableByteBuffer readable = new ReadableByteBuffer(buffer);
        assertEquals(256, readable.read(readable.getByteBuffer(null).buffer()));
    }

    @Test
    public void test_read_into_heap_bb() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(256);
        for (int i = 0; i < 256; i++) {
            buffer.put((byte) i);
        }
        ReadableByteBuffer readable = new ReadableByteBuffer(buffer);

        ByteBuffer b = ByteBuffer.allocate(256);
        assertEquals(256, readable.read(b));
        assertEquals(256, b.position());
        b.flip();
        for (int i = 0; i < 256; i++) {
            assertEquals((byte) i, b.get());
        }
    }

    @Test
    public void test_read_into_direct_bb() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(256);
        for (int i = 0; i < 256; i++) {
            buffer.put((byte) i);
        }
        ReadableByteBuffer readable = new ReadableByteBuffer(buffer);

        ByteBuffer b = ByteBuffer.allocateDirect(256);
        assertEquals(256, readable.read(b));
        assertEquals(256, b.position());
        b.flip();
        for (int i = 0; i < 256; i++) {
            assertEquals((byte) i, b.get());
        }
    }
}