package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.data.hardware.EscPosReceipt
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.data.hardware.jvm.EscPosPrinter
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.PrintResult
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.Proxy
import javax.print.Doc
import javax.print.DocPrintJob
import javax.print.PrintException
import javax.print.PrintService
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * The "one source" guarantee: what the real driver writes is exactly what the builder (and so the
 * preview) made. And it reports what the spooler did: an unreachable or refusing printer is a
 * [PrintResult.Failed], never a silent Ok.
 */
internal class EscPosPrinterTest {

    private val doc = ReceiptDoc(
        header = listOf("Panadería El Sol"),
        lines = listOf(ReceiptLine("Marraqueta", "0,842 kg", 2500, 2105)),
        totalMinor = 2105,
        paymentMethod = PaymentMethod.CASH,
        footer = listOf("Gracias"),
        saleId = Uuid.random(),
    )

    private fun service(onPrint: (Doc) -> Unit): PrintService {
        val job = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DocPrintJob::class.java)) { _, method, args ->
            if (method.name == "print") onPrint(args[0] as Doc)
            null
        } as DocPrintJob
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PrintService::class.java)) { _, method, _ ->
            if (method.name == "createPrintJob") job else null
        } as PrintService
    }

    @Test
    fun `writes exactly the bytes the receipt builder produces`() = runTest {
        val sink = ByteArrayOutputStream()
        val printer = EscPosPrinter(emptyMap(), Dispatchers.Unconfined, openOutput = { sink })

        val result = printer.print(doc)

        assertEquals(PrintResult.Ok, result)
        assertContentEquals(EscPosReceipt.build(doc, PaperWidth.Mm58).bytes, sink.toByteArray())
        assertEquals(DeviceStatus.CONNECTED, printer.status.value)
    }

    @Test
    fun `uses the configured paper width`() = runTest {
        val sink = ByteArrayOutputStream()

        EscPosPrinter(mapOf("paperWidthMm" to "80"), Dispatchers.Unconfined, openOutput = { sink }).print(doc)

        assertContentEquals(EscPosReceipt.build(doc, PaperWidth.Mm80).bytes, sink.toByteArray())
    }

    @Test
    fun `an IO failure is a Failed result and an ERROR status, not a throw`() = runTest {
        val broken = object : OutputStream() {
            override fun write(b: Int) = throw IOException("paper jam")
        }
        val printer = EscPosPrinter(emptyMap(), Dispatchers.Unconfined, openOutput = { broken })

        val result = printer.print(doc)

        assertIs<PrintResult.Failed>(result)
        assertEquals(DeviceStatus.ERROR, printer.status.value)
    }

    @Test
    fun `a spooler that accepts the job is Ok`() = runTest {
        var received = 0
        val printer = EscPosPrinter(emptyMap(), Dispatchers.Unconfined, serviceLookup = { service { received++ } })

        assertEquals(PrintResult.Ok, printer.print(doc))
        assertEquals(1, received)
        assertEquals(DeviceStatus.CONNECTED, printer.status.value)
    }

    @Test
    fun `a spooler that rejects the job is Failed, not a silent Ok`() = runTest {
        val printer = EscPosPrinter(emptyMap(), Dispatchers.Unconfined, serviceLookup = { service { throw PrintException("printer offline") } })

        val result = printer.print(doc)

        assertIs<PrintResult.Failed>(result)
        assertTrue("printer offline" in result.reason)
        assertEquals(DeviceStatus.ERROR, printer.status.value)
    }

    @Test
    fun `no print service installed is Failed`() = runTest {
        val printer = EscPosPrinter(emptyMap(), Dispatchers.Unconfined, serviceLookup = { null })

        assertIs<PrintResult.Failed>(printer.print(doc))
        assertEquals(DeviceStatus.ERROR, printer.status.value)
    }

    @Test
    fun `the library's missing-service exception is Failed, not a crash`() = runTest {
        val printer = EscPosPrinter(emptyMap(), Dispatchers.Unconfined, serviceLookup = { throw IllegalArgumentException("Default Print Service is not found") })

        assertIs<PrintResult.Failed>(printer.print(doc))
    }

    @Test
    fun `a drawer kick the spooler refuses is an ERROR status, not a silent success`() = runTest {
        val printer = EscPosPrinter(emptyMap(), Dispatchers.Unconfined, serviceLookup = { service { throw PrintException("printer offline") } })

        printer.kickDrawer()

        assertEquals(DeviceStatus.ERROR, printer.status.value)
    }
}
