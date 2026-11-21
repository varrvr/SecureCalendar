"""Fetch only the pinned upstream source into an isolated checkout."""
import argparse
import json
from pathlib import Path
import subprocess

source = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument("--dest", type=Path, default=source.parents[1] / ".tools/upstream/bee2")
args = parser.parse_args()
metadata = json.loads((source / "upstream.json").read_text())
if args.dest.exists():
    revision = subprocess.check_output(["git", "-C", str(args.dest), "rev-parse", "HEAD"], text=True).strip()
    if revision != metadata["commit"]:
        raise SystemExit("Existing checkout differs; it was not modified. Choose a new destination.")
else:
    args.dest.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["git", "init", str(args.dest)], check=True)
    subprocess.run(["git", "-C", str(args.dest), "remote", "add", "origin", metadata["repository"]], check=True)
    subprocess.run(["git", "-C", str(args.dest), "fetch", "--depth", "1", "origin", metadata["commit"]], check=True)
    subprocess.run(["git", "-C", str(args.dest), "checkout", "--detach", metadata["commit"]], check=True)
print("Bee2 source pinned to " + metadata["commit"])
