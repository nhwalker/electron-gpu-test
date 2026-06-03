# Flags.md — Electron 41.1.1 switches & feature flags

Reference for every command-line switch and feature flag you can pass to the
Electron build used by this project.

| Component | Version |
| --------- | ------- |
| Electron  | **41.1.1** |
| Chromium  | **146.0.7680.166** |
| Node.js   | **v24.14.0** |
| V8        | **14.6** (ships with Chromium 146) |

> **Scope note.** Electron's *own* switches are a small, fixed, fully
> documented set — they are listed in full in
> [§1](#1-electron-cli-switches), [§2](#2-nodejs-flags) and
> [§3](#3-v8-flags---js-flags). Everything else Electron accepts is inherited
> from **Chromium**, whose switch and `--enable-features` universe runs to
> *thousands* of entries that are generated from Chromium source and change
> every release. Those cannot be exhaustively hand-listed and kept correct, so
> [§4](#4-chromium-switches) and [§5](#5-feature-flags---enable-features--
> --disable-features) document the mechanism, the switches/features that matter
> for this repo, and — in [§7](#7-reference-links--how-to-enumerate-the-complete-set)
> — link a complete switches reference and the version-exact Chromium 146 source
> tree.

---

## How flags reach the right layer

A flag on the Electron command line can be consumed by one of four layers:

```
electron <appPath> [ELECTRON & CHROMIUM SWITCHES] [-- node/app args]
            │              │
            │              └─ Electron switches (§1) + Chromium switches (§4)
            │                 + --enable-features/--disable-features (§5)
            └─ V8 flags via --js-flags="…" (§3)
               Node flags via NODE_OPTIONS / argv when running as a Node CLI (§2)
```

* **Electron switches** (§1) are parsed by Electron itself; unrecognised ones
  are forwarded to Chromium.
* **Chromium switches** (§4) and **features** (§5) are parsed by Chromium's
  `base::CommandLine` and `base::FeatureList`.
* **Node.js flags** (§2) configure the bundled Node runtime in the main
  process.
* **V8 flags** (§3) are *not* passed directly — they go through
  `--js-flags="…"`.

Switches must be passed **before** the app's own arguments and, for anything
that affects process startup (sandbox, GPU, V8), **on the very first launch of
the main process**. In code you can also set them with
`app.commandLine.appendSwitch(...)` / `appendArgument(...)` before
`app.whenReady()`.

---

## 1. Electron CLI switches

The complete set of switches Electron adds on top of Chromium, for v41.x.

| Switch | Description |
| ------ | ----------- |
| `--auth-server-whitelist=url` | Comma-separated list of servers for which integrated authentication is enabled. |
| `--auth-negotiate-delegate-whitelist=url` | Comma-separated list of servers for which delegation of user credentials is required. |
| `--disable-ntlm-v2` | Disables NTLM v2 for POSIX platforms; no effect elsewhere. |
| `--disable-http-cache` | Disables the disk cache for HTTP requests. |
| `--disable-http2` | Disable HTTP/2 and SPDY/3.1 protocols. |
| `--disable-geolocation` | Disables the Geolocation API. Permission requests are denied internally regardless of any handler set via `session.setPermissionRequestHandler`. Implemented only for macOS; no effect on other platforms. |
| `--disable-renderer-backgrounding` | Prevents Chromium from lowering the priority of invisible pages' renderer processes. |
| `--disk-cache-size=size` | Forces the maximum disk space used by the disk cache, in bytes. |
| `--enable-logging[=file]` | Prints Chromium's logging to stderr (or to a log file). |
| `--force-fieldtrials=trials` | Field trials to be forcefully enabled or disabled. |
| `--host-rules=rules` | *(Deprecated)* Comma-separated list of rules controlling how hostnames are mapped. |
| `--host-resolver-rules=rules` | Comma-separated list of rules controlling how hostnames are mapped. |
| `--ignore-certificate-errors` | Ignores certificate-related errors. |
| `--ignore-connections-limit=domains` | Ignore the connections limit for the comma-separated `domains` list. |
| `--js-flags=flags` | Flags passed to the V8 engine. To enable flags in the **main** process, this must be passed on startup. See §3. |
| `--lang` | Set a custom locale. |
| `--log-file=path` | If `--enable-logging` is specified, write logs to this path. |
| `--log-net-log=path` | Enable net-log events and write them to `path`. |
| `--log-level=N` | Verbosity of logging when used with `--enable-logging`. |
| `--no-proxy-server` | Don't use a proxy server; always make direct connections. |
| `--no-sandbox` | Disables the Chromium sandbox. Forces renderer and helper processes to run un-sandboxed. **Used by this repo — see [§6](#6-flags-used-in-this-repo).** |
| `--no-stdio-init` | Disable stdio initialization during Node initialization. |
| `--proxy-bypass-list=hosts` | Bypass the proxy server for the given semicolon-separated list of hosts. |
| `--proxy-pac-url=url` | Use the PAC script at the specified URL. |
| `--proxy-server=address:port` | Use a specified proxy server, overriding the system setting. |
| `--remote-debugging-port=port` | Enables remote debugging over HTTP on the specified port. |
| `--v=log_level` | Default maximal active V-logging level; `0` is the default. |
| `--vmodule=pattern` | Per-module maximal V-logging levels, overriding `--v`. |
| `--force_high_performance_gpu` | Force the discrete GPU when multiple GPUs are available. |
| `--force_low_power_gpu` | Force the integrated GPU when multiple GPUs are available. |
| `--xdg-portal-required-version=version` | Minimum required version of the XDG portal implementation. |

---

## 2. Node.js flags

Electron's main process embeds Node v24.14.0. These Node flags are recognised by
Electron (others can be supplied via the `NODE_OPTIONS` environment variable,
subject to Electron's allow-list):

| Flag | Description |
| ---- | ----------- |
| `--inspect[=[host:]port]` | Activate the inspector on `host:port` (default `127.0.0.1:9229`). |
| `--inspect-brk[=[host:]port]` | Activate the inspector and break at the start of the user script. |
| `--inspect-brk-node[=[host:]port]` | Activate the inspector and break at the first internal JS script. |
| `--inspect-port=[host:]port` | Set the `host:port` used when the inspector is activated. |
| `--inspect-publish-uid=stderr,http` | Choose how the inspector WebSocket URL is exposed. |
| `--experimental-network-inspection` | Enable DevTools network-inspector events for Node's `http`/`https` modules. |
| `--no-deprecation` | Silence deprecation warnings. |
| `--throw-deprecation` | Throw errors for deprecations. |
| `--trace-deprecation` | Print stack traces for deprecations. |
| `--trace-warnings` | Print stack traces for process warnings (including deprecations). |
| `--dns-result-order=order` | Set the default `verbatim` value used by `dns.lookup()`. |
| `--diagnostic-dir=directory` | Directory for all Node diagnostic output files. |
| `--no-experimental-global-navigator` | Disable exposing the `Navigator` API on Node's global scope. |
| `--experimental-transform-types` | Enable transforming TypeScript-only syntax into JavaScript. |

> `NODE_OPTIONS` is honoured only for a subset of flags and is ignored for
> packaged apps unless `ELECTRON_RUN_AS_NODE` is set. Startup-affecting flags
> (e.g. `--max-old-space-size`) must be set this way before launch.

---

## 3. V8 flags (`--js-flags`)

V8 options are not passed directly; pass them through Electron's `--js-flags`,
space-separated and quoted:

```sh
electron . --js-flags="--max-old-space-size=4096 --expose-gc --harmony"
```

The set is owned by V8 14.6 and is large/volatile. Dump the authoritative list
from *this* binary:

```sh
electron . --js-flags="--help"        # prints every V8 flag, then exits
```

Commonly used ones:

| `--js-flags` content | Effect |
| -------------------- | ------ |
| `--max-old-space-size=N` | Old-space (heap) limit in MB. |
| `--max-semi-space-size=N` | Young-generation semi-space size in MB. |
| `--expose-gc` | Expose `global.gc()` for manual collection. |
| `--harmony` | Enable staged (not-yet-default) JS features. |
| `--jitless` | Disable all JIT (interpreter only). |
| `--no-opt` | Disable optimizing compilers. |
| `--trace-gc` | Log every garbage collection. |
| `--prof` | Write a V8 CPU profile (`isolate-*.log`). |
| `--stack-size=N` | V8 stack size limit (KB). |

---

## 4. Chromium switches

Beyond §1, Electron forwards **any** Chromium `content`/`gpu`/`ui`/`net`/`cc`
switch to the underlying Chromium 146. There are thousands; below are the ones
that matter for this project (GPU / Ozone / video decode) plus the most common
general-purpose ones. For the complete, version-exact list see §7.

### GPU, GL & Ozone (relevant to this repo)

| Switch | Description |
| ------ | ----------- |
| `--disable-gpu` | Disable GPU hardware acceleration; force software rendering. **Used (software fallback).** |
| `--disable-gpu-sandbox` | Disable the GPU-process sandbox only (narrower than `--no-sandbox`). |
| `--disable-gpu-driver-bug-workarounds` | Disable the per-driver bug workaround list. **Used.** |
| `--ignore-gpu-blocklist` | Override the software-rendering-list / GPU blocklist for blocklisted GPUs. **Used.** |
| `--use-gl=MODE` | Select the GL implementation: `angle`, `egl`, `desktop`, `swiftshader`, `disabled`. **Used (`angle`).** |
| `--use-angle=BACKEND` | ANGLE backend: `gl`, `gles`, `vulkan`, `metal`, `d3d11`, `d3d9`, `swiftshader`, `default`. **Used (`gl`).** |
| `--enable-gpu-rasterization` | Rasterize on the GPU. |
| `--disable-gpu-compositing` | Use software compositing even if GPU is up. |
| `--gpu-testing-vendor-id` / `--gpu-testing-device-id` | Spoof GPU IDs for testing the blocklist. |
| `--ozone-platform=NAME` | Force the Ozone backend: `x11`, `wayland`, `headless`, `drm`. **Used.** |
| `--ozone-platform-hint=HINT` | Auto-pick Ozone backend: `auto`, `x11`, `wayland`. **Used (`auto`).** |
| `--enable-hardware-overlays` | Enable hardware overlay planes. |
| `--in-process-gpu` | Run the GPU code in the browser process (debugging). |
| `--disable-software-rasterizer` | Disable the SwiftShader software GL fallback. |

### Process model, sandbox & rendering

| Switch | Description |
| ------ | ----------- |
| `--single-process` | Run renderer in the browser process (debug only; unstable). |
| `--process-per-site` / `--process-per-tab` | Override the process model. |
| `--renderer-process-limit=N` | Cap the number of renderer processes. |
| `--disable-dev-shm-usage` | Don't use `/dev/shm` (small-shm containers). |
| `--disable-setuid-sandbox` | Disable the setuid sandbox helper (POSIX). |
| `--no-zygote` | Disable the zygote process (implies more memory). |
| `--disable-software-rasterizer` | See above. |

### Networking, security & web platform

| Switch | Description |
| ------ | ----------- |
| `--proxy-server`, `--proxy-bypass-list`, `--proxy-pac-url`, `--no-proxy-server` | Proxy config (also surfaced as Electron switches, §1). |
| `--host-resolver-rules=rules` | Static host-resolution overrides. |
| `--disable-web-security` | Disable the same-origin policy (**dangerous**, testing only). |
| `--allow-running-insecure-content` | Allow mixed content. |
| `--unsafely-treat-insecure-origin-as-secure=origins` | Treat the listed http origins as secure. |
| `--autoplay-policy=POLICY` | `no-user-gesture-required`, `user-gesture-required`, `document-user-activation-required`. |
| `--force-device-scale-factor=N` | Override the display scale factor. |
| `--lang=LOCALE` | UI locale (also §1). |
| `--user-agent=STRING` | Override the User-Agent string. |
| `--remote-allow-origins=origins` | Allowed origins for the remote-debugging WebSocket. |

### Logging & diagnostics

| Switch | Description |
| ------ | ----------- |
| `--enable-logging[=stderr]` | Route Chromium logging (also §1). |
| `--v=N`, `--vmodule=pattern` | Verbose logging levels (also §1). |
| `--trace-startup` / `--trace-to-file` | Capture a tracing session at startup. |
| `--vmodule=*/gpu/*=2` | Example: bump GPU module log verbosity. |

---

## 5. Feature flags (`--enable-features` / `--disable-features`)

Chromium "features" (`base::Feature`) are toggled as **comma-separated lists**,
not as individual switches:

```sh
electron . \
  --enable-features=FeatureA,FeatureB:param/value \
  --disable-features=FeatureC
```

* A feature may carry inline parameters: `Feature:key1/val1/key2/val2`.
* `--disable-features` wins over `--enable-features` for the same feature.
* The full feature catalogue is generated from Chromium 146 source
  (`*_features.cc`) and numbers in the thousands. The authoritative,
  version-exact list is the Chromium 146.0.7680.166 source tree — see §7.

### Features used / relevant in this repo

| Feature | Effect |
| ------- | ------ |
| `UseOzonePlatform` | Use the Ozone abstraction for the windowing backend (required for the X11/Wayland selection in `launch.sh`). **Used.** |
| `VaapiOnNvidiaGPUs` | Allow the VA-API decode path on NVIDIA GPUs (developer/test switch, off by default). **Used.** |
| `VaapiIgnoreDriverChecks` | Skip VA-API driver allow-list checks (needed for the `nvidia-vaapi-driver` bridge). **Used.** |
| `AcceleratedVideoDecodeLinuxGL` | Enable accelerated video **decode** on Linux through the GL path. **Used.** |
| `AcceleratedVideoDecodeLinuxZeroCopyGL` | Zero-copy variant of the above (decoded frames stay on the GPU). **Used.** |
| `PlatformHEVCDecoderSupport` | Enable platform HEVC/H.265 decode. |
| `VaapiVideoDecoder` / `VaapiVideoEncoder` | The VA-API decode/encode backends. |
| `Vulkan` | Use the Vulkan graphics backend (pairs with `--use-angle=vulkan`). |
| `CanvasOopRasterization` | Out-of-process canvas raster. |
| `WebRTCPipeWireCapturer` | PipeWire screen capture on Wayland. |

### Other commonly toggled features

| Feature | Typical use |
| ------- | ----------- |
| `OverlayScrollbar` | Overlay (thin) scrollbars. |
| `CalculateNativeWinOcclusion` | Occlusion-based renderer throttling (often disabled to keep background windows live). |
| `BackForwardCache` | bfcache navigation. |
| `IntensiveWakeUpThrottling` | Background-timer throttling. |
| `ElectronSerialChooser` | Electron's serial-port chooser. |
| `WebContentsForceDark` / `WebUIDarkMode` | Forced dark mode. |
| `HardwareMediaKeyHandling` | OS media-key integration. |

---

## 6. Flags used in this repo

For traceability, here is exactly what `app/launch.sh` passes, and where each
flag is documented above.

**Hardware path (GPU present):**

```
--ozone-platform=x11 | --ozone-platform=wayland | --ozone-platform-hint=auto   (§4 Ozone)
--enable-features=UseOzonePlatform,AcceleratedVideoDecodeLinuxGL,\
                  AcceleratedVideoDecodeLinuxZeroCopyGL,VaapiOnNvidiaGPUs,\
                  VaapiIgnoreDriverChecks                                       (§5)
--use-gl=angle                                                                 (§4 GPU)
--use-angle=gl                                                                 (§4 GPU)
--ignore-gpu-blocklist                                                         (§4 GPU)
--disable-gpu-driver-bug-workarounds                                           (§4 GPU)
--no-sandbox                                                                   (§1)
```

**Software fallback (no GPU detected):**

```
--disable-gpu                                                                  (§4 GPU)
--enable-features=UseOzonePlatform                                             (§5)
--no-sandbox                                                                   (§1)
```

See `app/launch.sh` for the GPU-detection logic and the `--no-sandbox`
rationale (the non-root `app` user can't bring up Chromium's namespace
sandbox).

---

## 7. Reference links & how to enumerate the complete set

Hand-maintained lists drift between Chromium releases, so for the Chromium
switches/features in §4–§5 use these instead.

### Reference web pages

* **Chromium command-line switches (full list):**
  <https://peter.sh/experiments/chromium-command-line-switches/>
  The de-facto complete switch reference, auto-generated from Chromium source
  with per-switch descriptions and availability conditions.
  ⚠️ It tracks **tip-of-tree** (last automated update 2026-04-12), *not* a
  pinned release — it's close to but **not guaranteed identical** to this build's
  146.0.7680.166. Use it for descriptions; confirm version-exact presence in the
  source tree below.

* **Version-exact source tree (Chromium 146.0.7680.166 — this build):**
  <https://chromium.googlesource.com/chromium/src/+/refs/tags/146.0.7680.166/>
  The only authoritative, build-exact reference. Switch *names* live in the
  `*_switches.cc` files, e.g.:
  * [`content/public/common/content_switches.cc`](https://chromium.googlesource.com/chromium/src/+/refs/tags/146.0.7680.166/content/public/common/content_switches.cc)
  * [`ui/gl/gl_switches.cc`](https://chromium.googlesource.com/chromium/src/+/refs/tags/146.0.7680.166/ui/gl/gl_switches.cc)
  * [`gpu/config/gpu_switches.cc`](https://chromium.googlesource.com/chromium/src/+/refs/tags/146.0.7680.166/gpu/config/gpu_switches.cc)
  * [`ui/ozone/public/ozone_switches.cc`](https://chromium.googlesource.com/chromium/src/+/refs/tags/146.0.7680.166/ui/ozone/public/ozone_switches.cc)

  Feature *names* live in the matching `*_features.cc` files, e.g.
  [`media/base/media_switches.cc`](https://chromium.googlesource.com/chromium/src/+/refs/tags/146.0.7680.166/media/base/media_switches.cc)
  (which defines the VA-API / video-decode features used in §5).

* **Electron switches (this exact tag):**
  <https://github.com/electron/electron/blob/v41.1.1/docs/api/command-line-switches.md>
  — the source for §1–§2, verbatim.

### Dumping flags from this binary

> **`chrome://flags` is NOT available in Electron** ([electron#22209]) — Electron
> ships without the `about_flags` UI, so there is no in-app feature browser.
> `chrome://gpu` is also unreliable and returns `ERR_FAILED` on Linux in recent
> Electron releases ([electron#39535]). Don't rely on either to enumerate flags.

What *does* work from the binary:

1. **All V8 flags** (prints to stderr, then exits):

   ```sh
   electron . --js-flags="--help"
   ```

2. **All Node flags:**

   ```sh
   ELECTRON_RUN_AS_NODE=1 electron --help
   ```

3. **See what actually took effect at runtime:** launch with
   `--enable-logging --v=1` and read the process launch line in stderr, or in
   the main process inspect `app.commandLine` / `process.argv`.

[electron#22209]: https://github.com/electron/electron/issues/22209
[electron#39535]: https://github.com/electron/electron/issues/39535

> Rule of thumb: §1–§3 are stable for the life of Electron 41.x; §4–§5 should be
> treated as *examples* and verified against the source tree above for anything
> you depend on.

---

*Generated for Electron 41.1.1 (Chromium 146.0.7680.166, Node v24.14.0,
V8 14.6). Electron/Node switch lists are taken verbatim from the upstream
`v41.1.1` documentation; Chromium switch/feature entries are a curated subset —
use §7 for the authoritative, build-exact source.*
