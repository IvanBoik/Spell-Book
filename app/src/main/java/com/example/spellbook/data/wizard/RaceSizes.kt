package com.example.spellbook.data.wizard

import com.example.spellbook.data.model.Character
import com.example.spellbook.data.model.CreatureSize
import com.example.spellbook.data.model.Race

/** Размер персонажа по расе: «Ваш размер — Маленький или Средний» в тексте расы или разновидности. */
object RaceSizes {

    /** Варианты размера; разновидность с собственным размером важнее расы. Пусто — размер не указан. */
    fun options(race: RaceRules?, subrace: SubraceRules?): List<CreatureSize> =
        CreatureSize.parse(subrace?.grants?.size?.takeIf { it.isNotBlank() } ?: race?.grants?.size.orEmpty())

    /** Варианты размера для собранного мастером черновика. */
    fun options(context: WizardContext): List<CreatureSize> = options(context.race, context.subrace)

    /**
     * Размер нового персонажа: единственный вариант расы или выбранный в мастере.
     * Пусто, если раса даёт выбор, а он не сделан.
     */
    fun resolve(draft: CharacterDraft, context: WizardContext): String {
        val options = options(context)
        return when (options.size) {
            0 -> ""
            1 -> options.single().code
            else -> draft.size.takeIf { code -> options.any { it.code == code } }.orEmpty()
        }
    }

    /**
     * Варианты размера уже созданного персонажа: раса и разновидность ищутся в библиотеке
     * по сохранённым у персонажа названиям. Пусто — раса не найдена или размер в ней не указан.
     */
    fun forCharacter(character: Character, races: List<Race>, cache: WizardRulesCache = WizardRulesCache()): List<CreatureSize> {
        if (character.race.isBlank()) return emptyList()
        val candidates = races.filter { norm(it.name) == norm(character.race) }.map(cache::race)
        if (candidates.isEmpty()) return emptyList()
        val subraceTitle = norm(character.subrace)
        candidates.forEach { rules ->
            rules.subraces.firstOrNull { subraceTitle.isNotEmpty() && norm(it.title) == subraceTitle }
                ?.let { return options(rules, it) }
        }
        val rules = candidates.firstOrNull { !it.extended } ?: candidates.first()
        return options(rules, null)
    }

    private fun norm(text: String) = text.trim().lowercase().replace('ё', 'е')
}
