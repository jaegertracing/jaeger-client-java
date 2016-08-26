FROM maven:3.3-jdk-7
EXPOSE 8080-8082

ADD build/libs/jaeger-crossdock.jar /

CMD ["java", "-jar", "jaeger-crossdock.jar"]
