// Generates the tracking-screen dog sprite sheet from a parametric vector pup.
// 8 frames per clip × 4 clip rows (walk / look / idle / sit), rendered at 2×.
// Output → app/composeApp/src/commonMain/composeResources/drawable/dog_sheet.png
// Run with `npm run gen:dog`. The Compose sprite player slices it by row/frame.
import sharp from 'sharp'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const INK = '#27313B', COAT = '#E7D6BC', PATCH = '#C79E73', CREAM = '#F6F1E7', PINK = '#F4A6AD'
const W = 200, H = 158, GROUND = 142, N = 8

function leg(cx, top, bot, w, fill) {
  const r = w / 2
  return `<rect x="${(cx - r).toFixed(1)}" y="${top.toFixed(1)}" width="${w}" height="${(bot - top).toFixed(1)}" rx="${r}" fill="${fill}" stroke="${INK}" stroke-width="3"/>`
}
function gait(p, phase, stride, lift) {
  const th = 2 * Math.PI * (p + phase)
  return { dx: stride * Math.cos(th), lift: lift * Math.max(0, Math.sin(th)) }
}
function pup({ p = 0, moving = false, head = 0, blink = 0, mouth = 0, tail = 0, sit = false, breath = 0 } = {}) {
  const bob = sit ? 0 : (moving ? -3 * Math.abs(Math.sin(2 * Math.PI * p)) : breath)
  const by = 90 + bob
  let behind = '', front = ''
  if (sit) {
    front += leg(126, 116, GROUND, 18, COAT)
    behind += `<ellipse cx="80" cy="130" rx="30" ry="16" fill="${PATCH}" stroke="${INK}" stroke-width="3"/>`
  } else if (moving) {
    const fb = gait(p, 0.5, 6, 10), ff = gait(p, 0.0, 6, 10), nb = gait(p, 0.0, 7, 12), nf = gait(p, 0.5, 7, 12)
    behind += leg(72 + fb.dx, 108 + bob, GROUND - fb.lift, 14, PATCH) + leg(120 + ff.dx, 108 + bob, GROUND - ff.lift, 14, PATCH)
    front += leg(82 + nb.dx, 110 + bob, GROUND - nb.lift, 17, COAT) + leg(130 + nf.dx, 110 + bob, GROUND - nf.lift, 17, COAT)
  } else {
    behind += leg(72, 108 + bob, GROUND, 14, PATCH) + leg(120, 108 + bob, GROUND, 14, PATCH)
    front += leg(82, 110 + bob, GROUND, 17, COAT) + leg(130, 110 + bob, GROUND, 17, COAT)
  }
  const tw = tail * Math.PI / 180, tx = 46 + 12 * Math.sin(tw), ty = 66 + bob - 8 * Math.cos(tw)
  behind += `<path d="M60 ${88 + bob} Q42 ${74 + bob} ${tx} ${ty}" fill="none" stroke="${INK}" stroke-width="14" stroke-linecap="round"/>`
  behind += `<path d="M60 ${88 + bob} Q42 ${74 + bob} ${tx} ${ty}" fill="none" stroke="${COAT}" stroke-width="8.5" stroke-linecap="round"/>`
  let body = `<ellipse cx="104" cy="${by}" rx="60" ry="33" fill="${COAT}" stroke="${INK}" stroke-width="3.5"/>`
  body += `<path d="M66 ${70 + bob} Q104 ${56 + bob} 148 ${74 + bob}" fill="none" stroke="${PATCH}" stroke-width="22" stroke-linecap="round" opacity="0.65"/>`
  body += `<ellipse cx="112" cy="${by + 16}" rx="42" ry="19" fill="${CREAM}"/>`
  const hx = 158, hy = 66 + bob
  let h = `<g transform="rotate(${head} ${hx} ${hy + 12})">`
  h += `<path d="M${hx - 12} ${hy - 20} Q${hx - 42} ${hy - 8} ${hx - 32} ${hy + 30} Q${hx - 14} ${hy + 26} ${hx - 6} ${hy + 2} Z" fill="${PATCH}" stroke="${INK}" stroke-width="3"/>`
  h += `<circle cx="${hx}" cy="${hy}" r="34" fill="${COAT}" stroke="${INK}" stroke-width="3.5"/>`
  h += `<ellipse cx="${hx + 24}" cy="${hy + 14}" rx="22" ry="16" fill="${CREAM}" stroke="${INK}" stroke-width="3"/>`
  h += `<ellipse cx="${hx + 42}" cy="${hy + 10}" rx="6" ry="5" fill="${INK}"/>`
  if (mouth > 0) h += `<path d="M${hx + 28} ${hy + 22} q7 ${5 + mouth * 7} 14 0" fill="${PINK}" stroke="${INK}" stroke-width="2.5"/>`
  const eo = 1 - blink
  h += `<ellipse cx="${hx + 13}" cy="${hy - 3}" rx="13" ry="11" fill="${PATCH}" opacity="0.55"/>`
  h += `<ellipse cx="${hx + 14}" cy="${hy - 1}" rx="6" ry="${(7 * eo).toFixed(1)}" fill="${INK}"/>`
  if (eo > 0.4) h += `<circle cx="${hx + 16}" cy="${hy - 3}" r="2" fill="#fff"/>`
  h += `<path d="M${hx - 4} ${hy - 26} Q${hx - 34} ${hy - 14} ${hx - 26} ${hy + 34} Q${hx - 4} ${hy + 30} ${hx + 4} ${hy - 4} Z" fill="${PATCH}" stroke="${INK}" stroke-width="3.5"/>`
  h += `</g>`
  return `<g>${behind}${body}${front}${h}</g>`
}
function frame(row, i) {
  const t = i / N, a = 2 * Math.PI * t
  if (row === 0) return pup({ p: t, moving: true, tail: 14 * Math.sin(a) })                              // walk
  if (row === 1) return pup({ head: 20 * Math.sin(a), tail: 9 * Math.sin(a), breath: -1.5 * Math.abs(Math.sin(a)), blink: i === 4 ? 1 : 0 }) // look
  if (row === 2) return pup({ tail: 10 * Math.sin(a), breath: -2 * Math.abs(Math.sin(a)), blink: i === 6 ? 1 : 0 }) // idle
  return pup({ sit: true, mouth: 1, head: 4 * Math.sin(a), tail: 30 * Math.sin(2 * a), blink: i === 5 ? 1 : 0 })    // sit/happy
}

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W * N * 2}" height="${H * 4 * 2}" viewBox="0 0 ${W * N} ${H * 4}">`
for (let row = 0; row < 4; row++) for (let i = 0; i < N; i++) svg += `<g transform="translate(${i * W} ${row * H})">${frame(row, i)}</g>`
svg += `</svg>`
const out = resolve(root, '../app/composeApp/src/commonMain/composeResources/drawable')
mkdirSync(out, { recursive: true })
await sharp(Buffer.from(svg)).png().toFile(resolve(out, 'dog_sheet.png'))
console.log(`wrote dog_sheet.png — cell ${W * 2}x${H * 2}, ${N} frames × 4 clips`)
