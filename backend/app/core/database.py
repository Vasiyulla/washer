"""
SmartWash — Async SQLAlchemy database engine and session management.

Supports both PostgreSQL (asyncpg) and SQLite (aiosqlite) via the DATABASE_URL
environment variable.
"""

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from app.core.config import settings

import re

# Normalize Render / cloud Postgres URLs to postgresql+asyncpg://
_db_url = settings.DATABASE_URL

# Strip sslmode query param from URL because asyncpg expects it via connect_args
if "sslmode=" in _db_url:
    _db_url = re.sub(r'[\?&]sslmode=[^&]*', '', _db_url)
    if "?" not in _db_url and "&" in _db_url:
        _db_url = _db_url.replace("&", "?", 1)

if _db_url.startswith("postgres://"):
    _db_url = _db_url.replace("postgres://", "postgresql+asyncpg://", 1)
elif _db_url.startswith("postgresql://") and not _db_url.startswith("postgresql+asyncpg://"):
    _db_url = _db_url.replace("postgresql://", "postgresql+asyncpg://", 1)

_is_sqlite = _db_url.startswith("sqlite")
if _is_sqlite:
    _connect_args = {"check_same_thread": False}
else:
    # Render and cloud PostgreSQL require SSL
    _connect_args = {"ssl": "require"}

engine = create_async_engine(
    _db_url,
    echo=False,
    connect_args=_connect_args,
)

async_session = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


class Base(DeclarativeBase):
    """Declarative base for all ORM models."""
    pass


async def get_db() -> AsyncSession:  # type: ignore[misc]
    """FastAPI dependency — yields an async DB session, auto-closes."""
    async with async_session() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


async def init_db() -> None:
    """Create all tables (used for local dev / SQLite). Alembic handles Postgres."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
