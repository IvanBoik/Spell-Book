package com.example.spellbook.data

import com.example.spellbook.data.model.Components
import com.example.spellbook.data.model.Materials
import com.example.spellbook.data.model.Spell
import com.example.spellbook.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Парсер страницы заклинания с сайта dnd.su.
 *
 * Разбирает блок `<ul class="params">`, где параметры лежат в `<li>`:
 * первый — «уровень, школа», далее пары `<strong>Метка:</strong> значение`,
 * а последний `<li class="subsection desc">` содержит описание.
 * Названия сопоставляются с кодами формата LSS через [SpellOptions].
 */
object DndSuSpellParser {

    /** Разбирает полный HTML страницы dnd.su в доменную модель заклинания. */
    fun parse(html: String): Spell {
        val doc = Jsoup.parse(html)
        val params = doc.selectFirst("ul.params")
            ?: throw IllegalArgumentException("Не найден блок параметров заклинания")

        val typeLine = params.selectFirst("li.size-type-alignment")?.text().orEmpty()
        val (level, school, schoolNote) = parseLevelAndSchool(typeLine)
        val ritual = isRitual(typeLine)
        val fields = parseLabeledFields(doc)

        val (activationType, activationCost) = parseActivation(fields[LABEL_CASTING_TIME].orEmpty())
        val (rangeValue, rangeUnits) = parseRange(fields[LABEL_RANGE].orEmpty())
        val (durationValue, durationUnits, concentration) = parseDuration(fields[LABEL_DURATION].orEmpty())
        val componentsRaw = fields[LABEL_COMPONENTS].orEmpty()
        val components = parseComponents(componentsRaw, concentration, ritual)
        val materials = parseMaterials(componentsRaw)
        val classes = parseClasses(fields[LABEL_CLASSES].orEmpty())
        val subclasses = parseSubclasses(fields[LABEL_SUBCLASSES].orEmpty())

        return Spell(
            name = parseName(doc),
            description = parseDescription(doc),
            source = parseSource(doc),
            level = level,
            school = school,
            schoolNote = schoolNote,
            activationType = activationType,
            activationCost = activationCost,
            durationValue = durationValue,
            durationUnits = durationUnits,
            rangeValue = rangeValue,
            rangeUnits = rangeUnits,
            components = components,
            materials = materials,
            classes = classes,
            subclasses = subclasses,
        )
    }

    // region Отдельные поля

    /** Название берём из части до «[», например «Огненный снаряд [Fire bolt]» → «Огненный снаряд». */
    private fun parseName(doc: Document): String {
        val raw = doc.selectFirst("h2.card-title span[data-copy]")?.text()
            ?: doc.selectFirst("h2.card-title")?.text()
            ?: ""
        return raw.substringBefore('[').trim().ifBlank { raw.trim() }
    }

    /**
     * Описание: содержимое `div[itemprop=description]`. Ссылки-подсказки dnd.su
     * (`<span tooltip-for=...>слово</span>`) превращаем в токены `[[ref слово]]`,
     * чтобы такие слова подсвечивались на экране деталей.
     */
    private fun parseDescription(doc: Document): String {
        val desc = doc.selectFirst("[itemprop=description]") ?: return ""
        desc.select("span[tooltip-for]").forEach { span ->
            span.text("[[ref ${span.text()}]]")
        }
        // Комментарии маскота сайта — не часть заклинания, поэтому в описание не попадают.
        return HtmlUtils.removeMascotNotes(HtmlUtils.htmlToPlain(desc.html()))
    }

    /**
     * «2 уровень, преобразование» / «Заговор, воплощение» / «1 уровень, прорицание (ритуал)»
     * → (level, schoolCode). Пометка «(ритуал)» отбрасывается при определении школы.
     */
    private fun parseLevelAndSchool(text: String): Triple<Int, String, String> {
        val parts = text.split(',').map { it.trim() }
        val levelPart = parts.getOrNull(0).orEmpty().lowercase()
        val level = when {
            levelPart.startsWith("заговор") -> 0
            else -> Regex("\\d+").find(levelPart)?.value?.toIntOrNull() ?: 0
        }
        val schoolPart = parts.getOrNull(1).orEmpty()
        // Само название школы — до скобки: «воплощение (дюнамантия)» → «воплощение».
        val schoolName = schoolPart.substringBefore('(').trim()
        // Сравниваем с каноническими именами источника: они не зависят от языка интерфейса.
        val school = SpellOptions.schools.firstOrNull {
            it.canonical.equals(schoolName, ignoreCase = true)
        }?.code ?: "evo"
        // В скобках бывает либо пометка ритуала, либо уточнение вроде «дюнамантия: хронургия».
        val note = schoolPart.substringAfter('(', "").substringBeforeLast(')', "").trim()
        val schoolNote = note.takeUnless { it.isEmpty() || it.contains(RITUAL_MARK, ignoreCase = true) }.orEmpty()
        return Triple(level, school, schoolNote)
    }

    /** «1 действие» / «1 бонусное действие» / «1 минута» / «10 минут» / «Реакция» → код и стоимость. */
    private fun parseActivation(text: String): Pair<String, Int?> {
        val lower = text.lowercase()
        val cost = Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: 1
        val type = when {
            lower.contains("бонусн") -> "bonus"
            lower.contains("реакц") -> "reaction"
            lower.contains("действ") -> "action"
            lower.contains("минут") -> "minute"
            lower.contains("час") -> "hour"
            lower.contains("день") || lower.contains("дн") -> "day"
            else -> "special"
        }
        return type to cost
    }

