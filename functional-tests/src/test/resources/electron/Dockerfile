# =============================================================================
# Test-only image: runs the REAL app/ (electron-gpu-test) headlessly so Selenium
# can drive it. This deliberately drops the production Containerfile's NVIDIA /
# RPM-Fusion / GPU stack (none of which is available or needed in CI) and instead
# adds Xvfb (a virtual display) plus a matching ChromeDriver so the Electron app
# can be controlled over the WebDriver protocol.
#
# GPU note: a goal of the production image is NVIDIA hardware acceleration, but
# here Electron/Chromium falls back to software rendering (SwiftShader) under
# Xvfb, which is exactly what lets us test the app's basics without a GPU.
#
# Build context is composed by Testcontainers (ImageFromDockerfile) from:
#   - this Dockerfile
#   - app/                 (the real Electron app from the repo root)
#   - render-check.html    (a deterministic page for the test to load)
#   - electron-entrypoint.sh
#   - extra-cas/           (optional extra CA certs; usually empty)
# =============================================================================
# Pulled from the AWS ECR public mirror of Docker Hub to avoid Hub's
# unauthenticated pull-rate limits in CI.
FROM public.ecr.aws/docker/library/node:22-bookworm-slim

# Chromium/Electron runtime shared libraries + a virtual X server. These mirror
# the runtime libs the production image installs from UBI, but as Debian packages.
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb \
        ca-certificates \
        dbus dbus-x11 \
        libnss3 libnspr4 \
        libatk1.0-0 libatk-bridge2.0-0 libatspi2.0-0 \
        libcups2 \
        libdrm2 libgbm1 \
        libxkbcommon0 \
        libx11-6 libxcomposite1 libxdamage1 libxext6 libxfixes3 libxrandr2 \
        libxcb1 libxshmfence1 libxi6 libxtst6 libxrender1 \
        libgtk-3-0 libpango-1.0-0 libcairo2 libasound2 libexpat1 libglib2.0-0 \
        fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Optionally trust additional CA certificates (corporate / sandbox egress
# proxies that intercept TLS). The 'extra-cas' build-context dir is always
# present but is usually empty -- a no-op in environments with direct egress.
COPY extra-cas/ /opt/extra-cas/
RUN if ls /opt/extra-cas/*.crt >/dev/null 2>&1; then \
        cp /opt/extra-cas/*.crt /usr/local/share/ca-certificates/ && update-ca-certificates && \
        cat /opt/extra-cas/*.crt >> /etc/ssl/certs/ca-certificates.crt ; \
    fi
# Make Node/npm honour the (possibly augmented) system trust store.
ENV NODE_EXTRA_CA_CERTS=/etc/ssl/certs/ca-certificates.crt

# Install dependencies first (better layer caching). The app pins electron@41.1.1;
# electron-chromedriver@41 provides the matching ChromeDriver for Chromium 146.
COPY app/package.json ./package.json
RUN npm install --omit=optional --no-audit --no-fund \
    && npm install --no-save --no-audit --no-fund electron-chromedriver@41

# The real application sources (main.js, launch.sh, package.json).
COPY app/ ./
# The deterministic page the functional test loads via file://.
COPY render-check.html /app/render-check.html

# Wrapper that ChromeDriver invokes as the "browser binary" -- it injects the
# Electron executable + app path so the REAL app launches (see the script).
COPY electron-run.sh /usr/local/bin/electron-run
RUN chmod +x /usr/local/bin/electron-run

# ChromeDriver listens here; Testcontainers maps it to the host.
EXPOSE 4444

# Start a virtual display, then run ChromeDriver bound to all interfaces so the
# Testcontainers host can reach it. The test supplies the Electron binary + app
# path via chromeOptions, so ChromeDriver launches the real app on demand.
COPY electron-entrypoint.sh /usr/local/bin/electron-entrypoint.sh
RUN chmod +x /usr/local/bin/electron-entrypoint.sh
ENTRYPOINT ["/usr/local/bin/electron-entrypoint.sh"]
