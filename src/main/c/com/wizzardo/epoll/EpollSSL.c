#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include "com_wizzardo_epoll_EpollSSL.h"
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/epoll.h>
#include <errno.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netinet/tcp.h>
#include <openssl/ssl.h>
#include <openssl/err.h>

void throwException(JNIEnv *env, char *message) {
    fprintf(stderr, "throwException %d: %s\n", errno, message);
    throwException2(env, message, "java/io/IOException");
}

void throwException2(JNIEnv *env, char *message, char *clazz) {
    fprintf(stderr, "throwException2 %d: %s\n", errno, message);
    jclass exc = (*env)->FindClass(env, clazz);
    jmethodID constr = (*env)->GetMethodID(env, exc, "<init>", "(Ljava/lang/String;)V");
    jstring str = (*env)->NewStringUTF(env, message);
    jthrowable t = (jthrowable) (*env)->NewObject(env, exc, constr, str, NULL);
    (*env)->Throw(env, t);
}

JNIEXPORT void JNICALL Java_com_wizzardo_epoll_EpollSSL_initLib(JNIEnv *env, jclass clazz) {
    SSL_library_init();
    OpenSSL_add_all_algorithms();		/* load & register all cryptos, etc. */
    SSL_load_error_strings();			/* load all error messages */
}

JNIEXPORT jlong JNICALL Java_com_wizzardo_epoll_EpollSSL_initSSL(JNIEnv *env, jclass clazz) {
    SSL_METHOD *method;
    SSL_CTX *ctx;

    method = SSLv23_server_method();	/* create new server-method instance */
    ctx = SSL_CTX_new(method);			/* create new context from method */
    if (ctx == NULL) {
        ERR_print_errors_fp(stderr);
        abort();
    }
	SSL_CTX_set_mode(ctx, SSL_MODE_ENABLE_PARTIAL_WRITE | SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER | SSL_MODE_RELEASE_BUFFERS);
    SSL_CTX_set_options(ctx, SSL_OP_NO_SSLv2);
    return (long) ctx;
}

JNIEXPORT jlong JNICALL Java_com_wizzardo_epoll_EpollSSL_initSSLClient(JNIEnv *env, jclass clazz, jboolean verifyCertValidity) {
    SSL_CTX *ctx;

    ctx = SSL_CTX_new(SSLv23_client_method());
    if (ctx == NULL) {
        ERR_print_errors_fp(stderr);
        abort();
    }

    if (verifyCertValidity) {
        SSL_CTX_set_verify(ctx, SSL_VERIFY_PEER, NULL);
    }

    // Load system default CA certificates
    if (SSL_CTX_set_default_verify_paths(ctx) != 1) {
        ERR_print_errors_fp(stderr);
    }

	SSL_CTX_set_mode(ctx, SSL_MODE_ENABLE_PARTIAL_WRITE | SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER | SSL_MODE_RELEASE_BUFFERS);
    SSL_CTX_set_options(ctx, SSL_OP_NO_SSLv2);
    return (long) ctx;
}

JNIEXPORT jlong JNICALL Java_com_wizzardo_epoll_EpollSSL_createSSL(JNIEnv *env, jclass clazz, jlong sslContextPointer, jint fd){
    SSL_CTX *ctx = (SSL_CTX *) sslContextPointer;
    SSL *ssl = SSL_new(ctx);
    SSL_set_fd(ssl, fd);
    return (long) ssl;
}

