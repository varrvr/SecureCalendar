# Запуск Secure Calendar: Python и Android

Актуальный проверенный локальный HTTPS/WSS запуск и адреса: [local-comparison.md](local-comparison.md). Debug BuildConfig теперь по умолчанию `https://10.0.2.2:8443`; прежние HTTP команды ниже оставлены для диагностики.

## Что проверено

Рабочий модуль — `server.main:app`; новый сервер не создавался. Проверка проводилась на macOS arm64, CPython 3.12.16, Rust 1.100.0, JDK 21 из Android Studio, Android 17 / API 37 arm64 (`emulator-5554`). Проверенный endpoint эмулятора — `http://10.0.2.2:8000`, host endpoint — `http://127.0.0.1:8000`; WS — тот же адрес с `/ws` и схемой `ws`. Host слушает `0.0.0.0:8000`. Найденный при проверке Wi-Fi IP компьютера — `172.20.10.7`; он может измениться. Подключение физического телефона не тестировалось.

OPAQUE, поиск, запросы и WebSocket работают. Реальный ECDH-PSI заблокирован отсутствием согласованного hash-to-curve и движка. После принятия запрос не превращается в фиктивный результат.

## Python, Rust, зависимости

Все команды выполняются из корня `SecureCalendar`. Рекомендуется Python 3.12 (код сервера требует Python 3.10+), Rust/Cargo и macOS Command Line Tools либо соответствующий системный C toolchain. Rust нужен для локального binding; нельзя заменять его опубликованным wheel `opaque-snake` с другим suite.

```sh
python3.12 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
cargo --version
python -m pip install -r server/requirements-test.txt
```

`requirements-test.txt` включает `requirements.txt` и добавляет pytest/httpx. Без тестов достаточно `python -m pip install -r server/requirements.txt`. Установка из корня существенна: editable dependency указывает на `./server/opaque-snake-p256`.

Проверенная конфигурация: FastAPI 0.165.0, Uvicorn 0.54.0, Pydantic 2.14.4, cryptography 50.0.2, httpx 0.28.1, pytest 9.2.0, maturin 1.16.0. Производственные требования оставлены в существующем `server/requirements.txt`; версии всей среды проверки записаны в `docs/verification-environment.txt`. `Cargo.lock` фиксирует Rust-зависимости; `opaque-ke` — 4.0.1, P-256/SHA-256, Identity KSF.

Если Python 3.12 отсутствует, в этой рабочей копии также создан `.bootstrap/bin/uv`, а Rust изолирован в `.tools`. Для пересборки именно этой среды:

```sh
env -u CONDA_PREFIX \
  CARGO_HOME="$PWD/.tools/cargo" RUSTUP_HOME="$PWD/.tools/rustup" \
  PATH="$PWD/.tools/cargo/bin:$PATH" VIRTUAL_ENV="$PWD/.venv" \
  .venv/bin/maturin develop --release --manifest-path server/opaque-snake-p256/Cargo.toml
```

При конфликте Conda/VIRTUAL_ENV убирается только CONDA_PREFIX для команды, а не меняется глобальная конфигурация.

## БД и секреты

Переменные сервера:

| Переменная | Назначение |
| --- | --- |
| `DB_PATH` | Путь к SQLite; по умолчанию `server/secure_calendar.db` |
| `SECRET_KEY` | Подпись access token и производный ключ шифрования OPAQUE setup; должен быть постоянным и случайным |
| `OPAQUE_SETUP_ENCRYPTION_KEY` | Необязательный отдельный URL-safe Base64 ключ AES-GCM, ровно 32 байта |
| `TOKEN_TTL_SECONDS` | Срок токена, по умолчанию 86400 секунд |
| `MAX_MESSAGE_CHARS` | Максимальный WS JSON, по умолчанию 2000000 символов |
| `PSI_RATE_MAX`, `PSI_RATE_WINDOW` | Лимит opaque relay: 30 сообщений на 60 секунд по умолчанию |

Для **новой** установки создайте секрет один раз. Для существующей установки используйте исходный SECRET_KEY/OPAQUE_SETUP_ENCRYPTION_KEY: смена ключа делает старый OPAQUE setup нечитаемым. Не удаляйте БД ради исправления ошибки входа.

```sh
mkdir -p .secrets
chmod 700 .secrets
python - <<'PYSECRET'
import os, secrets
path = '.secrets/server.key'
if not os.path.exists(path):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as out:
        out.write(secrets.token_hex(32))
PYSECRET
export SECRET_KEY="$(cat .secrets/server.key)"
export DB_PATH="$PWD/server/secure_calendar.db"
python -m uvicorn server.main:app --host 0.0.0.0 --port 8000 --workers 1
```

