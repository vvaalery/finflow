FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/audit-service-1.0.0.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
