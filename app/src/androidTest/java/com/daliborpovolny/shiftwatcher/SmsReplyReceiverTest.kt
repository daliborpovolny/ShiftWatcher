package com.daliborpovolny.shiftwatcher

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class SmsReplyReceiverTest {

    private class TestContext(base: Context) : ContextWrapper(base) {
        var startedServiceIntent: Intent? = null

        override fun startForegroundService(service: Intent?): ComponentName? {
            startedServiceIntent = service
            // Do not actually start the service to keep test isolated and fast
            return ComponentName(this, WatcherService::class.java)
        }

        override fun startService(service: Intent?): ComponentName? {
            startedServiceIntent = service
            return ComponentName(this, WatcherService::class.java)
        }
    }

    @Test
    fun testOnReceiveWithZastavitEskalaci() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = TestContext(baseContext)
        val receiver = SmsReplyReceiver()

        // Generate PDU for "zastavit eskalaci" keyword
        val pdu = createSmsPdu("123456789", "Ahoj, prosim zastavit eskalaci.")
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pdu))
            putExtra("format", "3gpp")
        }

        receiver.onReceive(testContext, intent)

        val serviceIntent = testContext.startedServiceIntent
        assertNotNull("WatcherService should have been started", serviceIntent)
        assertEquals(WatcherService.ACTION_END_MESSAGE_RECEIVED, serviceIntent?.action)
        assertEquals("123456789", serviceIntent?.getStringExtra("sender"))
        assertTrue(serviceIntent?.getStringExtra("message")?.contains("zastavit eskalaci") == true)
    }

    @Test
    fun testOnReceiveWithStopEskalaci() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = TestContext(baseContext)
        val receiver = SmsReplyReceiver()

        // Generate PDU for "stop eskalaci" keyword
        val pdu = createSmsPdu("+420777888999", "STOP ESKALACI")
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pdu))
            putExtra("format", "3gpp")
        }

        receiver.onReceive(testContext, intent)

        val serviceIntent = testContext.startedServiceIntent
        assertNotNull(serviceIntent)
        assertEquals(WatcherService.ACTION_END_MESSAGE_RECEIVED, serviceIntent?.action)
        assertEquals("+420777888999", serviceIntent?.getStringExtra("sender"))
        assertTrue(serviceIntent?.getStringExtra("message")?.contains("stop eskalaci", ignoreCase = true) == true)
    }

    @Test
    fun testOnReceiveWithStopEscalation() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = TestContext(baseContext)
        val receiver = SmsReplyReceiver()

        // Generate PDU for "stop escalation" keyword
        val pdu = createSmsPdu("720123456", "Please stop escalation now!")
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pdu))
            putExtra("format", "3gpp")
        }

        receiver.onReceive(testContext, intent)

        val serviceIntent = testContext.startedServiceIntent
        assertNotNull(serviceIntent)
        assertEquals(WatcherService.ACTION_END_MESSAGE_RECEIVED, serviceIntent?.action)
        assertEquals("720123456", serviceIntent?.getStringExtra("sender"))
        assertTrue(serviceIntent?.getStringExtra("message")?.contains("stop escalation", ignoreCase = true) == true)
    }

    @Test
    fun testOnReceiveWithUnrelatedMessage() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = TestContext(baseContext)
        val receiver = SmsReplyReceiver()

        // Generate PDU for unrelated message
        val pdu = createSmsPdu("123456789", "Ahoj, jak se mas?")
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pdu))
            putExtra("format", "3gpp")
        }

        receiver.onReceive(testContext, intent)

        val serviceIntent = testContext.startedServiceIntent
        assertNull("WatcherService should NOT have been started for unrelated message", serviceIntent)
    }

    /**
     * Helper to construct a simple 3GPP SMS PDU byte array for testing.
     */
    private fun createSmsPdu(sender: String, body: String): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        
        // 1. Service Center Address Length: 0 (default / no SC address)
        baos.write(0x00)
        
        // 2. First byte of SMS-DELIVER (e.g. 0x04)
        baos.write(0x04)
        
        // 3. Originating Address Length (number of digits)
        val cleanSender = sender.replace("+", "")
        baos.write(cleanSender.length)
        
        // 4. Originating Address Type
        if (sender.startsWith("+")) {
            baos.write(0x91) // International
        } else {
            baos.write(0x81) // Unknown/National
        }
        
        // 5. Originating Address digits (semi-octets)
        for (i in 0 until cleanSender.length step 2) {
            val digit1 = cleanSender[i]
            val digit2 = if (i + 1 < cleanSender.length) cleanSender[i + 1] else 'f'
            val byteVal = (digit2.toString().toInt(16) shl 4) or digit1.toString().toInt(16)
            baos.write(byteVal)
        }
        
        // 6. Protocol Identifier: 0
        baos.write(0x00)
        
        // 7. Data Coding Scheme: 0x08 (UCS-2 / UTF-16 BE)
        baos.write(0x08)
        
        // 8. Service Center Time Stamp (7 bytes, dummy values)
        for (i in 0 until 7) {
            baos.write(0x00)
        }
        
        // 9. User Data Length (length of body bytes)
        val bodyBytes = body.toByteArray(Charsets.UTF_16BE)
        baos.write(bodyBytes.size)
        
        // 10. User Data
        baos.write(bodyBytes)
        
        return baos.toByteArray()
    }
}
