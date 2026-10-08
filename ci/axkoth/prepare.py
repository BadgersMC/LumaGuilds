"""Prepare only checksum-pinned official dependencies; retry transport, never Gradle."""
import hashlib
import subprocess
import sys
import time
from pathlib import Path

MAX_ATTEMPTS = 32
TIME_BUDGET = 120
BASE_URL = 'https://repo.artillex-studios.com/releases/'
RETRYABLE_CURL = {5, 6, 7, 18, 28, 35, 52, 55, 56, 92}
RETRYABLE_HTTP = {'408', '429', '500', '502', '503', '504'}


def verified(path, digest):
    return path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest


def fetch(relative, temporary, remaining):
    command = ['curl', '--fail', '--silent', '--show-error', '--location', '--http1.1',
               '--proto', '=https', '--proto-redir', '=https', '--connect-timeout', '10',
               '--max-time', str(min(15, int(remaining))), '--output', str(temporary),
               '--write-out', '%{http_code}', BASE_URL + relative]
    try:
        return subprocess.run(command, capture_output=True, text=True, timeout=min(20, remaining))
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(command, 28, '', 'Download process timed out')


def retryable(result):
    return result.returncode in RETRYABLE_CURL or (
        result.returncode == 22 and result.stdout.strip() in RETRYABLE_HTTP)


def prepare(digest, relative, target):
    if verified(target, digest):
        print(f'Verified cache: {relative}')
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + '.part')
    deadline = time.monotonic() + TIME_BUDGET
    try:
        for attempt in range(1, MAX_ATTEMPTS + 1):
            remaining = deadline - time.monotonic()
            if remaining < 1:
                break
            temporary.unlink(missing_ok=True)
            result = fetch(relative, temporary, remaining)
            if result.returncode == 0:
                if not verified(temporary, digest):
                    raise RuntimeError(f'Checksum mismatch: {relative}')
                temporary.replace(target)
                print(f'Verified download: {relative}, attempt {attempt}')
                return
            if not retryable(result):
                raise RuntimeError(f'Non-retryable download error {result.returncode}: {relative}')
            print(f'Incomplete/unavailable transfer: {relative}, attempt {attempt}/{MAX_ATTEMPTS}')
            if attempt < MAX_ATTEMPTS:
                time.sleep(1)
        raise RuntimeError(f'Artifact preparation exhausted its bounded retries: {relative}')
    finally:
        temporary.unlink(missing_ok=True)


def main():
    repository = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / '.m2' / 'repository'
    for line in Path(__file__).with_name('artifacts.sha256').read_text().splitlines():
        digest, relative = line.split()
        prepare(digest, relative, repository / relative)


if __name__ == '__main__':
    main()
