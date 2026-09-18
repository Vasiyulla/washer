/*
 * SmartWash ESP32 Firmware — Vibration Detection Implementation
 *
 * Reads MPU6050, computes vibration deviation from gravity baseline,
 * and implements rolling-window state detection matching the server-side
 * state machine logic.
 */

#include "vibration.h"
#include "config.h"
#include <Adafruit_Sensor.h>
#include <Adafruit_ADXL345_U.h>
#include <Wire.h>
#include <math.h>

// ── Constants ────────────────────────────────────────────────────────────────

static const float GRAVITY = 9.81f;

// Rolling window size = duration * sample rate, but we only evaluate at
// send intervals, so we track timestamps instead of fixed-size arrays.
static const int WINDOW_SIZE = 100;  // enough for 15s at ~6 samples/sec

// ── Module State ─────────────────────────────────────────────────────────────

static Adafruit_ADXL345_Unified accel = Adafruit_ADXL345_Unified(12345);
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
    Wire.begin(ADXL_SDA_PIN, ADXL_SCL_PIN);

    // Try default I2C address 0x53, fallback to 0x1D if ALT ADDRESS is tied high
    if (!accel.begin(0x53) && !accel.begin(0x1D)) {
        Serial.println("[VIBRATION] ERROR: ADXL345 not found! Check I2C wiring (SDA=21, SCL=22).");
        return false;
    }

    // Configure sensor ranges: ±16g provides plenty of dynamic range for violent spin cycles
    accel.setRange(ADXL345_RANGE_16_G);
    accel.setDataRate(ADXL345_DATARATE_100_HZ);

    Serial.println("[VIBRATION] ADXL345 accelerometer initialized successfully");
    Serial.printf("[VIBRATION] Start threshold: %.2f, Stop threshold: %.2f\n",
                  START_THRESHOLD, STOP_THRESHOLD);
    Serial.printf("[VIBRATION] Start duration: %ds, Stop duration: %ds\n",
                  START_DURATION_SEC, STOP_DURATION_SEC);

    return true;
}

VibrationReading readVibration() {
    sensors_event_t event;
    accel.getEvent(&event);

    VibrationReading reading;
    reading.x = event.acceleration.x;
    reading.y = event.acceleration.y;
    reading.z = event.acceleration.z;
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
