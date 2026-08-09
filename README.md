# Price Change Alert — Indian stock & crypto price-change alerts (PWA)

Phase 1 mobile-first web app: add NSE/BSE stocks and crypto coins to a watchlist;
the backend polls prices and pushes a notification to your phone the moment one drops.

## Stack

| Layer  | Tech |
|--------|------|
| Backend  | Java 21, Spring Boot 4, H2 locally / PostgreSQL in production, WebClient, web-push |
| Frontend | React 19 + Vite 8, service worker, manifest (PWA) |
| Notifications | Web Push / VAPID (works on Android Chrome + iOS Safari 16.4+, HTTPS required) |

## Data sources (fallback chain)

| Market | Primary | Fallback |
|--------|---------|----------|
| Crypto  | CoinGecko (official API, free, no key) | Yahoo Finance (`BTC-INR` / `BTC-USD`) |
| NSE     | Yahoo Finance (`RELIANCE.NS`) | — |
| BSE     | Yahoo Finance (`RELIANCE.BO`) | — |

Crypto is quoted in USD (stocks in INR). NSE/BSE direct endpoints
(nseindia.com / api.bseindia.com) were tried and removed: they are bot-blocked
or unstable, so stocks use Yahoo Finance directly. Yahoo's unofficial chart API
can throttle at high poll rates — keep the watchlist small or raise
`pricedrop.poll.interval-ms`.

## Run it

Backend (serves the built PWA too):

```
cd backend
mvnw package -DskipTests            (first run downloads Maven)
java -jar target\pricedrop-0.0.1-SNAPSHOT.jar
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
(contents of frontend/dist auto-copied to backend/src/main/resources/static)
cd ..\backend
mvnw.cmd package
```

## Configuration

| Key | Meaning |
|-----|---------|
| `pricedrop.poll.interval-ms` | alert engine poll cadence (default 30000) |
| `PRICEDROP_VAPID_PUBLIC_KEY` / `PRICEDROP_VAPID_PRIVATE_KEY` | Web Push keys; required for notifications in production |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Production PostgreSQL connection |

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

Alert logic: PRICE alerts when `price <= threshold`; PERCENT alerts when the
drop from the previous polled price is `>= threshold%`. Re-alerts only fire on
a further 0.5% drop below the last alerted level, so a symbol sitting under the
threshold doesn't spam you every 30 s.

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
| **Render** | 750 h/mo, spins down after 15 min idle | Use `render.yaml` below; sleep kills alerts — wake it once an hour (cron hitting the URL) or upgrade to $7 starter |
| **Railway** | 500 h/mo trial credit | Good for testing; needs card |
| **Fly.io** | ~3 free VMs | 24/7 free small machine — best for always-on alerts |
| **Oracle Cloud Always Free** | 2 free ARM VMs (24 GB RAM) | Real 24/7 VPS, installs Java + jar directly; most reliable free option |
| **Koyeb** | 3 nano services | Similar to Render |

**Recommendation:** want zero-config today -> Render with `render.yaml`. Want reliable 24/7 alerts
for free -> Oracle Cloud VM (or Fly.io) running `java -jar app.jar` from `backend/target/`.

Steps (Render, ~10 min):

1. Push this repo to GitHub (git init, add, commit, push).
2. On Render: New -> Web Service -> connect the repo -> pick the `Dockerfile`/render.yaml.
3. Deploy. Your URL `https://pricedrop.onrender.com` gets HTTPS automatically.
4. Open it on your phone, Add to Home screen, allow notifications -> done.

Local Docker build check: `docker build -t pricedrop .` (`render.yaml` + `Dockerfile` included).

## Phase 2 ideas

- Users/accounts + per-user watchlists
- Notifications per-symbol dedupe window + quiet hours
- TradingView chart embed on the item page
- Email fallback when push is unavailable
- Batch symbol fetching (CoinGecko multi-id, Yahoo multi-ticker) to scale
