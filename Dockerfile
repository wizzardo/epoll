
FROM buildpack-deps:jammy

ARG TARGETPLATFORM
RUN echo '===========================SETTING UP JAVA FOR '$TARGETPLATFORM'=========================='

# Since the java is being downloaded from a source, its essential to download the specific version meant
#for the targetplatform read https://nielscautaerts.xyz/making-dockerfiles-architecture-independent.html

RUN if [ "$TARGETPLATFORM" = "linux/amd64" ]; then ARCHITECTURE='https://cdn.azul.com/zulu/bin/zulu13.46.15-ca-jdk13.0.10-linux_x64.tar.gz'; elif [ "$TARGETPLATFORM" = "linux/arm64" ]; then ARCHITECTURE='https://cdn.azul.com/zulu-embedded/bin/zulu13.46.15-ca-jdk13.0.10-linux_aarch64.tar.gz'; else ARCHITECTURE='https://cdn.azul.com/zulu/bin/zulu13.46.15-ca-jdk13.0.10-linux_x64.tar.gz'; fi \
    && curl -fSsL   "${ARCHITECTURE}" -o /tmp/openjdk13.tar.gz \
    && mkdir /usr/local/openjdk13  \
    && tar xzf /tmp/openjdk13.tar.gz  -C /usr/local/openjdk13 --strip-components=1 \
    && ln -s /usr/local/openjdk13/bin/javac /usr/local/bin/javac  \
    && ln -s /usr/local/openjdk13/bin/java /usr/local/bin/java  \
    && ln -s /usr/local/openjdk13/bin/jar /usr/local/bin/jar \
    && ln -s /usr/local/openjdk13/bin/javah /usr/local/bin/javah

RUN set -xe && \
    apt-get update && \
    apt-get install -y --no-install-recommends git libcap-dev && \
    git clone https://github.com/wizzardo/epoll.git /tmp/epoll && \
    cd /tmp/epoll && \
    git checkout 6788310

RUN wget -q https://services.gradle.org/distributions/gradle-7.0-bin.zip \
    && unzip gradle-7.0-bin.zip -d /opt \
    && rm gradle-7.0-bin.zip

# Set Gradle in the environment variables
ENV JAVA_HOME /usr/local/openjdk13
ENV GRADLE_HOME /opt/gradle-7.0
ENV PATH $PATH:/opt/gradle-7.0/bin:$JAVA_HOME/bin

