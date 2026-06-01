# Store graphical assets

| File                 | Use                                                    |
| -------------------- | ------------------------------------------------------ |
| `app-icon-1024.png`  | App Store icon (1024×1024, no alpha) — generated from `logo.svg` |
| `../../web/public/icons/icon-512.png` | Reusable 512 mark (Play, PWA)         |

Regenerate with `cd web && npm run gen:icons` (PWA sizes) or the inline script in
the project README.

## Screenshots (to capture from a running build)

Screenshots need a real device/simulator, so they aren't committed here. Capture
these three minimalist frames per platform once the app runs:

1. **Discovery** — the clean device list (highlight a connected device).
2. **Tracking** — the big arrow mid-sweep on a warm pastel background with a
   guidance line ("Keep going").
3. **Completion** — the celebration screen with the mascot.

Required sizes:
- **App Store:** 6.7" (1290×2796) and 6.1" (1179×2556); Apple Watch 410×502.
- **Google Play:** phone 1080×1920+ (min 2, max 8); Wear OS 384×384.
- **Feature graphic (Play):** 1024×500.

Keep the pastel background, generous whitespace, and a single short caption per
shot to match the brand.
