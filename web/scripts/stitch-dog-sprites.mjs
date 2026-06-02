// Turns the AI-generated sprite sheets in ../sprites (6×2 grids of a pup on a
// cream page, with a soft shadow, "FRAME n" captions and faint dividers) into
// one clean, stable, transparent sprite sheet for the app.
//
// Per cell:
//   1. flood-fill the light background to transparent FROM THE EDGES (removes
//      page + shadow + dividers; the pup's enclosed light belly is preserved),
//   2. connected-component cleanup: keep the pup (+bone), drop the caption,
//      specks and any stray bits (small, or living only in the bottom band),
//   3. crop to the pup's true extent (no fixed crops → nothing gets clipped).
// Then per clip:
//   4. ONE uniform scale for all 12 frames (kills zoom/breathing jitter),
//   5. register each frame by centre-of-mass-x + feet baseline (stops it
//      wandering around).
//
// Output → app/composeApp/src/commonMain/composeResources/drawable/dog_sheet.png
// Run with `npm run gen:dog`.
//
// NOTE: the raw source art under ../sprites is gitignored (it's large). The
// committed asset is the generated dog_sheet.png; keep the sources locally (or
// drop in replacements with the same filenames) to regenerate.
import sharp from 'sharp'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = resolve(root, '../sprites')

// Row order = clip order the app expects (walk, look, idle, found).
const ROWS = [
  { file: 'Gemini_Generated_Image_p3ehtop3ehtop3eh.png', clip: 'walk' },
  { file: 'Gemini_Generated_Image_u9yhe2u9yhe2u9yh.png', clip: 'look' },
  { file: 'Gemini_Generated_Image_62vn0762vn0762vn.png', clip: 'idle' },
  { file: 'Gemini_Generated_Image_6m0nln6m0nln6m0n.png', clip: 'found' },
]

const COLS = 6, GRID_ROWS = 2, FRAMES = 12
const CELL_W = 300, CELL_H = 248, TARGET_H = 172, BASE_PAD = 14
const AREA_MIN = 900        // drop components smaller than this (caption letters/specks)
const FILL_MIN = 0.12       // drop thin components (borders / divider lines)

// A pixel is "background" if it's light (page cream / soft shadow / divider).
const isBgPx = (r, g, b) => Math.min(r, g, b) > 185

/**
 * Extract one cell's pup as a tight RGBA cutout, or null.
 *
 * The crop is expanded horizontally so the pup's nose/tail (which often cross
 * the source cell boundary) aren't clipped. Any component that touches the
 * left/right crop edge is a neighbouring frame's pup bleeding in → dropped, as
 * are thin full-width divider lines, captions and specks.
 */
