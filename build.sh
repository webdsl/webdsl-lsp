#!/bin/sh

FILENAME=webdsl-lsp-1.0.0.jar

docker build -t webdsl-lsp -f Dockerfile .

mkdir -p build
id=$(docker create webdsl-lsp)
docker cp $id:/app/app/build/libs/$FILENAME build/$FILENAME
docker rm -v $id
