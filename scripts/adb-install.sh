#!/bin/bash
# Install script: pushes APKs + WADs to Android phone
# Requirements: USB debugging enabled, adb connected
# Usage: bash adb-install.sh

set -e

ADB=${ADB:-adb}
APK_DIR="$HOME/android-toolchain/apk"
WAD_DIR="/tmp"

# Check adb
if ! $ADB devices 2>/dev/null | grep -q 'device$'; then
    echo "❌ No Android device connected via adb"
    echo "   Connect phone via USB with USB debugging enabled"
    echo "   Then run: adb devices"
    exit 1
fi

echo "=== Installing APKs ==="

# Map game -> package (the APK determines the package name)
for game in doom heretic hexen strife setup; do
    apk="$APK_DIR/chocolate-${game}.apk"
    pkg="com.chocolate.${game}"
    # doom APK uses com.chocolate.doom not com.chocolate.doom
    [ "$game" = "doom" ] && pkg="com.chocolate.doom"
    
    if [ ! -f "$apk" ]; then
        echo "⚠️  Skipping $game: APK not found at $apk"
        continue
    fi
    
    echo "--- Installing $pkg ---"
    $ADB install -r "$apk" 2>&1 || echo "⚠️  Install failed for $pkg (may already be installed)"
    
    # Create data directory for WADs
    echo "--- Setting up WAD directory for $pkg ---"
    $ADB shell "mkdir -p /sdcard/Android/data/$pkg/files/" 2>/dev/null
done

echo ""
echo "=== Pushing WAD files ==="

# Helper: push WAD to all relevant packages
push_wad() {
    local wad="$1"
    local name="$2"
    if [ ! -f "$wad" ]; then
        echo "⚠️  $name not found at $wad"
        return
    fi
    echo "Pushing $name ($(ls -lh "$wad" | awk '{print $5}'))..."
    
    case "$name" in
        freedoom1.wad|freedoom2.wad|doom.wad|doom2.wad)
            $ADB push "$wad" "/sdcard/Android/data/com.chocolate.doom/files/" 2>&1
            ;;
        freedoom1.wad|doom.wad)
            $ADB push "$wad" "/sdcard/Android/data/com.chocolate.heretic/files/" 2>&1 | tail -1
            ;;
        freedoom2.wad|doom2.wad|freedm.wad)
            for pkg in com.chocolate.doom com.chocolate.strife; do
                $ADB push "$wad" "/sdcard/Android/data/$pkg/files/" 2>&1 | tail -1
            done
            ;;
    esac
}

# FreeDM (deathmatch WAD - works with doom engine)
if [ -f "$WAD_DIR/freedm-0.13.0/freedm.wad" ]; then
    echo "--- FreeDM ---"
    $ADB push "$WAD_DIR/freedm-0.13.0/freedm.wad" "/sdcard/Android/data/com.chocolate.doom/files/doom2.wad" 2>&1 | tail -1
    echo "  (pushed freedm.wad as doom2.wad for DOOM)"
fi

# Freedoom (when available)
FREEDOOM_ZIP="$WAD_DIR/freedoom-0.13.0.zip"
if [ -f "$FREEDOOM_ZIP" ]; then
    echo "--- Freedoom ---"
    TMPDIR=$(mktemp -d)
    unzip -qo "$FREEDOOM_ZIP" -d "$TMPDIR/"
    for wad in "$TMPDIR"/freedoom-*/freedoom*.wad; do
        if [ -f "$wad" ]; then
            name=$(basename "$wad")
            echo "  Pushing $name..."
            # Rename to what the engines expect
            case "$name" in
                freedoom1.wad)
                    $ADB push "$wad" "/sdcard/Android/data/com.chocolate.doom/files/doom.wad" 2>&1 | tail -1
                    ;;
                freedoom2.wad)
                    $ADB push "$wad" "/sdcard/Android/data/com.chocolate.doom/files/doom2.wad" 2>&1 | tail -1
                    ;;
            esac
        fi
    done
    rm -rf "$TMPDIR"
fi

# Minimal test WADs as fallback
for wad in "$WAD_DIR/doom.wad" "$WAD_DIR/doom2.wad"; do
    if [ -f "$wad" ]; then
        name=$(basename "$wad")
        echo "--- Test $name ---"
        $ADB push "$wad" "/sdcard/Android/data/com.chocolate.doom/files/$name" 2>&1 | tail -1
    fi
done

echo ""
echo "=== ✅ Setup complete! ==="
echo ""
echo "Now try launching:"
echo "  doom:    adb shell am start -n com.chocolate.doom/.ChocolateDoom"
echo "  heretic: adb shell am start -n com.chocolate.heretic/.ChocolateHeretic"
echo "  hexen:   adb shell am start -n com.chocolate.hexen/.ChocolateHexen"
echo "  strife:  adb shell am start -n com.chocolate.strife/.ChocolateStrife"
echo "  setup:   adb shell am start -n com.chocolate.setup/.ChocolateSetup"
echo ""
echo "Or tap the app icons on your phone."
echo "Chocolate Doom will look for WADs in: /sdcard/Android/data/<package>/files/"
