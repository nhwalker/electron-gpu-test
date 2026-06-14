# Electron-gpu-test packaged as an RPM.
#
# This bundles a self-contained app tree: the tiny Electron app (main.js,
# package.json), the launch + cert-import wrappers, AND the prebuilt Electron
# runtime that `npm install` fetched (node_modules/electron). packaging/build-rpm.sh
# runs that npm install up front and tars the result, so this spec only stages
# files -- no network or nodejs at rpmbuild time.
#
# Because the bundled Electron runtime ships its own private shared objects
# (libEGL.so, libGLESv2.so, libvk_swiftshader.so, ...), automatic RPM dependency
# generation is turned OFF for the install dir: otherwise rpm would advertise
# those bundled sonames as system Provides (shadowing mesa, etc.) and demand their
# internal Requires. The real runtime libraries are declared explicitly below.
%global appdir %{_prefix}/lib/%{name}
%global __provides_exclude_from ^%{appdir}/.*$
%global __requires_exclude_from ^%{appdir}/.*$
# The bundled runtime is already stripped/optimized; don't let rpm restrip it.
%global __os_install_post %{nil}
%global debug_package %{nil}

Name:           electron-gpu-test
Version:        1.0.0
Release:        1%{?dist}
Summary:        Minimal Electron app that opens web pages passed as CLI arguments

License:        MIT
URL:            https://github.com/nhwalker/electron-gpu-test
Source0:        %{name}-%{version}.tar.gz

# Bundles the prebuilt x86_64 Electron binary, so this is not noarch.
ExclusiveArch:  x86_64

# Runtime libraries the Electron/Chromium binary needs to start and render, plus
# the NSS tools the launcher's setup-certs.sh drives to import mounted TLS certs.
# gtk3 pulls the rest of the GTK/X GUI toolkit (cairo, pango, gdk-pixbuf2, atk,
# at-spi2, the libX*/libxkbcommon stack, ...).
Requires:       bash
Requires:       coreutils
Requires:       gtk3
Requires:       nss
Requires:       nss-tools
Requires:       openssl
Requires:       alsa-lib
Requires:       libdrm
Requires:       mesa-libgbm
Requires:       mesa-libGL
Requires:       mesa-libEGL
# Optional: desktop notifications, and NVIDIA VAAPI hardware video decode.
Recommends:     libnotify
Recommends:     libva
Recommends:     libva-nvidia-driver

%description
A minimal Electron application that opens one window per URL given on its command
line, packaged with its prebuilt Electron runtime and the GPU/Ozone launch wrapper.
Mounted TLS certificates (CA bundles and client cert/key pairs under /certs, or
$TLS_CERT_DIR) are imported into the per-user NSS DB at launch for custom-CA HTTPS
and mutual TLS. Run it as: electron-gpu-test https://example.com

%prep
%setup -q

%install
rm -rf %{buildroot}
# Stage the whole self-contained app tree under /usr/lib/<name>.
install -d %{buildroot}%{appdir}
cp -a main.js package.json launch.sh setup-certs.sh node_modules %{buildroot}%{appdir}/
chmod 0755 %{buildroot}%{appdir}/launch.sh %{buildroot}%{appdir}/setup-certs.sh

# Entry point on PATH. launch.sh resolves its real location via readlink, so a
# symlink here still finds the app tree and the bundled Electron binary.
install -d %{buildroot}%{_bindir}
ln -s %{appdir}/launch.sh %{buildroot}%{_bindir}/%{name}

%files
%dir %{appdir}
%{appdir}/main.js
%{appdir}/package.json
%{appdir}/launch.sh
%{appdir}/setup-certs.sh
%{appdir}/node_modules
%{_bindir}/%{name}

%changelog
* Sun Jun 14 2026 electron-gpu-test maintainers <noreply@example.com> - 1.0.0-1
- Initial RPM packaging of the Electron app with its bundled runtime.
