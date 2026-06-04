#!/usr/bin/env bash
# Stop the recording started by record-start.sh and transcode the raw capture to
# WebM. Encoding happens HERE, after the grab has ended, so the live capture
# never paid any encoding cost. Prints the output path on success.
#
# Usage: record-stop.sh [output.webm]   (default /tmp/recording.webm)
set -euo pipefail

RAW=/tmp/recording.nut
PIDFILE=/tmp/recording.pid
OUT="${1:-/tmp/recording.webm}"

[ -f "${PIDFILE}" ] || { echo "no recording in progress" >&2; exit 1; }
PID="$(cat "${PIDFILE}")"

# Graceful stop (SIGINT == ffmpeg 'q') so the NUT file is flushed and finalized,
# then wait for exit, and only hard-kill if it ignores us.
kill -INT "${PID}" 2>/dev/null || true
for _ in $(seq 1 150); do kill -0 "${PID}" 2>/dev/null || break; sleep 0.1; done
kill -KILL "${PID}" 2>/dev/null || true
rm -f "${PIDFILE}"

[ -s "${RAW}" ] || { echo "raw capture ${RAW} is missing/empty; see /tmp/record.log" >&2; cat /tmp/record.log >&2 || true; exit 1; }

# Transcode raw -> WebM (VP9). yuv420p keeps it broadly playable in an HTML
# <video>, which is how Allure renders the attachment. cpu-used/row-mt bound the
# encode time for a short clip.
ffmpeg -nostdin -y -loglevel warning -i "${RAW}" \
    -c:v libvpx-vp9 -b:v 0 -crf 33 -deadline good -cpu-used 2 -row-mt 1 \
    -pix_fmt yuv420p -an "${OUT}" >/tmp/encode.log 2>&1 || {
        echo "webm encode failed; see /tmp/encode.log" >&2; cat /tmp/encode.log >&2 || true; exit 1; }
rm -f "${RAW}"

echo "${OUT}"
