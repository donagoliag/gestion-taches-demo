# Remplace tout le Dockerfile par :
cat > Dockerfile << 'EOF'
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/gestiontaches-*.jar app.jar
EXPOSE $PORT
CMD ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
EOF
