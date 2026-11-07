#!/usr/bin/env python3
"""List the other repo's gameplay commits that this repo has not ported yet.

Stock Farmer has an iOS repo and an Android remake. Each commit carries
`Port:`, `Port-Size:` and `Port-Note:` trailers; a commit that ports one
from the other repo carries `Ported-From: ios@<sha>` or `android@<sha>`.
This repo's docs/SYNC.md names the other repo's watermark
(`iOS synced through: <sha>` or `Android synced through: <sha>`) and both
repos' docs/SYNC.md hold legacy tables for commits made before trailers.

The two copies of this script (stock-farmer-ios and stock-farmer-android)
must stay identical; change them together.

    python scripts/port-backlog.py --from ../stock-farmer-android
    python scripts/port-backlog.py --from ../stock-farmer-ios --since 5279b68
    python scripts/port-backlog.py --self-test

Standard library only.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

REPO_NAMES = {"ios": "iOS", "android": "Android"}
SIZES = ("S", "M", "L")
AGENT_BY_SIZE = {
    "S": "Sonnet, low effort",
    "M": "Sonnet high or Opus medium",
    "L": "Opus high, Core tests first",
}
UNCLASSIFIED = "unclassified"
FIELD, RECORD, VALUES = "\x1f", "\x1e", "\x1d"
WATERMARK = re.compile(r"^(iOS|Android) synced through:\s*`?([0-9a-fA-F]{7,40})`?", re.MULTILINE)
LEGACY_ROW = re.compile(r"^\|\s*`?(ios|android)@([0-9a-fA-F]{7,40})`?\s*\|(.*)\|\s*$", re.MULTILINE)


@dataclass
class Item:
    sha: str
    date: str
    subject: str
    port: str
    size: str
    note: str
    source: str  # "trailer", "legacy" or "legacy, before watermark"

    @property
    def agent(self):
        return AGENT_BY_SIZE.get(self.size, "triage first")


def git(repo, *args):
    result = subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed in {repo}: {result.stderr.strip()}")
    return result.stdout


def git_ok(repo, *args):
    return subprocess.run(["git", "-C", str(repo), *args], capture_output=True).returncode == 0


def read_sync(repo):
    path = Path(repo) / "docs" / "SYNC.md"
    return path.read_text(encoding="utf-8") if path.exists() else ""


def watermark(sync_text):
    """Returns (other repo name, sha) from this repo's SYNC.md."""
    match = WATERMARK.search(sync_text)
    if not match:
        raise SystemExit("docs/SYNC.md has no 'iOS synced through:' or 'Android synced through:' line")
    return match.group(1).lower(), match.group(2)


def legacy_rows(sync_texts, source):
    """Legacy rows for `source` commits: | ios@sha | date | subject | port | size | note |."""
    rows = []
    for text in sync_texts:
        for match in LEGACY_ROW.finditer(text):
            if match.group(1) != source:
                continue
            cells = [cell.strip().strip("`") for cell in match.group(3).split("|")]
            cells += [""] * (5 - len(cells))
            date, subject, port, size, note = cells[:5]
            rows.append(Item(match.group(2).lower(), date, subject, port.lower(), size.upper(), note, "legacy"))
    return rows


def main_ref(repo):
    for ref in ("origin/main", "main"):
        if git_ok(repo, "rev-parse", "--verify", "--quiet", ref + "^{commit}"):
            return ref
    raise SystemExit(f"{repo} has neither origin/main nor main")


def commits(repo, since, ref):
    """Non-merge commits in since..ref, oldest first. Merges are skipped: their branch commits carry the trailers."""
    trailer = lambda key: f"%(trailers:key={key},valueonly,separator=%x1d)"
    fmt = FIELD.join(["%H", "%ad", "%s", trailer("Port"), trailer("Port-Size"), trailer("Port-Note")]) + RECORD
    out = git(repo, "log", "--no-merges", "--reverse", "--date=short", f"--format={fmt}", f"{since}..{ref}")
    items = []
    for record in out.split(RECORD):
        record = record.strip("\n")
        if not record:
            continue
        sha, date, subject, port, size, note = record.split(FIELD)
        first = lambda value: value.split(VALUES)[0].strip()
        items.append(Item(sha, date, subject, first(port).lower(), first(size).upper(), first(note), "trailer"))
    return items


