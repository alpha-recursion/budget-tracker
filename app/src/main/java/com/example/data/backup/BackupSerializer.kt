package com.example.data.backup

import com.example.data.local.BudgetEntity
import com.example.data.local.DebtEntryEntity
import com.example.domain.DebtKind
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.MerchantRuleEntity
import com.example.data.local.SmsImportEntity
import com.example.data.local.SmsTemplateEntity
import com.example.data.sms.toDomainOrNull
import com.example.data.local.SmsImportStatus
import com.example.data.local.TransactionEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class BackupData(
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val categories: List<CustomCategoryEntity>,
    val smsImports: List<SmsImportEntity> = emptyList(),
    val merchantRules: List<MerchantRuleEntity> = emptyList(),
    val smsTemplates: List<SmsTemplateEntity> = emptyList(),
    val debts: List<DebtEntryEntity> = emptyList(),
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
            // Added after format 1 shipped; older backups simply lack these optional arrays.
            .put("smsImports", JSONArray().apply {
                data.smsImports.forEach { record ->
                    put(
                        JSONObject()
                            .put("dedupeKey", record.dedupeKey)
                            .put("status", record.status)
                            // JSONObject.put(name, null) would drop the key, so write NULL explicitly.
                            .put("transactionId", record.transactionId ?: JSONObject.NULL)
                            .put("receivedAt", record.receivedAt)
                    )
                }
            })
            .put("merchantRules", JSONArray().apply {
                data.merchantRules.forEach { rule ->
                    put(
                        JSONObject()
                            .put("counterpartyKey", rule.counterpartyKey)
                            .put("category", rule.category)
                            .put("isSelfTransfer", rule.isSelfTransfer)
                    )
                }
            })
            .put("smsTemplates", JSONArray().apply {
                data.smsTemplates.forEach { t ->
                    put(
                        JSONObject()
                            .put("id", t.id)
                            .put("bank", t.bank)
                            .put("type", t.type)
                            .put("pattern", t.pattern)
                            .put("amountGroup", t.amountGroup)
                            .put("payeeGroup", t.payeeGroup ?: JSONObject.NULL)
                            .put("dateGroup", t.dateGroup ?: JSONObject.NULL)
                            .put("dateFormat", t.dateFormat ?: JSONObject.NULL)
                            .put("refGroup", t.refGroup ?: JSONObject.NULL)
                            .put("sample", t.sample)
                            .put("createdAt", t.createdAt)
                    )
                }
            })
            .put("debts", JSONArray().apply {
                data.debts.forEach { d ->
                    put(
                        JSONObject()
                            .put("id", d.id)
                            .put("person", d.person)
                            .put("personKey", d.personKey)
                            .put("kind", d.kind)
                            .put("amount", d.amount)
                            .put("date", d.date)
                            .put("dueDate", d.dueDate ?: JSONObject.NULL)
                            .put("note", d.note)
                            .put("smsKey", d.smsKey ?: JSONObject.NULL)
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

            val smsImports = root.optJSONArray("smsImports")?.objects()?.map { o ->
                val status = o.getString("status")
                if (SmsImportStatus.entries.none { it.name == status }) {
                    throw InvalidBackupException("Backup contains an unknown SMS import status")
                }
                SmsImportEntity(
                    dedupeKey = o.getString("dedupeKey"),
                    status = status,
                    transactionId = if (o.isNull("transactionId")) null else o.getLong("transactionId"),
                    receivedAt = o.getLong("receivedAt")
                )
            }.orEmpty()
            val merchantRules = root.optJSONArray("merchantRules")?.objects()?.map { o ->
                MerchantRuleEntity(
                    counterpartyKey = o.getString("counterpartyKey"),
                    category = o.getString("category"),
                    isSelfTransfer = o.getBoolean("isSelfTransfer")
                )
            }.orEmpty()

            // Rows that cannot be used (e.g. a pattern that does not compile) are dropped
            // rather than failing the whole restore.
            val smsTemplates = root.optJSONArray("smsTemplates")?.objects()?.mapNotNull { o ->
                fun optInt(name: String): Int? = if (o.isNull(name)) null else o.getInt(name)
                SmsTemplateEntity(
                    id = o.getLong("id"),
                    bank = o.getString("bank"),
                    type = o.getString("type"),
                    pattern = o.getString("pattern"),
                    amountGroup = o.getInt("amountGroup"),
                    payeeGroup = optInt("payeeGroup"),
                    dateGroup = optInt("dateGroup"),
                    dateFormat = if (o.isNull("dateFormat")) null else o.getString("dateFormat"),
                    refGroup = optInt("refGroup"),
                    sample = o.getString("sample"),
                    createdAt = o.getLong("createdAt")
                ).takeIf { it.toDomainOrNull() != null }
            }.orEmpty()

            val debts = root.optJSONArray("debts")?.objects()?.map { o ->
                val kind = o.getString("kind")
                if (DebtKind.fromName(kind) == null) {
                    throw InvalidBackupException("Backup contains an unknown kind of loan entry")
                }
                DebtEntryEntity(
                    id = o.getLong("id"),
                    person = o.getString("person"),
                    personKey = o.getString("personKey"),
                    kind = kind,
                    amount = o.getDouble("amount"),
                    date = o.getLong("date"),
                    dueDate = if (o.isNull("dueDate")) null else o.getLong("dueDate"),
                    note = o.optString("note", ""),
                    smsKey = if (o.isNull("smsKey")) null else o.getString("smsKey")
                )
            }.orEmpty()

            return BackupData(
                transactions = transactions,
                budgets = budgets,
                categories = categories,
                smsImports = smsImports,
                merchantRules = merchantRules,
                smsTemplates = smsTemplates,
                debts = debts,
                exportedAt = root.optLong("exportedAt", 0L)
            )
        } catch (e: JSONException) {
            throw InvalidBackupException("The backup file is damaged or incomplete")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
