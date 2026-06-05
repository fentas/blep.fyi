#!/usr/bin/env python3
"""Upload a signed AAB to Google Play and release it on a track.

Android Publisher API v3: insert edit -> bundles.upload -> tracks.update (create a
release with the new versionCode) -> commit. Dry-run by default; --commit does it.

Phone and Wear OS are separate form-factor tracks (the API prefixes the watch ones
with `wear:`), so each bundle goes to its own track:

  tools/publish-release.py --key sa.json \
    --aab app/composeApp/build/outputs/bundle/release/composeApp-release.aab \
    --track alpha       --version-name 1.0.0 --commit

  tools/publish-release.py --key sa.json \
    --aab app/wearApp/build/outputs/bundle/release/wearApp-release.aab \
    --track wear:blep   --version-name 0.1.0 --commit

Optional per-locale release notes: --notes-dir <dir> with <locale>.txt files.
By default the release status is `completed` (live to that track's testers);
pass --draft to stage it without releasing.
"""
import argparse
import os
import sys
import zipfile


def aab_version_code(path):
    """Read versionCode from the bundle's protobuf manifest without uploading."""
    with zipfile.ZipFile(path) as z:
        data = z.read("base/manifest/AndroidManifest.xml")
    i = data.find(b"versionCode")
    if i < 0:
        return None
    j = data.find(b"\x1a", i, i + 30)  # next length-delimited string = the source value
    if j < 0:
        return None
    ln = data[j + 1]
    return data[j + 2:j + 2 + ln].decode("latin1")


def read_notes(notes_dir):
    if not notes_dir or not os.path.isdir(notes_dir):
        return []
    out = []
    for fn in sorted(os.listdir(notes_dir)):
        if fn.endswith(".txt"):
            with open(os.path.join(notes_dir, fn), encoding="utf-8") as f:
                out.append({"language": fn[:-4], "text": f.read().strip()})
    return out


def main():
    ap = argparse.ArgumentParser(description="Upload + release a signed AAB on a Play track.")
    ap.add_argument("--package", default="fyi.blep")
    ap.add_argument("--key", help="service-account JSON (required for --commit)")
    ap.add_argument("--aab", required=True, help="path to the signed .aab")
    ap.add_argument("--track", required=True, help="track id, e.g. alpha / internal / wear:blep")
    ap.add_argument("--version-name", default="", help="human release name (defaults to versionCode)")
    ap.add_argument("--notes-dir", help="dir of <locale>.txt release notes (optional)")
    ap.add_argument("--draft", action="store_true", help="stage as draft instead of releasing")
    ap.add_argument("--commit", action="store_true", help="actually upload + release")
    args = ap.parse_args()

    if not os.path.isfile(args.aab):
        print(f"AAB not found: {args.aab}", file=sys.stderr)
        return 1
    vc = aab_version_code(args.aab)
    status = "draft" if args.draft else "completed"
    name = f"{args.version_name} ({vc})" if args.version_name else f"{vc}"
    notes = read_notes(args.notes_dir)

    size = os.path.getsize(args.aab) / 1e6
    print(f"Package : {args.package}")
    print(f"AAB     : {os.path.relpath(args.aab)}  ({size:.1f} MB, versionCode {vc})")
    print(f"Track   : {args.track}")
    print(f"Release : status={status}  name={name!r}  notes={len(notes)} locale(s)")

    if not args.commit:
        print("\nDry run — nothing uploaded. Add --commit (and --key) to release.")
        return 0
    if not args.key:
        print("\n--commit needs --key <service-account.json>.", file=sys.stderr)
        return 2

    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
        from googleapiclient.http import MediaFileUpload
    except ImportError:
        print("\nMissing deps. Run:  pip install google-api-python-client google-auth", file=sys.stderr)
        return 3

    creds = service_account.Credentials.from_service_account_file(
        args.key, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    svc = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
    edits = svc.edits()

    edit_id = edits.insert(packageName=args.package, body={}).execute()["id"]
    print(f"\nOpened edit {edit_id}")
    try:
        bundle = edits.bundles().upload(
            packageName=args.package, editId=edit_id,
            media_body=MediaFileUpload(args.aab, mimetype="application/octet-stream", resumable=True),
        ).execute()
        uploaded_vc = bundle["versionCode"]
        print(f"  ✓ uploaded bundle versionCode {uploaded_vc}")

        release = {"status": status, "versionCodes": [uploaded_vc], "name": name}
        if notes:
            release["releaseNotes"] = notes
        edits.tracks().update(
            packageName=args.package, editId=edit_id, track=args.track,
            body={"track": args.track, "releases": [release]},
        ).execute()
        print(f"  ✓ {args.track}: release {name} status={status}")

        edits.commit(packageName=args.package, editId=edit_id).execute()
        print(f"\nCommitted edit {edit_id} — {args.track} now serves versionCode {uploaded_vc}.")
    except Exception:
        try:
            edits.delete(packageName=args.package, editId=edit_id).execute()
            print(f"\nError — abandoned edit {edit_id}.", file=sys.stderr)
        finally:
            raise
    return 0


if __name__ == "__main__":
    sys.exit(main())
