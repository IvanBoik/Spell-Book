package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.Spell

/**
 * Проверка требований варианта выбора: «5-й уровень колдуна», «умение «Договор клинка»»,
 * «заговор мистический заряд [eldritch blast]».
 *
 * Требования, которые разобрать не удалось (например, «способность колдуна к проклятию»),
 * не блокируют выбор — пользователь видит их текст на карточке и решает сам.
 */
object OptionRequirements {

    private val PACT = Regex("«\\s*(договор\\s+[^»]+)»", RegexOption.IGNORE_CASE)
    private val CANTRIP = Regex("заговор\\*?\\s+([^\\[,]+?)\\s*(?:\\[([^\\]]+)])?(?:,|$)", RegexOption.IGNORE_CASE)

    /** Почему вариант пока недоступен; пусто — доступен. */
    fun unmet(option: ChoiceOption, draft: CharacterDraft, context: WizardContext, spells: List<Spell>): List<Unmet> {
        val requirement = option.requirement.trim()
        if (requirement.isEmpty()) return emptyList()
        val result = mutableListOf<Unmet>()
        option.requiredLevel?.takeIf { it > draft.level }?.let { result += Unmet.Level(it) }

        PACT.find(requirement)?.groupValues?.get(1)?.trim()?.let { pact ->
            if (!hasPact(pact, draft, context)) result += Unmet.Feature(pact.replaceFirstChar { it.uppercase() })
        }

        CANTRIP.find(requirement)?.let { match ->
            val name = match.groupValues[1].trim()
            val english = match.groupValues[2].trim()
            val known = spells.filter { it.id in knownSpellIds(draft, context) }
            val has = known.any { spell ->
                MarkupText.matchesSpellName(name, spell.name) ||
                    (english.isNotEmpty() && spell.name.contains(english, ignoreCase = true))
            }
            if (!has) result += Unmet.Spell(name)
        }
        return result
    }

    /** Выбран ли предмет договора с таким названием («Договор клинка»). */
    private fun hasPact(pact: String, draft: CharacterDraft, context: WizardContext): Boolean {
        val wanted = pact.lowercase().replace('ё', 'е')
        return context.choices
            .filter { it.kind == ChoiceKind.OPTION }
            .flatMap { choice ->
                draft.selections[choice.id].orEmpty().mapNotNull { id -> choice.options.firstOrNull { it.id == id } }
            }
            .any { it.title.lowercase().replace('ё', 'е').contains(wanted) }
    }

    /** Заговоры и заклинания, уже выбранные в мастере (класс и выборы рас/черт/умений). */
    private fun knownSpellIds(draft: CharacterDraft, context: WizardContext): Set<String> =
        draft.cantripIds + draft.spellIds + context.choices
            .filter { it.kind == ChoiceKind.CANTRIP || it.kind == ChoiceKind.SPELL }
            .flatMap { draft.selections[it.id].orEmpty() }

    /** Невыполненное требование: интерфейс показывает его текстом. */
    sealed interface Unmet {
        data class Level(val level: Int) : Unmet
        data class Feature(val name: String) : Unmet
        data class Spell(val name: String) : Unmet
    }
}
