package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableByteArray;
import com.wizzardo.tools.http.HttpClient;
import com.wizzardo.tools.http.Response;
import com.wizzardo.tools.misc.Stopwatch;
import com.wizzardo.tools.misc.Unchecked;
import com.wizzardo.tools.security.MD5;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author: wizzardo
 * Date: 5/5/14
 */
public class EpollClientTest {
    @Test
    public void simpleTest() throws UnknownHostException, InterruptedException {
        int port = 8082;
        EpollServer<Connection> server = new EpollServer<Connection>(port) {
            @Override
            protected IOThread<Connection> createIOThread(int number, int divider) {
                return new IOThread<Connection>(number, divider) {
                    @Override
                    public void onRead(Connection connection) {
                        try {
                            byte[] b = new byte[1024];
                            int r = connection.read(b, 0, b.length, this);
                            connection.write(b, 0, r, this);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
            }
        };

        server.setIoThreadsCount(0);
        server.start();


        String requestString = "GET /1 HTTP/1.1\r\n" +
                "Host: localhost:8082\r\n" +
                "Connection: Close\r\n" +
                "\r\n";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        EpollCore<Connection> epoll = new EpollCore<Connection>() {
            @Override
            protected IOThread<Connection> createIOThread(int number, int divider) {
                return new IOThread<Connection>(number, divider) {
                    @Override
                    public void onRead(Connection connection) {
                        System.out.println("onRead " + connection);
                        byte[] b = new byte[1024];
                        int r = 0;

                        try {
                            r = connection.read(b, 0, b.length, this);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        String s = new String(b, 0, r);
                        System.out.println(s);
                        Assert.assertEquals(requestString, s);
                        countDownLatch.countDown();
                    }
                };
            }
        };

        epoll.setIoThreadsCount(0);
        epoll.start();

        Connection connection = Unchecked.call(() -> epoll.connect("localhost", port));
        connection.write(requestString, () -> new ByteBufferWrapper(1024));

        try {
            Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));
        } finally {
            server.close();
            epoll.close();
        }
    }

//    @Test
    public void sslTest() throws UnknownHostException, InterruptedException {
//        int port = 8082;
//        EpollServer<Connection> server = new EpollServer<Connection>(port) {
//            @Override
//            protected IOThread<Connection> createIOThread(int number, int divider) {
//                return new IOThread<Connection>(number, divider) {
//                    @Override
//                    public void onRead(Connection connection) {
//                        try {
//                            byte[] b = new byte[1024];
//                            int r = connection.read(b, 0, b.length, this);
//                            connection.write(b, 0, r, this);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                };
//            }
//        };
//
//        server.setIoThreadsCount(0);
//        server.start();


//        String host = "stage.timberbase.com";
        String host = "qwe.wapps.duckdns.org";
        String requestString = "GET / HTTP/1.1\r\n" +
                "Host: " + host + ":443\r\n" +
//                "Connection: Close\r\n" +
                "\r\n";
        for (int i = 0; i < 100; i++) {

            CountDownLatch countDownLatch = new CountDownLatch(1);
            EpollCore<Connection> epoll = new EpollCore<Connection>() {
                @Override
                protected IOThread<Connection> createIOThread(int number, int divider) {
                    return new IOThread<Connection>(number, divider) {
                        int total = 0;

                        @Override
                        public void onRead(Connection connection) {
                            System.out.println("onRead " + connection);
                            byte[] b = new byte[1024];
                            int r = 0;

                            while (true) {
                                try {
                                    r = connection.read(b, 0, b.length, this);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                System.out.println("read " + r);

                                if (r == 0)
                                    return;

                                total += r;

                                String s = new String(b, 0, r);
//                                System.out.println(s);
//                        Assert.assertEquals(requestString, s);
                                if (r != b.length)
                                    countDownLatch.countDown();
                            }
                        }

                        @Override
                        public void onError(Connection connection, Exception e) throws IOException {
                            super.onError(connection, e);
                            e.printStackTrace();
                        }
                    };
                }
            };
//            epoll.setTTL(500);
            epoll.setIoThreadsCount(1);
            epoll.start();
            Connection connection = Unchecked.call(() -> epoll.connect(host, 443, true, false));
//        Thread.sleep(500);
            connection.write(requestString, () -> new ByteBufferWrapper(1024));
//            System.out.println("connection.sending.size(): " + connection.sending.size());

            try {
                Assert.assertTrue(countDownLatch.await(2, TimeUnit.SECONDS));
            } finally {
//                System.out.println("wait until close");
//                Thread.sleep(100);
                System.out.println("set closing at " + System.currentTimeMillis());
                System.out.println("test thread " + Thread.currentThread().getName());
                System.out.println("connection iothread " + connection.epoll.getName());
//                System.out.println("connection.sending.size(): " + connection.sending.size());
                connection.close();
                System.out.println("closing epoll " + epoll.getName());
                epoll.close();
            }
        }
    }


//    @Test
    public void payloadTest() throws UnknownHostException, InterruptedException {
        final byte[] image = new byte[25 * 1024 * 1024];
        new Random().nextBytes(image);
        String md5String = MD5.create().update(image).toString();

        byte[] data = ("HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: " + image.length + "\r\nContent-Type: image/gif\r\n\r\n").getBytes();

        int port = 8082;
        EpollServer<Connection> server = new EpollServer<Connection>(port) {
            @Override
            protected IOThread<Connection> createIOThread(int number, int divider) {
                return new IOThread<Connection>(number, divider) {
                    byte[] b = new byte[1024];

                    @Override
                    public void onRead(final Connection connection) {
                        try {
                            int r = connection.read(b, 0, b.length, this);
                            if (r == 0)
                                return;
//                            System.out.println("read: " + r);
//                            System.out.println(new String(b, 0, r));
//                            System.out.println("end");
//                            System.out.println("");
                            if (r >= 4 && b[r - 4] == '\r' && b[r - 3] == '\n' && b[r - 2] == '\r' && b[r - 1] == '\n') {
                                connection.write(data, this);
                                connection.write(image, this);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                };
            }
        };

        server.setIoThreadsCount(0);
        server.start();


        String requestString = "GET /1 HTTP/1.1\r\n" +
                "Host: localhost:8082\r\n" +
                "Connection: Close\r\n" +
                "\r\n";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        EpollCore<Connection> epoll = new EpollCore<>();

        epoll.setIoThreadsCount(0);
        epoll.start();
        Connection connection = Unchecked.call(() -> epoll.connect("localhost", port));

        byte[] b = new byte[8192];
        AtomicBoolean readingBody = new AtomicBoolean(false);
        AtomicInteger payloadSize = new AtomicInteger(0);
        MD5 md5 = MD5.create();
        connection.onRead((c, bp) -> {
            int r;
            outer:
            while ((r = c.read(b, bp)) > 0) {
//                System.out.println("onread " + r);
                if (!readingBody.get()) {
                    int i = 0;
                    while (i >= 0) {
                        i = indexOf(b, i + 1, r, (byte) '\r');
                        if (i < 0)
                            continue;

                        if (i + 3 >= r)
                            throw new IllegalStateException("Not implemented yet");

                        if (b[i + 3] == '\n' && b[i + 1] == '\n' && b[i + 2] == '\r') {
                            readingBody.set(true);
                            if (r >= i + 4) {
                                int l = r - (i + 4);
                                md5.update(b, i + 4, l);
                                payloadSize.addAndGet(l);
                            } else
                                throw new IllegalStateException("r <= i + 4: " + r + " <= " + (i + 4));
                            continue outer;
                        }
                    }
                } else {
                    md5.update(b, 0, r);
                    payloadSize.addAndGet(r);
                }
            }
            if (payloadSize.get() == image.length) {
                Assert.assertEquals(md5String, md5.asString());
                countDownLatch.countDown();
            }
        });
//        connection.onDisconnect((c, bp) -> {
//            System.out.println("expected " + md5String);
//            System.out.println("received " + md5.asString());
//            countDownLatch.countDown();
//        });
        connection.write(requestString, () -> new ByteBufferWrapper(1024));

        try {
            Assert.assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        } finally {
            server.close();
            epoll.close();
        }
    }

    @Test
    public void payloadTestSSL() throws UnknownHostException, InterruptedException {
        final byte[] image = new byte[25 * 1024 * 1024];
        new Random().nextBytes(image);
        String md5String = MD5.create().update(image).toString();

        byte[] data = ("HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: " + image.length + "\r\nContent-Type: image/gif\r\n\r\n").getBytes();

        int port = 8082;
        EpollServer<Connection> server = new EpollServer<Connection>(port) {
            @Override
            protected IOThread<Connection> createIOThread(int number, int divider) {
                return new IOThread<Connection>(number, divider) {
                    byte[] b = new byte[1024];

                    @Override
                    public void onRead(final Connection connection) {
                        try {
                            int r = connection.read(b, 0, b.length, this);
                            if (r == 0)
                                return;
//                            System.out.println("read: " + r);
//                            System.out.println(new String(b, 0, r));
//                            System.out.println("end");
//                            System.out.println("");
                            if (r >= 4 && b[r - 4] == '\r' && b[r - 3] == '\n' && b[r - 2] == '\r' && b[r - 1] == '\n') {
                                connection.write(data, this);
                                connection.write(image, this);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                };
            }
        };

        server.setIoThreadsCount(0);
        server.loadCertificates("src/test/resources/ssl/test_cert.pem", "src/test/resources/ssl/test_key.pem");
        server.start();


        String requestString = "GET /1 HTTP/1.1\r\n" +
                "Host: localhost:8082\r\n" +
                "Connection: Close\r\n" +
                "\r\n";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        EpollCore<Connection> epoll = new EpollCore<>();

        epoll.setIoThreadsCount(0);
        epoll.start();
        Connection connection = Unchecked.call(() -> epoll.connect("localhost", port, true, false));

        byte[] b = new byte[8192];
        AtomicBoolean readingBody = new AtomicBoolean(false);
        AtomicInteger payloadSize = new AtomicInteger(0);
        MD5 md5 = MD5.create();
        connection.onRead((c, bp) -> {
            int r;
            outer:
            while ((r = c.read(b, bp)) > 0) {
//                System.out.println("onread " + r);
                if (!readingBody.get()) {
                    int i = 0;
                    while (i >= 0) {
                        i = indexOf(b, i + 1, r, (byte) '\r');
                        if (i < 0)
                            continue;

                        if (i + 3 >= r)
                            throw new IllegalStateException("Not implemented yet");

                        if (b[i + 3] == '\n' && b[i + 1] == '\n' && b[i + 2] == '\r') {
                            readingBody.set(true);
                            if (r >= i + 4) {
                                int l = r - (i + 4);
                                md5.update(b, i + 4, l);
                                payloadSize.addAndGet(l);
                            } else
                                throw new IllegalStateException("r <= i + 4: " + r + " <= " + (i + 4));
                            continue outer;
                        }
                    }
                } else {
                    md5.update(b, 0, r);
                    payloadSize.addAndGet(r);
                }
            }
            if (payloadSize.get() == image.length) {
                Assert.assertEquals(md5String, md5.asString());
                countDownLatch.countDown();
            }
        });
//        connection.onDisconnect((c, bp) -> {
//            System.out.println("expected " + md5String);
//            System.out.println("received " + md5.asString());
//            countDownLatch.countDown();
//        });
        connection.write(requestString, () -> new ByteBufferWrapper(1024));

        try {
            Assert.assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        } finally {
            server.close();
            epoll.close();
        }
    }

//    @Test
    public void compareClients() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        final byte[] image = new byte[10 * 1024 * 1024];
        new Random().nextBytes(image);
        int multiplier = 50;
        String md5String;
        Stopwatch stopwatchMD5 = new Stopwatch("md5 hashing");
        {
            MD5 md5 = MD5.create();
            for (int i = 0; i < multiplier; i++) {
                md5.update(image);
            }
            md5String = md5.asString();
            stopwatchMD5.stop();
        }

        System.out.println(stopwatchMD5);
        int payloadSize = image.length * multiplier;
        byte[] data = ("HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: " + payloadSize + "\r\nContent-Type: image/gif\r\n\r\n").getBytes();

        int port = 8082;
        EpollServer<Connection> server = new EpollServer<Connection>(port) {
            @Override
            protected IOThread<Connection> createIOThread(int number, int divider) {
                return new IOThread<Connection>(number, divider) {
                    byte[] b = new byte[1024];

                    @Override
                    public void onRead(final Connection connection) {
                        try {
                            int r = connection.read(b, 0, b.length, this);
                            if (r == 0)
                                return;
//                            System.out.println("read: " + r);
//                            System.out.println(new String(b, 0, r));
//                            System.out.println("end");
//                            System.out.println("");
                            if (r >= 4 && b[r - 4] == '\r' && b[r - 3] == '\n' && b[r - 2] == '\r' && b[r - 1] == '\n') {
                                connection.write(data, this);
                                for (int i = 0; i < multiplier; i++) {
                                    connection.write(image, this);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                };
            }
        };

        server.setIoThreadsCount(0);
        server.loadCertificates("src/test/resources/ssl/test_cert.pem", "src/test/resources/ssl/test_key.pem");
        server.start();


        for (MemoryPoolMXBean memoryMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (!memoryMXBean.getName().contains("Old Gen") && !memoryMXBean.getName().contains("Eden"))
                continue;

            MemoryUsage usage = memoryMXBean.getUsage();
            System.out.println(memoryMXBean.getName() + ".commited " + usage.getCommitted() / 1024f / 1024 + "mb");
            System.out.println(memoryMXBean.getName() + ".used " + usage.getUsed() / 1024f / 1024 + "mb");
            System.out.println(memoryMXBean.getName() + ".max " + usage.getMax() / 1024f / 1024 + "mb");
        }

        try {
            for (int j = 0; j < 5; j++) {
                System.out.println();
                EpollCore<Connection> epoll = new EpollCore<>();
                CpuAndAllocationStats stats = new CpuAndAllocationStats(epoll);

                epoll.setIoThreadsCount(0);
                epoll.start();

                String requestString = "GET /1 HTTP/1.1\r\n" +
                        "Host: localhost:8082\r\n" +
                        "Connection: Close\r\n" +
                        "\r\n";
                CountDownLatch countDownLatch = new CountDownLatch(1);

                Thread.sleep(100); // wait until epoll thread starts to get allocation
                Stopwatch stopwatch = new Stopwatch("request");
                long allocationBefore = stats.getTotalAllocation();
                Connection connection = Unchecked.call(() -> epoll.connect("localhost", port, true, false));
//                Connection connection = Unchecked.call(() -> epoll.connect("localhost", port));

                byte[] b = new byte[8192];
                AtomicBoolean readingBody = new AtomicBoolean(false);
                AtomicInteger donwloaded = new AtomicInteger(0);
                AtomicLong hashingTime = new AtomicLong(0);
                MD5 md5 = MD5.create();
                connection.onRead((c, bp) -> {
                    int r;
                    outer:
                    while ((r = c.read(b, bp)) > 0) {
//                System.out.println("onread " + r);
                        if (!readingBody.get()) {
                            int i = 0;
                            while (i >= 0) {
                                i = indexOf(b, i + 1, r, (byte) '\r');
                                if (i < 0)
                                    continue;

                                if (i + 3 >= r)
                                    throw new IllegalStateException("Not implemented yet");

                                if (b[i + 3] == '\n' && b[i + 1] == '\n' && b[i + 2] == '\r') {
                                    readingBody.set(true);
                                    if (r >= i + 4) {
                                        int l = r - (i + 4);
                                        long timeBefore = System.nanoTime();
                                        md5.update(b, i + 4, l);
                                        long timeAfter = System.nanoTime();
                                        hashingTime.addAndGet(timeAfter - timeBefore);
                                        donwloaded.addAndGet(l);
                                    } else
                                        throw new IllegalStateException("r <= i + 4: " + r + " <= " + (i + 4));
                                    continue outer;
                                }
                            }
                        } else {
                            long timeBefore = System.nanoTime();
                            md5.update(b, 0, r);
                            long timeAfter = System.nanoTime();
                            hashingTime.addAndGet(timeAfter - timeBefore);
                            donwloaded.addAndGet(r);
                        }
                    }
                    if (donwloaded.get() == payloadSize) {
                        Assert.assertEquals(md5String, md5.asString());
                        countDownLatch.countDown();
                        stopwatch.stop();
                    }
                });
                connection.write(requestString, () -> new ByteBufferWrapper(1024));

                long allocationAfter;
                try {
                    Assert.assertTrue(countDownLatch.await(20, TimeUnit.SECONDS));
                    allocationAfter = stats.getTotalAllocation();
                } finally {
                    epoll.close();
                }
                System.out.println(stopwatch);
                System.out.println("hashing took: " + (hashingTime.get() / 1000f / 1000f) + "ms");
                float requestTime = stopwatch.getValue() - (hashingTime.get() / 1000f / 1000f);
                System.out.println("actual request time: " + requestTime + "ms");
                System.out.println("download speed: " + (payloadSize / 1024f / 1024f / (requestTime / 1000f)) + "mb/s");
                System.out.println("epoll client allocation: " + (allocationAfter - allocationBefore) / 1024f / 1024f + "mb");
//                System.out.println("allocationBefore: " + allocationBefore);
//                System.out.println("allocationAfter : " + allocationAfter);
            }


            for (int i = 0; i < 5; i++) {
                System.out.println();
                Stopwatch stopwatch = new Stopwatch("request");
                CpuAndAllocationStats stats = new CpuAndAllocationStats();
                long allocationBefore = stats.getTotalAllocation();
                TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }};
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new SecureRandom());
                Response response = HttpClient.createRequest("https://localhost:8082/1")
                        .setHostnameVerifier((s, sslSession) -> true)
                        .setSSLSocketFactory(sc.getSocketFactory())
                        .get();

                byte[] b = new byte[8192];
                InputStream stream = response.asStream();
                int r;
                MD5 md5 = MD5.create();
                long hashingTime = 0;
                while ((r = stream.read(b)) != -1) {
                    long timeBefore = System.nanoTime();
                    md5.update(b, 0, r);
                    long timeAfter = System.nanoTime();
                    hashingTime += timeAfter - timeBefore;
                }
                Assert.assertEquals(md5String, md5.asString());
                System.out.println(stopwatch);
                System.out.println("hashing took: " + (hashingTime / 1000f / 1000f) + "ms");
                float requestTime = stopwatch.getValue() - (hashingTime / 1000f / 1000f);
                System.out.println("actual request time: " + requestTime + "ms");
                System.out.println("download speed: " + (payloadSize / 1024f / 1024f / (requestTime / 1000f)) + "mb/s");
                long allocationAfter = stats.getTotalAllocation();
                System.out.println("java client allocation: " + (allocationAfter - allocationBefore) / 1024f / 1024f + "mb");
            }

        } finally {
            server.close();
        }

        System.out.println();
        for (MemoryPoolMXBean memoryMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (!memoryMXBean.getName().contains("Old Gen") && !memoryMXBean.getName().contains("Eden"))
                continue;

            MemoryUsage usage = memoryMXBean.getUsage();
            System.out.println(memoryMXBean.getName() + ".commited " + usage.getCommitted() / 1024f / 1024 + "mb");
            System.out.println(memoryMXBean.getName() + ".used " + usage.getUsed() / 1024f / 1024 + "mb");
            System.out.println(memoryMXBean.getName() + ".max " + usage.getMax() / 1024f / 1024 + "mb");
        }
    }

    static int indexOf(byte[] bytes, int offset, int length, byte searchFor) {
        length += offset;
        for (int i = offset; i < length; i++) {
            if (bytes[i] == searchFor)
                return i;
        }
        return -1;
    }

    static class BenchmarkConnection extends Connection {
        int read = 0;

        public BenchmarkConnection(int fd, int ip, int port) {
            super(fd, ip, port);
        }
    }

    //    @Test
    public void benchmark() throws IOException, InterruptedException {
//        final byte[] request = ("GET /grails2_4/ HTTP/1.1\r\n" +
        final byte[] request = ("GET / HTTP/1.1\r\n" +
                "Host: localhost:8084\r\n" +
                "Connection: Keep-Alive\r\n" +
//                "Connection: Close\r\n" +
                "\r\n").getBytes();
        final AtomicInteger counter = new AtomicInteger();

        final byte[] b = new byte[1024];
        final EpollCore<BenchmarkConnection> epoll = new EpollCore<BenchmarkConnection>() {
            @Override
            protected BenchmarkConnection createConnection(int fd, int ip, int port) {
                return new BenchmarkConnection(fd, ip, port);
            }

            @Override
            protected IOThread<BenchmarkConnection> createIOThread(int number, int divider) {
                return new IOThread<BenchmarkConnection>(number, divider) {
                    @Override
                    public void onRead(BenchmarkConnection connection) {
//                System.out.println("onRead " + connection);
                        int r = 0;

                        try {
                            r = connection.read(b, 0, b.length, this);
                        } catch (IOException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
//                System.out.println(new String(b, 0, r));
//                System.out.println("length: "+r);
                        connection.read += r;
//                if (connection.read >= 170) {
                        if (connection.read >= 113) {
                            counter.incrementAndGet();
                            connection.read = 0;
                            connection.write(request, this);
//                    connection.epoll.close(connection);
                        }

                    }
                };
            }
        };

        epoll.start();
        int c = 64;
        for (int i = 0; i < c; i++) {
            Connection connection = epoll.connect("localhost", 8084);
            connection.write(request, new ByteBufferProvider() {
                @Override
                public ByteBufferWrapper getBuffer() {
                    return new ByteBufferWrapper(1024);
                }
            });
        }
//        for (int i = 0; i < c; i++) {
//            Thread t = new Thread() {
//                @Override
//                public void run() {
//                    while (true) {
//                        try {
//                            Connection connection = epoll.connect("localhost", 8084);
//                            connection.write(request);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//            };
//            t.setDaemon(true);
//            t.start();
//        }

        int time = 10;
        Thread.sleep(time * 1000);
        System.out.println("total requests: " + counter.get());
        System.out.println("rps: " + counter.get() * 1f / time);
    }
}
