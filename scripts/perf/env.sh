export MSYS_NO_PATHCONV=1
export ADB="${ADB:-/c/Users/Lenovo/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
export SERIAL="${SERIAL:-UCDADEIFV445AYJ7}"
export PKG="${PKG:-com.shiny.music.debug}"
export ACT="$PKG/com.shiny.music.MainActivity"
export OUT="${OUT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/_local/perf}"
mkdir -p "$OUT"

a() { "$ADB" -s "$SERIAL" "$@"; }
s() { "$ADB" -s "$SERIAL" shell "$@" | tr -d '\r'; }

focused() { s "dumpsys window | grep -m1 mCurrentFocus"; }
is_ours() { focused | grep -q "$PKG"; }
require_ours() {
  is_ours || { echo "STOP: $PKG is not focused: $(focused)" >&2; exit 3; }
}

apk_hash() { s "sha1sum \$(pm path $PKG | head -1 | cut -d: -f2)" | cut -d' ' -f1; }

conditions() {
  local level temp status volt thermal
  level=$(s "dumpsys battery | grep -m1 ' level:'" | awk '{print $2}')
  temp=$(s "dumpsys battery | grep -m1 temperature" | awk '{print $2}')
  status=$(s "dumpsys battery | grep -m1 ' status:'" | awk '{print $2}')
  thermal=$(s "dumpsys thermalservice | grep -m1 'Thermal Status'" | awk '{print $3}')
  echo "battery=$level status=$status temp=$temp thermal=$thermal apk=$(apk_hash)"
}

wait_cool() {
  local limit="${1:-380}" tries=0
  while :; do
    local t
    t=$(s "dumpsys battery | grep -m1 temperature" | awk '{print $2}')
    [ "$t" -le "$limit" ] && return 0
    tries=$((tries + 1))
    [ "$tries" -gt 60 ] && { echo "still hot: $t" >&2; return 1; }
    sleep 20
  done
}
