package com.example.spellbook.tools

import com.example.spellbook.data.ClassLibraryCodec
import com.example.spellbook.data.DndSuClassParser
import com.example.spellbook.data.model.CharClass
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Генератор встроенной библиотеки классов `app/src/main/assets/classes-library.json`.
 *
 * Инструмент разработчика, а не проверка кода, поэтому без явного флага он
 * пропускается: обычный прогон тестов не должен ходить в сеть. Запуск вручную:
 * ```
 * ./gradlew :app:testDebugUnitTest --tests "*BundledClassLibraryGenerator*" "-Dgenerate.classes=true"
 * ```
 * Генератор использует тот же [DndSuClassParser], что и приложение, поэтому формат
 * описания совпадает с остальными разделами библиотеки.
 *
 * В набор попадают только официальные классы: разделы «Классы Homebrew» и «Напарники»
 * пропускаются. Подклассы из Unearthed Arcana сохраняются, homebrew-подклассы
 * вырезаются парсером внутри страницы.
 *
 * Запуск накопительный: уже собранные классы читаются из файла и сохраняются, даже
 * если сейчас страница не ответила.
 */
class BundledClassLibraryGenerator {

    @Test
    fun `generate bundled class library`() {
        assumeTrue(
            "Запуск только с -D$GENERATE_PROPERTY=true",
            System.getProperty(GENERATE_PROPERTY).toBoolean(),
        )

        val urls = loadClassUrls()
        assertTrue("Каталог классов пуст — изменилась разметка dnd.su?", urls.isNotEmpty())
        println("Официальных классов в каталоге: ${urls.size}")

        val output = File(ASSETS_DIR, OUTPUT_FILE)
        val collected = linkedMapOf<String, CharClass>()
        if (output.exists()) {
            ClassLibraryCodec.decodeAll(output.readText()).forEach { charClass ->
                if (charClass.source in urls) collected[charClass.source] = charClass
            }
            println("Уже собрано: ${collected.size}")
        }

        // Сначала недостающие: если сайт начнёт отбрасывать запросы,
        // проход всё равно принесёт максимум новых классов.
        val missing = urls.filterNot { it in collected }
        val ordered = missing + urls.filter { it in collected }
        println("Не хватает: ${missing.size}")

        var added = 0
        var failed = 0
        ordered.forEachIndexed { index, url ->
            println("[${index + 1}/${ordered.size}] $url")
            val charClass = fetchClass(url)
            if (charClass == null) {
                failed++
            } else {
                if (url !in collected) added++
                collected[url] = charClass
            }
            Thread.sleep(REQUEST_DELAY_MS)
        }

        assertTrue("Не удалось разобрать ни одного класса", collected.isNotEmpty())

        // Стабильный порядок: так обновление библиотеки даёт минимальный diff.
        val sorted = collected.values.sortedBy { it.name.lowercase() }
        output.parentFile?.mkdirs()
        output.writeText(ClassLibraryCodec.encodeAll(sorted))

        println("Сохранено ${sorted.size} классов (добавлено $added, не ответили $failed)")
        println("Осталось добрать: ${urls.size - sorted.size}")
    }

    /**
     * Загружает и разбирает один класс, повторяя попытки при сетевых ошибках.
     *
     * При массовой загрузке сайт часть запросов отбрасывает, хотя поодиночке
     * те же страницы отдаются нормально.
     */
    private fun fetchClass(url: String): CharClass? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = runCatching {
                val html = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    // Страница класса вместе с подклассами весит под мегабайт.
                    .maxBodySize(MAX_BODY_SIZE)
                    .get()
                    .outerHtml()
                val parsed = DndSuClassParser.parse(html)
                CharClass(
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
                Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return null
    }

    /**
     * Ссылки на официальные классы со страницы каталога.
     *
     * Раздел определяется по ближайшему заголовку выше ссылки. Путь для этого не годится:
     * официальные классы лежат в `/class/…`, но по такому же пути идут «Напарники»,
     * которые классами не являются. Пропускаются разделы [HOMEBREW_SECTION]
     * и [SIDEKICKS_SECTION].
     */
    private fun loadClassUrls(): List<String> {
        val body = Jsoup.connect(CATALOG_URL)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .get()
            .body()

        val result = linkedSetOf<String>()
        var section = ""
        body.select("h1, h2, h3, h4, a[href]").forEach { element ->
            if (element.tagName().startsWith("h")) {
                val title = element.text().trim()
                if (title.isNotEmpty()) section = title
                return@forEach
            }
            val href = element.attr("href")
            if (!CLASS_PATH_REGEX.containsMatchIn(href)) return@forEach
            if (section in SKIPPED_SECTIONS) return@forEach

            val url = if (href.startsWith("http")) href else BASE_URL + href.removePrefix("/")
            result += url
        }
        return result.toList()
    }

    private companion object {
        /** Флаг ручного запуска; пробрасывается в JVM тестов из app/build.gradle.kts. */
        const val GENERATE_PROPERTY = "generate.classes"

        const val BASE_URL = "https://dnd.su/"
        const val CATALOG_URL = "https://dnd.su/class/"
        const val USER_AGENT = "Mozilla/5.0 (Android) SpellBook/1.0"
        const val TIMEOUT_MS = 30_000

        /** Страница класса со всеми подклассами крупнее стандартного лимита jsoup. */
        const val MAX_BODY_SIZE = 8 * 1024 * 1024

        /** Пауза между запросами, чтобы не создавать нагрузку на сайт. */
        const val REQUEST_DELAY_MS = 700L

        /** Сколько раз пробуем загрузить страницу, прежде чем пропустить её. */
        const val MAX_ATTEMPTS = 3

        /** Базовая пауза перед повтором; умножается на номер попытки. */
        const val RETRY_DELAY_MS = 1_500L

        val CLASS_PATH_REGEX = Regex("/class/\\d+-")

        /**
         * Разделы каталога, которые в библиотеку не идут: пользовательские классы
         * и напарники (спутники из «Tasha's», а не классы персонажа).
         */
        const val HOMEBREW_SECTION = "Классы Homebrew"
        const val SIDEKICKS_SECTION = "Напарники"
        val SKIPPED_SECTIONS = setOf(HOMEBREW_SECTION, SIDEKICKS_SECTION)

        /** Путь относительно каталога модуля `app`, откуда Gradle запускает тесты. */
        const val ASSETS_DIR = "src/main/assets"
        const val OUTPUT_FILE = "classes-library.json"
    }
}
