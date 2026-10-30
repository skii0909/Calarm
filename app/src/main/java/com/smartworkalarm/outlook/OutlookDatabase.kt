package com.smartworkalarm.outlook

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "snapshot")
data class OutlookSnapshotRow(@PrimaryKey val id: Int = 1, val payload: String)

@Dao
interface OutlookSnapshotDao {
    @Query("SELECT * FROM snapshot WHERE id = 1")
    suspend fun read(): OutlookSnapshotRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: OutlookSnapshotRow)
}

@Database(entities = [OutlookSnapshotRow::class], version = 1, exportSchema = false)
abstract class OutlookDatabase : RoomDatabase() {
    abstract fun snapshotDao(): OutlookSnapshotDao
}
