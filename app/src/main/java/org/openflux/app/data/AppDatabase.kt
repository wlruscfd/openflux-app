package org.openflux.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, DeployServerEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun deployServerDao(): DeployServerDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun build(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openflux.db",
                )
                    // No shipped users yet, so a destructive migration for
                    // this schema bump (adding deploy_servers) is the
                    // pragmatic choice rather than writing a real Migration.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
