#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include "com_wizzardo_epoll_EpollCore.h"
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/epoll.h>
#include <errno.h>
#include <netinet/in.h>
#include <arpa/inet.h>


#define MAXEVENTS 1024

struct Scope {
    int maxEvents;
    int sfd;
    int efd;
    jbyte* jEvents;
    struct epoll_event event;
    struct epoll_event *events;
};


static int create_and_bind(const char *host, const char *port)
{
    struct addrinfo hints;
    struct addrinfo *result, *rp;
    int s, sfd, on;

    memset(&hints, 0, sizeof(struct addrinfo));
    hints.ai_family = AF_UNSPEC;    /* Return IPv4 and IPv6 choices */
    hints.ai_socktype = SOCK_STREAM;    /* We want a TCP socket */
    hints.ai_flags = AI_PASSIVE;    /* All interfaces, will be ignored if host is not null */

    s = getaddrinfo(host, port, &hints, &result);
    if (s != 0)
    {
        fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(s));
        return -1;
    }
    fprintf(stderr, "bind on: %s:%s\n", (host == NULL? "ANY":host), port);

    for (rp = result; rp != NULL; rp = rp->ai_next)
    {
        sfd = socket(rp->ai_family, rp->ai_socktype, rp->ai_protocol);
        if (sfd == -1)
            continue;

        on = 1;
        s =  setsockopt(sfd, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on));
        if (s != 0) {
            fprintf(stderr, "can not set SO_REUSEADDR: %s\n", gai_strerror(s));
            return -1;
        }

        s = bind(sfd, rp->ai_addr, rp->ai_addrlen);
        if (s == 0)
        {
            /* We managed to bind successfully! */
            break;
        }

        close(sfd);
    }

    if (rp == NULL)
    {
        fprintf(stderr, "Could not bind\n");
        return -1;
    }

    freeaddrinfo(result);

    fprintf(stderr, "Core fd: %d\n", sfd);
    return sfd;
}

static int make_socket_non_blocking(int sfd)
{
    int flags, s;

    flags = fcntl(sfd, F_GETFL, 0);
    if (flags == -1)
    {
        perror("fcntl");
        return -1;
    }

    flags |= O_NONBLOCK;
    s = fcntl(sfd, F_SETFL, flags);
    if (s == -1)
    {
        perror("fcntl");
        return -1;
    }

    return 0;
}

static void intToBytes(int i, char* b){
    b[0] = (i >> 24) & 0xff;
    b[1] = (i >> 16) & 0xff;
    b[2] = (i >> 8) & 0xff;
    b[3] = (i) & 0xff;
}

