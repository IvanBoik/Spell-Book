package com.example.spellbook.tools

import com.example.spellbook.data.DndSuRaceParser
import com.example.spellbook.data.RaceLibraryCodec
import com.example.spellbook.data.model.Race
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Генератор встроенной библиотеки рас `app/src/main/assets/races-library.json`.
 *
 * Инструмент разработчика, а не проверка кода, поэтому без явного флага он
 * пропускается: обычный прогон тестов не должен ходить в сеть. Запуск вручную
 * при обновлении библиотеки:
 * ```
 * ./gradlew :app:testDebugUnitTest --tests "*BundledRaceLibraryGenerator*" "-Dgenerate.races=true"
 * ```
 * Генератор использует тот же [DndSuRaceParser], что и загрузка расы по ссылке
 * в приложении: формат описания гарантированно совпадает, и встроенные расы
 * неотличимы от загруженных пользователем вручную.
 *
 * Запуск накопительный: уже собранные расы читаются из файла и сохраняются, даже
 * если сейчас страница не ответила. При массовой загрузке сайт стабильно отбрасывает
 * часть запросов (каждый раз разных), поэтому за один проход весь каталог не выгружается —
 * достаточно повторить команду несколько раз, пока не останется пропусков.
 */
class BundledRaceLibraryGenerator {

    @Test
    fun `generate bundled race library`() {
        assumeTrue(
            "Запуск только с -D$GENERATE_PROPERTY=true",
            System.getProperty(GENERATE_PROPERTY).toBoolean(),
        )

        val catalog = loadCatalog()
        assertTrue("Каталог рас пуст — изменилась разметка dnd.su?", catalog.isNotEmpty())
        println("Разделы: " + catalog.values.groupingBy { it }.eachCount())

        val output = File(ASSETS_DIR, OUTPUT_FILE)
        // Ключ — ссылка: по ней расу видно однозначно, в отличие от названия,
        // которое у вариантов из разных книг совпадает.
        val collected = linkedMapOf<String, Race>()
        if (output.exists()) {
            RaceLibraryCodec.decodeAll(output.readText()).forEach { race ->
                // Записи вне текущего каталога (например, homebrew из старой выгрузки) отбрасываем.
                if (race.source in catalog) collected[race.source] = race
            }
            println("Уже собрано: ${collected.size}")
        }

        // Сначала то, чего ещё нет: если сайт начнёт отбрасывать запросы,
        // проход всё равно принесёт максимум новых рас.
        val urls = catalog.keys.toList()
        val missing = urls.filterNot { it in collected }
        val ordered = missing + urls.filter { it in collected }
        println("Всего в каталоге: ${urls.size}, не хватает: ${missing.size}")

        var added = 0
        var failed = 0
        ordered.forEachIndexed { index, url ->
            println("[${index + 1}/${ordered.size}] $url")
            val category = catalog.getValue(url)
            val race = fetchRace(url)?.copy(category = category)
            if (race == null) {
                failed++
            } else {
                if (url !in collected) added++
                collected[url] = race
            }
            Thread.sleep(REQUEST_DELAY_MS)
        }

        assertTrue("Не удалось разобрать ни одной расы", collected.isNotEmpty())

        // Стабильный порядок: так обновление библиотеки даёт минимальный diff.
        val sorted = collected.values.sortedBy { it.name.lowercase() }
        println("Категории: " + sorted.groupingBy { it.category.ifBlank { "<нет>" } }.eachCount())
        output.parentFile?.mkdirs()
        output.writeText(RaceLibraryCodec.encodeAll(sorted))

        println("Сохранено ${sorted.size} рас (добавлено $added, не ответили $failed)")
        println("Осталось добрать: ${urls.size - sorted.size}")
    }

    /**
     * Загружает и разбирает одну расу, повторяя попытки при сетевых ошибках.
     *
     * При массовой загрузке сайт часть запросов отбрасывает, хотя поодиночке
     * те же страницы отдаются нормально. Без повторов библиотека теряла почти
     * половину рас, причём каждый раз разных.
     */
    private fun fetchRace(url: String): Race? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = runCatching {
                val html = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get()
                    .outerHtml()
                val parsed = DndSuRaceParser.parse(html)
                Race(
                    name = parsed.name,
                    description = parsed.description,
                    source = url,
                    book = parsed.book,
                )
            }
            result.getOrNull()?.let { return it }

