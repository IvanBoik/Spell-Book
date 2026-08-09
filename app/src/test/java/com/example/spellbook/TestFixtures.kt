package com.example.spellbook

import com.example.spellbook.data.model.Components
import com.example.spellbook.data.model.Spell

/** Компактный конструктор заклинания для тестов: задаются только значимые поля. */
internal fun testSpell(
    id: String = "id-$DEFAULT_SPELL_COUNTER",
    name: String = "Заклинание",
    level: Int = 1,
    school: String = "evo",
    activationType: String = "action",
    components: Components = Components(),
    classes: List<String> = emptyList(),
    createdAt: Long = 0L,
): Spell = Spell(
    id = id,
    createdAt = createdAt,
    name = name,
    level = level,
    school = school,
    activationType = activationType,
    components = components,
    classes = classes,
)

private const val DEFAULT_SPELL_COUNTER = 0
