package com.example.spellbook.tools

import com.example.spellbook.data.BackgroundLibraryCodec
import com.example.spellbook.data.DndSuBackgroundParser
import com.example.spellbook.data.model.Background
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Генератор встроенной библиотеки предысторий `app/src/main/assets/backgrounds-library.json`.
 *
 * Инструмент разработчика, а не проверка кода, поэтому без явного флага он
 * пропускается: обычный прогон тестов не должен ходить в сеть. Запуск вручную:
 * ```
 * ./gradlew :app:testDebugUnitTest --tests "*BundledBackgroundLibraryGenerator*" "-Dgenerate.backgrounds=true"
 * ```
 * Генератор использует тот же [DndSuBackgroundParser], что и приложение, поэтому формат
 * описания совпадает с остальными разделами библиотеки.
 *
 * В набор попадают только официальные предыстории: раздел homebrew лежит по отдельному
 * адресу `/homebrew/backgrounds/` и на страницу каталога не выводится.
 *
 * Запуск накопительный: уже собранные предыстории читаются из файла и сохраняются, даже
 * если сейчас страница не ответила.
 */
class BundledBackgroundLibraryGenerator {

    @Test
    fun `generate bundled background library`() {
        assumeTrue(
            "Запуск только с -D$GENERATE_PROPERTY=true",
            System.getProperty(GENERATE_PROPERTY).toBoolean(),
        )

        val catalog = loadCatalog()
        assertTrue("Каталог предысторий пуст — изменилась разметка dnd.su?", catalog.isNotEmpty())
        val urls = catalog.keys.toList()
        println("Официальных предысторий в каталоге: ${urls.size}")
        println("Блоки: " + catalog.values.distinct())

        val output = File(ASSETS_DIR, OUTPUT_FILE)
        // Ключ — ссылка: по ней запись видна однозначно, в отличие от названия,
        // которое у вариантов из разных книг совпадает («Артист» и «Артист Врат Балдура»).
        val collected = linkedMapOf<String, Background>()
        if (output.exists()) {
            BackgroundLibraryCodec.decodeAll(output.readText()).forEach { background ->
                if (background.source in urls) collected[background.source] = background
            }
            println("Уже собрано: ${collected.size}")
        }

        // Сначала недостающие: если сайт начнёт отбрасывать запросы,
        // проход всё равно принесёт максимум новых предысторий.
        val missing = urls.filterNot { it in collected }
        val ordered = missing + urls.filter { it in collected }
        println("Не хватает: ${missing.size}")

        var added = 0
        var failed = 0
        ordered.forEachIndexed { index, url ->
            println("[${index + 1}/${ordered.size}] $url")
            // Блок берём из каталога, а не из плашки страницы: они расходятся.
            val background = fetchBackground(url)?.copy(category = catalog.getValue(url))
            if (background == null) {
                failed++
            } else {
                if (url !in collected) added++
                collected[url] = background
            }
            Thread.sleep(REQUEST_DELAY_MS)
        }

        assertTrue("Не удалось разобрать ни одной предыстории", collected.isNotEmpty())

        // Стабильный порядок: так обновление библиотеки даёт минимальный diff.
        val sorted = collected.values.sortedBy { it.name.lowercase() }
        println("По блокам: " + sorted.groupingBy { it.category.ifBlank { "<нет>" } }.eachCount())
        output.parentFile?.mkdirs()
        output.writeText(BackgroundLibraryCodec.encodeAll(sorted))

        println("Сохранено ${sorted.size} предысторий (добавлено $added, не ответили $failed)")
        println("Осталось добрать: ${urls.size - sorted.size}")
    }

    /**
     * Загружает и разбирает одну предысторию, повторяя попытки при сетевых ошибках.
     *
     * При массовой загрузке сайт часть запросов отбрасывает, хотя поодиночке
     * те же страницы отдаются нормально.
     */
    private fun fetchBackground(url: String): Background? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = runCatching {
                val html = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE)
                    .get()
                    .outerHtml()
                val parsed = DndSuBackgroundParser.parse(html)
                Background(
                    name = parsed.name,
                    description = parsed.description,
                    source = url,
                    book = parsed.book,
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
     * Ссылки на официальные предыстории вместе с блоком каталога.
     *
     * На странице каталога записи разбиты на блоки по книгам: перед каждой
     * группой стоит [GROUP_TITLE_SELECTOR] с названием книги. Блок берём оттуда,
     * а не из плашки страницы: они расходятся — например, «Преследуемый» стоит
     * в Curse of Strahd, а плашка указывает Van Richten's Guide.
     *
     * Подходят оба вида путей: `/backgrounds/757-entertainer/` и
     * `/multiverse/backgrounds/815-astral-drifter/` — второй используется для книг
     * по другим сеттингам. Пользовательские материалы лежат в `/homebrew/backgrounds/`
     * и отсеиваются по [HOMEBREW_PATH].
     *
     * @return ссылка → название блока, в порядке следования на странице.
     */
    private fun loadCatalog(): Map<String, String> {
        val body = Jsoup.connect(CATALOG_URL)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .maxBodySize(MAX_BODY_SIZE)
            .get()
            .body()

        val result = linkedMapOf<String, String>()
        var group = ""
        // Обходим всё в порядке документа: заголовки переключают текущий блок.
        body.select("$GROUP_TITLE_SELECTOR, a[href]").forEach { element ->
            if (element.hasClass(GROUP_TITLE_CLASS)) {
                val title = element.text().trim()
                if (title.isNotEmpty()) group = title
                return@forEach
            }
            val href = element.attr("href")
            if (!BACKGROUND_PATH_REGEX.containsMatchIn(href)) return@forEach
            if (href.contains(HOMEBREW_PATH)) return@forEach

            val url = if (href.startsWith("http")) href else BASE_URL + href.removePrefix("/")
            result.putIfAbsent(url, group)
        }
        return result
    }

    private companion object {
        /** Флаг ручного запуска; пробрасывается в JVM тестов из app/build.gradle.kts. */
        const val GENERATE_PROPERTY = "generate.backgrounds"

        const val BASE_URL = "https://dnd.su/"
        const val CATALOG_URL = "https://dnd.su/backgrounds/"
        const val USER_AGENT = "Mozilla/5.0 (Android) SpellBook/1.0"
        const val TIMEOUT_MS = 30_000

        /** Страница каталога вместе со списком всех предысторий крупнее лимита jsoup. */
        const val MAX_BODY_SIZE = 8 * 1024 * 1024

        /** Пауза между запросами, чтобы не создавать нагрузку на сайт. */
        const val REQUEST_DELAY_MS = 700L

        /** Сколько раз пробуем загрузить страницу, прежде чем пропустить её. */
        const val MAX_ATTEMPTS = 3

        /** Базовая пауза перед повтором; умножается на номер попытки. */
        const val RETRY_DELAY_MS = 1_500L

        /** Подходят `/backgrounds/757-…` и `/multiverse/backgrounds/815-…`. */
        val BACKGROUND_PATH_REGEX = Regex("/backgrounds/\\d+-")

        /** Пользовательские материалы в библиотеку не идут. */
        const val HOMEBREW_PATH = "/homebrew/"

        /** Подпись блока книги на странице каталога. */
        const val GROUP_TITLE_CLASS = "list-group__wrapper"
        const val GROUP_TITLE_SELECTOR = ".$GROUP_TITLE_CLASS"

        /** Путь относительно каталога модуля `app`, откуда Gradle запускает тесты. */
        const val ASSETS_DIR = "src/main/assets"
        const val OUTPUT_FILE = "backgrounds-library.json"
    }
}
