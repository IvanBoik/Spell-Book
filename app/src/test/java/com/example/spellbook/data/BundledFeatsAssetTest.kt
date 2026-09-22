package com.example.spellbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Проверяет встроенный файл черт `assets/feats-library.json`.
 *
 * Тест страхует от «тихой» поломки библиотеки: файл кладётся в приложение заранее,
 * и без проверки битый JSON обнаружился бы только на устройстве пользователя.
 */
class BundledFeatsAssetTest {

    private val feats by lazy {
        val file = File(ASSET_PATH)
        assertTrue("Не найден $ASSET_PATH", file.exists())
        FeatLibraryCodec.decodeList(file.readText())
    }

    @Test
    fun `bundled feats file is readable and not empty`() {
        assertTrue("Библиотека черт пуста", feats.size >= MIN_EXPECTED_FEATS)
    }

    @Test
    fun `every bundled feat has name description and source`() {
        feats.forEach { feat ->
            assertTrue("Пустое описание у «${feat.name}»", feat.description.isNotBlank())
            assertTrue("Нет источника у «${feat.name}»", feat.source.startsWith(DND_SU_FEATS_PREFIX))
        }
    }

    @Test
    fun `every bundled feat knows its book`() {
        // По книге черты группируются в библиотеке: без неё черта уйдёт в «Прочее».
        feats.forEach { feat ->
            assertTrue("Не указана книга у «${feat.name}»", feat.book.isNotBlank())
        }
    }

    @Test
    fun `bundled feats come from the core rulebook among others`() {
        val books = feats.map { it.book }.toSet()
        assertTrue("Нет черт из Player's Handbook: $books", PLAYERS_HANDBOOK in books)
        // Разделение на блоки имеет смысл только при нескольких источниках.
        assertTrue("Ожидались черты из разных книг", books.size > 1)
    }

    @Test
    fun `bundled feats have unique names`() {
        // Дубли по названию сломали бы защиту от повторного импорта.
        val duplicates = feats.groupBy { it.name.lowercase() }.filterValues { it.size > 1 }
        assertEquals("Дубли: ${duplicates.keys}", emptySet<String>(), duplicates.keys)
    }

    @Test
    fun `bundled feats contain well known entries`() {
        val names = feats.map { it.name }
        listOf("Бдительный", "Бездонная удача", "Меткий стрелок").forEach { expected ->
            assertTrue("Нет черты «$expected»", expected in names)
        }
    }

    private companion object {
        /** Путь относительно каталога модуля `app`, откуда Gradle запускает тесты. */
        const val ASSET_PATH = "src/main/assets/feats-library.json"

        const val DND_SU_FEATS_PREFIX = "https://dnd.su/feats/"
        const val PLAYERS_HANDBOOK = "Player's Handbook"

        /** Официальных черт на dnd.su около сотни; резкое падение числа — признак поломки. */
        const val MIN_EXPECTED_FEATS = 100
    }
}
