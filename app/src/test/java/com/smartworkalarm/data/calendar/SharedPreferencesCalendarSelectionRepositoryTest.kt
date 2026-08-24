package com.smartworkalarm.data.calendar

import android.content.SharedPreferences
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPreferencesCalendarSelectionRepositoryTest {
    @Test
    fun `initial reconciliation selects every discovered calendar`() {
        val repository = repository()

        val selected = repository.reconcile(setOf(10L, 20L)).getOrThrow()

        assertEquals(setOf(10L, 20L), selected)
        assertEquals(setOf(10L, 20L), repository.getSelectedCalendarIds().getOrThrow())
    }

    @Test
    fun `reconciliation retains a user calendar deselection`() {
        val repository = repository()
        repository.reconcile(setOf(10L, 20L)).getOrThrow()
        repository.setCalendarSelected(20L, false).getOrThrow()

        val selected = repository.reconcile(setOf(10L, 20L)).getOrThrow()

        assertEquals(setOf(10L), selected)
    }

    @Test
    fun `reconciliation selects calendars discovered after a user change`() {
        val repository = repository()
        repository.reconcile(setOf(10L, 20L)).getOrThrow()
        repository.setCalendarSelected(20L, false).getOrThrow()

        val selected = repository.reconcile(setOf(10L, 20L, 30L)).getOrThrow()

        assertEquals(setOf(10L, 30L), selected)
    }

    @Test
    fun `reconciliation removes calendars no longer available from stored selection`() {
        val repository = repository()
        repository.reconcile(setOf(10L, 20L)).getOrThrow()

        val selected = repository.reconcile(setOf(20L)).getOrThrow()

        assertEquals(setOf(20L), selected)
        assertEquals(setOf(20L), repository.getSelectedCalendarIds().getOrThrow())
    }

    @Test
    fun `reconciliation retains an intentional deselection of every calendar`() {
        val repository = repository()
        repository.reconcile(setOf(10L, 20L)).getOrThrow()
        repository.setCalendarsSelected(setOf(10L, 20L), false).getOrThrow()

        val selected = repository.reconcile(setOf(10L, 20L)).getOrThrow()

        assertEquals(emptySet<Long>(), selected)
    }

    @Test
    fun `bulk selection changes only the calendar ids provided for an account`() {
        val repository = repository()
        repository.reconcile(setOf(10L, 20L, 30L)).getOrThrow()

        repository.setCalendarsSelected(setOf(10L, 20L), false).getOrThrow()

        assertEquals(setOf(30L), repository.getSelectedCalendarIds().getOrThrow())
    }

    @Test
    fun `a user toggle cannot be overwritten by an interleaving reconciliation`() {
        val preferences = InterleavingSharedPreferences(InMemorySharedPreferences())
        val backgroundRepository = SharedPreferencesCalendarSelectionRepository(preferences)
        val settingsRepository = SharedPreferencesCalendarSelectionRepository(preferences)
        backgroundRepository.reconcile(setOf(10L, 20L)).getOrThrow()
        preferences.blockReconcileSelectionRead = true

        val reconcile = FutureTask { backgroundRepository.reconcile(setOf(10L, 20L)).getOrThrow() }
        Thread(reconcile, RECONCILE_THREAD).start()
        assertTrue(preferences.reconcileSelectionRead.await(5, TimeUnit.SECONDS))

        val toggle = FutureTask { settingsRepository.setCalendarSelected(20L, false).getOrThrow() }
        Thread(toggle, TOGGLE_THREAD).start()
        preferences.toggleCommitted.await(1, TimeUnit.SECONDS)
        preferences.releaseReconcile.countDown()

        reconcile.get(5, TimeUnit.SECONDS)
        toggle.get(5, TimeUnit.SECONDS)
        assertEquals(setOf(10L), settingsRepository.getSelectedCalendarIds().getOrThrow())
    }

    private fun repository() = SharedPreferencesCalendarSelectionRepository(InMemorySharedPreferences())
}

private const val RECONCILE_THREAD = "calendar-reconcile"
private const val TOGGLE_THREAD = "calendar-toggle"

private class InterleavingSharedPreferences(
    private val delegate: SharedPreferences,
) : SharedPreferences by delegate {
    @Volatile var blockReconcileSelectionRead: Boolean = false
    val reconcileSelectionRead = CountDownLatch(1)
    val releaseReconcile = CountDownLatch(1)
    val toggleCommitted = CountDownLatch(1)

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val snapshot = delegate.getStringSet(key, defValues)
        if (
            blockReconcileSelectionRead &&
            key == "selected_ids" &&
            Thread.currentThread().name == RECONCILE_THREAD
        ) {
            reconcileSelectionRead.countDown()
            check(releaseReconcile.await(5, TimeUnit.SECONDS)) { "reconcile was not released" }
        }
        return snapshot
    }

    override fun edit(): SharedPreferences.Editor = NotifyingEditor(delegate.edit()) {
        if (Thread.currentThread().name == TOGGLE_THREAD) toggleCommitted.countDown()
    }
}

private class NotifyingEditor(
    private val delegate: SharedPreferences.Editor,
    private val onCommit: () -> Unit,
) : SharedPreferences.Editor by delegate {
    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
        delegate.putStringSet(key, values)
        return this
    }

    override fun commit(): Boolean {
        val committed = delegate.commit()
        onCommit()
        return committed
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues?.toMutableSet()

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

private class Editor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private var clear = false

    override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)

    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
        put(key, values?.toSet())

    override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)

    override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)

    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)

    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)

    override fun remove(key: String?): SharedPreferences.Editor = put(key, null)

    override fun clear(): SharedPreferences.Editor {
        clear = true
        return this
    }

    override fun commit(): Boolean {
        if (clear) values.clear()
        pending.forEach { (key, value) ->
            if (value == null) values.remove(key) else values[key] = value
        }
        return true
    }

    override fun apply() = Unit

    private fun put(key: String?, value: Any?): SharedPreferences.Editor {
        if (key != null) pending[key] = value
        return this
    }
}
