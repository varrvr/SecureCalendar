# Проверка кандидата malicious PSI

Выбранная пользователем модель: защищаться также от собеседника, нарушающего протокол. Простое ECDH double masking не удовлетворяет этому требованию. Bee2 keys/signatures не заменяются автоматически.

Рассмотрен кандидат https://github.com/ladnir/volepsi , commit `1ecfe796c661085f21d37c7945bba11e9f54e04d`. Host-зависимости собраны в `.tools`, не глобально. В APK кандидат не включён. Проверенные коммиты основных зависимостей: libOTe `c14ddaff424ec636823162a30b82435a8754ca78`, cryptoTools `f30c7e8d0b762c96bf5d42d50724c4c18db7d29e`. Host-конфигурация: macOS arm64, CMake 3.31.6, C++20, SSE off, Relic on, Sodium off, networking Boost/OpenSSL off, PIC on.

Однажды выполненная настройка (клонирование pinned source и зависимости требуют сети):

```sh
mkdir -p .tools/volepsi-deps
PATH="$PWD/.tools/psi-build-tools/cmake/data/bin:$PWD/.venv/bin:$PATH" \
.tools/psi-build-tools/cmake/data/bin/cmake \
  -S .tools/upstream/volepsi -B .tools/volepsi-host -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$PWD/.venv/bin/ninja" \
  -DFETCH_AUTO=ON -DVOLE_PSI_NO_SYSTEM_PATH=ON -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$PWD/.tools/volepsi-prefix" \
  -DVOLE_PSI_THIRDPARTY_CLONE_DIR="$PWD/.tools/volepsi-deps" \
  -DVOLEPSI_THIRDPARTY_DIR="$PWD/.tools/volepsi-prefix" \
  -DVOLE_PSI_ENABLE_SSE=OFF -DVOLE_PSI_ENABLE_RELIC=ON \
  -DVOLE_PSI_ENABLE_SODIUM=OFF -DVOLE_PSI_ENABLE_BOOST=OFF \
  -DVOLE_PSI_ENABLE_CPSI=OFF -DVOLE_PSI_ENABLE_GMW=OFF \
  -DVOLE_PSI_ENABLE_OPPRF=OFF -DVOLE_PSI_ENABLE_PIC=ON
.tools/psi-build-tools/cmake/data/bin/cmake --build .tools/volepsi-host --parallel 4
```

Запуск проверки без повторной сборки:

```sh
.venv/bin/python research/psi-candidates/evaluate_volepsi.py \
  --source .tools/upstream/volepsi --build .tools/volepsi-host \
  --output research/psi-candidates/volepsi-evaluation.json
```

Runner требует точный revision, exit code 0, все семь именованных Passed и строку All Passed (7); пустой/пропущенный набор тестов не считается успехом. Проверяются malicious PSI, malicious OPRF, salt-order, forbidden seed, Ribbon seed, local abort и peer abort. Положительные тесты используют синтетические множества, отрицательные — специальные атакующие входы upstream. Ожидаемый результат теста не подменяет операции самого протокола.

Результаты — конкретная host-проверка семи случаев, а не независимый аудит, не проверка всех возможных атак и не Android/relay integration. Benchmark security parameters нельзя автоматически считать production-параметрами; в Android профиле их нужно задать и проверить отдельно. Переход к этому протоколу меняет исходный план ECDH-PSI/Bee2 и должен быть согласован. Требования интеграции перечислены в `docs/psi-malicious-plan.md`.
