package com.f776.ecopan.data.hardware.jvm

import co.touchlab.kermit.Logger
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.RawTextSource
import com.f776.ecopan.domain.hardware.Scale
import com.f776.ecopan.domain.hardware.SerialScaleSettings
import com.f776.ecopan.domain.hardware.WeightReading
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Real JVM scale driver (04 §2, §3) using jSerialComm (API verified via context7 against
 * `/fazecast/jserialcomm` -- see `docs/hardware/scale-protocol-notes.md`). Bytes are handed to a
 * configured [ScaleProtocol] (`hw.scale.serial.protocol`); parsed frames become [WeightReading]s.
 *
 * HONESTY NOTE: this driver has not been exercised against physical hardware in this development
 * environment (Phase-0 spike NO-GO -- no scale or substitute available). It is tested only against
 * a synthetic capture-log replay (`jvmTest`); `ManualScale` remains the primary supported mode.
 *
 * Failure modes never throw to the collector, per plan:
 * - port absent / `openPort()` fails -> [DeviceStatus.ERROR], retried with capped exponential backoff.
 * - unplugged mid-shift -> `LISTENING_EVENT_PORT_DISCONNECTED` (verified constant, see context7 query
 *   in the notes doc) *plus* a stale-frame watchdog (no valid frame for [STALE_TIMEOUT_MS]) -> DISCONNECTED.
 * - garbage frames (wrong baud/protocol) -> parser yields nothing -> watchdog -> ERROR.
 */
