package com.example.spellbook.data

import androidx.annotation.StringRes
import com.example.spellbook.R
import com.example.spellbook.data.model.AbilityType
import com.example.spellbook.data.model.StatPart
import com.example.spellbook.data.model.StatPartKind
import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.DEFAULT_SPEED_FORMULA
import com.example.spellbook.data.model.UNARMORED_AC_FORMULA
import com.example.spellbook.data.model.DEFAULT_ARMOR_CLASS
import com.example.spellbook.data.model.unarmoredArmorClassPart
import com.example.spellbook.data.model.ComboRollMode
import kotlin.random.Random

/**
 * Переменная формулы: код для вставки и понятное описание для подсказки.
 *
 * [abilityLabelRes] задан, если переменная — модификатор характеристики:
 * тогда подпись складывается из двух ресурсов («Модификатор: Сила»).
 */
data class FormulaVariable(
    val code: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val abilityLabelRes: Int? = null,
)

/**
 * Результат вычисления выражения.
 *
 * @param breakdown запись с подставленными значениями, например `6 + 2 + 5`.
 */
data class FormulaResult(val total: Int, val rolls: List<Int>, val breakdown: String = "")

/**
 * Универсальные выражения для ресурсов и шагов комбинаций.
 *
 * Поддерживаются:
 * - числа: `3`;
 * - кости: `1d8`, `6d8` (латинская `d` и русская `к`);
 * - переменные в квадратных скобках: `[pb]`, `[level]`, `[str]`, `[dex]`,
 *   `[con]`, `[int]`, `[wis]`, `[cha]`;
 * - операции `+`, `-`, `*`, `/` и группирующие скобки.
 *
 * Примеры: `6d8 + [int]`, `[pb] * 2`, `([level] + 1) / 2`.
 *
 * Формулы нужны, чтобы не переписывать ресурсы и шаги комбинаций при росте
 * уровня или характеристик: значения пересчитываются автоматически.
 */
object StatFormula {

    private const val VARIABLE_OPEN = '['
    private const val VARIABLE_CLOSE = ']'

    /** Английские коды характеристик в порядке [AbilityType]. */
    private val ABILITY_CODES = listOf("str", "dex", "con", "int", "wis", "cha")

    /** Переменные формулы: код → как вычислить по персонажу. */
    private val VARIABLES: Map<String, (Character) -> Int> = buildMap {
        put("pb", Character::proficiencyBonus)
        put("level", Character::level)
        AbilityType.entries.forEach { ability ->
            put(ABILITY_CODES[ability.ordinal]) { it.abilityModifierOf(ability) }
        }
    }

    /** Список подсказок, который показывается после ввода `[`. */
    val SUGGESTIONS: List<FormulaVariable> = buildList {
        add(FormulaVariable("pb", R.string.formula_proficiency_bonus))
        add(FormulaVariable("level", R.string.formula_character_level))
        AbilityType.entries.forEach { ability ->
            add(
                FormulaVariable(
                    code = ABILITY_CODES[ability.ordinal],
                    labelRes = R.string.formula_ability_modifier,
                    abilityLabelRes = ability.labelRes,
                ),
            )
        }
    }

    /** Короткие читаемые названия переменных — для отображения формулы пользователю. */
    val VARIABLE_NAME_RES: Map<String, Int> = buildMap {
        put("pb", R.string.formula_proficiency_bonus)
        put("level", R.string.formula_level)
        AbilityType.entries.forEach { ability ->
            put(ABILITY_CODES[ability.ordinal], ability.labelRes)
        }
    }

    /**
     * Заменяет коды переменных читаемыми названиями: `2d6 + [str]` → `2d6 + Сила`.
     * Неизвестные переменные остаются как есть — так видна опечатка в формуле.
     *
     * @param names готовые подписи на языке интерфейса (код → название).
     */
    fun humanize(expression: String, names: Map<String, String>): String =
        VARIABLE_PATTERN.replace(expression) { match ->
            val code = match.groupValues[1].trim().lowercase()
            names[code] ?: match.value
        }

