# sha from https://hub.docker.com/layers/amd64/eclipse-temurin/11.0.22_7-jre-alpine/images/sha256-d7a82981336958683f147f17396fe2219cb1072a5853e8a8ef16d07f0535343a?context=explore
FROM eclipse-temurin@sha256:564eb67091b2cda82952299b4be52bf1b039289234b52f46057fe1286c173b71

WORKDIR /app
EXPOSE 8089

ARG JAR_NAME=uid2-admin
ARG JAR_VERSION=1.0.0-SNAPSHOT
ARG IMAGE_VERSION=1.0.0.unknownhash
ENV JAR_NAME=${JAR_NAME}
ENV JAR_VERSION=${JAR_VERSION}
ENV IMAGE_VERSION=${IMAGE_VERSION}
ENV LOGBACK_CONF=${LOGBACK_CONF:-./conf/logback.xml}

COPY ./target/${JAR_NAME}-${JAR_VERSION}-jar-with-dependencies.jar /app/${JAR_NAME}-${JAR_VERSION}.jar
COPY ./target/${JAR_NAME}-${JAR_VERSION}-sources.jar /app
COPY ./conf/default-config.json /app/conf/
COPY ./conf/*.xml /app/conf/
COPY ./webroot/ /app/webroot/

RUN adduser -D uid2-admin && mkdir -p /app && chmod 705 -R /app && mkdir -p /app/file-uploads && chmod 777 -R /app/file-uploads
USER uid2-admin

CMD java \
    -Djava.security.egd=file:/dev/./urandom \
    -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory \
    -Dlogback.configurationFile=${LOGBACK_CONF} \
    -jar ${JAR_NAME}-${JAR_VERSION}.jar
