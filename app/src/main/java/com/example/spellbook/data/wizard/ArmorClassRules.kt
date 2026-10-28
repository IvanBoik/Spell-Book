package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.Feat

/**
 * Поиск прибавок к КД в текстах черт, боевых стилей и особенностей:
 * «Вы получаете бонус +1 к КД, когда держите в каждой руке по рукопашному оружию».
 */
object ArmorClassRules {

    private val BONUS = Regex("бонус\\s*\\+\\s*(\\d+)\\s+к\\s+(?:КД|Классу Доспеха)", RegexOption.IGNORE_CASE)

    /** Префикс id составляющей, связанной с чертой: по нему её убирают вместе с чертой. */
    private const val FEAT_PART_PREFIX = "feat:"

    /**
     * Прибавка к КД из текста; null — прибавки нет.
     *
     * Условие («когда держите в каждой руке…») становится пояснением: составляющую
     * удобно отключать, когда условие не выполняется.
     */
    fun bonusPart(name: String, markup: String, id: String? = null): StatPart? {
        val plain = MarkupText.plain(markup)
        val match = BONUS.find(plain) ?: return null
        val sentence = plain.split(Regex("(?<=[.!?])\\s+")).firstOrNull { BONUS.containsMatchIn(it) }.orEmpty()
        val condition = sentence.substring((sentence.indexOf(match.value) + match.value.length).coerceAtMost(sentence.length))
            .trim().trimStart(',').trim().trimEnd('.')
            .replaceFirstChar { it.uppercase() }
        return StatPart(
            id = id ?: java.util.UUID.randomUUID().toString(),
            name = name,
            formula = match.groupValues[1],
            kind = StatPartKind.BONUS,
            description = condition,
        )
    }

    /**
     * Добавляет персонажу прибавку к КД от черты, если черта её даёт.
     *
     * У персонажа без составляющих текущее число КД сначала становится основой,
     * чтобы прибавка не обнулила ручную настройку.
     */
    fun withFeat(character: Character, feat: Feat): Character {
        val part = bonusPart(feat.name, feat.description, FEAT_PART_PREFIX + feat.id) ?: return character
        if (character.armorClassParts.any { it.id == part.id }) return character
        val base = character.armorClassParts.ifEmpty {
            listOf(StatPart(name = "", formula = character.armorClass.toString(), kind = StatPartKind.BASE))
        }
        return character.copy(armorClassParts = base + part)
    }

    /** Убирает прибавку к КД, связанную с чертой. */
    fun withoutFeat(character: Character, featId: String): Character =
        character.copy(armorClassParts = character.armorClassParts.filterNot { it.id == FEAT_PART_PREFIX + featId })
}
