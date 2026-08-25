package com.smartworkalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmActivityTerminalLifecycleTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<AlarmActivity>
    private lateinit var originalCommandDispatcher: (Context, Intent) -> Unit

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        if (::originalCommandDispatcher.isInitialized) {
            alarmTerminalCommandDispatcher = originalCommandDispatcher
        }
        alarmTerminalResultStore.clear()
    }

    @Test
    fun recreatedActivityFinishesOnMatchingSuccessDespiteDuplicateRejection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionKey = RingingSessionKey.create(
            isShiftAlarm = false,
            alarmId = 7L,
            attemptToken = "lifecycle-attempt",
        )
        var dispatchedCommand: Intent? = null
        originalCommandDispatcher = alarmTerminalCommandDispatcher
        alarmTerminalCommandDispatcher = { _, command ->
            dispatchedCommand = Intent(command)
        }
        alarmTerminalResultStore.clear()
        scenario = ActivityScenario.launch(ringingIntent(context, sessionKey))

        composeRule.onNodeWithContentDescription("알람 해제").performClick()

        val terminalCommand = requireNotNull(dispatchedCommand)
        val matchingRequestId = requireNotNull(terminalCommand.terminalRequestId())
        assertEquals(AlarmRingingService.ACTION_DISMISS, terminalCommand.action)
        assertEquals(sessionKey, terminalCommand.ringingSessionKey())

        scenario.recreate()
        composeRule.onNodeWithContentDescription("알람 해제").assertIsNotEnabled()

        val duplicateRequestId = UUID.randomUUID().toString()
        val duplicateRejection = AlarmTerminalServiceResult(
            requestId = duplicateRequestId,
            sessionKey = sessionKey,
            result = TerminalActionResult.REJECTED,
            message = "duplicate rejection",
        )
        publishResultAndBroadcast(context, duplicateRejection)
        composeRule.waitForIdle()

        assertEquals(Lifecycle.State.RESUMED, scenario.state)
        composeRule.onNodeWithContentDescription("알람 해제").assertIsNotEnabled()

        publishResultAndBroadcast(
            context,
            AlarmTerminalServiceResult(
                requestId = matchingRequestId,
                sessionKey = sessionKey,
                result = TerminalActionResult.SUCCESS,
                message = null,
            ),
        )
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            scenario.state == Lifecycle.State.DESTROYED
        }

        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        assertEquals(duplicateRejection, alarmTerminalResultStore.take(duplicateRequestId))
    }

    private fun ringingIntent(
        context: Context,
        sessionKey: RingingSessionKey,
    ): Intent = Intent(context, AlarmActivity::class.java).apply {
        putRingingSessionKey(sessionKey)
        putExtra(AlarmSchedulerImpl.EXTRA_ALARM_ID, sessionKey.alarmId)
        putExtra(AlarmRingingService.EXTRA_ALARM_NAME, "Lifecycle alarm")
        putExtra(AlarmRingingService.EXTRA_IS_SHIFT_ALARM, sessionKey.isShiftAlarm)
        putExtra(AlarmRingingService.EXTRA_SNOOZE_ENABLED, true)
        putExtra(AlarmRingingService.EXTRA_SNOOZE_MINUTES, 5)
    }

    private fun publishResultAndBroadcast(
        context: Context,
        result: AlarmTerminalServiceResult,
    ) {
        alarmTerminalResultStore.publish(result)
        val delivered = CountDownLatch(1)
        context.sendOrderedBroadcast(
            Intent(AlarmRingingService.ACTION_TERMINAL_RESULT)
                .setPackage(context.packageName)
                .apply {
                    putTerminalRequestId(result.requestId)
                    putRingingSessionKey(result.sessionKey)
                    putTerminalActionResult(result.result, result.message)
                },
            null,
            object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    delivered.countDown()
                }
            },
            null,
            0,
            null,
            null,
        )
        assertTrue("terminal result broadcast was not delivered", delivered.await(5, TimeUnit.SECONDS))
    }
}
