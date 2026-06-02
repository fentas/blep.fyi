// Frame-library pipeline for the app's dog animation.
//
//   1. CUT      — every frame of every source sheet is flood-keyed + cleaned
//                 (drops page/shadow/dividers, captions, specks, neighbour
//                 bleed, thin border lines) and cropped to the pup's extent.
//   2. ALIGN    — one global uniform scale + centre-of-mass placement; the
//                 applied offset (drift) is recorded per frame.
//   3. CATEGORISE — frames are clustered by pose similarity so like frames are
//                 grouped (so they can be reused between animations).
//   4. MANIFEST — web/dog-frames.json holds every frame (drift + category) and
//                 the CLIPS as ordered frame-id lists. It is the manual control
//                 surface: hand-pick frames, reuse frames across sheets, rename
//                 / link categories. Existing clip/category edits are preserved;
//                 drift + categories are (re)computed each run (calc once, here).
//   5. BUILD    — the sprite sheet is assembled from the clip frame-lists, and a
//                 cross-reference transition map is baked to DogFrames.kt (best
//                 entry frame per clip pair) so the app needs no runtime calc.
//
// Run with `npm run gen:dog`. Source folders (spr24/, sprites/) are gitignored.
import sharp from 'sharp'
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

// Source sheets (id → grid). Add sheets here; reference their frames from clips.
const SHEETS = {
  walk:    { dir: 'spr24', file: 'Gemini_Generated_Image_u9yhe2u9yhe2u9yh.png', cols: 8, rows: 3, frames: 24 }, // trot loop
  sit:     { dir: 'spr24', file: 'Gemini_Generated_Image_w1h6y3w1h6y3w1h6.png', cols: 8, rows: 3, frames: 24 }, // sit / look
  stand:   { dir: 'spr24', file: 'Gemini_Generated_Image_xsgea4xsgea4xsge.png', cols: 8, rows: 3, frames: 24 }, // idle
  dig:     { dir: 'spr24', file: 'Gemini_Generated_Image_tgx7wutgx7wutgx7.png', cols: 8, rows: 3, frames: 24 }, // dig → bone
  walkdig: { dir: 'spr24', file: 'Gemini_Generated_Image_fbd8sxfbd8sxfbd8.png', cols: 8, rows: 3, frames: 24 }, // walk → dig → bone (spare)
}
// Default clips (rows, in this order) → playback + frame-id lists. The manifest
// overrides these (edit web/dog-frames.json or the browser editor's export).
const DEFAULT_CLIPS = {
  walk:  { fps: 12, loop: true,  reverse: true,  frames: range('walk', 24) },
  look:  { fps: 12, loop: true,  reverse: true,  frames: range('sit', 24) },
  idle:  { fps: 12, loop: true,  reverse: true,  frames: range('stand', 24) },
  found: { fps: 12, loop: false, reverse: false, frames: range('dig', 24) },
}
function range(sheet, n) { return Array.from({ length: n }, (_, i) => `${sheet}#${i}`) }

const CELL_W = 400, CELL_H = 264, TARGET_H = 170, ANCHOR_Y = 0.54
const AREA_MIN = 900, FILL_MIN = 0.12, MASK = 40
const isBgPx = (r, g, b) => Math.min(r, g, b) > 185
const maskDiff = (a, b) => { let d = 0; for (let i = 0; i < a.length; i++) d += a[i] !== b[i] ? 1 : 0; return d }
const median = (a) => { const s = [...a].sort((x, y) => x - y); return s[s.length >> 1] || 1 }

// ── 1. CUT ──────────────────────────────────────────────────────────────────
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
    const w = maxx - minx + 1, h = maxy - miny + 1
    comps.push({ id: n, area, fill: area / (w * h), aspect: Math.max(w, h) / Math.min(w, h), touchesLR: minx === 0 || maxx === cw - 1, miny })
  }
  const keep = new Set(comps.filter((c) => c.area >= AREA_MIN && c.fill >= FILL_MIN && c.aspect <= 7 && !c.touchesLR && c.miny < captionTop).map((c) => c.id))
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

