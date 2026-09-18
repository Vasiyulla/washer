"""
SmartWash — Notification service: create, list, mark-as-read.
"""

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException, status

from app.models.notification import Notification
from app.models.user import User


async def get_user_notifications(
    db: AsyncSession, user: User, unread_only: bool = False
) -> list[Notification]:
    """List notifications for a user, optionally filtering to unread only."""
    query = select(Notification).where(Notification.user_id == user.id)
    if unread_only:
        query = query.where(Notification.is_read == False)  # noqa: E712
    query = query.order_by(Notification.created_at.desc())
    result = await db.execute(query)
    return list(result.scalars().all())


async def mark_as_read(db: AsyncSession, user: User, notification_id: str) -> Notification:
    """Mark a single notification as read, enforcing ownership."""
    result = await db.execute(
        select(Notification).where(
            Notification.id == notification_id,
            Notification.user_id == user.id,
        )
    )
    notif = result.scalar_one_or_none()
    if not notif:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Notification not found",
        )
    notif.is_read = True
    await db.flush()
    return notif
