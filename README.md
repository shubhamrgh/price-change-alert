# Price Change Alert — Indian stock & crypto price-change alerts (PWA)

Add NSE/BSE stocks and crypto coins to a watchlist; the backend polls prices and
delivers alerts to the authenticated subscriber's selected channels.

Accounts use an opaque, hashed server-side session token in an HttpOnly cookie.
Watchlists, alerts, browser subscriptions, and notification destinations are
owned by the account ID; caller-supplied visitor IDs are no longer trusted for
private data. On the first login or registration after upgrading, the browser's
previous visitor token is used once to claim its existing watchlist, alerts, and
Web Push subscription for the authenticated account.

## Stack

| Layer  | Tech |
|--------|------|
| Backend  | Java 21, Spring Boot 4, H2 locally / PostgreSQL in production, WebClient, web-push |
| Frontend | React 19 + Vite 8, service worker, manifest (PWA) |
| Notifications | Durable multi-channel outbox: Web Push, email, Telegram, Discord |

## Data sources (fallback chain)

| Market | Primary | Fallback |
|--------|---------|----------|
| Crypto  | Coinbase spot price | CoinGecko, then Yahoo Finance (`BTC-INR` / `BTC-USD`) |
| NSE     | Yahoo Finance (`RELIANCE.NS`) | Google Finance |
| BSE     | Yahoo Finance (`RELIANCE.BO`) | Google Finance |

Crypto is quoted in USD (stocks in INR). NSE/BSE direct endpoints
(nseindia.com / api.bseindia.com) were tried and removed: they are bot-blocked
or unstable, so stocks use Yahoo Finance directly. Yahoo's unofficial chart API
can throttle at high poll rates — keep the watchlist small or raise
`price-change-alert.poll.interval-ms`.

## Run it

Backend (serves the built PWA too):

```
cd backend
mvnw package -DskipTests            (first run downloads Maven)
java -jar target\price-change-alert-0.0.1-SNAPSHOT.jar
```

App: http://localhost:8080  (API on the same server)

Frontend dev mode (hot reload, proxies /api to :8080):

```
cd frontend
npm install
npm run dev        -> http://localhost:5173
```

After changing the frontend, ship the UI into the backend jar:

```
cd frontend
npm run build
copy the contents of frontend/dist to backend/src/main/resources/static
cd ..\backend
mvnw.cmd package
```

## Configuration

| Key | Meaning |
|-----|---------|
| `price-change-alert.poll.interval-ms` | alert engine poll cadence (default 30000) |
| `price-change-alert.poll.max-quote-age` | oldest quote allowed to trigger an alert (default 2 minutes) |
| `price-change-alert.cache.quote-ttl` | quote cache freshness window (default 20 seconds) |
| `price-change-alert.cache.search-ttl` | successful search cache window (default 10 minutes) |
| `price-change-alert.cache.chart-ttl` | chart cache window (default 5 minutes) |
| `price-change-alert.cache.logo-ttl` | resolved stock-logo cache window (default 24 hours) |
| `PRICE_CHANGE_ALERT_VAPID_PUBLIC_KEY` / `PRICE_CHANGE_ALERT_VAPID_PRIVATE_KEY` | Web Push keys; required for notifications in production |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Production PostgreSQL connection |
| `PRICE_CHANGE_ALERT_EMAIL_ENABLED` / `PRICE_CHANGE_ALERT_EMAIL_FROM` | Enable email delivery and choose the sender address |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP transport for email delivery |
| `PRICE_CHANGE_ALERT_TELEGRAM_BOT_TOKEN` | Bot token used for Telegram delivery |
| `PRICE_CHANGE_ALERT_BASE_URL` | Absolute URL inserted into email messages |

Local-only VAPID values belong in `backend/application-local.properties`, which is excluded from Git and Docker builds.

## API

| Endpoint | Description |
|----------|-------------|
| `GET /api/watchlist` | all watch items (with last price/source/status) |
| `POST /api/watchlist` | add item `{symbol, market, triggerType: PRICE\|PERCENT, thresholdValue, name?}` |
| `DELETE /api/watchlist/{id}` | remove |
| `PATCH /api/watchlist/{id}/active` | pause/resume `{active: bool}` |
| `GET /api/quote?market=NSE&symbol=RELIANCE&primary=true` | live price (primary-only or full chain) |
| `GET /api/alerts` | last 50 triggered alerts |
| `GET /api/push/vapid-key` · `POST /api/push/subscribe` · `DELETE /api/push/unsubscribe` | push plumbing |

Authentication and channel-management endpoints:

* `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`
* `GET /api/notification-preferences`, `PUT /api/notification-preferences/{channel}`