            val error = result.exceptionOrNull()
            val lastAttempt = attempt == MAX_ATTEMPTS - 1
            if (lastAttempt) {
                println("  ! пропущено после $MAX_ATTEMPTS попыток: ${error?.message}")
            } else {
                // Пауза растёт с каждой попыткой: сайту надо дать передохнуть.
                Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return null
    }

    /**
     * Ссылки на расы со страницы каталога вместе с разделом, в котором они стоят.
     *
     * Как и у черт, карта сайта не подходит: в ней есть только страница раздела,
     * без ссылок на отдельные расы.
     *
     * Раздел определяется по ближайшему заголовку выше ссылки: путь `/homebrew/race/…`
     * для этого непригоден — в нём лежат и официальные дополнения (Plane Shift,
     * Unearthed Arcana, Midgard), и пользовательские материалы. Раздел [HOMEBREW_SECTION]
     * в библиотеку не попадает.
     *
     * @return ссылка → название раздела, в порядке следования на странице.
     */
    private fun loadCatalog(): Map<String, String> {
        val body = Jsoup.connect(CATALOG_URL)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .get()
            .body()

        val result = linkedMapOf<String, String>()
        var section = ""
        // Обходим все элементы в порядке документа: заголовки переключают текущий раздел.
        body.select("h1, h2, h3, h4, a[href]").forEach { element ->
            if (element.tagName().startsWith("h")) {
                val title = element.text().trim()
                if (title.isNotEmpty()) section = title
                return@forEach
            }
            val href = element.attr("href")
            if (!RACE_PATH_REGEX.containsMatchIn(href)) return@forEach
            if (section.equals(HOMEBREW_SECTION, ignoreCase = true)) return@forEach

            val url = if (href.startsWith("http")) href else BASE_URL + href.removePrefix("/")
            result.putIfAbsent(url, normalizeSection(section))
        }
        return result
    }

    /**
     * Приводит заголовок раздела к названию категории.
     *
     * На сайте заголовки выглядят как «Расы Unearthed Arcana» — слово «Расы» в подписи
     * блока избыточно. Первый раздел каталога называется «Расы и происхождения» и собирает
     * основные расы — ему даём понятное имя.
     */
    private fun normalizeSection(section: String): String = when {
        section.equals(MAIN_SECTION, ignoreCase = true) -> MAIN_CATEGORY
        section.startsWith(SECTION_PREFIX) -> section.removePrefix(SECTION_PREFIX).trim()
        else -> section
    }

    private companion object {
        /** Флаг ручного запуска; пробрасывается в JVM тестов из app/build.gradle.kts. */
        const val GENERATE_PROPERTY = "generate.races"

        const val BASE_URL = "https://dnd.su/"
        const val CATALOG_URL = "https://dnd.su/race/"
        const val USER_AGENT = "Mozilla/5.0 (Android) SpellBook/1.0"
        const val TIMEOUT_MS = 30_000

        /** Пауза между запросами, чтобы не создавать нагрузку на сайт. */
        const val REQUEST_DELAY_MS = 500L

        /** Сколько раз пробуем загрузить страницу, прежде чем пропустить её. */
        const val MAX_ATTEMPTS = 3

        /** Базовая пауза перед повтором; умножается на номер попытки. */
        const val RETRY_DELAY_MS = 1_500L

        /** Подходят оба вида путей: `/race/103-goliath/` и `/homebrew/race/328-minotaur/`. */
        val RACE_PATH_REGEX = Regex("/race/\\d+-")

        /** Раздел каталога с пользовательскими расами — в библиотеку не идёт. */
        const val HOMEBREW_SECTION = "Расы Homebrew"

        /** Первый раздел каталога и его название в библиотеке. */
        const val MAIN_SECTION = "Расы и происхождения"
        const val MAIN_CATEGORY = "Основные расы"

        /** Префикс остальных заголовков: «Расы Unearthed Arcana» → «Unearthed Arcana». */
        const val SECTION_PREFIX = "Расы "

        /** Путь относительно каталога модуля `app`, откуда Gradle запускает тесты. */
        const val ASSETS_DIR = "src/main/assets"
        const val OUTPUT_FILE = "races-library.json"
    }
}
