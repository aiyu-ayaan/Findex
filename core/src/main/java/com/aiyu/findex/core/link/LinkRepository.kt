package com.aiyu.findex.core.link

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.aiyu.findex.core.db.LinkPeerDao
import com.aiyu.findex.core.db.LinkPeerEntity
import com.aiyu.findex.core.db.NetworkServerDao
import com.aiyu.findex.core.db.NetworkServerEntity
import com.aiyu.findex.core.network.CredentialCipher
import com.aiyu.findex.core.network.NetworkProtocol
import com.aiyu.findex.core.network.SmbRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** A device paired with this phone. [serverId] is its `network_servers` row, which the Explorer opens. */
data class LinkPeer(val id: String, val name: String, val platform: String, val serverId: Long?)

/** A Findex device seen on the network. [id] is the fingerprint prefix it advertises. */
data class NearbyDevice(val id: String, val name: String, val platform: String, val host: String, val port: Int)

sealed interface LinkServerState {
    data object Stopped : LinkServerState
    data object Starting : LinkServerState
    /** [address] is `ip:port` on Wi-Fi or hotspot, as a computer would type it. */
    data class Running(val address: String) : LinkServerState
    data object NoNetwork : LinkServerState
}

/** A computer asks to pair with this phone. Pair only if the computer shows the same [code]. */
class PairRequest(val name: String, val platform: String, val code: String, internal val answer: CompletableDeferred<Boolean>)

sealed interface LinkEvent {
    data class Received(val from: String, val name: String, val path: String) : LinkEvent
    data class Text(val from: String, val text: String) : LinkEvent
}

/** The pairing failed for a reason the user can act on. */
class LinkPairException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { UNREACHABLE, DECLINED, IDENTITY_MISMATCH, CANCELLED }
}

/**
 * Findex Link on the phone: paired devices, discovery (NSD, `_findex._tcp`), pairing as either side,
 * and the server's lifecycle ([LinkServerService]). Paired computers are also `network_servers` rows
 * with protocol FINDEX, so they browse and copy like any network share.
 */
