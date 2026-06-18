package dev.otherworld.shoppinglist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ListEntity::class,
        ItemEntity::class,
        AreaEntity::class,
        MutationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun itemDao(): ItemDao
    abstract fun areaDao(): AreaDao
    abstract fun mutationDao(): MutationDao
}