JNIEXPORT jboolean JNICALL Java_com_wizzardo_epoll_EpollSSL_acceptSSL(JNIEnv *env, jclass clazz, jlong sslPointer){
    SSL *ssl = (SSL *) sslPointer;
    errno = 0;
    int s = SSL_accept(ssl);					/* do SSL-protocol accept */
    if (errno != 0) {
        if(errno == 11)
            return JNI_FALSE;
//        fprintf(stderr, "result: %d, errno: %d\n", s, errno);
        ERR_print_errors_fp(stderr);
        throwException(env, strerror(errno));
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_wizzardo_epoll_EpollSSL_loadCertificates(JNIEnv *env, jclass clazz, jlong sslContextPointer, jstring certFile, jstring keyFile){
    SSL_CTX *ctx = (SSL_CTX *) sslContextPointer;
    const char *CertFile = ((*env)->GetStringUTFChars(env, certFile, NULL));
    const char *KeyFile = ((*env)->GetStringUTFChars(env, keyFile, NULL));

	/* set the local certificate from CertFile */
    if (SSL_CTX_use_certificate_file(ctx, CertFile, SSL_FILETYPE_PEM) <= 0) {
        ERR_print_errors_fp(stderr);
        throwException(env, strerror(errno));
    }
    /* set the private key from KeyFile (may be the same as CertFile) */
    if (SSL_CTX_use_PrivateKey_file(ctx, KeyFile, SSL_FILETYPE_PEM) <= 0) {
        ERR_print_errors_fp(stderr);
        throwException(env, strerror(errno));
    }
    /* verify private key */
    if (!SSL_CTX_check_private_key(ctx)) {
//        fprintf(stderr, "Private key does not match the public certificate\n");
        throwException(env, strerror(errno));
    }
}

JNIEXPORT jint JNICALL Java_com_wizzardo_epoll_EpollSSL_readSSL(JNIEnv *env, jclass clazz, jint fd, jlong bb, jint offset, jint length, jlong sslPointer) {
    jbyte *buf =(jbyte *) bb;
    SSL *ssl = (SSL *) sslPointer;

    errno = 0;
    int count = SSL_read(ssl, &(buf[offset]), length);

    if (count == 0) {
        if (errno > 0 && errno != 11)
            throwException(env, strerror(errno));
        // read(2) returns 0 on EOF. Java returns -1.
        return -1;
    } else if (count == -1) {
        if (errno > 0 && errno != 11)
            throwException(env, strerror(errno));
        return -1;
    }

    return count;
}

JNIEXPORT jint JNICALL Java_com_wizzardo_epoll_EpollSSL_writeSSL(JNIEnv *env, jclass clazz, jint fd, jlong bb, jint offset, jint length, jlong sslPointer) {
    jbyte *buf = (jbyte *) bb;
    SSL *ssl = (SSL *) sslPointer;

    int total = 0;
    int s = 0;

    while (total != length) {
        errno = 0;
        s = SSL_write(ssl, &(buf[offset + total]), length - total);
//                fprintf(stderr,"writed: %d\ttotal: %d\tfrom %d\n", s, total+(s>0?s:0),length);
        if (s == -1) {
            int err = errno;
            if (err != EAGAIN)
                throwException(env, strerror(err));
            return total;
        }

        int err = errno;
        if (err > 0 && err != EAGAIN)
            throwException(env, strerror(err));
        total += s;
    }

    return total;
}

JNIEXPORT void JNICALL Java_com_wizzardo_epoll_EpollSSL_closeSSL(JNIEnv *env, jclass clazz, jlong sslPointer) {
    SSL *ssl = (SSL *) sslPointer;
    SSL_shutdown(ssl);
    SSL_free(ssl);									/* release SSL state */
}

JNIEXPORT void JNICALL Java_com_wizzardo_epoll_EpollSSL_releaseSslContext(JNIEnv *env, jclass clazz, jlong sslContextPointer) {
    SSL_CTX *ctx = (SSL_CTX *) sslContextPointer;
    SSL_CTX_free(ctx);									/* release context */
}


JNIEXPORT jboolean JNICALL Java_com_wizzardo_epoll_EpollSSL_connect(JNIEnv *env, jclass clazz, jlong sslPointer) {
    SSL *ssl = (SSL *) sslPointer;
    errno = 0;
    int result = SSL_connect(ssl);
    if (result != 1) {
        if(errno == 11)
            return JNI_FALSE;
        fprintf(stderr, "connect: %d, errno: %d\n", result, errno);
//        fprintf(stderr, "connect.error: %d: %s\n", errno, strerror(errno));
//        int ssl_error = SSL_get_error(ssl, result);
//        fprintf(stderr, "connect.ssl: %d: %d\n", errno, ssl_error);

        unsigned long err;
        const char* error_string;

        if ((err = ERR_get_error()) != 0) {
            error_string = ERR_error_string(err, NULL);
            printf("OpenSSL Error: %s\n", error_string);
            throwException(env, error_string);
            return JNI_FALSE;
        }

//        char* error_string error_string = ERR_error_string(ssl_error, NULL);
//        printf("OpenSSL Error: %s\n", error_string);
//        ERR_print_errors_fp(stderr);
        throwException(env, strerror(errno));
        fprintf(stderr, "connect2: %d, errno: %d\n", result, errno);
        return JNI_FALSE;
    }

    long ssl_verify_result = SSL_get_verify_result(ssl);
    fprintf(stderr, "ssl_verify_result: %d\n", ssl_verify_result);

    return JNI_TRUE;
}