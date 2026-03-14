
# STAGE 1 — BUILD
# Compila y genera el fat JAR con Gradle Shadow
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Copiar solo los archivos de dependencias primero (mejor cache de capas)
COPY build.gradle settings.gradle* ./
RUN gradle dependencies --no-daemon || true

# Copiar el resto del código fuente
COPY src ./src

# Compilar y generar el fat JAR (shadowJar)
RUN gradle shadowJar --no-daemon -x test

# STAGE 2 — RUNTIME
# Imagen mínima solo con JRE para ejecutar la aplicación
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Crear usuario no-root por seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copiar el JAR desde el stage de build
COPY --from=build /app/build/libs/*.jar app.jar

# Ajustar permisos
RUN chown appuser:appgroup app.jar

USER appuser

# Puerto de la aplicación Javalin
EXPOSE 7000

# Variables de entorno por defecto (se sobreescriben en docker-compose)
ENV PORT=7000
    DB_URL=jdbc:h2:tcp://h2-server/~/eventos_academicos
    DB_USER=sa
    DB_PASS=sa

# Ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]