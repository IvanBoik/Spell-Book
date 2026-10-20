# SpellBook API: авторизация (для мобильного клиента)

Авторизация в приложении **опциональна** и нужна только для синхронизации данных между устройствами.
Вход по почте и паролю, почта подтверждается 6-значным кодом из письма.

## Общее

| | |
|---|---|
| Base URL | `http://185.240.103.127` (временно, без HTTPS — см. раздел «Важно») |
| Формат запросов | `Content-Type: application/json`, UTF-8 |
| Формат ошибок | `Content-Type: application/problem+json` |
| Авторизация | заголовок `Authorization: Bearer <accessToken>` |
| Даты | ISO-8601 в UTC, например `2026-10-06T10:09:23.294Z` (дробная часть секунд может быть или не быть) |
| Идентификаторы | UUID-строки |

## Токены

| Токен | Срок жизни | Назначение |
|---|---|---|
| `accessToken` | 15 минут | передаётся в `Authorization: Bearer ...` во всех защищённых запросах |
| `refreshToken` | 60 дней | только для `POST /auth/refresh`; **одноразовый** — после обновления старый перестаёт работать |

Каждый вход (`login`, `verify-email`, `password/reset`) создаёт отдельную **сессию** — одно устройство.
Пользователь видит список своих устройств и может завершить любое.

Разбирать JWT на клиенте не нужно: срок действия приходит в `accessTokenExpiresAt`.

### Ответ с токенами — `TokenResponse`

Возвращается из `login`, `verify-email`, `password/reset`, `refresh`.

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresAt": "2026-10-06T10:24:23Z",
  "refreshToken": "Qm9vbGVhbi1yYW5kb20tMzItYnl0ZXMtYmFzZTY0dXJs",
  "refreshTokenExpiresAt": "2026-12-05T10:09:23Z",
  "sessionId": "4ddfb73b-11af-4b70-a0bf-98ab6676c6fc",
  "tokenType": "Bearer"
}
```

## Ошибки

Все ошибки приходят в одном формате. **Ориентируйтесь на поле `code`** — это стабильный контракт.
`detail` — текст для отладки, его не нужно показывать пользователю и на него нельзя завязываться.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid confirmation code",
  "instance": "/auth/verify-email",
  "code": "INVALID_CODE",
  "attemptsLeft": 4
}
```

Дополнительные поля, которые могут появиться:

- `retryAfterSeconds` (число) — через сколько секунд можно повторить; дублируется заголовком `Retry-After`.
- `attemptsLeft` (число) — сколько попыток ввода кода осталось.
- `errors` (массив `{ "field": "...", "message": "..." }`) — только для `VALIDATION_ERROR`.

### Общие коды (могут прийти с любого эндпоинта)

| HTTP | `code` | Что делать |
|---|---|---|
| 400 | `VALIDATION_ERROR` | поля не прошли проверку, см. `errors` |
| 400 | `MALFORMED_REQUEST` | тело запроса не JSON / не тот формат |
| 401 | `UNAUTHORIZED` | не передан `Authorization` |
| 401 | `INVALID_ACCESS_TOKEN` | access-токен истёк или повреждён → вызвать `/auth/refresh` |
| 409 | `CONFLICT` | одновременное изменение, повторить запрос |

## Ограничения полей

| Поле | Правило |
|---|---|
| `email` | валидный email, до 254 символов. Сервер приводит к нижнему регистру; пробелы по краям лучше обрезать на клиенте |
| `password`, `newPassword` | от 8 до 72 символов **и** не больше 72 байт в UTF-8 (кириллица занимает 2 байта, т.е. максимум ~36 русских символов); иначе `PASSWORD_TOO_LONG` |
| `password` при входе / `currentPassword` | минимальная длина не проверяется |
| `code` | только цифры (сейчас всегда 6) |
| `deviceName` | необязательно, до 100 символов, например `"Pixel 8"` или `"iPhone 15 Pro"`. Показывается в списке устройств |
| `refreshToken` | непустая строка до 200 символов |

---

## Публичные эндпоинты (без `Authorization`)

### `POST /auth/register` — регистрация

```json
{ "email": "user@example.com", "password": "Password123" }
```

**202** → `CodeSentResponse`:

```json
{ "resendAvailableInSeconds": 60 }
```

На почту уходит код (действует 15 минут). Если почта уже зарегистрирована, но не подтверждена,
пароль перезаписывается, и регистрацию можно пройти заново. Если код уже отправлялся меньше
минуты назад, новый не отправляется, а `resendAvailableInSeconds` показывает оставшееся время.

