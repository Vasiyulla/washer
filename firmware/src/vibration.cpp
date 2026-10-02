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
#include <Adafruit_MPU6050.h>
#include <Adafruit_ADXL345_U.h>
#include <Wire.h>
#include <math.h>

// ── Constants ────────────────────────────────────────────────────────────────

static const float GRAVITY = 9.81f;

// Rolling window size = duration * sample rate, but we only evaluate at
// send intervals, so we track timestamps instead of fixed-size arrays.
static const int WINDOW_SIZE = 100;  // enough for 15s at ~6 samples/sec

// ── Module State ─────────────────────────────────────────────────────────────

static Adafruit_MPU6050 mpu;
static Adafruit_ADXL345_Unified accel = Adafruit_ADXL345_Unified(12345);
static bool isMpu = false;
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
    Wire.end(); // Clean up any prior bus state
    delay(50);

    // Physical pin state check before I2C starts
    pinMode(ADXL_SDA_PIN, INPUT_PULLUP);
    pinMode(ADXL_SCL_PIN, INPUT_PULLUP);
    delay(10);
    int sdaLevel = digitalRead(ADXL_SDA_PIN);
    int sclLevel = digitalRead(ADXL_SCL_PIN);
    Serial.printf("[I2C DIAG] Pin physical levels before bus start: SDA(D21)=%s, SCL(D22)=%s\n",
                  sdaLevel ? "HIGH (OK)" : "LOW (STUCK/SHORT)",
                  sclLevel ? "HIGH (OK)" : "LOW (STUCK/SHORT)");

    // Try primary pin assignment: SDA=21, SCL=22
    int sda = ADXL_SDA_PIN;
    int scl = ADXL_SCL_PIN;
    Wire.begin(sda, scl, 100000); // 100kHz standard mode

    Serial.printf("[I2C] Scanning bus on SDA=%d, SCL=%d...\n", sda, scl);
    int nDevices = 0;
    byte lastError = 0;
    for (byte address = 1; address < 127; address++) {
        Wire.beginTransmission(address);
        byte error = Wire.endTransmission();
        if (error == 0) {
            Serial.printf("[I2C] Device found at address 0x%02X!\n", address);
            nDevices++;
        } else if (address == 0x68) {
            lastError = error;
        }
    }

    // If not found, try swapped pins (SDA=22, SCL=21) in case wires are reversed
    if (nDevices == 0) {
        Serial.printf("[I2C] 0 devices on SDA=21,SCL=22 (err on 0x68=%d). Testing swapped pins (SDA=22, SCL=21)...\n", lastError);
        Wire.end();
        delay(50);
        sda = ADXL_SCL_PIN;
        scl = ADXL_SDA_PIN;
        Wire.begin(sda, scl, 100000);
        for (byte address = 1; address < 127; address++) {
            Wire.beginTransmission(address);
            byte error = Wire.endTransmission();
            if (error == 0) {
                Serial.printf("[I2C] SWAPPED SUCCESS! Device found at address 0x%02X on SDA=%d, SCL=%d!\n", address, sda, scl);
                nDevices++;
            }
        }
    }

    if (nDevices == 0) {
        Serial.println("[I2C] No I2C devices found on bus with either pin configuration!");
    }

    // Try MPU6050 first (address 0x68 or 0x69)
    if (mpu.begin(0x68, &Wire) || mpu.begin(0x69, &Wire)) {
        isMpu = true;
        mpu.setAccelerometerRange(MPU6050_RANGE_16_G);
        mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
        Serial.println("[VIBRATION] MPU-6050 accelerometer initialized successfully!");
    } else if (accel.begin(0x53) || accel.begin(0x1D)) {
        isMpu = false;
        accel.setRange(ADXL345_RANGE_16_G);
        accel.setDataRate(ADXL345_DATARATE_100_HZ);
        Serial.println("[VIBRATION] ADXL345 accelerometer initialized successfully!");
    } else {
        Serial.println("[VIBRATION] ERROR: Neither MPU6050 nor ADXL345 found! Check I2C wiring (SDA=21, SCL=22).");
        Wire.end();
        return false;
    }

    Serial.printf("[VIBRATION] Start threshold: %.2f, Stop threshold: %.2f\n",
                  START_THRESHOLD, STOP_THRESHOLD);
    Serial.printf("[VIBRATION] Start duration: %ds, Stop duration: %ds\n",
                  START_DURATION_SEC, STOP_DURATION_SEC);

    return true;
}

VibrationReading readVibration() {
    VibrationReading reading;

    if (isMpu) {
        sensors_event_t a, g, temp;
        mpu.getEvent(&a, &g, &temp);
        reading.x = a.acceleration.x;
        reading.y = a.acceleration.y;
        reading.z = a.acceleration.z;
    } else {
        sensors_event_t event;
        accel.getEvent(&event);
        reading.x = event.acceleration.x;
        reading.y = event.acceleration.y;
        reading.z = event.acceleration.z;
    }

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
