# Use pre-built JAR (build locally with: mvn package -DskipTests)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S cv && adduser -S cv -G cv

COPY target/*.jar app.jar

RUN chown -R cv:cv /app
USER cv

EXPOSE 8080

# JVM tuning for production: G1GC, 512MB-1GB heap, container-aware
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
