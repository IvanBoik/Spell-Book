package com.example.spellbook.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.StatFormula
import com.example.spellbook.ui.formulaVariableLabel

/**
 * Поле для числа или формулы от характеристик персонажа.
 *
 * Значение вводится в одном поле: можно написать обычное число (`3`) либо
 * выражение с переменными в квадратных скобках (`[pb] * 2`). Как только
 * пользователь открывает скобку, показывается список доступных переменных.
 */
@Composable
fun FormulaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "1d8 + [str]",
    isError: Boolean = false,
) {
    // Курсор нужен, чтобы понимать, где именно набирается переменная.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }

    val caret = fieldValue.selection.end
    val showSuggestions = StatFormula.isTypingVariable(fieldValue.text, caret)
    val valid = StatFormula.isValid(fieldValue.text)

    Box(modifier = modifier) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { updated ->
                fieldValue = updated
                onValueChange(updated.text)
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            isError = isError || !valid,
            // Подсказка показывается только при ошибке: формат виден из плейсхолдера.
            supportingText = if (valid) null else {
                { Text(stringResource(R.string.formula_invalid)) }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        DropdownMenu(
            expanded = showSuggestions,
            // Фокус остаётся в поле: список закрывается сам, когда скобка закрыта.
            onDismissRequest = {},
            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            StatFormula.SUGGESTIONS.forEach { variable ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.formula_variable_hint,
                                variable.code,
                                formulaVariableLabel(variable),
                            ),
                        )
                    },
                    onClick = {
                        val (text, cursor) = StatFormula.insertVariable(fieldValue.text, caret, variable)
                        fieldValue = TextFieldValue(text, TextRange(cursor))
                        onValueChange(text)
                    },
                )
            }
        }
    }
}
