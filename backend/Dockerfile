# --- Stage 1: Build Stage ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy pom.xml và tải trước các dependencies để tối ưu hóa Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy mã nguồn và đóng gói (package) thành file JAR
COPY src ./src
RUN mvn package -DskipTests

# --- Stage 2: Runtime Stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy file JAR đã được build từ Stage 1
COPY --from=builder /app/target/vibecart-api-0.0.1-SNAPSHOT.jar app.jar

# Cấu hình cổng mạng của container (Spring Boot mặc định chạy ở cổng 8080)
EXPOSE 8080

# Chạy ứng dụng với Spring Local Profile (có nạp cấu hình từ các biến Docker)
ENTRYPOINT ["java", "-Dspring.profiles.active=local", "-jar", "app.jar"]
