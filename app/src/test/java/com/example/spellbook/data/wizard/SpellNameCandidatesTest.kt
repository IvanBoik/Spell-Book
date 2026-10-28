package com.example.spellbook.data.wizard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Выделение названий заклинаний перед английскими названиями в скобках. */
class SpellNameCandidatesTest {

    @Test
    fun `table row with comma separated spells`() {
        assertEquals(
            listOf("благословение", "лечение ран"),
            MarkupText.spellNameCandidates("благословение [bless], лечение ран [cure wounds]"),
        )
    }

    @Test
    fun `service words before name are dropped`() {
        assertEquals(listOf("туманный шаг"), MarkupText.spellNameCandidates("Вы узнаёте заклинание туманный шаг [misty step]."))
        assertEquals(
            listOf("бесследное передвижение", "тёмное зрение", "тишину", "тьму"),
            MarkupText.spellNameCandidates(
                "чтобы наложить бесследное передвижение [pass without trace], тёмное зрение [darkvision], " +
                    "тишину [silence] или тьму [darkness]",
            ),
        )
    }

    @Test
    fun `conjunction inside name is kept`() {
        assertEquals(
            listOf("защита от зла и добра"),
            MarkupText.spellNameCandidates("заклинание защита от зла и добра [protection from evil and good]"),
        )
    }
}
