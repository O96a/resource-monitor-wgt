# Resource Monitor

> Android homescreen widget + FastAPI backend for real-time Oracle server resource monitoring.
> Arc gauges · Live sparklines · High-density metrics · Push alarm notifications

**Version: 1.1.1**

---

## Project structure

```
resource-monitor/
├── server/                          # FastAPI backend (runs on Oracle server)
│   ├── main.py                      # App entry point, scheduler, auth
│   ├── config.py                    # Pydantic settings (reads .env)
│   ├── state.py                     # In-memory latest snapshot (stateless)
│   ├── requirements.txt
│   ├── Dockerfile
│   ├── resource-monitor.service     # systemd unit
│   ├── .env.example                 # Copy → .env and fill in
│   ├── metrics/
│   │   ├── os_collector.py          # psutil: CPU / RAM / disk / net / load
│   │   └── oracle_collector.py      # oracledb: sessions / tablespace / redo
│   ├── routers/
│   │   ├── metrics.py               # GET /metrics
│   │   └── alarms.py                # GET /alarms  POST /alarms/{id}/acknowledge
│   └── services/
│       ├── alarm_engine.py          # Threshold evaluation + push notifications
│       └── push.py                  # FCM push notification sender
│
└── android/                         # Kotlin + Jetpack Glance widget app
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle/
    │   └── libs.versions.toml       # Version catalog
    └── app/
        ├── build.gradle.kts
        ├── proguard-rules.pro
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/aamer/resourcemonitor/
            │   ├── ResourceMonitorApp.kt        # @HiltAndroidApp entry
            │   ├── di/
            │   │   └── DiModule.kt              # Hilt dependency injection
            │   ├── data/
            │   │   ├── api/
            │   │   │   ├── ResourceMonitorApi.kt   # Retrofit interface
            │   │   │   └── ApiClientFactory.kt     # OkHttp + API key
            │   │   ├── models/
            │   │   │   └── Models.kt               # All Moshi data classes
            │   │   └── repository/
            │   │       ├── MetricsRepository.kt
            │   │       └── SettingsRepository.kt   # DataStore prefs
            │   ├── widget/
            │   │   ├── ResourceWidget.kt           # Glance widget + receiver
            │   │   ├── WidgetStateHolder.kt        # Shared snapshot state
            │   │   └── MetricsFetchWorker.kt       # WorkManager periodic poller
            │   ├── ui/
            │   │   ├── MainActivity.kt             # Compose UI shell + screens
            │   │   └── DashboardViewModel.kt
            │   └── notifications/
            │       └── ResourceFcmService.kt       # FCM push handler
            └── res/
                ├── xml/resource_widget_info.xml    # Widget metadata
                ├── layout/widget_preview.xml
                ├── values/strings.xml
                ├── values/themes.xml
                └── drawable/ic_launcher*.xml
```

---

## Quick start — Server

### Option A: Direct Python (recommended for Oracle servers)

```bash
# 1. Copy server/ to your Oracle server
scp -r server/ oracle@your-server:/opt/resource-monitor/server/

# 2. SSH in and create a virtualenv
ssh oracle@your-server
cd /opt/resource-monitor
python3 -m venv venv
source venv/bin/activate

# 3. Install dependencies
pip install -r server/requirements.txt

# 4. Configure
cd server
cp .env.example .env
nano .env   # Fill in ORACLE_PASSWORD, API_KEY, SERVER_NAME at minimum

# 5. Run
python main.py

# 6. Verify
curl http://localhost:8080/health
```

### Option B: Docker

```bash
cd server
cp .env.example .env && nano .env

docker build -t resource-monitor .
docker run -d \
  --name resource-monitor \
  --env-file .env \
  -p 8080:8080 \
  -v $(pwd)/metrics.db:/app/metrics.db \
  resource-monitor
```

### Option C: systemd service (production)

```bash
# After Option A setup:
sudo cp server/resource-monitor.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable resource-monitor
sudo systemctl start resource-monitor
sudo systemctl status resource-monitor

# Logs
journalctl -u resource-monitor -f
```

---

## API reference

All endpoints (except `/health`) require the header:
```
X-API-Key: <your API_KEY from .env>
```

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check — no auth |
| GET | `/metrics` | Current snapshot (OS + Oracle) |
| GET | `/alarms` | Active (unacknowledged) alarms |
| POST | `/alarms/{id}/acknowledge` | Dismiss an alarm |

**Swagger UI:** `http://your-server:8080/docs`

---

## Sample /metrics response

```json
{
  "timestamp": "2026-03-19T10:30:00Z",
  "server_name": "ORACLE-PROD-01",
  "os": {
    "cpu_percent": 42.1,
    "ram_percent": 67.3,
    "ram_used_gb": 21.5,
    "ram_total_gb": 32.0,
    "disk_percent": 81.4,
    "disk_used_gb": 326.0,
    "disk_total_gb": 400.0,
    "load_avg_1m": 1.42
  },
  "oracle": {
    "active_sessions": 38,
    "max_sessions": 150,
    "session_percent": 25.3,
    "tablespace_used_gb": 187.2,
    "tablespace_total_gb": 220.0,
    "tablespace_percent": 85.1,
    "redo_switches_per_hour": 4,
    "slow_queries_count": 2,
    "db_status": "OPEN",
    "db_version": "19.3.0.0.0"
  }
}
```

---

## Oracle user setup

Run once as DBA to create a read-only monitoring user:

