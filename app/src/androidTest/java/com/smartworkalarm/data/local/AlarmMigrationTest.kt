package com.smartworkalarm.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartworkalarm.di.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkAlarmDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFrom1To2PreservesAlarmAndAddsDefaults() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO alarms (id,name,hour,minute,repeat_days_mask,enabled,skip_on_vacation,created_at) " +
                    "VALUES (8,'출근',7,0,31,1,1,123)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db.query("SELECT * FROM alarms WHERE id = 8").use { cursor ->
            cursor.moveToFirst()
            assertEquals("출근", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("hour")))
            assertEquals("휴가", cursor.getString(cursor.getColumnIndexOrThrow("calendar_keywords")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("ringtone_uri")))
        }
        db.close()
    }

    companion object { private const val TEST_DB = "migration-test" }
}
