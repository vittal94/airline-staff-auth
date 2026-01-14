FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build the war file
RUN mvn clean package -DskipTests

# Runtime stage
FROM tomcat:10.1-jdk21

# Remove default tomcat applications
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the war file
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war

# Expose port
EXPOSE 8080

# Start tomcat
CMD ["catalina.sh", "run"]