package fyi.blep.ui

import androidx.compose.runtime.Composable

/** Keeps the display awake while it's composed — you shouldn't have to keep
 *  tapping the screen mid-hunt. Released automatically when it leaves composition. */
@Composable
expect fun KeepScreenOn()
