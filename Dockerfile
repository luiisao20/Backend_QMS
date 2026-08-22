ARG BASE_JRE_IMAGE=eclipse-temurin:23-jre-alpine
ARG BASE_JDK_IMAGE=amazoncorretto:23-alpine-jdk
# Stage 1: Dependencies
FROM ${BASE_JDK_IMAGE} AS dependencies
RUN apk add --no-cache maven
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline

#Stage 2: Build application
FROM dependencies AS builder
COPY src ./src
RUN mvn package -DskipTests

#Stage 3: Runtime
FROM ${BASE_JRE_IMAGE} AS runtime
WORKDIR /app 
ARG JAR_FILE=*.jar
COPY --from=builder /build/target/${JAR_FILE} app.jar
LABEL author="Luis Bravo" \
  version=1.0.0 \
  title="Backend QMS"
ENTRYPOINT [ "java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar" ]