JNIEXPORT jint JNICALL Java_com_wizzardo_epoll_EpollCore_waitForEvents(JNIEnv *env, jobject obj, jlong scopePointer, jint timeout)
{
    int n, i, s, j = 0;
    struct Scope *scope = (struct Scope *)scopePointer;
    struct epoll_event *events = scope->events;
    struct epoll_event event = scope->event;
    int sfd = scope->sfd;
    int efd = scope->efd;
    jbyte *jEvents = scope->jEvents;

    if(timeout>0)
        n = epoll_wait(efd, events, scope->maxEvents, timeout);
    else
        n = epoll_wait(efd, events, scope->maxEvents, -1);

//    fprintf(stderr, "get %d events\n", n);
    for (i = 0; i < n; i++)
    {
        if ((events[i].events & EPOLLERR) || (events[i].events & EPOLLHUP) || (events[i].events & EPOLLRDHUP) || (!(events[i].events & EPOLLIN) && !(events[i].events & EPOLLOUT)))
        {
            /* An error has occured on this fd, or the socket is not
               ready for reading (why were we notified then?) */
//            fprintf(stderr, "connection closed for fd %d, event: %d\n", events[i].data.fd, events[i].events);
//
//            int       error = 0;
//            socklen_t errlen = sizeof(error);
//            if (getsockopt(events[i].data.fd, SOL_SOCKET, SO_ERROR, (void *)&error, &errlen) == 0)
//            {
//                printf("error = %s\n", strerror(error));
//            }

            close(events[i].data.fd);

            jEvents[j] = 3;  // close connection
            j ++;
            intToBytes(events[i].data.fd, &jEvents[j]);
            j += 4;

            continue;
        }

        else if (sfd == events[i].data.fd)
        {
            /* We have a notification on the listening socket, which
               means one or more incoming connections. */
            while (1)
            {
                struct sockaddr addr;
                socklen_t in_len;
                int infd;

                in_len = sizeof addr;
                infd = accept(sfd, &addr, &in_len);
                if (infd == -1)
                {
                    if ((errno == EAGAIN) || (errno == EWOULDBLOCK))
                    {
                        /* We have processed all incoming
                           connections. */
                        break;
                    }
                    else
                    {
                        perror("accept");
                        break;
                    }
                }
//                fprintf(stderr, "new connection from  %d %d %d %d %d %d %d %d %d %d %d %d %d %d\n", addr.sa_data[0], addr.sa_data[1], addr.sa_data[2], addr.sa_data[3], addr.sa_data[4], addr.sa_data[5], addr.sa_data[6], addr.sa_data[7], addr.sa_data[8], addr.sa_data[9], addr.sa_data[10], addr.sa_data[11], addr.sa_data[12], addr.sa_data[13]);

//                int port = (addr.sa_data[0] < 0 ? 256 + addr.sa_data[0] : addr.sa_data[0]) << 8;
//                port += (addr.sa_data[1] < 0 ? 256 + addr.sa_data[1] : addr.sa_data[1]);
//
//                int ip = (addr.sa_data[2] < 0 ? 256 + addr.sa_data[2] : addr.sa_data[2]) << 24;
//                ip += (addr.sa_data[3] < 0 ? 256 + addr.sa_data[3] : addr.sa_data[3]) << 16;
//                ip += (addr.sa_data[4] < 0 ? 256 + addr.sa_data[4] : addr.sa_data[4]) << 8;
//                ip += (addr.sa_data[5] < 0 ? 256 + addr.sa_data[5] : addr.sa_data[5]);
//                fprintf(stderr, "new connection from  %d %d %d \n",infd, ip, port);

                s = make_socket_non_blocking(infd);
                if (s == -1)
                    abort();

                event.data.fd = infd;
                event.events = EPOLLIN | EPOLLET | EPOLLERR | EPOLLHUP | EPOLLRDHUP;
                s = epoll_ctl(efd, EPOLL_CTL_ADD, infd, &event);
                if (s == -1)
                {
                    perror("epoll_ctl");
                    abort();
                }


                jEvents[j] = 0; // 0 - new connection
                j ++;
                intToBytes(infd, &jEvents[j]);
                j += 4;

                jEvents[j] = addr.sa_data[2];
                jEvents[j+1] = addr.sa_data[3];
                jEvents[j+2] = addr.sa_data[4];
                jEvents[j+3] = addr.sa_data[5];
                j += 4;

                jEvents[j] = addr.sa_data[0];
                jEvents[j+1] = addr.sa_data[1];
                j += 2;
            }
            continue;
        }
        else
        {
//                fprintf(stderr, "ready to ");
//                if(events[i].events & EPOLLOUT)
//                    fprintf(stderr, "write");
//                else
//                    fprintf(stderr, "read");
//
//                fprintf(stderr, " data on descriptor %d\n", events[i].data.fd);


            jEvents[j] = (events[i].events & EPOLLOUT) ? 2 : 1; // 2-write; 1-read
            j ++;
            intToBytes(events[i].data.fd, &jEvents[j]);
            j += 4;
        }
    }

    return j;
}

