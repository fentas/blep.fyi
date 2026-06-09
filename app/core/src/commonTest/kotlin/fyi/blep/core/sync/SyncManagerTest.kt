package fyi.blep.core.sync

import fyi.blep.core.platform.createKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeTransport : SyncTransport {
    val published = mutableListOf<String>()
    val sent = mutableListOf<String>()
    val stateIn = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val msgIn = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val nearby = MutableStateFlow(false)
    override fun publishState(encoded: String) { published += encoded }
    override val incomingState: Flow<String> = stateIn
    override fun sendMessage(encoded: String) { sent += encoded }
    override val incomingMessages: Flow<String> = msgIn
    override val peerNearby: Flow<Boolean> = nearby
}

private class FakeSource(
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

private class FakeSink : SyncSink {
    var favorites: Set<String>? = null
    var aliases: Map<String, String>? = null
    val settings = mutableMapOf<String, String>()
    val messages = mutableListOf<SyncMessage>()
    var nearby: Boolean? = null
    override fun applyFavorites(ids: Set<String>) { favorites = ids }
    override fun applyTethered(ids: Set<String>) {}
    override fun applyMuted(ids: Set<String>) {}
    override fun applyAliases(map: Map<String, String>) { aliases = map }
    override fun applySetting(name: String, value: String) { settings[name] = value }
    override fun onMessage(msg: SyncMessage) { messages += msg }
    override fun onPeerNearby(nearby: Boolean) { this.nearby = nearby }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {
    private fun settings() = SyncSettings(createKeyValueStore())

    @Test fun localChangePublishes() = runTest {
        val t = FakeTransport()
        val src = FakeSource(favorites = setOf("X"))
        val mgr = SyncManager(t, settings(), src, FakeSink(), backgroundScope, now = { 100 })
        mgr.start(); runCurrent()
        assertTrue(t.published.last().contains("F\tX\t100\t1"))
    }

    @Test fun incomingMergesIntoSink() = runTest {
        val t = FakeTransport()
        val sink = FakeSink()
        val mgr = SyncManager(t, settings(), FakeSource(favorites = setOf("X")), sink, backgroundScope, now = { 100 })
        mgr.start(); runCurrent()
        t.stateIn.emit(SyncState.EMPTY.withSet(Section.FAVORITES, setOf("Y"), 50).encode())
        runCurrent()
        assertEquals(setOf("X", "Y"), sink.favorites) // peer's Y merged with local X
    }

    @Test fun disabledCategoryIsNotSynced() = runTest {
        val s = settings().apply { setNames(false) }
        val t = FakeTransport()
        val sink = FakeSink()
        val src = FakeSource(favorites = setOf("X"), aliases = mapOf("X" to "Keys"))
        val mgr = SyncManager(t, s, src, sink, backgroundScope, now = { 100 })
        mgr.start(); runCurrent()
        assertTrue(t.published.last().contains("F\tX")) // favourites still sync
        assertTrue(t.published.none { it.contains("A\tX") }) // names do not
        // An incoming name must not be applied when name-sync is off.
        t.stateIn.emit(SyncState.EMPTY.withNames(mapOf("Z" to "Bag"), 50).encode())
        runCurrent()
        assertEquals(null, sink.aliases)
    }

    @Test fun masterOffDisablesEverything() = runTest {
        val s = settings().apply { setEnabled(false) }
        val t = FakeTransport()
        val mgr = SyncManager(t, s, FakeSource(favorites = setOf("X")), FakeSink(), backgroundScope, now = { 100 })
        mgr.start(); runCurrent()
        assertTrue(t.published.isEmpty())
    }

    @Test fun relaysAndReceivesMessages() = runTest {
        val t = FakeTransport()
        val sink = FakeSink()
        val mgr = SyncManager(t, settings(), FakeSource(), sink, backgroundScope, now = { 100 })
        mgr.start(); runCurrent()
        mgr.send(SyncMessage.TetherLeft("AA", "Keys"))
        assertEquals(SyncMessage.TetherLeft("AA", "Keys"), SyncMessage.decode(t.sent.last()))
        t.msgIn.emit(SyncMessage.TrackerAlert("AirTag").encode())
        runCurrent()
        assertEquals(SyncMessage.TrackerAlert("AirTag"), sink.messages.last())
    }

    @Test fun tracksPeerNearby() = runTest {
        val t = FakeTransport()
        val sink = FakeSink()
        val mgr = SyncManager(t, settings(), FakeSource(), sink, backgroundScope, now = { 100 })
        mgr.start(); runCurrent()
        t.nearby.value = true
        runCurrent()
        assertEquals(true, sink.nearby)
        assertEquals(true, mgr.peerNearby.value)
    }
}
