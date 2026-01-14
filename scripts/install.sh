#!/bin/bash
# Quick build and install

cd "$(dirname "$0")/.." || exit 1

# Check device connected
DEVICE_COUNT=$(adb devices | grep -v "List" | grep -c "device$")
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "No device connected. Run ./scripts/connect.sh first"
    exit 1
fi

echo "Building and installing..."
./gradlew installDebug --console=plain 2>&1 | grep -E "(BUILD|FAILURE|Installing|> Task :app:(compile|install))"

if [ $? -eq 0 ]; then
    echo ""
    echo "Installed!"
fi