void throwException(JNIEnv *env, char *message, jstring file)
{
    jclass exc = (*env)->FindClass(env, "java/io/IOException");
    jmethodID constr = (*env)->GetMethodID(env, exc, "<init>", "(Ljava/lang/String;)V");
    jstring str = (*env)->NewStringUTF(env, message);
    jthrowable t = (jthrowable) (*env)->NewObject(env, exc, constr, str, file);
    (*env)->Throw(env, t);
}


JNIEXPORT void JNICALL Java_com_wizzardo_epoll_EpollCore_startWriting(JNIEnv *env, jobject obj, jlong scopePointer, jint fd)
{
    int s;
    struct epoll_event e;
    struct Scope *scope = (struct Scope *)scopePointer;

    e.data.fd = fd;
    e.events = EPOLLIN | EPOLLET | EPOLLOUT | EPOLLERR | EPOLLHUP | EPOLLRDHUP;
    s = epoll_ctl((*scope).efd, EPOLL_CTL_MOD, fd, &e);
    if (s == -1)
    {
        throwException(env, strerror(errno), NULL);
        perror("epoll_ctl");
        abort();
    }
}

JNIEXPORT void JNICALL Java_com_wizzardo_epoll_EpollCore_stopWriting(JNIEnv *env, jobject obj, jlong scopePointer, jint fd)
{
    int s;
    struct epoll_event e;
    struct Scope *scope = (struct Scope *)scopePointer;

    e.data.fd = fd;
    e.events = EPOLLIN | EPOLLET | EPOLLERR | EPOLLHUP | EPOLLRDHUP;
    s = epoll_ctl((*scope).efd, EPOLL_CTL_MOD, fd, &e);
    if (s == -1)
    {
        throwException(env, strerror(errno), NULL);
        perror("epoll_ctl");
        abort();
    }
}


JNIEXPORT jint JNICALL Java_com_wizzardo_epoll_EpollCore_read(JNIEnv *env, jclass clazz, jint fd, jlong bb, jint offset, jint length)
{
    jbyte *buf =(jbyte *) bb;
    if (buf == NULL)
    {
        return -1;
    }

    ssize_t count = read(fd, &(buf[offset]), length);

    if (count == 0)
    {
        // read(2) returns 0 on EOF. Java returns -1.
        return -1;
    }
    else if (count == -1)
    {
        int err = errno;
        if (err != EAGAIN)
            throwException(env, strerror(err), NULL);
        return -1;
    }

    return count;
}


JNIEXPORT jint JNICALL Java_com_wizzardo_epoll_EpollCore_write(JNIEnv *env, jclass clazz, jint fd, jlong bb, jint offset, jint length)
{
    jbyte *buf = (jbyte *) bb;
    if (buf == NULL)
    {
        return;
    }
    int total = 0;
    int s = 0;

    while (total != length)
    {
        s = write(fd, &(buf[offset + total]), length - total);
//                fprintf(stderr,"writed: %d\ttotal: %d\tfrom %d\n", s, total+(s>0?s:0),length);
        if (s == -1)
        {
            int err = errno;
            if (err != EAGAIN)
                throwException(env, strerror(err), NULL);
            return total;
        }
        total += s;
    }

    return total;
}


JNIEXPORT void JNICALL Java_com_wizzardo_epoll_EpollCore_close(JNIEnv *env, jobject obj, jint fd)
{
//    shutdown(fd, SHUT_RDWR);
    close(fd);
}

JNIEXPORT jboolean JNICALL Java_com_wizzardo_epoll_EpollCore_stopListening(JNIEnv *env, jobject obj, jlong scopePointer)
{
    struct Scope *scope = (struct Scope *)scopePointer;
    free(scope->events);
    int s = close(scope->sfd);
    free(scope);
    return  s  == 0;
}


