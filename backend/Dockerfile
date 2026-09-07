# Build depuis la racine du dépôt :
#   docker compose build backend
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
COPY database database
COPY ollama/prompts ollama/prompts
COPY backend/src backend/src
WORKDIR /src/backend
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache \
      curl \
      tesseract-ocr \
      tesseract-ocr-data-eng \
      tesseract-ocr-data-fra \
    && addgroup -S aipack \
    && adduser -S aipack -G aipack
WORKDIR /app
COPY --from=build /src/backend/target/backend-0.1.0-SNAPSHOT.jar app.jar
USER aipack
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=8 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