| HTTP | `code` | |
|---|---|---|
| 409 | `EMAIL_ALREADY_REGISTERED` | почта уже подтверждена → предложить вход / восстановление пароля |
| 400 | `PASSWORD_TOO_LONG` | |

Неподтверждённые аккаунты удаляются через 7 дней.

### `POST /auth/verify-email` — подтверждение почты

```json
{ "email": "user@example.com", "code": "123456", "deviceName": "Pixel 8" }
```

**200** → `TokenResponse`. Пользователь сразу залогинен.

| HTTP | `code` | |
|---|---|---|
| 400 | `INVALID_CODE` | неверный код; `attemptsLeft` — сколько попыток осталось (может отсутствовать, если кода нет вообще) |
| 400 | `CODE_EXPIRED` | код истёк → запросить новый |
| 400 | `CODE_ATTEMPTS_EXCEEDED` | 5 неверных попыток, код аннулирован → запросить новый |

### `POST /auth/verify-email/resend` — отправить код повторно

```json
{ "email": "user@example.com" }
```

**202** → `CodeSentResponse`.

| HTTP | `code` | |
|---|---|---|
| 429 | `CODE_RESEND_TOO_EARLY` | рано, см. `retryAfterSeconds` (таймер на кнопке «Отправить ещё раз») |

Для незарегистрированных и уже подтверждённых адресов тоже отвечает 202, письмо не отправляется.

### `POST /auth/login` — вход

```json
{ "email": "user@example.com", "password": "Password123", "deviceName": "Pixel 8" }
```

**200** → `TokenResponse`.

| HTTP | `code` | |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | неверная почта или пароль (сервер не различает эти случаи) |
| 403 | `EMAIL_NOT_VERIFIED` | пароль верный, но почта не подтверждена → экран ввода кода + `verify-email/resend` |
| 429 | `LOGIN_LOCKED` | 10 неверных паролей подряд, вход заблокирован на 15 минут, см. `retryAfterSeconds` |

### `POST /auth/refresh` — обновить токены

```json
{ "refreshToken": "Qm9vbGVhbi1yYW5kb20tMzItYnl0ZXMtYmFzZTY0dXJs" }
```

**200** → `TokenResponse` с **новыми** access- и refresh-токенами. Сохраните оба, старый refresh больше не действует.

| HTTP | `code` | |
|---|---|---|
| 401 | `INVALID_REFRESH_TOKEN` | токен истёк, отозван или уже использован → разлогинить пользователя локально |

Заголовок `Authorization` на этом эндпоинте игнорируется, просроченный access-токен ему не мешает.

### `POST /auth/password/forgot` — запросить код для сброса пароля

```json
{ "email": "user@example.com" }
```

**202** → `CodeSentResponse`. Ответ **всегда** одинаковый, даже если почта не зарегистрирована,
поэтому показывайте нейтральное «Если аккаунт существует, мы отправили код».

### `POST /auth/password/reset` — установить новый пароль по коду

```json
{ "email": "user@example.com", "code": "123456", "newPassword": "NewPassword456", "deviceName": "Pixel 8" }
```

**200** → `TokenResponse`. **Все остальные устройства разлогиниваются**, текущее получает новые токены.

Ошибки: `INVALID_CODE`, `CODE_EXPIRED`, `CODE_ATTEMPTS_EXCEEDED`, `PASSWORD_TOO_LONG` (как у `verify-email`).

---

## Защищённые эндпоинты (`Authorization: Bearer <accessToken>`)

### `GET /account` — текущий пользователь

**200** → `AccountResponse`:

```json
{
  "id": "e866256f-ea7d-4fd0-8217-4cbf562284a3",
  "email": "user@example.com",
  "emailVerifiedAt": "2026-10-06T10:09:45Z",
  "createdAt": "2026-10-06T10:08:53Z"
}
```

| HTTP | `code` | |
|---|---|---|
| 404 | `ACCOUNT_NOT_FOUND` | аккаунт удалён (например, с другого устройства) → разлогинить локально |

### `POST /account/password` — сменить пароль

```json
{ "currentPassword": "Password123", "newPassword": "NewPassword456" }
```

**204**. Остальные устройства разлогиниваются, текущее остаётся залогиненным с теми же токенами.

| HTTP | `code` | |
|---|---|---|
| 403 | `INVALID_PASSWORD` | неверный текущий пароль |
| 400 | `PASSWORD_TOO_LONG` | |

