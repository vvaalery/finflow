#!/bin/sh
set -eu
mkdir -p /app/certs
if [ ! -f /app/certs/keystore.p12 ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
    -keyout /tmp/finflow.key -out /tmp/finflow.crt \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
  openssl pkcs12 -export -out /app/certs/keystore.p12 \
    -inkey /tmp/finflow.key -in /tmp/finflow.crt \
    -name finflow -passout pass:changeit
  rm -f /tmp/finflow.key /tmp/finflow.crt
fi
exec java ${JAVA_OPTS:-} -jar /app/app.jar