    private val VARIABLE_PATTERN = Regex("\\[([^]]*)]")

    /** Есть ли в строке выражение, а не просто число. */
    fun isFormula(expression: String): Boolean =
        expression.isNotBlank() && expression.trim().toIntOrNull() == null

    /** Содержит ли выражение кости — такие значения нельзя использовать как лимит ресурса. */
    fun hasDice(expression: String): Boolean =
        Regex("\\d*\\s*[dкD]\\s*\\d+").containsMatchIn(expression)

    /**
     * Вычисляет выражение для персонажа.
     *
     * @param mode влияет только на кости: максимум и криты.
     * @return результат или null, если выражение некорректно.
     */
    fun evaluate(
        expression: String,
        character: Character,
        mode: ComboRollMode = ComboRollMode.NORMAL,
        random: Random = Random.Default,
    ): FormulaResult? {
        if (expression.isBlank()) return null
        return runCatching { Parser(expression, character, mode, random).parse() }.getOrNull()
    }

    /** Итоговое число без детализации бросков — удобно для ресурсов. */
    fun evaluateValue(expression: String, character: Character): Int? =
        evaluate(expression, character)?.total

    /** Проверяет, что выражение разбирается, — для подсветки ошибок в форме. */
    fun isValid(expression: String): Boolean =
        expression.isBlank() || evaluate(expression, Character()) != null

    /**
     * Открыта ли перед курсором незакрытая квадратная скобка.
     * Используется, чтобы показывать список переменных ровно во время их набора.
     */
    fun isTypingVariable(text: String, caret: Int): Boolean {
        val open = text.lastIndexOf(VARIABLE_OPEN, caret - 1)
        if (open < 0) return false
        return !text.substring(open, caret).contains(VARIABLE_CLOSE)
    }

    /**
     * Подставляет выбранную переменную вместо незакрытой скобки перед курсором.
     *
     * @return новый текст и позиция курсора после вставки.
     */
    fun insertVariable(text: String, caret: Int, variable: FormulaVariable): Pair<String, Int> {
        val open = text.lastIndexOf(VARIABLE_OPEN, caret - 1)
        val prefixEnd = if (open >= 0) open else caret
        val inserted = "$VARIABLE_OPEN${variable.code}$VARIABLE_CLOSE"
        val result = text.substring(0, prefixEnd) + inserted + text.substring(caret)
        return result to (prefixEnd + inserted.length)
    }

