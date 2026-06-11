// Regenerate the landing-page screenshot gallery (public/shots/*.webp) from the
// captioned store screenshots in ../screenshots/store/i18n/en-US/phone.
// Run `npm run gen:shots` after `make screenshots` refreshes the store set.
import sharp from 'sharp'
import { mkdir } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const src = resolve(here, '../../screenshots/store/i18n/en-US/phone')
const out = resolve(here, '../public/shots')
await mkdir(out, { recursive: true })

// Store order → web order: lead with the hunt, end on dark mode.
const shots = ['01', '02', '03', '05', '07', '06', '04']

for (const name of shots) {
  await sharp(join(src, `${name}.png`))
    .resize({ width: 480 })
    .webp({ quality: 82 })
    .toFile(join(out, `${name}.webp`))
  console.log(`shots/${name}.webp`)
}
