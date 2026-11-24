# Runs the Marketplace screenshot test (Gradle task testUi) on a virtual display.
# Built and started by scripts/marketplace-media.sh.
FROM eclipse-temurin:21-jdk-noble AS jdk21

FROM eclipse-temurin:25-jdk-noble

# The plugin itself is compiled for Java 21; Gradle runs on 25.
COPY --from=jdk21 /opt/java/openjdk /opt/jdk-21

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      xvfb x11-utils ffmpeg rsync git unzip \
      fontconfig fonts-dejavu-core fonts-liberation \
      libxext6 libxrender1 libxtst6 libxi6 libxrandr2 libfreetype6 libgtk-3-0 libnss3 libasound2t64 \
 && rm -rf /var/lib/apt/lists/*

COPY run.sh /usr/local/bin/run-media
ENTRYPOINT ["/usr/local/bin/run-media"]