    /**
     * Рекурсивный спуск: сложение и вычитание, затем умножение и целочисленное
     * деление, затем кости, числа, переменные и группировка.
     *
     * Параллельно собирается детализация: кости заменяются выпавшими значениями,
     * а переменные — числами персонажа.
     */
    private class Parser(
        expression: String,
        private val character: Character,
        private val mode: ComboRollMode,
        private val random: Random,
    ) {
        private val text = expression.lowercase()
        private var position = 0
        private val rolls = mutableListOf<Int>()

        /**
         * Разобранная часть выражения.
         *
         * @param compound состоит ли из нескольких слагаемых — такие части
         * берутся в скобки внутри умножения, чтобы детализация не вводила в заблуждение.
         */
        private data class Term(val value: Int, val detail: String, val compound: Boolean = false) {
            /** Запись для вставки в произведение или деление. */
            val grouped: String get() = if (compound) "($detail)" else detail
        }

        fun parse(): FormulaResult {
            val term = parseSum()
            skipSpaces()
            require(position == text.length) { "Лишние символы в формуле" }
            return FormulaResult(term.value, rolls.toList(), term.detail)
        }

        private fun parseSum(): Term {
            var result = parseProduct()
            while (true) {
                skipSpaces()
                val operator = peek()
                if (operator != '+' && operator != '-') return result
                position++
                val right = parseProduct()
                val value = if (operator == '+') result.value + right.value else result.value - right.value
                result = Term(
                    value = value,
                    detail = "${result.detail} $operator ${right.grouped}",
                    compound = true,
                )
            }
        }

        private fun parseProduct(): Term {
            var result = parseAtom()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '*', '×' -> {
                        position++
                        val right = parseAtom()
                        result = Term(
                            value = result.value * right.value,
                            detail = "${result.grouped} × ${right.grouped}",
                        )
                    }

                    '/' -> {
                        position++
                        val right = parseAtom()
                        require(right.value != 0) { "Деление на ноль" }
                        result = Term(
                            // Округление вниз — как в правилах D&D.
                            value = Math.floorDiv(result.value, right.value),
                            detail = "${result.grouped} / ${right.grouped}",
                        )
                    }

                    else -> return result
                }
            }
        }

        private fun parseAtom(): Term {
            skipSpaces()
            return when (val symbol = peek()) {
                '(' -> {
                    position++
                    val inner = parseSum()
                    skipSpaces()
                    require(peek() == ')') { "Не закрыта скобка" }
                    position++
                    inner
                }

                VARIABLE_OPEN -> parseVariable()
                '-' -> {
                    position++
                    val inner = parseAtom()
                    Term(-inner.value, "-${inner.grouped}")
                }

                '+' -> { position++; parseAtom() }
                in '0'..'9' -> parseNumberOrDice()
                null -> error("Формула не закончена")
                // Функции min(a, b) и max(a, b): нужны для КД средних доспехов («Ловкость, не больше 2»).
                'm' -> parseFunction()
                // Запись без количества: `d6` означает одну кость.
                in DICE_CHARS -> parseDice(count = 1)
                else -> error("Неизвестный символ: $symbol")
            }
        }

        /** Число либо кости: после числа может идти `d`/`к`. */
        private fun parseNumberOrDice(): Term {
            val value = parseNumber()
            skipSpaces()
            val next = peek()
            return if (next != null && next in DICE_CHARS) parseDice(value) else Term(value, value.toString())
        }

        private fun parseNumber(): Int {
            val start = position
            while (peek()?.isDigit() == true) position++
            return text.substring(start, position).toInt()
        }

        /** Бросает [count] костей; количество граней читается после `d`. */
        private fun parseDice(count: Int): Term {
            position++ // пропускаем 'd' или 'к'
            skipSpaces()
            require(peek()?.isDigit() == true) { "Не указано количество граней" }
            val sides = parseNumber()
            require(sides >= 2) { "Кость должна иметь минимум 2 грани" }
            require(count in 1..MAX_DICE_COUNT) { "Слишком много костей" }

            fun randomDice() = List(count) { random.nextInt(1, sides + 1) }
            fun maximumDice() = List(count) { sides }

            val result = when (mode) {
                ComboRollMode.NORMAL -> randomDice()
                ComboRollMode.MAXIMUM -> maximumDice()
                // Все кости пробрасываются дважды, модификаторы прибавляются один раз.
                ComboRollMode.CRITICAL_CLASSIC -> randomDice() + randomDice()
                // Максимум на основном наборе + ещё один обычный бросок всех костей.
                ComboRollMode.CRITICAL_HOMEBREW -> maximumDice() + randomDice()
            }
            rolls += result
            return Term(
                value = result.sum(),
                detail = result.joinToString(" + "),
                compound = result.size > 1,
            )
        }

        /** Читает `min(a, b)` или `max(a, b)`. */
        private fun parseFunction(): Term {
            val name = FUNCTIONS.firstOrNull { text.startsWith(it, position) } ?: error("Неизвестная функция")
            position += name.length
            skipSpaces()
            require(peek() == '(') { "Ожидалась скобка" }
            position++
            val first = parseSum()
            skipSpaces()
            require(peek() == ',') { "Ожидалась запятая" }
            position++
            val second = parseSum()
            skipSpaces()
            require(peek() == ')') { "Не закрыта скобка" }
            position++
            val value = if (name == "min") minOf(first.value, second.value) else maxOf(first.value, second.value)
            return Term(value, value.toString())
        }

        /** Читает `[name]` и возвращает значение переменной для персонажа. */
        private fun parseVariable(): Term {
            position++ // пропускаем '['
            val end = text.indexOf(VARIABLE_CLOSE, position)
            require(end > 0) { "Не закрыта квадратная скобка" }
            val name = text.substring(position, end).trim()
            position = end + 1
            val resolve = VARIABLES[name] ?: error("Неизвестная переменная: $name")
            val value = resolve(character)
            return Term(value, value.toString())
        }

        private fun peek(): Char? = text.getOrNull(position)

        private fun skipSpaces() {
            while (peek() == ' ') position++
        }

        private companion object {
            /** Латинская и русская запись костей. */
            val DICE_CHARS = charArrayOf('d', 'к')
            const val MAX_DICE_COUNT = 100
            val FUNCTIONS = listOf("min", "max")
        }
    }
}

