#!/bin/sh -x

set -e

apt-get install --yes clang g++ libc++-dev

VERSION='2.17.0'

cd /opt/
wget "https://github.com/stan-dev/cmdstan/releases/download/v${VERSION}/cmdstan-${VERSION}.tar.gz"
tar -xzf "cmdstan-${VERSION}.tar.gz"

mv "cmdstan-${VERSION}" "cmdstan"
cd "cmdstan"

make build -j4
