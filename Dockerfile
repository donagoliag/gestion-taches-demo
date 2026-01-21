FROM ubuntu:latest
LABEL authors="donag"

ENTRYPOINT ["top", "-b"]