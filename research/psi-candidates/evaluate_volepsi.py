"""Evaluate the pinned candidate without installing it in the Android app."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time

EXPECTED = "1ecfe796c661085f21d37c7945bba11e9f54e04d"
NAMES = ["Psi_Rs_mal_test", "RsOprf_mal_test", "RsOprf_salt_order_test",
         "RsOprf_seed_rejection_test", "RsOprf_ribbon_seed_test",
         "RsOprf_localAbort_test", "RsOprf_peerAbort_test"]
parser = argparse.ArgumentParser()
parser.add_argument("--source", type=Path, required=True)
parser.add_argument("--build", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
revision = subprocess.check_output(["git", "-C", str(args.source), "rev-parse", "HEAD"], text=True).strip()
if revision != EXPECTED:
    raise SystemExit("Candidate version differs from the reviewed revision")
command = [str(args.build / "frontend/frontend"), "-u", *NAMES, "-n", "257"]
started = time.monotonic()
try:
    result = subprocess.run(command, capture_output=True, text=True, timeout=180)
    clean = re.sub(r"\x1b\[[0-9;]*m", "", result.stdout + result.stderr)
    passed = [name for name in NAMES if re.search(re.escape(name) + r"\s+Passed", clean)]
    success = result.returncode == 0 and len(passed) == len(NAMES) and "All Passed (7)" in clean
    exit_code = result.returncode
except subprocess.TimeoutExpired:
    clean = "Candidate tests timed out"
    passed = []
    success = False
    exit_code = 124
report = {"repository": "https://github.com/ladnir/volepsi", "commit": revision,
          "tests_passed": passed, "success": success, "exit_code": exit_code,
          "elapsed_seconds": round(time.monotonic() - started, 2),
          "android_backend_enabled": False, "independent_security_audit": False}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2) + "\n")
args.output.with_suffix(".log").write_text(clean)
print(json.dumps(report, indent=2))
raise SystemExit(0 if success else 1)
