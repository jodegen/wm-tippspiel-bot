# --- Build-Stufe: Maven-Build mit JDK 21 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Abhängigkeiten zuerst cachen (nur pom.xml ändert sich selten).
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Quellcode bauen; Tests laufen separat (brauchen Docker/Testcontainers).
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime-Stufe: schlankes JRE ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Nicht als root laufen.
RUN useradd --system --uid 1001 --create-home appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

# Langlaufender Gateway-Prozess, kein Web-Port (web-application-type=none).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
