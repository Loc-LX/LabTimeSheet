# Multi-stage production build. Base images are pinned multi-architecture indexes.
FROM node:24-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS frontend
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY src/main ./src/main
RUN npm run build

FROM eclipse-temurin:25-jdk-alpine@sha256:5ecfde8e5ecde5954ea3721155b345ef56c1d579b940c761318ad4c05959a151 AS builder
WORKDIR /workspace
RUN apk add --no-cache curl
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -Dmaven.test.skip=true dependency:go-offline
COPY src/main ./src/main
COPY --from=frontend /workspace/src/main/resources/static/assets/app.css ./src/main/resources/static/assets/app.css
COPY --from=frontend /workspace/src/main/resources/static/assets/icons.svg ./src/main/resources/static/assets/icons.svg
RUN ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0
ARG VCS_REF=unknown
ARG SOURCE_URL=https://git.sechmachine.io.vn/sechmachine/labtimesheet
LABEL org.opencontainers.image.title="Lab Timesheet" \
      org.opencontainers.image.source="${SOURCE_URL}" \
      org.opencontainers.image.revision="${VCS_REF}"

RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app
COPY --from=builder --chown=10001:10001 /workspace/target/*.war /app/app.war

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
USER 10001:10001

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.war"]
