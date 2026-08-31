#!/usr/bin/env python3
"""Resolve the recurring app/build.gradle.kts version conflict during upstream sync.

Upstream bumps the same versionCode/versionName lines the fork owns, so this file
conflicts on every single sync. Everything else in the file is merged normally by
git; only the version hunk is rewritten here.

Deliberately does NOT use `git checkout --ours/--theirs`: --theirs would revert
applicationId to upstream's app.mihon (breaking upgrades for existing installs)
and --ours would drop new upstream dependencies.

Version scheme: <upstream versionName>.<fork counter>, e.g. upstream 0.20.4 with
fork counter 1 -> "0.20.4.1". The counter restarts at 1 on a new upstream release
and increments while patching the same one. versionCode is max(both) + 1 so the
build is always installable as an upgrade.
"""
import re
import subprocess
import sys

PATH = "app/build.gradle.kts"
CONFLICT = re.compile(r"<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n", re.S)


def stage(n):
    return subprocess.run(
        ["git", "show", f":{n}:{PATH}"], capture_output=True, text=True, check=True
    ).stdout


def version_code(text):
    return int(re.search(r"versionCode\s*=\s*(\d+)", text).group(1))


def version_name(text):
    return re.search(r'versionName\s*=\s*"([^"]+)"', text).group(1)


def compute(ours, theirs):
    new_code = max(version_code(ours), version_code(theirs)) + 1

    ours_name, theirs_name = version_name(ours), version_name(theirs)
    parts = ours_name.split(".")
    base, fork = ".".join(parts[:3]), parts[3] if len(parts) > 3 else None

    if base == theirs_name and fork is not None and fork.isdigit():
        new_name = f"{theirs_name}.{int(fork) + 1}"
    else:
        new_name = f"{theirs_name}.1"

    return new_code, new_name


def main():
    new_code, new_name = compute(stage(2), stage(3))

    text = open(PATH).read()
    replacement = f'        versionCode = {new_code}\n        versionName = "{new_name}"\n'
    text, n = CONFLICT.subn(lambda _: replacement, text)
    if n != 1:
        sys.exit(f"expected exactly 1 conflict block in {PATH}, found {n}")
    if "<<<<<<<" in text or ">>>>>>>" in text:
        sys.exit(f"conflict markers remain in {PATH}")
    open(PATH, "w").write(text)

    print(f"Resolved -> versionCode={new_code} versionName={new_name}")
    print(f"version={new_name}")


if __name__ == "__main__":
    main()
