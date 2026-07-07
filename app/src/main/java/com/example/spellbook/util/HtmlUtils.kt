package com.example.spellbook.util

/**
 * Преобразование между простым текстом (как его вводит пользователь) и HTML,
 * который требует формат LSS в полях описания.
 */
object HtmlUtils {

    /** Оборачивает абзацы простого текста в `<p>...</p>` с экранированием. */
    fun plainToHtml(text: String): String {
        val paragraphs = text
            .replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return ""
        return paragraphs.joinToString(separator = "") { "<p>${escape(it)}</p>" }
    }

    /**
     * Внутренние ссылки FoundryVTT/LSS вида `@Compendium[ref]{подпись}` или `@UUID[ref]{подпись}`.
     * Из ссылки оставляем только видимую подпись (например, «Рывок»),
     * а ссылку без подписи — последний сегмент идентификатора как запасной вариант.
     */
    private val LINK_WITH_LABEL = Regex("@\\w+\\[[^\\]]*\\]\\{([^}]*)\\}")
    private val LINK_WITHOUT_LABEL = Regex("@\\w+\\[([^\\]]*)\\]")

    /**
     * Токен выделенного из ссылки слова: `[[ref Рывок]]`. Группа 1 — само слово.
     * Используется на экране деталей для подсветки таких слов.
     */
    val REF_TOKEN_REGEX = Regex("\\[\\[ref\\s+(.+?)\\]\\]")

    /** Грубо очищает HTML до простого текста: теги-блоки превращаются в переводы строк. */
    fun htmlToPlain(html: String): String {
        if (html.isBlank()) return ""
        val withBreaks = html
            .replace(Regex("(?i)</p\\s*>"), "\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</div\\s*>"), "\n")
        val noTags = withBreaks.replace(Regex("<[^>]*>"), "")
        val noLinks = noTags
            .replace(LINK_WITH_LABEL) { "[[ref ${it.groupValues[1]}]]" }
            .replace(LINK_WITHOUT_LABEL) { "[[ref ${it.groupValues[1].substringAfterLast('.')}]]" }
        return unescape(noLinks)
            .replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
