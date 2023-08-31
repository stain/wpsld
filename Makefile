default: build

GRADLE=./gradlew

all: clean build

build:
	$(GRADLE) build

test:
	$(GRADLE) test

clean: 
	$(GRADLE) clean