    /** «120 футов» / «Касание» / «На себя» / «Особая» → (value, unitsCode). */
    private fun parseRange(text: String): Pair<Int?, String> {
        val lower = text.lowercase()
        return when {
            lower.contains("касан") -> null to "touch"
            lower.contains("на себя") -> null to "self"
            lower.contains("особ") -> null to "spec"
            lower.contains("миля") || lower.contains("мил") -> Regex("\\d+").find(lower)?.value?.toIntOrNull() to "mi"
            lower.contains("фут") -> Regex("\\d+").find(lower)?.value?.toIntOrNull() to "ft"
            else -> null to "self"
        }
    }

    /**
     * «Мгновенная» / «8 часов» / «Концентрация, вплоть до 1 минуты» → (value, unitsCode, concentration).
     */
    private fun parseDuration(text: String): Triple<Int?, String, Boolean> {
        val lower = text.lowercase()
        val concentration = lower.contains("концентрац")
        val value = Regex("\\d+").find(lower)?.value?.toIntOrNull()
        val units = when {
            lower.contains("мгновен") -> "inst"
            lower.contains("раунд") -> "round"
            lower.contains("минут") -> "minute"
            lower.contains("час") -> "hour"
            lower.contains("день") || lower.contains("дн") -> "day"
            lower.contains("постоян") -> "perm"
            lower.contains("особ") -> "spec"
            else -> "inst"
        }
        return Triple(value, units, concentration)
    }

    /** «В, С, М (материалы...)» → флаги компонентов. Материалы игнорируем при разборе букв. */
    private fun parseComponents(raw: String, concentration: Boolean, ritual: Boolean): Components {
        val letters = raw.substringBefore('(').split(',').map { it.trim() }
        return Components(
            vocal = letters.any { it.equals("В", ignoreCase = true) },
            somatic = letters.any { it.equals("С", ignoreCase = true) },
            material = letters.any { it.equals("М", ignoreCase = true) },
            ritual = ritual,
            concentration = concentration,
            value = raw.trim(),
        )
    }

    /** Текст материальных компонентов — из скобок в строке компонентов. */
    private fun parseMaterials(raw: String): Materials {
        val inParens = raw.substringAfter('(', "").substringBeforeLast(')', "").trim()
        return Materials(value = inParens)
    }

    /** «волшебник, изобретатель, чародей» → коды классов LSS (сноски-источники отбрасываем). */
    private fun parseClasses(text: String): List<String> {
        return text.split(',')
            .map { it.trim() }
            .mapNotNull { name ->
                // Отсекаем возможные буквенные пометки-источники после названия (TCE, XGE и т.п.).
                val clean = name.substringBefore('(').trim().takeWhile { it.isLetter() || it == ' ' || it == '-' }.trim()
                SpellOptions.classes.firstOrNull { it.canonical.equals(clean, ignoreCase = true) }?.code
            }
            .distinct()
    }

    /**
     * Подклассы идут списком вида «домен магии (жрец), круг земли (друид)».
     * Подписи сохраняем как есть: справочника подклассов в формате LSS нет.
     * Запятая внутри скобок не встречается, поэтому достаточно простого разбиения.
     */
    private fun parseSubclasses(text: String): List<String> = text
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    /** Ритуал определяется по наличию слова «ритуал» в блоке параметров. */
    private fun isRitual(paramsText: String): Boolean = paramsText.contains(RITUAL_MARK, ignoreCase = true)

    /**
     * Книга-источник из заголовка карточки: `<span class="source-plaque" title="...">XGE</span>`.
     * Берём первую плашку — она соответствует книге, где заклинание появилось впервые.
     */
    private fun parseSource(doc: Document): String {
        val plaque = doc.selectFirst("h2.card-title .source-plaque") ?: return SOURCE_DND_SU
        val code = plaque.text().trim()
        val title = plaque.attr("title").trim()
        return when {
            code.isNotEmpty() -> code
            title.isNotEmpty() -> title
            else -> SOURCE_DND_SU
        }
    }

    /**
     * Собирает пары «метка → значение» из `<li><strong>Метка:</strong> значение</li>`.
     */
    private fun parseLabeledFields(doc: Document): Map<String, String> {
        val result = mutableMapOf<String, String>()
        doc.select("ul.params > li").forEach { li ->
            val strong = li.selectFirst("strong") ?: return@forEach
            val label = strong.text().trim().trimEnd(':').trim()
            // Значение — текст всего li без метки.
            val value = li.text().removePrefix(strong.text()).trim()
            if (label.isNotEmpty()) result[label] = value
        }
        return result
    }

    // endregion

    private const val SOURCE_DND_SU = "dnd.su"
    private const val LABEL_CASTING_TIME = "Время накладывания"
    private const val LABEL_RANGE = "Дистанция"
    private const val LABEL_COMPONENTS = "Компоненты"
    private const val LABEL_DURATION = "Длительность"
    private const val LABEL_CLASSES = "Классы"
    private const val LABEL_SUBCLASSES = "Подклассы"

    /** Пометка ритуала в строке типа: её не путаем с уточнением школы. */
    private const val RITUAL_MARK = "ритуал"
}
