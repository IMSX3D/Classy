"""Host-side alarm delivery check. Only an isolated, empty Android emulator is allowed.

Instrumentation force-stops its target. Prepare fixtures, start normally to restore alarms,
kill the background process (not force-stop), inspect system records, then clean up.
Never run this against user data; never change the device clock to simulate delivery.
"""
import argparse
import datetime
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--reboot", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        raise SystemExit("Emulator only")
    args.output.mkdir(parents=True, exist_ok=False)
    package = "com.imsx3d.classy.debug"

    def adb(*command):
        return subprocess.check_output([args.adb, "-s", args.serial, *command], timeout=60).decode("utf-8", errors="replace")

    def prefs(name):
        root = ET.fromstring(adb("shell", "run-as", package, "cat", f"shared_prefs/{name}.xml"))
        return {n.get("name"): n.text or n.get("value") for n in root}

    def test(method):
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "coldDelivery", "true",
                     "-e", "leadMinutes", "6" if args.reboot else "2",
                     "-e", "class", "com.imsx3d.classy.ui.ReminderColdStartTest#" + method,
                     package + ".test/androidx.test.runner.AndroidJUnitRunner")
        (args.output / f"{method}.txt").write_text(result, encoding="utf-8")
        if "OK (1 test)" not in result or "AssumptionViolatedException" in result:
            raise RuntimeError(f"{method} failed or skipped")

    test("prepare")
    try:
        adb("shell", "am", "start", "-W", "-n", package + "/com.imsx3d.classy.MainActivity")
        time.sleep(3)
        before = prefs("course_alarm_registry")["generation"]
        alarm_time = prefs("sleepy_prefs")["daily_reminder_time"]
        # Device local clock, not the host's timezone. Compute the remaining real interval.
        local = datetime.datetime.fromisoformat(adb("shell", "date", "+%Y-%m-%dT%H:%M:%S").strip())
        target = datetime.datetime.combine(local.date(), datetime.time.fromisoformat(alarm_time))
        if target < local:
            raise RuntimeError("Preparation overran its alarm window; rerun after cleanup")
        remaining = (target - local).total_seconds() + 25
        deadline = time.monotonic() + remaining
        adb("shell", "input", "keyevent", "3")
        if args.reboot:
            adb("reboot")
            time.sleep(10)
            adb("wait-for-device")
            boot_deadline = time.monotonic() + 240
            while adb("shell", "getprop", "sys.boot_completed").strip() != "1":
                if time.monotonic() > boot_deadline:
                    raise RuntimeError("Emulator boot timed out")
                time.sleep(1)
            adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            adb("shell", "wm", "dismiss-keyguard")
        else:
            time.sleep(2)  # Let foreground-exit lifecycle and pending widget refresh finish.
            adb("shell", "am", "kill", package)
            time.sleep(1)
            # pidof has exit status 1 for a successfully killed process.
            pid = subprocess.run([args.adb, "-s", args.serial, "shell", "pidof", package], capture_output=True)
            if pid.stdout.strip():
                # am kill skips temporarily important processes. Debug run-as can kill its own
                # exact PID without setting Android's stopped flag or deleting scheduled alarms.
                target_pid = pid.stdout.decode().strip()
                if not target_pid.isdigit():
                    raise RuntimeError("Unexpected process identity")
                adb("shell", "run-as", package, "kill", "-9", target_pid)
                time.sleep(1)
                pid = subprocess.run([args.adb, "-s", args.serial, "shell", "pidof", package], capture_output=True)
                if pid.stdout.strip():
                    raise RuntimeError("Process restarted before alarm; not a cold-delivery sample")
        print(f"Waiting for {alarm_time} delivery plus 25 seconds", flush=True)
        while time.monotonic() < deadline:
            time.sleep(min(1, deadline - time.monotonic()))
        records = adb("shell", "dumpsys", "notification")
        ids = sorted({int(n) for n in re.findall(
            r"NotificationRecord[^\n]*pkg=" + re.escape(package) + r"\s[^\n]*?id=(\d+)", records)})
        after = prefs("course_alarm_registry")["generation"]
        result = {"mode": "reboot" if args.reboot else "cold-process", "notification_ids": ids,
                  "generation_preserved": before == after}
        (args.output / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        assert {1001, 1002, 2001}.issubset(ids), result
        if not args.reboot:
            assert before == after, "Cold Application invalidated the scheduled generation"
        print(json.dumps(result), flush=True)
    finally:
        test("restoreFixture")


if __name__ == "__main__":
    main()
