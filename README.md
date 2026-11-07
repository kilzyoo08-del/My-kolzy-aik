# Centerion — Android client

Android-приложение на Kotlin + Jetpack Compose. Клиент подключается к облачному Centerion API; если публичный ключ Supabase не задан при сборке, остаётся автономный режим с локальной арифметикой и справочной базой Roblox.

## Облачный Centerion

В приложении реализованы:
- Регистрация и вход через Supabase Auth.
- Защищённое хранение access/refresh токенов через Android Keystore (AES-GCM).
- Чат через Supabase Edge Function `centerion`.
- Сохранение диалогов и загрузка истории из Supabase.
- Синхронизируемая долговременная память, которую сервер извлекает через pgvector.
- Обратная связь об ответах через API.

API base URL:
`https://tdqyhptkrmxoohfpfwvs.supabase.co/functions/v1/centerion`

### Параметры сборки

Перед сборкой задайте только публичные параметры клиента:

```bash
export CENTERION_SUPABASE_URL="https://tdqyhptkrmxoohfpfwvs.supabase.co"
export CENTERION_SUPABASE_PUBLISHABLE_KEY="sb_publishable_..."
gradle :app:assembleDebug
```

Или используйте Gradle properties `-PCENTERION_SUPABASE_URL=...` и `-PCENTERION_SUPABASE_PUBLISHABLE_KEY=...`.

Для GitHub Actions добавьте repository variable `CENTERION_SUPABASE_PUBLISHABLE_KEY`. Для Codemagic задайте такую же переменную в настройках сборки.

**Никогда не добавляйте в APK** `OPENAI_API_KEY`, `SUPABASE_SERVICE_ROLE_KEY` или другой секретный серверный ключ. Языковая модель вызывается только с сервера.

Если публичный ключ не задан, приложение не пытается авторизоваться и остаётся в автономном режиме.

## Видео

Существующий модуль локального процедурного рендеринга видео сохранён. Это не нейросетевая генерация видео. Он не зависит от Centerion chat API.

## Ограничения текущей интеграции

- Отправка изображений в облачное ядро пока не включена; прикреплённое фото обрабатывается локальным режимом с честным сообщением об ограничении.
- Поиск в интернете остаётся отдельным клиентским действием; обычный чат идёт через GPT-6 Astra на сервере.
- Для первого входа пользователь должен создать аккаунт или войти через экран авторизации.

## Сборка APK

Открыть проект в Android Studio и собрать `app:assembleDebug`, либо запустить GitHub Actions workflow. Целевая платформа — Android 14 (targetSdk 34).
