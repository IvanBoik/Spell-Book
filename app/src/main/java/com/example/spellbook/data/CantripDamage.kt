package com.example.spellbook.data

import com.example.spellbook.data.model.Spell

/**
 * Урон заговора для списка «Оружие и боевые заговоры» листа LSS.
 *
 * @param dice урон с учётом уровня персонажа: `2к10`.
 * @param damageType код типа урона из [SpellOptions.damageTypes]; пусто — не распознан.
 * @param notes подсказка к броску: спасбросок, число лучей.
 */
data class CantripDamage(val dice: String, val damageType: String, val notes: String)

/**
 * Разбор урона заговоров. В библиотеке урон заклинаний не заполнен структурно, поэтому он
 * берётся из описания dnd.su: «получает 1к8 урона огнём», «получает урон огнём 1к10»,
 * «получает колющий урон 1к6». Урон заговора растёт на 5, 11 и 17 уровнях персонажа:
 * если в тексте есть «5-го уровня (2к10)», число костей увеличивается с уровнем.
 *
 * Заговоры, которые усиливают атаку оружием («Клинок зелёного пламени», «Громовой клинок»),
 * не разбираются: их урон — это урон оружия.
 */
object CantripDamages {

    private val DICE = Regex("(\\d+)\\s*[кkdD]\\s*(\\d+)")
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")
    private val WEAPON_ATTACK = Regex("атак\\S*\\s+(?:\\S+\\s+){0,3}?оружием")
    private val SAVE = Regex("спасброс\\S*\\s+(Силы|Ловкости|Телосложения|Интеллекта|Мудрости|Харизмы)")

    /** Основы слов типа урона в описаниях dnd.su → код типа урона. */
    private val TYPE_STEMS = listOf(
        "огн" to "fire",
        "холод" to "cold",
        "электричеств" to "lightning",
        "звук" to "thunder",
        "кислот" to "acid",
        "яд" to "poison",
        "некротическ" to "necrotic",
        "излучени" to "radiant",
        "психическ" to "psychic",
        "силов" to "force",
        "колющ" to "piercing",
        "рубящ" to "slashing",
        "дробящ" to "bludgeoning",
    )

    /** Уровни персонажа, на которых урон заговора получает ещё одну кость. */
    private val SCALING_LEVELS = listOf(5, 11, 17)

    /** Урон заговора на уровне персонажа [characterLevel]; null — заговор не наносит урон. */
    fun of(spell: Spell, characterLevel: Int): CantripDamage? {
        if (spell.level != 0) return null
        val text = spell.description
        if (WEAPON_ATTACK.containsMatchIn(text.lowercase())) return null
        val extraDice = SCALING_LEVELS.count { characterLevel >= it }

        // Структурный урон (заклинания, загруженные в формате LSS с заполненным уроном).
        spell.damageParts.firstOrNull { it.formula.isNotBlank() && it.type != "healing" }?.let { part ->
            val dice = DICE.find(part.formula)
            val scaled = dice?.let { "${it.groupValues[1].toInt() + extraDice}к${it.groupValues[2]}" } ?: part.formula
            return CantripDamage(scaled, part.type, notesOf(text, characterLevel))
        }

        val sentence = text.split(SENTENCE_END).firstOrNull { sentence ->
            sentence.contains("урон", ignoreCase = true) && DICE.containsMatchIn(sentence)
        } ?: return null
        val dice = DICE.find(sentence) ?: return null
        val count = dice.groupValues[1].toInt()
        val sides = dice.groupValues[2]
        // «…5-го уровня (2к10)…» — урон растёт с уровнем; иначе (лучи «Мистического заряда») — нет.
        val scales = Regex("уровн\\S*\\s*\\(\\s*\\d+\\s*[кkdD]\\s*$sides\\s*\\)").containsMatchIn(text)
        val type = damageType(sentence)
        return CantripDamage(
            dice = "${count + if (scales) extraDice else 0}к$sides",
            damageType = type,
            notes = notesOf(text, characterLevel),
        )
    }

    /** Первый по тексту тип урона в предложении. */
    private fun damageType(sentence: String): String {
        val lower = sentence.lowercase()
        val damageAt = lower.indexOf("урон").coerceAtLeast(0)
        return TYPE_STEMS
            .mapNotNull { (stem, code) ->
                // Тип стоит рядом со словом «урон»: «урона огнём», «колющий урон».
                // \\b в Java не видит границ кириллических слов, поэтому начало слова — без буквы перед ним.
                Regex("(?<![а-яё])$stem").findAll(lower).map { it.range.first }
                    .minByOrNull { kotlin.math.abs(it - damageAt) }
                    ?.let { position -> Triple(kotlin.math.abs(position - damageAt), position, code) }
            }
            .minWithOrNull(compareBy({ it.first }, { it.second }))
            ?.third
            .orEmpty()
    }

    /** Спасбросок цели и число лучей («Мистический заряд»). */
    private fun notesOf(text: String, characterLevel: Int): String {
        val notes = mutableListOf<String>()
        SAVE.find(text)?.let { notes += "Спасбросок ${it.groupValues[1]}" }
        if (Regex("ещё один луч|два луча", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            notes += "Лучей: ${1 + SCALING_LEVELS.count { characterLevel >= it }}"
        }
        return notes.joinToString(", ")
    }
}
