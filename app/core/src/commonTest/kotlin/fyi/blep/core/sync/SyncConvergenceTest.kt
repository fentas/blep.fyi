package fyi.blep.core.sync

import fyi.blep.core.platform.createKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end convergence: two real [SyncManager]s (a "phone" and a "watch") wired together
 * by a crossed in-memory link, so what one publishes the other receives. This is the
 * hardware-free stand-in for two paired emulators — it exercises the full publish → merge →
 * apply → re-publish path on both sides and proves the CRDT converges without a flake-prone
 * Data Layer bridge. (See scripts/sync-emu.sh for why the emulator rig only smoke-tests the
 * companion *link*, not DataItem propagation.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncConvergenceTest {

    /** One end of a crossed pair: it publishes into [out] and reads the peer's [inc]. */
    private class Side(
        private val out: MutableSharedFlow<String>,
        override val incomingState: Flow<String>,
        private val outMsg: MutableSharedFlow<String>,
        override val incomingMessages: Flow<String>,
        override val peerNearby: MutableStateFlow<Boolean>,
    ) : SyncTransport {
        var publishes = 0; private set
        override fun publishState(encoded: String) { publishes++; out.tryEmit(encoded) }
        override fun sendMessage(encoded: String) { outMsg.tryEmit(encoded) }
    }

    /** A crossed link: phone.out == watch.in and vice-versa, for state and messages.
     *  State flows use replay=1 to mirror the real DataClient — a newly-attached listener
     *  is handed the current replica — so the initial publish during start() isn't lost to
     *  a peer whose collector subscribes a beat later. Messages stay fire-and-forget. */
    private class Link {
        private val ab = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 256)
        private val ba = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 256)
        private val abM = MutableSharedFlow<String>(extraBufferCapacity = 256)
        private val baM = MutableSharedFlow<String>(extraBufferCapacity = 256)
        val phone = Side(ab, ba, abM, baM, MutableStateFlow(true))
        val watch = Side(ba, ab, baM, abM, MutableStateFlow(true))
    }

    private class Src(
        var favorites: Set<String> = emptySet(),
        var tethered: Set<String> = emptySet(),
        var muted: Set<String> = emptySet(),
        var aliases: Map<String, String> = emptyMap(),
        var settings: Map<String, String> = emptyMap(),
    ) : SyncSource {
        override fun favorites() = favorites
        override fun tethered() = tethered
        override fun muted() = muted
        override fun aliases() = aliases
        override fun settings() = settings
    }

    /** A sink that mirrors applied state straight back into a [Src], like the real stores do,
     *  so a later [SyncManager.localChanged] re-publishes the merged truth (not stale local). */
    private class Sink(private val mirror: Src) : SyncSink {
        val settings = mutableMapOf<String, String>()
        val messages = mutableListOf<SyncMessage>()
        override fun applyFavorites(ids: Set<String>) { mirror.favorites = ids }
        override fun applyTethered(ids: Set<String>) { mirror.tethered = ids }
        override fun applyMuted(ids: Set<String>) { mirror.muted = ids }
        override fun applyAliases(map: Map<String, String>) { mirror.aliases = map }
        override fun applySetting(name: String, value: String) { settings[name] = value; mirror.settings = settings.toMap() }
        override fun onMessage(msg: SyncMessage) { messages += msg }
    }

    private fun settings() = SyncSettings(createKeyValueStore())

    @Test fun twoDevicesConvergeOnTheUnionOfFavourites() = runTest(UnconfinedTestDispatcher()) {
        val link = Link()
        val phoneSrc = Src(favorites = setOf("KEYS"))
        val watchSrc = Src(favorites = setOf("BAG"))
        val phoneSink = Sink(phoneSrc)
        val watchSink = Sink(watchSrc)
        val phone = SyncManager(link.phone, settings(), phoneSrc, phoneSink, backgroundScope, now = { 100 })
        val watch = SyncManager(link.watch, settings(), watchSrc, watchSink, backgroundScope, now = { 200 })

        phone.start(); watch.start(); advanceUntilIdle()

        // Each device learns the other's favourite — both stores hold the union.
        assertEquals(setOf("KEYS", "BAG"), phoneSrc.favorites)
        assertEquals(setOf("KEYS", "BAG"), watchSrc.favorites)
    }

    @Test fun renameOnWatchReachesPhone() = runTest(UnconfinedTestDispatcher()) {
        val link = Link()
        val phoneSrc = Src(favorites = setOf("AA"))
        val watchSrc = Src(aliases = mapOf("AA" to "Backpack"))
        val phoneSink = Sink(phoneSrc)
        val phone = SyncManager(link.phone, settings(), phoneSrc, phoneSink, backgroundScope, now = { 100 })
        val watch = SyncManager(link.watch, settings(), watchSrc, Sink(watchSrc), backgroundScope, now = { 200 })

        phone.start(); watch.start(); advanceUntilIdle()

        assertEquals(mapOf("AA" to "Backpack"), phoneSrc.aliases)
    }

    @Test fun settingsAndMutesPropagateBothWays() = runTest(UnconfinedTestDispatcher()) {
        val link = Link()
        val phoneSrc = Src(settings = mapOf("sensitivity" to "high"))
        val watchSrc = Src(muted = setOf("MINE"))
        val phoneSink = Sink(phoneSrc)
        val watchSink = Sink(watchSrc)
        val phone = SyncManager(link.phone, settings(), phoneSrc, phoneSink, backgroundScope, now = { 100 })
        val watch = SyncManager(link.watch, settings(), watchSrc, watchSink, backgroundScope, now = { 200 })

        phone.start(); watch.start(); advanceUntilIdle()

        assertEquals("high", watchSink.settings["sensitivity"]) // setting phone→watch
        assertEquals(setOf("MINE"), phoneSrc.muted)             // mute watch→phone
    }

    @Test fun convergenceTerminates() = runTest(UnconfinedTestDispatcher()) {
        // Two non-empty starting states force a real merge+re-publish on both sides; the
        // loop must still settle (idempotent merge → no-change → no re-publish).
        val link = Link()
        val phone = SyncManager(link.phone, settings(), Src(favorites = setOf("A")), Sink(Src()), backgroundScope, now = { 100 })
        val watch = SyncManager(link.watch, settings(), Src(favorites = setOf("B")), Sink(Src()), backgroundScope, now = { 200 })

        phone.start(); watch.start(); advanceUntilIdle()

        // Initial publish + at most one re-publish of the merged union, then quiescent — a
        // runaway loop would blow far past this. Proves no infinite republish ping-pong.
        assertTrue(link.phone.publishes <= 3, "phone published ${link.phone.publishes}×")
        assertTrue(link.watch.publishes <= 3, "watch published ${link.watch.publishes}×")
    }

    @Test fun messagesRelayBothDirections() = runTest(UnconfinedTestDispatcher()) {
        val link = Link()
        val phoneSink = Sink(Src())
        val watchSink = Sink(Src())
        val phone = SyncManager(link.phone, settings(), Src(), phoneSink, backgroundScope, now = { 100 })
        val watch = SyncManager(link.watch, settings(), Src(), watchSink, backgroundScope, now = { 200 })
        phone.start(); watch.start(); advanceUntilIdle()

        phone.send(SyncMessage.TetherLeft("AA", "Keys"))
        watch.send(SyncMessage.TrackerAlert("AirTag"))
        advanceUntilIdle()

        assertEquals(SyncMessage.TetherLeft("AA", "Keys"), watchSink.messages.last())
        assertEquals(SyncMessage.TrackerAlert("AirTag"), phoneSink.messages.last())
    }
}
