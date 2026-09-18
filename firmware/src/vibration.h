/*
 * SmartWash ESP32 Firmware — Vibration Detection Module
 *
 * Reads the MPU6050 accelerometer, computes vibration magnitude/deviation,
 * and maintains local state tracking (IDLE/RUNNING) using a rolling window
 * of recent readings.
 */

#ifndef VIBRATION_H
#define VIBRATION_H

#include <Arduino.h>

// ── Data Structures ──────────────────────────────────────────────────────────

enum MachineState {
    STATE_IDLE,
    STATE_RUNNING
};

struct VibrationReading {
    float x;            // Raw accelerometer X (m/s²)
    float y;            // Raw accelerometer Y (m/s²)
    float z;            // Raw accelerometer Z (m/s²)
    float magnitude;    // sqrt(x² + y² + z²)
    float deviation;    // abs(magnitude - GRAVITY) — vibration metric
};

// ── Public API ───────────────────────────────────────────────────────────────

/**
 * Initialize the MPU6050 accelerometer over I2C.
 * Returns true on success, false if sensor not found.
 */
bool initAccelerometer();

/**
 * Read a single vibration sample from the MPU6050.
 * Returns a VibrationReading with raw axes, magnitude, and deviation.
 */
VibrationReading readVibration();

/**
 * Evaluate the current machine state based on the latest deviation reading.
 * Uses a rolling window to enforce sustained threshold crossing.
 *
 * @param deviation  The latest vibration deviation value.
 * @return           Current machine state (STATE_IDLE or STATE_RUNNING).
 *
 * Call this after every readVibration() to update the state.
 * Check for transitions by comparing with the previous state.
 */
MachineState evaluateState(float deviation);

/**
 * Get the current machine state without updating it.
 */
MachineState getCurrentState();

/**
 * Get a string representation of the state for telemetry payloads.
 */
const char* stateToString(MachineState state);

#endif // VIBRATION_H
