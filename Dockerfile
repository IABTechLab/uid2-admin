# sha from https://hub.docker.com/layers/library/eclipse-temurin/21.0.9_10-jre-alpine-3.23/images/sha256-f599f6fa11f007b6dcf6e85ec2c372c1eba2b6940a7828eb6e665665ea5edd1c
FROM eclipse-temurin@sha256:243e711289b0f17e05a4df60454bbb1b8ed7b126db4de2d5535da994b7417111

WORKDIR /app
EXPOSE 8089

ARG JAR_NAME=uid2-admin
ARG JAR_VERSION=1.0.0-SNAPSHOT
ARG IMAGE_VERSION=1.0.0.unknownhash
ENV JAR_NAME=${JAR_NAME}
ENV JAR_VERSION=${JAR_VERSION}
ENV IMAGE_VERSION=${IMAGE_VERSION}

COPY ./target/${JAR_NAME}-${JAR_VERSION}-jar-with-dependencies.jar /app/${JAR_NAME}-${JAR_VERSION}.jar
COPY ./target/${JAR_NAME}-${JAR_VERSION}-sources.jar /app
COPY ./conf/default-config.json /app/conf/
COPY ./conf/*.xml /app/conf/
COPY ./webroot/ /app/webroot/

RUN apk add --no-cache --upgrade libpng && adduser -D uid2-admin && mkdir -p /app && chmod 705 -R /app && mkdir -p /app/file-uploads && chmod 777 -R /app/file-uploads
USER uid2-admin

CMD java \
    -Djava.security.egd=file:/dev/./urandom \
    -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory \
    -Dlogback.configurationFile=/app/conf/logback.xml \
    -Xmx4g \
    -jar ${JAR_NAME}-${JAR_VERSION}.jar
