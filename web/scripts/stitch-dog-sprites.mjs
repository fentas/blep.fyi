// Builds the app's dog sprite sheet from the AI-generated source grids and
// turns each loose set of frames into a SMOOTH animation:
//
//   1. CUT   — flood-key the cream page/shadow/dividers from the edges, then
//              connected-component cleanup keeps the pup (+bone) and drops
//              captions, specks, neighbour-frame bleed (touches L/R edge) and
//              thin divider lines. Crop expanded horizontally so nose/tail
//              aren't clipped.
//   2. ALIGN — scale every frame of a clip by one uniform factor, then place
//              each by its centre of mass at a fixed point (drift removed, so
//              the frames stack on top of each other).
//   3. ORDER — source frames are usually already sequenced, so the source order
//              is kept by default; a "smoothest cycle" (greedy NN + 2-opt over
//              the frame-to-frame diff) is *suggested* in the report and can be
//              applied per clip via `order: true`.
//   4. WRITE — compose the sheet + web/dog-sprites-report.md (applied/suggested
//              order, roughness, and isolated frames to review).
//
// Output → app/composeApp/src/commonMain/composeResources/drawable/dog_sheet.png
// Run with `npm run gen:dog`.  Source folders (spr24/, sprites/) are gitignored.
import sharp from 'sharp'
import { writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

const CLIPS = [
  { clip: 'walk',  dir: 'spr24',   file: 'Gemini_Generated_Image_fbd8sxfbd8sxfbd8.png', cols: 8, rows: 3, frames: 24, order: false },
  { clip: 'look',  dir: 'spr24',   file: 'Gemini_Generated_Image_w1h6y3w1h6y3w1h6.png', cols: 8, rows: 3, frames: 24, order: false },
  { clip: 'idle',  dir: 'spr24',   file: 'Gemini_Generated_Image_xsgea4xsgea4xsge.png', cols: 8, rows: 3, frames: 24, order: false },
  { clip: 'found', dir: 'sprites', file: 'Gemini_Generated_Image_6m0nln6m0nln6m0n.png', cols: 6, rows: 2, frames: 12, order: false },
]

const CELL_W = 400, CELL_H = 264, TARGET_H = 170, ANCHOR_Y = 0.54
const AREA_MIN = 900, FILL_MIN = 0.12, MASK = 40
const isBgPx = (r, g, b) => Math.min(r, g, b) > 185

// ── 1. CUT ────────────────────────────────────────────────────────────────
function extractCell(src, W, H, ch, cellX, cellY, cellW, cellH) {
  const EX = Math.round(cellW * 0.18)
  const cx0 = Math.max(0, cellX - EX), cx1 = Math.min(W, cellX + cellW + EX)
  const cy0 = Math.max(0, cellY + 2), cy1 = Math.min(H, cellY + cellH - 2)
  const cw = cx1 - cx0, chh = cy1 - cy0
  const captionTop = chh - Math.round(cellH * 0.16)

  const buf = new Uint8Array(cw * chh * 4)
  for (let y = 0; y < chh; y++) for (let x = 0; x < cw; x++) {
    const si = ((cy0 + y) * W + (cx0 + x)) * ch, di = (y * cw + x) * 4
    buf[di] = src[si]; buf[di + 1] = src[si + 1]; buf[di + 2] = src[si + 2]; buf[di + 3] = 255
  }
  const seen = new Uint8Array(cw * chh), stack = []
  const visit = (x, y) => {
    if (x < 0 || y < 0 || x >= cw || y >= chh) return
    const p = y * cw + x; if (seen[p]) return
    const i = p * 4; if (!isBgPx(buf[i], buf[i + 1], buf[i + 2])) return
    seen[p] = 1; buf[i + 3] = 0; stack.push(p)
  }
  for (let x = 0; x < cw; x++) { visit(x, 0); visit(x, chh - 1) }
  for (let y = 0; y < chh; y++) { visit(0, y); visit(cw - 1, y) }
  while (stack.length) { const p = stack.pop(), x = p % cw, y = (p / cw) | 0; visit(x + 1, y); visit(x - 1, y); visit(x, y + 1); visit(x, y - 1) }

  const label = new Int32Array(cw * chh); let n = 0; const comps = []
  for (let s = 0; s < cw * chh; s++) {
    if (buf[s * 4 + 3] === 0 || label[s]) continue
    n++; const q = [s]; label[s] = n
    let area = 0, minx = cw, miny = chh, maxx = 0, maxy = 0
    while (q.length) {
      const p = q.pop(), x = p % cw, y = (p / cw) | 0
      area++; if (x < minx) minx = x; if (x > maxx) maxx = x; if (y < miny) miny = y; if (y > maxy) maxy = y
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx, ny = y + dy
        if (nx < 0 || ny < 0 || nx >= cw || ny >= chh) continue
        const np = ny * cw + nx; if (label[np] || buf[np * 4 + 3] === 0) continue
        label[np] = n; q.push(np)
      }
    }
    const fill = area / ((maxx - minx + 1) * (maxy - miny + 1))
    comps.push({ id: n, area, fill, touchesLR: minx === 0 || maxx === cw - 1, miny })
  }
  const keep = new Set(comps.filter((c) => c.area >= AREA_MIN && c.fill >= FILL_MIN && !c.touchesLR && c.miny < captionTop).map((c) => c.id))
  if (!keep.size) return null
  let kminx = cw, kminy = chh, kmaxx = 0, kmaxy = 0, ksx = 0, ksy = 0, ka = 0
  for (let y = 0; y < chh; y++) for (let x = 0; x < cw; x++) {
    const p = y * cw + x; if (!label[p]) continue
    if (!keep.has(label[p])) { buf[p * 4 + 3] = 0; continue }
    ka++; ksx += x; ksy += y
    if (x < kminx) kminx = x; if (x > kmaxx) kmaxx = x; if (y < kminy) kminy = y; if (y > kmaxy) kmaxy = y
  }
  const bw = kmaxx - kminx + 1, bh = kmaxy - kminy + 1
  const out = new Uint8Array(bw * bh * 4)
  for (let y = 0; y < bh; y++) for (let x = 0; x < bw; x++) {
    const si = ((kminy + y) * cw + (kminx + x)) * 4, di = (y * bw + x) * 4
    out[di] = buf[si]; out[di + 1] = buf[si + 1]; out[di + 2] = buf[si + 2]; out[di + 3] = buf[si + 3]
  }
  return { raw: out, bw, bh, cxRel: ksx / ka - kminx, cyRel: ksy / ka - kminy }
}

