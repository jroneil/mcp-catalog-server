FROM maven:3.9.16-eclipse-temurin-21-noble AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
# Tests run separately with a Docker daemon; do not mount its socket into builds.
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21.0.12_8-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/mcp-catalog-server-0.1.0.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=6 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
