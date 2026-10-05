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
    """Create all tables and seed default user, machine, and ESP32 device."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    # Seed defaults so the device and app always find their entities on startup
    try:
        from sqlalchemy import select
        from app.models.user import User
        from app.models.machine import Machine
        from app.models.device import Device
        from app.core.security import hash_password
        import uuid

        async with async_session() as session:
            # 1. User
            u_res = await session.execute(select(User).where(User.email == "demo@smartwash.io"))
            user = u_res.scalar_one_or_none()
            if not user:
                user = User(
                    id=str(uuid.uuid4()),
                    email="demo@smartwash.io",
                    name="Demo User",
                    hashed_password=hash_password("demo1234"),
                )
                session.add(user)
                await session.flush()

            # 2. Machine
            m_res = await session.execute(select(Machine).where(Machine.machine_code == "WM-001"))
            machine = m_res.scalar_one_or_none()
            if not machine:
                machine = Machine(
                    id=str(uuid.uuid4()),
                    owner_id=user.id,
                    name="Kitchen Washer",
                    machine_code="WM-001",
                    start_threshold=0.10,
                    stop_threshold=0.03,
                    start_duration_seconds=2,
                    stop_duration_seconds=6,
                    offline_timeout_seconds=90,
                )
                session.add(machine)
                await session.flush()

            # 3. Device
            d_res = await session.execute(select(Device).where(Device.device_code == "ESP32-001"))
            device = d_res.scalar_one_or_none()
            if not device:
                device = Device(
                    id=str(uuid.uuid4()),
                    machine_id=machine.id,
                    device_code="ESP32-001",
                    firmware_version="1.0.0",
                    is_online=True,
                )
                session.add(device)
                await session.flush()

            await session.commit()
    except Exception as e:
        import logging
        logging.getLogger(__name__).warning("Default seeding notice: %s", e)
