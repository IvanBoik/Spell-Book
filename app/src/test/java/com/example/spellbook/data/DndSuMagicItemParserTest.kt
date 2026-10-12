package com.example.spellbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты парсера страницы магического предмета.
 *
 * Разметка воспроизводит структуру dnd.su: название с английским дубликатом,
 * плашка источника и список параметров со строкой типа, стоимостью и описанием.
 */
class DndSuMagicItemParserTest {

    private fun page(
        title: String = "Амулет здоровья [Amulet of health]",
        plaque: String = "Dungeon Master's Guide",
        category: String = "Чудесный предмет, редкий (требуется настройка)",
        price: String = "501-5 000 зм",
        description: String = "<p>Пока вы носите этот амулет, ваше Телосложение равно 19.</p>",
    ): String = """
        <html><body>
          <h2 class="card-title">
            <span data-copy="$title">$title</span>
            <span class='source-plaque' title="$plaque">DMG14</span>
          </h2>
          <ul class="params">
            <li class='size-type-alignment'>$category</li>
            <li class='price'><strong>Рекомендованная стоимость:</strong> $price</li>
            <li class="subsection desc"><div itemprop="description">$description</div></li>
          </ul>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses name without the english duplicate`() {
        assertEquals("Амулет здоровья", DndSuMagicItemParser.parse(page()).name)
    }

    @Test
    fun `parses kind rarity and attunement`() {
        val item = DndSuMagicItemParser.parse(page())

        assertEquals("Чудесный предмет", item.kind)
        assertEquals("редкий", item.rarity)
        assertTrue(item.requiresAttunement)
    }

    @Test
    fun `keeps the original category line`() {
        // Строка нужна целиком: в ней бывают уточнения вроде «настройка волшебником».
        assertEquals(
            "Чудесный предмет, редкий (требуется настройка)",
            DndSuMagicItemParser.parse(page()).category,
        )
    }

    @Test
    fun `comma inside parentheses does not split the kind`() {
        val item = DndSuMagicItemParser.parse(
            page(category = "Доспех (средний или тяжёлый, кроме шкурного), необычный"),
        )

        assertEquals("Доспех", item.kind)
        assertEquals("необычный", item.rarity)
        assertFalse(item.requiresAttunement)
    }

    @Test
    fun `parses price without its label`() {
        assertEquals("501-5 000 зм", DndSuMagicItemParser.parse(page()).price)
    }

    @Test
    fun `parses the source book from the plaque`() {
        assertEquals("Dungeon Master's Guide", DndSuMagicItemParser.parse(page()).book)
    }

    @Test
    fun `missing category leaves the fields empty`() {
        val html = """
            <html><body>
              <h2 class="card-title"><span data-copy="Боб">Боб</span></h2>
              <ul class="params">
                <li class="subsection desc"><div itemprop="description"><p>Описание.</p></div></li>
              </ul>
            </body></html>
        """.trimIndent()

        val item = DndSuMagicItemParser.parse(html)

        assertEquals("", item.kind)
        assertEquals("", item.rarity)
        assertEquals("", item.price)
    }

    @Test
    fun `category without rarity still yields the kind`() {
        // Так оформлены предметы приключений: «Оружие (палица)» без указания редкости.
        val item = DndSuMagicItemParser.parse(page(category = "Оружие (палица)"))

        assertEquals("Оружие", item.kind)
        assertEquals("", item.rarity)
    }

    @Test
    fun `drops interactive site buttons`() {
        // Кнопки колод работают на JS и в приложении остались бы строками-пустышками.
        val item = DndSuMagicItemParser.parse(
            page(
                description = """
                    <p>Описание колоды.</p>
                    <a onclick="DeckRoll.roll(false)" class="btn">ВЫТЯНУТЬ КАРТУ</a>
                    <p>Продолжение.</p>
                """.trimIndent(),
            ),
        )

        assertFalse(item.description.contains("ВЫТЯНУТЬ КАРТУ"))
        assertTrue(item.description.contains("Продолжение."))
    }

    @Test
    fun `converts tooltip terms into reference tokens`() {
        val item = DndSuMagicItemParser.parse(
            page(description = """<p>Бросок <span tooltip-for="1">Атлетика</span>.</p>"""),
        )

        assertTrue(item.description.contains("[[ref Атлетика]]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `page without description is rejected`() {
        DndSuMagicItemParser.parse("<html><body><h2 class='card-title'>Меч</h2></body></html>")
    }

    @Test
    fun `homebrew card is detected by its markup`() {
        // Часть пользовательских предметов лежит в обычном `/items/`,
        // и по адресу отличить их нельзя.
        val html = """
            <html><body>
              <div class='paper-1 card card__group-homebrew card__category-items'>
                <h2 class="card-title">Посох мощи</h2>
              </div>
            </body></html>
        """.trimIndent()

        assertTrue(DndSuMagicItemParser.isHomebrew(html))
    }

    @Test
    fun `official card is not marked as homebrew`() {
        assertFalse(DndSuMagicItemParser.isHomebrew(page()))
    }
}
