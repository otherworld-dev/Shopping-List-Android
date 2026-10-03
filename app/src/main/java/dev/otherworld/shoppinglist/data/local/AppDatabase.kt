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
        GuestShareEntity::class,
        GuestIdEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun itemDao(): ItemDao
    abstract fun areaDao(): AreaDao
    abstract fun mutationDao(): MutationDao
    abstract fun guestShareDao(): GuestShareDao
    abstract fun guestIdDao(): GuestIdDao

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
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE lists ADD COLUMN position INTEGER")
                }
            },
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `guest_shares` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `server` TEXT NOT NULL, " +
                            "`token` TEXT NOT NULL, `permission` INTEGER NOT NULL, " +
                            "`passwordProtected` INTEGER NOT NULL, `title` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                            "`lastRefreshedAt` INTEGER NOT NULL, `droppedChanges` INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_guest_shares_server_token` " +
                            "ON `guest_shares` (`server`, `token`)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `guest_ids` (" +
                            "`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `shareId` INTEGER NOT NULL, " +
                            "`kind` TEXT NOT NULL, `remoteId` INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_guest_ids_shareId_kind_remoteId` " +
                            "ON `guest_ids` (`shareId`, `kind`, `remoteId`)",
                    )
                    db.execSQL("ALTER TABLE lists ADD COLUMN guestShareId INTEGER")
                }
            },
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE lists ADD COLUMN isLocal INTEGER NOT NULL DEFAULT 0")
                }
            },
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE items ADD COLUMN imageKey TEXT")
                }
            },
        )
    }
}
