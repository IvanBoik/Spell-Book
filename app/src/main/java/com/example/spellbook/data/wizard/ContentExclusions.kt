package com.example.spellbook.data.wizard

/*
 * Исключение заметок и ресурсов, которые мастер создания или повышения уровня предлагает
 * добавить персонажу: блок заметок целиком, отдельный абзац (умение) внутри блока или ресурс.
 *
 * Исключения запоминаются у персонажа (`Character.excludedContent`): при следующих повышениях
 * исключённый блок не создаётся заново, абзац не дописывается, а ресурс не добавляется и не
 * вызывает вопрос «вернуть?». Ключи строятся из названий, поэтому не зависят от id записей.
 *
 * Заметки выбранных вариантов (воззвания, приёмы, инфузии, стиль…) исключить нельзя: по ним
 * повышение уровня восстанавливает уже взятые варианты.
 */

/**
 * Абзац заметки, который будет добавлен.
 *
 * @param key ключ исключения абзаца ([ContentExclusions.paragraphKey]).
 * @param heading заголовок абзаца — как в заметке («Дополнительная атака (5 ур.)»).
 * @param excludedEarlier абзац исключён при прошлом создании или повышении.
 */
data class ProposedParagraph(
    val key: String,
    val heading: String,
    val text: String = "",
    val excludedEarlier: Boolean = false,
)

/**
 * Блок заметок, который будет создан или дополнен.
 *
 * @param locked блок выбранных вариантов — исключить нельзя.
 * @param existing блок уже есть у персонажа: абзацы допишутся в него.
 * @param excludedEarlier блок исключён раньше: пока его не включат обратно, абзацы не добавляются.
 */
data class ProposedBlock(
    val key: String,
    val title: String,
    val paragraphs: List<ProposedParagraph>,
    val locked: Boolean = false,
    val existing: Boolean = false,
    val excludedEarlier: Boolean = false,
)

/** Ресурс, который будет добавлен; [excludedEarlier] — исключён раньше, по умолчанию не добавляется. */
data class ProposedResource(
    val key: String,
    val name: String,
    val maximum: Int,
    val description: String = "",
    val excludedEarlier: Boolean = false,
    /**
     * Повышение уровня: ресурс, исключённый раньше, который по правилам уже был на прошлом уровне:
     * при включении обратно он добавляется с полным максимумом нового уровня.
     */
    internal val spec: com.example.spellbook.data.model.CharacterResource? = null,
)

/** Всё, что мастер предлагает записать в заметки и ресурсы, — для переключателей на итоговом шаге. */
data class ContentProposal(
    val blocks: List<ProposedBlock> = emptyList(),
    val resources: List<ProposedResource> = emptyList(),
) {
    val isEmpty: Boolean get() = blocks.isEmpty() && resources.isEmpty()

    /** Ключи, которые можно исключить: блоки без закрепления, их абзацы и ресурсы. */
    val excludableKeys: Set<String>
        get() = blocks.filter { !it.locked }.flatMap { block -> listOf(block.key) + block.paragraphs.map { it.key } }.toSet() +
            resources.map { it.key }
}

object ContentExclusions {

    private const val BLOCK = "block:"
    private const val PARAGRAPH = "para:"
    private const val RESOURCE = "res:"
    private val SPACES = Regex("\\s+")

    fun blockKey(title: String): String = BLOCK + norm(title)

    fun paragraphKey(blockTitle: String, heading: String): String = PARAGRAPH + norm(blockTitle) + "|" + norm(heading)

    /**
     * Ключ ресурса без уточнения в скобках — так же, как ресурсы сопоставляются при повышении
     * уровня: «Кости превосходства (к8)» и «(к10)» — один ресурс.
     */
    fun resourceKey(name: String): String = RESOURCE + norm(name.substringBefore(" ("))

    /** Абзац исключён сам или вместе со всем блоком. */
    fun isParagraphExcluded(blockTitle: String, heading: String, excluded: Set<String>): Boolean =
        blockKey(blockTitle) in excluded || paragraphKey(blockTitle, heading) in excluded

    fun isResourceExcluded(name: String, excluded: Set<String>): Boolean = resourceKey(name) in excluded

    fun toggle(set: Set<String>, key: String): Set<String> = if (key in set) set - key else set + key

    private fun norm(text: String): String = text.lowercase().replace('ё', 'е').trim().replace(SPACES, " ")
}
