# Utiliser la version slim pour limiter la surface d'attaque
FROM eclipse-temurin:17-jre-jammy

# Mettre à jour le système et nettoyer le cache APT
USER root
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        tzdata \
        wget \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Créer le répertoire de travail
WORKDIR /app

# Copier le jar compilé
COPY target/devsecops-0.0.1-SNAPSHOT.jar app.jar

# Exposer le port de l'application
EXPOSE 8080

# Utiliser un utilisateur non-root pour exécuter l'application
RUN groupadd -r appuser && useradd -r -g appuser appuser
USER appuser

# Lancer l'application
ENTRYPOINT ["java", "-jar", "app.jar"]
