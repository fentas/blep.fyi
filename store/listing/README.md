# Store listing text — canonical source

This tree is the **single source of truth** for the Play Store listing copy, one
folder per Play locale (the same locale codes as the screenshots):

```
store/listing/<locale>/
  title.txt              # app name on Play          (≤ 30 chars)
  short_description.txt  # one-liner                  (≤ 80 chars)
  full_description.txt   # the long description       (≤ 4000 chars)
```

`title.txt` is `blep` everywhere — the brand stays untranslated. Edit the short
and full descriptions here; **do not** keep a second copy elsewhere.

Push everything (text + screenshots) to Play in one commit:

```bash
tools/publish-listing.py                       # dry run — prints lengths + plan
tools/publish-listing.py --key sa.json --commit
tools/publish-listing.py --key sa.json --commit --types listing --locales de-DE
```

The tool validates every field against Play's limits and refuses to upload if
anything is over. Locale codes here mirror `screenshots/store/i18n/<locale>/`, so
the same `<locale>` drives both the copy and the images.

`docs/store-listing-localized.md` is now **reference only** (it also holds the
release-notes paste block and the locale-code notes); the descriptions there are
a snapshot of these files.
