# Результат интеграции Secure Calendar — 20.11.2026

## Итог и границы

| Компонент | Статус | Подтверждение |
| --- | --- | --- |
| Python startup, `/health`, `/openapi.json`, сохранение пользователей/setup | WORKING_AND_TESTED | HTTP/WebSocket integration + реальный перезапуск Uvicorn с той же SQLite |
| OPAQUE Android → Python, регистрация/login/token/ошибка пароля | WORKING_AND_TESTED | OpaquePythonE2eTest на Android API 37 arm64 |
| Разделение local/server IDs, account-bound token, очистка/expiry/stale-auth guard | WORKING_AND_TESTED | ServerSessionManagerTest и ComparisonRepositoryE2eTest |
| Поиск Алисы/Боба, запрос, принятие, отказ, отмена, сохранённая inbox | WORKING_AND_TESTED | Python protocol tests + Android repository E2E |
| Compose UI → ViewModel → Repository → Python → UI Боба | WORKING_AND_TESTED | ComparisonFlowUiTest: настоящее приглашение, кнопка «Принять», честная ошибка PSI у обеих сторон |
| Реальный Bee2 JNI: hash, scalar/key, point multiplication, sign/verify, Keystore | WORKING_AND_TESTED | Два Bee2NativeInstrumentedTest на поддерживаемой ABI; это smoke tests, не аудит native-кода |
| WS authentication, relay, запрет control-event spoofing, replay/foreign-session/offline | WORKING_AND_TESTED | Python protocol suite, Android TwoUserServerE2eTest |
| Bounded WS reconnect/backoff и изоляция устаревших callbacks | IMPLEMENTED_NOT_TESTED | Реализованы; отдельное испытание Android с многократным отключением физической сети не выполнено |
| Release HTTPS/WSS policy | IMPLEMENTED_NOT_TESTED | Ограничения в Gradle, endpoint validator и manifest; production TLS deployment не предоставлен |
| Полный ECDH-PSI | BLOCKED | Нет согласованного hash-to-curve, полной спецификации/engine и проверенных transcript vectors |
| Готовность PSI инфраструктуры | PARTIALLY_IMPLEMENTED | Lifecycle/routing guard, Base64 relay, точки/скаляры и локальные slot utilities; это не PSI-протокол |

Проверка выполнялась на одном arm64 Android-эмуляторе с двумя независимыми клиентами/аккаунтами. Два физических устройства, production TLS и длительный soak test сети не проверялись. В проект не добавлялось ни фиктивное пересечение, ни подмена Bee2 другой криптографией.

## Фактические причины неработоспособности

1. `ComparisonRepositoryImpl` сравнивал серверные `responder_id` с локальным Room owner ID и проверял self-comparison в неправильном пространстве ID. Теперь server user ID сохраняется отдельно при OPAQUE-authentication; token остаётся привязан к local owner.
2. Код ссылался на отсутствующий `ServerEndpointSettings`. Теперь REST/WS получают один централизованный endpoint, а смена endpoint инвалидирует токен и socket.
3. На устройстве Android 17 / targetSdk 37 отсутствовал `ACCESS_LOCAL_NETWORK`. Это давало реальные TCP connection timeouts даже при работающем `/health` на host. Добавлены manifest permission и runtime-запрос перед LAN-подключением; instrumentation grants то же разрешение.
4. Файл в `jniLibs/arm64-v8a` был ELF32 ARM, загрузчик выдавал `32-bit instead of 64-bit`. Восстановлен настоящий существовавший JNI bridge из commit `82c163d`; неверный артефакт сохранён отдельно без изменения байтов. Сборка проверяет соответствие ELF ABI директории.
5. Relay принимал произвольный `type`: клиент мог подделать `session_accepted`. Сейчас control events создаёт только REST/server, relay принимает только `psi_*` с непустым корректным Base64, проверяет участников и статус, отклоняет повторный digest.
6. Старые версии AndroidX Test/Espresso вызывали отсутствующий `InputManager.getInstance()` на Android 17. Обновлены runner 1.7.0, ext.junit 1.3.0, Espresso 3.7.0; Compose UI-тесты теперь выполняются.
7. Исходный PSI действительно не был реализован. Это не исправлялось обычным пересечением множеств: в точке запуска протокола сохраняется fail-closed ошибка.

## Сервер и целостность данных

При startup **нет удаления пользователей**. Сохраняются password_file и зашифрованный OPAQUE setup. Существующие таблицы и записи не очищались; additive migration создаёт registration states и relay receipts. Username уникален; новая регистрация требует канонический lowercase ASCII логин, как Android. Идентичности уже созданных пользователей не переписываются.

Finish регистрации связан с конкретным start через случайный одноразовый registration_state_id (в SQLite только его SHA-256); TTL 120 секунд. Это дополнение контракта согласовано с AuthApi. Полный login проверяет настоящую OPAQUE finalization и одноразовый login_state_id. HTTP/WS проверяют подпись/срок token и наличие зарегистрированного пользователя. WS повторно проверяет token во время соединения.

