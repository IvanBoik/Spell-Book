package com.example.spellbook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.AppLanguage
import com.example.spellbook.data.AppTheme
import com.example.spellbook.data.SectionLayout
import com.example.spellbook.data.model.Character
import com.example.spellbook.ui.components.CharacterSection
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.DragReorderState
import com.example.spellbook.ui.components.dragToReorder
import com.example.spellbook.ui.components.ordered
import com.example.spellbook.ui.components.rememberDragReorderState
import com.example.spellbook.ui.components.arrange

/** Отступы карточек и блоков экрана настроек. */
private val SECTION_SPACING = 12.dp
private val CARD_PADDING = 16.dp

/** Шаг перестановки при перетаскивании — высота строки раздела вместе с отступом. */
private val SECTION_ROW_HEIGHT = 68.dp

/**
 * Экран настроек приложения: язык, тема и состав панели разделов персонажа.
 *
 * Панель настраивается либо сразу для всех персонажей, либо отдельно для выбранного:
 * личная настройка перекрывает общую, а её сброс возвращает персонажа к общим правилам.
 *
 * @param scopeCharacterId null — редактируется общая настройка, иначе настройка персонажа.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    language: AppLanguage,
    theme: AppTheme,
    characters: List<Character>,
    scopeCharacterId: String?,
    onScopeChange: (String?) -> Unit,
    /** Действующая раскладка выбранной области (для персонажа — уже с учётом наследования). */
    layout: SectionLayout,
    /** Есть ли у выбранного персонажа собственная настройка панели. */
    hasOwnLayout: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    onLayoutChange: (SectionLayout) -> Unit,
    /** Сброс: у персонажа — к общей настройке, у общей области — к порядку по умолчанию. */
    onLayoutReset: () -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        // Настройки — корневая вкладка нижней навигации, поэтому стрелки «назад» в шапке нет.
        topBar = { DndTopBar(title = stringResource(R.string.settings_title)) },
        bottomBar = bottomBar,
    ) { padding ->
        // В настройках показываем все разделы, включая скрытые: иначе отключённый раздел
        // исчез бы из списка и его нельзя было бы вернуть.
        val dragState = rememberDragReorderState<String>(step = SECTION_ROW_HEIGHT)
        // Во время жеста показываем порядок, который строит сам жест.
        val sections = dragState.order(CharacterSection.ordered(layout).map { it.name })
            .mapNotNull { CharacterSection.fromName(it) }


        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
        ) {
            item {
                GeneralSettingsCard(
                    language = language,
                    theme = theme,
                    onLanguageChange = onLanguageChange,
                    onThemeChange = onThemeChange,
                )
            }

            item {
                SectionsScopeCard(
                    characters = characters,
                    scopeCharacterId = scopeCharacterId,
                    hasOwnLayout = hasOwnLayout,
                    onScopeChange = onScopeChange,
                    onLayoutReset = onLayoutReset,
                    // Первое изменение личной настройки начинается с текущей действующей.
                    onCustomize = { onLayoutChange(layout) },
                )
            }

            sectionItems(sections, layout, dragState, onLayoutChange)

            item {
                Text(
                    stringResource(R.string.settings_sections_prepare_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Строки списка разделов: переключатель видимости и перетаскивание для смены порядка.
 *
 * Главная страница персонажа отмечена подписью: с неё начинается просмотр
 * персонажа и туда же возвращает кнопка «Назад».
 */
private fun LazyListScope.sectionItems(
    sections: List<CharacterSection>,
    layout: SectionLayout,
    dragState: DragReorderState<String>,
    onLayoutChange: (SectionLayout) -> Unit,
) {
    item {
        Text(
            stringResource(R.string.settings_sections_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val order = sections.map { it.name }
    items(sections, key = { it.name }) { section ->
        SectionRow(
            section = section,
            visible = section.name !in layout.hidden,
            isHome = section == CharacterSection.HOME,
            dragModifier = Modifier.dragToReorder(
                state = dragState,
                id = section.name,
                currentOrder = order,
                onReorder = { newOrder -> onLayoutChange(layout.copy(order = newOrder)) },
            ),
            onToggle = { checked ->
                val hidden = if (checked) layout.hidden - section.name else layout.hidden + section.name
                onLayoutChange(layout.copy(order = order, hidden = hidden))
            },
        )
    }
}

/** Карточка общих настроек: язык (с пояснением) и тема. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralSettingsCard(
    language: AppLanguage,
    theme: AppTheme,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (AppTheme) -> Unit,
) {
    SettingsCard(title = stringResource(R.string.settings_general)) {
        Text(
            stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { option ->
                FilterChip(
                    selected = option == language,
                    onClick = { onLanguageChange(option) },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
        // Важное уточнение: пользовательский контент не переводится.
        Text(
            stringResource(R.string.settings_language_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Text(
            stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTheme.entries.forEach { option ->
                FilterChip(
                    selected = option == theme,
                    onClick = { onThemeChange(option) },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
    }
}

/** Выбор области настройки панели: все персонажи или конкретный персонаж. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionsScopeCard(
    characters: List<Character>,
    scopeCharacterId: String?,
    hasOwnLayout: Boolean,
    onScopeChange: (String?) -> Unit,
    onLayoutReset: () -> Unit,
    onCustomize: () -> Unit,
) {
    SettingsCard(title = stringResource(R.string.settings_sections)) {
        Text(
            stringResource(R.string.settings_sections_scope),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = scopeCharacterId == null,
                onClick = { onScopeChange(null) },
                label = { Text(stringResource(R.string.settings_sections_scope_all)) },
            )
            characters.forEach { character ->
                FilterChip(
                    selected = character.id == scopeCharacterId,
                    onClick = { onScopeChange(character.id) },
                    label = { Text(character.name.ifBlank { stringResource(R.string.section_settings) }) },
                )
            }
        }
        if (characters.isEmpty()) {
            Text(
                stringResource(R.string.settings_no_characters),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            scopeCharacterId == null -> TextButton(onClick = onLayoutReset) {
                Text(stringResource(R.string.settings_sections_reset_all))
            }

            hasOwnLayout -> TextButton(onClick = onLayoutReset) {
                Text(stringResource(R.string.settings_sections_reset_character))
            }

            else -> {
                Text(
                    stringResource(R.string.settings_sections_inherited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onCustomize) {
                    Text(stringResource(R.string.settings_sections_customize))
                }
            }
        }
    }
}

/** Одна строка настройки раздела. */
@Composable
private fun SectionRow(
    section: CharacterSection,
    visible: Boolean,
    /** Главная страница персонажа — точка входа и цель кнопки «Назад». */
    isHome: Boolean,
    dragModifier: Modifier,
    onToggle: (Boolean) -> Unit,
) {
    // Главную страницу и настройки персонажа скрыть нельзя.
    val locked = section in CharacterSection.ALWAYS_VISIBLE

    Card(
        modifier = Modifier.fillMaxWidth().then(dragModifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = stringResource(R.string.settings_sections_drag),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                section.icon,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(section.labelRes), style = MaterialTheme.typography.titleMedium)
                val hintRes = when {
                    isHome -> R.string.settings_sections_home
                    locked -> R.string.settings_sections_locked
                    else -> null
                }
                hintRes?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (locked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.settings_sections_locked),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Switch(checked = visible, onCheckedChange = onToggle)
            }
        }
    }
}

/** Общая карточка-обёртка блока настроек. */
@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Подпись языка в списке выбора. */
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.RUSSIAN -> R.string.settings_language_ru
    AppLanguage.ENGLISH -> R.string.settings_language_en
}

/** Подпись темы в списке выбора. */
private fun AppTheme.labelRes(): Int = when (this) {
    AppTheme.SYSTEM -> R.string.settings_theme_system
    AppTheme.LIGHT -> R.string.settings_theme_light
    AppTheme.DARK -> R.string.settings_theme_dark
}
