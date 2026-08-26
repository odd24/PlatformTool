#!/system/bin/sh

PACKAGE="com.example.platformtool"
PID_FILE="/data/local/tmp/platformtool-kernel-bridge.pid"
QUICK_PID_FILE="/data/local/tmp/platformtool-quick-tools-bridge.pid"

kill_tree() {
    PARENT_PID="$1"
    for CHILD_PID in $(ps -A -o PID,PPID 2>/dev/null | \
        awk -v parent="$PARENT_PID" 'NR > 1 && $2 == parent {print $1}'); do
        kill_tree "$CHILD_PID"
    done
    kill "$PARENT_PID" 2>/dev/null
}

stop_bridge() {
    if [ -f "$PID_FILE" ]; then
        OLD_PID="$(cat "$PID_FILE" 2>/dev/null)"
        if [ -n "$OLD_PID" ] && [ -r "/proc/$OLD_PID/cmdline" ]; then
            OLD_COMMAND="$(tr '\000' ' ' < "/proc/$OLD_PID/cmdline" 2>/dev/null)"
            case "$OLD_COMMAND" in
                *"run-as $PACKAGE"*"files/kernel.log"*) kill_tree "$OLD_PID" ;;
            esac
        fi
        rm -f "$PID_FILE"
    fi
    if [ -f "$QUICK_PID_FILE" ]; then
        QUICK_PID="$(cat "$QUICK_PID_FILE" 2>/dev/null)"
        if [ -n "$QUICK_PID" ] && [ -r "/proc/$QUICK_PID/cmdline" ]; then
            QUICK_COMMAND="$(tr '\000' ' ' < "/proc/$QUICK_PID/cmdline" 2>/dev/null)"
            case "$QUICK_COMMAND" in
                *"platformtool-kernel-bridge.sh quick-loop"*) kill "$QUICK_PID" 2>/dev/null ;;
            esac
        fi
        rm -f "$QUICK_PID_FILE"
    fi
    run-as "$PACKAGE" sh -c 'rm -f files/kernel.log files/kernel.bridge files/quick-tools.bridge files/quick-tools.request files/quick-tools.response' 2>/dev/null
}

quick_tools_loop() {
    while run-as "$PACKAGE" test -f files/quick-tools.bridge 2>/dev/null; do
        REQUEST="$(run-as "$PACKAGE" cat files/quick-tools.request 2>/dev/null)"
        case "$REQUEST" in
            0|1)
                if settings put system show_touches "$REQUEST" >/dev/null 2>&1 && \
                   settings put system pointer_location "$REQUEST" >/dev/null 2>&1; then
                    RESPONSE="$REQUEST:ok"
                else
                    RESPONSE="$REQUEST:error"
                fi
                run-as "$PACKAGE" sh -c "printf '%s\n' '$RESPONSE' > files/quick-tools.response.tmp && mv files/quick-tools.response.tmp files/quick-tools.response; rm -f files/quick-tools.request" 2>/dev/null
                ;;
        esac
        sleep 0.1
    done
}

if [ "$1" = "quick-loop" ]; then
    quick_tools_loop
    exit 0
fi

if [ "$1" = "stop" ]; then
    stop_bridge
    exit 0
fi

stop_bridge
BOOT_ID="$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
if [ -z "$BOOT_ID" ]; then
    echo "Cannot read the current Android boot ID."
    exit 1
fi

run-as "$PACKAGE" sh -c "mkdir -p files && : > files/kernel.log && printf '%s\\n' '$BOOT_ID' > files/kernel.bridge" || {
    echo "Cannot create app-private kernel.log. Install the debug APK first."
    exit 1
}

run-as "$PACKAGE" sh -c ': > files/quick-tools.bridge; rm -f files/quick-tools.request files/quick-tools.response' || {
    echo "Cannot create the Quick Tools bridge marker."
    exit 1
}
nohup sh "$0" quick-loop >/dev/null 2>&1 &
QUICK_PID="$!"
echo "$QUICK_PID" > "$QUICK_PID_FILE"

nohup sh -c "dmesg -w 2>&1 | run-as $PACKAGE sh -c 'cat >> files/kernel.log'; run-as $PACKAGE rm -f files/kernel.bridge" \
    >/dev/null 2>&1 &
BRIDGE_PID="$!"
echo "$BRIDGE_PID" > "$PID_FILE"

sleep 1
if kill -0 "$BRIDGE_PID" 2>/dev/null; then
    echo "Kernel bridge started, pid=$BRIDGE_PID"
    echo "Quick Tools bridge started, pid=$QUICK_PID"
    exit 0
fi

echo "Kernel bridge failed to start"
rm -f "$PID_FILE"
exit 1
