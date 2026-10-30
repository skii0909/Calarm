package com.smartworkalarm.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.smartworkalarm.data.repository.AlarmRepositoryImpl
import com.smartworkalarm.di.DatabaseModule
import com.smartworkalarm.domain.model.Alarm
import com.smartworkalarm.domain.repository.ScheduleResult
import com.smartworkalarm.prealarm.PreAlarmNotificationFactory
import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a disposable emulator. Verifies digital playback, not physical speaker audibility. */
@RunWith(AndroidJUnit4::class)
class AlarmDeliveryReliabilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    @Test fun scheduledAlarmReachesReceiverServiceAndAudioPlayer() {
        checkScheduledDelivery("android.resource://com.smartworkalarm/raw/digital_pulse")
    }

    @Test fun inaccessibleSelectedSoundFallsBackToAudiblePlayback() {
        checkScheduledDelivery("content://com.smartworkalarm.missing-provider/deleted-sound")
    }

    private fun checkScheduledDelivery(ringtone: String) = runBlocking {
        val packageName = context.packageName
        val manager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            shell("appops set $packageName SCHEDULE_EXACT_ALARM allow")
        }
        await("Exact alarm access was not granted") {
            Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        }
        println("Delivery test permissions ready at ${java.time.Instant.now()}")
        // Finish visible work; a permission broadcast can still enqueue more work after this query.
        await("Permission restoration did not finish") {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(SystemChangeReceiver.UNIQUE_WORK_NAME)
                .get().all { it.state.isFinished }
        }
        val notifications = context.getSystemService(NotificationManager::class.java)
        context.stopService(Intent(context, AlarmRingingService::class.java))
        await("Old ringing service did not stop") { notifications.activeNotifications.none { it.id == 7001 } }
        val database = DatabaseModule.provideDatabase(context)
        val repository = AlarmRepositoryImpl(database.alarmDao())
        val clock = Clock.systemDefaultZone()
        val scheduler = AlarmSchedulerImpl(
            context, manager, ExactAlarmPermission(manager),
            SharedPreferencesAlarmScheduleAuditStore(
                context.getSharedPreferences("alarm_schedule_audit", Context.MODE_PRIVATE), clock,
            ),
            clock, PreAlarmNotificationFactory(context, notifications),
        )
        var id: Long? = null
        try {
            // Match the persisted hour/minute exactly. A delayed permission/installation restore
            // must calculate the same future occurrence, rather than moving a seconds-only fixture to tomorrow.
            val due = clock.instant().plusSeconds(5).truncatedTo(ChronoUnit.MINUTES).plusSeconds(60)
            val local = due.atZone(ZoneId.systemDefault())
            val name = "Delivery instrumentation ${System.nanoTime()}"
            val savedId = repository.upsert(
                Alarm(
                    id = 0, name = name, hour = local.hour, minute = local.minute,
                    repeatDaysMask = 127, enabled = true, skipOnCalendarMatch = false,
                    skipOnPublicHoliday = false, ringtoneUri = ringtone,
                ),
            )
            id = savedId
            assertEquals(ScheduleResult.Scheduled, scheduler.schedule(savedId, due))
            println("Delivery fixture #$savedId scheduled at ${clock.instant()} for $due; sound=$ringtone")
            val deliveryWaitMs = java.time.Duration.between(clock.instant(), due).toMillis() + 30_000L
            await("Actual alarm broadcast did not start foreground ringing", deliveryWaitMs) {
                notifications.activeNotifications.any {
                    it.id == 7001 && it.notification.extras.getString(Notification.EXTRA_TITLE) == name
                }
            }
            println("Delivery fixture #$savedId foreground notification seen at ${clock.instant()}")
            val journal = AlarmExecutionJournal(context, clock)
            await("Receiver did not record a ringing decision: ${journal.readSummary()}") {
                journal.readSummary().lineSequence().any { it.contains("#$savedId · 소리 실행 요청") }
            }
            var audioDump = ""
            val uid = context.applicationInfo.uid
            await("No started USAGE_ALARM player for application UID $uid", 25_000L) {
                audioDump = shell("dumpsys audio")
                audioDump.lineSequence().any { line ->
                    Regex("u/pid:\\s*$uid/").containsMatchIn(line) &&
                        line.contains("state:started") && line.contains("USAGE_ALARM")
                }
            }
            assertTrue(audioDump.contains("USAGE_ALARM"))
            println("Confirmed actual scheduled receiver -> foreground service -> started alarm audio for UID $uid")
        } catch (error: Throwable) {
            val diagnostics = buildString {
                appendLine("Alarm fixture ID: $id, database row: ${id?.let { repository.getById(it) }}")
                appendLine("Execution journal:\n${AlarmExecutionJournal(context, clock).readSummary()}")
                appendLine("Schedule audit: ${SharedPreferencesAlarmScheduleAuditStore(
                    context.getSharedPreferences("alarm_schedule_audit", Context.MODE_PRIVATE), clock,
                ).read()}")
                val alarms = shell("dumpsys alarm").lines()
                val related = alarms.indices.filter { alarms[it].contains(packageName) }
                    .flatMap { index -> (maxOf(0, index - 3)..minOf(alarms.lastIndex, index + 5)).toList() }
                    .distinct()
                appendLine("AlarmManager records:\n${related.joinToString("\n") { alarms[it] }}")
                appendLine("Audio records:\n${shell("dumpsys audio").lineSequence().filter {
                    it.contains("u/pid:") || it.contains("USAGE_ALARM")
                }.joinToString("\n")}")
            }
            throw AssertionError("${error.message}\n$diagnostics", error)
        } finally {
            context.stopService(Intent(context, AlarmRingingService::class.java))
            id?.let { scheduler.cancel(it); repository.delete(it) }
            database.close()
        }
    }

    private fun await(message: String, timeoutMs: Long = 10_000L, ready: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (ready()) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(message)
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
}
