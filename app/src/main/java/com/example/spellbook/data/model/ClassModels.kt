package com.example.spellbook.data.model

/**
 * Насколько класс продвигает заклинательство при мультиклассировании.
 *
 * По правилам уровни заклинателей суммируются с разными коэффициентами,
 * а колдун использует отдельную «магию договора».
 */
enum class SpellcasterType {
    /** Не заклинатель: варвар, воин, монах, плут. */
    NONE,

    /** Полный заклинатель: бард, жрец, друид, чародей, волшебник. */
    FULL,

    /** Половина уровня с округлением вниз: паладин, следопыт. */
    HALF,

    /** Половина уровня с округлением вверх: изобретатель. */
    HALF_ROUNDED_UP,

    /** Треть уровня: мистический ловкач, рыцарь тайны. */
    THIRD,

    /** Колдун: ячейки считаются отдельно от остальных классов. */
    PACT,
}

/** Классы D&D 5e с типом заклинательства. */
enum class CharacterClass(val label: String, val spellcaster: SpellcasterType) {
    ARTIFICER("Изобретатель", SpellcasterType.HALF_ROUNDED_UP),
    BARBARIAN("Варвар", SpellcasterType.NONE),
    BARD("Бард", SpellcasterType.FULL),
    CLERIC("Жрец", SpellcasterType.FULL),
    DRUID("Друид", SpellcasterType.FULL),
    FIGHTER("Воин", SpellcasterType.NONE),
    MONK("Монах", SpellcasterType.NONE),
    PALADIN("Паладин", SpellcasterType.HALF),
    RANGER("Следопыт", SpellcasterType.HALF),
    ROGUE("Плут", SpellcasterType.NONE),
    SORCERER("Чародей", SpellcasterType.FULL),
    WARLOCK("Колдун", SpellcasterType.PACT),
    WIZARD("Волшебник", SpellcasterType.FULL),

    /** Произвольный класс: название задаётся текстом, ячейки не считаются. */
    OTHER("Другое", SpellcasterType.NONE),
    ;

    /** Умеет ли класс переподготавливать заклинания после отдыха. */
    val preparesSpells: Boolean
        get() = this in PREPARING_CLASSES

    /**
     * Готовит ли класс заклинания из полного списка своего класса.
     *
     * Волшебник сюда не входит: он готовит заклинания из личной книги,
     * а не из всего списка, доступного классу.
     */
    val preparesFromClassList: Boolean
        get() = this in CLASS_LIST_PREPARING_CLASSES

    /** Код класса в списках заклинаний (`SpellOptions.classes`); null — у класса нет заклинаний. */
    val spellListCode: String?
        get() = SPELL_LIST_CODES[this]

    private companion object {
        val PREPARING_CLASSES = setOf(ARTIFICER, CLERIC, DRUID, PALADIN, WIZARD)

        /** Классы, готовящие заклинания из списка всего класса. */
        val CLASS_LIST_PREPARING_CLASSES = setOf(ARTIFICER, CLERIC, DRUID, PALADIN)

        val SPELL_LIST_CODES = mapOf(
            ARTIFICER to "artificer",
            BARD to "bard",
            CLERIC to "cleric",
            DRUID to "druid",
            PALADIN to "paladin",
            RANGER to "ranger",
            SORCERER to "sorcerer",
            WARLOCK to "warlock",
            WIZARD to "wizard",
        )
    }
}

/**
 * Класс и уровень в нём — одна строка мультикласса.
 *
 * [customName] заполняется только для [CharacterClass.OTHER],
 * чтобы назвать класс, которого нет в списке.
 */
data class CharacterClassLevel(
    val characterClass: CharacterClass,
    val level: Int,
    val customName: String = "",
) {
    /** Название для отображения: своё для «Другого», иначе штатное. */
    val displayName: String
        get() = customName.ifBlank { characterClass.label }

    /** Формат хранения в базе: `WIZARD:5` или `OTHER:3:Кровомаг`. */
    fun serialize(): String = buildString {
        append(characterClass.name).append(SEPARATOR).append(level)
        // Название идёт последним, поэтому в нём допустимы любые символы.
        if (customName.isNotBlank()) append(SEPARATOR).append(customName)
    }

    companion object {
        private const val SEPARATOR = ':'

        /** Разбирает строку вида `WIZARD:5`; null, если класс неизвестен. */
        fun parse(raw: String): CharacterClassLevel? {
            val parts = raw.split(SEPARATOR, limit = 3)
            val characterClass = runCatching { CharacterClass.valueOf(parts[0]) }.getOrNull() ?: return null
            val level = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return CharacterClassLevel(
                characterClass = characterClass,
                level = level.coerceIn(1, MAX_CLASS_LEVEL),
                customName = parts.getOrNull(2).orEmpty(),
            )
        }
    }
}

/** Максимальный уровень персонажа и любого отдельного класса. */
const val MAX_CLASS_LEVEL = 20

/**
 * Таблица ячеек заклинаний по уровню заклинателя (индекс 0 — уровень 1).
 * Каждая строка — количество ячеек с 1 по 9 уровень.
 */
