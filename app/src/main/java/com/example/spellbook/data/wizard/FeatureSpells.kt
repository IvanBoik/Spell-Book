package com.example.spellbook.data.wizard

/**
 * Заклинания, которые особенность расы, умение класса или архетипа даёт персонажу.
 *
 * Заклинания в текстах dnd.su записаны как «тишина [silence]», но упоминаются по разным
 * поводам: «вы знаете заговор…» — это заклинание персонажа, а «на него действует эффект
 * заклинания прыжок», «если на вас накладывают заклинание починка», «например, заклинание
 * огненный шар» — нет. Поэтому заклинание считается полученным, только если предложение
 * говорит, что персонаж его знает, изучает или может накладывать.
 */
object FeatureSpells {

    /** Глаголы получения: «знаете заговор», «изучаете заклинание», «можете накладывать», «чтобы наложить». */
    private val GRANT = Regex(
        "(?:знаете|узна[её]те|изучаете|получаете|выучиваете|учите|подготавливаете|добавляете|известн\\S*)" +
            "\\s+(?:\\S+\\s+){0,3}?(?:заговор|заклинани)|" +
            "(?:можете|чтобы)\\s+(?:\\S+\\s+){0,5}?(?:накладывать|наложить|сотворить|для\\s+накладывания)",
    )

    /** «как заклинание 2-го уровня», «как заклинание 3 уровня» — указание круга накладывания. */
    private val CAST_AT_LEVEL = Regex("как заклинание\\s+\\d+(?:-\\S+)?\\s+уровн\\S*")

    /** Упоминания заклинаний, которые ничего не дают персонажу. */
    private val NOT_GRANT = listOf(
        "на вас наклады", "на вас налож", "например", "эффект заклинания", "эффекты заклинания",
        "эффектом заклинания", "как заклинание",
        "заменяет", "блок статистики", "параметры", "если на ", "когда вы наклады", "при накладывании",
        "против заклинания", "от заклинани", "заклинанием ",
    )

    /**
     * Выбор одного из названных при создании: «один из следующих заговоров по вашему выбору: …».
     * «Можете наложить одно из следующих заклинаний» без слова «выбор» — выбор при каждом
     * использовании («Странник во снах»): даются все.
     */
    private val CHOICE = Regex("по\\s+вашему\\s+выбору|на\\s+ваш\\s+выбор|на\\s+выбор|на\\s+свой\\s+выбор")

    /**
     * Разбор текста особенности.
     *
     * @param minLevelOf уровень, с которого доступно заклинание из предложения
     * («Начиная с 3-го уровня…»); по умолчанию — уровень самой особенности.
     */
    fun parse(
        text: String,
        id: String,
        source: ChoiceSource,
        title: String,
        level: Int = 1,
    ): Parsed {
        val granted = mutableListOf<GrantedSpell>()
        val choices = mutableListOf<ChoiceSpec>()
        FeatureRules.sentences(text, withBullets = true)
            .filter { !it.startsWith("|") }
            .forEachIndexed { index, sentence ->
                // «…как заклинание 2-го уровня» — лишь круг накладывания (тифлинг: адское возмездие),
                // а не «действует как заклинание X», поэтому не считается признаком чужого заклинания.
                val lower = sentence.lowercase().replace(CAST_AT_LEVEL, " ")
                if (!GRANT.containsMatchIn(lower) || NOT_GRANT.any { lower.contains(it) }) return@forEachIndexed
                val names = MarkupText.spellNameCandidates(sentence)
                if (names.isEmpty()) return@forEachIndexed
                // «Достигнув 3-го уровня…», «Начиная с 5-го уровня…», «Когда вы достигаете 3 уровня…».
                val minLevel = Regex("(?:^|\\s)(?:достиг\\S*|с|после)\\s+(\\d+)(?:-\\S+)?\\s+уров").find(lower)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: level
                if (CHOICE.containsMatchIn(lower) && names.size > 1) {
                    val isCantrip = lower.contains("заговор")
                    choices += ChoiceSpec(
                        id = "$id.spell.$index",
                        source = source,
                        title = if (isCantrip) "$title: заговор" else "$title: заклинание",
                        kind = if (isCantrip) ChoiceKind.CANTRIP else ChoiceKind.SPELL,
                        count = 1,
                        sourceText = sentence,
                        spellFilter = SpellFilter(minLevel = 0, maxLevel = MAX_SPELL_LEVEL, names = names),
                    )
                } else {
                    granted += names.map { GrantedSpell(it, minLevel) }
                }
            }
        return Parsed(granted.distinctBy { it.name }, choices)
    }

    data class Parsed(val spells: List<GrantedSpell>, val choices: List<ChoiceSpec>)

