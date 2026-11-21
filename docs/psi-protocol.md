# Реализованный локальный OPRF-PSI

Действующая модель — доверенные сервер и подтвердивший запрос peer; сетевой MITM закрывается HTTPS/WSS с проверкой сертификата. Bee2 сохранён. Результат получают оба. См. `threat-model.md`, `local-comparison.md`.

## Профиль SC-OPRF-PSI-v1-BIGN128-XMD-BELT-SSWU-RO

Проектный профиль, не зарегистрированный IETF suite. BIGN OID 1.2.112.0.2.0.34.101.45.3.1; p=2^256-189, стандартные A/B/q Bee2, cofactor 1. Ненулевые скаляры выбираются SecureRandom rejection sampling в [1,q-1], 32-byte LE. Точки — канонические x||y, 64-byte LE; bignDH/bignPubkeyVal проверяют входы. hash-times-generator не используется.

HashToGroup: expand_message_xmd RFC 9380 §5.3.1 с BELT (output=32, block=32), hash_to_field count=2,L=48 (big-endian reduction modulo p), SSWU §6.6.2 с Z=-11, сумма двух точек, h=1. DST — ASCII profile + `/H1`. Четыре критерия Z проверяются независимым скриптом, 122 вектора совпали с native Bee2. Identity на выходе отвергается, fallback отсутствует. Базовая арифметика исполняется в Bee2; не заявляется независимый аудит или подтверждённый constant-time всего адаптера.

Context кодируется DataOutputStream: writeUTF(profile), writeUTF(sessionId), writeLong(initiatorId), writeLong(responderId), writeLong(periodStartSeconds), writeLong(periodEndSeconds). writeUTF — modified UTF-8 с двухбайтовой длиной; используемые profile/session UUID ASCII. Вход H1 — context || signed int64 BE UTC slot number. Finalize = BELT(ASCII(profile+'/Finalize') || context || slot8BE || point64LE).

## Шаги

Оба клиента создают listener до согласования готовности, читают публичные busy события локально, обрезают периодом и преобразуют в упорядоченные 15-минутные слоты. Обмениваются ready.

B генерирует отдельный временный k, вычисляет U={Finalize(y,kH1(y))}, сортирует PRF outputs и передаёт U. A маскирует каждую H1(x) независимым ненулевым r_x и отправляет M_x=r_x H1(x). B отвечает Z_x=kM_x. A снимает маску N_x=r_x^-1 Z_x, финализирует и сравнивает PRF outputs с U. Это криптографическое пересечение, не пересечение открытых расписаний.

A передаёт B только совпавшие PRF outputs. B сопоставляет их со своим локальным отображением и возвращает BELT-подтверждение списка. Оба получают интервалы; инициатор завершает серверную сессию через существующий /complete. Пересечение раскрывается обеим сторонам намеренно; размеры/трафик не скрываются. Секрет k и все r_x стираются в finally, обратный скаляр стирается нативно.

## Транспорт и ошибки

Существующий relay envelope: type=psi_oprf, session_id, recipient_id, Base64 payload; сервер добавляет аутентифицированный sender_id. Payload JSON: profile, context (Base64 BELT(context)), phase, seq, last, items (Base64). Фазы ready,outputs,query,evaluated,matches,ack; партии до 1024 элементов, пустая партия имеет last=true. Проверяются profile/context/session/peer/recipient/фаза/последовательность/длины/точки/кардинальности. При ошибке — abort и отсутствие результата. psi_abort прекращает ожидание peer, не содержит слот/текст исключения. Есть технические timeout, ограниченные входные очереди и server replay guards.

Malicious-secure PSI не требуется выбранной моделью и не заявляется. Проверки входных данных сохранены. Источники: https://www.rfc-editor.org/rfc/rfc9380.html , https://github.com/agievich/bee2 .
