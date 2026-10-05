"""
SmartWash — Application configuration via environment variables.

Uses pydantic-settings to load from .env file with typed defaults.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All application settings, loaded from environment / .env file."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # ── Database ──────────────────────────────────────────────────────────────
    DATABASE_URL: str = "sqlite+aiosqlite:///./smartwash.db"

    # ── JWT Auth ──────────────────────────────────────────────────────────────
    SECRET_KEY: str = "change-me-to-a-random-string-in-production"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7

    # ── Device Auth ───────────────────────────────────────────────────────────
    DEVICE_API_KEY: str = "dev-device-key-change-in-production"

    # ── State Machine Defaults (in g units) ───────────────────────────────────
    # Applied when creating a new Machine; each Machine stores its own copy.
    DEFAULT_START_THRESHOLD: float = 0.10
    DEFAULT_STOP_THRESHOLD: float = 0.03
    DEFAULT_START_DURATION_SECONDS: int = 2
    DEFAULT_STOP_DURATION_SECONDS: int = 6
    DEFAULT_OFFLINE_TIMEOUT_SECONDS: int = 90

    # ── Rate Limiting ─────────────────────────────────────────────────────────
    TELEMETRY_RATE_LIMIT: str = "300/minute"


settings = Settings()
