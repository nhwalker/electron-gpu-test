#!/usr/bin/env bash
# Start recording the virtual X display, independent of this container's
# lifecycle: invoked on demand (e.g. via `docker exec`) by the test, it launches
# ffmpeg in the background and records its PID so record-stop.sh can end it.
#
# Capture is RAW (rawvideo straight off x11grab) into a NUT container: no codec
# runs during the grab, so the capture can't fall behind / introduce latency or
# dropped frames. The raw file is transcoded to WebM later, in record-stop.sh.
set -euo pipefail

DISPLAY_NUM="${DISPLAY_NUM:-99}"
SIZE="${REC_SIZE:-1280x800}"
FPS="${REC_FPS:-15}"
RAW=/tmp/recording.nut
PIDFILE=/tmp/recording.pid

# Refuse to stack recordings: one in flight at a time keeps the PID file honest.
if [ -f "${PIDFILE}" ] && kill -0 "$(cat "${PIDFILE}")" 2>/dev/null; then
    echo "recording already in progress (pid $(cat "${PIDFILE}"))" >&2
    exit 1
fi
rm -f "${RAW}"

# nohup + detached IO so ffmpeg survives the exec session that launched it.
# -nostdin keeps it from grabbing a terminal; SIGINT (sent by record-stop.sh)
# makes it flush and finalize the file cleanly.
nohup ffmpeg -nostdin -y -loglevel warning \
    -f x11grab -draw_mouse 0 -framerate "${FPS}" -video_size "${SIZE}" -i ":${DISPLAY_NUM}" \
    -c:v rawvideo "${RAW}" </dev/null >/tmp/record.log 2>&1 &
echo $! > "${PIDFILE}"
disown || true

echo "recording started (pid $(cat "${PIDFILE}")) -> ${RAW}"
