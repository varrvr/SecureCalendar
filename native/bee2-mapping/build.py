"""Build the fixed-input BAKE mapping; does not enable a PSI protocol."""
import argparse
import json
import hashlib
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--bee2", type=Path, required=True)
parser.add_argument("--ndk", type=Path)
parser.add_argument("--cmake", default="cmake")
parser.add_argument("--ninja", default="ninja")
args = parser.parse_args()
for name in ("cmake", "ninja"):
    value = shutil.which(getattr(args, name))
    if not value:
        raise SystemExit("Missing build tool: " + name)
    setattr(args, name, str(Path(value).resolve()))
source = Path(__file__).resolve().parent
root = source.parents[1]
build = root / ".tools" / ("bee2-mapping-arm64" if args.ndk else "bee2-mapping-host")
revision = subprocess.check_output(["git", "-C", str(args.bee2), "rev-parse", "HEAD"], text=True).strip()
expected = json.loads((source / "upstream.json").read_text())["commit"]
if revision != expected:
    raise SystemExit("Unexpected Bee2 checkout: fetch the exact revision from upstream.json")
command = [args.cmake, "-S", str(source), "-B", str(build), "-G", "Ninja",
           "-DCMAKE_MAKE_PROGRAM=" + str(Path(args.ninja).resolve()),
           "-DBEE2_SOURCE_DIR=" + str(args.bee2.resolve()), "-DCMAKE_BUILD_TYPE=Release"]
if args.ndk:
    command += ["-DCMAKE_TOOLCHAIN_FILE=" + str(args.ndk.resolve() / "build/cmake/android.toolchain.cmake"),
                "-DANDROID_ABI=arm64-v8a", "-DANDROID_PLATFORM=android-24"]
subprocess.run(command, check=True)
subprocess.run([args.cmake, "--build", str(build), "--parallel", "4"], check=True)
if args.ndk:
    target = root / "app/src/main/jniLibs/arm64-v8a/libbee2mapping.so"
    shutil.copy2(build / "libbee2mapping.so", target)
    metadata = json.loads((source / "upstream.json").read_text())
    metadata.update({"abi": "arm64-v8a", "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        "sources": {name: hashlib.sha256((source / name).read_bytes()).hexdigest()
                    for name in ["mapping.c", "mapping.h", "oprf_point.c", "oprf_point.h", "hash_group.c", "hash_group.h", "mapping_jni.c", "CMakeLists.txt"]}})
    (source / "artifact.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print("Built separate mapping library; the existing bee2bridge was not modified")
else:
    ctest = str(Path(args.cmake).resolve().with_name("ctest"))
    subprocess.run([ctest, "--test-dir", str(build), "--output-on-failure"], check=True)
