from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # Server
    server_name: str = "ORACLE-SERVER"
    api_key: str = "change-me"
    host: str = "0.0.0.0"
    port: int = 8090

    # Oracle
    oracle_enabled: bool = True
    oracle_user: str = "monitor_user"
    oracle_password: str = ""
    oracle_dsn: str = "localhost:1521/ORCL"

    # Alarm thresholds
    alarm_cpu_threshold: float = 85.0
    alarm_ram_threshold: float = 90.0
    alarm_disk_threshold: float = 80.0
    alarm_session_threshold: float = 80.0
    alarm_tablespace_threshold: float = 85.0

    # FCM
    fcm_enabled: bool = False
    fcm_server_key: str = ""
    fcm_device_token: str = ""

    # Storage
    collection_interval_seconds: int = 3


settings = Settings()
