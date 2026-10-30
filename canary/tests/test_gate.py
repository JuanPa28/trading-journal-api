import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gate  # noqa: E402

SLO = json.loads((Path(__file__).resolve().parents[1] / "slo.json").read_text(encoding="utf-8"))
STAGE_10 = SLO["stages"][0]
STAGE_100 = SLO["stages"][2]


def samples(track, count, ms=100, errors=0):
    items = [{"track": track, "status": 200, "ms": ms} for _ in range(count - errors)]
    items += [{"track": track, "status": 500, "ms": ms} for _ in range(errors)]
    return items


class PercentileTest(unittest.TestCase):
    def test_nearest_rank(self):
        values = list(range(1, 101))
        self.assertEqual(gate.percentile(values, 50), 50)
        self.assertEqual(gate.percentile(values, 95), 95)
        self.assertEqual(gate.percentile(values, 99), 99)

    def test_empty(self):
        self.assertEqual(gate.percentile([], 95), 0.0)


class SummarizeTest(unittest.TestCase):
    def test_counts_5xx_and_connection_failures_but_not_4xx(self):
        data = samples("stable", 8) + [
            {"track": "stable", "status": 404, "ms": 10},
            {"track": "stable", "status": 0, "ms": 10},
        ]
        stats = gate.summarize(data)
        self.assertEqual(stats["stable"]["requests"], 10)
        self.assertEqual(stats["stable"]["errors"], 1)

    def test_share_per_track(self):
        stats = gate.summarize(samples("stable", 90) + samples("canary", 10))
        self.assertAlmostEqual(stats["canary"]["share"], 0.10)


class EvaluateTest(unittest.TestCase):
    def evaluate(self, stable, canary, stage=STAGE_10, **kw):
        return gate.evaluate(stage, gate.summarize(stable + canary), SLO, **kw)

    def test_promotes_when_canary_is_as_good_as_stable(self):
        result = self.evaluate(samples("stable", 200), samples("canary", 80))
        self.assertEqual(result["decision"], gate.PROMOTE)

    def test_holds_when_canary_sample_is_too_small(self):
        result = self.evaluate(samples("stable", 200), samples("canary", 5))
        self.assertEqual(result["decision"], gate.HOLD)
        self.assertIn("insuficiente", result["reasons"][0])

    def test_holds_when_baseline_sample_is_too_small(self):
        result = self.evaluate(samples("stable", 3), samples("canary", 80))
        self.assertEqual(result["decision"], gate.HOLD)

    def test_aborts_on_error_rate_regression(self):
        result = self.evaluate(samples("stable", 200), samples("canary", 80, errors=20))
        self.assertEqual(result["decision"], gate.ABORT)

    def test_aborts_on_p95_regression(self):
        result = self.evaluate(samples("stable", 200, ms=100), samples("canary", 80, ms=900))
        self.assertEqual(result["decision"], gate.ABORT)

    def test_small_latency_noise_is_tolerated(self):
        result = self.evaluate(samples("stable", 200, ms=100), samples("canary", 80, ms=170))
        self.assertEqual(result["decision"], gate.PROMOTE)

    def test_holds_on_cpu_saturation(self):
        result = self.evaluate(samples("stable", 200), samples("canary", 80), saturation={"cpu": 0.97})
        self.assertEqual(result["decision"], gate.HOLD)

    def test_full_rollout_compares_against_previous_baseline(self):
        baseline = gate.summarize(samples("stable", 200, ms=100))["stable"]
        stats = gate.summarize(samples("canary", 200, ms=100))
        result = gate.evaluate(STAGE_100, stats, SLO, baseline=baseline)
        self.assertEqual(result["decision"], gate.PROMOTE)

    def test_full_rollout_aborts_when_canary_degrades_against_baseline(self):
        baseline = gate.summarize(samples("stable", 200, ms=100))["stable"]
        stats = gate.summarize(samples("canary", 200, ms=100, errors=40))
        result = gate.evaluate(STAGE_100, stats, SLO, baseline=baseline)
        self.assertEqual(result["decision"], gate.ABORT)


class PickBaselineTest(unittest.TestCase):
    def stats(self, requests, p95):
        return {"requests": requests, "p95": p95}

    def test_first_measurement_becomes_the_baseline(self):
        self.assertEqual(gate.pick_baseline(None, self.stats(100, 500)), self.stats(100, 500))

    def test_keeps_the_measurement_with_the_largest_sample(self):
        high_load = self.stats(1200, 915)
        lower_load = self.stats(1000, 681)
        self.assertEqual(gate.pick_baseline(high_load, lower_load), high_load)
        self.assertEqual(gate.pick_baseline(lower_load, high_load), high_load)

    def test_no_new_stable_data_keeps_the_current_baseline(self):
        current = self.stats(500, 400)
        self.assertEqual(gate.pick_baseline(current, None), current)


class ShareWarningTest(unittest.TestCase):
    def test_no_warning_when_close_to_expected(self):
        stats = gate.summarize(samples("stable", 90) + samples("canary", 10))
        self.assertIsNone(gate.share_warning(STAGE_10, stats, SLO["expected_share_tolerance"]))

    def test_warning_when_far_from_expected(self):
        stats = gate.summarize(samples("stable", 50) + samples("canary", 50))
        self.assertIsNotNone(gate.share_warning(STAGE_10, stats, SLO["expected_share_tolerance"]))


if __name__ == "__main__":
    unittest.main()
