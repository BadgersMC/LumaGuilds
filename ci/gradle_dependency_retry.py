"""Retry only the observed incomplete Artillex transfer before tests start."""

import re
import subprocess
import sys
import time
from pathlib import Path

MAX_ATTEMPTS = 3
RETRY_DELAY_SECONDS = 5


def run_gradle(command, cwd=None):
    lines = []
    with subprocess.Popen(
        command,
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    ) as process:
        for line in process.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
            lines.append(line)
        status = process.wait()
    return status, "".join(lines)


def is_incomplete_dependency(log):
    resolution_failure = "Could not resolve all files for configuration ':compileClasspath'" in log
    known_artifact = re.search(r"Could not download (AxKothAPI-4|axapi-1\.4\.8)\.jar", log)
    incomplete_body = "Premature end of Content-Length delimited message body" in log
    tests_or_source_error = re.search(r"> Task :test(?:\s|$)|(?:^|\s)e: ", log)
    return bool(resolution_failure and known_artifact and incomplete_body and not tests_or_source_error)


def run_with_retries(command, runner=run_gradle, pause=time.sleep):
    for attempt in range(1, MAX_ATTEMPTS + 1):
        status, log = runner(command)
        if status == 0 or attempt == MAX_ATTEMPTS or not is_incomplete_dependency(log):
            return status
        print(f"Incomplete dependency download; retrying Gradle attempt {attempt + 1}/{MAX_ATTEMPTS}.")
        pause(RETRY_DELAY_SECONDS)
    raise AssertionError("Retry loop must return the final Gradle status")


if __name__ == "__main__":
    project_root = Path(__file__).resolve().parent.parent
    invocation = [str(project_root / "gradlew"), *sys.argv[1:], "--console=plain"]
    sys.exit(run_with_retries(invocation, runner=lambda command: run_gradle(command, cwd=project_root)))
