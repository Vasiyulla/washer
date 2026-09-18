"""
SmartWash — Machine service: CRUD, ownership enforcement, config updates.
"""

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status

from app.models.machine import Machine
from app.models.device import Device
from app.models.device_health import DeviceHealth
from app.models.user import User
from app.core.config import settings
from app.schemas.machine import MachineCreate, MachineConfigUpdate


async def get_user_machines(db: AsyncSession, user: User) -> list[Machine]:
    """Return all machines owned by the given user."""
    result = await db.execute(
        select(Machine).where(Machine.owner_id == user.id)
    )
    return list(result.scalars().all())


async def create_machine(db: AsyncSession, user: User, req: MachineCreate) -> Machine:
    """Create a machine and its linked device."""
    # Check for duplicate machine_code
    existing = await db.execute(
        select(Machine).where(Machine.machine_code == req.machine_code)
    )
    if existing.scalar_one_or_none():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Machine code '{req.machine_code}' already exists",
        )

    machine = Machine(
        owner_id=user.id,
        name=req.name,
        machine_code=req.machine_code,
        start_threshold=settings.DEFAULT_START_THRESHOLD,
        stop_threshold=settings.DEFAULT_STOP_THRESHOLD,
        start_duration_seconds=settings.DEFAULT_START_DURATION_SECONDS,
        stop_duration_seconds=settings.DEFAULT_STOP_DURATION_SECONDS,
        offline_timeout_seconds=settings.DEFAULT_OFFLINE_TIMEOUT_SECONDS,
    )
    db.add(machine)
    await db.flush()

    import uuid

    device = Device(
        id=str(uuid.uuid4()),
        machine_id=machine.id,
        device_code=req.device_code,
    )
    db.add(device)
    await db.flush()

    # Create initial DeviceHealth record
    health = DeviceHealth(id=str(uuid.uuid4()), device_id=device.id)
    db.add(health)

    await db.flush()
    await db.refresh(machine)
    return machine


async def get_machine_for_user(db: AsyncSession, user: User, machine_id: str) -> Machine:
    """Get a machine by ID, enforcing ownership."""
    result = await db.execute(
        select(Machine).where(Machine.id == machine_id, Machine.owner_id == user.id)
    )
    machine = result.scalar_one_or_none()
    if not machine:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Machine not found",
        )
    return machine


async def update_machine_config(
    db: AsyncSession, machine: Machine, req: MachineConfigUpdate
) -> Machine:
    """Update configurable thresholds on a machine."""
    update_data = req.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(machine, field, value)
    await db.flush()
    await db.refresh(machine)
    return machine
