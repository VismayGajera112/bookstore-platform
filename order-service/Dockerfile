# Multi-stage build: JDK compiles the Maven module; JRE runs the fat jar.
# Build from repo root: docker build -f order-service/Dockerfile -t bookstore/order-service:1.0.0 .
# Jammy tags are multi-arch (amd64/arm64); alpine JRE is not published for all platforms.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN apt-get update && apt-get install -y --no-install-recommends maven \
 && mvn -pl order-service -am package -DskipTests -q \
 && JAR=$(ls order-service/target/order-service-*.jar | grep -v original | head -n 1) \
 && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system spring && useradd --system --gid spring spring
USER spring:spring
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8083
ENV SERVER_PORT=8083
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
