import base64
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import bark_release


class ReleaseVerificationTest(unittest.TestCase):
    def badging(self, application_id=None, name="2.9.8-bark.26", code="26"):
        return (f"package: name='{application_id or bark_release.APPLICATION_ID}' "
                f"versionCode='{code}' versionName='{name}' platformBuildVersionName='14'\n")

    def test_release_identity_matches(self):
        value = bark_release.validate_badging(self.badging(), "2.9.8-bark.26", 26)
        self.assertEqual(value["applicationId"], bark_release.APPLICATION_ID)

    def test_upstream_application_id_is_rejected(self):
        with self.assertRaises(bark_release.ReleaseError):
            bark_release.validate_badging(self.badging(application_id="com.lanbing.smsforwarder"), "2.9.8-bark.26", 26)

    def test_legacy_version_code_is_rejected(self):
        with self.assertRaises(bark_release.ReleaseError):
            bark_release.validate_badging(self.badging(code="1"), "2.9.8-bark.26", 26)

    def test_wrong_version_name_is_rejected(self):
        with self.assertRaises(bark_release.ReleaseError):
            bark_release.validate_badging(self.badging(name="2.9.6-bark.26"), "2.9.8-bark.26", 26)

    def test_debuggable_apk_is_rejected(self):
        with self.assertRaises(bark_release.ReleaseError):
            bark_release.validate_badging(self.badging() + "application-debuggable\n", "2.9.8-bark.26", 26)

    def test_matching_certificate_is_accepted(self):
        bark_release.check_certificate(":".join(bark_release.CERTIFICATE_SHA256[i:i + 2]
                                                for i in range(0, 64, 2)).upper())

    def test_different_certificate_is_rejected(self):
        with self.assertRaises(bark_release.ReleaseError):
            bark_release.check_certificate("0" * 64)

    def test_version_comes_from_one_configuration_file(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "gradle.properties").write_text("barkVersionName=2.9.8\nbarkVersionCode=27\n")
            self.assertEqual(bark_release.read_version(root), ("2.9.8-bark.27", 27))

    def test_invalid_versions_are_rejected(self):
        for name, code in [("", "26"), ("2.9.8", "0"), ("2.9.8", "-1"),
                           ("2.9.8", "1\\ninjected=yes"), ("2.9.8", "2100000001")]:
            with self.subTest(name=name, code=code), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                (root / "gradle.properties").write_text(f"barkVersionName={name}\nbarkVersionCode={code}\n")
                with self.assertRaises(bark_release.ReleaseError):
                    bark_release.read_version(root)

    def test_missing_signing_secrets_fail_before_writing_a_key(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(bark_release.ReleaseError, "Missing repository secrets"):
                bark_release.prepare_signing()

    def test_malformed_base64_fails_before_writing_a_key(self):
        with tempfile.TemporaryDirectory() as folder:
            key = Path(folder) / "key.jks"
            values = {"KEYSTORE_BASE64": "not valid base64!", "KEYSTORE_PASSWORD": "test-only",
                      "KEY_ALIAS": "test-only", "KEY_PASSWORD": "test-only", "KEYSTORE_FILE": str(key)}
            with patch.dict(os.environ, values, clear=True):
                with self.assertRaisesRegex(bark_release.ReleaseError, "Base64"):
                    bark_release.prepare_signing()
            self.assertFalse(key.exists())

    def test_repository_key_path_is_rejected(self):
        values = {"KEYSTORE_BASE64": base64.b64encode(b"test-only").decode(),
                  "KEYSTORE_PASSWORD": "test-only", "KEY_ALIAS": "test-only", "KEY_PASSWORD": "test-only",
                  "KEYSTORE_FILE": str(bark_release.ROOT / "signing/key.jks")}
        with patch.dict(os.environ, values, clear=True):
            with self.assertRaisesRegex(bark_release.ReleaseError, "outside the repository"):
                bark_release.prepare_signing()


if __name__ == "__main__":
    unittest.main()
