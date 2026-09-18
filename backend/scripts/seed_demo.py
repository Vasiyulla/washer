"""
SmartWash -- Seed / Demo Script

Simulates a full IDLE -> RUNNING -> COMPLETED washing cycle by posting fake
telemetry to the running backend. Tests the state machine, cycle creation,
and WebSocket broadcast end-to-end without real hardware.

Usage:
    cd backend
    python -m scripts.seed_demo

    Or with Docker:
    docker compose exec backend python -m scripts.seed_demo

Requires the backend to be running at http://localhost:8000.
"""

import httpx
import time
import math
import random
from datetime import datetime, timezone

BASE = "http://localhost:8000"
API = f"{BASE}/api/v1"
DEVICE_API_KEY = "dev-device-key-change-in-production"

# Demo data
DEMO_EMAIL = "demo@smartwash.io"
DEMO_PASSWORD = "demo1234"
DEMO_NAME = "Demo User"
MACHINE_CODE = "WM-001"
DEVICE_CODE = "ESP32-001"


def log(msg: str):
    ts = datetime.now().strftime("%H:%M:%S")
    print(f"  [{ts}] {msg}")


def main():
    print("=" * 60)
    print("  SmartWash -- Seed / Demo Script")
    print("=" * 60)

    client = httpx.Client(timeout=10)

    # -- 1. Health check -------------------------------------------------------
    print("\n> Checking backend health...")
    try:
        r = client.get(f"{BASE}/health")
        r.raise_for_status()
        log(f"Backend healthy: {r.json()}")
    except Exception as e:
        print(f"  [FAIL] Backend not reachable at {BASE}: {e}")
        print("  Make sure the backend is running (uvicorn app.main:app --reload)")
        return

    # -- 2. Register demo user -------------------------------------------------
    print("\n> Registering demo user...")
    r = client.post(f"{API}/auth/register", json={
        "email": DEMO_EMAIL,
        "password": DEMO_PASSWORD,
        "name": DEMO_NAME,
    })
    if r.status_code == 201:
        tokens = r.json()
        log(f"Registered: {DEMO_EMAIL}")
    elif r.status_code == 409:
        log("User already exists, logging in...")
        r = client.post(f"{API}/auth/login", json={
            "email": DEMO_EMAIL,
            "password": DEMO_PASSWORD,
        })
        r.raise_for_status()
        tokens = r.json()
    else:
        print(f"  [FAIL] Registration failed: {r.status_code} {r.text}")
        return

    access_token = tokens["access_token"]
    auth_headers = {"Authorization": f"Bearer {access_token}"}
    log(f"Access token: {access_token[:20]}...")

    # -- 3. Create machine -----------------------------------------------------
    print("\n> Creating machine...")
    r = client.post(f"{API}/machines", json={
        "name": "Kitchen Washer",
        "machine_code": MACHINE_CODE,
        "device_code": DEVICE_CODE,
    }, headers=auth_headers)

    if r.status_code == 201:
        machine = r.json()
        log(f"Created machine: {machine['name']} ({machine['machine_code']})")
    elif r.status_code == 409:
        log("Machine already exists, fetching...")
        r = client.get(f"{API}/machines", headers=auth_headers)
        r.raise_for_status()
        machines = r.json()
        machine = next((m for m in machines if m["machine_code"] == MACHINE_CODE), None)
        if not machine:
            print("  [FAIL] Could not find existing machine")
            return
    else:
        print(f"  [FAIL] Failed: {r.status_code} {r.text}")
        return

    machine_id = machine["id"]
    log(f"Machine ID: {machine_id}")

    # -- 4. Check initial status -----------------------------------------------
    print("\n> Initial machine status...")
    r = client.get(f"{API}/machines/{machine_id}/status", headers=auth_headers)
    r.raise_for_status()
    log(f"Status: {r.json()}")

    # -- 5. Simulate telemetry: IDLE phase (10s) ------------------------------
    print("\n> Phase 1: IDLE -- sending low vibration telemetry (10 readings)...")
    device_headers = {"X-Device-API-Key": DEVICE_API_KEY}

    for i in range(10):
        # Low vibration around 0.1-0.3 (well below start_threshold of 2.0)
        vib = 0.1 + random.uniform(0, 0.2)
        payload = {
            "device_id": DEVICE_CODE,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "x": round(random.uniform(-0.1, 0.1), 3),
            "y": round(random.uniform(-0.1, 0.1), 3),
            "z": round(9.8 + random.uniform(-0.1, 0.1), 3),
            "vibration": round(vib, 3),
            "status": "IDLE",
        }
        r = client.post(f"{API}/telemetry", json=payload, headers=device_headers)
        log(f"  IDLE telemetry #{i+1}: vib={vib:.3f} -> {r.status_code}")
        time.sleep(0.5)

    # Check status -- should still be IDLE
    r = client.get(f"{API}/machines/{machine_id}/status", headers=auth_headers)
    log(f"  Status after IDLE phase: {r.json()['current_status']}")

    # -- 6. Simulate telemetry: ramp-up -> RUNNING (8s of sustained high vib) --
    print("\n> Phase 2: STARTING -- ramping vibration above threshold (8 readings)...")
    for i in range(8):
        # High vibration 3.0-5.0 (above start_threshold of 2.0)
        vib = 3.0 + random.uniform(0, 2.0)
        payload = {
            "device_id": DEVICE_CODE,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "x": round(random.uniform(-2, 2), 3),
            "y": round(random.uniform(-2, 2), 3),
            "z": round(9.8 + random.uniform(-2, 2), 3),
            "vibration": round(vib, 3),
            "status": "RUNNING",
        }
        r = client.post(f"{API}/telemetry", json=payload, headers=device_headers)
        log(f"  STARTING telemetry #{i+1}: vib={vib:.3f} -> {r.status_code}")
        time.sleep(1.0)

    r = client.get(f"{API}/machines/{machine_id}/status", headers=auth_headers)
    status = r.json()["current_status"]
    log(f"  Status after ramp-up: {status}")
    if status == "RUNNING":
        log("  [OK] State machine correctly transitioned to RUNNING!")

    # -- 7. Simulate telemetry: RUNNING phase (15s of varying high vib) --------
    print("\n> Phase 3: RUNNING -- sustained high vibration (15 readings)...")
    for i in range(15):
        vib = 2.5 + random.uniform(0, 3.0)
        # Simulate occasional high vibration
        if random.random() < 0.1:
            vib = 8.0 + random.uniform(0, 2.0)
        payload = {
            "device_id": DEVICE_CODE,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "x": round(random.uniform(-3, 3), 3),
            "y": round(random.uniform(-3, 3), 3),
            "z": round(9.8 + random.uniform(-3, 3), 3),
            "vibration": round(vib, 3),
            "status": "RUNNING",
        }
        r = client.post(f"{API}/telemetry", json=payload, headers=device_headers)
        log(f"  RUNNING telemetry #{i+1}: vib={vib:.3f} -> {r.status_code}")
        time.sleep(1.0)

    # -- 8. Simulate telemetry: wind-down -> COMPLETED -------------------------
    print("\n> Phase 4: STOPPING -- vibration dropping below threshold (18 readings)...")
    for i in range(18):
        # Low vibration 0.05-0.3 (below stop_threshold of 0.5)
        vib = 0.05 + random.uniform(0, 0.2)
        payload = {
            "device_id": DEVICE_CODE,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "x": round(random.uniform(-0.05, 0.05), 3),
            "y": round(random.uniform(-0.05, 0.05), 3),
            "z": round(9.8 + random.uniform(-0.05, 0.05), 3),
            "vibration": round(vib, 3),
            "status": "IDLE",
        }
        r = client.post(f"{API}/telemetry", json=payload, headers=device_headers)
        log(f"  STOPPING telemetry #{i+1}: vib={vib:.3f} -> {r.status_code}")
        time.sleep(1.0)

    r = client.get(f"{API}/machines/{machine_id}/status", headers=auth_headers)
    status = r.json()["current_status"]
    log(f"  Status after wind-down: {status}")
    if status == "COMPLETED":
        log("  [OK] State machine correctly transitioned to COMPLETED!")

    # -- 9. Verify cycle was created -------------------------------------------
    print("\n> Checking washing cycles...")
    r = client.get(f"{API}/machines/{machine_id}/cycles?range=today", headers=auth_headers)
    r.raise_for_status()
    cycles = r.json()
    log(f"Found {len(cycles)} cycle(s)")
    for cycle in cycles:
        log(f"  Cycle {cycle['id'][:8]}...: {cycle['status']}, "
            f"duration={cycle.get('duration_seconds')}s, "
            f"avg_vib={cycle.get('avg_vibration')}, "
            f"max_vib={cycle.get('max_vibration')}, "
            f"min_vib={cycle.get('min_vibration')}")

    # -- 10. Verify notifications ----------------------------------------------
    print("\n> Checking notifications...")
    r = client.get(f"{API}/notifications", headers=auth_headers)
    r.raise_for_status()
    notifs = r.json()
    log(f"Found {len(notifs)} notification(s)")
    for n in notifs[:5]:
        log(f"  [{n['type']}] {n['message']}")

    # -- 11. Check health endpoint ---------------------------------------------
    print("\n> Checking device health...")
    r = client.get(f"{API}/machines/{machine_id}/health", headers=auth_headers)
    r.raise_for_status()
    log(f"Health: {r.json()}")

    # -- Done ------------------------------------------------------------------
    print("\n" + "=" * 60)
    print("  [OK] Demo completed successfully!")
    print(f"  Machine: {MACHINE_CODE} (ID: {machine_id})")
    print(f"  Final status: {status}")
    print(f"  Cycles: {len(cycles)}")
    print(f"  Notifications: {len(notifs)}")
    print("=" * 60)


if __name__ == "__main__":
    main()
