FROM ubuntu:latest
LABEL authors="pragu"

ENTRYPOINT ["top", "-b"]