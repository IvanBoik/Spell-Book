package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.MagicItem
import com.example.spellbook.util.HtmlUtils

/**
 * Инфузия изобретателя «Копия магического предмета».
 *
 * Её можно изучить несколько раз — каждый раз с новым предметом из таблиц «Копируемые предметы
 * (N-й уровень изобретателя)». Поэтому в выборе инфузий она разворачивается в отдельные варианты-
 * копии, по одному на предмет: id `<id инфузии>#<предмет>`, название «Копия магического предмета:
 * Сумка хранения», требование — уровень изобретателя из названия таблицы. Каждая копия занимает
 * одно место «Известных инфузий», а заметка и восстановление по заметкам работают как у обычного
 * варианта — по названию копии.
 *
 * Обычные (common) предметы, которые текст тоже разрешает копировать, в выбор не входят —
 * только предметы таблиц.
 */
object ReplicateInfusion {

    /** Предмет из таблицы инфузии. */
    data class Item(val name: String, val english: String, val level: Int, val attunement: Boolean)

    private const val TITLE_PREFIX = "копия магического предмета"
    private const val ID_SEPARATOR = '#'

    /** «Копируемые предметы (6-й уровень изобретателя)». */
    private val TABLE_TITLE = Regex("копируемые предметы\\s*\\((\\d+)", RegexOption.IGNORE_CASE)

    /** «Сумка хранения [bag of holding]». */
    private val NAME_WITH_ENGLISH = Regex("^(.*?)\\s*\\[([^\\]]+)]\\s*$")

    fun isReplicate(option: ChoiceOption): Boolean = norm(option.title).startsWith(TITLE_PREFIX)

    /** Вариант — копия «Копии магического предмета» (развёрнутый по предмету). */
    fun isCopy(optionId: String): Boolean = ID_SEPARATOR in optionId

    /** Предметы таблиц инфузии по порядку. */
    fun items(text: String): List<Item> {
        val result = mutableListOf<Item>()
        var level: Int? = null
        text.lineSequence().map { it.trim() }.filter { HtmlUtils.isTableRow(it) && !HtmlUtils.isTableSeparator(it) }.forEach { line ->
            val cells = HtmlUtils.parseTableRow(line).map { it.trim() }
            val first = MarkupText.plain(cells.firstOrNull().orEmpty())
            TABLE_TITLE.find(first)?.let {
                level = it.groupValues[1].toIntOrNull()
                return@forEach
            }
            val current = level ?: return@forEach
            if (cells.size < 2 || norm(first).startsWith("магический предмет")) return@forEach
            val match = NAME_WITH_ENGLISH.find(first)
            val name = (match?.groupValues?.get(1) ?: first).trim()
            if (name.isEmpty()) return@forEach
            result += Item(
                name = name,
                english = match?.groupValues?.get(2)?.trim().orEmpty(),
                level = current,
                attunement = norm(MarkupText.plain(cells[1])).startsWith("да"),
            )
        }
        return result.distinctBy { norm(it.name) }
    }

    /** Текст инфузии без таблиц предметов — правило, общее для всех копий. */
    fun ruleText(text: String): String = text.lines()
        .filterNot { HtmlUtils.isTableRow(it.trim()) }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /**
     * Разворачивает инфузию «Копия магического предмета» в варианты-копии; остальные варианты
     * остаются как есть. Результат кэшируется: выборы пересчитываются при каждом изменении черновика.
     */
    fun expand(options: List<ChoiceOption>, magicItems: List<MagicItem>): List<ChoiceOption> {
        if (options.none(::isReplicate)) return options
        return options.flatMap { option ->
            if (!isReplicate(option)) return@flatMap listOf(option)
            val key = CacheKey(option, System.identityHashCode(magicItems), magicItems.size)
            cache.getOrPut(key) { copies(option, magicItems) }
        }
    }

    private data class CacheKey(val option: ChoiceOption, val itemsIdentity: Int, val itemsSize: Int)

    /** Развёрнутые копии по варианту и библиотеке предметов: выборы пересчитываются на каждое изменение. */
    private val cache = java.util.concurrent.ConcurrentHashMap<CacheKey, List<ChoiceOption>>()

    private fun copies(option: ChoiceOption, magicItems: List<MagicItem>): List<ChoiceOption> {
        val items = items(option.text)
        if (items.isEmpty()) return listOf(option)
        val bySlug = magicItems.groupBy { slugOf(it) }
        val byName = magicItems.groupBy { norm(it.name) }
        val groupTitle = option.title
        val rule = ruleText(option.text)
        return items.map { item ->
            val library = item.english.takeIf { it.isNotBlank() }?.let { bySlug[englishSlug(it)]?.firstOrNull() }
                ?: byName[norm(item.name)]?.firstOrNull()
            ChoiceOption(
                id = "${option.id}$ID_SEPARATOR${itemKey(item)}",
                // Название — из таблицы инфузии, а не из библиотеки предметов: по нему взятые копии
                // восстанавливаются из заметок, и оно не должно зависеть от состава библиотеки.
                title = "$groupTitle: ${item.name}",
                text = copyText(item, library),
                requiredLevel = item.level,
                requirement = "${item.level}-й уровень изобретателя",
                extended = option.extended,
                group = groupTitle,
                groupText = rule,
            )
        }
    }

    /**
     * Описание копии: название предмета с английским в скобках и строка «вид, редкость, настройка».
     *
     * «Сумка хранения [bag of holding]» в текстах приложения — ссылка на окно предмета (в карточке
     * варианта и в заметке), поэтому полное описание предмета сюда не копируется: оно в окне.
     * Заголовок копии не меняется — по нему взятые копии восстанавливаются из заметок.
     */
    private fun copyText(item: Item, library: MagicItem?): String = buildString {
        val attunement = if (item.attunement) "требуется настройка" else "настройка не требуется"
        val name = library?.name?.takeIf { it.isNotBlank() } ?: item.name
        val english = item.english.ifBlank { library?.let(::englishOf).orEmpty() }
        append("Предмет: ").append(name)
        if (english.isNotBlank()) append(" [").append(english).append("]")
        append("\n\n")
        val category = library?.category?.trim().orEmpty()
        append("*").append(category.ifBlank { attunement }).append("*")
        if (category.isNotBlank() && !category.contains("настройк", ignoreCase = true)) {
            append(" · ").append(attunement)
        }
    }

    /** Английское название по ссылке dnd.su: `bag_of_holding` → `bag of holding`. */
    private fun englishOf(item: MagicItem): String = slugOf(item).replace('_', ' ').trim()

    private fun itemKey(item: Item): String =
        (item.english.ifBlank { item.name }).lowercase().replace('ё', 'е').trim().replace(Regex("[^a-zа-я0-9]+"), "_").trim('_')

    /** Ключ предмета библиотеки по ссылке dnd.su: `…/items/227-bag_of_holding/` → `bag_of_holding`. */
    private fun slugOf(item: MagicItem): String =
        item.source.trimEnd('/').substringAfterLast('/').substringAfter('-').lowercase().trim('_')

    private fun englishSlug(english: String): String =
        english.lowercase().replace('’', '\'').replace("'", "").replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private fun norm(text: String): String = text.lowercase().replace('ё', 'е').trim()
}
