# Dockerfile simplifié - Build local, déploiement simple
FROM openjdk:17-alpine
WORKDIR /app

# Copie le JAR que tu as build localement
COPY target/gestiontaches-0.0.1-SNAPSHOT.jar app.jar

# Exposition du port
EXPOSE 8080

# Commande de démarrage
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]