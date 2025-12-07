FROM gradle:9.2.0-jdk21-corretto

WORKDIR /app
COPY . .

RUN ["./gradlew", "--no-daemon", "clean", "build"]

CMD ["./gradlew", "run"]
