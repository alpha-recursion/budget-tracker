package com.example.data.backup

import com.example.data.local.BudgetEntity
import com.example.data.local.ChallengeEntity
import com.example.data.local.RecurringEntity
import com.example.domain.RecurringFrequency
import com.example.domain.ChallengeType
import com.example.data.local.DebtEntryEntity
import com.example.data.local.GoalEntity
import com.example.data.local.GoalEntryEntity
import com.example.data.local.GoalMilestoneEntity
import com.example.domain.DebtKind
import com.example.data.local.CustomCategoryEntity
import com.example.data.local.MerchantRuleEntity
import com.example.data.local.SmsImportEntity
import com.example.data.local.SmsTemplateEntity
import com.example.data.sms.toDomainOrNull
import com.example.data.local.SmsImportStatus
import com.example.data.local.TransactionEntity
import com.example.data.local.PlanningPrefs
import com.example.data.local.CategoryStyle
import com.example.domain.PaySettings
import com.example.domain.QuickPin
import com.example.domain.RoundUpSettings
import com.example.domain.SavingsStrategy
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
    val goals: List<GoalEntity> = emptyList(),
    val goalEntries: List<GoalEntryEntity> = emptyList(),
    val goalMilestones: List<GoalMilestoneEntity> = emptyList(),
    val challenges: List<ChallengeEntity> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
    /** Null for backups made before settings were included: the phone's own are then kept. */
    val phoneSettings: PhoneSettings? = null,
    val exportedAt: Long = 0L
)

/**
 * What the app keeps in SharedPreferences rather than the database. Reminder switches are not
 * included: they belong to the phone (they schedule its alerts).
 */