```sql
-- Create user
CREATE USER monitor_user IDENTIFIED BY "your_strong_password";
GRANT CREATE SESSION TO monitor_user;

-- Grant read-only access to monitoring views
GRANT SELECT ON V_$SESSION       TO monitor_user;
GRANT SELECT ON V_$INSTANCE      TO monitor_user;
GRANT SELECT ON V_$LOG_HISTORY   TO monitor_user;
GRANT SELECT ON V_$PARAMETER     TO monitor_user;
GRANT SELECT ON DBA_TABLESPACE_USAGE_METRICS TO monitor_user;

-- Verify grants
SELECT * FROM DBA_SYS_PRIVS   WHERE GRANTEE = 'MONITOR_USER';
SELECT * FROM DBA_TAB_PRIVS   WHERE GRANTEE = 'MONITOR_USER';
```

---

## Quick start — Android

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Steps

```bash
# 1. Open android/ in Android Studio
# 2. Add google-services.json to android/app/ (from Firebase console)
#    — only needed for push notifications; skip if FCM_ENABLED=false
# 3. Sync Gradle
# 4. Build & install on device
# 5. Open the app → Settings tab → enter server URL and API key
# 6. Long-press homescreen → Widgets → Resource Monitor → drag to screen
```

### Without Firebase (FCM disabled)
If `FCM_ENABLED=false` in your `.env`, you can remove the Firebase dependency:
1. Delete the `implementation(platform(libs.firebase.bom))` lines from `app/build.gradle.kts`
2. Delete the `google-services` plugin lines from both `build.gradle.kts` files
3. Delete `ResourceFcmService.kt` and remove its `<service>` entry from `AndroidManifest.xml`

---

## Alarm thresholds

Set in `.env` on the server — no app rebuild needed:

| Variable | Default | Metric |
|----------|---------|--------|
| `ALARM_CPU_THRESHOLD` | 85% | OS CPU usage |
| `ALARM_RAM_THRESHOLD` | 90% | OS RAM usage |
| `ALARM_DISK_THRESHOLD` | 80% | Root filesystem |
| `ALARM_SESSION_THRESHOLD` | 80% | Oracle sessions / max |
| `ALARM_TABLESPACE_THRESHOLD` | 85% | Total tablespace usage |

When a threshold is breached:
- The alarm is stored in SQLite and returned by `GET /alarms`
- The widget shows a red alarm bar
- If `FCM_ENABLED=true`, a push notification fires on the Android device

---

## Security notes

- The API key is sent as `X-API-Key` header — use HTTPS in production
- Recommended: put the FastAPI server behind Nginx with a self-signed cert or Let's Encrypt
- `monitor_user` is read-only — it cannot modify any Oracle objects
- The `.env` file is in `.gitignore` — never commit credentials

### Nginx reverse proxy (optional, HTTPS)

```nginx
server {
    listen 443 ssl;
    server_name your-oracle-server.local;

    ssl_certificate     /etc/ssl/certs/monitor.crt;
    ssl_certificate_key /etc/ssl/private/monitor.key;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## Customisation

### Add a new metric (server side)
1. Add a collector function in `metrics/os_collector.py` or `oracle_collector.py`
2. The value is automatically included in the snapshot and persisted to history
3. Add the dot-path to `VALID_METRICS` in `routers/history.py`
4. Add an alarm rule tuple in `services/alarm_engine.py` if needed

### Change the collection interval
Update `COLLECTION_INTERVAL_SECONDS` in `.env` — the scheduler restarts on next deploy.

### Add a second server (Android)
The current Android app supports one server. Multi-server support requires:
- A `List<ServerConfig>` in `SettingsRepository`
- A server selector in the Dashboard header
- One `MetricsRepository` instance per server

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Server runtime | Python 3.11 · FastAPI · uvicorn |
| OS metrics | psutil |
| Oracle metrics | python-oracledb (thin mode) |
| Scheduling | APScheduler |
| Push | Firebase Cloud Messaging (optional) |
| Android UI | Jetpack Compose + Material3 |
| Widget | Jetpack Glance |
| DI | Hilt |
| Background work | WorkManager |
| HTTP client | Retrofit2 + OkHttp3 |
| JSON | Moshi |
| Preferences | DataStore |

---

## Troubleshooting

| Symptom | Check |
|---------|-------|
| Widget shows "Connection error" | Confirm server is running: `curl http://<ip>:8080/health` |
| Oracle metrics missing (`null`) | Set `ORACLE_ENABLED=false` to test OS-only first; check grants |
| `ORA-01031: insufficient privileges` | Run the SQL grants block above |
| Widget never updates | WorkManager min interval is 15 min on stock Android; use "Refresh" tap for immediate fetch |
| Push notifications not arriving | Check `FCM_SERVER_KEY` and `FCM_DEVICE_TOKEN` in `.env`; verify Firebase project is linked |
| App crashes on launch | Ensure Hilt dependencies are correctly configured; check `ResourceMonitorApp.kt` has `@HiltAndroidApp` |

---

## Changelog

### v1.1.1
- Fixed graphic paint constant casing
- Fixed color reference in gauge bitmap factory

### v1.1.0
- **Radical UI overhaul**: 2x larger gauges, ultra-dense info layout
- **High-density metrics**: Network MB/s, CPU cores, RAM/Disk GB, Load avg, Oracle Tablespace/Status/Sessions
- **Live sparkline**: Real-time CPU trend chart in widget
- **Hilt DI**: Complete dependency injection refactor
- **Stateless server**: Removed database dependency for simpler deployment
- **Instant refresh**: "Syncing..." feedback, 15-min auto-refresh fallback
- **Responsive tiers**: 6 specialized layouts for different widget sizes

---

*Built with FastAPI + Jetpack Glance · Designed for Oracle production servers*
