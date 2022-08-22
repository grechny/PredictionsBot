FROM openjdk:17-jdk-slim-buster

# create non-root user and group
# -l and static IDs assigned to avoid delay in lookups and system logging
ARG THE_USER_ID=1001
ARG THE_GROUP_ID=1001
RUN DEBIAN_FRONTEND=noninteractive && \
/usr/sbin/groupadd -g $THE_GROUP_ID spring && \
/usr/sbin/useradd -l -u $THE_USER_ID -G spring -g $THE_GROUP_ID spring && \
mkdir logs && chgrp spring logs && chmod ug+rwx logs

# run as non-root
USER spring:spring

EXPOSE 8080

COPY build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]