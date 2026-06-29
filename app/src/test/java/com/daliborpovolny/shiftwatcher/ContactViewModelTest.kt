package com.daliborpovolny.shiftwatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class FakeContactDao : ContactDao {
    val escalationContactsMap = mutableMapOf<String, EscalationContact>()
    val infoContactsMap = mutableMapOf<String, InfoContact>()
    val settingsMap = mutableMapOf<String, String>()

    private val escalationFlow = MutableStateFlow<List<EscalationContact>>(emptyList())
    private val infoFlow = MutableStateFlow<List<InfoContact>>(emptyList())
    private val settingsFlows = mutableMapOf<String, MutableStateFlow<String?>>()

    private fun updateEscalation() {
        escalationFlow.value = escalationContactsMap.values.sortedBy { it.priority }
    }

    private fun updateInfo() {
        infoFlow.value = infoContactsMap.values.sortedBy { it.name }
    }

    override fun getAllEscalationContacts(): Flow<List<EscalationContact>> = escalationFlow

    override fun getAllEscalationContactsSync(): List<EscalationContact> =
        escalationContactsMap.values.sortedBy { it.priority }

    override suspend fun insertEscalationContact(escalationContact: EscalationContact) {
        escalationContactsMap[escalationContact.id] = escalationContact
        updateEscalation()
    }

    override suspend fun deleteEscalationContact(escalationContact: EscalationContact) {
        escalationContactsMap.remove(escalationContact.id)
        updateEscalation()
    }

    override suspend fun updateEscalationContact(escalationContact: EscalationContact) {
        escalationContactsMap[escalationContact.id] = escalationContact
        updateEscalation()
    }

    override fun getAllInfoContacts(): Flow<List<InfoContact>> = infoFlow

    override fun getAllInfoContactsSync(): List<InfoContact> =
        infoContactsMap.values.sortedBy { it.name }

    override suspend fun insertInfoContact(infoContact: InfoContact) {
        infoContactsMap[infoContact.id] = infoContact
        updateInfo()
    }

    override suspend fun deleteInfoContact(infoContact: InfoContact) {
        infoContactsMap.remove(infoContact.id)
        updateInfo()
    }

    override suspend fun updateInfoContact(infoContact: InfoContact) {
        infoContactsMap[infoContact.id] = infoContact
        updateInfo()
    }

    override suspend fun getUserSetting(key: String): String? = settingsMap[key]

    override fun getUserSettingFlow(key: String): Flow<String?> {
        return settingsFlows.getOrPut(key) { MutableStateFlow(settingsMap[key]) }
    }

    override suspend fun insertUserSetting(setting: UserSetting) {
        settingsMap[setting.key] = setting.value
        settingsFlows[setting.key]?.value = setting.value
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ContactViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeDao = FakeContactDao()

    private fun TestScope.createViewModel(): ContactViewModel {
        val viewModel = ContactViewModel(fakeDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.escalationContacts.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.infoContacts.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.userName.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.batteryThreshold.collect {}
        }
        return viewModel
    }

    @Test
    fun testAddAndGetEscalationContacts() = runTest {
        val viewModel = createViewModel()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.escalationContacts.value.size)

        viewModel.addEscalationContact("John", "777888999")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.escalationContacts.value.size)
        val added = viewModel.escalationContacts.value[0]
        assertEquals("John", added.name)
        assertEquals("777888999", added.number)
        assertEquals(0, added.priority)
    }

    @Test
    fun testEscalationPriorityIncrements() = runTest {
        val viewModel = createViewModel()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addEscalationContact("John", "111")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addEscalationContact("Paul", "222")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val list = viewModel.escalationContacts.value
        assertEquals(2, list.size)
        assertEquals("John", list[0].name)
        assertEquals(0, list[0].priority)
        assertEquals("Paul", list[1].name)
        assertEquals(1, list[1].priority)
    }

    @Test
    fun testDeleteEscalationContact() = runTest {
        val viewModel = createViewModel()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addEscalationContact("John", "111")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val added = viewModel.escalationContacts.value[0]
        viewModel.deleteEscalationContact(added)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.escalationContacts.value.size)
    }

    @Test
    fun testMoveUpAndDownEscalationContacts() = runTest {
        val viewModel = createViewModel()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addEscalationContact("John", "111") // Priority 0
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addEscalationContact("Paul", "222") // Priority 1
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addEscalationContact("George", "333") // Priority 2
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        // Verify initial state
        var list = viewModel.escalationContacts.value
        assertEquals(3, list.size)
        assertEquals("John", list[0].name)
        assertEquals("Paul", list[1].name)
        assertEquals("George", list[2].name)

        // Move "Paul" down (swap with George)
        viewModel.moveDown(1)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        list = viewModel.escalationContacts.value
        assertEquals("John", list[0].name)
        assertEquals("George", list[1].name)
        assertEquals("Paul", list[2].name)

        // Move "Paul" up (swap with George)
        viewModel.moveUp(2)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        list = viewModel.escalationContacts.value
        assertEquals("John", list[0].name)
        assertEquals("Paul", list[1].name)
        assertEquals("George", list[2].name)
    }

    @Test
    fun testInfoContactsCrud() = runTest {
        val viewModel = createViewModel()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        println("STEP 1: infoContacts size = ${viewModel.infoContacts.value.size}")
        assertEquals(0, viewModel.infoContacts.value.size)

        viewModel.addInfoContact("Ringo", "444")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        println("STEP 2: infoContacts size = ${viewModel.infoContacts.value.size}")
        assertEquals(1, viewModel.infoContacts.value.size)
        val added = viewModel.infoContacts.value[0]
        assertEquals("Ringo", added.name)

        println("STEP 3 BEFORE DELETE: added = $added, map keys = ${fakeDao.infoContactsMap.keys}")
        viewModel.deleteInfoContact(added)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        println("STEP 3 AFTER DELETE: infoContacts size = ${viewModel.infoContacts.value.size}, map keys = ${fakeDao.infoContactsMap.keys}")
        assertEquals(0, viewModel.infoContacts.value.size)
    }

    @Test
    fun testUpdateUserName() = runTest {
        val viewModel = createViewModel()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.userName.value)

        viewModel.updateUserName("Dalibor")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Dalibor", viewModel.userName.value)
    }

    @Test
    fun testUpdateBatteryThreshold() = runTest {
        val viewModel = createViewModel()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(20, viewModel.batteryThreshold.value)

        viewModel.updateBatteryThreshold(35)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(35, viewModel.batteryThreshold.value)
    }
}