function extractCell(src, W, H, ch, cellX, cellY, cellW, cellH) {
  const EX = Math.round(cellW * 0.18)            // horizontal breathing room
  const cx0 = Math.max(0, cellX - EX), cx1 = Math.min(W, cellX + cellW + EX)
  const cy0 = Math.max(0, cellY + 2), cy1 = Math.min(H, cellY + cellH - 2)
  const cw = cx1 - cx0, chh = cy1 - cy0
  const captionTop = chh - Math.round(cellH * 0.16)

  const buf = new Uint8Array(cw * chh * 4)
  for (let y = 0; y < chh; y++) for (let x = 0; x < cw; x++) {
    const si = ((cy0 + y) * W + (cx0 + x)) * ch, di = (y * cw + x) * 4
    buf[di] = src[si]; buf[di + 1] = src[si + 1]; buf[di + 2] = src[si + 2]; buf[di + 3] = 255
  }
  // (1) flood-fill background from the edges
  const seen = new Uint8Array(cw * chh)
  const stack = []
  const visit = (x, y) => {
    if (x < 0 || y < 0 || x >= cw || y >= chh) return
    const p = y * cw + x
    if (seen[p]) return
    const i = p * 4
    if (!isBgPx(buf[i], buf[i + 1], buf[i + 2])) return
    seen[p] = 1; buf[i + 3] = 0; stack.push(p)
  }
  for (let x = 0; x < cw; x++) { visit(x, 0); visit(x, chh - 1) }
  for (let y = 0; y < chh; y++) { visit(0, y); visit(cw - 1, y) }
  while (stack.length) { const p = stack.pop(), x = p % cw, y = (p / cw) | 0; visit(x + 1, y); visit(x - 1, y); visit(x, y + 1); visit(x, y - 1) }

  // (2) connected components of the remaining opaque pixels
  const label = new Int32Array(cw * chh)
  let n = 0
  const comps = []
  for (let s = 0; s < cw * chh; s++) {
    if (buf[s * 4 + 3] === 0 || label[s]) continue
    n++
    const q = [s]; label[s] = n
    let area = 0, sumx = 0, sumy = 0, minx = cw, miny = chh, maxx = 0, maxy = 0
    while (q.length) {
      const p = q.pop(), x = p % cw, y = (p / cw) | 0
      area++; sumx += x; sumy += y
      if (x < minx) minx = x; if (x > maxx) maxx = x; if (y < miny) miny = y; if (y > maxy) maxy = y
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx, ny = y + dy
        if (nx < 0 || ny < 0 || nx >= cw || ny >= chh) continue
        const np = ny * cw + nx
        if (label[np] || buf[np * 4 + 3] === 0) continue
        label[np] = n; q.push(np)
      }
    }
    const fill = area / ((maxx - minx + 1) * (maxy - miny + 1))
    const touchesLR = minx === 0 || maxx === cw - 1
    comps.push({ id: n, area, fill, touchesLR, minx, miny, maxx, maxy })
  }
  if (!comps.length) return null
  // keep the pup (+ bone): solid, sizeable, not a neighbour bleed, not caption.
  const keep = new Set(
    comps.filter((c) => c.area >= AREA_MIN && c.fill >= FILL_MIN && !c.touchesLR && c.miny < captionTop).map((c) => c.id),
  )
  if (!keep.size) return null
  // zero out dropped components; gather bbox + centre-of-mass over kept pixels
  let kminx = cw, kminy = chh, kmaxx = 0, kmaxy = 0, ksx = 0, ksy = 0, ka = 0
  for (let y = 0; y < chh; y++) for (let x = 0; x < cw; x++) {
    const p = y * cw + x
    if (!label[p]) continue
    if (!keep.has(label[p])) { buf[p * 4 + 3] = 0; continue }
    ka++; ksx += x; ksy += y
    if (x < kminx) kminx = x; if (x > kmaxx) kmaxx = x; if (y < kminy) kminy = y; if (y > kmaxy) kmaxy = y
  }
  const bw = kmaxx - kminx + 1, bh = kmaxy - kminy + 1
  // crop to bbox into a fresh tight buffer
  const out = new Uint8Array(bw * bh * 4)
  for (let y = 0; y < bh; y++) for (let x = 0; x < bw; x++) {
    const si = ((kminy + y) * cw + (kminx + x)) * 4, di = (y * bw + x) * 4
    out[di] = buf[si]; out[di + 1] = buf[si + 1]; out[di + 2] = buf[si + 2]; out[di + 3] = buf[si + 3]
  }
  return { raw: out, bw, bh, cxRel: ksx / ka - kminx } // centre-of-mass x within bbox
}

const median = (a) => { const s = [...a].sort((x, y) => x - y); return s[s.length >> 1] }

const composites = []
const debug = []
for (let r = 0; r < ROWS.length; r++) {
  const { data, info } = await sharp(resolve(SRC, ROWS[r].file)).ensureAlpha().raw().toBuffer({ resolveWithObject: true })
  const W = info.width, H = info.height, ch = info.channels
  const cellW = W / COLS, cellH = H / GRID_ROWS
  const frames = []
  for (let k = 0; k < FRAMES; k++) {
    const gc = k % COLS, gr = (k / COLS) | 0
    const cellX = Math.round(gc * cellW), cellY = Math.round(gr * cellH)
    frames.push(extractCell(data, W, H, ch, cellX, cellY, Math.round(cellW), Math.round(cellH)))
  }
  // (4) one uniform scale for the whole clip
  const heights = frames.filter(Boolean).map((f) => f.bh)
  const scale = TARGET_H / median(heights)
  for (let k = 0; k < FRAMES; k++) {
    const f = frames[k]; if (!f) continue
    const rw = Math.max(1, Math.round(f.bw * scale)), rh = Math.max(1, Math.round(f.bh * scale))
    const png = await sharp(Buffer.from(f.raw), { raw: { width: f.bw, height: f.bh, channels: 4 } }).resize(rw, rh).png().toBuffer()
    // (5) register: centre-of-mass-x at cell centre, feet at the baseline
    let left = Math.round(CELL_W / 2 - f.cxRel * scale)
    left = Math.max(Math.min(left, CELL_W - rw), 0)
    const top = CELL_H - BASE_PAD - rh
    composites.push({ input: png, left: k * CELL_W + left, top: r * CELL_H + top })
    if (k < 12) debug.push({ row: r, input: png, left, top })
  }
}

const sheetW = CELL_W * FRAMES, sheetH = CELL_H * ROWS.length
const outDir = resolve(root, '../app/composeApp/src/commonMain/composeResources/drawable')
await sharp({ create: { width: sheetW, height: sheetH, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
  .composite(composites).png({ palette: true, compressionLevel: 9 }).toFile(resolve(outDir, 'dog_sheet.png'))
console.log(`dog_sheet.png — ${ROWS.length} clips × ${FRAMES} frames, cell ${CELL_W}x${CELL_H} (${sheetW}x${sheetH})`)