const median = (a) => { const s = [...a].sort((x, y) => x - y); return s[s.length >> 1] || 1 }

// ── 3. ORDER: smoothest cycle via nearest-neighbour + 2-opt ─────────────────
function maskDiff(a, b) { let d = 0; for (let i = 0; i < a.length; i++) d += a[i] !== b[i] ? 1 : 0; return d }
function smoothestOrder(masks) {
  const N = masks.length
  const D = masks.map((m, i) => masks.map((o, j) => (i === j ? 0 : maskDiff(m, o))))
  const used = Array(N).fill(false); const tour = [0]; used[0] = true
  for (let s = 1; s < N; s++) {
    const last = tour[tour.length - 1]; let best = -1, bd = Infinity
    for (let j = 0; j < N; j++) if (!used[j] && D[last][j] < bd) { bd = D[last][j]; best = j }
    tour.push(best); used[best] = true
  }
  const len = (t) => { let s = 0; for (let i = 0; i < N; i++) s += D[t[i]][t[(i + 1) % N]]; return s }
  let improved = true
  while (improved) {
    improved = false
    for (let i = 0; i < N - 1; i++) for (let k = i + 1; k < N; k++) {
      const nt = tour.slice(0, i).concat(tour.slice(i, k + 1).reverse(), tour.slice(k + 1))
      if (len(nt) < len(tour) - 1e-9) { tour.splice(0, N, ...nt); improved = true }
    }
  }
  return tour
}

const MAX_FRAMES = Math.max(...CLIPS.map((c) => c.frames))
const sheetW = CELL_W * MAX_FRAMES, sheetH = CELL_H * CLIPS.length
const composites = []
const report = ['# Dog sprite frame report', '',
  'Auto-generated by `npm run gen:dog`. Each clip is cut + centre-of-mass aligned',
  '(drift removed). Source frame order is kept by default; the smoothest order is',
  'only *suggested* here (set `order: true` for a clip to apply it). Use the',
  'isolated-frames list to spot frames worth redrawing/replacing.', '']

