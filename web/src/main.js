// Minimal entry: register the PWA service worker (auto-update) + theme toggle.
// vite-plugin-pwa generates the worker; this just wires it up.
import { registerSW } from 'virtual:pwa-register'
import { initThemeToggle } from './theme.js'

registerSW({ immediate: true })
initThemeToggle(document.getElementById('theme-toggle'))