    /**
     * Даёт ли текст умения персонажу заклинания, которые всегда при нём: таблицу заклинаний
     * по уровню класса («Уровень изобретателя | Заклинания») или по варианту, либо предложение
     * «вы знаете заговор…», «можете накладывать…» с названием заклинания. Расширенный список
     * колдуна («Уровень заклинания | Заклинания») — лишь выбор, поэтому не считается.
     *
     * Нужно выгрузке в лист LSS: такие умения идут в блок «Атаки и заклинания».
     */
    fun grantsSpells(text: String): Boolean {
        val hasTable = MarkupText.tables(text).any { rows ->
            val kind = tableKind(rows)
            kind == TableKind.CLASS_LEVEL || kind == TableKind.VARIANT
        }
        if (hasTable) return true
        val parsed = parse(text, id = "grants", source = ChoiceSource.CLASS, title = "")
        return parsed.spells.isNotEmpty() || parsed.choices.isNotEmpty()
    }

    // region Таблицы заклинаний архетипов

    /** Вид таблицы заклинаний архетипа по первой колонке. */
    enum class TableKind {
        /** «Уровень жреца | Заклинания»: заклинания даются по уровню класса. */
        CLASS_LEVEL,

        /** «Уровень заклинания | Заклинания»: расширенный список для выбора (колдун). */
        SPELL_LEVEL,

        /** «Склонность | Заклинание»: одна строка на выбор (божественная душа). */
        VARIANT,
    }

    /**
     * Таблица заклинаний в умении архетипа.
     *
     * @param label подпись варианта перед таблицей (`**Болото**` у круга земли); пусто — нет.
     * @param featureLevel уровень умения, в котором находится таблица.
     */
    data class SpellTable(
        val label: String,
        val rows: List<List<String>>,
        val kind: TableKind,
        val featureLevel: Int,
    )

    /** Вид двухколоночной таблицы с заклинаниями во второй колонке; null — не таблица заклинаний. */
    private fun tableKind(rows: List<List<String>>): TableKind? {
        val header = rows.firstOrNull()?.map { MarkupText.plain(it).lowercase() }.orEmpty()
        if (header.size != 2 || !header[1].contains("заклинани")) return null
        return when {
            header[0].contains("уровень заклинани") -> TableKind.SPELL_LEVEL
            header[0].contains("уровень") -> TableKind.CLASS_LEVEL
            header[0].contains("заклинани") || header[0].isBlank() -> null
            else -> TableKind.VARIANT
        }
    }

    /** Заклинания строк таблицы до уровня включительно. */
    fun tableSpells(rows: List<List<String>>, maxLevel: Int): List<String> =
        rows.drop(1)
            .filter { row -> (Regex("\\d+").find(row.first())?.value?.toIntOrNull() ?: Int.MAX_VALUE) <= maxLevel }
            .flatMap { MarkupText.spellNameCandidates(it.getOrElse(1) { "" }) }

    /**
     * Таблицы заклинаний архетипа, сгруппированные по вариантам.
     *
     * Обычно таблица одна. У круга земли их несколько — по одной на каждую местность,
     * перед каждой строка `**Болото**`; тогда это выбор местности.
     */
    fun subclassTables(features: List<RuleFeature>): List<SpellTable> {
        val tables = rawTables(features)
        // Подпись — признак варианта, только когда подписанных таблиц несколько (местности круга земли).
        // Одиночная «**Заклинания домена жизни**» — просто заголовок.
        val labelled = tables.count { it.label.isNotBlank() && it.kind == TableKind.CLASS_LEVEL }
        return if (labelled >= 2) tables else tables.map { it.copy(label = "") }
    }

    private fun rawTables(features: List<RuleFeature>): List<SpellTable> =
        features.flatMap { feature ->
            val result = mutableListOf<SpellTable>()
            var label = ""
            val lines = feature.text.lines()
            var index = 0
            while (index < lines.size) {
                val line = lines[index].trim()
                Regex("^\\*\\*([^*]+)\\*\\*$").matchEntire(line)?.let { label = it.groupValues[1].trim() }
                if (line.startsWith("|")) {
                    val block = mutableListOf<String>()
                    while (index < lines.size && lines[index].trim().startsWith("|")) {
                        block += lines[index]
                        index++
                    }
                    val rows = MarkupText.tables(block.joinToString("\n")).firstOrNull().orEmpty()
                    tableKind(rows)?.let { kind -> result += SpellTable(label, rows, kind, feature.level) }
                    label = ""
                    continue
                }
                index++
            }
            result
        }

    // endregion

    const val MAX_SPELL_LEVEL = 9
}
