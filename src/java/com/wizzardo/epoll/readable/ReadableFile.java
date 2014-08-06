package com.wizzardo.epoll.readable;

import com.wizzardo.tools.misc.WrappedException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author: wizzardo
 * Date: 8/5/14
 */
public class ReadableFile extends ReadableData {
    private long offset;
    private long length;
    private long position;
    private RandomAccessFile randomAccessFile;
    private FileChannel channel;

    public ReadableFile(File file, long offset, long length) throws IOException {
        if (offset < 0)
            throw new IllegalArgumentException("negative offset: " + offset);
        if (length < 0)
            throw new IllegalArgumentException("negative length: " + length);
        if (offset + length > file.length())
            throw new IllegalArgumentException("offset + length > file.length()");

        this.offset = offset;
        this.length = length;

        position = offset;
        randomAccessFile = new RandomAccessFile(file, "r");
        if (offset > 0)
            randomAccessFile.seek(offset);

        channel = randomAccessFile.getChannel();
    }

    @Override
    public int read(ByteBuffer byteBuffer) {
        try {
            int limit = (int) (offset + length - position);
            if (limit < byteBuffer.limit())
                byteBuffer.limit(limit);

            int read = channel.read(byteBuffer);
            if (read > 0)
                position += read;
            return read;
        } catch (IOException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public void unread(int i) {
        if (i < 0)
            throw new IllegalArgumentException("can't unread negative value: " + i);
        if (position - i < offset)
            throw new IllegalArgumentException("can't unread value bigger than offset (" + offset + "): " + i);
        position -= i;
        try {
            randomAccessFile.seek(position);
        } catch (IOException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public boolean isComplete() {
        return position == offset + length;
    }

    @Override
    public long complete() {
        return position - offset;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public long remains() {
        return length + offset - position;
    }
}
