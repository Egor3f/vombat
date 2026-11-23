package com.efprojects.vombatsu_client.battle

import com.efprojects.vombatsu_client.content.parseInlineHtml
import com.efprojects.vombatsu_client.feed.parseServerDate
import com.efprojects.vombatsu_client.graphql.IncomingAttacksQuery
import java.time.Instant

private const val TYPE_ATTACK = "attack"
private const val STATUS_PENDING = "pending"
private const val TARGET_PRONOUN = "Вас"

data class IncomingAttack(
    val id: Int,
    val expiresAt: Instant,
    val toolName: String,
    val attacker: String,
    val message: String?,
)

fun attackMessage(template: String, attacker: String, pronoun: String?): String =
    parseInlineHtml(template).joinToString("") { it.text }
        .replace("{source}", attacker)
        .replace("{target}", pronoun ?: TARGET_PRONOUN)
        .trim()

fun IncomingAttacksQuery.Data.incomingAttacks(now: Instant): List<IncomingAttack> {
    val myId = me?.id ?: return emptyList()
    return gameState?.pending_interactions.orEmpty().filterNotNull().mapNotNull { interaction ->
        if (interaction.type != TYPE_ATTACK || interaction.status != STATUS_PENDING) return@mapNotNull null
        if (interaction.target_user?.id != myId) return@mapNotNull null
        val duration = interaction.tool_duration ?: return@mapNotNull null
        val expiresAt = parseServerDate(interaction.created_at_iso)?.plusSeconds(duration.toLong())
            ?.takeIf { it > now } ?: return@mapNotNull null
        val attacker = interaction.source_user?.login.orEmpty()
        val message = interaction.messages.orEmpty().filterNotNull()
            .flatMap { set ->
                set.male?.map { it?.message to it?.pronoun } ?: set.default.orEmpty().map { it?.message to it?.pronoun }
            }
            .mapNotNull { (template, pronoun) -> template?.let { attackMessage(it, attacker, pronoun) } }
            .joinToString(" ")
            .ifBlank { null }
        IncomingAttack(interaction.id, expiresAt, interaction.tool?.name.orEmpty(), attacker, message)
    }
}
