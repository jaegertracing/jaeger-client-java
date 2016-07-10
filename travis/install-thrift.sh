#!/bin/sh
set -e
set -x

thrift_ver=0.9.3

BUILD="$HOME/.thrift-build"
if [ ! -d "$BUILD" ]; then
	mkdir -p "$BUILD"
fi
cd "$BUILD"

if [ ! -d thrift-$thrift_ver ]; then
	wget http://archive.apache.org/dist/thrift/$thrift_ver/thrift-$thrift_ver.tar.gz
	tar -xzf thrift-$thrift_ver.tar.gz
	cd thrift-$thrift_ver
	./configure --enable-libs=no --enable-tests=no --enable-tutorial=no
	make -j2
else
	cd thrift-$thrift_ver
fi

sudo make install
