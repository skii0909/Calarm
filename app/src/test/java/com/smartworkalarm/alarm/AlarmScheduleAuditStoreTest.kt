package com.smartworkalarm.alarm

import android.content.SharedPreferences
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AlarmScheduleAuditStoreTest {
    @Test
    fun `recording and cancelling exposes next active trigger`() {
        val store = SharedPreferencesAlarmScheduleAuditStore(InMemorySharedPreferences())
        val later = Instant.parse("2026-08-26T22:00:00Z")
        val sooner = Instant.parse("2026-08-26T21:00:00Z")

        store.recordScheduled("regular:1", later)
        store.recordScheduled("shift:2", sooner)

        assertEquals(2, store.read().activeAlarmCount)
        assertEquals(sooner, store.read().nextTriggerAt)
        assertNotNull(store.read().lastSuccessfulScheduleAt)

        store.recordCancelled("shift:2")

        assertEquals(1, store.read().activeAlarmCount)
        assertEquals(later, store.read().nextTriggerAt)
    }

    @Test
    fun `snooze record keeps recurring alarm represented separately`() {
        val store = SharedPreferencesAlarmScheduleAuditStore(InMemorySharedPreferences())
        val recurring = Instant.parse("2026-08-27T07:00:00Z")
        val snooze = Instant.parse("2026-08-26T07:10:00Z")

        store.recordScheduled("regular:1", recurring)
        store.recordScheduled("regular:1:snooze", snooze)
        store.recordCancelled("regular:1:snooze")

        assertEquals(1, store.read().activeAlarmCount)
        assertEquals(recurring, store.read().nextTriggerAt)
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)
            ?.filterIsInstance<String>()
            ?.toMutableSet()
            ?: defValues?.toMutableSet()
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

    override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)
    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = put(key, values?.toSet())
    override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
    override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)
    override fun remove(key: String?): SharedPreferences.Editor = put(key, null)
    override fun clear(): SharedPreferences.Editor = apply { values.clear() }
    override fun commit(): Boolean = pending.all { (key, value) ->
        if (value == null) values.remove(key) else values[key] = value
        true
    }
    override fun apply() = Unit

    private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply {
        if (key != null) pending[key] = value
    }
}
