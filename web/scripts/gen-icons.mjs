// Rasterises the mascot SVG into PWA PNG icons with a safe maskable margin.
// Run with `npm run gen:icons`; output PNGs are committed so CI needs no sharp.
import sharp from 'sharp'
import { readFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const svg = readFileSync(resolve(root, 'public/logo.svg'))
const outDir = resolve(root, 'public/icons')
mkdirSync(outDir, { recursive: true })

const BG = '#EEF2F6'

for (const size of [192, 512]) {
  const pad = Math.round(size * 0.16) // maskable safe area
  const inner = size - pad * 2
  const logo = await sharp(svg)
    .resize(inner, inner, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer()
  await sharp({ create: { width: size, height: size, channels: 4, background: BG } })
    .composite([{ input: logo, top: pad, left: pad }])
    .png()
    .toFile(resolve(outDir, `icon-${size}.png`))
  console.log(`wrote icons/icon-${size}.png`)
}
