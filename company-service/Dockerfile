FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/company-service-1.0.0.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
