FROM maven:3.8.5-openjdk-11 AS build
WORKDIR /app
COPY . .
RUN mvn clean package

FROM openjdk:11-jre-slim
RUN apt-get update && apt-get install -y tesseract-ocr
COPY --from=build /app/target/nakuri-1.0-SNAPSHOT.jar /app/nakuri-1.0-SNAPSHOT.jar
CMD ["java", "-jar", "/app/nakuri-1.0-SNAPSHOT.jar"]