#!/usr/bin/env python3
"""Push localized store screenshots + feature graphics to Google Play.

Uses the Google Play Android Publisher API v3 (edits.images): opens an edit,
replaces each image slot per locale, and commits. The two screenshot trees are
kept strictly separate:

  screenshots/store/i18n/<locale>/phone/*.png      -> phoneScreenshots
  screenshots/store/i18n/<locale>/tablet/*.png     -> sevenInchScreenshots + tenInchScreenshots
  screenshots/store/i18n/<locale>/feature-1024x500.png -> featureGraphic
  screenshots/store/wear/<locale>/*.png            -> wearScreenshots   (Wear OS only — never mixed)

chromebook/ is intentionally skipped: the Publisher API v3 imageType enum has no
chromebookScreenshots slot (the Console web UI added one, the API never did, and
Fastlane supply has the same gap). It's not lost coverage — Play serves the
tenInchScreenshots set to Chromebooks by fallback, and we upload tablet/ there.
Set Chromebook-specific shots by hand in the Console if you ever want them.

Prereq (one-time, only you can do it): create a Google Cloud service account,
download its JSON key, then in Play Console -> Users & permissions invite that
service-account email and grant it the "Edit store listing" permission.

  pip install google-api-python-client google-auth

  # dry run — prints exactly what it would upload, touches nothing:
  tools/publish-listing.py --key sa.json

  # do it (opens an edit, uploads, commits):
  tools/publish-listing.py --key sa.json --commit

  # narrow it down:
  tools/publish-listing.py --key sa.json --commit --locales de-DE,fr-FR --types phone,wear
"""
import argparse
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# imageType -> True when only a single image is allowed in that slot.
SINGLE = {"featureGraphic", "icon", "promoGraphic", "tvBanner"}
MAX_SCREENSHOTS = 8  # Play's per-type cap


def build_plan(store_dir, want_types):
    """Return [(locale, imageType, [file, ...]), ...] from the screenshot trees."""
    i18n = os.path.join(store_dir, "i18n")
    wear = os.path.join(store_dir, "wear")
    plan = []

    def pngs(d):
        return sorted(os.path.join(d, f) for f in os.listdir(d) if f.lower().endswith(".png")) if os.path.isdir(d) else []

    # Phone / tablet / feature graphic — the i18n tree. NEVER wearScreenshots.
    for locale in sorted(os.listdir(i18n)) if os.path.isdir(i18n) else []:
        base = os.path.join(i18n, locale)
        if not os.path.isdir(base):
            continue
        if "phone" in want_types:
            shots = pngs(os.path.join(base, "phone"))
            if shots:
                plan.append((locale, "phoneScreenshots", shots))
        if "tablet" in want_types:
            shots = pngs(os.path.join(base, "tablet"))
            if shots:
                plan.append((locale, "sevenInchScreenshots", shots))
                plan.append((locale, "tenInchScreenshots", shots))
        if "feature" in want_types:
            fg = os.path.join(base, "feature-1024x500.png")
            if os.path.isfile(fg):
                plan.append((locale, "featureGraphic", [fg]))

    # Wear OS — its own tree, its own slot. Kept fully separate from the above.
    if "wear" in want_types:
        for locale in sorted(os.listdir(wear)) if os.path.isdir(wear) else []:
            shots = pngs(os.path.join(wear, locale))
            if shots:
                plan.append((locale, "wearScreenshots", shots))

    return plan


def main():
    ap = argparse.ArgumentParser(description="Upload localized store screenshots to Google Play.")
    ap.add_argument("--package", default="fyi.blep", help="applicationId (default: fyi.blep)")
    ap.add_argument("--key", help="service-account JSON key (required for --commit)")
    ap.add_argument("--store-dir", default=os.path.join(ROOT, "screenshots", "store"))
    ap.add_argument("--types", default="phone,tablet,wear,feature",
                    help="comma list of: phone,tablet,wear,feature")
    ap.add_argument("--locales", default="", help="comma list to restrict to (default: all found)")
    ap.add_argument("--commit", action="store_true", help="actually open an edit, upload and commit")
    ap.add_argument("--no-review", action="store_true",
                    help="commit with changesNotSentForReview=true (don't submit the listing for review)")
    args = ap.parse_args()

    want_types = {t.strip() for t in args.types.split(",") if t.strip()}
    only_locales = {l.strip() for l in args.locales.split(",") if l.strip()}

    plan = build_plan(args.store_dir, want_types)
    if only_locales:
        plan = [p for p in plan if p[0] in only_locales]
    if not plan:
        print("Nothing to upload (no matching screenshots found).", file=sys.stderr)
        return 1

    # Validate caps and print the plan.
    print(f"Package: {args.package}\n")
    for locale, image_type, files in plan:
        flag = ""
        if image_type not in SINGLE and len(files) > MAX_SCREENSHOTS:
            flag = f"  !! {len(files)} > {MAX_SCREENSHOTS}; only the first {MAX_SCREENSHOTS} would upload"
        print(f"  {locale:<7} {image_type:<22} {len(files)} file(s){flag}")
        for f in files:
            print(f"            {os.path.relpath(f, ROOT)}")
    n_imgs = sum(len(f) for _, _, f in plan)
    print(f"\nTotal: {len(plan)} slot(s), {n_imgs} image(s) across {len({p[0] for p in plan})} locale(s).")

    if not args.commit:
        print("\nDry run — nothing uploaded. Re-run with --commit (and --key sa.json) to apply.")
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
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
    edits = service.edits()

    edit_id = edits.insert(packageName=args.package, body={}).execute()["id"]
    print(f"\nOpened edit {edit_id}")
    try:
        for locale, image_type, files in plan:
            edits.images().deleteall(
                packageName=args.package, editId=edit_id,
                language=locale, imageType=image_type).execute()
            for f in files[:MAX_SCREENSHOTS] if image_type not in SINGLE else files[:1]:
                edits.images().upload(
                    packageName=args.package, editId=edit_id,
                    language=locale, imageType=image_type,
                    media_body=MediaFileUpload(f, mimetype="image/png")).execute()
            print(f"  ✓ {locale} {image_type}")
        edits.commit(packageName=args.package, editId=edit_id,
                     changesNotSentForReview=args.no_review).execute()
        print(f"\nCommitted edit {edit_id}. Listing images updated.")
    except Exception:
        # Abandon the edit so a failed run doesn't leave a dangling draft.
        try:
            edits.delete(packageName=args.package, editId=edit_id).execute()
            print(f"\nError — abandoned edit {edit_id}.", file=sys.stderr)
        finally:
            raise
    return 0


if __name__ == "__main__":
    sys.exit(main())
