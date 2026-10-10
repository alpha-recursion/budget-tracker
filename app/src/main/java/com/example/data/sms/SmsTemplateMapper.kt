package com.example.data.sms

import com.example.data.local.SmsTemplateEntity
import com.example.data.model.TransactionType
import com.example.domain.sms.SmsBank
import com.example.domain.sms.SmsDateFormat
import com.example.domain.sms.SmsTemplate

/**
 * Entity -> domain. Returns null for rows that cannot be used (unknown enum names, or a pattern
 * that does not compile, e.g. from a damaged backup) so a bad row never crashes a scan.
 */
fun SmsTemplateEntity.toDomainOrNull(): SmsTemplate? {
    val parsedBank = SmsBank.entries.firstOrNull { it.name == this.bank } ?: return null
    val parsedType = TransactionType.entries.firstOrNull { it.name == this.type } ?: return null
    val format = dateFormat?.let { name -> SmsDateFormat.entries.firstOrNull { it.name == name } ?: return null }
    if (runCatching { Regex(pattern) }.isFailure) return null
    return SmsTemplate(
        id = id,
        bank = parsedBank,
        type = parsedType,
        pattern = pattern,
        amountGroup = amountGroup,
        payeeGroup = payeeGroup,
        dateGroup = dateGroup,
        dateFormat = format,
        refGroup = refGroup,
        sample = sample
    )
}

fun SmsTemplate.toEntity(createdAt: Long): SmsTemplateEntity = SmsTemplateEntity(
    id = id,
    bank = bank.name,
    type = type.name,
    pattern = pattern,
    amountGroup = amountGroup,
    payeeGroup = payeeGroup,
    dateGroup = dateGroup,
    dateFormat = dateFormat?.name,
    refGroup = refGroup,
    sample = sample,
    createdAt = createdAt
)
