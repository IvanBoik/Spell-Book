package com.example.spellbook.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.spellbook.R
import com.example.spellbook.data.auth.AuthConfig
import com.example.spellbook.data.auth.AuthState
import com.example.spellbook.data.auth.DeviceSession
import com.example.spellbook.ui.UiMessage
import com.example.spellbook.ui.components.DndTopBar
import com.example.spellbook.ui.components.imeAwareContentInsets
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Отступы содержимого экрана аккаунта. */
private val CONTENT_PADDING = 16.dp

/**
 * Экран аккаунта.
 *
 * Пока пользователь не вошёл — формы входа, регистрации и восстановления пароля;
 * после входа — профиль, список устройств и действия с аккаунтом. Аккаунт
 * опционален: без него всё приложение работает локально.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AuthViewModel,
    onClose: () -> Unit,
) {
    val signedIn = viewModel.authState as? AuthState.SignedIn

    LaunchedEffect(Unit) { viewModel.onScreenOpened() }

    val back = { if (!viewModel.navigateBack()) onClose() }

    Scaffold(
        topBar = {
            DndTopBar(
                title = stringResource(
                    if (signedIn != null) R.string.account_title else viewModel.step.titleRes(),
                ),
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (signedIn != null) {
                        IconButton(onClick = viewModel::loadProfile, enabled = !viewModel.profileLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.account_refresh))
                        }
                    }
                },
            )
        },
        // Поля ввода не должны уходить под клавиатуру.
        contentWindowInsets = imeAwareContentInsets,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CONTENT_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (signedIn != null) {
                ProfileContent(viewModel = viewModel, email = signedIn.email)
            } else {
                when (viewModel.step) {
                    AuthStep.LOGIN -> LoginForm(viewModel)
                    AuthStep.REGISTER -> RegisterForm(viewModel)
                    AuthStep.VERIFY_EMAIL -> VerifyEmailForm(viewModel)
                    AuthStep.FORGOT_PASSWORD -> ForgotPasswordForm(viewModel)
                    AuthStep.RESET_PASSWORD -> ResetPasswordForm(viewModel)
                }
            }
        }
    }

    AccountDialogs(viewModel)
}

private fun AuthStep.titleRes(): Int = when (this) {
    AuthStep.LOGIN -> R.string.auth_login_title
    AuthStep.REGISTER -> R.string.auth_register_title
    AuthStep.VERIFY_EMAIL -> R.string.auth_verify_title
    AuthStep.FORGOT_PASSWORD, AuthStep.RESET_PASSWORD -> R.string.auth_reset_title
}

// region Формы входа

@Composable
private fun LoginForm(viewModel: AuthViewModel) {
    Text(
        stringResource(R.string.auth_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EmailField(viewModel.email, viewModel::updateEmail, enabled = !viewModel.loading)
    PasswordField(
        value = viewModel.password,
        onValueChange = viewModel::updatePassword,
        label = stringResource(R.string.auth_password),
        enabled = !viewModel.loading,
        imeAction = ImeAction.Done,
        onDone = viewModel::login,
    )
    FormStatus(viewModel)
    PrimaryButton(R.string.auth_login_action, viewModel.loading, viewModel::login)
    TextButton(onClick = { viewModel.goTo(AuthStep.FORGOT_PASSWORD) }, enabled = !viewModel.loading) {
        Text(stringResource(R.string.auth_forgot_password))
    }
    HorizontalDivider()
    Text(stringResource(R.string.auth_no_account), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(
        onClick = { viewModel.goTo(AuthStep.REGISTER) },
        enabled = !viewModel.loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.auth_register_action))
    }
}

@Composable
private fun RegisterForm(viewModel: AuthViewModel) {
    EmailField(viewModel.email, viewModel::updateEmail, enabled = !viewModel.loading)
    PasswordField(
        value = viewModel.password,
        onValueChange = viewModel::updatePassword,
        label = stringResource(R.string.auth_password),
        enabled = !viewModel.loading,
        supportingText = stringResource(R.string.auth_password_rules),
    )
    PasswordField(
        value = viewModel.passwordRepeat,
        onValueChange = viewModel::updatePasswordRepeat,
        label = stringResource(R.string.auth_password_repeat),
        enabled = !viewModel.loading,
        imeAction = ImeAction.Done,
        onDone = viewModel::register,
    )
    FormStatus(viewModel)
    PrimaryButton(R.string.auth_register_action, viewModel.loading, viewModel::register)
    TextButton(onClick = { viewModel.goTo(AuthStep.LOGIN) }, enabled = !viewModel.loading) {
        Text(stringResource(R.string.auth_have_account))
    }
}

@Composable
private fun VerifyEmailForm(viewModel: AuthViewModel) {
    CodeField(viewModel.code, viewModel::updateCode, enabled = !viewModel.loading, onDone = viewModel::verifyEmail)
    FormStatus(viewModel)
    PrimaryButton(R.string.auth_verify_action, viewModel.loading, viewModel::verifyEmail)
    ResendButton(viewModel, onResend = viewModel::resendVerification)
}

@Composable
private fun ForgotPasswordForm(viewModel: AuthViewModel) {
    Text(
        stringResource(R.string.auth_forgot_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EmailField(
        viewModel.email,
        viewModel::updateEmail,
        enabled = !viewModel.loading,
        imeAction = ImeAction.Done,
        onDone = viewModel::forgotPassword,
    )
    FormStatus(viewModel)
    PrimaryButton(R.string.auth_send_code, viewModel.loading, viewModel::forgotPassword)
}

@Composable
private fun ResetPasswordForm(viewModel: AuthViewModel) {
    CodeField(viewModel.code, viewModel::updateCode, enabled = !viewModel.loading)
    PasswordField(
        value = viewModel.password,
        onValueChange = viewModel::updatePassword,
        label = stringResource(R.string.auth_new_password),
        enabled = !viewModel.loading,
        supportingText = stringResource(R.string.auth_password_rules),
    )
    PasswordField(
        value = viewModel.passwordRepeat,
        onValueChange = viewModel::updatePasswordRepeat,
        label = stringResource(R.string.auth_password_repeat),
        enabled = !viewModel.loading,
        imeAction = ImeAction.Done,
        onDone = viewModel::resetPassword,
    )
    Text(
        stringResource(R.string.auth_reset_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FormStatus(viewModel)
    PrimaryButton(R.string.auth_reset_action, viewModel.loading, viewModel::resetPassword)
    ResendButton(viewModel, onResend = viewModel::resendResetCode)
}

/**
 * Кнопка повторной отправки кода с обратным отсчётом.
 *
 * Сервер не отправит новый код раньше, чем через минуту после предыдущего,
 * поэтому кнопка до этого момента неактивна и показывает, сколько ждать.
 */
