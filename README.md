# Berkut — Real-time Fleet Monitoring

Симулятор автопарка → Kafka → backend → Angular. Тестовое задание ISS, расширенный scope: 1 неделя, 1000 машин, фокус на масштабируемости.

> **Status:** все 7 дней реализованы. Backend (simulator + processor + api-gateway), frontend (Angular 18 + Leaflet), end-to-end live на 1000 машин.

## Quickstart

```bash
# 1. Инфра (postgres+timescaledb, redis, kafka KRaft, kafka-ui)
docker compose up -d
docker compose ps           # все healthy / kafka-init exited 0

# 2. Backend (3 jvm-процесса). Из корня репо:
./gradlew :processor-service:bootJar :api-gateway:bootJar :simulator:bootJar
java -jar processor-service/build/libs/processor-service.jar &     # порт 8092
java -jar api-gateway/build/libs/api-gateway.jar &                  # порт 8090 (REST + WS)
java -jar simulator/build/libs/simulator.jar &                      # 1000 машин

# 3. Frontend
cd frontend && npm install && npm start    # http://localhost:4200
```

**UIs:** фронт `http://localhost:4200`, Kafka UI `http://localhost:8080`, REST `http://localhost:8090/api`, actuator `http://localhost:8092/actuator`.

**Порты на хосте:** postgres → **5433**, redis → 6379, kafka → 9092, kafka-ui → 8080, api-gateway → 8090, processor → 8092, frontend → 4200. 5432 специально не используем: на dev-машинах часто уже стоит нативный Postgres и SCRAM auth ошибки из-за конфликта выглядят как «неправильный пароль».

**Webhook на CRITICAL:** по умолчанию URL не задан → notifier пишет события в лог. Для реального POST задать env-переменную:
```bash
WEBHOOK_URL=https://webhook.site/<your-uuid> java -jar processor-service/build/libs/processor-service.jar
```

**Параметры симулятора** (можно переопределить через `--key=value` или env):
- `--simulator.fleet.size=1000` — N машин
- `--simulator.gps.interval-seconds=3` — частота GPS-точек (с jitter ±1с)
- `--simulator.event.rate-per-vehicle-per-min=0.3` — Poisson-rate событий
- `--simulator.event.burst-probability=0.15` — шанс что событие триггерит burst для демо дедупа

## Архитектура

```
simulator (Spring Boot) ──► Kafka ──► processor-service ──► Postgres + Redis
                              │              │ ingest
                              │              │ dedup → events.deduped, events.critical
                              │              │ notifier → POST webhook (на CRITICAL)
                              │
                              └──► api-gateway ──► WebSocket (STOMP) ──► Angular
                                       │
                                       └──► REST (история, фильтры, бэкфилл при reconnect)
```