JNIEXPORT jint JNICALL Java_com_wizzardo_epoll_EpollCore_connect(JNIEnv *env, jobject obj, jlong scopePointer, jstring host, jint port)
{
    struct Scope *scope = (struct Scope *)scopePointer;
    struct epoll_event event = scope->event;
    int efd = scope->efd;

    const char *hhost = (*env)->GetStringUTFChars(env, host, NULL);
    const char ip[15];
    hostnameToIp(hhost, ip);

    int tcp_socket;
    if((tcp_socket = socket(AF_INET, SOCK_STREAM, 0)) < 0){
        printf("Error : Could not create socket \n");
        throwException(env, strerror(errno), NULL);
        return -1;
    }

    struct sockaddr_in serv_addr;
    memset(&serv_addr, '0', sizeof(serv_addr));

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);

    if(inet_pton(AF_INET, ip, &serv_addr.sin_addr)<=0){
        printf("inet_pton error occured\n");
        throwException(env, strerror(errno), NULL);
        return -1;
    }

    if(connect(tcp_socket, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0){
        printf("Error : Connect Failed \n");
        throwException(env, strerror(errno), NULL);
        return -1;
    }

    if (make_socket_non_blocking(tcp_socket) < 0){
        throwException(env, strerror(errno), NULL);
        return -1;
    }

    event.data.fd = tcp_socket;
    event.events = EPOLLIN | EPOLLET | EPOLLERR | EPOLLHUP | EPOLLRDHUP;
    if (epoll_ctl(efd, EPOLL_CTL_ADD, tcp_socket, &event) < 0){
        throwException(env, strerror(errno), NULL);
        return -1;
    }

    return tcp_socket;
}

int hostnameToIp(char *hostname, char *ip)
{
    struct addrinfo hints, *servinfo, *p;
    struct sockaddr_in *h;
    int rv;

    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC; // use AF_INET6 to force IPv6
    hints.ai_socktype = SOCK_STREAM;

    if ( (rv = getaddrinfo( hostname , NULL , &hints , &servinfo)) != 0)
    {
        printf("getaddrinfo: %s\n", gai_strerror(rv));
        return 1;
    }

    // loop through all the results and connect to the first we can
    for(p = servinfo; p != NULL; p = p->ai_next)
    {
        h = (struct sockaddr_in *) p->ai_addr;
        strcpy(ip , inet_ntoa( h->sin_addr ) );
    }

    freeaddrinfo(servinfo); // all done with this structure
    return 0;
}

JNIEXPORT jlong JNICALL Java_com_wizzardo_epoll_EpollCore_listen(JNIEnv *env, jobject obj, jstring host, jstring port, jint maxEvents, jobject bb)
{
    const char *pport = (*env)->GetStringUTFChars(env, port, NULL);
    const char *hhost = host == NULL? NULL:((*env)->GetStringUTFChars(env, host, NULL));

    int sfd = create_and_bind(hhost, pport);
    if (sfd == -1)
        abort();

    int s = make_socket_non_blocking(sfd);
    if (s == -1)
        abort();

    s = listen(sfd, SOMAXCONN);
    if (s == -1)
    {
        perror("listen");
        abort();
    }

    int efd = epoll_create1(0);
    if (efd == -1)
    {
        perror("epoll_create");
        abort();
    }

    struct epoll_event event;
    event.data.fd = sfd;
    event.events = EPOLLIN | EPOLLET;
    s = epoll_ctl(efd, EPOLL_CTL_ADD, sfd, &event);
    if (s == -1)
    {
        perror("epoll_ctl");
        abort();
    }

    struct Scope *scope;
    scope = (struct Scope *)malloc(sizeof(struct Scope));

    /* Buffer where events are returned */
    (*scope).events = calloc(maxEvents, sizeof event);
    (*scope).event = event;
    (*scope).sfd = sfd;
    (*scope).efd = efd;
    (*scope).maxEvents = maxEvents;
    (*scope).jEvents = (*env)->GetDirectBufferAddress(env, bb);

    long lp = (long)scope;
    return lp;
}

JNIEXPORT jlong JNICALL Java_com_wizzardo_epoll_EpollCore_getAddress(JNIEnv *env, jclass cl, jobject bb){
    return (long) (*env)->GetDirectBufferAddress(env, bb);
}