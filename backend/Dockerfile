# syntax=docker/dockerfile:1.7
# ERMS backend image. Contract: docs/ARCHITECTURE.md section 3.11.
#   Ports: 8080 (API), 8081 (management: /actuator/health/{liveness,readiness}, /actuator/prometheus)
#   User:  10001:10001, works with a read-only root filesystem when /tmp is writable.
# Tests run in CI separately, so the image build skips them.

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests -Djacoco.skip=true -Dspotless.skip=true package \
 && java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre-alpine
ARG VERSION=0.1.0
ARG REVISION=unknown
LABEL org.opencontainers.image.title="erms-backend" \
      org.opencontainers.image.description="ERMS REST API (Spring Boot)" \
      org.opencontainers.image.source="https://github.com/manojis/CriticalUtilityResponseService" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.licenses="NOASSERTION"

RUN addgroup -S -g 10001 erms \
 && adduser -S -D -H -u 10001 -G erms -h /app -s /sbin/nologin erms

WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp" \
    SERVER_PORT=8080 \
    MANAGEMENT_SERVER_PORT=8081

USER 10001:10001
EXPOSE 8080 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8081/actuator/health/liveness"]

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
