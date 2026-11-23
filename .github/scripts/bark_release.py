"""Restore the permanent Bark signing key and verify a public APK artifact."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
APPLICATION_ID = "com.lanbing.smsforwarder.bark"
# Public certificate fingerprint from the existing Bark APK and BARK_README.txt.
CERTIFICATE_SHA256 = "e7c52e6d0ff2e04c8fe05c691a72f9548b934ee4c2b9f91a0f7b91d3a8d3f039"
SECRET_NAMES = ("KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")


class ReleaseError(Exception):
    pass


def read_version(root: Path = ROOT) -> tuple[str, int]:
    values = {}
    for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    base_version = values.get("barkVersionName", "")
    code = values.get("barkVersionCode", "")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){2}", base_version):
        raise ReleaseError("Set barkVersionName to a base version such as 2.9.8 in gradle.properties.")
    if not re.fullmatch(r"[1-9][0-9]*", code) or int(code) > 2100000000:
        raise ReleaseError("Set barkVersionCode to a positive release number in gradle.properties.")
    return f"{base_version}-bark.{int(code)}", int(code)


def check_certificate(digest: str) -> None:
    if digest.replace(":", "").strip().lower() != CERTIFICATE_SHA256:
        raise ReleaseError("Signing certificate differs from the permanent Bark key; restore the original Bark backup.")


def prepare_signing() -> None:
    missing = [name for name in SECRET_NAMES if not os.environ.get(name)]
    if missing:
        raise ReleaseError("Missing repository secrets: " + ", ".join(missing))
    destination = os.environ.get("KEYSTORE_FILE")
    if not destination:
        raise ReleaseError("KEYSTORE_FILE must point to a temporary file outside the repository.")
    key_file = Path(destination).resolve()
    if key_file.is_relative_to(ROOT):
        raise ReleaseError("The CI signing key must be restored outside the repository.")
    try:
        encoded = "".join(os.environ["KEYSTORE_BASE64"].split())
        key_bytes = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        raise ReleaseError("KEYSTORE_BASE64 must be the Base64 encoding of the original Bark keystore.") from None
    if not key_bytes:
        raise ReleaseError("The signing keystore is empty.")
    key_file.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(key_file, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "wb") as handle:
        handle.write(key_bytes)
    key_file.chmod(0o600)
    try:
        result = subprocess.run(
            ["keytool", "-exportcert", "-keystore", str(key_file),
             "-storepass:env", "KEYSTORE_PASSWORD", "-alias", os.environ["KEY_ALIAS"]],
            capture_output=True, check=False,
        )
        if result.returncode != 0:
            raise ReleaseError("Cannot open the signing key; check KEYSTORE_BASE64, KEYSTORE_PASSWORD and KEY_ALIAS.")
        check_certificate(hashlib.sha256(result.stdout).hexdigest())
    except Exception:
        key_file.unlink(missing_ok=True)
        raise
    print("Permanent Bark signing certificate verified.")


def build_tools() -> Path:
    override = os.environ.get("BARK_BUILD_TOOLS_DIR")
    if override:
        candidates = [Path(override)]
    else:
        sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
        if not sdk:
            raise ReleaseError("Set ANDROID_SDK_ROOT or ANDROID_HOME to an Android SDK installation.")
        candidates = sorted((Path(sdk) / "build-tools").glob("*"),
                            key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.name)), reverse=True)
    for directory in candidates:
        if (directory / "aapt").is_file() and (directory / "apksigner").is_file():
            return directory
    raise ReleaseError("Android SDK Build Tools containing aapt and apksigner are required.")


def validate_badging(badging: str, version_name: str, version_code: int) -> dict:
    package = next((line for line in badging.splitlines() if line.startswith("package:")), "")
    attributes = dict(re.findall(r"([A-Za-z][A-Za-z0-9]*)='([^']*)'", package))
    if attributes.get("name") != APPLICATION_ID:
        raise ReleaseError("APK application ID does not match the Bark application.")
    if attributes.get("versionName") != version_name or attributes.get("versionCode") != str(version_code):
        raise ReleaseError("APK version does not match gradle.properties.")
    if any(line.startswith("application-debuggable") for line in badging.splitlines()):
        raise ReleaseError("A debuggable APK cannot be published as the Bark release.")
    return {"applicationId": APPLICATION_ID, "versionName": version_name, "versionCode": version_code}


def verify_apk() -> None:
    version_name, version_code = read_version()
    apks = list((ROOT / "app/build/outputs/apk/bark").glob("*.apk"))
    if len(apks) != 1:
        raise ReleaseError("Expected exactly one APK in app/build/outputs/apk/bark/.")
    apk = apks[0]
    tools = build_tools()
    badging = subprocess.run([str(tools / "aapt"), "dump", "badging", str(apk)],
                             capture_output=True, text=True, check=True).stdout
    metadata = validate_badging(badging, version_name, version_code)
    signed = subprocess.run([str(tools / "apksigner"), "verify", "--print-certs", str(apk)],
                            capture_output=True, text=True, check=True).stdout
    digests = re.findall(r"Signer #[0-9]+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", signed)
    if len(digests) != 1:
        raise ReleaseError("Expected exactly one APK signing certificate.")
    check_certificate(digests[0])
    output = ROOT / "release-artifacts"
    output.mkdir(exist_ok=True)
    filename = f"smsforwarder-{version_name}.apk"
    destination = output / filename
    shutil.copyfile(apk, destination)
    metadata.update({"certificateSha256": CERTIFICATE_SHA256,
                     "apkSha256": hashlib.sha256(destination.read_bytes()).hexdigest()})
    (output / "SHA256SUMS.txt").write_text(f"{metadata['apkSha256']}  {filename}\n", encoding="utf-8")
    (output / "release-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    if os.environ.get("GITHUB_OUTPUT"):
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as handle:
            handle.write(f"artifact_name=smsforwarder-{version_name}\n")
    print(f"Verified {filename}: application ID, version, release mode and permanent signature match.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare-signing", "verify-apk"))
    args = parser.parse_args()
    try:
        if args.command == "prepare-signing":
            prepare_signing()
        else:
            verify_apk()
    except (ReleaseError, OSError, subprocess.CalledProcessError) as exc:
        message = str(exc) if isinstance(exc, ReleaseError) else "A required release tool failed; check Android SDK and Java."
        print(f"::error::{message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
