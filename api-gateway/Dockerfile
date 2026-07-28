# Multi-stage build: JDK compiles the Maven module; JRE runs the fat jar.
# Build from repo root: docker build -f api-gateway/Dockerfile -t bookstore/api-gateway:1.0.0 .
# Jammy tags are multi-arch (amd64/arm64); alpine JRE is not published for all platforms.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN apt-get update && apt-get install -y --no-install-recommends maven \
 && mvn -pl api-gateway -am package -DskipTests -q \
 && JAR=$(ls api-gateway/target/api-gateway-*.jar | grep -v original | head -n 1) \
 && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system spring && useradd --system --gid spring spring
USER spring:spring
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENV SERVER_PORT=8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
