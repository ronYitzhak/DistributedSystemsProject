ARG IP
ARG STATE
ARG ZKHOST

FROM openjdk:latest
COPY . tmp/.
WORKDIR tmp/.
CMD java -cp DSProject.jar Application.Election $IP $STATE $ZKHOST