package com.example.data.backup

import com.example.data.local.BudgetEntity
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.TransactionEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class BackupData(
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val categories: List<CustomCategoryEntity>,
    val exportedAt: Long = 0L
)

/** Thrown when a file is not a readable Pennywise backup. The message is user-facing. */
class InvalidBackupException(message: String) : Exception(message)

/**
 * Plain, human-readable JSON backup of everything the user entered. The format carries a
 * version number so future app versions can keep reading old backups.
 */
object BackupSerializer {

    const val FORMAT_VERSION = 1
    private const val APP_ID = "pennywise"

    fun toJson(data: BackupData): String {
        val root = JSONObject()
            .put("app", APP_ID)
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", data.exportedAt)
            .put("transactions", JSONArray().apply {
                data.transactions.forEach { tx ->
                    put(
                        JSONObject()
                            .put("id", tx.id)
                            .put("title", tx.title)
                            .put("amount", tx.amount)
                            .put("type", tx.type)
                            .put("category", tx.category)
                            .put("date", tx.date)
                            .put("note", tx.note)
                    )
                }
            })
            .put("budgets", JSONArray().apply {
                data.budgets.forEach { budget ->
                    put(JSONObject().put("category", budget.category).put("monthlyLimit", budget.monthlyLimit))
                }
            })
            .put("categories", JSONArray().apply {
                data.categories.forEach { cat ->
                    put(
                        JSONObject()
                            .put("name", cat.name)
                            .put("iconKey", cat.iconKey)
                            .put("colorHex", cat.colorHex)
                            .put("type", cat.type)
                    )
                }
            })
        return root.toString(2)
    }

    fun fromJson(json: String): BackupData {
        try {
            val root = JSONObject(json)
            if (root.optString("app") != APP_ID) {
                throw InvalidBackupException("This file is not a Pennywise backup")
            }
            val version = root.optInt("formatVersion", -1)
            if (version < 1 || version > FORMAT_VERSION) {
                throw InvalidBackupException("This backup was made by a newer version of Pennywise")
            }

            val transactions = root.getJSONArray("transactions").objects().map { o ->
                val type = o.getString("type")
                if (type != "EXPENSE" && type != "INCOME") {
                    throw InvalidBackupException("Backup contains an unknown transaction type")
                }
                TransactionEntity(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    amount = o.getDouble("amount"),
                    type = type,
                    category = o.getString("category"),
                    date = o.getLong("date"),
                    note = o.optString("note", "")
                )
            }
            val budgets = root.optJSONArray("budgets")?.objects()?.map { o ->
                BudgetEntity(category = o.getString("category"), monthlyLimit = o.getDouble("monthlyLimit"))
            }.orEmpty()
            val categories = root.optJSONArray("categories")?.objects()?.map { o ->
                CustomCategoryEntity(
                    name = o.getString("name"),
                    iconKey = o.getString("iconKey"),
                    colorHex = o.getLong("colorHex"),
                    type = o.getString("type")
                )
            }.orEmpty()

            return BackupData(transactions, budgets, categories, root.optLong("exportedAt", 0L))
        } catch (e: JSONException) {
            throw InvalidBackupException("The backup file is damaged or incomplete")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
