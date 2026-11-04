#!/usr/bin/env bash
# The app in the Android emulator: menus, pages, layout and key handling at the A6000's screen size, with no camera.
#
#   tools/emulator.sh setup              system image (Android 7.0, pinned below) and an AVD at 640x480, once
#   tools/emulator.sh start              boot it (or reuse the running one), root adb, put sonykey on it
#   tools/emulator.sh install [APK]      RecipeLab.apk by default: native library out, re-signed, installed, opened
#   tools/emulator.sh key KEY[:MS] ...   press camera keys by name or scan code, held MS ms (60 by default):
#                                          tools/emulator.sh key MENU:900     hold MENU, the app menu
#                                          tools/emulator.sh key WHEEL_CW WHEEL_CW ENTER
#   tools/emulator.sh shot FILE.png      a screenshot
#
# What it cannot show: there is no CameraEx (no live preview, no shutter, no AF or histogram data) and no settings
# store (the native library is ARM 32-bit, which no emulator on an Apple Silicon Mac can run, so it is taken out and
# every read and write fails as on a body without it). The fonts are Android 7's, not the camera's; timings mean
# nothing. A green run here is a layout and key check, never a camera check.
#
# Keys: Android drops every code its emulated keyboard does not declare, and that is most of Sony's (MENU 514,
# Fn 520, the wheel 522 / 523, the shutter 516 / 518). tools/emulator/sonykey.c makes a uinput keyboard that declares
# them all, so the app gets the scan codes a camera sends. It is built static, in Docker, on first use.
#
# Toolchain (as build.sh): JAVA_HOME (or java on PATH) for keytool and apksigner; ANDROID_SDK with the emulator,
# platform-tools and a build-tools (BUILD_TOOLS, newest installed by default). Docker, once, for sonykey.
set -euo pipefail
cd "$(dirname "$0")/.."

for d in "${ANDROID_SDK:-}" "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
  [ -n "$d" ] && [ -d "$d" ] && { ANDROID_SDK="$d"; break; }
done
: "${ANDROID_SDK:?set ANDROID_SDK to the Android SDK}"
: "${BUILD_TOOLS:=$(ls "$ANDROID_SDK/build-tools" 2>/dev/null | sort -V | tail -1)}"
BT="$ANDROID_SDK/build-tools/$BUILD_TOOLS"
ADB="$ANDROID_SDK/platform-tools/adb"
EMU="$ANDROID_SDK/emulator/emulator"
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"   # apksigner and keytool exec java from PATH
AVD=RecipeLab
API=24

# the image for this host: arm64 on Apple Silicon (and ARM Linux), x86_64 elsewhere -- the x86_64 one is untried
case "$(uname -m)" in
  arm64|aarch64) ABI=arm64-v8a; IMG=arm64-v8a-24_r09.zip; IMG_SHA1=e88ebdf4533efa0370603ee4ab0e7834e0cc364f; ARCH=arm64 ;;
  *)             ABI=x86_64;    IMG=x86_64-24_r08.zip;    IMG_SHA1=f6559e1949a5879f31a9662f4f0e50ad60181684; ARCH=amd64 ;;
esac
SYSDIR="system-images/android-$API/default/$ABI"
KEYTOOL_STORE=out/emulator/debug.keystore

die() { echo "emulator.sh: $*" >&2; exit 1; }

setup() {
  if [ ! -f "$ANDROID_SDK/$SYSDIR/system.img" ]; then
    mkdir -p out/emulator
    echo "fetching $IMG"
    curl -fsSL -o "out/emulator/$IMG" "https://dl.google.com/android/repository/sys-img/android/$IMG"
    echo "$IMG_SHA1  out/emulator/$IMG" | shasum -a 1 -c - >/dev/null || die "$IMG does not match its pinned SHA-1"
    mkdir -p "$ANDROID_SDK/system-images/android-$API/default"
    unzip -q -o "out/emulator/$IMG" -d "$ANDROID_SDK/system-images/android-$API/default"
    rm -f "out/emulator/$IMG"
  fi
  local dir="$HOME/.android/avd/$AVD.avd"
  mkdir -p "$dir"
  printf 'avd.ini.encoding=UTF-8\npath=%s\ntarget=android-%s\n' "$dir" "$API" > "$HOME/.android/avd/$AVD.ini"
  # 640x480 is the A6000's panel; 240 dpi is a guess nobody has read off a camera yet
  cat > "$dir/config.ini" <<EOF
AvdId=$AVD
avd.ini.displayname=Recipe Lab (A6000 screen)
abi.type=$ABI
hw.cpu.arch=${ABI%%-*}
image.sysdir.1=$SYSDIR/
tag.id=default
hw.ramSize=1024
hw.lcd.width=640
hw.lcd.height=480
hw.lcd.density=${DENSITY:-240}
hw.keyboard=yes
hw.dPad=yes
hw.mainKeys=yes
hw.gpu.enabled=yes
showDeviceFrame=no
EOF
  echo "AVD $AVD ready: tools/emulator.sh start"
}

