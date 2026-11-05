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

# JavaFX jmods for jlink, so the native libraries end up in the runtime
# image. Keep the version in sync with javafx.version in ui/build.gradle.
ARG JAVAFX_VERSION=27.0.1
ARG JAVAFX_JMODS_SHA256=323ceb45b938485f6921d7c31c994cc649adb1f75b16192580ff84a3119694ba
WORKDIR /opt
ADD https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_linux-x64_bin-jmods.zip \
    jmods.zip
RUN echo "${JAVAFX_JMODS_SHA256}  jmods.zip" | sha256sum -c - \
 && jar xf jmods.zip && rm jmods.zip \
 && mv javafx-jmods-${JAVAFX_VERSION} javafx-jmods

WORKDIR /src

# Download the Gradle distribution in its own layer, so it is cached
# across source changes
COPY gradlew settings.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon --version

COPY . .
RUN ./gradlew --no-daemon build :ui:jlink -PjavafxJmods=/opt/javafx-jmods

FROM scratch AS export
COPY --from=build /src/ui/build/image /image
