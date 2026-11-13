FROM eclipse-temurin:17-jdk-jammy@sha256:76751c16a2a07ec0960e659a2603491f8c79d95592173f6c0df8bf8ebab257cc
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl --fail --location --silent --show-error https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o /tmp/sdk.zip && \
    echo "7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  /tmp/sdk.zip" | sha256sum -c - && \
    unzip -q /tmp/sdk.zip -d /tmp/sdk && mv /tmp/sdk/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && rm /tmp/sdk.zip && \
    yes | sdkmanager --licenses > /dev/null
RUN sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"
WORKDIR /workspace
CMD ["sh", "gradlew", "--no-daemon", "assembleRelease", "bundleRelease", "lint"]