sonykey() {
  local bin="out/emulator/sonykey-$ARCH"
  if [ ! -x "$bin" ]; then
    command -v docker >/dev/null || die "sonykey needs Docker to build (or put a static $bin there)"
    mkdir -p out/emulator
    docker run --rm --platform "linux/$ARCH" -v "$PWD":/w -w /w alpine:3.20 \
      sh -c "apk add -q build-base linux-headers >/dev/null && gcc -static -O2 -Wall -o $bin tools/emulator/sonykey.c"
  fi
  "$ADB" push -q "$bin" /data/local/tmp/sonykey && "$ADB" shell chmod 755 /data/local/tmp/sonykey
}

start() {
  [ -f "$HOME/.android/avd/$AVD.avd/config.ini" ] || die "no AVD yet: tools/emulator.sh setup"
  if ! "$ADB" get-state >/dev/null 2>&1; then
    mkdir -p out/emulator
    nohup "$EMU" -avd "$AVD" -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect > out/emulator/emulator.log 2>&1 &
    "$ADB" wait-for-device
  fi
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
  if [ "$("$ADB" shell id -u | tr -d '\r')" != 0 ]; then   # root for /dev/uinput; adbd restarts, and the old link can stall
    "$ADB" root >/dev/null; sleep 2; "$ADB" kill-server; "$ADB" wait-for-device
  fi
  sonykey
  echo "booted: $("$ADB" shell wm size | tr -d '\r'), $("$ADB" shell wm density | tr -d '\r')"
}

install() {
  local apk="${1:-RecipeLab.apk}" w=out/emulator/apk
  [ -f "$apk" ] || die "no $apk: build it (./build.sh) or pass one, e.g. a CI artifact"
  rm -rf "$w" && mkdir -p "$w"
  cp "$apk" "$w/in.apk"
  # the native library cannot load here, and the camera's signature lists it: both go, and a throwaway key signs again
  zip -q -d "$w/in.apk" 'lib/*' 'META-INF/*' || [ $? -eq 12 ]   # 12: neither was there (an unsigned, Java-only build)
  [ -f "$KEYTOOL_STORE" ] || keytool -genkeypair -keystore "$KEYTOOL_STORE" -alias emulator -keyalg RSA -keysize 2048 \
      -validity 10000 -storepass android -keypass android -dname "CN=Recipe Lab emulator" >/dev/null 2>&1
  "$BT/zipalign" -f 4 "$w/in.apk" "$w/aligned.apk"
  "$BT/apksigner" sign --ks "$KEYTOOL_STORE" --ks-pass pass:android --v2-signing-enabled false --v3-signing-enabled false \
      --min-sdk-version 10 --out "$w/emulator.apk" "$w/aligned.apk"
  # -d: one branch's dev build after another's is often a lower versionCode, and switching builds is the point here
  if ! "$ADB" install -r -d "$w/emulator.apk" 2>&1 | tee "$w/install.log" | grep -q Success; then
    grep -q UPDATE_INCOMPATIBLE "$w/install.log" || { cat "$w/install.log" >&2; die "install failed"; }
    "$ADB" uninstall com.voxivoid.recipelab >/dev/null       # installed under another key: it has to go first
    "$ADB" install "$w/emulator.apk" | grep -q Success || die "install failed"
  fi
  "$ADB" shell am start -n com.voxivoid.recipelab/.MainActivity >/dev/null
}

# scan codes, as Keys.java names them
code() {
  case "$1" in
    UP) echo 103 ;; DOWN) echo 108 ;; LEFT) echo 105 ;; RIGHT) echo 106 ;; ENTER) echo 232 ;; MENU) echo 514 ;;
    SK1) echo 229 ;; SK2) echo 513 ;; TRASH|DELETE) echo 595 ;; PLAY) echo 207 ;; DISP) echo 608 ;; FN) echo 520 ;;
    AEL) echo 532 ;; C1) echo 622 ;; S1) echo 516 ;; S2) echo 518 ;; MOVIE) echo 515 ;;
    WHEEL_CW) echo 522 ;; WHEEL_CCW) echo 523 ;; DIAL_CW) echo 525 ;; DIAL_CCW) echo 526 ;;
    *[!0-9]*|'') die "unknown key $1" ;;
    *) echo "$1" ;;
  esac
}

key() {
  [ $# -gt 0 ] || die "key KEY[:MS] ..."
  local args=() k
  for k in "$@"; do
    if [[ "$k" == *:* ]]; then args+=("$(code "${k%%:*}"):${k#*:}"); else args+=("$(code "$k")"); fi
  done
  "$ADB" shell /data/local/tmp/sonykey "${args[@]}"
}

case "${1:-}" in
  setup) setup ;;
  start) start ;;
  install) shift; install "$@" ;;
  key) shift; key "$@" ;;
  shot) [ -n "${2:-}" ] || die "shot FILE.png"; "$ADB" exec-out screencap -p > "$2" ;;
  *) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
