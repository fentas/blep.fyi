import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  // Served from the apex domain (blep.fyi), so assets live at the root.
  base: '/',
  // Multi-page: landing + /donate.html (app's donate link) + /privacy.html (store-required).
  build: {
    rollupOptions: {
      input: {
        main: 'index.html',
        guide: 'guide.html',
        donate: 'donate.html',
        privacy: 'privacy.html',
      },
    },
  },
  plugins: [
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['logo.svg', 'favicon.svg'],
      manifest: {
        name: 'blep — BLE Pointer & Tracker',
        short_name: 'blep',
        description: 'Find your lost Bluetooth things — guided by signal strength.',
        theme_color: '#5F90C3',
        background_color: '#EEF2F6',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          { src: 'logo.svg', sizes: 'any', type: 'image/svg+xml' },
        ],
      },
    }),
  ],
})
