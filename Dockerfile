# Use Java 21 base image
FROM openjdk:21-jdk-slim AS build

# Install dependencies including Maven
RUN apt-get update && \
    apt-get install -y curl unzip && \
    curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.1/binaries/apache-maven-3.9.1-bin.tar.gz -o /tmp/apache-maven-3.9.1-bin.tar.gz && \
    tar -xzf /tmp/apache-maven-3.9.1-bin.tar.gz -C /opt && \
    ln -s /opt/apache-maven-3.9.1 /opt/maven && \
    ln -s /opt/maven/bin/mvn /usr/local/bin/mvn && \
    rm -rf /tmp/*

# Set working directory for building
WORKDIR /app

# Copy Maven project files
COPY pom.xml ./
COPY src ./src

# Use Maven to build the project
RUN mvn -B --quiet package -Ddir=/tmp/codecrafters-sqlite-target && \
    ls -l /tmp/codecrafters-sqlite-target

# Runtime image
FROM openjdk:21-jdk-slim

# Set working directory
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /tmp/codecrafters-sqlite-target/java_sqlite.jar /app/java_sqlite.jar

# Add the script to the image
COPY antdb.sh /app/antdb.sh

# Copy sample.db into the container (if it's available during build)
COPY sample.db /app/sample.db
COPY companies.db /app/companies.db
COPY superheroes.db /app/superheroes.db


# Grant execute permissions to the script
RUN chmod +x /app/antdb.sh

# Set the script as the default entry point
ENTRYPOINT ["/app/antdb.sh"]
