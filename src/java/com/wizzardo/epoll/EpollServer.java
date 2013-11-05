package com.wizzardo.epoll;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * @author: moxa
 * Date: 11/5/13
 */
public abstract class EpollServer extends Thread {
    //  gcc -m32 -shared -fpic -o ../../../../../libepoll-server_x32.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollServer.c
    //  gcc      -shared -fpic -o ../../../../../libepoll-server_x64.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollServer.c
    //  javah -jni com.wizzardo.epoll.EpollServer

    private volatile boolean running = true;

    static {
        try {
            loadLib("libepoll-server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadLib(String name) {
        String arch = System.getProperty("os.arch");
        name = name + (arch.contains("64") ? "_x64" : "_x32") + ".so";
        // have to use a stream
        InputStream in = EpollServer.class.getResourceAsStream("/" + name);

        File fileOut = null;
        try {
            if (in == null) {
                in = new FileInputStream(name);
            }
            fileOut = File.createTempFile(name, "lib");
            OutputStream out = new FileOutputStream(fileOut);
            int r;
            byte[] b = new byte[1024];
            while ((r = in.read(b)) != -1) {
                out.write(b, 0, r);
            }
            in.close();
            out.close();
            System.load(fileOut.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (running) {
            try {

                int[] descriptors = waitForEvents(500);
                for (int i = 0; i < descriptors.length; i += 2) {
                    final int fd = descriptors[i];
                    int event = descriptors[i + 1];
                    switch (event) {
                        case 0: {
//                            System.out.println("new connection from " + getIp(descriptors[i + 2]) + ":" + descriptors[i + 3]);
                            onOpen(fd, descriptors[i + 2], descriptors[i + 3]);
                            i += 2;
                            break;
                        }
                        case 1: {
                            onRead(fd);
                            break;
                        }
                        case 2: {
                            onWrite(fd);
                            break;
                        }
                        case 3: {
                            onClose(fd);
                            break;
                        }

                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public void stopServer() {
        running = false;
        stopListening();
    }

    public static String getIp(int ip) {
        StringBuilder sb = new StringBuilder();
        sb.append((ip >> 24) + (ip < 0 ? 256 : 0)).append(".");
        sb.append((ip & 16777215) >> 16).append(".");
        sb.append((ip & 65535) >> 8).append(".");
        sb.append(ip & 255);
        return sb.toString();
    }

    public abstract void onRead(int fd);

    public abstract void onWrite(int fd);

    public abstract void onOpen(int fd, int ip, int port);

    public abstract void onClose(int fd);

    public boolean bind(int port) {
        return listen(String.valueOf(port));
    }

    private native boolean listen(String port);

    private native boolean stopListening();

    public native int[] waitForEvents(int timeout);

    public int[] waitForEvents() {
        return waitForEvents(-1);
    }

    synchronized public native void startWriting(int fd);

    synchronized public native void stopWriting(int fd);

    public native void close(int fd);

    public native int read(int fd, ByteBuffer b, int off, int len) throws IOException;

    public native int write(int fd, ByteBuffer b, int off, int len) throws IOException;

}