for (let r = 0; r < CLIPS.length; r++) {
  const C = CLIPS[r]
  const { data, info } = await sharp(resolve(root, '..', C.dir, C.file)).ensureAlpha().raw().toBuffer({ resolveWithObject: true })
  const W = info.width, H = info.height, ch = info.channels
  const cellW = W / C.cols, cellH = H / C.rows

  // 1. cut
  const cuts = []
  for (let k = 0; k < C.frames; k++) {
    const gc = k % C.cols, gr = (k / C.cols) | 0
    cuts.push(extractCell(data, W, H, ch, Math.round(gc * cellW), Math.round(gr * cellH), Math.round(cellW), Math.round(cellH)))
  }
  // 2. align: uniform scale + centre-of-mass placement (drift removed)
  const scale = TARGET_H / median(cuts.filter(Boolean).map((c) => c.bh))
  const anchorX = CELL_W / 2, anchorY = CELL_H * ANCHOR_Y
  const placed = [], masks = [], drift = []
  for (let k = 0; k < C.frames; k++) {
    const f = cuts[k]
    if (!f) { placed.push(null); masks.push(null); drift.push(null); continue }
    let rw = Math.max(1, Math.round(f.bw * scale)), rh = Math.max(1, Math.round(f.bh * scale))
    if (rw > CELL_W || rh > CELL_H) { const fit = Math.min(CELL_W / rw, CELL_H / rh); rw = Math.round(rw * fit); rh = Math.round(rh * fit) }
    const dog = await sharp(Buffer.from(f.raw), { raw: { width: f.bw, height: f.bh, channels: 4 } }).resize(rw, rh).png().toBuffer()
    let left = Math.round(anchorX - f.cxRel * scale)
    let top = Math.round(anchorY - f.cyRel * scale)
    drift.push([left, top])
    left = Math.max(Math.min(left, CELL_W - rw), 0); top = Math.max(Math.min(top, CELL_H - rh), 0)
    const cell = await sharp({ create: { width: CELL_W, height: CELL_H, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
      .composite([{ input: dog, left, top }]).png().toBuffer()
    placed.push(cell)
    const m = await sharp(cell).extractChannel('alpha').resize(MASK, MASK, { fit: 'fill' }).raw().toBuffer()
    masks.push(Uint8Array.from(m, (v) => (v > 24 ? 1 : 0)))
  }
  // fill any failed frame with its predecessor so the cycle stays full
  for (let k = 0; k < C.frames; k++) if (!placed[k]) { const p = (k - 1 + C.frames) % C.frames; placed[k] = placed[p]; masks[k] = masks[p] }

  // 3. order. The source frames are usually already correctly sequenced
  // (FRAME 1..N), and silhouette-similarity reordering can fold a gait cycle,
  // so we KEEP the source order by default and only *suggest* the smoothest
  // order in the report (opt in per clip via `order: true`).
  const identity = masks.map((_, i) => i)
  const suggested = smoothestOrder(masks)
  const order = C.order ? suggested : identity
  const roughness = (t) => Math.round(t.reduce((s, _, i) => s + maskDiff(masks[t[i]], masks[t[(i + 1) % t.length]]), 0))
  // isolated frames: those whose best match is still far (candidate for redraw)
  const isolated = []
  for (let i = 0; i < masks.length; i++) {
    let best = Infinity
    for (let j = 0; j < masks.length; j++) if (j !== i) best = Math.min(best, maskDiff(masks[i], masks[j]))
    if (best > MASK * MASK * 0.16) isolated.push(i + 1)
  }

  // 4. write into sheet in the chosen order
  order.forEach((srcIdx, dst) => composites.push({ input: placed[srcIdx], left: dst * CELL_W, top: r * CELL_H }))

  report.push(`## ${C.clip} — ${C.file} (${C.frames} frames)`)
  report.push(`applied order: ${C.order ? 'suggested (reordered)' : 'source order (1..N)'}`)
  report.push(`transition roughness — source: ${roughness(identity)}, suggested: ${roughness(suggested)}`)
  report.push(`suggested smoothest order: ${suggested.map((i) => i + 1).join(', ')}`)
  report.push(isolated.length ? `isolated frames to review (consider redraw/replace): ${isolated.join(', ')}` : 'no isolated frames')
  report.push('')
}

const outDir = resolve(root, '../app/composeApp/src/commonMain/composeResources/drawable')
await sharp({ create: { width: sheetW, height: sheetH, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
  .composite(composites).png({ palette: true, compressionLevel: 9 }).toFile(resolve(outDir, 'dog_sheet.png'))
writeFileSync(resolve(root, 'dog-sprites-report.md'), report.join('\n'))
console.log(`dog_sheet.png — ${CLIPS.length} clips, up to ${MAX_FRAMES} frames, cell ${CELL_W}x${CELL_H} (${sheetW}x${sheetH})`)
console.log('report → web/dog-sprites-report.md')
