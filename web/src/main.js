// Minimal entry: register the PWA service worker (auto-update).
// vite-plugin-pwa generates the worker; this just wires it up.
import { registerSW } from 'virtual:pwa-register'

registerSW({ immediate: true })
