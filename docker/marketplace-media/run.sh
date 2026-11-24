#!/usr/bin/env bash
# Inside the container: copy the sources, start a virtual display, record it and run the screenshot test.
set -euo pipefail

export SCREEN="${SCREEN:-2880x1800}"

rsync -a --delete --exclude build/ --exclude .gradle/ --exclude .intellijPlatform/ --exclude .kotlin/ \
  --exclude .idea/ --exclude out/ /src/ /work/
mkdir -p /work/out /media /home/dev
cd /work

export DISPLAY=:99
Xvfb "$DISPLAY" -screen 0 "${SCREEN}x24" -nolisten tcp 2>/dev/null &
for _ in $(seq 50); do xdpyinfo >/dev/null 2>&1 && break; sleep 0.1; done

ffmpeg -loglevel error -f x11grab -video_size "$SCREEN" -framerate 24 -i "$DISPLAY" \
  -codec:v libx264 -preset veryfast -pix_fmt yuv420p -y /media/recording.mp4 &
recorder=$!

status=0
./gradlew --no-daemon --console=plain testUi -PmediaDir=/media -PmediaHome=/home/dev \
  -Porg.gradle.java.installations.paths=/opt/jdk-21,/opt/java/openjdk "$@" || status=$?

kill -INT "$recorder" && wait "$recorder" || true

# Test report and IDE log, to see what went wrong without entering the container.
rm -rf /media/reports && mkdir -p /media/reports
cp -r build/reports/tests/testUi /media/reports/ 2>/dev/null || true
find out -name idea.log -newer /work/build.gradle.kts -exec cp {} /media/reports/ \; 2>/dev/null || true

exit "$status"
