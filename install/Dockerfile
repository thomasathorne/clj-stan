FROM clojure

RUN apt-get update
RUN apt-get install make g++ clang libc++-dev -y

WORKDIR /opt

RUN wget https://github.com/stan-dev/cmdstan/releases/download/v2.17.0/cmdstan-2.17.0.tar.gz
RUN tar -xzf cmdstan-2.17.0.tar.gz

WORKDIR /opt/cmdstan-2.17.0
RUN make build -j4
ENV STAN_HOME /opt/cmdstan-2.17.0
