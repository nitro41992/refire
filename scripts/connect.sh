#!/bin/bash
# Wireless ADB connection helper

LAST_IP_FILE="$HOME/.adb_last_ip"

# If IP:port provided as argument, use it
if [ -n "$1" ]; then
    IP_PORT="$1"
else
    # Check for saved IP
    if [ -f "$LAST_IP_FILE" ]; then
        LAST_IP=$(cat "$LAST_IP_FILE")
        echo "Last used: $LAST_IP"
        read -p "Press Enter to reuse, or type new IP:port: " INPUT
        IP_PORT="${INPUT:-$LAST_IP}"
    else
        read -p "Enter IP:port: " IP_PORT
    fi
fi

# Save for next time
echo "$IP_PORT" > "$LAST_IP_FILE"

# Connect
echo "Connecting to $IP_PORT..."
adb connect "$IP_PORT"

# Verify
echo ""
adb devices
