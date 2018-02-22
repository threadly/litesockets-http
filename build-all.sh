#!/bin/bash
cd protocol
./gradlew clean build install
cd ..
cd server
./gradlew clean build install
cd ..
cd client
./gradlew clean build install
cd ..
