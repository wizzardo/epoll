package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableData;
import com.wizzardo.tools.http.*;
import com.wizzardo.tools.security.MD5;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by wizzardo on 12.05.15.
 */
public class SslServerTest {

    @Test
    public void isSupported() {
        Assert.assertTrue(EpollSSL.SUPPORTED);
    }

    @Test
    public void httpsTest() throws InterruptedException, IOException, NoSuchAlgorithmException, KeyManagementException {
        int port = 8084;
//        final byte[] image = FileTools.bytes("/home/wizzardo/interface.gif");
        final byte[] image = new byte[25 * 1024 * 1024];
        new Random().nextBytes(image);
        EpollServer<Connection> server = new EpollServer<Connection>(port) {

            @Override
            protected Connection createConnection(int fd, int ip, int port) {
                return new Connection(fd, ip, port) {
//                    @Override
//                    public void onWriteData(ReadableData readable, boolean hasMore) {
//                        if (readable.length() > 1000)
//                            try {
//                                close();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                    }
                };
            }


            //            byte[] data = "HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: 5\r\nContent-Type: text/html;charset=UTF-8\r\n\r\nololo".getBytes();
            byte[] data = ("HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: " + image.length + "\r\nContent-Type: image/gif\r\n\r\n").getBytes();
//            byte[] data = ("HTTP/1.1 200 OK\r\nConnection: Close\r\nContent-Length: " + image.length + "\r\nContent-Type: image/gif\r\n\r\n").getBytes();

//                        byte[] response = "HTTP/1.1 200 OK\r\nConnection: Close\r\nContent-Length: 5\r\nContent-Type: text/html;charset=UTF-8\r\n\r\nololo".getBytes();
//            ReadableByteBuffer response = new ReadableByteBuffer(new ByteBufferWrapper(data));

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
        server.setIoThreadsCount(4);
        server.loadCertificates("src/test/resources/ssl/test_cert.pem", "src/test/resources/ssl/test_key.pem");

        server.start();

        Thread.sleep(100);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] arr = new byte[1];
        int r = 0;

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
        InputStream in = HttpClient.createRequest("https://localhost:" + port + "/").setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        }).setSSLSocketFactory(sc.getSocketFactory()).get().asStream();

        int read = 0;
        while ((r = in.read(arr)) > 0) {
            out.write(arr);
            read += r;
            if (read >= image.length / 10) {
                Thread.sleep(2000);
                read = 0;
            }
        }
        Assert.assertEquals(MD5.create().update(image).asString(), MD5.create().update(out.toByteArray()).asString());
//        Thread.sleep(25 * 60 * 1000);

        server.close();
    }

    @Test
    public void testCloseReadable() throws NoSuchAlgorithmException, KeyManagementException {
        int port = 9090;
        final AtomicInteger onClose = new AtomicInteger();
        final AtomicInteger onCloseResource = new AtomicInteger();

        EpollServer server = new EpollServer(port) {
            @Override
            protected IOThread createIOThread(int number, int divider) {
                return new IOThread(number, divider) {
                    @Override
                    public void onDisconnect(Connection connection) {
                        onClose.incrementAndGet();
                    }

                    @Override
                    public void onConnect(Connection connection) {
                        byte[] data = ("HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: " + (1024 * 1024 * 1024) + "\r\nContent-Type: image/gif\r\n\r\n").getBytes();
                        connection.write(data, this);
                        connection.write(new ReadableData() {
                            long total = 1024 * 1024 * 1024;
                            int complete;

                            @Override
                            public int read(ByteBuffer byteBuffer) {
                                int l = byteBuffer.limit();
                                complete += l;
                                return l;
                            }

                            @Override
                            public void unread(int i) {
                                complete -= i;
                            }

                            @Override
                            public boolean isComplete() {
                                return remains() == 0;
                            }

                            @Override
                            public long complete() {
                                return complete;
                            }

                            @Override
                            public long length() {
                                return total;
                            }

                            @Override
                            public long remains() {
                                return total - complete;
                            }

                            @Override
                            public void close() throws IOException {
                                onCloseResource.incrementAndGet();
                            }
                        }, this);
                    }
                };
            }
        };
        server.setIoThreadsCount(1);
        server.setTTL(500);
        server.loadCertificates("src/test/resources/ssl/test_cert.pem", "src/test/resources/ssl/test_key.pem");

        server.start();
        try {
            int pause = 1600;
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
            Response response = HttpClient.createRequest("https://localhost:" + port + "/").setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            }).setSSLSocketFactory(sc.getSocketFactory()).get();
            Thread.sleep(pause);
            Assert.assertEquals("image/gif", response.header("Content-Type"));
            Assert.assertEquals(1, onClose.get());
            Assert.assertEquals(1, onCloseResource.get());

        } catch (IOException e) {
            e.printStackTrace();
            assert e == null;
        } catch (InterruptedException e) {
            e.printStackTrace();
            assert e == null;
        }
        server.close();
    }
}
