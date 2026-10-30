import os
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import rollout  # noqa: E402

DIGEST = "us-central1-docker.pkg.dev/p/r/api@sha256:2d89ce7ff5c3b4ecd87f4f3ee597d3a8198cee1eed3172bb85707dfab438ae0f"


class RevisionSuffixTest(unittest.TestCase):
    def test_includes_track_version_and_digest(self):
        self.assertEqual(rollout.revision_suffix("canary", "4.0.0", DIGEST), "canary-400-2d89ce7f")

    def test_same_image_gives_the_same_revision(self):
        self.assertEqual(rollout.revision_suffix("stable", "3.1.0", DIGEST),
                         rollout.revision_suffix("stable", "3.1.0", DIGEST))

    def test_prerelease_versions_stay_valid_names(self):
        suffix = rollout.revision_suffix("canary", "4.0.0-rc.1", DIGEST)
        self.assertRegex(suffix, r"^[a-z0-9-]+$")

    def test_extra_marker_is_appended(self):
        self.assertTrue(rollout.revision_suffix("canary", "4.0.0", DIGEST, "faulty").endswith("-faulty"))


class RuntimeEnvTest(unittest.TestCase):
    def setUp(self):
        patcher = mock.patch.dict(os.environ, {"OCI_TENANCY_ID": "t", "OCI_USER_ID": "u", "OCI_FINGERPRINT": "f"})
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_stable_never_enables_the_fault_header(self):
        env = rollout.runtime_env("stable", "3.1.0")
        self.assertEqual(env["APP_TRACK"], "stable")
        self.assertNotIn("FAULT_HEADER_ENABLED", env)

    def test_canary_enables_the_fault_header_and_reports_its_version(self):
        env = rollout.runtime_env("canary", "4.0.0")
        self.assertEqual(env["APP_TRACK"], "canary")
        self.assertEqual(env["APP_VERSION"], "4.0.0")
        self.assertEqual(env["FAULT_HEADER_ENABLED"], "true")

    def test_secrets_are_never_part_of_the_plain_environment(self):
        env = rollout.runtime_env("canary", "4.0.0")
        for forbidden in ("DB_PASSWORD", "DB_URL", "OCI_PRIVATE_KEY"):
            self.assertNotIn(forbidden, env)


class TrafficTest(unittest.TestCase):
    def allocation(self, percent):
        with mock.patch.object(rollout, "gcloud") as gcloud:
            rollout.set_traffic(percent)
        return gcloud.call_args.args[-2]

    def test_zero_percent_sends_everything_to_stable(self):
        self.assertEqual(self.allocation(0), "stable=100")

    def test_split(self):
        self.assertEqual(self.allocation(10), "stable=90,canary=10")

    def test_full_promotion(self):
        self.assertEqual(self.allocation(100), "canary=100")


if __name__ == "__main__":
    unittest.main()