**Сервисы:**
- `simulator` — генерит N машин (configurable, default 1000), GPS каждые 2-5с, события, шлёт в Kafka
- `processor-service` — три consumer-группы в одном процессе: ingest (→ Postgres + Redis), dedup (→ events.deduped/critical), notifier (→ webhook). Разделение обсуждается в [Trade-offs](#trade-offs)
- `api-gateway` — REST + WebSocket для фронта; собственный Kafka consumer с уникальным `group.id` для fan-out
- `frontend` — Angular 18 + Leaflet + supercluster

**Стек:** Java 21, Spring Boot 3.3, Gradle (Kotlin DSL), PostgreSQL 16 + TimescaleDB, Redis 7, Kafka 3.6 (KRaft, single broker), Angular 18.

**Топики Kafka:**

| Топик | Партиций | Retention | Назначение |
|---|---|---|---|
| `gps.points` | 12 | 1 день | GPS-точки, key=device_id |
| `events.raw` | 6 | 7 дней | Сырые события до дедупа |
| `events.deduped` | 6 | 7 дней | После windowed dedup |
| `events.critical` | 3 | 7 дней | Только CRITICAL — для notifier |
| `vehicles.registry` | 3 | compacted (∞) | Метаданные машин; симулятор публикует на старте → processor хидратит `vehicles` таблицу |

12 партиций для GPS — запас по горизонтальному масштабированию consumer'ов до 12 инстансов на топик.

## Допущения

- **Масштаб демо:** 1000 машин. GPS rate = 1 точка / 3с / машину = ~333 msg/s. Архитектура расчитана на 10k+ (см. [Scaling](#scaling)).
- **Rate событий:** спека говорит «1-2 события/мин на весь парк», но это не масштабируется. Использую **per-vehicle rate** (1 событие на машину каждые ~5 мин ≈ 200/мин для 1k). Параметр `simulator.event.rate-per-vehicle-per-min`.
- **Координаты:** Алматы, центр (43.2389, 76.8897), радиус ~10км. Случайное блуждание + эвристика «придерживайся улиц» (упрощённо, без OSRM-routing).
- **Один диспетчер**, без auth (mock-токен для демонстрации формы; реальный SSO вне scope).
- **Webhook:** URL из env-переменной (`webhook.url`), по умолчанию https://webhook.site/<uuid>; можно подставить локальный echo-сервер.
- **PostGIS не подключаю** — нет distance/polygon операций в spec, для bbox-фильтрации достаточно `lat BETWEEN ... AND lon BETWEEN ...` с обычным B-tree индексом.
- **Avro/Schema Registry — нет.** JSON через Jackson. Зафиксировано в [Trade-offs](#trade-offs).

## Real-time транспорт

### Симулятор → backend: **Kafka producer напрямую**

| Вариант | Почему нет |
|---|---|
| REST polling | Не real-time, нагрузка на backend растёт линейно с числом устройств |
| WebSocket sim↔backend | Backend становится stateful, сложно горизонтально размножить без sticky routing |
| MQTT (Mosquitto) → bridge → Kafka | Реалистичнее всего (IoT-устройства говорят MQTT), но +½ дня инфры. Описано как production path |
| **Kafka напрямую** ✓ | Партиционирование по `device_id` (порядок событий по машине), backend как consumer — горизонтальное масштабирование тривиально, buffer на даунтайм consumer'ов |

**Trade-off:** в проде между устройством и Kafka обычно стоит MQTT-broker (устройства не должны знать про Kafka). Здесь упростил — описано в [What's missing](#что-не-сделано).

### Backend → frontend: **WebSocket + STOMP**

| Вариант | Почему нет |
|---|---|
| SSE | Однонаправленный; неудобно для подписок типа «следить только за этими 5 машинами» |
| REST polling | Не real-time, бессмысленный круговорот HTTP |
| **WebSocket+STOMP** ✓ | Топиковая модель (`/topic/gps`, `/topic/events`) родная для Spring; reconnect через `@stomp/stompjs` из коробки |

**Fan-out между несколькими инстансами api-gateway:** каждый инстанс — свой Kafka consumer group (`api-gateway-${randomUUID}`) → каждый инстанс получает все сообщения и пушит своим WebSocket-клиентам. Альтернатива (Redis Pub/Sub) — нужна когда инстансов >50 и дублирование чтения из Kafka становится дорогим. Документировано как сознательный выбор.

## Reconnect: оператор закрыл вкладку, вернулся через 10 минут

Фронт хранит `lastSeenTimestamp` в `localStorage`, обновляет по каждому пришедшему WS-сообщению. При (re)connect:

1. **GET `/api/positions/last`** — текущие позиции онлайн-машин из Redis. Карта сразу заполнена.
2. **GET `/api/events?since={lastSeen}&limit=500`** — пропущенные события из Postgres. Если результатов 500 — баннер «показаны последние 500 — открыть полный журнал».
3. Подписка на WS `/topic/gps` + `/topic/events` — далее всё real-time.

Если `lastSeen` пустой (первый заход) — `since = now() - 1h` по умолчанию.

**Почему не Kafka rewind с клиента:** оффсеты Kafka — серверный примитив, пробрасывать их через WS — over-engineering. REST-backfill через TimescaleDB hypertable (индекс по time) проще, корректнее и кэшируемее CDN'ом.

## Дедупликация и группировка событий

**Проблема:** DROWSINESS может срабатывать 5 раз за 10 секунд — это одно событие с count=5, не 5 событий.

**Алгоритм (windowed dedup + hysteresis):**
- **Ключ группы:** `(device_id, event_type)`
- **Окно:** 30 секунд (configurable: `dedup.window.seconds`)
- **Хранение состояния окна:** Redis `SET dedup:{device_id}:{event_type} = {group_id} NX EX 30`
  - Если ключ уже есть → событие приклеивается к существующей группе. `count++`, `last_seen = now()`. На WS уходит **update** (не новое событие).
  - Если ключа нет → создаётся новая группа в Postgres (`event_groups`), на WS уходит **новое событие**.
- **Гистерезис:** окно «оживает» с каждым новым событием — TTL обновляется (`EXPIRE 30` снова). Группа закрывается автоматически после 30с тишины. Это убирает дрожание на границе окна.
- **Severity в группе:** хранится как max() всех наблюдённых. Если прилетает событие выше текущего — обновляется на месте в `event_groups`.
- **Webhook:** каждое CRITICAL событие (независимо от состояния группы) уходит в `events.critical` → notifier шлёт POST на внешний URL с `Idempotency-Key: {eventId}`. 5 CRITICAL в одной группе = 5 POST — по спеке. Retry: 0.2s / 1s / 5s (4 попытки).

## Performance

**Backend:**
- Kafka партиционирование по `device_id` — 12 партиций для GPS = до 12 параллельных consumers. На 1k машин один consumer держит ~333 msg/s, запас 10x.
- Postgres TimescaleDB hypertable, chunk по дням → запросы по диапазону дат сканят только нужные chunks.
- Redis `vehicle:last:{id}` для горячего пути карты — Postgres не страдает от 1k запросов на load карты.

**Frontend:**
- GPS-апдейты на карте throttled до **1 кадра / 500мс** через буфер + `requestAnimationFrame`.
- **Marker clustering** через `supercluster`: 1000 индивидуальных маркеров в Leaflet = 5–10 fps в Chrome.
- **Журнал событий:** Angular CDK virtual scroll, в DOM ~50 строк независимо от размера журнала.
- На WS пушим GPS только для **видимых в viewport** машин — фронт отправляет bbox при move/zoom.

## Load test results (1000 машин)

Прогон на dev-ноуте (Windows 11 + Docker Desktop с WSL2 backend, 16GB RAM, JDK 21):

| Метрика | Значение |
|---|---|
| Vehicles в `vehicles` таблице | 1000 (registry → Postgres за <2с после старта симулятора) |
| GPS sustained throughput | **~287 msg/s** (теоретический предел при 1000 машин × 1/3с = 333/s — 86% efficiency) |
| Consumer lag (gps.points, 12 партиций) | **0** на всех партициях после warmup |
| События произведены | 5301 raw → 2808 groups в Postgres |
| **Dedup ratio** | **1.89×** (47% уменьшение) — без burst-демо было бы ~1.05×, с `--burst-probability=0.15` → 1.89× |
| Max burst в группе | **22 события** в одной группе DROWSINESS |
| CRITICAL groups → webhook | 444 критических → 482 POST-вызова (несколько событий в одной группе) |
| Redis state | 1000 `vehicle:online:*`, 1000 `vehicle:last:*`, 400 активных `dedup:*` окон |
| End-to-end latency (приблизительно) | <100ms от kafka.send в симуляторе до видимости в Postgres/Redis |

Перепроверка: `docker exec berkut-kafka kafka-consumer-groups --bootstrap-server localhost:29092 --describe --group processor-gps-ingest`

**Что значит «sustained 287 msg/s»:** это чистая throughput пайплайна `симулятор Kafka producer → broker → consumer batch → Postgres batch insert + Redis pipelined SETEX`. Бутылочное горлышко на этом stack — single-threaded `@Scheduled` симулятора (1 тред перебирает 1000 машин). При горизонтальном масштабировании симулятора на 3 инстанса (по 333 машины каждый) суммарная throughput линейно вырастет до ~860 msg/s. Архитектура расчитана на это: `device_id` как ключ Kafka гарантирует, что один device всегда идёт в одну партицию → consumer ordering сохраняется.

## Scaling

**Текущая конфигурация (1k машин, single host, docker-compose):**
- 1 Postgres, 1 Redis, 1 Kafka broker, 1 каждого Spring-сервиса.

**До 10k машин (single host, vertical):**
- Поднять `processor-service` × 3, `api-gateway` × 2 (consumer parallelism вырастет).
- Postgres настроить (shared_buffers, work_mem). Continuous aggregates для дашборда обязательны.
- Redis всё ещё крошечный (1k keys × ~200 bytes).

**До 100k+ машин (horizontal, k8s):**
- Kafka cluster (3 brokers, RF=3, min.insync.replicas=2), увеличить партиции `gps.points` до 36-60.
- Processor-service на отдельные деплойменты (ingest / dedup / notifier), HPA по Kafka consumer lag.
- API-gateway за nginx + sticky sessions ИЛИ Redis Pub/Sub для fan-out (когда >50 инстансов).
- Postgres → Citus или sharding по `device_id` range; чтение через read replicas.
- MQTT broker (EMQ X cluster) перед Kafka — устройства не должны знать про Kafka.

K8s-манифесты не построены — описано как next step.

## Trade-offs

- **3 модуля бэка вместо 4** (ingest+dedup+notifier склеены в `processor-service`) — внутри отдельные `@KafkaListener` бины с разными consumer groups. Разделение на 3 деплоймента — механическое, описано в [Scaling](#scaling). Сэкономило ~день.
- **JSON вместо Avro** — нет Schema Registry в compose. В проде schema evolution must-have.
- **Single Kafka broker, RF=1** — для 1k машин достаточно. В проде 3 брокера, RF=3.
- **WebSocket fan-out через дубль чтения из Kafka** — проще Redis Pub/Sub, но дублирует трафик. Окупается до ~50 инстансов api-gateway.
- **Dedup state в Redis (а не в Kafka Streams)** — Redis проще для state с TTL, но не survives multi-region. Kafka Streams был бы правильнее в реальной геораспределённой системе.

## Что не сделано

- **Avro/Schema Registry** — JSON через Jackson. Schema evolution в проде маст-хэв
- **k8s манифесты + HPA по consumer lag** — описано в Scaling, не построено
- **Auth (Keycloak/Spring Security)** — нет; CORS открыт для localhost
- **MQTT broker (Mosquitto) → Kafka bridge** — реалистичный IoT-путь (устройства не должны знать про Kafka). Сэкономило ~½ дня
- **Prometheus + Grafana дашборды** — actuator-endpoint `/actuator/prometheus` готов в processor и api-gateway, но docker-сервис Prometheus + готовые JSON дашборды не подняты
- **E2E тесты (Playwright)** — нет; есть только JVM-side smoke-тесты через прогон стека
- **CSV-стрим из БД напрямую** — текущий `/api/events/export` собирает в память (LIMIT 5000). Для миллионов строк нужен `JdbcTemplate.query` с `RowCallbackHandler` и потоковой записью

## Если бы было +неделя

- MQTT (Mosquitto) → Kafka bridge через Kafka Connect
- Avro + Schema Registry, миграции схем
- k8s манифесты + HPA по lag
- Auth (Keycloak SSO)
- Geo-зоны и правила (выезд за границу = SPEEDING-эскалация)
- Replay режим: проиграть прошедший час на карте (slider времени)
- Тепловая карта инцидентов поверх TimescaleDB continuous aggregates

## Структура проекта

```
berkut/
├── docker-compose.yml             # postgres+timescaledb, redis, kafka KRaft, kafka-ui
├── infra/postgres/init.sql        # CREATE EXTENSION timescaledb
├── settings.gradle.kts            # multi-module Gradle (Kotlin DSL)
├── build.gradle.kts               # общая конфигурация (Java 21, Spring Boot 3.3.4)
├── common/                        # shared DTO: Vehicle, GpsPoint, VehicleEvent, Topics
├── simulator/                     # Spring Boot — генератор: random walk + Poisson event rate + burst для дедупа
│   └── kz/berkut/simulator/
│       ├── config/                # @ConfigurationProperties
│       └── fleet/                 # PlateGenerator, DriverNames, SimulatedVehicle, FleetScheduler
├── processor-service/             # Spring Boot — три consumer-группы: ingest, dedup, notifier
│   └── kz/berkut/processor/
│       ├── ingest/                # GpsIngestListener, VehicleRegistryListener
│       ├── dedup/                 # DedupScript (Lua), EventGroupRepository, DedupListener
│       ├── notifier/              # WebhookNotifier (JDK HttpClient + retry), NotifierListener
│       ├── storage/               # GpsRepository, EventRepository, VehicleRepository (JdbcTemplate batch)
│       └── cache/                 # LastPositionCache (pipelined Redis SETEX)
├── api-gateway/                   # Spring Boot — REST + WebSocket для фронта
│   └── kz/berkut/gateway/
│       ├── config/                # KafkaFanoutConfig (uniq group.id per instance), WebSocketConfig, WebConfig (CORS)
│       ├── rest/                  # FleetController (events / positions / vehicle / dashboard / csv export)
│       ├── storage/               # read-only EventsRepository, VehicleRepository, PositionsCache
│       └── ws/                    # KafkaFanout (Kafka → SimpMessagingTemplate → /topic/{gps,events})
└── frontend/                      # Angular 18 + standalone components + signals
    └── src/app/
        ├── components/            # map (Leaflet canvas + 500ms throttle), events-log (CDK virtual scroll), vehicle-card, dashboard
        ├── services/              # api.service (REST), ws.service (STOMP + reconnect + lastSeen в localStorage)
        ├── models.ts              # типы из backend
        └── app.component.ts       # layout grid (карта + журнал)
```

## Как протестировать вручную

1. `docker compose up -d` + запустить все три jar + `npm start` (см. Quickstart)
2. Открыть `http://localhost:4200` — карта с 1000 точек в Алматы, 1000 машин начнут двигаться через 3-5с
3. **Дедуп:** перезапустить симулятор с `--simulator.event.burst-probability=0.9 --simulator.event.rate-per-vehicle-per-min=10` — увидеть в журнале события с `×N` (N>1)
4. **Reconnect:** закрыть вкладку, подождать 30с, открыть снова — карта моментально заполнится (через `/api/positions/last`), журнал покажет события за период отсутствия (`/api/events?since=lastSeen`)
5. **CRITICAL webhook:** запустить processor с `WEBHOOK_URL=https://webhook.site/<uuid>` и смотреть на webhook.site как падают POST с `Idempotency-Key`
6. **Click машины** на карте → карточка с водителем, скоростью, последними 10 группами событий
7. **CSV:** кнопка в шапке журнала → скачать CSV всех групп с фильтрами