def credited(repo, source):
    """Source-commit sha prefixes that this repo's history credits with a Ported-From trailer."""
    out = git(repo, "log", "HEAD", "--format=%(trailers:key=Ported-From,valueonly,separator=%x1d)")
    prefixes = set()
    for value in re.split(r"[\x1d\n,\s]+", out):
        name, _, sha = value.strip().partition("@")
        if name.lower() == source and re.fullmatch(r"[0-9a-fA-F]{7,40}", sha):
            prefixes.add(sha.lower())
    return prefixes


def is_credited(sha, prefixes):
    return any(sha.startswith(prefix) or prefix.startswith(sha) for prefix in prefixes)


def backlog(this_repo, other_repo, since=None):
    """Returns (source name, since, ref, items to port)."""
    this_sync = read_sync(this_repo)
    source, mark = watermark(this_sync)
    since = since or mark
    ref = main_ref(other_repo)
    legacy = legacy_rows([this_sync, read_sync(other_repo)], source)
    used = set()

    def classify(item):
        if item.port:
            return item
        for index, row in enumerate(legacy):
            if item.sha.startswith(row.sha) or (row.subject and row.subject == item.subject):
                used.add(index)
                return Item(item.sha, item.date, item.subject, row.port or UNCLASSIFIED, row.size, row.note, "legacy")
        return Item(item.sha, item.date, item.subject, UNCLASSIFIED, "", "", "untagged")

    in_range = [classify(item) for item in commits(other_repo, since, ref)]
    # Rows for commits outside the range that are not behind the watermark either,
    # such as history squashed away, stay open until a Ported-From credits them.
    before = [
        Item(row.sha, row.date, row.subject, row.port or UNCLASSIFIED, row.size, row.note, "legacy, before watermark")
        for index, row in enumerate(legacy)
        if index not in used
        and not any(item.sha.startswith(row.sha) for item in in_range)
        and not git_ok(other_repo, "merge-base", "--is-ancestor", row.sha, since)
    ]
    prefixes = credited(this_repo, source)
    items = [item for item in before + in_range if item.port != "none" and not is_credited(item.sha, prefixes)]
    return source, since, ref, items


def render(source, since, ref, items):
    lines = [f"{REPO_NAMES[source]} commits to port, {since[:7]}..{ref} plus open legacy rows: {len(items)}", ""]
    if items:
        lines.append("| sha | date | subject | port | size | suggested agent | note |")
        lines.append("| --- | --- | --- | --- | --- | --- | --- |")
        for item in items:
            cells = [item.sha[:7], item.date, item.subject, item.port, item.size or "?", item.agent, item.note]
            lines.append("| " + " | ".join(cell.replace("|", "/") for cell in cells) + " |")
        counts = {size: sum(item.size == size for item in items) for size in SIZES}
        unsized = len(items) - sum(counts.values())
        lines.append("")
        lines.append("By size: " + ", ".join(f"{size} {counts[size]}" for size in SIZES) + f", unsized {unsized}")
    return "\n".join(lines)


def run(argv):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--from", dest="other", help="path to the other repo")
    parser.add_argument("--since", help="other repo's sha to start after (default: this repo's docs/SYNC.md watermark)")
    parser.add_argument("--repo", default=str(Path(__file__).resolve().parents[1]), help="this repo (default: the script's repo)")
    parser.add_argument("--self-test", action="store_true", help="run the built-in tests")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.other:
        parser.error("--from is required")
    print(render(*backlog(Path(args.repo), Path(args.other), args.since)))
    return 0


# Self-test: two throwaway repos, checked against what the backlog should list.