Не используйте встроенное значение `dev-only-change-me` в реальном развёртывании. Резервируйте БД и секреты раздельно. SQLite-файл создаётся с правами владельца 0600. Миграция добавляет `registration_states` и `relay_receipts`; пользователи и OPAQUE password files сохраняются. Незавершённая регистрация резервируется на 120 секунд; после истечения её можно начать заново, не перезаписывая зарегистрированного пользователя.

Один worker обязателен для этой архитектуры: ephemeral OPAQUE login states и WS connections находятся в памяти процесса. Для нескольких workers нужен общий session store/pub-sub, сейчас он не реализован.

## Проверка сети

```sh
curl --fail http://127.0.0.1:8000/health
curl --fail http://127.0.0.1:8000/openapi.json
lsof -nP -iTCP:8000 -sTCP:LISTEN
ipconfig getifaddr en0
```

`/health` возвращает только `{"status":"ok"}`. На Linux IP можно посмотреть через `ip -4 addr`. Проверка с телефона: открыть `http://<фактический-IP-компьютера>:8000/health` в браузере. Если host curl работает, а телефон — нет, проверьте общую сеть, изоляцию клиентов Wi-Fi, VPN и firewall. На macOS правило firewall должно разрешать входящие соединения именно Python, который запущен из `.venv`; не отключайте firewall глобально. В процессе этой проверки настройки firewall не менялись.

## Android: общий адрес HTTP/WS

Настройки сборки находятся в `app/build.gradle.kts`:

```sh
./gradlew :app:assembleDebug -PsecureCalendarDebugServerUrl=http://<проверенный-IP>:8000
./gradlew :app:assembleRelease -PsecureCalendarServerUrl=https://<реальный-TLS-host>
```

Локальная настройка может быть записана в Gradle user properties. В debug по умолчанию используется проверенный стандартный Android emulator endpoint `http://10.0.2.2:8000`. Для физического телефона этот адрес непригоден: используйте фактический LAN IP сервера. `localhost` на телефоне означает телефон, а не компьютер.

В профиле приложения можно сохранить endpoint для текущей установки. `ServerEndpointSettings` централизует адрес и производный WS URL; смена адреса очищает токен и отключает прежний WebSocket. Сохранённая настройка профиля имеет приоритет над BuildConfig. Пароли, user-info, query и fragment в URL запрещены. В release HTTP/WS отклоняются и manifest запрещает cleartext. `preReleaseBuild` требует явный HTTPS endpoint, а не placeholder localhost. TLS reverse proxy должен поддерживать WebSocket Upgrade; секрет в query `/ws?token=...` нельзя записывать в proxy access logs. Фильтры Uvicorn уже скрывают token; TLS обязателен для удалённого сервера.

На Android 17 приложение с target SDK 37 запрашивает `ACCESS_LOCAL_NETWORK` перед подключением к локальному адресу. Если разрешение отклонено, разрешите доступ к локальной сети в настройках приложения. Одного INTERNET на API 37 недостаточно. Официальная документация: https://developer.android.com/privacy-and-security/local-network-permission . Именно отсутствие этого разрешения давало тайм-ауты в проверенном эмуляторе.

## OPAQUE и реальные аккаунты

1. Создайте локальный аккаунт, затем откройте профиль. Локальная регистрация сама по себе не регистрирует пользователя на сервере.
2. Нажмите «Подключиться к серверу», введите пароль. При первой регистрации создаётся настоящий Bee2-ключ; на неподдерживаемой ABI показывается ошибка, а не фиктивный публичный ключ.
3. Для повторного подключения используется OPAQUE login. Серверная сессия хранит token только в памяти, отдельные local owner ID и server user ID, а также `expires_at` от сервера. Logout, смена аккаунта/endpoint и истечение срока обнуляют token/WS.
4. Проверяйте отдельно «OPAQUE: зарегистрирован», серверную авторизацию и состояние подключения. После перезапуска приложения пароль нужно ввести снова.

Параметры обеих сторон: client identity — нормализованный lowercase ASCII login в UTF-8; server identity — `secure-calendar-python-server/v1`; context — `com.example.calendar/opaque/v1`; P-256 OPRF, TripleDH P-256/SHA-256, Identity KSF. Не меняйте эти значения для существующих записей. Пароль кодируется UTF-8 без нормализации. Сообщения — стандартный Base64 без переносов.

