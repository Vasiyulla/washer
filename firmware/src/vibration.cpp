/*
 * SmartWash ESP32 Firmware — Vibration Detection Implementation
 *
 * Reads MPU6050, computes vibration deviation from gravity baseline,
 * and implements rolling-window state detection matching the server-side
 * state machine logic.
 */

#include "vibration.h"
#include "config.h"
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>
#include <math.h>

// ── Constants ────────────────────────────────────────────────────────────────

static const float GRAVITY = 9.81f;

// Rolling window size = duration * sample rate, but we only evaluate at
// send intervals, so we track timestamps instead of fixed-size arrays.
static const int WINDOW_SIZE = 100;  // enough for 15s at ~6 samples/sec

// ── Module State ─────────────────────────────────────────────────────────────

static Adafruit_MPU6050 mpu;
static MachineState currentState = STATE_IDLE;

// Rolling window: circular buffer of (timestamp_ms, deviation) pairs
struct TimedReading {
    unsigned long timestamp_ms;
    float deviation;
};

static TimedReading window[WINDOW_SIZE];
static int windowHead = 0;
static int windowCount = 0;

// ── Private Helpers ──────────────────────────────────────────────────────────

static void addToWindow(float deviation) {
    window[windowHead].timestamp_ms = millis();
    window[windowHead].deviation = deviation;
    windowHead = (windowHead + 1) % WINDOW_SIZE;
    if (windowCount < WINDOW_SIZE) windowCount++;
}

/**
 * Check if all readings in the last `duration_sec` seconds satisfy a condition.
 * `above` = true  → check all > threshold
 * `above` = false → check all < threshold
 * Returns false if fewer than 2 readings exist in the window.
 */
static bool windowCheck(float threshold, int duration_sec, bool above) {
    unsigned long now = millis();
    unsigned long cutoff = now - (unsigned long)(duration_sec * 1000);
    int count = 0;
    bool allSatisfy = true;

    for (int i = 0; i < windowCount; i++) {
        int idx = (windowHead - 1 - i + WINDOW_SIZE) % WINDOW_SIZE;
        if (window[idx].timestamp_ms < cutoff) break;

        count++;
        if (above) {
            if (window[idx].deviation <= threshold) { allSatisfy = false; break; }
        } else {
            if (window[idx].deviation >= threshold) { allSatisfy = false; break; }
        }
    }

    // Need at least 2 readings to be confident
    return (count >= 2) && allSatisfy;
}

// ── Public API Implementation ────────────────────────────────────────────────

bool initAccelerometer() {
    Wire.begin(MPU_SDA_PIN, MPU_SCL_PIN);

    if (!mpu.begin()) {
        Serial.println("[VIBRATION] ERROR: MPU6050 not found! Check wiring.");
        return false;
    }

    // Configure sensor ranges
    mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
    mpu.setGyroRange(MPU6050_RANGE_500_DEG);
    mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);

    Serial.println("[VIBRATION] MPU6050 initialized");
    Serial.printf("[VIBRATION] Start threshold: %.2f, Stop threshold: %.2f\n",
                  START_THRESHOLD, STOP_THRESHOLD);
    Serial.printf("[VIBRATION] Start duration: %ds, Stop duration: %ds\n",
                  START_DURATION_SEC, STOP_DURATION_SEC);

    return true;
}

VibrationReading readVibration() {
    sensors_event_t accel, gyro, temp;
    mpu.getEvent(&accel, &gyro, &temp);

    VibrationReading reading;
    reading.x = accel.acceleration.x;
    reading.y = accel.acceleration.y;
    reading.z = accel.acceleration.z;
    reading.magnitude = sqrtf(
        reading.x * reading.x +
        reading.y * reading.y +
        reading.z * reading.z
    );
    // Deviation from gravity baseline — this is the vibration metric.
    // A stationary sensor reads ~9.81 m/s²; deviation ~0 when still.
    reading.deviation = fabsf(reading.magnitude - GRAVITY);

    return reading;
}

MachineState evaluateState(float deviation) {
    addToWindow(deviation);

    MachineState previousState = currentState;

    switch (currentState) {
        case STATE_IDLE:
            // Transition to RUNNING if vibration sustained above start threshold
            if (windowCheck(START_THRESHOLD, START_DURATION_SEC, true)) {
                currentState = STATE_RUNNING;
                Serial.println("[VIBRATION] *** State transition: IDLE → RUNNING ***");
            }
            break;

        case STATE_RUNNING:
            // Transition to IDLE (completed) if vibration sustained below stop threshold
            if (windowCheck(STOP_THRESHOLD, STOP_DURATION_SEC, false)) {
                currentState = STATE_IDLE;
                Serial.println("[VIBRATION] *** State transition: RUNNING → IDLE (cycle complete) ***");
            }
            break;
    }

    return currentState;
}

MachineState getCurrentState() {
    return currentState;
}

const char* stateToString(MachineState state) {
    switch (state) {
        case STATE_IDLE:    return "IDLE";
        case STATE_RUNNING: return "RUNNING";
        default:            return "UNKNOWN";
    }
}
