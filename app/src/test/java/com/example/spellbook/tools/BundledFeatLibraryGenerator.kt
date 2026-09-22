package com.example.spellbook.tools

import com.example.spellbook.data.DndSuFeatParser
import com.example.spellbook.data.FeatLibraryCodec
import com.example.spellbook.data.model.Feat
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Генератор встроенной библиотеки черт `app/src/main/assets/feats-library.json`.
 *
 * Это инструмент разработчика, а не проверка кода, поэтому без явного флага он
 * пропускается: обычный прогон тестов не должен ходить в сеть. Запуск вручную
 * при обновлении библиотеки:
 * ```
 * ./gradlew :app:testDebugUnitTest --tests "*BundledFeatLibraryGenerator*" -Dgenerate.feats=true
 * ```
 * Генератор намеренно использует тот же [DndSuFeatParser], что и загрузка черты по
 * ссылке в приложении: формат описания гарантированно совпадает, и встроенные черты
 * неотличимы от загруженных пользователем вручную.
 */
class BundledFeatLibraryGenerator {

    @Test
    fun `generate bundled feat library`() {
        assumeTrue(
            "Запуск только с -D$GENERATE_PROPERTY=true",
            System.getProperty(GENERATE_PROPERTY).toBoolean(),
        )

        val urls = loadFeatUrls()
        assertTrue("Каталог черт пуст — изменилась разметка dnd.su?", urls.isNotEmpty())

        val feats = urls.mapIndexedNotNull { index, url ->
            println("[${index + 1}/${urls.size}] $url")
            runCatching {
                val html = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get()
                    .outerHtml()
                val parsed = DndSuFeatParser.parse(html)
                Feat(
                    name = parsed.name,
                    description = parsed.description,
                    source = url,
                    book = parsed.book,
                )
            }.onFailure { println("  ! пропущено: ${it.message}") }
                .getOrNull()
                .also { Thread.sleep(REQUEST_DELAY_MS) }
        }

        assertTrue("Не удалось разобрать ни одной черты", feats.isNotEmpty())

        // Стабильный порядок: так обновление библиотеки даёт минимальный diff.
        val sorted = feats.sortedBy { it.name.lowercase() }
        println("Книги: " + sorted.groupingBy { it.book.ifBlank { "<нет>" } }.eachCount())
        val output = File(ASSETS_DIR, OUTPUT_FILE)
        output.parentFile?.mkdirs()
        output.writeText(FeatLibraryCodec.encodeList(sorted))

        println("Сохранено ${sorted.size} черт в ${output.absolutePath}")
    }

    /**
     * Ссылки на черты со страницы каталога.
     *
     * Карта сайта (`sitemap.xml`) для черт не подходит: в ней есть только сама страница
     * раздела, без ссылок на отдельные черты — в отличие от заклинаний.
     */
    private fun loadFeatUrls(): List<String> {
        val html = Jsoup.connect(CATALOG_URL)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .get()

        return html.select("a[href]")
            .map { it.attr("href") }
            .filter { FEAT_PATH_REGEX.containsMatchIn(it) }
            // Пользовательские материалы в официальную библиотеку не попадают.
            .filterNot { it.contains(HOMEBREW_MARKER, ignoreCase = true) }
            .map { path -> if (path.startsWith("http")) path else BASE_URL + path.removePrefix("/") }
            .distinct()
            .sorted()
    }

    private companion object {
        /** Флаг ручного запуска; пробрасывается в JVM тестов из app/build.gradle.kts. */
        const val GENERATE_PROPERTY = "generate.feats"

        const val BASE_URL = "https://dnd.su/"
        const val CATALOG_URL = "https://dnd.su/feats/"
        const val USER_AGENT = "Mozilla/5.0 (Android) SpellBook/1.0"
        const val TIMEOUT_MS = 30_000

        /** Пауза между запросами, чтобы не создавать нагрузку на сайт. */
        const val REQUEST_DELAY_MS = 300L

        const val HOMEBREW_MARKER = "/homebrew/"
        val FEAT_PATH_REGEX = Regex("/feats/\\d+-")

        /** Путь относительно каталога модуля `app`, откуда Gradle запускает тесты. */
        const val ASSETS_DIR = "src/main/assets"
        const val OUTPUT_FILE = "feats-library.json"
    }
}
