FROM openjdk:8u121-jdk-alpine
EXPOSE 8080-8082

ADD build/libs/jaeger-crossdock.jar /

CMD ["java", "-jar", "jaeger-crossdock.jar"]