data class PhoneSettings(
    val checkInDays: Set<Long> = emptySet(),
    val planning: PlanningPrefs = PlanningPrefs(),
    val roundUps: RoundUpSettings = RoundUpSettings(),
    /** Badges already celebrated, so a restore does not celebrate them all again. */
    val seenBadges: Set<String> = emptySet(),
    val lastLevel: Int = 1,
    /** Icon / colour picked for built-in categories, by name. */
    val categoryStyles: Map<String, CategoryStyle> = emptyMap()
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
            .put("goals", JSONArray().apply {
                data.goals.forEach { g ->
                    put(
                        JSONObject()
                            .put("id", g.id)
                            .put("name", g.name)
                            .put("target", g.target)
                            .put("iconKey", g.iconKey)
                            .put("colorHex", g.colorHex)
                            .put("targetDate", g.targetDate ?: JSONObject.NULL)
                            .put("createdAt", g.createdAt)
                            .put("celebratedMilestone", g.celebratedMilestone)
                            .put("completedAt", g.completedAt ?: JSONObject.NULL)
                            .put("purchasedAt", g.purchasedAt ?: JSONObject.NULL)
                            .put("purchasedAmount", g.purchasedAmount ?: JSONObject.NULL)
                    )
                }
            })
            .put("goalEntries", JSONArray().apply {
                data.goalEntries.forEach { e ->
                    put(
                        JSONObject()
                            .put("id", e.id)
                            .put("goalId", e.goalId)
                            .put("amount", e.amount)
                            .put("date", e.date)
                            .put("note", e.note)
                    )
                }
            })
            .put("goalMilestones", JSONArray().apply {
                data.goalMilestones.forEach { m ->
                    put(
                        JSONObject()
                            .put("id", m.id)
                            .put("goalId", m.goalId)
                            .put("name", m.name)
                            .put("amount", m.amount)
                            .put("celebrated", m.celebrated)
                    )
                }
            })
            .put("recurring", JSONArray().apply {
                data.recurring.forEach { r ->
                    put(
                        JSONObject()
                            .put("id", r.id)
                            .put("name", r.name)
                            .put("amount", r.amount)
                            .put("category", r.category)
                            .put("frequency", r.frequency)
                            .put("startDate", r.startDate)
                            .put("active", r.active)
                            .put("createdAt", r.createdAt)
                    )
                }
            })
            .put("challenges", JSONArray().apply {
                data.challenges.forEach { c ->
                    put(
                        JSONObject()
                            .put("id", c.id)
                            .put("month", c.month)
                            .put("type", c.type)
                            .put("category", c.category ?: JSONObject.NULL)
                            .put("target", c.target)
                            .put("createdAt", c.createdAt)
                    )
                }
            })
        data.phoneSettings?.let { root.put("phoneSettings", settingsToJson(it)) }
        return root.toString(2)
    }

    private fun settingsToJson(s: PhoneSettings): JSONObject = JSONObject()
        .put("checkInDays", JSONArray(s.checkInDays.sorted()))
        .put("quickPins", JSONArray().apply {
            s.planning.pins.forEach { put(JSONObject().put("title", it.title).put("amount", it.amount).put("category", it.category)) }
        })
        .put("quickHidden", JSONArray(s.planning.hidden.sorted()))
        .put("monthlyIncome", s.planning.pay.monthlyIncome)
        .put("payday", s.planning.pay.payday)
        .put("salaryDismissedCycle", s.planning.salaryDismissedCycle)
        .put("savingsStrategy", s.planning.pay.strategy.name)
        .put("savingsHandledCycle", s.planning.savingsHandledCycle)
        .put("roundUps", JSONObject()
            .put("enabled", s.roundUps.enabled)
            .put("unit", s.roundUps.unit)
            .put("startedAt", s.roundUps.startedAt)
            .put("banked", s.roundUps.banked)
            .put("moved", s.roundUps.moved))
        .put("seenBadges", JSONArray(s.seenBadges.sorted()))
        .put("lastLevel", s.lastLevel)
        .put("categoryStyles", JSONArray().apply {
            s.categoryStyles.toSortedMap().forEach { (name, style) ->
                put(JSONObject().put("name", name).put("iconKey", style.iconKey).put("colorHex", style.colorHex))
            }
        })

    private fun settingsFromJson(o: JSONObject): PhoneSettings {
        val round = o.optJSONObject("roundUps")
        val defaults = RoundUpSettings()
        return PhoneSettings(
            checkInDays = o.optJSONArray("checkInDays")?.let { a -> (0 until a.length()).map { a.getLong(it) } }.orEmpty().toSet(),
            planning = PlanningPrefs(
                pins = o.optJSONArray("quickPins")?.objects()?.mapNotNull { p ->
                    QuickPin(p.getString("title"), p.getDouble("amount"), p.getString("category"))
                        .takeIf { it.title.isNotBlank() && it.amount > 0 }
                }.orEmpty(),
                hidden = o.optJSONArray("quickHidden")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().toSet(),
                pay = PaySettings(
                    monthlyIncome = o.optDouble("monthlyIncome", 0.0).takeIf { it >= 0 } ?: 0.0,
                    payday = o.optInt("payday", 0).takeIf { it in 0..31 } ?: 0,
                    strategy = SavingsStrategy.fromName(o.optString("savingsStrategy", ""))
                ),
                salaryDismissedCycle = o.optLong("salaryDismissedCycle", 0L),
                savingsHandledCycle = o.optLong("savingsHandledCycle", 0L)
            ),
            roundUps = if (round == null) defaults else RoundUpSettings(
                enabled = round.optBoolean("enabled", defaults.enabled),
                unit = round.optInt("unit", defaults.unit),
                startedAt = round.optLong("startedAt", defaults.startedAt),
                banked = round.optDouble("banked", defaults.banked),
                moved = round.optDouble("moved", defaults.moved)
            ),
            seenBadges = o.optJSONArray("seenBadges")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().toSet(),
            lastLevel = o.optInt("lastLevel", 1).coerceAtLeast(1),
            categoryStyles = o.optJSONArray("categoryStyles")?.objects()?.associate { c ->
                c.getString("name") to CategoryStyle(c.getString("iconKey"), c.getLong("colorHex"))
            }.orEmpty()
        )
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

            val goals = root.optJSONArray("goals")?.objects()?.map { o ->
                GoalEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    target = o.getDouble("target"),
                    iconKey = o.getString("iconKey"),
                    colorHex = o.getLong("colorHex"),
                    targetDate = if (o.isNull("targetDate")) null else o.getLong("targetDate"),
                    createdAt = o.getLong("createdAt"),
                    celebratedMilestone = o.optInt("celebratedMilestone", 0),
                    completedAt = if (o.isNull("completedAt")) null else o.getLong("completedAt"),
                    purchasedAt = if (!o.has("purchasedAt") || o.isNull("purchasedAt")) null else o.getLong("purchasedAt"),
                    purchasedAmount = if (!o.has("purchasedAmount") || o.isNull("purchasedAmount")) null else o.getDouble("purchasedAmount")
                )
            }.orEmpty()
            val goalIds = goals.map { it.id }.toSet()
            // Entries of a goal that is not in the file would be invisible; leave them out.
            val goalEntries = root.optJSONArray("goalEntries")?.objects()?.map { o ->
                GoalEntryEntity(
                    id = o.getLong("id"),
                    goalId = o.getLong("goalId"),
                    amount = o.getDouble("amount"),
                    date = o.getLong("date"),
                    note = o.optString("note", "")
                )
            }.orEmpty().filter { it.goalId in goalIds }
            val goalMilestones = root.optJSONArray("goalMilestones")?.objects()?.map { o ->
                GoalMilestoneEntity(
                    id = o.getLong("id"),
                    goalId = o.getLong("goalId"),
                    name = o.getString("name"),
                    amount = o.getDouble("amount"),
                    celebrated = o.optBoolean("celebrated", false)
                )
            }.orEmpty().filter { it.goalId in goalIds }

            // Challenge kinds this version does not know are left out rather than failing the restore.
            val challenges = root.optJSONArray("challenges")?.objects()?.mapNotNull { o ->
                ChallengeEntity(
                    id = o.getLong("id"),
                    month = o.getInt("month"),
                    type = o.getString("type"),
                    category = if (o.isNull("category")) null else o.getString("category"),
                    target = o.getDouble("target"),
                    createdAt = o.getLong("createdAt")
                ).takeIf { c -> ChallengeType.entries.any { it.name == c.type } }
            }.orEmpty()

            // Frequencies this version does not know are left out rather than failing the restore.
            val recurring = root.optJSONArray("recurring")?.objects()?.mapNotNull { o ->
                RecurringEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    amount = o.getDouble("amount"),
                    category = o.getString("category"),
                    frequency = o.getString("frequency"),
                    startDate = o.getLong("startDate"),
                    active = o.optBoolean("active", true),
                    createdAt = o.getLong("createdAt")
                ).takeIf { r -> RecurringFrequency.fromName(r.frequency) != null }
            }.orEmpty()

            return BackupData(
                transactions = transactions,
                budgets = budgets,
                categories = categories,
                smsImports = smsImports,
                merchantRules = merchantRules,
                smsTemplates = smsTemplates,
                debts = debts,
                goals = goals,
                goalEntries = goalEntries,
                goalMilestones = goalMilestones,
                challenges = challenges,
                recurring = recurring,
                phoneSettings = root.optJSONObject("phoneSettings")?.let { settingsFromJson(it) },
                exportedAt = root.optLong("exportedAt", 0L)
            )
        } catch (e: JSONException) {
            throw InvalidBackupException("The backup file is damaged or incomplete")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
