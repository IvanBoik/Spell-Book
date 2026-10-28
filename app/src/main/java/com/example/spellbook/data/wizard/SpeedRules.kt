package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CharacterClass
import com.example.spellbook.data.model.Feat
import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind

/**
 * Составляющие скорости: скорость расы и постоянные прибавки от черт и умений.
 *
 * Прибавка считается постоянной, только если предложение прямо говорит «Ваша скорость
 * (ходьбы) увеличивается на N футов» и не ограничено временем действия («до конца хода»,
 * «пока вы в ярости», «во время превращения»): такие эффекты включаются в бою и в лист
 * не попадают. Условие вида «если вы не носите тяжёлые доспехи» становится пояснением —
 * составляющую удобно отключить, пока условие не выполняется.
 */
object SpeedRules {

    /** Префикс id составляющей от черты: по нему её убирают вместе с чертой. */
    private const val FEAT_PART_PREFIX = "feat:"

    private val PERMANENT = Regex(
        "^ваша\\s+(?:базовая\\s+)?скорость(?:\\s+ходьбы)?\\s+увеличивается\\s+на\\s+(\\d+)\\s+фут\\S*\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )

    /** Признаки временного эффекта: прибавка действует только в определённый момент. */
    private val TEMPORARY = listOf(
        "до конца", "пока ", "во время", "в течение", "в состоянии", "когда ", "на 1 минуту", "в этот ход",
    )

    /** «Движение без доспехов» монаха: +10 на 2-м уровне и ещё +5 каждые 4 уровня (до +30). */
    private const val MONK_FORMULA = "min(30, 10 + 5 * (([level] - 2) / 4))"
    private const val MONK_FEATURE = "движение без доспехов"
    private const val MONK_LEVEL = 2

    /**
     * Постоянная прибавка из текста; null — в тексте её нет или она временная.
     *
     * @param withBullets разбирать пункты списка. У черт пункты — сами эффекты («- Ваша скорость
     * увеличивается…»), а у умений классов — варианты на выбор (свойства камня преобразования).
     */
    fun bonusPart(name: String, markup: String, id: String? = null, withBullets: Boolean = false): StatPart? {
        FeatureRules.sentences(markup, withBullets = withBullets).forEach { sentence ->
            val match = PERMANENT.find(sentence.trim()) ?: return@forEach
            val tail = match.groupValues[2].trim().trimStart(',').trim().trimEnd('.', ';')
            if (TEMPORARY.any { tail.lowercase().contains(it) }) return@forEach
            return StatPart(
                id = id ?: java.util.UUID.randomUUID().toString(),
                name = name,
                formula = match.groupValues[1],
                kind = StatPartKind.BONUS,
                description = tail.replaceFirstChar { it.uppercase() },
            )
        }
        return null
    }

    /** Прибавки от умений класса и архетипа на уровне. */
    fun classParts(rules: ClassRules, subclass: SubclassRules?, level: Int, showOptional: Boolean): List<StatPart> {
        val features = rules.features.filter { it.level <= level && (showOptional || !it.extended) } +
            subclass?.features.orEmpty().filter { it.level <= level && (showOptional || !it.extended) }
        return features.mapNotNull { feature ->
            val isMonkMovement = rules.type == CharacterClass.MONK && feature.title.lowercase().startsWith(MONK_FEATURE)
            if (isMonkMovement) {
                // Прибавка растёт с уровнем — формула, чтобы она пересчитывалась при повышении.
                val condition = bonusPart(feature.title, feature.text)?.description.orEmpty()
                StatPart(
                    id = "class:monk-movement",
                    name = feature.title,
                    formula = MONK_FORMULA,
                    kind = StatPartKind.BONUS,
                    description = condition,
                ).takeIf { level >= MONK_LEVEL }
            } else {
                bonusPart(feature.title, feature.text, "class:${feature.title.lowercase()}")
            }
        }
    }

    /** Прибавка от черты («Подвижный», «Низкорослое проворство»). */
    fun featPart(feat: Feat): StatPart? =
        bonusPart(feat.name, feat.description, FEAT_PART_PREFIX + feat.id, withBullets = true)

    /**
     * Добавляет персонажу прибавку к скорости от черты, если черта её даёт.
     *
     * У персонажа без составляющих текущая скорость сначала становится основой,
     * чтобы прибавка не обнулила ручную настройку.
     */
    fun withFeat(character: Character, feat: Feat): Character {
        val part = featPart(feat) ?: return character
        if (character.speedParts.any { it.id == part.id }) return character
        val base = character.speedParts.ifEmpty {
            listOf(StatPart(name = "", formula = character.speed.toString(), kind = StatPartKind.BASE))
        }
        return character.copy(speedParts = base + part)
    }

    /** Убирает прибавку к скорости, связанную с чертой. */
    fun withoutFeat(character: Character, featId: String): Character =
        character.copy(speedParts = character.speedParts.filterNot { it.id == FEAT_PART_PREFIX + featId })
}
