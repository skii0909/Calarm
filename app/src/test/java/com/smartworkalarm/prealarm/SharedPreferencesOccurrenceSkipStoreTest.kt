package com.smartworkalarm.prealarm

import android.content.SharedPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPreferencesOccurrenceSkipStoreTest {
    private val defaultNow = Instant.parse("2026-08-26T12:00:00Z")

    @Test
    fun `marked occurrence is consumed exactly once`() {
        val store = store()
        val key = occurrence(1L, "2026-08-26T22:00:00Z")

        store.mark(key)

        assertTrue(store.consume(key))
        assertFalse(store.consume(key))
    }

    @Test
    fun `marking the same occurrence twice remains a single consumable skip`() {
        val store = store()
        val key = occurrence(1L, "2026-08-26T22:00:00Z")

        store.mark(key)
        store.mark(key)

        assertTrue(store.consume(key))
        assertFalse(store.consume(key))
    }

    @Test
    fun `marked occurrence persists when the store is recreated`() {
        val preferences = InMemorySharedPreferences()
        val key = occurrence(1L, "2026-08-26T22:00:00Z")

        store(preferences).mark(key)

        assertTrue(store(preferences).consume(key))
    }

    @Test
    fun `consuming one occurrence keeps an unrelated occurrence`() {
        val store = store()
        val first = occurrence(1L, "2026-08-26T22:00:00Z")
        val second = occurrence(2L, "2026-08-26T22:00:00Z")
        store.mark(first)
        store.mark(second)

        assertTrue(store.consume(first))
        assertTrue(store.consume(second))
    }

    @Test
    fun `pruning removes occurrences older than twenty four hours and preserves newer ones`() {
        val now = Instant.parse("2026-08-27T22:00:00Z")
        val store = store(now = Instant.parse("2026-08-26T20:00:00Z"))
        val stale = occurrence(1L, "2026-08-26T21:59:59Z")
        val recent = occurrence(2L, "2026-08-26T22:00:00Z")
        store.mark(stale)
        store.mark(recent)

        store.prune(now)

        assertFalse(store.consume(stale))
        assertTrue(store.consume(recent))
    }

    @Test
    fun `mark prunes stored stale occurrences before persisting a new skip`() {
        val preferences = InMemorySharedPreferences()
        val stale = occurrence(1L, "2026-08-26T21:59:59Z")
        val current = occurrence(2L, "2026-08-28T07:00:00Z")
        store(preferences, Instant.parse("2026-08-26T20:00:00Z")).mark(stale)
        val maintained = store(preferences, Instant.parse("2026-08-27T22:00:00Z"))

        maintained.mark(current)

        assertFalse(maintained.consume(stale))
        assertTrue(maintained.consume(current))
    }

    @Test
    fun `consume prunes stale entries even when requested occurrence is absent`() {
        val preferences = InMemorySharedPreferences()
        val stale = occurrence(1L, "2026-08-26T21:59:59Z")
        val boundary = occurrence(2L, "2026-08-26T22:00:00Z")
        val missing = occurrence(3L, "2026-08-28T07:00:00Z")
        val initial = store(preferences, Instant.parse("2026-08-26T20:00:00Z"))
        initial.mark(stale)
        initial.mark(boundary)

        assertFalse(store(preferences, Instant.parse("2026-08-27T22:00:00Z")).consume(missing))

        val retained = preferences.getStringSet("skipped_alarm_occurrences", mutableSetOf()).orEmpty()
        assertFalse(stale.encode() in retained)
        assertTrue(boundary.encode() in retained)
    }

    private fun store(
        preferences: SharedPreferences = InMemorySharedPreferences(),
        now: Instant = defaultNow,
    ) = SharedPreferencesOccurrenceSkipStore(
        preferences = preferences,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun occurrence(id: Long, scheduledAt: String) =
        AlarmOccurrenceKey(AlarmKind.REGULAR, id, Instant.parse(scheduledAt))
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues?.toMutableSet()
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = InMemoryEditor(values)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

private class InMemoryEditor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
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
    override fun apply() {
        commit()
    }

    private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply {
        if (key != null) pending[key] = value
    }
}
