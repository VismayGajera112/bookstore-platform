# Multi-stage build: JDK compiles the Maven module; JRE runs the fat jar.
# Build from repo root: docker build -f config-server/Dockerfile -t bookstore/config-server:1.0.0 .
# Jammy tags are multi-arch (amd64/arm64); alpine JRE is not published for all platforms.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN apt-get update && apt-get install -y --no-install-recommends maven \
 && mvn -pl config-server -am package -DskipTests -q \
 && JAR=$(ls config-server/target/config-server-*.jar | grep -v original | head -n 1) \
 && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system spring && useradd --system --gid spring spring
USER spring:spring
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
COPY --chown=spring:spring config-repo /config-repo
EXPOSE 8888
ENV SERVER_PORT=8888 \
    SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS=file:/config-repo
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