@Composable
private fun ResendButton(viewModel: AuthViewModel, onResend: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val availableAt = viewModel.resendAvailableAtMillis
    LaunchedEffect(availableAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= availableAt) break
            delay(1_000)
        }
    }
    val secondsLeft = ((availableAt - now + 999) / 1000).toInt()

    TextButton(
        onClick = onResend,
        enabled = secondsLeft <= 0 && !viewModel.loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (secondsLeft > 0) {
                stringResource(R.string.auth_resend_in, secondsLeft)
            } else {
                stringResource(R.string.auth_resend)
            },
        )
    }
}

// endregion

// region Профиль

@Composable
private fun ProfileContent(viewModel: AuthViewModel, email: String) {
    val account = viewModel.account

    AccountCard(title = stringResource(R.string.account_profile)) {
        Text(email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        account?.createdAtMillis?.let {
            Text(
                stringResource(R.string.account_created_at, formatDate(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.account_sync_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    viewModel.profileError?.let { ErrorText(it) }

    AccountCard(title = stringResource(R.string.account_devices)) {
        when {
            viewModel.profileLoading && viewModel.sessions.isEmpty() ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }

            viewModel.sessions.isEmpty() -> Text(
                stringResource(R.string.account_devices_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> viewModel.sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider()
                SessionRow(session, onTerminate = { viewModel.requestTerminateSession(session) })
            }
        }
    }

    AccountCard(title = stringResource(R.string.account_security)) {
        OutlinedButton(
            onClick = { viewModel.openDialog(AccountDialog.CHANGE_PASSWORD) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.account_change_password)) }
        OutlinedButton(
            onClick = { viewModel.openDialog(AccountDialog.LOGOUT_ALL) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.account_logout_all)) }
    }

    Button(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.account_logout))
    }

    // Удаление — необратимое действие, поэтому отдельно и неброско, в самом низу.
    TextButton(
        onClick = { viewModel.openDialog(AccountDialog.DELETE_ACCOUNT) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SessionRow(session: DeviceSession, onTerminate: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Devices, contentDescription = null)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.deviceName.ifBlank { stringResource(R.string.account_device_unknown) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (session.current) FontWeight.Bold else FontWeight.Normal,
            )
            val subtitle = if (session.current) {
                stringResource(R.string.account_device_current)
            } else {
                session.lastUsedAtMillis?.let { stringResource(R.string.account_device_last_used, formatDate(it)) }
            }
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Своё устройство закрывается кнопкой «Выйти»: так понятнее, что будет дальше.
        if (!session.current) {
            IconButton(onClick = onTerminate) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.account_session_terminate))
            }
        }
    }
}

