package com.kaskita.lokal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KasKitaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "kaskita.db"

        fun create(context: Context): KasKitaDatabase =
            Room.databaseBuilder(context.applicationContext, KasKitaDatabase::class.java, NAME)
                .addCallback(SeedCallback())
                .build()
    }
}
