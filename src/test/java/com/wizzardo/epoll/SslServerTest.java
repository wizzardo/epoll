package com.wizzardo.epoll;

import com.wizzardo.tools.http.HttpClient;
import com.wizzardo.tools.security.MD5;
import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Random;

/**
 * Created by wizzardo on 12.05.15.
 */
public class SslServerTest {
    @Test
    public void httpsTest() throws InterruptedException, IOException, NoSuchAlgorithmException, KeyManagementException {
        int port = 8084;
//        final byte[] image = FileTools.bytes("/home/wizzardo/interface.gif");
        final byte[] image = new byte[25 * 1024 * 1024];
        new Random().nextBytes(image);
        EpollServer<SecuredConnection> server = new EpollServer<SecuredConnection>(port) {

            @Override
            protected SecuredConnection createConnection(int fd, int ip, int port) {
                return new SecuredConnection(fd, ip, port) {
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
            protected IOThread<SecuredConnection> createIOThread(int number, int divider) {
                return new IOThread<SecuredConnection>(number, divider) {

                    byte[] b = new byte[1024];

                    @Override
                    public void onRead(final SecuredConnection connection) {
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
        server.loadCertificates("/home/wizzardo/ssl_server/test_cert.pem", "/home/wizzardo/ssl_server/test_key.pem");

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

        server.stopEpoll();
    }
}
