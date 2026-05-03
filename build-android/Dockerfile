FROM debian:bookworm-slim

ARG ANDROID_API=35
ARG BUILD_TOOLS_VERSION=35.0.0
ARG NDK_VERSION=27.2.12479018
ARG NDK_API=23
ARG COMMANDLINE_TOOLS_VERSION=11076708

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROIDSDK=/opt/android-sdk \
    ANDROIDNDK=/opt/android-sdk/ndk/${NDK_VERSION} \
    ANDROIDAPI=${ANDROID_API} \
    NDKAPI=${NDK_API} \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/build-tools/${BUILD_TOOLS_VERSION}:${PATH}

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        autoconf \
        automake \
        bash \
        build-essential \
        ca-certificates \
        ccache \
        clang \
        cmake \
        curl \
        gettext \
        git \
        libffi-dev \
        libltdl-dev \
        libncurses5 \
        libssl-dev \
        libtool \
        lld \
        openjdk-17-jdk \
        patch \
        patchelf \
        pkg-config \
        python3 \
        python3-pip \
        python3-setuptools \
        python3-venv \
        python3-wheel \
        tar \
        unzip \
        wget \
        zip \
        zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fL "https://dl.google.com/android/repository/commandlinetools-linux-${COMMANDLINE_TOOLS_VERSION}_latest.zip" -o /tmp/android-commandline-tools.zip \
    && unzip -q /tmp/android-commandline-tools.zip -d /tmp/android-commandline-tools \
    && mv /tmp/android-commandline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm -rf /tmp/android-commandline-tools /tmp/android-commandline-tools.zip

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager \
        "platform-tools" \
        "platforms;android-${ANDROID_API}" \
        "build-tools;${BUILD_TOOLS_VERSION}" \
        "ndk;${NDK_VERSION}"

RUN pip3 install --break-system-packages --no-cache-dir \
        "cython<3" \
        bleak==0.19.5 \
        python-for-android

RUN mkdir -p /opt/cat-printer-build \
    && curl -fL "https://files.pythonhosted.org/packages/e6/b4/e63829826a157d180831a1c5d3720e75d613c1290cb239510d148b906836/bleak-0.19.5.tar.gz" -o /tmp/bleak-0.19.5.tar.gz \
    && tar -xzf /tmp/bleak-0.19.5.tar.gz -C /opt/cat-printer-build \
    && mv /opt/cat-printer-build/bleak-0.19.5 /opt/cat-printer-build/bleak \
    && rm /tmp/bleak-0.19.5.tar.gz \
    && git clone --depth 1 https://github.com/delight-im/Android-AdvancedWebView.git /opt/cat-printer-build/Android-AdvancedWebView

WORKDIR /workspace