Alert logic: PRICE alerts when the configured threshold is reached; PERCENT
alerts compare the latest quote with the previous poll. A triggered watch item,
alert log, and one outbox row per enabled channel are persisted in the same
short transaction. A separate dispatcher claims each row, performs provider
I/O outside database locks, and retries temporary failures with bounded
exponential backoff. This provides at-least-once delivery.

### Notification setup

* Mobile/browser: configure VAPID keys, open the app over HTTPS, log in, and
  enable Mobile / browser in the Notify tab.
* Email: configure SMTP plus `PRICE_CHANGE_ALERT_EMAIL_ENABLED=true` and a
  verified `PRICE_CHANGE_ALERT_EMAIL_FROM` address.
* Telegram: create a bot, set its token, send `/start` to the bot, and enter the
  resulting chat ID (or an `@channel` username) in Notify.
* Discord: create an Incoming Webhook in a channel and paste its HTTPS URL in
  Notify. Webhook URLs are write-only in the API after saving.

## Caching and polling design

The app uses bounded, per-instance Caffeine caches with normalized keys and
single-flight loading. Duplicate quote, search, chart, and logo requests share
one upstream call. Successful and failed lookups have separate TTLs so brief
provider outages do not become long-lived negative results. Cache hit/miss,
eviction, and size metrics are registered with Actuator.

The browser pauses its 30-second watchlist refresh while the tab is hidden,
refreshes when focus returns, and cancels obsolete search requests. Content-
hashed frontend assets are served with immutable one-year browser caching.

The alert scheduler performs network calls outside database transactions, then
locks and updates each watch item in its own transaction. This keeps database
connections short-lived and prevents duplicate processing across app instances.

These caches are intentionally local to one process. A multi-instance deployment
should replace them with Redis (or another shared cache) and use a distributed
scheduler lock. Render's free-tier suspension is platform behavior; application
caching does not keep a sleeping service alive.

## Push notifications on mobile

Web Push needs a **secure origin (HTTPS)** or localhost. For Phase 1:

* Android: Chrome — open the site, press the bell, install the PWA ("Add to Home screen").
* iPhone: Safari 16.4+ — install the PWA first (Share -> Add to Home screen),
  then tap the bell; notifications will then appear.

Deploy behind any HTTPS host (Railway/Render/Fly free tiers are enough).

## Price charts

Every watch item expands to a chart (tap the card). History endpoint:

`GET /api/chart?market=CRYPTO&symbol=BTC&days=30` -> `{points: [[epochMs, price], ...], source, currency}`

Sources: crypto hourly/daily via CoinGecko `market_chart`; stocks daily via Yahoo `v8/finance/chart`
(cached 5 min server-side). Default display values on each card: current price, source badge,
threshold tag, and period change % (computed from the chart's first/last point).

## Free hosting (pick one)

> These three things matter: (1) 24/7 uptime — the poller must run around the clock to catch drops,
> (2) HTTPS — needed for push notifications, (3) a persistent disk for the H2 file db.

| Host | Free tier | Notes |
|------|-----------|-------|
| **Render** | 750 h/mo, spins down after 15 min idle | Use `render.yaml` below; sleep kills alerts, so ping it more often than every 15 minutes or use a paid instance |
| **Railway** | 500 h/mo trial credit | Good for testing; needs card |
| **Fly.io** | ~3 free VMs | 24/7 free small machine — best for always-on alerts |
| **Oracle Cloud Always Free** | 2 free ARM VMs (24 GB RAM) | Real 24/7 VPS, installs Java + jar directly; most reliable free option |
| **Koyeb** | 3 nano services | Similar to Render |

**Recommendation:** want zero-config today -> Render with `render.yaml`. Want reliable 24/7 alerts
for free -> Oracle Cloud VM (or Fly.io) running `java -jar app.jar` from `backend/target/`.

Steps (Render, ~10 min):

1. Push this repo to GitHub (git init, add, commit, push).
2. On Render: New -> Web Service -> connect the repo -> pick the `Dockerfile`/render.yaml.
3. Deploy. Your URL `https://price-change-alert.onrender.com` gets HTTPS automatically.
4. Keep GitHub Actions enabled. `.github/workflows/keep-render-awake.yml` checks the
   liveness endpoint every 5 minutes as an external wake-up request. Render can still
   delay scheduled GitHub jobs; guaranteed 24/7 alert polling requires a paid Render
   instance or another always-on host.
5. Open it on your phone, Add to Home screen, allow notifications -> done.

Local Docker build check: `docker build -t price-change-alert .` (`render.yaml` + `Dockerfile` included).

## Phase 2 ideas

- Users/accounts + per-user watchlists
- Notifications per-symbol dedupe window + quiet hours
- TradingView chart embed on the item page
- Email fallback when push is unavailable
- Batch symbol fetching (CoinGecko multi-id, Yahoo multi-ticker) to scale