@Composable
private fun AccountDialogs(viewModel: AuthViewModel) {
    when (viewModel.dialog) {
        AccountDialog.CHANGE_PASSWORD -> FormDialog(
            title = stringResource(R.string.account_change_password),
            confirmText = stringResource(R.string.action_save),
            viewModel = viewModel,
            onConfirm = viewModel::changePassword,
        ) {
            PasswordField(
                value = viewModel.password,
                onValueChange = viewModel::updatePassword,
                label = stringResource(R.string.auth_current_password),
                enabled = !viewModel.loading,
            )
            PasswordField(
                value = viewModel.newPassword,
                onValueChange = viewModel::updateNewPassword,
                label = stringResource(R.string.auth_new_password),
                enabled = !viewModel.loading,
                supportingText = stringResource(R.string.auth_password_rules),
            )
            PasswordField(
                value = viewModel.passwordRepeat,
                onValueChange = viewModel::updatePasswordRepeat,
                label = stringResource(R.string.auth_password_repeat),
                enabled = !viewModel.loading,
            )
            Text(
                stringResource(R.string.account_change_password_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AccountDialog.DELETE_ACCOUNT -> FormDialog(
            title = stringResource(R.string.account_delete_title),
            confirmText = stringResource(R.string.action_delete),
            viewModel = viewModel,
            onConfirm = viewModel::deleteAccount,
            destructive = true,
        ) {
            Text(stringResource(R.string.account_delete_text))
            PasswordField(
                value = viewModel.password,
                onValueChange = viewModel::updatePassword,
                label = stringResource(R.string.auth_password),
                enabled = !viewModel.loading,
            )
        }

        AccountDialog.LOGOUT_ALL -> FormDialog(
            title = stringResource(R.string.account_logout_all),
            confirmText = stringResource(R.string.account_logout_all_confirm),
            viewModel = viewModel,
            onConfirm = viewModel::logoutAll,
        ) {
            Text(stringResource(R.string.account_logout_all_text))
        }

        null -> Unit
    }

    viewModel.sessionToTerminate?.let { session ->
        AlertDialog(
            onDismissRequest = viewModel::cancelTerminateSession,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.account_session_terminate_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.account_session_terminate_text,
                        session.deviceName.ifBlank { stringResource(R.string.account_device_unknown) },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmTerminateSession) {
                    Text(stringResource(R.string.account_session_terminate))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelTerminateSession) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Диалог с полями, ошибкой и индикатором запроса. Пока запрос идёт, закрыть его нельзя. */
@Composable
private fun FormDialog(
    title: String,
    confirmText: String,
    viewModel: AuthViewModel,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = viewModel::closeDialog,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                viewModel.error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !viewModel.loading) {
                if (viewModel.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        confirmText,
                        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeDialog, enabled = !viewModel.loading) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AccountCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CONTENT_PADDING),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

// endregion

// region Поля

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.auth_email)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Поле пароля с кнопкой «показать»: на телефоне опечатку в скрытом пароле легко не заметить. */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.auth_password_hide else R.string.auth_password_show,
                    ),
                )
            }
        },
        supportingText = supportingText?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.auth_code)) },
        placeholder = { Text("0".repeat(AuthConfig.CODE_LENGTH)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        textStyle = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Пояснение («код отправлен на …») и ошибка формы. */
@Composable
private fun FormStatus(viewModel: AuthViewModel) {
    val context = LocalContext.current
    viewModel.notice?.let {
        Text(
            it.resolve(context),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    viewModel.error?.let { ErrorText(it) }
}

@Composable
private fun ErrorText(message: UiMessage) {
    Text(
        message.resolve(LocalContext.current),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun PrimaryButton(textRes: Int, loading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(textRes))
        }
    }
}

// endregion

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