def self_test():
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as root:
        ios, android = Path(root, "ios"), Path(root, "android")
        env = {"GIT_AUTHOR_NAME": "Test", "GIT_AUTHOR_EMAIL": "t@example.com",
               "GIT_COMMITTER_NAME": "Test", "GIT_COMMITTER_EMAIL": "t@example.com"}
        os.environ.update(env)

        def commit(repo, message, path="file.txt"):
            file = repo / path
            file.parent.mkdir(parents=True, exist_ok=True)
            with file.open("a", encoding="utf-8") as handle:
                handle.write(message + "\n")
            git(repo, "add", "-A")
            git(repo, "-c", "commit.gpgsign=false", "commit", "-q", "-m", message)
            return git(repo, "rev-parse", "HEAD").strip()

        for repo in (ios, android):
            repo.mkdir()
            git(repo, "init", "-q", "-b", "main")
        base = commit(ios, "Base")
        rules = commit(ios, "Rebalance wheat\n\nWhy.\n\nPort: rules\nPort-Size: M\nPort-Note: port CropEconomyTests first\nCo-Authored-By: X <x@example.com>")
        commit(ios, "Bump the Xcode Cloud script\n\nPort: none\nCo-Authored-By: X <x@example.com>")
        legacy = commit(ios, "Grey out locked rows")
        renamed = commit(ios, "Rename the clock")
        rebased = commit(ios, "Hold the loading screen")
        ported = commit(ios, "Add ducks\n\nPort: rules\nPort-Size: L")
        git(ios, "checkout", "-q", "-b", "side")
        branch = commit(ios, "Side feature\n\nPort: ui\nPort-Size: S", "side.txt")
        git(ios, "checkout", "-q", "main")
        commit(ios, "Main moves on\n\nPort: none")
        git(ios, "-c", "commit.gpgsign=false", "merge", "-q", "--no-ff", "-m", "Merge side\n\nPort: ui\nPort-Size: L", "side")
        open_before = commit(ios, "Older squashed change", "older.txt")  # an object, but not before the watermark
        git(ios, "reset", "-q", "--hard", "HEAD~1")

        commit(android, "\n".join([
            "# Sync", "", f"iOS synced through: {base[:7]}", "",
            "| Commit | Date | Subject | Port | Size | Note |", "| --- | --- | --- | --- | --- | --- |",
            f"| ios@{legacy[:9]} | 2026-11-06 | Grey out locked rows | ui | S | grey the whole row |",
            "| ios@0000000 | 2026-11-06 | Hold the loading screen | ui | M | matched by subject |",
            f"| ios@{open_before[:7]} | 2026-11-05 | Older squashed change | rules | L | still open |",
            "| ios@1111111 | 2026-11-05 | Already ported squashed change | rules | S | credited |",
            f"| android@{base[:7]} | 2026-11-06 | Not an iOS row | rules | S | ignored |",
        ]), "docs/SYNC.md")
        commit(android, f"Port ducks\n\nPort: none\nPorted-From: ios@{ported[:8]}\nPorted-From: ios@1111111\nCo-Authored-By: X <x@example.com>")

        source, since, ref, items = backlog(android, ios)
        blank = Item("", "", "", "", "", "", "")
        get = lambda sha: next((item for item in items if item.sha.startswith(sha[:7])), blank)
        subjects = [item.subject for item in items]
        failures = []

        def expect(condition, what):
            if not condition:
                failures.append(what)

        expect((source, since, ref) == ("ios", base[:7], "main"), f"source/since/ref were {source} {since} {ref}")
        found = get(rules)
        expect((found.subject, found.port, found.size, found.note, found.source)
               == ("Rebalance wheat", "rules", "M", "port CropEconomyTests first", "trailer"), "tagged commit's trailers parsed")
        expect("Bump the Xcode Cloud script" not in subjects and "Main moves on" not in subjects, "Port: none skipped")
        expect((get(legacy).port, get(legacy).size, get(legacy).source) == ("ui", "S", "legacy"), "legacy row matched by sha")
        expect(get(rebased).size == "M" and subjects.count("Hold the loading screen") == 1, "legacy row matched once by subject")
        expect(get(renamed).port == UNCLASSIFIED, "untagged commit listed as unclassified")
        expect(get(ported) is blank, "Ported-From credits the ported commit")
        expect("Already ported squashed change" not in subjects, "Ported-From credits a legacy row")
        expect(get(open_before).source == "legacy, before watermark", "open legacy row outside the range listed")
        expect(get(branch).port == "ui" and "Merge side" not in subjects, "branch commit listed, merge commit skipped")
        expect("Not an iOS row" not in subjects and get(base) is blank, "other repo's rows and the watermark excluded")
        expect(Item("x", "", "", "rules", "L", "", "").agent == AGENT_BY_SIZE["L"], "size maps to agent")
        expect("By size: S 2, M 2, L 1, unsized 1" in render(source, since, ref, items), "summary counts")

        _, _, _, later = backlog(android, ios, since=legacy)
        expect(all(item.subject not in ("Rebalance wheat", "Grey out locked rows") for item in later), "--since moves the start")

    if failures:
        print("self-test FAILED:\n  " + "\n  ".join(failures))
        return 1
    print("self-test passed")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(run(sys.argv[1:]))
