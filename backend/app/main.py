"""
SmartWash — FastAPI application entry point.

Registers all routers, configures middleware, starts background tasks.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi import Limiter
from slowapi.util import get_remote_address

from app.core.database import init_db
from app.routers import auth, machines, telemetry, notifications
from app.websocket.router import router as ws_router
from app.services.offline_checker import start_offline_checker, stop_offline_checker

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup / shutdown hooks."""
    # Startup
    await init_db()
    start_offline_checker()
    logging.getLogger(__name__).info("SmartWash backend started")
    yield
    # Shutdown
    stop_offline_checker()
    logging.getLogger(__name__).info("SmartWash backend stopped")


app = FastAPI(
    title="SmartWash API",
    description="Remote washing-machine monitoring backend",
    version="1.0.0",
    lifespan=lifespan,
)

# ── Rate limiter ──────────────────────────────────────────────────────────────
limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# ── CORS (allow all origins for dev — restrict in production) ─────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Routers ───────────────────────────────────────────────────────────────────
app.include_router(auth.router)
app.include_router(machines.router)
app.include_router(telemetry.router)
app.include_router(notifications.router)
app.include_router(ws_router)


@app.get("/", tags=["health"])
async def root():
    return {"service": "SmartWash", "status": "ok", "version": "1.0.0"}


@app.get("/health", tags=["health"])
async def health():
    return {"status": "healthy"}
