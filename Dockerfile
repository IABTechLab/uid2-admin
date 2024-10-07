# sha from https://hub.docker.com/layers/amd64/eclipse-temurin/21.0.4_7-jre-alpine/images/sha256-8179ddc8a6c5ac9af935020628763b9a5a671e0914976715d2b61b21881cefca
FROM eclipse-temurin@sha256:8179ddc8a6c5ac9af935020628763b9a5a671e0914976715d2b61b21881cefca

WORKDIR /app
EXPOSE 8089

ARG JAR_NAME=uid2-admin
ARG EXTRA_CONFIG_RENAMED=""
ARG JAR_VERSION=1.0.0-SNAPSHOT
ARG IMAGE_VERSION=1.0.0.unknownhash
ENV JAR_NAME=${JAR_NAME}
ENV JAR_VERSION=${JAR_VERSION}
ENV IMAGE_VERSION=${IMAGE_VERSION}
ENV LOGBACK_CONF=${LOGBACK_CONF:-./conf/logback.xml}

COPY ./target/${JAR_NAME}-${JAR_VERSION}-jar-with-dependencies.jar /app/${JAR_NAME}-${JAR_VERSION}.jar
COPY ./target/${JAR_NAME}-${JAR_VERSION}-sources.jar /app
COPY ./conf/default-config.json "${EXTRA_CONFIG_RENAMED}" /app/conf/
COPY ./conf/*.xml /app/conf/
COPY ./webroot/ /app/webroot/

RUN adduser -D uid2-admin && mkdir -p /app && chmod 705 -R /app && mkdir -p /app/file-uploads && chmod 777 -R /app/file-uploads
USER uid2-admin

CMD java \
    -Djava.security.egd=file:/dev/./urandom \
    -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory \
    -Dlogback.configurationFile=${LOGBACK_CONF} \
    -jar ${JAR_NAME}-${JAR_VERSION}.jar
