FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY payment-platform/pom.xml payment-platform/pom.xml
COPY payment-mcp-server/pom.xml payment-mcp-server/pom.xml
COPY ai-ops-agent/pom.xml ai-ops-agent/pom.xml
RUN mvn -q -pl payment-mcp-server -am -DskipTests dependency:go-offline
COPY payment-mcp-server/src payment-mcp-server/src
RUN mvn -q -pl payment-mcp-server -am -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/payment-mcp-server/target/payment-mcp-server-*.jar app.jar
USER 10001
EXPOSE 8082
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
