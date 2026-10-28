package com.example.spellbook.data.wizard

import com.example.spellbook.data.initiativePartsOrDefault
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind

/**
 * Составляющие инициативы: модификатор Ловкости и постоянные прибавки от черт и умений.
 *
 * Прибавка считается постоянной, только если текст прямо даёт её к броскам инициативы:
 * «бонус +5 к проверкам инициативы» («Бдительный»), «можете добавить к броскам инициативы
 * модификатор Мудрости» (школа прорицания, «Тактический ум»), «прибавляете к инициативе
 * бонус мастерства» (Зайцегон). Разовые эффекты («до конца хода», «в начале боя»,
 * «потратив кость превосходства») в лист не попадают.
 */
object InitiativeRules {

    /** Префикс id составляющей от черты: по нему её убирают вместе с чертой. */
    private const val FEAT_PART_PREFIX = "feat:"

    private val FLAT = Regex("бонус\\s*\\+\\s*(\\d+)\\s+к\\s+(?:проверкам|броскам)\\s+инициативы", RegexOption.IGNORE_CASE)

    /** «добавить к броскам инициативы модификатор Мудрости», «прибавлять модификатор Телосложения к инициативе». */
    private val ABILITY = Regex(
        "(?:добав|прибав)\\S*\\s+(?:к\\s+(?:своим\\s+|вашим\\s+)?(?:броскам|проверкам|результату броска)\\s+инициативы\\s+)?" +
            "(?:ваш\\S*\\s+|свой\\s+)?модификатор\\S*\\s+([а-яё]+)",
        RegexOption.IGNORE_CASE,
    )

    /** «прибавлять свой бонус мастерства к своим броскам инициативы». */
    private val PROFICIENCY = Regex("(?:добав|прибав)\\S*\\s+(?:сво\\S*\\s+|ваш\\S*\\s+)?бонус\\S*\\s+мастерства", RegexOption.IGNORE_CASE)

    /** Разовые эффекты: прибавка действует в определённый момент, а не всегда. */
    private val TEMPORARY = listOf(
        "до конца", "в течение", "кост", "потрат", "ход", "1 минут", "раз ", "пока ",
    )

    private val ABILITY_CODES = mapOf(
        AbilityType.STRENGTH to "str",
        AbilityType.DEXTERITY to "dex",
        AbilityType.CONSTITUTION to "con",
        AbilityType.INTELLIGENCE to "int",
        AbilityType.WISDOM to "wis",
        AbilityType.CHARISMA to "cha",
    )

    /** Постоянная прибавка из текста; null — в тексте её нет или она разовая. */
    fun bonusPart(name: String, markup: String, id: String? = null, withBullets: Boolean = false): StatPart? {
        FeatureRules.sentences(markup, withBullets).forEach { sentence ->
            val lower = sentence.lowercase()
            if (!lower.contains("инициатив")) return@forEach
            if (TEMPORARY.any { lower.contains(it) }) return@forEach
            val formula = FLAT.find(sentence)?.groupValues?.get(1)
                ?: ABILITY.find(sentence)?.groupValues?.get(1)?.let(MarkupText::ability)
                    ?.takeIf { it != AbilityType.DEXTERITY }
                    ?.let { "[${ABILITY_CODES.getValue(it)}]" }
                ?: if (PROFICIENCY.containsMatchIn(sentence)) "[pb]" else null
            if (formula != null) {
                return StatPart(
                    id = id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    formula = formula,
                    kind = StatPartKind.BONUS,
                )
            }
        }
        return null
    }

    /** Прибавки от особенностей расы. */
    fun raceParts(traits: List<RuleFeature>): List<StatPart> =
        traits.mapNotNull { bonusPart(it.title, it.text, "race:${it.title.lowercase()}") }

    /** Прибавки от умений класса и архетипа на уровне. */
    fun classParts(rules: ClassRules, subclass: SubclassRules?, level: Int, showOptional: Boolean): List<StatPart> {
        val features = rules.features.filter { it.level <= level && (showOptional || !it.extended) } +
            subclass?.features.orEmpty().filter { it.level <= level && (showOptional || !it.extended) }
        return features.mapNotNull { bonusPart(it.title, it.text, "class:${it.title.lowercase()}") }
    }

    /** Прибавка от черты («Бдительный»). */
    fun featPart(feat: Feat): StatPart? =
        bonusPart(feat.name, feat.description, FEAT_PART_PREFIX + feat.id, withBullets = true)

    /** Добавляет персонажу прибавку к инициативе от черты; прежняя формула становится основой. */
    fun withFeat(character: Character, feat: Feat): Character {
        val part = featPart(feat) ?: return character
        if (character.initiativeParts.any { it.id == part.id }) return character
        return character.copy(initiativeParts = character.initiativePartsOrDefault + part)
    }

    /** Убирает прибавку к инициативе, связанную с чертой. */
    fun withoutFeat(character: Character, featId: String): Character =
        character.copy(initiativeParts = character.initiativeParts.filterNot { it.id == FEAT_PART_PREFIX + featId })
}
