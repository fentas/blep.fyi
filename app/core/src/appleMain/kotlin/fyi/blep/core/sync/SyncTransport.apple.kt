package fyi.blep.core.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.WatchConnectivity.WCSession
import platform.WatchConnectivity.WCSessionDelegateProtocol
import platform.WatchConnectivity.WCSessionActivationState
import platform.Foundation.NSError
import platform.darwin.NSObject

private const val KEY = "d"

/**
 * Apple backing for [SyncTransport] over **WatchConnectivity** — the WCSession analog of
 * the Wear Data Layer, shared by the iPhone and Apple Watch apps:
 *  - `updateApplicationContext` carries the converged [SyncState] (latest wins, like DataClient).
 *  - `sendMessage` (or `transferUserInfo` when not reachable) carries one-shot [SyncMessage]s.
 *  - `reachable` ≈ CapabilityClient presence.
 *
 * NOTE: written against the Kotlin/Native WatchConnectivity interop but verified only by the
 * macOS CI build — not compiled on the Android/JVM path. Apple-side wiring (activating sync
 * from the watchOS app) is still TODO; see docs/sync.md.
 */
internal class WatchConnectivityTransport : SyncTransport {
    private val stateIn = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val msgIn = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val nearby = MutableStateFlow(false)

    private val session: WCSession? = if (WCSession.isSupported()) WCSession.defaultSession else null

    private val delegate = object : NSObject(), WCSessionDelegateProtocol {
        override fun session(
            session: WCSession,
            activationDidCompleteWithState: WCSessionActivationState,
            error: NSError?,
        ) {
            nearby.value = session.reachable
        }

        override fun session(session: WCSession, didReceiveApplicationContext: Map<Any?, *>) {
            (didReceiveApplicationContext[KEY] as? String)?.let { stateIn.tryEmit(it) }
        }

        override fun session(session: WCSession, didReceiveMessage: Map<Any?, *>) {
            (didReceiveMessage[KEY] as? String)?.let { msgIn.tryEmit(it) }
        }

        override fun session(session: WCSession, didReceiveUserInfo: Map<Any?, *>) {
            (didReceiveUserInfo[KEY] as? String)?.let { msgIn.tryEmit(it) }
        }

        override fun sessionReachabilityDidChange(session: WCSession) {
            nearby.value = session.reachable
        }
    }

    init {
        session?.let {
            it.delegate = delegate
            it.activateSession()
        }
    }

    override fun publishState(encoded: String) {
        runCatching { session?.updateApplicationContext(mapOf<Any?, Any?>(KEY to encoded), null) }
    }

    override val incomingState: Flow<String> = stateIn.asSharedFlow()

    override fun sendMessage(encoded: String) {
        val s = session ?: return
        val payload = mapOf<Any?, Any?>(KEY to encoded)
        if (s.reachable) {
            s.sendMessage(payload, replyHandler = null, errorHandler = null)
        } else {
            runCatching { s.transferUserInfo(payload) }
        }
    }

    override val incomingMessages: Flow<String> = msgIn.asSharedFlow()

    override val peerNearby: Flow<Boolean> = nearby.asStateFlow()
}

actual fun createSyncTransport(): SyncTransport =
    runCatching { WatchConnectivityTransport() as SyncTransport }.getOrElse { NoopSyncTransport() }