Сессии проверяют участников/роли, период и атомарные переходы статуса. Параллельная повторная отправка того же направления пары блокируется SQLite-транзакцией. Pending истекает через 120 секунд; accepted/active — через 300 секунд без активности (expiry выполняется при обращениях к API/relay). Отмена accepted/active разрешена обеим сторонам. Sender ID берётся из авторизации, а не из клиентского JSON. Повторный opaque payload отмечается в отдельной таблице receipts; открытых слотов там нет.

Сервер работает с одним Uvicorn worker: login states и sockets in-memory. Приглашения и ответы восстанавливаются REST polling, opaque PSI frames офлайн не очередятся. Сервер сообщает recipient_offline/peer_offline. Завершение серверной транспортной сессии само по себе никогда не означает криптографический SUCCESS.

Проверочный Uvicorn слушает `0.0.0.0:8000`, `/health` возвращает `ok`. Использована **отдельная** `.runtime/android-integration.sqlite3` с отдельным стабильным секретом 0600. Рабочая БД не затронута. Firewall host на момент проверки выключен; настройка не менялась. Подключение эмулятора к `10.0.2.2:8000` проверено настоящими Android HTTP/WS-вызовами после выдачи LAN permission.

## OPAQUE

- Suite: opaque-ke 4.0.1, P-256 OPRF, TripleDH P-256/SHA-256, Identity KSF.
- Client identifier: канонический login в UTF-8.
- Server identifier: `secure-calendar-python-server/v1`.
- Context: `com.example.calendar/opaque/v1`.
- Password: UTF-8, без нормализации.
- Wire: стандартный Base64 без переносов, typed OPAQUE wire messages.
- Реальные access_token выдаются после регистрации/проверенной login finalization; Android получает expires_at и не хранит token в БД/SharedPreferences.
- Kotlin и Python не сохраняют пароль, временные Kotlin ByteArray очищаются; immutable String/память runtime нельзя обещать гарантированно стереть. Логи не содержат пароль, token или OPAQUE payload.

В Python OpaqueClient добавлены необязательные identities/context для тестирования ровно того же протокола, что использует Android. Старые default параметры generic binding тестов сохранены. В тестах старого upstream binding исправлено ожидание 64-байтного ключа на 32 байта, соответствующие выбранному SHA-256 suite; криптография не менялась.

## PSI: точные недостающие функции

Доступны семь Bee2Provider JNI entry points:

```kotlin
beltHash(data: ByteArray): ByteArray                 // 32 bytes
validateScalar(scalar: ByteArray): Boolean           // 32-byte encoding
validatePoint(point: ByteArray): Boolean            // 64-byte encoding
publicFromScalar(scalar: ByteArray): ByteArray
multiplyPoint(point: ByteArray, scalar: ByteArray): ByteArray
bignSignMessage(message: ByteArray, privateKey: ByteArray): ByteArray // 48 bytes
bignVerifyMessage(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
```

От второго разработчика нужны как минимум:

```kotlin
external fun hashToCurve(message: ByteArray, domainSeparationTag: ByteArray): ByteArray
external fun validatePsiPoint(point: ByteArray): Boolean
```

Ожидаемые JNI exports — `Java_com_example_calendar_crypto_Bee2Provider_hashToCurve` и `Java_com_example_calendar_crypto_Bee2Provider_validatePsiPoint`. У hashToCurve должен быть **согласованный полный suite** для используемой BIGN-группы `1.2.112.0.2.0.34.101.45.3.1`: hash/expand, DST, mapping, очистка кофактора/подгруппа, canonical encoding и отказ на malformed input. Возврат — согласованные 64 байта, не `hash(m)*G`. Существующий внутренний `ecpSWU` не специфицирует этот suite.

validatePsiPoint должен гарантировать canonical encoding, принадлежность правильной кривой/подгруппе и запрет identity. Если существующий validatePoint уже даёт эти гарантии, нужны исходники и негативные векторы, подтверждающие это; тогда отдельная функция может не понадобиться. Нельзя предполагать subgroup guarantees из успешного smoke test.

Нужны исходники JNI и воспроизводимая сборка, форматы scalar/point/signature, порядок байтов, границы и zeroization/error contracts. HashToCurve недостаточно сам по себе: должен быть согласован PsiEngine с ролями, последовательностью раундов, повторной маскировкой, аутентификацией transcript, привязкой session/peers/version/direction/round, порядком сообщений, лимитами и политикой раскрытия результата. Никакая подпись не была отключена: протокол подписанного PSI-transcript ещё не существует.

Необходимые векторы:

1. scalar → public key, scalar boundaries (zero, q, q±1), point multiplication и endianness;
2. canonical/noncanonical, infinity, off-curve и wrong-subgroup points;
3. hash-to-curve: пустое/обычное/длинное сообщение, разные DST, точные ожидаемые точки;
4. BIGN sign/verify: корректная подпись и модифицированные message/signature/key;
5. полный двухсторонний PSI transcript с известным непустым результатом, пустым результатом, duplicate inputs и boundary slots;
6. replay, reorder, wrong session/participant, подпись неправильного transcript, отмена/тайм-аут;
7. кросс-реализационное совпадение всех wire bytes и результата на двух настоящих клиентах.

