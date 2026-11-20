# Spec 009 (T1410, AC-127): imagen única con frontend + backend.

# 1) Frontend
FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Backend (el frontend compilado va a classpath:/static, AC-123)
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /src/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src/ src/
COPY --from=frontend /src/frontend/dist/ src/main/resources/static/
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar /app.jar

# 3) Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /app.jar app.jar
# ./data/cien-mexicanos.db (application.yml) → volumen /app/data
RUN mkdir -p /app/data
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
