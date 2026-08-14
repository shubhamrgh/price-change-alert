# ---- build frontend ----
FROM node:22-alpine AS ui
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY frontend/ ./
RUN npm run build

# ---- build backend (grabs the fresh dist/ into static/) ----
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /app
COPY backend/pom.xml backend/mvnw backend/.mvn ./backend/
COPY backend/src ./backend/src
RUN rm -rf backend/src/main/resources/static/*
COPY --from=ui /app/frontend/dist ./backend/src/main/resources/static
RUN cd backend && mvn -q -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /srv
COPY --from=api /app/backend/target/trailify-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
