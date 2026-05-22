package com.daliborpovolny.shiftwatcher

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ContactDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ContactDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.contactDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun testEscalationContactsCrud() = runBlocking {
        val contact = EscalationContact(name = "John", number = "777888999", priority = 0)
        dao.insertEscalationContact(contact)

        var list = dao.getAllEscalationContacts().first()
        assertEquals(1, list.size)
        assertEquals("John", list[0].name)

        val updated = contact.copy(name = "John Lennon")
        dao.updateEscalationContact(updated)

        list = dao.getAllEscalationContacts().first()
        assertEquals("John Lennon", list[0].name)

        dao.deleteEscalationContact(updated)
        list = dao.getAllEscalationContacts().first()
        assertEquals(0, list.size)
    }

    @Test
    @Throws(Exception::class)
    fun testEscalationContactsSorting() = runBlocking {
        val c1 = EscalationContact(name = "George", number = "1", priority = 2)
        val c2 = EscalationContact(name = "John", number = "2", priority = 0)
        val c3 = EscalationContact(name = "Paul", number = "3", priority = 1)

        dao.insertEscalationContact(c1)
        dao.insertEscalationContact(c2)
        dao.insertEscalationContact(c3)

        // Sync check
        val listSync = dao.getAllEscalationContactsSync()
        assertEquals(3, listSync.size)
        assertEquals("John", listSync[0].name)
        assertEquals("Paul", listSync[1].name)
        assertEquals("George", listSync[2].name)

        // Flow check
        val listFlow = dao.getAllEscalationContacts().first()
        assertEquals(3, listFlow.size)
        assertEquals("John", listFlow[0].name)
        assertEquals("Paul", listFlow[1].name)
        assertEquals("George", listFlow[2].name)
    }

    @Test
    @Throws(Exception::class)
    fun testInfoContactsCrud() = runBlocking {
        val contact = InfoContact(name = "Ringo", number = "111")
        dao.insertInfoContact(contact)

        var list = dao.getAllInfoContacts().first()
        assertEquals(1, list.size)
        assertEquals("Ringo", list[0].name)

        val listSync = dao.getAllInfoContactsSync()
        assertEquals(1, listSync.size)
        assertEquals("Ringo", listSync[0].name)

        dao.deleteInfoContact(contact)
        list = dao.getAllInfoContacts().first()
        assertEquals(0, list.size)
    }

    @Test
    @Throws(Exception::class)
    fun testUserSettingsCrud() = runBlocking {
        assertNull(dao.getUserSetting("user_name"))

        val setting = UserSetting("user_name", "Dalibor")
        dao.insertUserSetting(setting)

        assertEquals("Dalibor", dao.getUserSetting("user_name"))

        val flowVal = dao.getUserSettingFlow("user_name").first()
        assertEquals("Dalibor", flowVal)
    }
}
