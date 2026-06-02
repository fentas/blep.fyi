// Turns the AI-generated sprite sheets in ../sprites (6×2 grids of a pup on a
// cream background, with "FRAME n" captions + cell borders) into one clean,
// transparent sprite sheet for the app.
//
// Per cell: crop (dropping borders + caption), flood-fill the cream background
// to transparent from the edges (so the pup's light belly is preserved),
// trim to the pup, normalise size, and place bottom-centred on a uniform cell.
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
const INSET = 42          // drop cell borders / card frame
const CAPTION = 184       // drop "FRAME n" strip at the bottom of each cell
const CELL_W = 260, CELL_H = 210, TARGET = 184, BASE_PAD = 10

function floodCut(src, W, ch, x0, y0, x1, y1) {
  const w = x1 - x0, h = y1 - y0
  const buf = new Uint8Array(w * h * 4)
  for (let yy = 0; yy < h; yy++) for (let xx = 0; xx < w; xx++) {
    const si = ((y0 + yy) * W + (x0 + xx)) * ch, di = (yy * w + xx) * 4
    buf[di] = src[si]; buf[di + 1] = src[si + 1]; buf[di + 2] = src[si + 2]; buf[di + 3] = 255
  }
  const isBg = (i) => buf[i] > 190 && buf[i + 1] > 188 && buf[i + 2] > 182
  const seen = new Uint8Array(w * h)
  const stack = []
  const push = (x, y) => {
    if (x < 0 || y < 0 || x >= w || y >= h) return
    const p = y * w + x
    if (seen[p]) return
    const i = p * 4
    if (!isBg(i)) return
    seen[p] = 1; buf[i + 3] = 0; stack.push(p)
  }
  for (let xx = 0; xx < w; xx++) { push(xx, 0); push(xx, h - 1) }
  for (let yy = 0; yy < h; yy++) { push(0, yy); push(w - 1, yy) }
  while (stack.length) {
    const p = stack.pop(), x = p % w, y = (p / w) | 0
    push(x + 1, y); push(x - 1, y); push(x, y + 1); push(x, y - 1)
  }
  let minx = w, miny = h, maxx = 0, maxy = 0, any = false
  for (let yy = 0; yy < h; yy++) for (let xx = 0; xx < w; xx++) {
    if (buf[(yy * w + xx) * 4 + 3] > 0) { any = true; if (xx < minx) minx = xx; if (xx > maxx) maxx = xx; if (yy < miny) miny = yy; if (yy > maxy) maxy = yy }
  }
  if (!any) return null
  return { buf, w, h, minx, miny, bw: maxx - minx + 1, bh: maxy - miny + 1 }
}

const composites = []
for (let r = 0; r < ROWS.length; r++) {
  const { data, info } = await sharp(resolve(SRC, ROWS[r].file)).ensureAlpha().raw().toBuffer({ resolveWithObject: true })
  const W = info.width, H = info.height, ch = info.channels
  const cellW = W / COLS, cellH = H / GRID_ROWS
  for (let k = 0; k < FRAMES; k++) {
    const gc = k % COLS, gr = (k / COLS) | 0
    const x0 = Math.round(gc * cellW) + INSET, y0 = Math.round(gr * cellH) + INSET
    const x1 = Math.round((gc + 1) * cellW) - INSET, y1 = Math.round((gr + 1) * cellH) - CAPTION
    const cut = floodCut(data, W, ch, x0, y0, x1, y1)
    if (!cut) continue
    let img = sharp(Buffer.from(cut.buf), { raw: { width: cut.w, height: cut.h, channels: 4 } })
      .extract({ left: cut.minx, top: cut.miny, width: cut.bw, height: cut.bh })
    const scale = TARGET / Math.max(cut.bw, cut.bh)
    const rw = Math.max(1, Math.round(cut.bw * scale)), rh = Math.max(1, Math.round(cut.bh * scale))
    const png = await img.resize(rw, rh).png().toBuffer()
    composites.push({ input: png, left: k * CELL_W + Math.round((CELL_W - rw) / 2), top: r * CELL_H + (CELL_H - rh - BASE_PAD) })
  }
}

const sheetW = CELL_W * FRAMES, sheetH = CELL_H * ROWS.length
const base = sharp({ create: { width: sheetW, height: sheetH, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
const outDir = resolve(root, '../app/composeApp/src/commonMain/composeResources/drawable')
// Paletted PNG keeps the bundled asset small (the art has a limited palette).
await base.composite(composites).png({ palette: true, compressionLevel: 9 }).toFile(resolve(outDir, 'dog_sheet.png'))
console.log(`dog_sheet.png — ${ROWS.length} clips × ${FRAMES} frames, cell ${CELL_W}x${CELL_H} (${sheetW}x${sheetH})`)

// preview on a pastel background for review
await sharp({ create: { width: sheetW, height: sheetH, channels: 4, background: { r: 174, g: 223, b: 166, alpha: 1 } } })
  .composite([{ input: resolve(outDir, 'dog_sheet.png') }]).resize(Math.round(sheetW / 4)).png().toFile('/tmp/dog-sheet-preview.png')
console.log('preview → /tmp/dog-sheet-preview.png')
