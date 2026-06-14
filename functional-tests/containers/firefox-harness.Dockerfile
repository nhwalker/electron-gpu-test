# =============================================================================
# Test-only layer ON TOP of the Firefox image (Containerfile.firefox). It adds a
# version-matched geckodriver so Selenium can drive the REAL Firefox -- with the
# enterprise policies (bookmarks toolbar + lockdowns) this image bakes in -- and
# assert they are actually applied (about:policies#active).
#
# Unlike the Electron harness (where ChromeDriver attaches to an already-running
# app), geckodriver LAUNCHES Firefox itself per WebDriver session. The browser,
# its policies, and setup-config.sh all come from the base image below; the
# entrypoint just re-runs the policy merge (so a mounted /config override applies)
# and exposes geckodriver. Firefox runs headless, so no Xvfb sidecar is needed.
# =============================================================================
ARG BASE_IMAGE=firefox-ubi9:undertest
FROM ${BASE_IMAGE}

USER root

# geckodriver 0.36 supports Firefox ESR 140. tar/gzip aren't guaranteed in UBI's
# minimal base; the base already trusts any proxy CA, so this https download works
# behind an egress proxy.
ARG GECKODRIVER_VERSION=0.36.0
RUN dnf -y install --setopt=install_weak_deps=False tar gzip && dnf clean all && \
    curl -fsSL -o /tmp/gd.tar.gz \
        "https://github.com/mozilla/geckodriver/releases/download/v${GECKODRIVER_VERSION}/geckodriver-v${GECKODRIVER_VERSION}-linux64.tar.gz" && \
    tar -xzf /tmp/gd.tar.gz -C /usr/local/bin geckodriver && \
    rm -f /tmp/gd.tar.gz && \
    chmod +x /usr/local/bin/geckodriver

COPY firefox-harness-entrypoint.sh /usr/local/bin/firefox-harness-entrypoint.sh
COPY firefox-policy-check.sh /usr/local/bin/firefox-policy-check.sh
COPY firefox-support-check.sh /usr/local/bin/firefox-support-check.sh
RUN chmod +x /usr/local/bin/firefox-harness-entrypoint.sh \
             /usr/local/bin/firefox-policy-check.sh \
             /usr/local/bin/firefox-support-check.sh

EXPOSE 4444
USER firefox
ENTRYPOINT ["/usr/local/bin/firefox-harness-entrypoint.sh"]
