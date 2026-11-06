# Build wpsld with Eclipse Temurin JDK 25, including jlink runtime images
# for Linux and (cross-linked) Windows, which the conda-forge JDK can't
# produce yet, see environment.yml.
#
# Build and test, exporting dist/wpsld-linux-x64.zip and
# dist/wpsld-windows-x64.zip (unzip and run bin/wpsld or bin\wpsld.bat;
# for native launchers on each platform see .github/workflows/release.yml):
#   podman build --network=host --output type=local,dest=dist .
#
# Use the build stage as a development shell with the project mounted:
#   podman build --network=host --target build -t wpsld-build .
#   podman run --rm -it --network=host -v "$PWD":/src -w /src wpsld-build ./gradlew build

# Pinned: cross-linking needs the Windows jmods of exactly this JDK build
ARG TEMURIN_VERSION=25.0.5
ARG TEMURIN_BUILD=8
FROM docker.io/library/eclipse-temurin:${TEMURIN_VERSION}_${TEMURIN_BUILD}-jdk AS build
ARG TEMURIN_VERSION
ARG TEMURIN_BUILD

# Pango for JavaFX text layout, needed by the headless GUI tests
RUN apt-get update && apt-get install -y --no-install-recommends libpangoft2-1.0-0 \
 && rm -rf /var/lib/apt/lists/*

# Keep in sync with javafx.version in ui/build.gradle
ARG JAVAFX_VERSION=27.0.1
ARG JAVAFX_LINUX_JMODS_SHA256=323ceb45b938485f6921d7c31c994cc649adb1f75b16192580ff84a3119694ba
ARG JAVAFX_WINDOWS_JMODS_SHA256=5c2a69a7ab42da347b0e8bb244a819e94af75d9f21ae4d129695a3ebc81e3979
# From https://api.adoptium.net/v3/assets/latest/25/hotspot?os=windows&architecture=x64&image_type=jmods
ARG TEMURIN_WINDOWS_JMODS_SHA256=09a8df20d57da0f4807e219798f0e76c0a24c70dc23595115f8cf26c53fd2727

# JavaFX jmods, so the native libraries end up in the runtime images
WORKDIR /opt/javafx-jmods
ADD https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_linux-x64_bin-jmods.zip linux.zip
ADD https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_windows-x64_bin-jmods.zip windows.zip
RUN echo "${JAVAFX_LINUX_JMODS_SHA256}  linux.zip" | sha256sum -c - \
 && echo "${JAVAFX_WINDOWS_JMODS_SHA256}  windows.zip" | sha256sum -c - \
 && for os in linux windows; do \
      jar xf $os.zip && mv javafx-jmods-${JAVAFX_VERSION} $os && rm $os.zip; \
    done

# Windows JDK jmods for cross-linking, as <jdkHome>/jmods
WORKDIR /opt/jdk-windows
ADD https://github.com/adoptium/temurin25-binaries/releases/download/jdk-${TEMURIN_VERSION}%2B${TEMURIN_BUILD}/OpenJDK25U-jmods_x64_windows_hotspot_${TEMURIN_VERSION}_${TEMURIN_BUILD}.zip jmods.zip
RUN echo "${TEMURIN_WINDOWS_JMODS_SHA256}  jmods.zip" | sha256sum -c - \
 && jar xf jmods.zip && mv jdk-*-jmods jmods && rm jmods.zip

WORKDIR /src

# Download the Gradle distribution in its own layer, so it is cached
# across source changes
COPY gradlew settings.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon --version

COPY . .
RUN ./gradlew --no-daemon build :ui:imageZips \
      -PjavafxJmods=/opt/javafx-jmods/linux \
      -PwindowsJdkHome=/opt/jdk-windows \
      -PwindowsJavafxJmods=/opt/javafx-jmods/windows

FROM scratch AS export
COPY --from=build /src/ui/build/image-zip/ /
