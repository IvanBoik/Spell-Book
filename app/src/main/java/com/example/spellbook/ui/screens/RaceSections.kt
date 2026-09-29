package com.example.spellbook.ui.screens

import com.example.spellbook.util.HtmlUtils

/**
 * Раздел длинного описания (расы или класса): заголовок и относящийся к нему текст.
 *
 * @param title заголовок раздела; пустой у вступления перед первым заголовком.
 * @param body текст раздела в обычной разметке описания.
 * @param label надпись над блоком, отделяющая его от предыдущих.
 * @param collapsible сворачивается ли раздел. Несворачиваемые разделы — крупные части
 * статьи вроде «Классовых умений»: заголовок у них есть, но текст всегда открыт.
 */
data class DescriptionSection(
    val title: String,
    val body: String,
    val label: String = "",
    val collapsible: Boolean = true,
) {

    /** Вступление без собственного заголовка показывается всегда раскрытым. */
    val isIntro: Boolean get() = title.isEmpty()

    /** Раздел рисуется карточкой со сворачиванием. */
    val isCollapsible: Boolean get() = collapsible && !isIntro

    /**
     * Текст раздела, разрезанный по подзаголовкам `### `.
     *
     * Первый кусок — текст до первого подзаголовка (может быть пустым), далее по одному
     * на каждый подзаголовок. Несворачиваемые разделы рисуются именно так: каждое
     * умение становится отдельным элементом списка, и переход из таблицы попадает
     * точно в него, а не в начало километрового раздела «Классовые умения».
     *
     * Внутри врезок `:::` разрез не делается, иначе врезка осталась бы без закрывающего маркера.
     */
    fun chunks(): List<String> {
        val result = mutableListOf<String>()
        val current = mutableListOf<String>()
        var insideCallout = false
        body.lines().forEach { line ->
            val trimmed = line.trim()
            if (!insideCallout && HtmlUtils.isSubheading(trimmed)) {
                result += current.joinToString("\n")
                current.clear()
            }
            // У врезки без заголовка обе границы одинаковы, поэтому просто переключаемся:
            // иначе закрывающий маркер считался бы концом ещё не начатой врезки.
            when {
                HtmlUtils.isCalloutStart(trimmed) -> insideCallout = true
                HtmlUtils.isCalloutEnd(trimmed) -> insideCallout = !insideCallout
            }
            current += line
        }
        result += current.joinToString("\n")
        return result
    }
}

/**
 * Разбивает длинное описание на разделы.
 *
 * Заголовки двух уровней:
 * - `## ` — сворачиваемый блок (подкласс, инфузии, врезка с предысторией);
 * - `# ` — крупная часть статьи, которая на dnd.su не сворачивается
 *   (например, «Классовые умения»). Её текст идёт обычными абзацами.
 *
 * Сворачиваемый блок продолжается до следующего заголовка любого из двух уровней:
 * так «Классовые умения» после врезки с предысторией не попадают внутрь врезки.
 *
 * Подзаголовки (`### `) остаются внутри тела раздела: на сайте ими подписаны части
 * одного блока — например, отдельные таблицы внутри «Таблиц эльфов». Если считать
 * их разделами, единый блок рассыпается на десяток кусков.
 *
 * Сворачиваемый заголовок без собственного текста не выбрасывается, а становится надписью
 * над следующим разделом. Так на сайте оформлены разделители вроде «Подрасы из
 * Unearthed Arcana»: они отделяют неофициальные материалы от остальных.
 */
fun splitDescriptionSections(description: String): List<DescriptionSection> {
    if (description.isBlank()) return emptyList()

    val sections = mutableListOf<DescriptionSection>()
    var title = ""
    var collapsible = true
    // Заголовок-разделитель, ожидающий раздела, к которому он относится.
    var pendingLabel = ""
    val body = mutableListOf<String>()

    fun flush() {
        val text = body.joinToString("\n").trim()
        body.clear()
        if (text.isEmpty()) {
            // Заголовок без текста — это разделитель перед следующим блоком.
            if (title.isNotEmpty()) pendingLabel = title
            return
        }
        sections += DescriptionSection(
            title = title,
            body = text,
            label = pendingLabel,
            collapsible = collapsible,
        )
        pendingLabel = ""
    }

    description.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            HtmlUtils.isHeading(trimmed) -> {
                flush()
                title = HtmlUtils.headingText(trimmed)
                collapsible = true
            }

            HtmlUtils.isTopHeading(trimmed) -> {
                flush()
                title = HtmlUtils.topHeadingText(trimmed)
                collapsible = false
            }

            else -> body += line
        }
    }
    flush()

    return sections
}
