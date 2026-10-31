#!/usr/bin/env bash
# Plays a song on the emulator, shows which queue files get rewritten, kills the process and checks the restore.
export MSYS_NO_PATHCONV=1
ADB=/c/Users/Lenovo/AppData/Local/Android/Sdk/platform-tools/adb.exe
A="$ADB -s emulator-5554"
P=com.shiny.music.debug
st() { $A shell "run-as $P sh -c 'stat -c \"%n %Y %s\" files/persistent_queue.data files/persistent_player_state.data files/persistent_automix.data'" | tr -d '\r' | sed 's#files/persistent_##; s#.data##' | awk '{printf "%s=%s(%sB) ", $1,$2,$3}'; echo; }
pos() { $A shell dumpsys media_session | tr -d '\r' | grep -A20 "package=$P" | grep -m1 -oE "state=[A-Z_]+\([0-9]+\), position=[0-9]+"; }
item() { $A shell dumpsys media_session | tr -d '\r' | grep -A25 "package=$P" | grep -m1 -E "metadata:" | cut -c1-120; }
$A shell am force-stop $P
$A shell am start -a android.intent.action.VIEW -d "https://music.youtube.com/watch?v=${1:-Rr1Cdli5nE8}" -p $P >/dev/null
for i in $(seq 1 45); do pos | grep -q PLAYING && break; sleep 2; done
echo "playing: $(pos)"
sleep 8
echo "t0  $(st)"
sleep 35
echo "t35 $(st)"
sleep 35
echo "t70 $(st)"
echo "before kill: $(pos) $(item)"
$A shell am force-stop $P
sleep 2
$A shell am start -W -n $P/com.shiny.music.MainActivity | grep -E "TotalTime"
sleep 7
echo "restored: $(pos) $(item)"
$A shell dumpsys window | grep -m1 mCurrentFocus