### `DELETE /account` — удалить аккаунт

Требование App Store / Google Play. Удаляет аккаунт и все его данные на сервере безвозвратно.

```json
{ "password": "Password123" }
```

**204**. После этого удалите токены локально.

| HTTP | `code` | |
|---|---|---|
| 403 | `INVALID_PASSWORD` | неверный пароль |

> У запроса `DELETE` есть тело. В Retrofit обычный `@DELETE` не позволяет передать `@Body`,
> используйте `@HTTP(method = "DELETE", path = "account", hasBody = true)`.

### `GET /account/sessions` — список устройств

**200** → массив `SessionResponse`, отсортирован по последней активности:

```json
[
  {
    "id": "4ddfb73b-11af-4b70-a0bf-98ab6676c6fc",
    "deviceName": "Pixel 8",
    "createdAt": "2026-10-06T10:09:45Z",
    "lastUsedAt": "2026-10-06T12:30:00Z",
    "current": true
  }
]
```

`current: true` — устройство, с которого сделан запрос. `lastUsedAt` обновляется при каждом `refresh`.

### `DELETE /account/sessions/{sessionId}` — завершить сессию устройства

**204**. 404 `SESSION_NOT_FOUND`, если такой сессии нет у пользователя.

### `POST /account/logout` — выход с текущего устройства

**204**. После ответа удалите токены локально.

### `POST /account/logout-all` — выход со всех устройств (включая текущее)

**204**.

---

## Рекомендуемая логика клиента

### Хранение
- `refreshToken` — только в защищённом хранилище: Android Keystore / EncryptedSharedPreferences, iOS Keychain.
- `accessToken` можно держать в памяти.

### Обновление токенов
1. Перед запросом: если до `accessTokenExpiresAt` осталось меньше ~60 секунд, сначала вызвать `/auth/refresh`.
2. Если запрос вернул **401 `INVALID_ACCESS_TOKEN`**: выполнить `/auth/refresh` и **один раз** повторить запрос.
3. Если `/auth/refresh` вернул **401 `INVALID_REFRESH_TOKEN`**: удалить токены, перевести приложение в
   локальный режим без синхронизации и предложить войти снова.

### ⚠️ Refresh должен выполняться строго в одном экземпляре
Refresh-токен одноразовый. Если отправить его повторно (два параллельных `refresh` или повтор
запроса после обрыва сети), сервер считает это кражей токена и **завершает сессию устройства**.

- Все обновления токенов — через один mutex / single-flight: параллельные запросы, получившие 401,
  ждут результат одного общего `refresh`, а не запускают свои.
  В OkHttp удобно делать это в `Authenticator` с `synchronized`.
- Новый `refreshToken` сохранять сразу после получения ответа, до любых других действий.
- Не включать автоматические повторы (retry) на уровне HTTP-клиента для `/auth/refresh`.

### После выхода / смены пароля на другом устройстве
Access-токен живёт до 15 минут и после выхода технически остаётся действительным до истечения срока.
Отзыв сессии срабатывает на следующем `refresh` (`INVALID_REFRESH_TOKEN`). Поэтому при `logout`
всегда удаляйте токены локально, не дожидаясь ответа сервера.

### Сценарии экранов

```
Регистрация:  register → [экран кода] → verify-email → залогинен
                            └─ resend (таймер по resendAvailableInSeconds / retryAfterSeconds)

Вход:         login → залогинен
                └─ 403 EMAIL_NOT_VERIFIED → verify-email/resend → [экран кода] → verify-email

Забыл пароль: password/forgot → [экран кода + новый пароль] → password/reset → залогинен
```

---

## Важно на текущем этапе

- **Нет HTTPS.** Сервер пока доступен только по `http://` и IP. Android (API 28+) и iOS по умолчанию
  блокируют такие запросы, поэтому для разработки нужно разрешить cleartext-трафик к этому адресу:
  - Android: `network_security_config.xml` с `<domain-config cleartextTrafficPermitted="true">` для IP
    (только в debug-сборке);
  - iOS: исключение `NSAppTransportSecurity` → `NSExceptionDomains` в `Info.plist`.

  Пароли и токены сейчас идут по сети в открытом виде, поэтому реальные пароли не используйте.
  Когда появится домен с HTTPS, сменится только Base URL, API останется прежним.
- **Письма пока не отправляются.** Коды подтверждения видны только в логах сервера, за ними нужно
  обращаться к бэкенд-разработчику.