REST-последовательность регистрации: `/auth/register/start` возвращает `registration_response` и одноразовый `registration_state_id`, который обязателен для `/auth/register/finish`; finish возвращает access_token и expires_at. Вход: `/auth/login/start` возвращает `credential_response` и `login_state_id`; `/auth/login/finish` проверяет настоящую OPAQUE finalization перед выдачей token. Pending login state живёт 120 секунд и используется один раз.

Автоматическая проверка (без вывода пароля/token):

```sh
.venv/bin/python -m pytest server/tests server/opaque-snake-p256/tests -q --tb=short
./gradlew :app:connectedDebugAndroidTest --no-configuration-cache -PsecureCalendarIntegrationTests=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.calendar.OpaquePythonE2eTest
```

Android-тесты требуют запущенного Python на endpoint из BuildConfig; Python-тесты создают временные БД и не изменяют рабочую. `test_live_server.py` сам запускает Uvicorn на свободном loopback-порту, завершает процесс и проверяет повторный вход после перезапуска с той же БД/секретом.

## WebSocket и два пользователя

HTTP использует `Authorization: Bearer ...`; существующий WS контракт — `/ws?token=...`. Сервер подтверждает соединение сообщением `connected`. Клиент использует ping frames каждые 20 секунд; при разрыве повторяет подключение с задержками 1, 2, 4, 8, 16 секунд. После отказа авторизации или logout переподключение прекращается. Отложенные callback старого socket не меняют состояние нового.

Приглашения хранятся в SQLite и восстанавливаются через `/sessions`, даже если push потерялся. Ответы также восстанавливаются из статуса `/sessions/{id}`. Polling Android — раз в 5 секунд; timeout приглашения — 120 секунд. Opaque PSI payload не хранится для офлайн-доставки: отправитель получает `recipient_offline`. При разрыве отправляется `peer_offline`; accepted/active сессии без активности истекают через 300 секунд. `complete` — только статус транспорта, а не доказательство PSI.

Проверенный сценарий:

1. Алиса и Боб регистрируются/входят независимо. Локальные ID могут отличаться от серверных.
2. Алиса находит логин Боба, выбирает начало и конец в автомате и опускает рычаг для каждой границы. Время в автомате декоративное; API сравнения использует целые даты UTC включительно.
3. Алиса отправляет запрос. Боб видит логин инициатора и период, принимает или отклоняет.
4. При отказе Алиса видит REJECTED. При принятии обе стороны доходят до проверки наличия PSI и показывают отсутствие криптографического модуля. SUCCESS/EMPTY не показываются.
5. «Сбросить и повторить» отменяет ещё живую сессию либо очищает уже завершённую/отклонённую. Любая сторона может отменить accepted/active; pending — только инициатор.

