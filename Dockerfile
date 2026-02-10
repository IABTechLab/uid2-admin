# sha from https://hub.docker.com/layers/library/eclipse-temurin/21-jre-alpine-3.23/images/sha256-693c22ea458d62395bac47a2da405d0d18c77b205211ceec4846a550a37684b6
FROM eclipse-temurin@sha256:693c22ea458d62395bac47a2da405d0d18c77b205211ceec4846a550a37684b6

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
