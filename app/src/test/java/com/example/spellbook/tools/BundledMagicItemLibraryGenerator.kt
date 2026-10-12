package com.example.spellbook.tools

import com.example.spellbook.data.DndSuMagicItemParser
import com.example.spellbook.data.MagicItemLibraryCodec
import com.example.spellbook.data.model.MagicItem
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Генератор встроенной библиотеки магических предметов `app/src/main/assets/magic-items-library.json`.
 *
 * Инструмент разработчика, а не проверка кода, поэтому без явного флага он
 * пропускается: обычный прогон тестов не должен ходить в сеть. Запуск вручную:
 * ```
 * ./gradlew :app:testDebugUnitTest --tests "*BundledMagicItemLibraryGenerator*" "-Dgenerate.magicitems=true"
 * ```
 * Генератор использует тот же [DndSuMagicItemParser], что и приложение, поэтому формат
 * описания совпадает с остальными разделами библиотеки.
 *
 * Запуск накопительный: уже собранные предметы читаются из файла и сохраняются, даже
 * если сейчас страница не ответила. Предметов около девятисот, и при массовой загрузке
 * сайт стабильно отбрасывает часть запросов, поэтому за один проход весь каталог может
 * не выгрузиться — достаточно повторить команду, пока не останется пропусков.
 */
class BundledMagicItemLibraryGenerator {

    @Test
    fun `generate bundled magic item library`() {
        assumeTrue(
            "Запуск только с -D$GENERATE_PROPERTY=true",
            System.getProperty(GENERATE_PROPERTY).toBoolean(),
        )

        val urls = loadOfficialItemUrls()
        assertTrue("Каталог предметов пуст — изменилась карта сайта dnd.su?", urls.isNotEmpty())
        println("Официальных предметов в каталоге: ${urls.size}")

        val output = File(ASSETS_DIR, OUTPUT_FILE)
        // Ключ — ссылка: по ней запись видна однозначно, в отличие от названия,
        // которое у вариантов из разных книг совпадает.
        val collected = linkedMapOf<String, MagicItem>()
        if (output.exists()) {
            MagicItemLibraryCodec.decodeAll(output.readText()).forEach { item ->
                if (item.source in urls) collected[item.source] = item
            }
            println("Уже собрано: ${collected.size}")
        }

        // Сначала недостающие: если сайт начнёт отбрасывать запросы,
        // проход всё равно принесёт максимум новых предметов.
        val missing = urls.filterNot { it in collected }
        val ordered = missing + urls.filter { it in collected }
        println("Не хватает: ${missing.size}")

        var added = 0
        var failed = 0
        ordered.forEachIndexed { index, url ->
            println("[${index + 1}/${ordered.size}] $url")
            val item = fetchItem(url)
            if (item == null) {
                failed++
            } else {
                if (url !in collected) added++
                collected[url] = item
            }
            Thread.sleep(REQUEST_DELAY_MS)
        }

        assertTrue("Не удалось разобрать ни одного предмета", collected.isNotEmpty())

        // Стабильный порядок: так обновление библиотеки даёт минимальный diff.
        val sorted = collected.values.sortedBy { it.name.lowercase() }
        println("Типы: " + sorted.groupingBy { it.kind.ifBlank { "<нет>" } }.eachCount())
        output.parentFile?.mkdirs()
        output.writeText(MagicItemLibraryCodec.encodeAll(sorted))

        println("Сохранено ${sorted.size} предметов (добавлено $added, не ответили $failed)")
        println("Осталось добрать: ${urls.size - sorted.size}")
    }

    /**
     * Загружает и разбирает один предмет, повторяя попытки при сетевых ошибках.
     *
     * При массовой загрузке сайт часть запросов отбрасывает, хотя поодиночке
     * те же страницы отдаются нормально.
     */
    private fun fetchItem(url: String): MagicItem? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = runCatching {
                val html = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE)
                    .get()
                    .outerHtml()
                // Часть пользовательских предметов лежит в обычном `/items/`,
                // и по адресу отличить их нельзя — смотрим на разметку карточки.
                if (DndSuMagicItemParser.isHomebrew(html)) return null
                val parsed = DndSuMagicItemParser.parse(html)
                MagicItem(
                    name = parsed.name,
                    description = parsed.description,
                    source = url,
                    book = parsed.book,
                    category = parsed.category,
                    kind = parsed.kind,
                    rarity = parsed.rarity,
                    requiresAttunement = parsed.requiresAttunement,
                    price = parsed.price,
                )
            }
            result.getOrNull()?.let { return it }

            val error = result.exceptionOrNull()
            if (attempt == MAX_ATTEMPTS - 1) {
                println("  ! пропущено после $MAX_ATTEMPTS попыток: ${error?.message}")
            } else {
                // Пауза растёт с каждой попыткой: сайту надо дать передохнуть.
                Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return null
    }

    /**
     * Ссылки на официальные предметы из карты сайта.
     *
     * Страница каталога подгружает список скриптом, поэтому обычным запросом ссылки
     * оттуда не получить — в отличие от рас и предысторий. Пользовательские материалы
     * лежат в `/homebrew/items/` и отсеиваются по [HOMEBREW_MARKER].
     */
    private fun loadOfficialItemUrls(): List<String> {
        val xml = Jsoup.connect(SITEMAP_URL)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .maxBodySize(MAX_BODY_SIZE)
            .ignoreContentType(true)
            .execute()
            .body()

        return ITEM_URL_REGEX.findAll(xml)
            .map { it.value.trimEnd('<') }
            .filterNot { it.contains(HOMEBREW_MARKER, ignoreCase = true) }
            .distinct()
            .sorted()
            .toList()
    }

    private companion object {
        /** Флаг ручного запуска; пробрасывается в JVM тестов из app/build.gradle.kts. */
        const val GENERATE_PROPERTY = "generate.magicitems"

        const val SITEMAP_URL = "https://dnd.su/sitemap.xml"
        const val USER_AGENT = "Mozilla/5.0 (Android) SpellBook/1.0"
        const val TIMEOUT_MS = 30_000

        /** Карта сайта весит около мегабайта — стандартного лимита Jsoup не хватает. */
        const val MAX_BODY_SIZE = 8 * 1024 * 1024

        /** Пауза между запросами, чтобы не создавать нагрузку на сайт. */
        const val REQUEST_DELAY_MS = 400L

        /** Сколько раз пробуем загрузить страницу, прежде чем пропустить её. */
        const val MAX_ATTEMPTS = 3

        /** Базовая пауза перед повтором; умножается на номер попытки. */
        const val RETRY_DELAY_MS = 1_500L

        /** Ссылка на страницу предмета: `/items/<id>-<slug>/`. */
        val ITEM_URL_REGEX = Regex("https?://[^<\\s]*?/items/\\d+-[^<\\s]*")

        /** Раздел пользовательских материалов — в библиотеку не идёт. */
        const val HOMEBREW_MARKER = "/homebrew/"

        /** Путь относительно каталога модуля `app`, откуда Gradle запускает тесты. */
        const val ASSETS_DIR = "src/main/assets"
        const val OUTPUT_FILE = "magic-items-library.json"
    }
}
