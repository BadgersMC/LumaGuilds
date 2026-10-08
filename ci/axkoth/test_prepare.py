import hashlib
import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('prepare', Path(__file__).with_name('prepare.py'))
prepare = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(prepare)


class PreparationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.target = Path(self.directory.name) / 'artifact.jar'
        self.original = b'published artifact'
        self.digest = hashlib.sha256(self.original).hexdigest()
        self.calls = 0

    def transfer(self, command, **kwargs):
        self.calls += 1
        output = Path(command[command.index('--output') + 1])
        complete = self.calls >= 8
        output.write_bytes(self.original if complete else self.original[:3])
        return subprocess.CompletedProcess(command, 0 if complete else 18, '200', '')

    def test_recovers_after_more_than_six_truncated_attempts(self):
        with patch.object(prepare.subprocess, 'run', self.transfer), patch.object(prepare.time, 'sleep'):
            prepare.prepare(self.digest, 'artifact.jar', self.target)
        self.assertEqual(self.original, self.target.read_bytes())
        self.assertEqual(8, self.calls)
        self.assertFalse(self.target.with_suffix('.jar.part').exists())

    def test_verified_cache_never_downloads(self):
        self.target.write_bytes(self.original)
        with patch.object(prepare.subprocess, 'run') as download:
            prepare.prepare(self.digest, 'artifact.jar', self.target)
        download.assert_not_called()

    def test_exhaustion_keeps_destination_and_removes_partial(self):
        self.target.write_bytes(b'old unverified bytes')
        with patch.object(prepare.subprocess, 'run', return_value=subprocess.CompletedProcess([], 18, '200', '')) as download, \
                patch.object(prepare.time, 'sleep'):
            with self.assertRaises(RuntimeError):
                prepare.prepare(self.digest, 'artifact.jar', self.target)
        self.assertEqual(b'old unverified bytes', self.target.read_bytes())
        self.assertEqual(prepare.MAX_ATTEMPTS, download.call_count)
        self.assertFalse(self.target.with_suffix('.jar.part').exists())

    def test_complete_checksum_mismatch_is_not_retried(self):
        def corrupt(command, **kwargs):
            Path(command[command.index('--output') + 1]).write_bytes(b'wrong publication')
            return subprocess.CompletedProcess(command, 0, '200', '')
        with patch.object(prepare.subprocess, 'run', side_effect=corrupt) as download:
            with self.assertRaises(RuntimeError):
                prepare.prepare(self.digest, 'artifact.jar', self.target)
        self.assertEqual(1, download.call_count)
        self.assertFalse(self.target.exists())

    def test_permanent_http_error_is_not_retried(self):
        with patch.object(prepare.subprocess, 'run', return_value=subprocess.CompletedProcess([], 22, '403', '')) as download:
            with self.assertRaises(RuntimeError):
                prepare.prepare(self.digest, 'artifact.jar', self.target)
        self.assertEqual(1, download.call_count)

    def test_deadline_prevents_another_attempt(self):
        with patch.object(prepare.time, 'monotonic', side_effect=[0, 0, 121]), \
                patch.object(prepare.time, 'sleep'), \
                patch.object(prepare.subprocess, 'run', return_value=subprocess.CompletedProcess([], 18, '200', '')) as download:
            with self.assertRaises(RuntimeError):
                prepare.prepare(self.digest, 'artifact.jar', self.target)
        self.assertEqual(1, download.call_count)

    def test_gateway_failure_then_success_retries(self):
        def recover(command, **kwargs):
            self.calls += 1
            Path(command[command.index('--output') + 1]).write_bytes(self.original)
            return subprocess.CompletedProcess(command, 22 if self.calls == 1 else 0,
                                               '502' if self.calls == 1 else '200', '')
        with patch.object(prepare.subprocess, 'run', side_effect=recover), patch.object(prepare.time, 'sleep'):
            prepare.prepare(self.digest, 'artifact.jar', self.target)
        self.assertEqual(2, self.calls)
        self.assertEqual(self.original, self.target.read_bytes())

    def test_timed_out_process_is_classified_for_bounded_retry(self):
        with patch.object(prepare.subprocess, 'run', side_effect=subprocess.TimeoutExpired('curl', 20)):
            result = prepare.fetch('artifact.jar', self.target, 100)
        self.assertTrue(prepare.retryable(result))


if __name__ == '__main__':
    unittest.main()
