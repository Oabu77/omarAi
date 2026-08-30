package com.darcloud.omarai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CustomerEntity::class,
        LeadEntity::class,
        JobEntity::class,
        InvoiceEntity::class,
        TaskEntity::class,
        AuditEventEntity::class,
        ChatMessageEntity::class,
        AiOutputReportEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class OmarDatabase : RoomDatabase() {
    abstract fun omarDao(): OmarDao

    companion object {
        @Volatile private var instance: OmarDatabase? = null

        fun get(context: Context): OmarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                OmarDatabase::class.java,
                "omar-ai.db",
            ).build().also { instance = it }
        }
    }
}
