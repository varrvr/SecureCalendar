# Secure Calendar: Private Set Intersection (PSI) with Bee2 & OPAQUE

**Тема проекта:** Разработка мобильного приложения для защищённого сравнения календарных событий с использованием протокола PSI и криптографической библиотеки Bee2.

## 📖 О проекте

Приложение поддерживает локальные календари, OPAQUE-аутентификацию на Python-сервере и запросы между реальными аккаунтами. Пользователи и сервер могут обменяться приглашением и маршрутизировать непрозрачные сообщения.

Локальное OPRF-PSI сравнение через Bee2 подключено и проверено: два пользователя проходят OPAQUE, принимают запрос и получают одинаковое пересечение публичных занятых слотов. Запуск без внешнего домена: [docs/local-comparison.md](docs/local-comparison.md). Открытые слоты серверу не передаются.

### 🔐 Модель угроз (Threat Model)

Сервер и подтверждённые собеседники доверенные; защищаемся от сетевого MITM. HTTPS/WSS с проверкой сертификата обязательны для этого режима; debug HTTP/WS защиты от MITM не обеспечивает. Bee2 сохраняется. Период задаёт инициатор, результат должны получать оба. Полный договор и оставшиеся криптографические условия: [docs/threat-model.md](docs/threat-model.md).

Подробный запуск сервера и Android: [docs/server-setup.md](docs/server-setup.md). Результаты интеграции и точные блокеры PSI: [docs/integration-report.md](docs/integration-report.md). Краткая настройка: [server/README.md](server/README.md). Текущие криптографические ограничения — в `docs/security-limitations.md` и `docs/psi-protocol.md`.

---

## 🏗️ Архитектура системы

```text
[Android (Алиса)] <-----------> [Сервер (Python)] <-----------> [Android (Боб)]
       |                               |                               |
  Jetpack Compose                 SQLite (БД)                   Jetpack Compose
  MVVM / Room                FastAPI + WebSocket                 MVVM / Room
       |                               |                               |
  CryptoModule                    OPAQUE Auth                     CryptoModule
  ├── Bee2 (JNI)                  WebSocket                       ├── Bee2 (JNI)
  └── OPAQUE (Rust)               Relay Only                      └── OPAQUE (Rust)
