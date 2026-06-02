// Light/dark toggle. The no-flash inline script in <head> applies any saved
// choice before paint; this wires the header button and keeps its icon in sync.
// No saved choice → follow the OS via the CSS `prefers-color-scheme` media query.
const html = document.documentElement
const mq = window.matchMedia('(prefers-color-scheme: dark)')

const isDark = () =>
  html.classList.contains('dark') || (!html.classList.contains('light') && mq.matches)

export function initThemeToggle(button) {
  if (!button) return
  const paint = () => {
    const dark = isDark()
    button.textContent = dark ? '☀️' : '🌙'
    button.setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode')
  }
  button.addEventListener('click', () => {
    const next = isDark() ? 'light' : 'dark'
    html.classList.remove('light', 'dark')
    html.classList.add(next)
    try { localStorage.setItem('blep-theme', next) } catch {}
    paint()
  })
  // Track OS changes while on "system" (no explicit choice saved).
  mq.addEventListener('change', paint)
  paint()
}
