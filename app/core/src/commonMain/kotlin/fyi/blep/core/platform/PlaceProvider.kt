package fyi.blep.core.platform

/**
 * A coarse, on-device "place cell" for the current location — rounded to roughly a
 * couple of kilometres — or null if unavailable or location permission isn't
 * granted. Used **only** to count how many distinct places a tracker has been seen
 * with you (the strongest "following you" signal). Never stored precisely, never
 * leaves the device. Platforms without it return null.
 */
expect fun coarsePlaceCell(): String?
