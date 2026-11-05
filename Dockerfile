# Build wpsld with Eclipse Temurin JDK 25, including the jlink runtime image
# (which the conda-forge JDK can't produce yet, see environment.yml).
#
# Build and test, exporting the runtime image to ./dist/image:
#   podman build --network=host --output type=local,dest=dist .
#   dist/image/bin/hello
#
# Use the build stage as a development shell with the project mounted:
#   podman build --network=host --target build -t wpsld-build .
#   podman run --rm -it --network=host -v "$PWD":/src -w /src wpsld-build ./gradlew build

FROM docker.io/library/eclipse-temurin:25-jdk AS build
WORKDIR /src

# Download the Gradle distribution in its own layer, so it is cached
# across source changes
COPY gradlew settings.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon --version

COPY . .
RUN ./gradlew --no-daemon build :ui:jlink

FROM scratch AS export
COPY --from=build /src/ui/build/image /image