/**
 * Формула инициативы по умолчанию: чистый модификатор Ловкости.
 *
 * Подставляется в поле редактирования, чтобы пользователь дописывал к ней своё
 * (например, `+ 5`), а не набирал выражение с нуля.
 */
const val DEFAULT_INITIATIVE_FORMULA: String = "[dex]"

/**
 * Бонус инициативы персонажа.
 *
 * Без собственной формулы — модификатор Ловкости, как и по правилам. Неразборчивое
 * выражение тоже даёт значение по умолчанию: экран характеристик должен оставаться
 * рабочим, даже если формула сломалась после ручной правки или импорта.
 */
val Character.initiativeBonus: Int
    get() = if (initiativeParts.isNotEmpty()) {
        // Как у КД и скорости: основа (Ловкость) плюс прибавки от черт и умений.
        statTotal(initiativeParts, DEFAULT_INITIATIVE_FORMULA)
    } else {
        initiativeFormula.takeIf { it.isNotBlank() }
            ?.let { StatFormula.evaluateValue(it, this) }
            ?: abilityModifierOf(AbilityType.DEXTERITY)
    }

/**
 * Составляющие инициативы для редактирования: у персонажа без составляющих старая формула
 * становится основой, чтобы ручная настройка (`[dex] + 5`) не потерялась.
 */
val Character.initiativePartsOrDefault: List<StatPart>
    get() = initiativeParts.ifEmpty {
        listOf(
            StatPart(
                name = "",
                formula = initiativeFormula.ifBlank { DEFAULT_INITIATIVE_FORMULA },
                kind = StatPartKind.BASE,
            ),
        )
    }

/** Значение составляющей (КД, скорости) для персонажа; неразборчивая формула даёт null. */
fun Character.statPartValue(part: StatPart): Int? = StatFormula.evaluateValue(part.formula, this)

/**
 * Итог по составляющим: наибольшая из включённых основ (или [defaultBase], если
 * все основы отключены) плюс сумма включённых прибавок.
 */
fun Character.statTotal(parts: List<StatPart>, defaultBase: String): Int {
    val active = parts.filter { it.enabled }
    val base = active.filter { it.kind == StatPartKind.BASE }
        .mapNotNull { statPartValue(it) }
        .maxOrNull()
        ?: StatFormula.evaluateValue(defaultBase, this)
        ?: 0
    val bonus = active.filter { it.kind == StatPartKind.BONUS }.sumOf { statPartValue(it) ?: 0 }
    return base + bonus
}

/** Включённая основа, которая сейчас применяется; null — действует основа по умолчанию. */
fun Character.activeStatBase(parts: List<StatPart>): StatPart? =
    parts.filter { it.enabled && it.kind == StatPartKind.BASE }
        .maxByOrNull { statPartValue(it) ?: Int.MIN_VALUE }

