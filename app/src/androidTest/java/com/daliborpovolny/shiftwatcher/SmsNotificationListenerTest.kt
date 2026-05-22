package com.daliborpovolny.shiftwatcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsNotificationListenerTest {

    private class TestContext(base: Context) : ContextWrapper(base) {
        var startedServiceIntent: Intent? = null

        override fun startForegroundService(service: Intent?): ComponentName? {
            startedServiceIntent = service
            return ComponentName(this, WatcherService::class.java)
        }

        override fun startService(service: Intent?): ComponentName? {
            startedServiceIntent = service
            return ComponentName(this, WatcherService::class.java)
        }
    }

    private fun createAndAttachListener(testContext: Context): SmsNotificationListener {
        val listener = SmsNotificationListener()
        val attachMethod = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachMethod.isAccessible = true
        attachMethod.invoke(listener, testContext)
        return listener
    }

    @Test
    fun testOnNotificationPostedWithZastavitEskalaci() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = TestContext(baseContext)
        
        val listener = createAndAttachListener(testContext)

        val extras = Bundle().apply {
            putCharSequence("android.title", "John Doe")
            putCharSequence("android.text", "zastavit eskalaci")
        }

        val notification = NotificationCompat.Builder(baseContext, "channel_id")
            .setContentTitle("John Doe")
            .setContentText("zastavit eskalaci")
            .addExtras(extras)
            .build()

        val sbn = createStatusBarNotification(
            packageName = "com.google.android.apps.messaging",
            notification = notification
        )

        listener.onNotificationPosted(sbn)

        val serviceIntent = testContext.startedServiceIntent
        assertNotNull("WatcherService should have been started", serviceIntent)
        assertEquals(WatcherService.ACTION_END_MESSAGE_RECEIVED, serviceIntent?.action)
        assertEquals("John Doe", serviceIntent?.getStringExtra("sender"))
        assertEquals("zastavit eskalaci", serviceIntent?.getStringExtra("message"))
    }

    @Test
    fun testOnNotificationPostedWithUnrelatedApp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = TestContext(baseContext)
        
        val listener = createAndAttachListener(testContext)

        val extras = Bundle().apply {
            putCharSequence("android.title", "John Doe")
            putCharSequence("android.text", "zastavit eskalaci")
        }

        val notification = NotificationCompat.Builder(baseContext, "channel_id")
            .setContentTitle("John Doe")
            .setContentText("zastavit eskalaci")
            .addExtras(extras)
            .build()

        // Unrelated app package
        val sbn = createStatusBarNotification(
            packageName = "com.example.unrelated.app",
            notification = notification
        )

        listener.onNotificationPosted(sbn)

        val serviceIntent = testContext.startedServiceIntent
        assertNull("WatcherService should NOT be started for unrelated packages", serviceIntent)
    }

    @Test
    fun testOnNotificationPostedWithUnrelatedMessage() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = TestContext(baseContext)
        
        val listener = createAndAttachListener(testContext)

        val extras = Bundle().apply {
            putCharSequence("android.title", "John Doe")
            putCharSequence("android.text", "Ahoj, jak se mas?")
        }

        val notification = NotificationCompat.Builder(baseContext, "channel_id")
            .setContentTitle("John Doe")
            .setContentText("Ahoj, jak se mas?")
            .addExtras(extras)
            .build()

        val sbn = createStatusBarNotification(
            packageName = "com.google.android.apps.messaging",
            notification = notification
        )

        listener.onNotificationPosted(sbn)

        val serviceIntent = testContext.startedServiceIntent
        assertNull("WatcherService should NOT be started for unrelated text", serviceIntent)
    }

    private fun createStatusBarNotification(
        packageName: String,
        notification: Notification
    ): StatusBarNotification {
        return StatusBarNotification(
            packageName,
            packageName,
            100,
            "tag",
            Process.myUid(),
            Process.myPid(),
            0,
            notification,
            Process.myUserHandle(),
            System.currentTimeMillis()
        )
    }
}
