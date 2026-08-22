FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY payment-platform/pom.xml payment-platform/pom.xml
COPY payment-mcp-server/pom.xml payment-mcp-server/pom.xml
COPY ai-ops-agent/pom.xml ai-ops-agent/pom.xml
RUN mvn -q -pl ai-ops-agent -am -DskipTests dependency:go-offline
COPY ai-ops-agent/src ai-ops-agent/src
RUN mvn -q -pl ai-ops-agent -am -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/ai-ops-agent/target/ai-ops-agent-*.jar app.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