internal class SerialScale(
    private val config: Map<String, String>,
    private val ioDispatcher: CoroutineDispatcher,
    private val ports: SerialPortProvider = JSerialCommPortProvider,
) : Scale, RawTextSource {

    private val _status = MutableStateFlow(DeviceStatus.DISCONNECTED)
    override val status = _status.asStateFlow()

    private val _readings = MutableSharedFlow<WeightReading>(replay = 1, extraBufferCapacity = 64)
    override val readings: Flow<WeightReading> = _readings

    private val _lastRawText = MutableStateFlow<String?>(null)
    override val lastRawText: StateFlow<String?> = _lastRawText.asStateFlow()

    private val settings = SerialScaleSettings.fromConfig(config)

    // A regex that slipped past the Settings validation must not crash the registry mid-swap: the
    // driver reports ERROR instead and stays closed until the config is fixed.
    private val protocol: ScaleProtocol? = runCatching { ScaleProtocolFactory.create(settings) }
        .onFailure { Logger.w(it) { "SerialScale: invalid protocol configuration" } }
        .getOrNull()
    private val rawLogEnabled = config[RAWLOG_KEY]?.toBooleanStrictOrNull() ?: false

    // Guards scope/connection/closed. `ports.open` itself blocks (the OS can take seconds), so it is
    // deliberately called outside the lock; the lock is what makes the *outcome* of that open safe
    // against a concurrent close().
    private val lock = Any()
    private var scope: CoroutineScope? = null
    private var connection: SerialConnection? = null
    private var pollJob: Job? = null
    private var closed = false
    private var reconnecting = false
    private var generation = 0

    @Volatile private var lastFrameAt: Instant = Clock.System.now()

    private enum class OpenResult { OPENED, FAILED, CLOSED }

    override fun start() {
        synchronized(lock) {
            if (closed || scope != null) return // idempotent; a closed driver stays closed
            // Nothing to retry when the config can never work: say so at once instead of looping.
            if (protocol == null || settings.port.isBlank()) {
                _status.value = DeviceStatus.ERROR
                return
            }
            val newScope = CoroutineScope(SupervisorJob() + ioDispatcher)
            scope = newScope
            newScope.launch { connectWithBackoff() }
            armWatchdog(newScope)
        }
    }

    override fun close() {
        val toClose: SerialConnection?
        synchronized(lock) {
            closed = true
            scope?.cancel()
            scope = null
            pollJob = null
            toClose = connection
            connection = null
        }
        toClose?.let { c -> runCatching { c.close() }.onFailure { Logger.w(it) { "SerialScale: error closing port" } } }
        _status.value = DeviceStatus.DISCONNECTED
    }

    private suspend fun connectWithBackoff() {
        var backoffMs = INITIAL_BACKOFF_MS
        try {
            while (currentCoroutineContext().isActive) {
                val result = runCatching { openAndArm() }.getOrElse { e ->
                    Logger.w(e) { "SerialScale: exception while opening port" }
                    OpenResult.FAILED
                }
                when (result) {
                    OpenResult.OPENED, OpenResult.CLOSED -> return
                    OpenResult.FAILED -> {
                        if (synchronized(lock) { closed }) return
                        _status.value = DeviceStatus.ERROR
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    }
                }
            }
        } finally {
            synchronized(lock) { reconnecting = false }
        }
    }

    private fun openAndArm(): OpenResult {
        val activeProtocol = protocol ?: return OpenResult.FAILED
        val myGeneration = synchronized(lock) { ++generation }
        val opened = ports.open(
            portName = settings.port,
            params = SerialLineParams(settings.baud, settings.framing),
            listener = object : SerialPortListener {
                override fun onData(bytes: ByteArray) = onDataAvailable(activeProtocol, bytes)
                override fun onDisconnected() = handleUnplug(myGeneration)
            },
        ) ?: return OpenResult.FAILED

        // close() may have run while the OS was opening the port. Adopting the connection now would
        // leak it (and keep the port locked for the next driver instance), so release it instead.
        val activeScope = synchronized(lock) {
            if (closed || scope == null) null else scope.also { connection = opened }
        }
        if (activeScope == null) {
            runCatching { opened.close() }.onFailure { Logger.w(it) { "SerialScale: error closing late-opened port" } }
            return OpenResult.CLOSED
        }

        lastFrameAt = Clock.System.now()
        _status.value = DeviceStatus.CONNECTED

        activeProtocol.pollCommand?.let { pollCmd ->
            val interval = activeProtocol.pollIntervalMs ?: PollGenericProtocol.DEFAULT_POLL_INTERVAL_MS
            val job = activeScope.launch {
                while (isActive) {
                    runCatching { opened.write(pollCmd) }
                    delay(interval)
                }
            }
            synchronized(lock) { pollJob = job }
        }
        return OpenResult.OPENED
    }

    /** The OS reported the device gone: release the dead handle and go back to opening the port. */
    private fun handleUnplug(fromGeneration: Int) {
        val activeScope = synchronized(lock) {
            if (closed || fromGeneration != generation || reconnecting) return
            reconnecting = true
            scope
        } ?: return
        _status.value = DeviceStatus.DISCONNECTED
        // Off the listener thread: closing a jSerialComm port from inside its own event callback is
        // not safe, and opening again can block.
        activeScope.launch {
            val dead: SerialConnection?
            synchronized(lock) {
                pollJob?.cancel()
                pollJob = null
                dead = connection
                connection = null
            }
            dead?.let { runCatching { it.close() }.onFailure { e -> Logger.w(e) { "SerialScale: error closing unplugged port" } } }
            connectWithBackoff()
        }
    }

    private fun onDataAvailable(activeProtocol: ScaleProtocol, bytes: ByteArray) {
        if (rawLogEnabled) logRawBytes(bytes)
        // Verbatim text for the live test, kept even when the protocol cannot read it -- that is
        // exactly when a person needs to see what the scale actually sends.
        String(bytes, Charsets.US_ASCII).lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }
            ?.let { _lastRawText.value = it }

        // This runs on the library's event thread, which must survive whatever a user-typed
        // protocol does with hostile input.
        val parsed = try {
            activeProtocol.feed(bytes)
        } catch (t: Throwable) {
            Logger.w(t) { "SerialScale: protocol failed on incoming bytes; dropping them" }
            return
        }
        if (parsed.isNotEmpty()) {
            lastFrameAt = Clock.System.now()
            if (_status.value != DeviceStatus.CONNECTED) _status.value = DeviceStatus.CONNECTED
        }
        parsed.forEach { _readings.tryEmit(it) }
    }

    /** Stale-frame watchdog (04 §2): no valid parsed frame for [STALE_TIMEOUT_MS] -> DISCONNECTED. */
    private fun armWatchdog(watchdogScope: CoroutineScope) {
        watchdogScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val staleMs = (Clock.System.now() - lastFrameAt).inWholeMilliseconds
                if (staleMs > STALE_TIMEOUT_MS && _status.value == DeviceStatus.CONNECTED) {
                    _status.value = DeviceStatus.DISCONNECTED
                }
            }
        }
    }

    private fun logRawBytes(bytes: ByteArray) {
        runCatching {
            val hex = bytes.joinToString("") { "%02X".format(it) }
            File(RAW_LOG_FILENAME).appendText("${Clock.System.now()}\t$hex\n")
        }.onFailure { Logger.w(it) { "SerialScale: failed to write raw log" } }
    }

    private companion object {
        const val RAWLOG_KEY = "rawlog"

        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val WATCHDOG_INTERVAL_MS = 1_000L
        const val STALE_TIMEOUT_MS = 3_000L
        const val RAW_LOG_FILENAME = "scale-raw.log"
    }
}
