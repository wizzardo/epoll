package com.wizzardo.epoll;


import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 1/4/14
 */
public class EpollServerTest {

    @Test
    public void startStopTest() throws InterruptedException {
        EpollServer server = new EpollServer() {
            @Override
            public void readyToRead(int fd) {
            }

            @Override
            public void readyToWrite(int fd) {
            }

            @Override
            public void onOpenConnection(int fd, int ip, int port) {
            }

            @Override
            public void onCloseConnection(int fd) {
            }
        };
        int port = 28991;

        server.bind(port);
        server.start();

        Thread.sleep(500);

        server.stopServer();

        Thread.sleep(510);

        String connectionRefuse = null;
        try {
            new Socket("localhost", port);
        } catch (IOException e) {
            connectionRefuse = e.getMessage();
        }
        Assert.assertEquals("Connection refused", connectionRefuse);
    }

    @Test
    public void echoTest() throws InterruptedException {
        EpollServer server = new EpollServer() {
            @Override
            public void readyToRead(int fd) {
                ByteBuffer bb = ByteBuffer.allocateDirect(1024);
                try {
                    int r = read(fd, bb, 0, 1024);
                    int w = 0;
                    while (w < r) {
                        w += write(fd, bb, w, r - w);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void readyToWrite(int fd) {
            }

            @Override
            public void onOpenConnection(int fd, int ip, int port) {
            }

            @Override
            public void onCloseConnection(int fd) {
            }
        };
        int port = 28991;

        server.bind(port);
        server.start();

        try {
            Socket s = new Socket("localhost", port);
            OutputStream out = s.getOutputStream();
            out.write("hello world!".getBytes());

            InputStream in = s.getInputStream();
            byte[] b = new byte[1024];
            int r = in.read(b);

            Assert.assertEquals("hello world!", new String(b, 0, r));
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.stopServer();
    }

}
