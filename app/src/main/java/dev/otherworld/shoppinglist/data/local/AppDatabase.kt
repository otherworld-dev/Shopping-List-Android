package dev.otherworld.shoppinglist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ListEntity::class,
        ItemEntity::class,
        AreaEntity::class,
        MutationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun itemDao(): ItemDao
    abstract fun areaDao(): AreaDao
    abstract fun mutationDao(): MutationDao

    companion object {
        /**
         * Explicit migrations, so an app update never falls back to the destructive wipe —
         * that would silently drop the offline mutation queue along with the cache.
         */
        val MIGRATIONS = arrayOf<Migration>(
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE lists ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                }
            },
        )
    }
}
