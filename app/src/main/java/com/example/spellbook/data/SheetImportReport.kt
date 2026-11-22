package com.example.spellbook.data

/**
 * Итог загрузки листа LSS: что не удалось перенести точно.
 *
 * @param unresolvedSpells сколько заклинаний листа не нашлось в библиотеке (их ссылки — в заметке).
 * @param unknownFeats черты, которых не было в библиотеке: они добавлены, но могут оказаться
 * ошибкой разбора текста блока «Черты».
 */
data class SheetImportReport(
    val characterId: String,
    val characterName: String,
    val unresolvedSpells: Int = 0,
    val unknownFeats: List<String> = emptyList(),
) {
    /** Есть ли о чём предупредить пользователя. */
    val hasWarnings: Boolean get() = unresolvedSpells > 0 || unknownFeats.isNotEmpty()
}
