package com.daliborpovolny.shiftwatcher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daliborpovolny.shiftwatcher.ui.theme.ShiftWatcherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dummyEscalationManipulator = object : EscalationContactManipulator {
        override fun add(name: String, number: String) {}
        override fun delete(contact: EscalationContact) {}
        override fun moveUp(index: Int) {}
        override fun moveDown(index: Int) {}
    }

    private val dummyInfoManipulator = object : InfoContactManipulator {
        override fun add(name: String, number: String) {}
        override fun delete(contact: InfoContact) {}
    }

    @Test
    fun testSettingsScreenDefaultPersonalTab() {
        composeTestRule.setContent {
            ShiftWatcherTheme {
                NewSettingsScreen(
                    escalationContacts = listOf(
                        EscalationContact(id = "1", number = "777888999", name = "John Lennon", priority = 0)
                    ),
                    infoContacts = listOf(
                        InfoContact(id = "2", number = "111222333", name = "George Harrison")
                    ),
                    escalationContactsManipulator = dummyEscalationManipulator,
                    infoContactsManipulator = dummyInfoManipulator,
                    userName = "Dalibor",
                    onUserNameChange = {},
                    batteryThreshold = 20,
                    onBatteryThresholdChange = {},
                    primaryCheckupInterval = 60,
                    onPrimaryCheckupIntervalChange = {},
                    escalationGracePeriod = 15,
                    onEscalationGracePeriodChange = {},
                    contactAnswerWaitTime = 3,
                    onContactAnswerWaitTimeChange = {},
                    onResetTimeIntervalsToDefault = {},
                    useTestConfig = false,
                    onUseTestConfigChange = {}
                )
            }
        }

        // Verify title
        composeTestRule.onNodeWithText("Nastavení").assertIsDisplayed()

        // Verify that default tab is Other/Ostatní
        composeTestRule.onNodeWithText("Osobní údaje").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vaše jméno").assertIsDisplayed()
        composeTestRule.onNodeWithText("Uložit jméno").assertIsDisplayed()
        composeTestRule.onNodeWithText("Minimální stav baterie pro spuštění směny").assertIsDisplayed()
        
        // Check that contacts are NOT shown in personal tab
        composeTestRule.onNodeWithText("John Lennon").assertDoesNotExist()
        composeTestRule.onNodeWithText("George Harrison").assertDoesNotExist()
    }

    @Test
    fun testSettingsScreenTabNavigationToEscalationAndInfo() {
        composeTestRule.setContent {
            ShiftWatcherTheme {
                NewSettingsScreen(
                    escalationContacts = listOf(
                        EscalationContact(id = "1", number = "777888999", name = "John Lennon", priority = 0)
                    ),
                    infoContacts = listOf(
                        InfoContact(id = "2", number = "111222333", name = "George Harrison")
                    ),
                    escalationContactsManipulator = dummyEscalationManipulator,
                    infoContactsManipulator = dummyInfoManipulator,
                    userName = "Dalibor",
                    onUserNameChange = {},
                    batteryThreshold = 20,
                    onBatteryThresholdChange = {},
                    primaryCheckupInterval = 60,
                    onPrimaryCheckupIntervalChange = {},
                    escalationGracePeriod = 15,
                    onEscalationGracePeriodChange = {},
                    contactAnswerWaitTime = 3,
                    onContactAnswerWaitTimeChange = {},
                    onResetTimeIntervalsToDefault = {},
                    useTestConfig = false,
                    onUseTestConfigChange = {}
                )
            }
        }

        // Click on "Eskalace" tab
        composeTestRule.onNodeWithText("Eskalace").performClick()

        // Verify we see "Přidat do Eskalace seznamu" and the Escalation Contact list
        composeTestRule.onNodeWithText("Přidat do Eskalace seznamu").assertIsDisplayed()
        composeTestRule.onNodeWithText("John Lennon").assertIsDisplayed()
        composeTestRule.onNodeWithText("777888999").assertIsDisplayed()
        // Info contact should NOT be here
        composeTestRule.onNodeWithText("George Harrison").assertDoesNotExist()

        // Click on "Info" tab
        composeTestRule.onNodeWithText("Info").performClick()

        // Verify we see "Přidat do Info seznamu" and the Info Contact list
        composeTestRule.onNodeWithText("Přidat do Info seznamu").assertIsDisplayed()
        composeTestRule.onNodeWithText("George Harrison").assertIsDisplayed()
        composeTestRule.onNodeWithText("111222333").assertIsDisplayed()
        // Escalation contact should NOT be here
        composeTestRule.onNodeWithText("John Lennon").assertDoesNotExist()
    }

    @Test
    fun testSettingsScreenTestConfigToggleAndWarning() {
        composeTestRule.setContent {
            ShiftWatcherTheme {
                NewSettingsScreen(
                    escalationContacts = emptyList(),
                    infoContacts = emptyList(),
                    escalationContactsManipulator = dummyEscalationManipulator,
                    infoContactsManipulator = dummyInfoManipulator,
                    userName = "Dalibor",
                    onUserNameChange = {},
                    batteryThreshold = 20,
                    onBatteryThresholdChange = {},
                    primaryCheckupInterval = 60,
                    onPrimaryCheckupIntervalChange = {},
                    escalationGracePeriod = 15,
                    onEscalationGracePeriodChange = {},
                    contactAnswerWaitTime = 3,
                    onContactAnswerWaitTimeChange = {},
                    onResetTimeIntervalsToDefault = {},
                    useTestConfig = false,
                    onUseTestConfigChange = {}
                )
            }
        }

        // Verify that the config card is displayed
        composeTestRule.onNodeWithText("Testovací konfigurace služby").assertIsDisplayed()
        
        // When toggle is false, it should show the regular config text
        composeTestRule.onNodeWithText("V běžném režimu jsou intervaly nastaveny na standardní produkční hodnoty: kontrola každou 1 hodinu, eskalace po 15 minutách nečinnosti.").assertIsDisplayed()
        
        // Now set toggleValue to true and recompose (simulate it)
        composeTestRule.setContent {
            ShiftWatcherTheme {
                NewSettingsScreen(
                    escalationContacts = emptyList(),
                    infoContacts = emptyList(),
                    escalationContactsManipulator = dummyEscalationManipulator,
                    infoContactsManipulator = dummyInfoManipulator,
                    userName = "Dalibor",
                    onUserNameChange = {},
                    batteryThreshold = 20,
                    onBatteryThresholdChange = {},
                    primaryCheckupInterval = 60,
                    onPrimaryCheckupIntervalChange = {},
                    escalationGracePeriod = 15,
                    onEscalationGracePeriodChange = {},
                    contactAnswerWaitTime = 3,
                    onContactAnswerWaitTimeChange = {},
                    onResetTimeIntervalsToDefault = {},
                    useTestConfig = true,
                    onUseTestConfigChange = {}
                )
            }
        }
        
        // It should display the warning text
        composeTestRule.onNodeWithText("POZOR: Tato konfigurace zkracuje kontrolní intervaly na sekundy (30s kontrola, 15s eskalace) a slouží VÝHRADNĚ k ověření funkčnosti aplikace (SMS, hovory, reakce na zprávy). V ŽÁDNÉM PŘÍPADĚ ji nepoužívejte pro reálnou ochranu životních funkcí!").assertIsDisplayed()
    }
}