/**
 * Итоговый КД.
 *
 * Без составляющих — число [Character.armorClass], как раньше. Иначе наибольшая из включённых
 * основ (или 10 + Лов, если все основы отключены) плюс сумма включённых прибавок:
 * по правилам способы расчёта КД не складываются, а щит и бонусы — складываются.
 */
val Character.effectiveArmorClass: Int
    get() = if (armorClassParts.isEmpty()) armorClass else statTotal(armorClassParts, UNARMORED_AC_FORMULA)

/**
 * Включённая основа, которая сейчас даёт КД; null — действует 10 + Лов по умолчанию.
 * Нужна, чтобы показать, какая из нескольких основ в итоге применяется.
 */
val Character.activeArmorClassBase: StatPart?
    get() = activeStatBase(armorClassParts)

/**
 * Итоговая скорость: как и КД, складывается из основы (скорость расы) и прибавок
 * («Подвижный», «Движение без доспехов»). Без составляющих — число [Character.speed].
 */
val Character.effectiveSpeed: Int
    get() = if (speedParts.isEmpty()) speed else statTotal(speedParts, DEFAULT_SPEED_FORMULA).coerceAtLeast(0)

/**
 * Составляющие КД для добавления прибавки. У персонажа без составляющих основой становится
 * его число КД, а КД по умолчанию (10, без Ловкости) — основа «Без доспехов» (10 + Лов).
 */
val Character.armorClassPartsOrBase: List<StatPart>
    get() = armorClassParts.ifEmpty {
        listOf(
            if (armorClass == DEFAULT_ARMOR_CLASS) {
                unarmoredArmorClassPart()
            } else {
                StatPart(name = "", formula = armorClass.toString(), kind = StatPartKind.BASE)
            },
        )
    }

/**
 * Исправляет КД персонажа без доспеха: раньше мастер не добавлял основу «Без доспехов»,
 * и у персонажа без составляющих оставалось число 10 без Ловкости. Число, отличное
 * от 10, задано вручную или из листа и не трогается.
 */
fun Character.withUnarmoredBase(): Character =
    if (armorClassParts.isEmpty() && armorClass == DEFAULT_ARMOR_CLASS) {
        copy(armorClassParts = listOf(unarmoredArmorClassPart())).withSyncedArmorClass()
    } else {
        this
    }

/** Копия с числами [Character.armorClass] и [Character.speed], совпадающими с расчётными: их читают выгрузка и старый код. */
fun Character.withSyncedArmorClass(): Character {
    val ac = if (armorClassParts.isEmpty()) armorClass else effectiveArmorClass
    val walk = if (speedParts.isEmpty()) speed else effectiveSpeed
    return if (ac == armorClass && walk == speed) this else copy(armorClass = ac, speed = walk)
}

/**
 * Пересчитывает максимумы ресурсов, заданных формулами.
 *
 * Потраченное количество сохраняется: если было 2 из 3 и максимум вырос до 4,
 * станет 3 из 4. Вызывается при сохранении персонажа, после изменения уровня
 * или характеристик.
 */
fun Character.withRecalculatedResources(): Character {
    // КД тоже зависит от характеристик — синхронизируем его число заодно.
    val character = withSyncedArmorClass()
    if (character.resources.none { it.maximumFormula.isNotBlank() }) return character
    val updated = character.resources.map { resource ->
        val target = resource.maximumFormula
            .takeIf { it.isNotBlank() }
            ?.let { StatFormula.evaluateValue(it, character) }
            ?.coerceAtLeast(1)
            ?: return@map resource
        if (target == resource.maximum) return@map resource
        val spent = (resource.maximum - resource.current).coerceAtLeast(0)
        resource.copy(maximum = target, current = (target - spent).coerceIn(0, target))
    }
    return character.copy(resources = updated)
}
