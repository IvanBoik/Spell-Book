package com.example.spellbook.ui.screens

import com.example.spellbook.util.HtmlUtils

/**
 * Раздел описания расы: заголовок и относящийся к нему текст.
 *
 * @param title заголовок раздела; пустой у вступления перед первым заголовком.
 * @param body текст раздела в обычной разметке описания.
 * @param label надпись над блоком, отделяющая его от предыдущих.
 */
data class RaceSection(
    val title: String,
    val body: String,
    val label: String = "",
) {

    /** Вступление без собственного заголовка показывается всегда раскрытым. */
    val isIntro: Boolean get() = title.isEmpty()
}

/**
 * Разбивает описание расы на разделы по заголовкам `## `.
 *
 * На dnd.su страница расы длинная и уже разбита на разделы, а сверху сайт рисует
 * меню-навигацию по ним. В приложении меню и сворачивание строятся по этим же
 * заголовкам, поэтому текст достаточно разложить по разделам.
 *
 * Подзаголовки (`### `) остаются внутри тела раздела: на сайте ими подписаны части
 * одного блока — например, отдельные таблицы внутри «Таблиц эльфов». Если считать
 * их разделами, единый блок рассыпается на десяток кусков.
 *
 * Заголовок без собственного текста не выбрасывается, а становится надписью над
 * следующим разделом. Так на сайте оформлены разделители вроде «Подрасы из
 * Unearthed Arcana»: они отделяют неофициальные подрасы от остальных.
 */
fun splitRaceSections(description: String): List<RaceSection> {
    if (description.isBlank()) return emptyList()

    val sections = mutableListOf<RaceSection>()
    var title = ""
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
        sections += RaceSection(title = title, body = text, label = pendingLabel)
        pendingLabel = ""
    }

    description.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (HtmlUtils.isHeading(trimmed)) {
            flush()
            title = HtmlUtils.headingText(trimmed)
        } else {
            body += line
        }
    }
    flush()

    return sections
}
