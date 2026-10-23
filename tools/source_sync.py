#!/usr/bin/env python3
"""Publish committed source checkpoints and verify release provenance. No builds."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import urllib.request


def git(repo, *args):
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return result.stdout.strip()


def trusted_repo(repo):
    repo = pathlib.Path(git(repo, "rev-parse", "--show-toplevel"))
    remote = git(repo, "remote", "get-url", "--push", "origin")
    if not re.fullmatch(r"https://git\.offchunk\.com/Offchunk/[a-z0-9-]+(?:\.git)?", remote):
        raise RuntimeError("origin must be the intended HTTPS Offchunk product repository; credentials must stay in Git Credential Manager")
    return repo


def remote_ref(repo, ref):
    output = git(repo, "ls-remote", "origin", ref, ref + "^{}")
    rows = dict((line.split("\t")[1], line.split("\t")[0]) for line in output.splitlines() if line)
    return rows.get(ref + "^{}", rows.get(ref))


def checkpoint(args):
    repo = trusted_repo(args.repo)
    branch = git(repo, "symbolic-ref", "--quiet", "--short", "HEAD")
    if not branch.startswith("dev/"):
        raise RuntimeError("automatic checkpoints are limited to explicit dev/* branches")
    if git(repo, "config", "--get", "offchunk.sourceSync") != "true":
        raise RuntimeError("this checkout has not enabled source checkpoints")
    # A commit is the explicit publication boundary. Never stage a working tree,
    # publish a frozen release ref, force-push, fetch, rebase, or touch other lanes.
    commit = git(repo, "rev-parse", "HEAD")
    changed = git(repo, "diff-tree", "--root", "--no-commit-id", "--name-only", "-r", "HEAD").splitlines()
    for filename in changed:
        parts = pathlib.PurePosixPath(filename).parts
        if any(p.lower() in {".gradle", ".git", "node_modules"} for p in parts) or (parts and parts[0].lower() in {"run", "runclient", "runserver", "world", "worlds", "logs", "crash-reports"}):
            raise RuntimeError("runtime/cache/player files cannot be source checkpoints: " + filename)
        if pathlib.PurePosixPath(filename).name.lower() in {".env", ".env.local", "id_rsa", "id_ed25519", "credentials.json"}:
            raise RuntimeError("private credential filename cannot be a source checkpoint: " + filename)
    git(repo, "push", "origin", "HEAD:refs/heads/" + branch)
    if remote_ref(repo, "refs/heads/" + branch) != commit:
        raise RuntimeError("remote branch readback differs from the committed checkpoint")
    print(json.dumps({"status": "PUBLISHED_SOURCE_CHECKPOINT", "branch": branch, "commit": commit}))


def verify_release(args):
    repo = trusted_repo(args.repo)
    manifest = json.loads(pathlib.Path(args.manifest).read_text(encoding="utf-8-sig"))
    if "releases" in manifest or "submitted" in manifest:
        entries = manifest.get("releases", []) + manifest.get("submitted", [])
    else:
        entries = [manifest]
    if not entries:
        raise RuntimeError("source index contains no release or submitted artifact records")
    result = []
    for entry in entries:
        for key in ["version", "minecraft", "source_commit", "tag", "files"]:
            if not entry.get(key):
                raise RuntimeError("missing release provenance field: " + key)
        commit = entry["source_commit"].lower()
        if not re.fullmatch(r"[0-9a-fA-F]{40,64}", commit):
            raise RuntimeError("source_commit must be a full immutable Git object ID")
        git(repo, "check-ref-format", "refs/tags/" + entry["tag"])
        if remote_ref(repo, "refs/tags/" + entry["tag"]) != commit:
            raise RuntimeError("public tag does not resolve to the exact source commit: " + entry["tag"])
        source_files = entry.get("source_files", [])
        for file in source_files:
            rel = file["path"]
            if rel.startswith("/") or ".." in pathlib.PurePosixPath(rel).parts or any(c in rel for c in "\n\r\0"):
                raise RuntimeError("source path must be repository-relative")
        if source_files:
            queries = "\n".join(commit + ":" + f["path"] for f in source_files) + "\n"
            proc = subprocess.run(["git", "-C", str(repo), "cat-file", "--batch"], input=queries.encode("utf-8"), capture_output=True)
            if proc.returncode:
                raise RuntimeError("cannot read source blobs at release commit")
            offset = 0
            for file in source_files:
                end = proc.stdout.find(b"\n", offset)
                header = proc.stdout[offset:end].split()
                if len(header) != 3 or header[1] != b"blob":
                    raise RuntimeError("missing source blob: " + file["path"])
                size = int(header[2])
                blob = proc.stdout[end + 1:end + 1 + size]
                offset = end + 2 + size
                if hashlib.sha256(blob).hexdigest().lower() != file["sha256"].lower():
                    raise RuntimeError("source byte mismatch at release commit: " + file["path"])
        for artifact in entry["files"]:
            if not artifact.get("sha512") or not artifact.get("filename"):
                raise RuntimeError("release artifact needs a filename and SHA512")
            if artifact.get("local_path"):
                actual = hashlib.sha512(pathlib.Path(artifact["local_path"]).read_bytes()).hexdigest()
                if actual.lower() != artifact["sha512"].lower():
                    raise RuntimeError("release artifact bytes changed: " + artifact["filename"])
        if entry.get("modrinth_version_id"):
            url = "https://api.modrinth.com/v2/version/" + entry["modrinth_version_id"]
            req = urllib.request.Request(url, headers={"User-Agent": "Offchunk-source-sync/1.0"})
            with urllib.request.urlopen(req, timeout=30) as response:
                public = json.load(response)
            if public["version_number"] != entry["version"] or entry["minecraft"] not in public["game_versions"]:
                raise RuntimeError("Modrinth version or target differs from source record")
            actual = {(f["filename"], f["hashes"]["sha512"].lower()) for f in public["files"]}
            expected = {(f["filename"], f["sha512"].lower()) for f in entry["files"]}
            if actual != expected:
                raise RuntimeError("Modrinth artifacts differ from source record")
        result.append({"version": entry["version"], "minecraft": entry["minecraft"], "tag": entry["tag"], "commit": commit})
    print(json.dumps({"status": "PASS_PUBLIC_SOURCE_PROVENANCE", "releases": result}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser("checkpoint", help="push only an explicitly committed dev/* branch")
    check.add_argument("--repo", required=True)
    check.set_defaults(run=checkpoint)
    verify = commands.add_parser("verify-release", help="read-only public source tag and artifact verification")
    verify.add_argument("--repo", required=True)
    verify.add_argument("--manifest", required=True)
    verify.set_defaults(run=verify_release)
    args = parser.parse_args()
    try:
        args.run(args)
    except (RuntimeError, OSError, ValueError) as error:
        print("SOURCE_SYNC_FAILED: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
