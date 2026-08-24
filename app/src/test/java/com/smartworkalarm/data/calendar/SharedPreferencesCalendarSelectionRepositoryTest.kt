package com.smartworkalarm.data.calendar

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
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

    private fun repository() = SharedPreferencesCalendarSelectionRepository(InMemorySharedPreferences())
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