private val CASTER_SLOTS: List<IntArray> = listOf(
    intArrayOf(2, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(3, 0, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(4, 2, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(4, 3, 0, 0, 0, 0, 0, 0, 0),
    intArrayOf(4, 3, 2, 0, 0, 0, 0, 0, 0),
    intArrayOf(4, 3, 3, 0, 0, 0, 0, 0, 0),
    intArrayOf(4, 3, 3, 1, 0, 0, 0, 0, 0),
    intArrayOf(4, 3, 3, 2, 0, 0, 0, 0, 0),
    intArrayOf(4, 3, 3, 3, 1, 0, 0, 0, 0),
    intArrayOf(4, 3, 3, 3, 2, 0, 0, 0, 0),
    intArrayOf(4, 3, 3, 3, 2, 1, 0, 0, 0),
    intArrayOf(4, 3, 3, 3, 2, 1, 0, 0, 0),
    intArrayOf(4, 3, 3, 3, 2, 1, 1, 0, 0),
    intArrayOf(4, 3, 3, 3, 2, 1, 1, 0, 0),
    intArrayOf(4, 3, 3, 3, 2, 1, 1, 1, 0),
    intArrayOf(4, 3, 3, 3, 2, 1, 1, 1, 0),
    intArrayOf(4, 3, 3, 3, 2, 1, 1, 1, 1),
    intArrayOf(4, 3, 3, 3, 3, 1, 1, 1, 1),
    intArrayOf(4, 3, 3, 3, 3, 2, 1, 1, 1),
    intArrayOf(4, 3, 3, 3, 3, 2, 2, 1, 1),
)

/** Магия договора колдуна: уровень класса → количество ячеек и их уровень. */
private val PACT_SLOTS: List<Pair<Int, Int>> = listOf(
    1 to 1, 2 to 1, 2 to 2, 2 to 2, 2 to 3, 2 to 3, 2 to 4, 2 to 4,
    2 to 5, 2 to 5, 3 to 5, 3 to 5, 3 to 5, 3 to 5, 3 to 5, 3 to 5,
    4 to 5, 4 to 5, 4 to 5, 4 to 5,
)

/** Суммарный уровень персонажа по всем классам. */
fun totalLevel(classes: List<CharacterClassLevel>): Int =
    classes.sumOf { it.level }.coerceIn(1, MAX_CLASS_LEVEL)

/**
 * Считает ячейки заклинаний по правилам мультикласса.
 *
 * Уровни классов-заклинателей складываются с учётом коэффициентов, после чего
 * ячейки берутся из общей таблицы. Ячейки колдуна независимы, поэтому они
 * добавляются к результату отдельно.
 *
 * @return Map<уровень ячейки, количество> без нулевых значений.
 */
fun spellSlotsFor(classes: List<CharacterClassLevel>): Map<Int, Int> {
    val casterLevel = classes.sumOf { entry ->
        when (entry.characterClass.spellcaster) {
            SpellcasterType.FULL -> entry.level
            SpellcasterType.HALF -> entry.level / 2
            SpellcasterType.HALF_ROUNDED_UP -> (entry.level + 1) / 2
            SpellcasterType.THIRD -> entry.level / 3
            // Колдун считается отдельно, а незаклинатели не дают уровней.
            SpellcasterType.NONE, SpellcasterType.PACT -> 0
        }
    }

    val slots = mutableMapOf<Int, Int>()
    CASTER_SLOTS.getOrNull(casterLevel - 1)?.forEachIndexed { index, count ->
        if (count > 0) slots[index + 1] = count
    }

    // Колдун получает свои ячейки поверх общей таблицы.
    val warlockLevel = classes.filter { it.characterClass.spellcaster == SpellcasterType.PACT }.sumOf { it.level }
    PACT_SLOTS.getOrNull(warlockLevel - 1)?.let { (count, slotLevel) ->
        slots[slotLevel] = (slots[slotLevel] ?: 0) + count
    }
    return slots
}

/** Есть ли среди классов хотя бы один заклинатель. */
fun hasSpellcaster(classes: List<CharacterClassLevel>): Boolean =
    classes.any { it.characterClass.spellcaster != SpellcasterType.NONE }

/** Умеет ли персонаж переподготавливать заклинания хотя бы по одному классу. */
fun preparesSpells(classes: List<CharacterClassLevel>): Boolean =
    classes.any { it.characterClass.preparesSpells }

/**
 * Максимальный круг заклинаний, доступный по одному классу.
 *
 * Считается из уровня именно этого класса, а не из суммарного уровня персонажа:
 * друид 4 уровня в паре с чародеем 1 уровня готовит заклинания только до 2 круга.
 */
fun maxSpellCircleFor(entry: CharacterClassLevel): Int =
    spellSlotsFor(listOf(entry)).keys.maxOrNull() ?: 0

/**
 * Классы, готовящие заклинания из полного списка класса, с доступным кругом.
 *
 * @return код класса из `SpellOptions.classes` → максимальный круг. При повторе класса
 * берётся наибольший круг; классы без доступных кругов в результат не попадают.
 */
fun classListPreparingLimits(classes: List<CharacterClassLevel>): Map<String, Int> {
    val limits = mutableMapOf<String, Int>()
    classes.filter { it.characterClass.preparesFromClassList }.forEach { entry ->
        val code = entry.characterClass.spellListCode ?: return@forEach
        val circle = maxSpellCircleFor(entry)
        if (circle > 0) limits[code] = maxOf(limits[code] ?: 0, circle)
    }
    return limits
}
