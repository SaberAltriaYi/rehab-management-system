"""Synthetic parser/contract regression; no Engine checkout needed."""

import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location(
    "verify_motion_engine_contract",
    Path(__file__).with_name("verify_motion_engine_contract.py"),
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class MotionEngineContractTests(unittest.TestCase):
    JAVA_FIXTURE = """
    FMS_TESTS = Collections.unmodifiableList(Arrays.asList("FMS_DEEP_SQUAT"));
    NASM_TESTS = Collections.unmodifiableList(Arrays.asList("NASM_OHS"));
    OTHER_TESTS = Collections.unmodifiableList(Arrays.asList("YBT_LQ", "TUCK_JUMP", "LESS"));
    BILATERAL_TESTS = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "FMS_DEEP_SQUAT", "NASM_OHS", "TUCK_JUMP", "LESS")));
    """

    def test_synthetic_matching_registry(self):
        java = MODULE.parse_java_registry(self.JAVA_FIXTURE)
        rows = [{"code": code, **details} for code, details in java.items()]
        self.assertEqual([], MODULE.compare_contract(java, rows))

    def test_unknown_side_or_code_fails_closed(self):
        java = MODULE.parse_java_registry(self.JAVA_FIXTURE)
        rows = [{"code": code, **details} for code, details in java.items()]
        rows[0] = {**rows[0], "sides": ["left", "right"]}
        rows.append({"code": "UNKNOWN_ENGINE_TEST", "family": "FMS", "sides": ["bilateral"]})
        issues = MODULE.compare_contract(java, rows)
        self.assertTrue(any("permitted side mismatch" in issue for issue in issues))
        self.assertTrue(any("Engine-only" in issue for issue in issues))

    def test_missing_java_constant_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Java constant not found"):
            MODULE.parse_java_registry(self.JAVA_FIXTURE.replace("FMS_TESTS", "NOT_FMS_TESTS"))

    FMS_FIXTURE = '''
    FMS_CONDITIONS = Collections.unmodifiableMap(new LinkedHashMap<String, List<String>>() {{
      put("FMS_DEEP_SQUAT", Collections.unmodifiableList(Arrays.asList("standard", "heels_elevated")));
      put("FMS_TSPU", Collections.unmodifiableList(Arrays.asList("high", "low")));
      put("FMS_ROTARY_STABILITY", Collections.unmodifiableList(Arrays.asList("unilateral", "diagonal")));
    }});
    '''

    def test_fms_conditions_match_ordered_engine_tiers(self):
        java = MODULE.parse_java_fms_conditions(self.FMS_FIXTURE)
        engine = {
            code: {"kind": "tiered", "tiers": [(name, grade, [], None)
                    for name, grade in zip(conditions, (3, 2))]}
            for code, conditions in java.items()
        }
        self.assertEqual([], MODULE.compare_fms_tiers(java, engine))
        self.assertEqual(3, len(java))

    def test_fms_tier_order_mismatch_and_extra_tier_fail(self):
        java = MODULE.parse_java_fms_conditions(self.FMS_FIXTURE)
        engine = {
            code: {"kind": "tiered", "tiers": [(name, grade, [], None)
                    for name, grade in zip(conditions, (3, 2))]}
            for code, conditions in java.items()
        }
        engine["FMS_TSPU"]["tiers"].reverse()
        engine["FMS_UNKNOWN"] = {"kind": "tiered", "tiers": [("a", 3), ("b", 2)]}
        issues = MODULE.compare_fms_tiers(java, engine)
        self.assertTrue(any("FMS_TSPU: FMS tier" in issue for issue in issues))
        self.assertTrue(any("FMS_UNKNOWN" in issue for issue in issues))

    def test_fms_tier_map_unrecognized_or_duplicate_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "FMS_CONDITIONS"):
            MODULE.parse_java_fms_conditions("no such Java map")
        with self.assertRaisesRegex(ValueError, "duplicate or invalid"):
            MODULE.parse_java_fms_conditions(
                self.FMS_FIXTURE.replace('"high", "low"', '"high", "high"')
            )


if __name__ == "__main__":
    unittest.main()