## Смысл результата

Для текущей интеграции UI явно сообщает: **пересечение занятых 15-минутных UTC-слотов публичных событий**, приватные события исключены (`isPublic` уже означает доступность для сравнения). Это не общая свобода. До рабочего engine даже эти множества не создаются в сетевом слое и не передаются серверу. Существующие SlotConverter.freeSlots/intersect — локальные utilities, а не реализация PSI.

Если требуется продукт «общие свободные интервалы», сначала нужно согласовать другой контракт: все влияющие на занятость события (включая приватную занятость), рабочие часы/универсум, округление границ и DST, затем локально построить свободные слоты и выполнить настоящий PSI. Такая смена семантики не сделана молча.

## Выполненные проверки

Все следующие команды реально запускались и успешно завершились на итоговом Android-коде:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest --no-configuration-cache
.venv/bin/python -m pytest server/tests server/opaque-snake-p256/tests -q --tb=short
```

JDK задавался через JAVA_HOME на bundled JDK Android Studio. Первоначально отсутствовал UTP artifact: выполнена разрешённая загрузка через Gradle с сетью. После установки зависимостей Android tasks выполняются локально. Configuration cache для connected tests отключён из-за UTP.

- **15 JVM tests**, 0 failures/errors/skips.
- **14 Android instrumentation tests**, 0 failures/errors/skips, Android API 37 arm64.
- **21 Python tests**, 0 failures. Сюда входят generic OPAQUE binding tests и серверные HTTP/WS/реальный restart tests.
- GET `/health` и `/openapi.json` проверены; host /health повторно вернул ok после тестов.
- `git diff --check` без ошибок whitespace.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- HTML JVM report: `app/build/reports/tests/testDebugUnitTest/index.html`.
- HTML device report: `app/build/reports/androidTests/connected/debug/index.html`.

Python сообщает deprecation warnings для FastAPI on_event и httpx adapter Starlette TestClient. Они не являются ошибками тестов и не скрывались. Независимого криптоаудита и настоящего PSI-теста нет — PSI помечен BLOCKED.

## Изменённые компоненты

Сервер: `server/main.py`, `auth.py`, `database.py`, `websocket.py`, `requirements-test.txt`; Rust `opaque-snake-p256/src/client.rs` и соответствующие suite tests; добавлены `tests/test_protocol_integration.py`, `tests/test_live_server.py`.

Android network/data: `ServerEndpointSettings.kt`, `AuthApi.kt`, `ServerSessionManager.kt`, `ComparisonApi.kt`, `OkHttpWebSocketTransport.kt`, `ServerAuthRepositoryImpl.kt`, `ComparisonRepositoryImpl.kt`, `ComparisonRepository.kt`, `AppContainer.kt`, `AppViewModelFactory.kt`.

Android UI: `ComparisonViewModel.kt`, `ComparisonScreen.kt`, `ComparisonCasino.kt`, `ProfileViewModel.kt`, `ProfileScreen.kt`, `ConnectionCleaningDialog.kt`, `AnimatedBee.kt`, `SecureCalendarApp.kt`; строки network/comparison/profile вынесены в `values/strings.xml` и `values-ru/strings.xml`. Розовая тема, календарь и предыдущие гороскопы сохранены.

Build/native: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, восстановленный arm64 `libbee2bridge.so`, сохранённый исходный артефакт и `native-artifacts/README.md`. Добавлены/обновлены E2E и session-manager tests, `.gitignore`, `docs/server-setup.md`, `docs/verification-environment.txt` и этот отчёт. Пользовательские изменения IDE не откатывались; git reset/очистка БД не выполнялись.


## Дополнение: исходная Bee2 и требуемая malicious security

После предоставленных пользователем ссылок добавлен source-backed `native/bee2-mapping`, стандартная BAKE SWU operation и `Bee2StandardMapping`. Пин upstream: `4341f7a5c300f8e6dc292559e671eb899d831433`, исходники получаются через `fetch.py`. Официальные upstream tests и host negative/vector tests прошли; новый Android JNI совпал с официальным вектором и действующей кодировкой Bee2. Метаданные и хеши — `native/bee2-mapping/artifact.json`. Лицензия включена в assets APK.

Актуальная Android проверка: **16 instrumentation tests**, 0 failures/errors/skips; JVM — **15 tests**, без ошибок. Последний device run использовал `-PsecureCalendarIntegrationTests=true` и отдельный пакет `com.example.calendar.integrationtest`. Один предыдущий device run встретил device offline при установке; его зелёный Gradle exit не считался доказательством. После восстановления ADB тесты выполнены повторно и результаты XML проверены.

Пользователь явно выбрал защиту от нарушающего протокол собеседника. Поэтому простая semi-honest ECDH-схема не включена. Полный malicious-secure PSI остаётся BLOCKED; стандартное fixed-input BAKE SWU отображение не является само по себе complete HashToCurve suite или malicious PSI engine. План и найденные ограничения кандидатов — `docs/psi-malicious-plan.md`. VOLE-PSI изучается отдельно в `.tools` и не заменяет Bee2 в приложении.
