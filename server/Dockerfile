FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /compilar
COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/.medishift-cache && chown 10001:1000 /app/.medishift-cache
COPY --from=compilacion --chown=10001:1000 /compilar/target/medishift-*.jar /app/medishift.jar
USER 10001:1000
EXPOSE 10000
CMD ["sh", "-c", "exec java -XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError -jar /app/medishift.jar --server.address=0.0.0.0 --server.port=\"${PORT:-10000}\""]
