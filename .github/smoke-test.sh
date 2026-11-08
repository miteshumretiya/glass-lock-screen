#!/usr/bin/env bash
# Installs the APK on the emulator, walks through setup, lock, wake, swipe and unlock,
# and saves screenshots plus logs to test-results/.
set -x
P=com.mitesh.glasslock
OUT=test-results
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

adb root || true
sleep 3
adb wait-for-device
APK=$(ls apk/*.apk | head -1)
adb install -r "$APK" | tee "$OUT/install.txt"

adb shell appops set $P SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener $P/$P.NotificationService
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell dumpsys deviceidle whitelist +$P
adb logcat -c

# 1. Setup screen
adb shell am start -n $P/.MainActivity
sleep 6
shot 01-setup

# Test notifications
adb shell cmd notification post -S bigtext -t "Mum" dinner "Dinner at 8? Bring the dessert please" 2>&1 | tee -a "$OUT/notify.txt"
sleep 1
adb shell cmd notification post -S bigtext -t "Alex" late "Running 5 minutes late, see you soon" 2>&1 | tee -a "$OUT/notify.txt"
sleep 2

# 2. Preview from the setup screen
adb shell am start -n $P/.LockActivity --ez preview true
sleep 5
shot 02-preview
adb shell input keyevent KEYCODE_BACK
sleep 2

# Turn the lock screen on (same as flipping the switch)
adb shell am force-stop $P
cat > /tmp/glass_lock.xml <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="enabled" value="true" />
</map>
EOF
adb push /tmp/glass_lock.xml /data/local/tmp/glass_lock.xml
APPUID=$(adb shell stat -c %u /data/data/$P | tr -d '\r')
adb shell mkdir -p /data/data/$P/shared_prefs
adb shell cp /data/local/tmp/glass_lock.xml /data/data/$P/shared_prefs/glass_lock.xml
adb shell chown -R "$APPUID:$APPUID" /data/data/$P/shared_prefs
adb shell chmod 771 /data/data/$P/shared_prefs
adb shell chmod 660 /data/data/$P/shared_prefs/glass_lock.xml
adb shell restorecon -R /data/data/$P/shared_prefs

# Secure lock with a PIN, like a real phone
adb shell locksettings set-pin 1234
adb shell am start -n $P/.MainActivity
sleep 5
shot 03-setup-enabled
adb shell dumpsys activity services $P > "$OUT/services.txt"
adb shell input keyevent KEYCODE_HOME
sleep 2

# 3. Lock (power button) and wake
adb shell input keyevent KEYCODE_POWER
sleep 5
adb shell input keyevent KEYCODE_WAKEUP
sleep 5
shot 04-wake
adb shell dumpsys activity activities | grep -E "ResumedActivity|topResumed" > "$OUT/top-after-wake.txt"

# 4. Swipe up to unlock -> system PIN prompt
adb shell input swipe 540 1750 540 450 260
sleep 4
shot 05-after-swipe

# 5. Enter the PIN
adb shell input text 1234
adb shell input keyevent KEYCODE_ENTER
sleep 5
shot 06-unlocked
adb shell dumpsys activity activities | grep -E "ResumedActivity|topResumed" > "$OUT/top-after-unlock.txt"

# 6. Second cycle to make sure it comes back every time
adb shell input keyevent KEYCODE_POWER
sleep 5
adb shell input keyevent KEYCODE_WAKEUP
sleep 5
shot 07-second-wake

adb shell dumpsys notification --noredact > "$OUT/dumpsys-notification.txt" 2>&1
adb logcat -d > "$OUT/logcat-full.txt"
grep -E "AndroidRuntime|FATAL|GlassLock|glasslock|RuntimeShader|ANR" "$OUT/logcat-full.txt" > "$OUT/logcat-app.txt" || true
exit 0