@Singleton
class LinkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    val identity: LinkIdentity,
    private val peerDao: LinkPeerDao,
    private val serverDao: NetworkServerDao,
    private val cipher: CredentialCipher,
    private val smb: SmbRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val peers: Flow<List<LinkPeer>> = peerDao.observeAll().map { list ->
        list.map { LinkPeer(it.id, it.name, it.platform, serverDao.byLinkFingerprint(it.id)?.id) }
    }

    private val _server = MutableStateFlow<LinkServerState>(LinkServerState.Stopped)
    val server: StateFlow<LinkServerState> = _server.asStateFlow()

    private val _request = MutableStateFlow<PairRequest?>(null)
    /** The pairing request waiting for the user, shown as a dialog over the whole app. */
    val request: StateFlow<PairRequest?> = _request.asStateFlow()

    private val _events = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<LinkEvent> = _events.asSharedFlow()

    /** The name computers show for this phone. */
    val deviceName: String
        get() = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

    // ---------------- server ----------------

    fun startServer() {
        _server.value = LinkServerState.Starting
        ContextCompat.startForegroundService(context, Intent(context, LinkServerService::class.java))
    }

    fun stopServer() {
        context.startService(Intent(context, LinkServerService::class.java).setAction(LinkServerService.ACTION_STOP))
    }

    internal fun publish(state: LinkServerState) { _server.value = state }

    internal fun emit(event: LinkEvent) { _events.tryEmit(event) }

    /** Token to peer, decrypted once; rebuilt whenever pairings change. */
    @Volatile private var tokens: Map<String, LinkPeerEntity>? = null

    internal suspend fun peerForToken(token: String): LinkPeerEntity? {
        val map = tokens ?: peerDao.all().mapNotNull { p -> cipher.decrypt(p.tokenInCipher)?.let { it to p } }.toMap().also { tokens = it }
        return map.entries.firstOrNull { LinkCrypto.same(it.key, token) }?.value
    }

    private val approvals = Mutex()

    /** Called on a server thread; blocks until the user answers or a minute passes (then: declined). */
    internal fun approve(name: String, platform: String, code: String): Boolean = runBlocking {
        approvals.withLock {
            val answer = CompletableDeferred<Boolean>()
            _request.value = PairRequest(name, platform, code, answer)
            try {
                withTimeoutOrNull(APPROVAL_MS) { answer.await() } ?: false
            } finally {
                _request.value = null
            }
        }
    }

    fun answer(request: PairRequest, accept: Boolean) { request.answer.complete(accept) }

    // ---------------- pairing (this phone is the client) ----------------

    /**
     * Pairs with the Findex server at [host]:[port]. [confirm] gets the computer's name and the 6-digit
     * code and returns true when the user saw the same code on the computer. Returns the computer's name.
     */
    suspend fun pair(host: String, port: Int, confirm: suspend (name: String, code: String) -> Boolean): String = withContext(Dispatchers.IO) {
        val nonceC = LinkCrypto.random32()
        val open = LinkClient(host, port, null, null)
        val start = try {
            open.pairStart(
                JSONObject().put("id", identity.id).put("name", deviceName).put("platform", PLATFORM)
                    .put("port", LinkServerService.PORTS.first).put("commit", LinkCrypto.hex(LinkCrypto.sha256(nonceC)))
            )
        } catch (e: Exception) {
            throw LinkPairException(LinkPairException.Reason.UNREACHABLE, e)
        }
        val fpS = open.seen ?: throw LinkPairException(LinkPairException.Reason.IDENTITY_MISMATCH)
        if (start.optString("id") != LinkCrypto.hex(fpS)) throw LinkPairException(LinkPairException.Reason.IDENTITY_MISMATCH)
        val nonceS = LinkCrypto.unhex(start.optString("nonce")) ?: throw LinkPairException(LinkPairException.Reason.IDENTITY_MISMATCH)
        val name = start.optString("name").ifBlank { host }
        val platform = start.optString("platform")
        val code = LinkCrypto.code(fpS, identity.fingerprint, nonceC, nonceS)
        if (!confirm(name, code)) throw LinkPairException(LinkPairException.Reason.CANCELLED)
        val tokenIn = LinkCrypto.token()
        val finish = try {
            LinkClient(host, port, fpS, null).pairFinish(
                JSONObject().put("session", start.optString("session")).put("nonce", LinkCrypto.hex(nonceC)).put("token", tokenIn)
            )
        } catch (e: LinkHttpException) {
            throw LinkPairException(LinkPairException.Reason.DECLINED, e)
        } catch (e: Exception) {
            throw LinkPairException(LinkPairException.Reason.UNREACHABLE, e)
        }
        val tokenOut = finish.optString("token").takeIf { it.length >= 32 } ?: throw LinkPairException(LinkPairException.Reason.DECLINED)
        savePeer(LinkCrypto.hex(fpS), name, platform, host, port, tokenOut, tokenIn)
        name
    }

    /** Stores a pairing, as client or server: the peer row and its browsable network entry. */
    internal suspend fun savePeer(id: String, name: String, platform: String, host: String, port: Int, tokenOut: String, tokenIn: String) {
        peerDao.upsert(LinkPeerEntity(id = id, name = name, platform = platform, tokenInCipher = cipher.encrypt(tokenIn)))
        tokens = null
        val old = serverDao.byLinkFingerprint(id)
        serverDao.upsert(
            NetworkServerEntity(
                id = old?.id ?: 0,
                name = name,
                host = host,
                share = "",
                username = id,
                domain = "",
                passwordCipher = cipher.encrypt(tokenOut),
                createdAt = old?.createdAt ?: System.currentTimeMillis(),
                protocol = NetworkProtocol.FINDEX.name,
                port = port,
                rememberPassword = true
            )
        )
        old?.let { smb.dropSession(it.id) }
    }

    /** Unpairs on both sides when the other device is reachable; forgets it here in any case. */
    suspend fun unpair(peerId: String) = withContext(Dispatchers.IO) {
        serverDao.byLinkFingerprint(peerId)?.let { row ->
            runCatching {
                val token = row.passwordCipher?.let(cipher::decrypt)
                LinkClient(row.host, row.port, LinkCrypto.unhex(peerId), token).unpair()
            }
        }
        forget(peerId)
    }

    /** Removes a pairing locally (also when the other side unpaired us). */
    internal suspend fun forget(peerId: String) {
        peerDao.delete(peerId)
        tokens = null
        serverDao.byLinkFingerprint(peerId)?.let { smb.delete(it.id) }
    }

    /** Sends text to a paired computer: it lands on that computer's clipboard. */
    suspend fun sendText(serverId: Long, text: String) = withContext(Dispatchers.IO) {
        val row = serverDao.byId(serverId) ?: return@withContext
        LinkClient(row.host, row.port, LinkCrypto.unhex(row.username), row.passwordCipher?.let(cipher::decrypt)).sendText(text)
    }

    // ---------------- discovery ----------------

    private val nsd: NsdManager? by lazy { context.getSystemService(NsdManager::class.java) }
    private val _nearby = MutableStateFlow<Map<String, NearbyDevice>>(emptyMap())
    val nearby: Flow<List<NearbyDevice>> = _nearby.map { it.values.sortedBy { d -> d.name.lowercase() } }
    private var discoveryUsers = 0
    private var discovery: NsdManager.DiscoveryListener? = null
    private val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var registration: NsdManager.RegistrationListener? = null

    init {
        // NSD resolves one service at a time on older Android versions.
        scope.launch { for (info in resolveQueue) resolve(info)?.let(::onResolved) }
    }

    /** Browses while some screen wants it (counted). */
    @Synchronized
    fun startDiscovery() {
        if (discoveryUsers++ > 0) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { resolveQueue.trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) { _nearby.update { it - info.serviceName } }
        }
        discovery = listener
        runCatching { nsd?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    @Synchronized
    fun stopDiscovery() {
        if (discoveryUsers == 0 || --discoveryUsers > 0) return
        discovery?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        discovery = null
        _nearby.value = emptyMap()
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? = withTimeoutOrNull(RESOLVE_MS) {
        suspendCancellableCoroutine { cont ->
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { if (cont.isActive) cont.resume(null) }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) { if (cont.isActive) cont.resume(serviceInfo) }
            }
            runCatching { nsd?.resolveService(info, listener) }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    @Suppress("DEPRECATION")
    private fun onResolved(info: NsdServiceInfo) {
        val attr = { key: String -> info.attributes[key]?.toString(Charsets.UTF_8).orEmpty() }
        val id = attr("id")
        if (id.isEmpty() || identity.id.startsWith(id)) return
        val host = if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress ?: info.hostAddresses.firstOrNull()?.hostAddress
        } else {
            info.host?.hostAddress
        } ?: return
        val device = NearbyDevice(id, attr("name").ifBlank { info.serviceName }, attr("platform"), host, info.port)
        _nearby.update { it + (info.serviceName to device) }
        // A paired computer that moved to another address: follow it.
        scope.launch {
            val row = peerDao.all().firstOrNull { it.id.startsWith(id) }?.let { serverDao.byLinkFingerprint(it.id) } ?: return@launch
            if (row.host != host || row.port != info.port) {
                serverDao.upsert(row.copy(host = host, port = info.port))
                smb.dropSession(row.id)
            }
        }
    }

    internal fun advertise(port: Int) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = "findex-${identity.id.take(ID_PREFIX)}"
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("id", identity.id.take(ID_PREFIX))
            setAttribute("name", deviceName)
            setAttribute("platform", PLATFORM)
            setAttribute("v", "1")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    internal fun stopAdvertising() {
        registration?.let { runCatching { nsd?.unregisterService(it) } }
        registration = null
    }

    companion object {
        const val PLATFORM = "android"
        private const val SERVICE_TYPE = "_findex._tcp"
        private const val ID_PREFIX = 16
        private const val APPROVAL_MS = 60_000L
        private const val RESOLVE_MS = 5_000L
    }
}
