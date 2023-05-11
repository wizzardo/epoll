package com.wizzardo.epoll.readable;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ReadableByteArrayTest {


    @Test
    public void test_read_into_heap_bb() {
        byte[] buffer = new byte[256];
        for (int i = 0; i < 256; i++) {
            buffer[i]= (byte) i;
        }
        ReadableByteArray readable = new ReadableByteArray(buffer);

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
        byte[] buffer = new byte[256];
        for (int i = 0; i < 256; i++) {
            buffer[i]= (byte) i;
        }
        ReadableByteArray readable = new ReadableByteArray(buffer);

        ByteBuffer b = ByteBuffer.allocateDirect(256);
        assertEquals(256, readable.read(b));
        assertEquals(256, b.position());
        b.flip();
        for (int i = 0; i < 256; i++) {
            assertEquals((byte) i, b.get());
        }
    }

    @Test
    public void test_sub_buffer() {
        byte[] buffer = new byte[256];
        for (int i = 0; i < 256; i++) {
            buffer[i]= (byte) i;
        }
        ReadableByteArray subBuffer = new ReadableByteArray(buffer, 32, 32);

        assertEquals(32, subBuffer.length());
        assertEquals(32, subBuffer.remains());

        ByteBuffer b = ByteBuffer.allocateDirect(128);
        assertEquals(32, subBuffer.read(b));
        assertEquals(32, b.position());
        b.flip();
        for (int i = 32; i < 64; i++) {
            assertEquals((byte) i, b.get());
        }
        assertEquals(0, subBuffer.remains());
        assertEquals(32, subBuffer.complete());
        assertEquals(true, subBuffer.isComplete());
    }
}