Автотесты:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest --no-configuration-cache -PsecureCalendarIntegrationTests=true
```

`ComparisonRepositoryE2eTest` использует настоящие Bee2 public keys, OPAQUE и два независимых Android-клиента с разными локальными/серверными ID. `ComparisonFlowUiTest` проводит приглашение через ViewModel, отображает его Бобу, нажимает «Принять» в Compose и проверяет честную ошибку PSI у обоих. `TwoUserServerE2eTest` отдельно проверяет relay с явно синтетическими **транспортными**, а не криптографическими данными.

## Bee2 и граница PSI

Текущий packaged bridge — ELF64 AArch64 из commit `82c163d`, SHA-256 `c97cf35187e879c2cfe72bedcc4b2761173e570d03a63d61754cddcc6e164c29`. Ранее под arm64 лежал ELF32 ARM без ожидаемых JNI exports; он сохранён в `native-artifacts/unverified/armeabi-v7a` и не включается в APK. Gradle `verifyBee2Abi` проверяет ELF header до сборки. Проверены настоящие BELT hash, scalar/public key, point multiplication, BIGN sign/verify и Keystore-wrapped key lifecycle. Это smoke tests примитивов, не подтверждение PSI и не независимые стандартные векторы.

Недостающие JNI-контракты и векторы перечислены в `docs/bee2-integration.md` и `docs/integration-report.md`. Нельзя заменить hash-to-curve на `hash(data) * G`. До согласования протокола сервер не получает открытые слоты, приложение не вычисляет обычное пересечение под видом PSI.

## Основные ошибки

| Симптом | Что проверить |
| --- | --- |
| connect timeout на Android 17 | LAN runtime permission, фактический endpoint, host `0.0.0.0`, firewall/VPN |
| Connection refused | Запущен ли `server.main:app`, совпадают ли порт и endpoint |
| HTTP 401 | Пароль, срок токена/login state; повторный OPAQUE-вход в профиле |
| HTTP 409 при регистрации | Логин уже зарегистрирован либо pending registration ещё живёт; login либо повтор после 120 секунд |
| HTTP 409 при запросе | Уже есть активное приглашение этой паре/направлению; отмените его или дождитесь истечения |
| setup decryption failed | Восстановите исходный SECRET_KEY/OPAQUE_SETUP_ENCRYPTION_KEY; не удаляйте записи |
| 32-bit instead of 64-bit | Неправильный ELF в jniLibs; проверяйте реальную архитектуру через `file` |
| NoClassDefFoundError Bee2Provider после ошибки JNI | Исправить artifact и перезапустить приложение; JVM уже пометила класс неинициализированным |
| Нет входящих запросов | Серверная авторизация, WS/REST, срок запроса; local ID не равен server ID |
| PSI недоступен после принятия | Ожидаемый BLOCKED до появления согласованного engine/hash-to-curve |
| Gradle offline не находит UTP | Один раз запустить connectedDebugAndroidTest с сетью и `--no-configuration-cache` |
| Espresso InputManager.getInstance | Использовать обновлённые test runner 1.7.0 / Espresso 3.7.0, а не старую версию 3.5.1 |

## Изолированная среда текущей проверки

Созданы `.runtime/android-integration.sqlite3`, `.runtime/integration.secret` (0600) и `.runtime/run_server.py`. Проверочный сервер запущен через `PYTHONPATH="$PWD" .venv/bin/python .runtime/run_server.py` и слушает 8000; эти файлы не коммитятся. Рабочая `server/secure_calendar.db` не использовалась и не удалялась. Не публикуйте `.runtime`, `.secrets` или токены. Если порт занят этим процессом, остановите именно его перед запуском другой конфигурации, не удаляйте БД.

Instrumentation можно запускать с `-PsecureCalendarIntegrationTests=true`: используется отдельный пакет `com.example.calendar.integrationtest`, чтобы установка и удаление тестового APK не затрагивали основной пакет приложения. После такого запуска соберите обычный `:app:assembleDebug` без этого свойства для APK основного приложения.

## Действующая модель MITM и TLS

Доверяем серверу и подтвердившему запрос собеседнику: см. `threat-model.md`. Локальные `http://...:8000` и `ws://...` пригодны для диагностики, но не защищают от сетевого MITM. OPAQUE не защищает последующие bearer-token запросы вместо TLS.

Если уже есть HTTPS reverse proxy, настройте один адрес в Android; WSS выводится автоматически из него. Сертификат должен соответствовать имени хоста и быть доверенным на каждом устройстве. TLS reverse proxy должен передавать `/ws` с WebSocket Upgrade и не журналировать token query. Сервер Uvicorn может оставаться за proxy в закрытой сети.

Для прямого запуска существующего приложения с готовыми сертификатом/ключом:

```sh
.venv/bin/python -m uvicorn server.main:app --host 0.0.0.0 --port 8443 \
  --ssl-certfile /absolute/path/fullchain.pem \
  --ssl-keyfile /absolute/path/privkey.pem
```

Сохраните существующие переменные окружения и путь SQLite при смене способа запуска; не создавайте новую БД вместо пользовательской. Адрес должен быть `https://<имя-из-сертификата>:8443` на всех клиентах. Проверка — `curl --fail https://<имя-из-сертификата>:8443/health` без `-k`. Имя должно разрешаться и быть доступным с ноутбука, эмулятора и телефона; сертификат для домена не подходит для URL с произвольным LAN IP.

Самоподписанный сертификат нельзя исправлять отключением проверки TLS. Локальный CA требует отдельной настройки доверия всех клиентов; автоматически приложение его не принимает. Тестовый keystore из `app/src/test/resources/tls` содержит только тестовый ключ, не включается в APK и не пригоден для запуска настоящего сервера.

Автоматические JVM-проверки `WebSocketMitmTest`: отказ от недоверенного TLS-сервера до HTTP/token и отказ от redirect на другой endpoint. HTTP AuthApi/ComparisonApi также отключают redirects. Реальный HTTPS endpoint ещё не предоставлен, поэтому соединение конкретных устройств с доверенным TLS-сервером не заявляется проверенным.
