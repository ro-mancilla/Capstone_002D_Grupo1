package com.f776.ecopan.data.hardware.jvm

import co.touchlab.kermit.Logger
import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.output.PrinterOutputStream
import com.f776.ecopan.data.hardware.EscPosReceipt
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.PrintResult
import com.f776.ecopan.domain.hardware.PrinterConfig
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptPrinter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.print.DocFlavor
import javax.print.PrintException
import javax.print.PrintService
import javax.print.SimpleDoc

/**
 * Real JVM printer driver (04 §2). Sends the bytes of [EscPosReceipt.build] -- the same builder
 * `MockPrinter` previews -- to an OS print service (`printerName`, else the default one); paper
 * width comes from `paperWidthMm`. The drawer kick still goes through escpos-coffee's `pulsePin`
 * (API verified via context7 against `/anastaciocintra/escpos-coffee`, see
 * `docs/hardware/scale-protocol-notes.md`). `POS never formats ESC/POS itself` (api KDoc).
 *
 * [openOutput] is where the receipt bytes go: when it yields nothing, the print service that
 * [serviceLookup] resolves; tests hand in a byte sink or a fake service.
 *
 * The job is handed to the spooler synchronously ([SpoolStream]) rather than through the library's
 * `PrinterOutputStream`, which submits on a background thread and only logs a failure -- so a dead
 * printer is a [PrintResult.Failed], never a silent Ok. Spooling can block for seconds (CUPS, a
 * network printer timing out), so all of it runs on [ioDispatcher], never the caller's thread.
 *
 * HONESTY NOTE: not exercised against a physical printer in this development environment -- no
 * printer/print-service was available to validate against (see `docs/hardware/supported-devices.md`).
 * That includes the receipt format itself (code page 858, ESC/POS command subset, layout).
 *
 * A missing print service or any [IOException] is a [PrintResult.Failed] + [DeviceStatus.ERROR],
 * never a thrown exception -- printing must never block a sale (04 §2, 05 §3 item 7).
 */
internal class EscPosPrinter(
    private val config: Map<String, String>,
    private val ioDispatcher: CoroutineDispatcher,
    private val openOutput: () -> OutputStream? = { null },
    /** Resolves the OS print service for a configured name (null = system default); null/throws when none exists. */
    private val serviceLookup: (String?) -> PrintService? = ::lookupPrintService,
) : ReceiptPrinter {

    private val _status = MutableStateFlow(DeviceStatus.DISCONNECTED)
    override val status = _status.asStateFlow()

    private val printerName: String? = config[PrinterConfig.KEY_NAME]?.takeIf { it.isNotBlank() }
    private val paper = PaperWidth.fromConfig(config)
    private val drawerPin: Int = config[DRAWER_PIN_KEY]?.toIntOrNull() ?: DEFAULT_DRAWER_PIN
    private val drawerOnMs: Int = config[DRAWER_ON_MS_KEY]?.toIntOrNull() ?: DEFAULT_DRAWER_ON_MS
    private val drawerOffMs: Int = config[DRAWER_OFF_MS_KEY]?.toIntOrNull() ?: DEFAULT_DRAWER_OFF_MS

    override suspend fun print(doc: ReceiptDoc): PrintResult = withContext(ioDispatcher) { printBlocking(doc) }

    private fun printBlocking(doc: ReceiptDoc): PrintResult {
        val output = try {
            openReceiptOutput()
        } catch (e: IOException) {
            _status.value = DeviceStatus.ERROR
            Logger.w(e) { "EscPosPrinter: failed to open print service '$printerName'" }
            return PrintResult.Failed(e.message ?: "failed to open print service")
        } ?: run {
            _status.value = DeviceStatus.ERROR
            return PrintResult.Failed("no print service found for '$printerName'")
        }

        return try {
            // The bytes come from the same builder MockPrinter previews, so the two cannot drift.
            output.write(EscPosReceipt.build(doc, paper).bytes)
            output.flush()
            // A spooled job is submitted here, so its failure surfaces in this try, not in a thread.
            output.close()
            _status.value = DeviceStatus.CONNECTED
            PrintResult.Ok
        } catch (e: IOException) {
            _status.value = DeviceStatus.ERROR
            Logger.w(e) { "EscPosPrinter: IO error while printing" }
            PrintResult.Failed(e.message ?: "IO error printing")
        } finally {
            runCatching { output.close() }
        }
    }

    override suspend fun kickDrawer() = withContext(ioDispatcher) { kickDrawerBlocking() }

    private fun kickDrawerBlocking() {
        val escpos = try {
            openEscPos()
        } catch (e: IOException) {
            _status.value = DeviceStatus.ERROR
            Logger.w(e) { "EscPosPrinter: failed to open print service for drawer kick" }
            return
        } ?: run {
            _status.value = DeviceStatus.ERROR
            return
        }
        try {
            escpos.pulsePin(pinFor(drawerPin), drawerOnMs, drawerOffMs)
            // The pulse is only spooled on close; closing here (not just in `finally`) lets a
            // refused job reach the catch instead of being dropped by runCatching.
            escpos.close()
        } catch (e: IOException) {
            _status.value = DeviceStatus.ERROR
            Logger.w(e) { "EscPosPrinter: IO error while kicking drawer" }
        } finally {
            runCatching { escpos.close() }
        }
    }

    /** `null` when no print service exists; the library throws [IllegalArgumentException] for that. */
    private fun resolveService(): PrintService? = try {
        serviceLookup(printerName)
    } catch (e: IllegalArgumentException) {
        Logger.w(e) { "EscPosPrinter: no print service '$printerName'" }
        null
    }

    private fun openSpool(): SpoolStream? = resolveService()?.let { SpoolStream(it) }

    private fun openReceiptOutput(): OutputStream? = openOutput() ?: openSpool()

    private fun openEscPos(): EscPos? = openSpool()?.let { EscPos(it) }

    private fun pinFor(pin: Int): EscPos.PinConnector =
        if (pin == 5) EscPos.PinConnector.Pin_5 else EscPos.PinConnector.Pin_2

    /** Buffers the bytes and hands them to [service] synchronously on [close], throwing if the spooler refuses. */
    private class SpoolStream(private val service: PrintService) : ByteArrayOutputStream() {
        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            try {
                service.createPrintJob().print(SimpleDoc(ByteArrayInputStream(toByteArray()), DocFlavor.INPUT_STREAM.AUTOSENSE, null), null)
            } catch (e: PrintException) {
                throw IOException(e.message ?: "print job failed", e)
            }
        }
    }

    private companion object {
        const val DRAWER_PIN_KEY = "drawerPin"
        const val DRAWER_ON_MS_KEY = "drawerOnMs"
        const val DRAWER_OFF_MS_KEY = "drawerOffMs"
        const val DEFAULT_DRAWER_PIN = 2
        const val DEFAULT_DRAWER_ON_MS = 50
        const val DEFAULT_DRAWER_OFF_MS = 100
    }
}

/** Throws [IllegalArgumentException] (library behaviour) when the named/default print service does not exist. */
internal fun lookupPrintService(name: String?): PrintService? =
    if (name.isNullOrBlank()) PrinterOutputStream.getDefaultPrintService() else PrinterOutputStream.getPrintServiceByName(name)
