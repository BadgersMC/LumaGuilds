"""Verify real process status, argument forwarding and bounded retry classification."""

import json
import sys
import tempfile
import unittest
from pathlib import Path

from gradle_dependency_retry import run_gradle, run_with_retries


class DependencyRetryTest(unittest.TestCase):
    def run_scenario(self, mode):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fake = root / "fake_gradle.py"
            fake.write_text(
                "import json, sys\n"
                "from pathlib import Path\n"
                "calls = Path('calls')\n"
                "count = int(calls.read_text()) + 1 if calls.exists() else 1\n"
                "calls.write_text(str(count))\n"
                "Path('arguments').write_text(json.dumps(sys.argv[1:]))\n"
                f"mode = '{mode}'\n"
                "if mode == 'transient' and count >= 3: sys.exit(0)\n"
                "if mode == 'tests': print('> Task :test FAILED'); sys.exit(7)\n"
                "if mode == 'source': print('e: file.kt: type mismatch'); sys.exit(8)\n"
                "print(\"Could not resolve all files for configuration ':compileClasspath'.\")\n"
                "artifact = 'unknown.jar' if mode == 'unknown' else 'AxKothAPI-4.jar'\n"
                "print('Could not download ' + artifact)\n"
                "if mode == 'permanent': print('HTTP 404'); sys.exit(6)\n"
                "print('Premature end of Content-Length delimited message body')\n"
                "if mode == 'reached_tests': print('> Task :test FAILED')\n"
                "if mode == 'mixed_source': print('e: file.kt: type mismatch')\n"
                "sys.exit(9)\n",
                encoding="utf-8",
            )
            command = [sys.executable, str(fake), "test", "-Pexample=a b", "--console=plain"]
            status = run_with_retries(
                command,
                runner=lambda invocation: run_gradle(invocation, cwd=root),
                pause=lambda duration: None,
            )
            arguments = json.loads((root / "arguments").read_text(encoding="utf-8"))
            self.assertEqual(["test", "-Pexample=a b", "--console=plain"], arguments)
            return status, int((root / "calls").read_text(encoding="utf-8"))

    def test_transient_then_success(self):
        self.assertEqual((0, 3), self.run_scenario("transient"))

    def test_retry_has_a_finite_limit(self):
        self.assertEqual((9, 3), self.run_scenario("persistent"))

    def test_test_failure_is_not_retried(self):
        self.assertEqual((7, 1), self.run_scenario("tests"))

    def test_source_error_is_not_retried(self):
        self.assertEqual((8, 1), self.run_scenario("source"))

    def test_permanent_error_is_not_retried(self):
        self.assertEqual((6, 1), self.run_scenario("permanent"))

    def test_test_start_prevents_retry(self):
        self.assertEqual((9, 1), self.run_scenario("reached_tests"))

    def test_unknown_artifact_not_retried(self):
        self.assertEqual((9, 1), self.run_scenario("unknown"))

    def test_mixed_source_error_no_retry(self):
        self.assertEqual((9, 1), self.run_scenario("mixed_source"))


if __name__ == "__main__":
    unittest.main()
