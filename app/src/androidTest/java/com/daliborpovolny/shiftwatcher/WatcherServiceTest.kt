package com.daliborpovolny.shiftwatcher

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatcherServiceTest {

    private lateinit var app: ShiftWatcherApp
    private lateinit var dao: ContactDao
    private lateinit var service: WatcherService

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Context>() as ShiftWatcherApp
        dao = app.database.contactDao()

        // Clean up database before test
        runBlocking {
            dao.getAllEscalationContactsSync().forEach {
                dao.deleteEscalationContact(it)
            }
        }

        // Initialize WatcherService using reflection to attach Context and Application
        service = WatcherService()

        val attachMethod = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachMethod.isAccessible = true
        attachMethod.invoke(service, app)

        val appField = Service::class.java.getDeclaredField("mApplication")
        appField.isAccessible = true
        appField.set(service, app)

        service.onCreate()
    }

    @After
    fun tearDown() {
        // Clean up database after test
        runBlocking {
            dao.getAllEscalationContactsSync().forEach {
                dao.deleteEscalationContact(it)
            }
        }
        service.onDestroy()
    }

    private fun invokeProcessIncomingCancellation(sender: String?, message: String?) {
        val method = WatcherService::class.java.getDeclaredMethod(
            "processIncomingCancellation",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(service, sender, message)
    }

    @Test
    fun testProcessIncomingCancellationVerifiedSenderCorrectKeyword() = runBlocking {
        // Setup contact in DB
        val contact = EscalationContact(id = "1", name = "Lennon", number = "777888999", priority = 0)
        dao.insertEscalationContact(contact)

        // Set initial state to ESCALATING
        WatcherService.currentState = ShiftState.ESCALATING
        WatcherService.escalationLogs.clear()

        // Invoke cancellation logic directly
        invokeProcessIncomingCancellation("777888999", "Ahoj, stop eskalaci")

        // Wait briefly for serviceScope coroutine to finish
        var attempts = 0
        while (WatcherService.currentState == ShiftState.ESCALATING && attempts < 20) {
            withContext(Dispatchers.IO) {
                Thread.sleep(500)
            }
            attempts++
        }

        assertEquals(ShiftState.STOPPED_ESCALATION, WatcherService.currentState)
        assertTrue(WatcherService.escalationLogs.any { it.contains("Eskalace přerušena díky přijaté zprávě") })
    }

    @Test
    fun testProcessIncomingCancellationUnverifiedSenderCorrectKeyword() = runBlocking {
        // Set initial state to ESCALATING
        WatcherService.currentState = ShiftState.ESCALATING
        WatcherService.escalationLogs.clear()

        // Sender 111222333 is not in database
        invokeProcessIncomingCancellation("111222333", "zastavit eskalaci")

        // Wait briefly
        withContext(Dispatchers.IO) {
            Thread.sleep(500)
        }

        // State must remain ESCALATING
        assertEquals(ShiftState.ESCALATING, WatcherService.currentState)
        assertTrue(WatcherService.escalationLogs.any { it.contains("Pokus o přerušení od neznámého čísla") })
    }

    @Test
    fun testProcessIncomingCancellationVerifiedSenderIncorrectKeyword() = runBlocking {
        // Setup contact in DB
        val contact = EscalationContact(id = "1", name = "Lennon", number = "777888999", priority = 0)
        dao.insertEscalationContact(contact)

        // Set initial state to ESCALATING
        WatcherService.currentState = ShiftState.ESCALATING
        WatcherService.escalationLogs.clear()

        // Message contains no approved phrase
        invokeProcessIncomingCancellation("777888999", "Ahoj, jak se mas?")

        // Wait briefly
        withContext(Dispatchers.IO) {
            Thread.sleep(500)
        }

        // State must remain ESCALATING, no stop should occur
        assertEquals(ShiftState.ESCALATING, WatcherService.currentState)
        assertFalse(WatcherService.escalationLogs.any { it.contains("Eskalace přerušena") })
    }
}