// Cut every frame of every sheet → frame library keyed by `${sheet}#${idx}`.
const FRAMES = {}
for (const [key, S] of Object.entries(SHEETS)) {
  const { data, info } = await sharp(resolve(root, '..', S.dir, S.file)).ensureAlpha().raw().toBuffer({ resolveWithObject: true })
  const W = info.width, H = info.height, ch = info.channels, cw = W / S.cols, chh = H / S.rows
  for (let k = 0; k < S.frames; k++) {
    const gc = k % S.cols, gr = (k / S.cols) | 0
    const cut = extractCell(data, W, H, ch, Math.round(gc * cw), Math.round(gr * chh), Math.round(cw), Math.round(chh))
    if (cut) FRAMES[`${key}#${k}`] = cut
  }
}

// ── 2. ALIGN (global uniform scale) + render each frame to a placed cell ──────
const SCALE = TARGET_H / median(Object.values(FRAMES).map((f) => f.bh))
const PLACED = {}, MASKS = {}, DRIFT = {}
for (const [id, f] of Object.entries(FRAMES)) {
  let rw = Math.max(1, Math.round(f.bw * SCALE)), rh = Math.max(1, Math.round(f.bh * SCALE))
  if (rw > CELL_W || rh > CELL_H) { const fit = Math.min(CELL_W / rw, CELL_H / rh); rw = Math.round(rw * fit); rh = Math.round(rh * fit) }
  const dog = await sharp(Buffer.from(f.raw), { raw: { width: f.bw, height: f.bh, channels: 4 } }).resize(rw, rh).png().toBuffer()
  let left = Math.round(CELL_W / 2 - f.cxRel * SCALE), top = Math.round(CELL_H * ANCHOR_Y - f.cyRel * SCALE)
  DRIFT[id] = [left, top]
  left = Math.max(Math.min(left, CELL_W - rw), 0); top = Math.max(Math.min(top, CELL_H - rh), 0)
  const cell = await sharp({ create: { width: CELL_W, height: CELL_H, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
    .composite([{ input: dog, left, top }]).png().toBuffer()
  PLACED[id] = cell
  const m = await sharp(cell).extractChannel('alpha').resize(MASK, MASK, { fit: 'fill' }).raw().toBuffer()
  MASKS[id] = Uint8Array.from(m, (v) => (v > 24 ? 1 : 0))
}

// ── 3. CATEGORISE (group like poses; reusable between animations) ─────────────
const ids = Object.keys(FRAMES)
const cats = []; const CAT = {}
const CAT_THRESH = MASK * MASK * 0.085
for (const id of ids) {
  let best = -1, bd = Infinity
  cats.forEach((c, ci) => { const d = maskDiff(MASKS[id], c.proto); if (d < bd) { bd = d; best = ci } })
  if (best >= 0 && bd < CAT_THRESH) { CAT[id] = best; cats[best].members.push(id) }
  else { CAT[id] = cats.length; cats.push({ proto: MASKS[id], members: [id] }) }
}

// ── 4. MANIFEST (read existing manual edits, else defaults) ───────────────────
const manifestPath = resolve(root, 'dog-frames.json')
let manifest = existsSync(manifestPath) ? JSON.parse(readFileSync(manifestPath, 'utf8')) : null
const clips = (manifest && manifest.clips) ? manifest.clips : DEFAULT_CLIPS
// validate frame ids + fill playback defaults (so old/partial manifests work)
for (const [name, c] of Object.entries(clips)) {
  c.frames = c.frames.filter((id) => PLACED[id])
  const d = DEFAULT_CLIPS[name] || { fps: 12, loop: true, reverse: false }
  if (c.fps == null) c.fps = d.fps
  if (c.loop == null) c.loop = d.loop
  if (c.reverse == null) c.reverse = d.reverse
}
const newManifest = {
  note: 'Edit `clips` to hand-pick/reorder frames (reuse ids across sheets). `category` groups like poses. Re-run `npm run gen:dog`.',
  cell: [CELL_W, CELL_H],
  categories: cats.map((c, i) => ({ id: `c${i}`, frames: c.members })),
  frames: Object.fromEntries(ids.map((id) => [id, { drift: DRIFT[id], category: `c${CAT[id]}` }])),
  clips,
}
writeFileSync(manifestPath, JSON.stringify(newManifest, null, 1))

// ── 5. LOOP-TRIM looping clips to their best loop point ───────────────────────
// (one-shot clips like `found` keep their full dig→bone sequence). Trimming the
// tail removes frames that drift into another pose and hard-cut on loop.
const clipList = Object.entries(clips)
const SEAM_OK = MASK * MASK * 0.05
for (const [name, c] of clipList) {
  if (!c.loop || c.frames.length <= 8) continue
  let bestK = c.frames.length - 1, bestSeam = Infinity
  for (let k = c.frames.length - 1; k >= 7; k--) {
    const seam = maskDiff(MASKS[c.frames[k]], MASKS[c.frames[0]])
    if (seam <= SEAM_OK) { bestK = k; break }       // longest acceptably-closing loop
    if (seam < bestSeam) { bestSeam = seam; bestK = k }
  }
  if (bestK < c.frames.length - 1) c.frames = c.frames.slice(0, bestK + 1)
}

// ── BUILD sheet ───────────────────────────────────────────────────────────────
const MAXF = Math.max(...clipList.map(([, c]) => c.frames.length))
const composites = []
clipList.forEach(([, c], r) => c.frames.forEach((id, k) => composites.push({ input: PLACED[id], left: k * CELL_W, top: r * CELL_H })))
const outDir = resolve(root, '../app/composeApp/src/commonMain/composeResources/drawable')
await sharp({ create: { width: CELL_W * MAXF, height: CELL_H * clipList.length, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
  .composite(composites).png({ palette: true, compressionLevel: 9 }).toFile(resolve(outDir, 'dog_sheet.png'))

// ── DogFrames.kt: per-clip frame count + transition map (calc once) ───────────
const clipMasks = clipList.map(([, c]) => c.frames.map((id) => MASKS[id]))
const trans = clipMasks.map((mf) => clipMasks.map((mt) => mf.map((a) => {
  let best = 0, bd = Infinity; mt.forEach((b, j) => { const d = maskDiff(a, b); if (d < bd) { bd = d; best = j } }); return best
})))
const frameCount = clipList.map(([, c]) => c.frames.length)
const fps = clipList.map(([, c]) => c.fps)
const loop = clipList.map(([, c]) => c.loop)
const reverse = clipList.map(([, c]) => c.reverse)
const ktRows = trans.map((a) => '        arrayOf(' + a.map((b) => 'intArrayOf(' + b.join(', ') + ')').join(', ') + ')').join(',\n')
writeFileSync(resolve(root, '../app/composeApp/src/commonMain/kotlin/fyi/blep/ui/screens/DogFrames.kt'),
  `package fyi.blep.ui.screens\n\n// Generated by web/scripts/stitch-dog-sprites.mjs — do not edit.\n` +
  `internal object DogFrames {\n` +
  `    /** All per-clip arrays are indexed by DogClip.ordinal (clip order in dog-frames.json). */\n` +
  `    /** Frame count per clip, after the stitcher's loop-trim. */\n` +
  `    val frameCount: IntArray = intArrayOf(${frameCount.join(', ')})\n\n` +
  `    /** Playback frames-per-second per clip. */\n` +
  `    val fps: IntArray = intArrayOf(${fps.join(', ')})\n\n` +
  `    /** Whether each clip loops (true) or plays once and holds (false). */\n` +
  `    val loop: BooleanArray = booleanArrayOf(${loop.join(', ')})\n\n` +
  `    /** Whether each clip plays in reverse. */\n` +
  `    val reverse: BooleanArray = booleanArrayOf(${reverse.join(', ')})\n\n` +
  `    /** transition[fromClip.ordinal][toClip.ordinal][fromFrame] = matching entry frame in toClip. */\n` +
  `    val transition: Array<Array<IntArray>> = arrayOf(\n${ktRows}\n    )\n}\n`)

// ── ANIMATION TEST: loop seams + transition seams (look for hard cuts) ─────────
const pct = (d) => `${(100 * d / (MASK * MASK)).toFixed(1)}%`
console.log(`dog_sheet.png — clips: ${clipList.map(([n, c]) => `${n}(${c.frames.length})`).join(', ')}`)
console.log('loop seams (last→first; lower = smoother):')
clipList.forEach(([n, c], i) => {
  const m = clipMasks[i]; if (!c.loop) { console.log(`  ${n}: one-shot (no loop)`); return }
  console.log(`  ${n}: ${pct(maskDiff(m[m.length - 1], m[0]))}`)
})
console.log('transition seams (exit→matched entry):')
clipList.forEach(([fn], fi) => clipList.forEach(([tn], ti) => {
  if (fi === ti) return
  const exit = clipMasks[fi][clipMasks[fi].length - 1]
  const entry = clipMasks[ti][trans[fi][ti][clipMasks[fi].length - 1]]
  const d = maskDiff(exit, entry)
  if (d > MASK * MASK * 0.10) console.log(`  ${fn}→${tn}: ${pct(d)}  (hard cut)`)
}))

// seam filmstrip for visual review: per clip [..last2,last1,last | first,1,2..]
const STH = 130, STW = Math.round(CELL_W * STH / CELL_H)
const strip = []
for (let r = 0; r < clipList.length; r++) {
  const [, c] = clipList[r], n = c.frames.length
  const seq = [n - 3, n - 2, n - 1, 0, 1, 2].map((i) => c.frames[(i + n) % n])
  for (let j = 0; j < seq.length; j++) {
    const img = await sharp(PLACED[seq[j]]).resize(STW, STH).png().toBuffer()
    strip.push({ input: img, left: j * STW + (j >= 3 ? 16 : 0), top: r * STH })
  }
}
await sharp({ create: { width: 6 * STW + 16, height: clipList.length * STH, channels: 4, background: { r: 174, g: 223, b: 166, alpha: 1 } } })
  .composite(strip).png().toFile('/tmp/dog-seams.png')
console.log('seam filmstrip → /tmp/dog-seams.png (gap = loop wrap; jump across gap = hard cut)')

// ── interactive browser editor (web/dog-test/index.html) ──────────────────────
// Emits a LIBRARY atlas of every cut frame + metadata so the editor can show all
// frames (filterable by category/sheet), drag new frames into clips, reorder,
// and scrub. Mirrors the app player's per-clip playback.
const ALL = Object.keys(PLACED)
const LIB_COLS = 12, LIB_W = Math.round(CELL_W * 0.6), LIB_H = Math.round(CELL_H * 0.6)
const libRows = Math.ceil(ALL.length / LIB_COLS)
const libComposite = await Promise.all(ALL.map(async (id, i) => ({
  input: await sharp(PLACED[id]).resize(LIB_W, LIB_H).png().toBuffer(),
  left: (i % LIB_COLS) * LIB_W, top: Math.floor(i / LIB_COLS) * LIB_H,
})))
mkdirSync(resolve(root, 'dog-test'), { recursive: true })
await sharp({ create: { width: LIB_COLS * LIB_W, height: libRows * LIB_H, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
  .composite(libComposite).png({ palette: true, compressionLevel: 9 }).toFile(resolve(root, 'dog-test/library.png'))

const allMasks = ALL.map((id) => MASKS[id])
const gdiff = allMasks.map((a) => allMasks.map((b) => maskDiff(a, b)))
const framesMeta = {}
ALL.forEach((id, i) => { framesMeta[id] = { col: i % LIB_COLS, row: Math.floor(i / LIB_COLS), ord: i, cat: `c${CAT[id]}`, sheet: id.split('#')[0], drift: DRIFT[id] } })
const clipsOut = {}
clipList.forEach(([n, c]) => { clipsOut[n] = { fps: c.fps, loop: c.loop, reverse: c.reverse, frames: c.frames } })
const anim = {
  cell: [LIB_W, LIB_H], maskArea: MASK * MASK, libCols: LIB_COLS,
  ids: ALL, frames: framesMeta, categories: cats.map((_, i) => `c${i}`),
  sheets: Object.keys(SHEETS), clips: clipsOut, diff: gdiff,
}
writeFileSync(resolve(root, 'dog-test/anim.js'), `window.DOG = ${JSON.stringify(anim)}\n`)
console.log(`browser editor → open web/dog-test/index.html (${ALL.length} frames, ${cats.length} categories)`)
