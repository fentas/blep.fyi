// Docs-page entry: theme toggle + scroll-spy (no service worker here).
import { initThemeToggle } from './theme.js'
import { initScrollSpy } from './spy.js'

initThemeToggle(document.getElementById('theme-toggle'))
initScrollSpy()
