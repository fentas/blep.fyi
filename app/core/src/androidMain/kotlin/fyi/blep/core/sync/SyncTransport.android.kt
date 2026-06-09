package fyi.blep.core.sync

import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import fyi.blep.core.ble.BlepContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val PATH_STATE = "/blep/sync"
private const val PATH_MSG = "/blep/msg"
private const val CAPABILITY = "blep_companion"

/**
 * Wear Data Layer backing for [SyncTransport]:
 *  - **DataClient** replicates the converged [SyncState] blob at [PATH_STATE] (last-writer
 *    wins at the CRDT layer; the Data Layer just ships the bytes, deduping identical ones).
 *  - **MessageClient** ships one-shot [SyncMessage]s at [PATH_MSG] to every connected node.
 *  - **CapabilityClient** reports whether the peer (which advertises the `blep_companion`
 *    capability via res/values/wear.xml) is reachable and *nearby* — the modern,
 *    non-deprecated successor to the legacy peer-connected listener.
 *
 * Works for both directions of the pair (phone↔watch) — same app id + signing key.
 */
internal class DataLayerTransport(ctx: android.content.Context) : SyncTransport {
    private val data: DataClient = Wearable.getDataClient(ctx)
    private val messages: MessageClient = Wearable.getMessageClient(ctx)
    private val nodes = Wearable.getNodeClient(ctx)
    private val capabilities: CapabilityClient = Wearable.getCapabilityClient(ctx)

    override fun publishState(encoded: String) {
        val req = PutDataRequest.create(PATH_STATE)
        req.data = encoded.encodeToByteArray()
        req.setUrgent()
        runCatching { data.putDataItem(req) }
    }

    override val incomingState: Flow<String> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { buffer ->
            for (event in buffer) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == PATH_STATE) {
                    event.dataItem.data?.let { trySend(it.decodeToString()) }
                }
            }
            buffer.release()
        }
        data.addListener(listener)
        // Seed with whatever the peer already published.
        data.dataItems.addOnSuccessListener { items ->
            for (item in items) if (item.uri.path == PATH_STATE) item.data?.let { trySend(it.decodeToString()) }
            items.release()
        }
        awaitClose { data.removeListener(listener) }
    }

    override fun sendMessage(encoded: String) {
        val bytes = encoded.encodeToByteArray()
        nodes.connectedNodes.addOnSuccessListener { list ->
            for (n in list) runCatching { messages.sendMessage(n.id, PATH_MSG, bytes) }
        }
    }

    override val incomingMessages: Flow<String> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { msg ->
            if (msg.path == PATH_MSG) trySend(msg.data.decodeToString())
        }
        messages.addListener(listener)
        awaitClose { messages.removeListener(listener) }
    }

    override val peerNearby: Flow<Boolean> = callbackFlow {
        fun emit(info: CapabilityInfo) { trySend(info.nodes.any { it.isNearby }) }
        val listener = CapabilityClient.OnCapabilityChangedListener { emit(it) }
        capabilities.addListener(listener, CAPABILITY)
        capabilities.getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { emit(it) }
        awaitClose { capabilities.removeListener(listener, CAPABILITY) }
    }
}

actual fun createSyncTransport(): SyncTransport {
    val ctx = BlepContext.app ?: return NoopSyncTransport()
    return runCatching { DataLayerTransport(ctx) }.getOrElse { NoopSyncTransport() }
}
