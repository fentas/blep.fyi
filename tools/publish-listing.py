#!/usr/bin/env python3
"""Push localized store listing text + screenshots + feature graphics to Google Play.

Uses the Google Play Android Publisher API v3 (edits.listings + edits.images):
opens an edit, replaces each locale's title/short/full description and each image
slot, and commits.

Listing text — one canonical, fastlane-compatible source tree (committed):

  store/listing/<locale>/title.txt              -> Listing.title              (<= 30)
  store/listing/<locale>/short_description.txt  -> Listing.shortDescription   (<= 80)
  store/listing/<locale>/full_description.txt   -> Listing.fullDescription    (<= 4000)

Screenshots — the two image trees are kept strictly separate:

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

# Listing.<field> -> filename, and Play's character limit for it.
LISTING_FIELDS = {
    "title": ("title.txt", 30),
    "shortDescription": ("short_description.txt", 80),
    "fullDescription": ("full_description.txt", 4000),
}


def build_listing_plan(listing_dir, only_locales):
    """Return [(locale, {title, shortDescription, fullDescription}), ...]."""
    plan = []
    if not os.path.isdir(listing_dir):
        return plan
    for locale in sorted(os.listdir(listing_dir)):
        base = os.path.join(listing_dir, locale)
        if not os.path.isdir(base):
            continue
        if only_locales and locale not in only_locales:
            continue
        body = {}
        for field, (fname, _) in LISTING_FIELDS.items():
            path = os.path.join(base, fname)
            if os.path.isfile(path):
                with open(path, encoding="utf-8") as f:
                    body[field] = f.read().strip()
        if body:
            plan.append((locale, body))
    return plan


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
    ap.add_argument("--listing-dir", default=os.path.join(ROOT, "store", "listing"))
    ap.add_argument("--types", default="listing,phone,tablet,wear,feature",
                    help="comma list of: listing,phone,tablet,wear,feature")
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

    listing_plan = build_listing_plan(args.listing_dir, only_locales) if "listing" in want_types else []

    if not plan and not listing_plan:
        print("Nothing to upload (no matching listing text or screenshots found).", file=sys.stderr)
        return 1

    print(f"Package: {args.package}\n")

    # Listing text — print each field's length and flag anything over Play's limit.
    over_limit = False
    if listing_plan:
        print("Listing text:")
        for locale, body in listing_plan:
            for field, (_, limit) in LISTING_FIELDS.items():
                if field not in body:
                    continue
                n = len(body[field])
                bad = n > limit
                over_limit = over_limit or bad
                flag = f"  !! {n} > {limit}" if bad else ""
                print(f"  {locale:<7} {field:<16} {n:>4}/{limit}{flag}")
        print()

    # Screenshots — print files and flag over-cap slots.
    for locale, image_type, files in plan:
        flag = ""
        if image_type not in SINGLE and len(files) > MAX_SCREENSHOTS:
            flag = f"  !! {len(files)} > {MAX_SCREENSHOTS}; only the first {MAX_SCREENSHOTS} would upload"
        print(f"  {locale:<7} {image_type:<22} {len(files)} file(s){flag}")
        for f in files:
            print(f"            {os.path.relpath(f, ROOT)}")
    n_imgs = sum(len(f) for _, _, f in plan)
    locales = {p[0] for p in plan} | {p[0] for p in listing_plan}
    print(f"\nTotal: {len(listing_plan)} listing(s), {len(plan)} image slot(s), "
          f"{n_imgs} image(s) across {len(locales)} locale(s).")

    if over_limit:
        print("\nRefusing to continue: some listing fields exceed Play's limits (see !! above).",
              file=sys.stderr)
        return 4

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
        for locale, body in listing_plan:
            edits.listings().update(
                packageName=args.package, editId=edit_id,
                language=locale, body=body).execute()
            print(f"  ✓ {locale} listing ({', '.join(body)})")
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
