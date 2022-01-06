FROM azul/zulu-openjdk:8
LABEL maintainer="vishal.kumar@solu-m.com"
ADD ./target/*.jar cloud.jar
EXPOSE 9015
ENTRYPOINT exec java -jar /cloud.jar
