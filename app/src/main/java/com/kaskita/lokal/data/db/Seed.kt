package com.kaskita.lokal.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kaskita.lokal.domain.AccountType
import com.kaskita.lokal.domain.TransactionType

/** Default accounts and categories only; no sample transactions. */
object DefaultData {
    data class SeedAccount(val name: String, val type: AccountType)
    data class SeedCategory(val name: String, val type: TransactionType, val iconKey: String, val colorKey: String)

    val accounts = listOf(
        SeedAccount("Cash", AccountType.CASH),
        SeedAccount("Rekening Bank", AccountType.BANK),
    )

    // "Tarik tunai" is intentionally absent: withdrawing cash is a TRANSFER, not an expense.
    val categories = listOf(
        SeedCategory("Makanan & minuman", TransactionType.EXPENSE, "restaurant", "orange"),
        SeedCategory("Belanja kebutuhan", TransactionType.EXPENSE, "shopping_cart", "blue"),
        SeedCategory("Transportasi / bensin", TransactionType.EXPENSE, "directions_car", "teal"),
        SeedCategory("Tagihan", TransactionType.EXPENSE, "receipt", "purple"),
        SeedCategory("Rumah tangga", TransactionType.EXPENSE, "home", "brown"),
        SeedCategory("Kesehatan", TransactionType.EXPENSE, "health", "red"),
        SeedCategory("Pendidikan", TransactionType.EXPENSE, "school", "indigo"),
        SeedCategory("Hiburan", TransactionType.EXPENSE, "movie", "pink"),
        SeedCategory("Belanja online", TransactionType.EXPENSE, "shopping_bag", "amber"),
        SeedCategory("Biaya kerja/usaha", TransactionType.EXPENSE, "work", "green"),
        SeedCategory("Lain-lain", TransactionType.EXPENSE, "more_horiz", "gray"),
        SeedCategory("Gaji", TransactionType.INCOME, "payments", "green"),
        SeedCategory("Usaha/penjualan", TransactionType.INCOME, "storefront", "teal"),
        SeedCategory("Bonus", TransactionType.INCOME, "star", "amber"),
        SeedCategory("Hadiah", TransactionType.INCOME, "card_giftcard", "pink"),
        SeedCategory("Pengembalian uang", TransactionType.INCOME, "undo", "blue"),
        SeedCategory("Bunga", TransactionType.INCOME, "savings", "indigo"),
        SeedCategory("Lain-lain", TransactionType.INCOME, "more_horiz", "gray"),
    )
}

/** Inserts [DefaultData] the first time the database file is created. Restore never re-runs it. */
class SeedCallback(private val now: () -> Long = System::currentTimeMillis) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val createdAt = now()
        DefaultData.accounts.forEach { a ->
            db.execSQL(
                "INSERT INTO accounts (name, type, openingBalance, createdAt, isArchived) VALUES (?, ?, 0, ?, 0)",
                arrayOf<Any>(a.name, a.type.name, createdAt),
            )
        }
        DefaultData.categories.forEach { c ->
            db.execSQL(
                "INSERT INTO categories (name, type, iconKey, colorKey, isDefault, isArchived) VALUES (?, ?, ?, ?, 1, 0)",
                arrayOf<Any>(c.name, c.type.name, c.iconKey, c.colorKey),
            )
        }
    }
}
