
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/backend-0.0.1-SNAPSHOT.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8443

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
