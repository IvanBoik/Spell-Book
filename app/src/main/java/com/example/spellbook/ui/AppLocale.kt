package com.example.spellbook.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.example.spellbook.data.AppLanguage
import java.util.Locale

/**
 * Применяет выбранный язык интерфейса ко всему дереву композиции.
 *
 * Локаль подменяется на уровне Compose, а не через `LocaleManager`: так смена языка
 * действует сразу и одинаково на всех поддерживаемых версиях Android (minSdk 30).
 * Переводятся только строковые ресурсы приложения — пользовательские материалы
 * (персонажи, заклинания, черты) хранятся в базе и остаются как есть.
 */
@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val locale = language.locale

    if (locale == null) {
        // Системный язык: ничего не подменяем, чтобы уважать настройки устройства.
        content()
        return
    }

    val localizedContext = remember(context, locale) { LocalizedContext(context, locale) }
    val configuration = remember(localizedContext) { localizedContext.resources.configuration }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalResources provides localizedContext.resources,
        LocalConfiguration provides configuration,
        content = content,
    )
}

/**
 * Контекст с принудительной локалью [locale].
 *
 * Важно, что это именно [ContextWrapper] поверх исходного контекста, а не результат
 * `createConfigurationContext`: Compose ищет владельцев (`ActivityResultRegistryOwner`,
 * `OnBackPressedDispatcherOwner`) обходом цепочки `baseContext` до Activity. Отдельный
 * контекст такую цепочку разрывает, из-за чего падали выбор файла и системная кнопка «Назад».
 */
private class LocalizedContext(base: Context, locale: Locale) : ContextWrapper(base) {

    private val localizedResources: Resources = run {
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        base.createConfigurationContext(configuration).resources
    }

    override fun getResources(): Resources = localizedResources
}
