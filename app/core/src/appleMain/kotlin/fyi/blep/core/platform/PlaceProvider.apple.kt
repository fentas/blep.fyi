package fyi.blep.core.platform

// iOS/watchOS CoreLocation not wired yet — location-aware detection is Android-only for now.
actual fun coarsePlaceCell(): String? = null
