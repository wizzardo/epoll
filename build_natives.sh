#!/usr/bin/env bash
set -e

docker buildx build --platform linux/386 -t epoll -f Dockerfile-x32 .
docker create -ti --name dummy epoll bash
docker cp dummy:/tmp/epoll/build/libepoll-core_x32.so build/
docker cp dummy:/tmp/epoll/build/libepoll-ssl_x32.so build/
docker rm -f dummy

docker buildx build --platform linux/amd64 -t epoll -f Dockerfile-x64 .
docker create -ti --name dummy epoll bash
docker cp dummy:/tmp/epoll/build/libepoll-core_x64.so build/
docker cp dummy:/tmp/epoll/build/libepoll-ssl_x64.so build/
docker rm -f dummy

docker buildx build --platform linux/arm64 -t epoll -f Dockerfile-aarch64 .
docker create -ti --name dummy epoll bash
docker cp dummy:/tmp/epoll/build/libepoll-core_aarch64.so build/
docker cp dummy:/tmp/epoll/build/libepoll-ssl_aarch64.so build/
docker rm -f dummy