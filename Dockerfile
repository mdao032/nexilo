# =============================================================================
# Nexilo — Dockerfile multi-stage
#
# Stage 1 (builder) : compilation Maven avec JDK 21
# Stage 2 (runtime) : image minimale JRE alpine, utilisateur non-root
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 : Builder Maven
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copier pom.xml en premier pour bénéficier du cache Docker des dépendances
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copier les sources et compiler
COPY src ./src
RUN mvn package -DskipTests -q

# -----------------------------------------------------------------------------
# Stage 2 : Runtime minimal
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Métadonnées
LABEL maintainer="Nexilo <dev@nexilo.io>"
LABEL org.opencontainers.image.title="Nexilo App"
LABEL org.opencontainers.image.description="Nexilo — AI-powered PDF platform"

# Créer un utilisateur non-root pour la sécurité
RUN addgroup -S nexilo && adduser -S nexilo -G nexilo

WORKDIR /app

# Répertoire de stockage local (profil dev)
RUN mkdir -p /app/uploads && chown nexilo:nexilo /app/uploads

# Copier le jar depuis le stage builder
COPY --from=builder /build/target/*.jar app.jar
RUN chown nexilo:nexilo app.jar

# Basculer sur l'utilisateur non-root
USER nexilo

# Port de l'application Spring Boot
EXPOSE 8080

# Point d'entrée — --enable-preview requis par le projet (Java 21)
ENTRYPOINT ["java", \
  "--enable-preview", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

