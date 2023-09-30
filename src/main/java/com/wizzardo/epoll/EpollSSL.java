package com.wizzardo.epoll;

import java.io.IOException;

public class EpollSSL {

    public static final boolean SUPPORTED;

    static {
        boolean supported = false;
        try {
            Utils.loadLib("libepoll-ssl");
            supported = true;
            System.out.println("epoll-ssl lib loaded");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("openssl is not supported");
        }
        SUPPORTED = supported;
        if (supported)
            initLib();
    }

    public static class SSLClientContext {
        public static final long SSL_CLIENT_CONTEXT_POINTER = initSSLClient(true);
    }

    public static class SSLClientContextUntrusted {
        public static final long SSL_CLIENT_CONTEXT_POINTER = initSSLClient(false);
    }

    private native static void initLib();

    native static long initSSL();

    native static long initSSLClient(boolean verifyCertValidity);

    native static long createSSL(long scope, int fd);

    native static void closeSSL(long ssl);

    native static boolean acceptSSL(long ssl);

    native static void releaseSslContext(long scope);

    native static void loadCertificates(long scope, String certFile, String keyFile);

    native static int readSSL(int fd, long bbPointer, int off, int lenm, long ssl) throws IOException;

    native static int writeSSL(int fd, long bbPointer, int off, int len, long ssl) throws IOException;

    native static boolean connect(long ssl) throws IOException;
}
