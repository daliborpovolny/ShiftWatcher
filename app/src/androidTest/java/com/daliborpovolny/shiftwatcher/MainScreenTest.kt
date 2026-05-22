package com.daliborpovolny.shiftwatcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daliborpovolny.shiftwatcher.ui.theme.ShiftWatcherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testMainScreenInactiveState() {
        composeTestRule.setContent {
            ShiftWatcherTheme {
                MainScreen(
                    shiftState = ShiftState.INACTIVE,
                    remainingTime = "00:00"
                )
            }
        }

        // Verify that the "Začít směnu" button is displayed
        composeTestRule.onNodeWithText("Začít směnu").assertIsDisplayed()
    }

    @Test
    fun testMainScreenActiveState() {
        val testTime = "25:40"
        composeTestRule.setContent {
            ShiftWatcherTheme {
                MainScreen(
                    shiftState = ShiftState.ACTIVE,
                    remainingTime = testTime
                )
            }
        }

        // Verify that the timer and labels are displayed
        composeTestRule.onNodeWithText("Další kontrola za:").assertIsDisplayed()
        composeTestRule.onNodeWithText(testTime).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ukončit směnu").assertIsDisplayed()
    }

    @Test
    fun testMainScreenAlarmingState() {
        composeTestRule.setContent {
            ShiftWatcherTheme {
                MainScreen(
                    shiftState = ShiftState.ALARMING,
                    remainingTime = "00:00"
                )
            }
        }

        // Verify that the check-in confirmation button is displayed
        composeTestRule.onNodeWithText("Jsem v pořádku").assertIsDisplayed()
    }

    @Test
    fun testMainScreenEscalatingState() {
        // Prepare some mock escalation logs
        WatcherService.escalationLogs.clear()
        WatcherService.escalationLogs.add("Sms s upozorněním poslána")

        composeTestRule.setContent {
            ShiftWatcherTheme {
                MainScreen(
                    shiftState = ShiftState.ESCALATING,
                    remainingTime = "00:00"
                )
            }
        }

        // Verify escalation texts, emergency stop button and log section are shown
        composeTestRule.onNodeWithText("Probíhá Eskalace").assertIsDisplayed()
        composeTestRule.onNodeWithText("STOP").assertIsDisplayed()
        composeTestRule.onNodeWithText("Výpis událostí").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sms s upozorněním poslána").assertIsDisplayed()
    }

    @Test
    fun testMainScreenStoppedEscalationState() {
        // Prepare some mock escalation logs
        WatcherService.escalationLogs.clear()
        WatcherService.escalationLogs.add("Eskalace přerušena odkliknutím")

        composeTestRule.setContent {
            ShiftWatcherTheme {
                MainScreen(
                    shiftState = ShiftState.STOPPED_ESCALATION,
                    remainingTime = "00:00"
                )
            }
        }

        // Verify stopped escalation layout
        composeTestRule.onNodeWithText("Eskalace ukončena").assertIsDisplayed()
        composeTestRule.onNodeWithText("Výpis událostí").assertIsDisplayed()
        composeTestRule.onNodeWithText("Eskalace přerušena odkliknutím").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zavřít").assertIsDisplayed()
    }
}
