/*
 * SmartWash ESP32 Firmware — Main Entry Point
 *
 * Reads accelerometer data, detects wash cycles via vibration analysis,
 * and reports telemetry to the SmartWash backend over HTTPS.
 *
 * Boot sequence:
 *   1. WiFi connect (WiFiManager captive portal)
 *   2. NTP time sync
 *   3. ADXL345 initialization
 *   4. Main loop: read → evaluate → report
 */

#include <Arduino.h>
#include <WiFi.h>
#include "config.h"
#include "vibration.h"
#include "network.h"

// ── Timing State ─────────────────────────────────────────────────────────────

static unsigned long lastSampleTime = 0;
static unsigned long lastSendTime = 0;
static unsigned long lastHeartbeatTime = 0;
static MachineState previousState = STATE_IDLE;

// Accumulated readings for averaging between send intervals
static float accX = 0, accY = 0, accZ = 0, accVib = 0;
static int sampleCount = 0;

// ═══════════════════════════════════════════════════════════════════════════════
// Setup
// ═══════════════════════════════════════════════════════════════════════════════

void setup() {
    Serial.begin(115200);
    delay(1000);

    Serial.println();
    Serial.println("╔══════════════════════════════════════╗");
    Serial.println("║     SmartWash ESP32 Firmware v" FIRMWARE_VER "    ║");
    Serial.println("║     Device: " DEVICE_CODE "               ║");
    Serial.println("╚══════════════════════════════════════╝");
    Serial.println();

    // 1. Initialize sensor (loop until detected so user can adjust SCL/SDA pins)
    while (!initAccelerometer()) {
        Serial.println("[MAIN] Waiting for MPU-6050... Please ensure SCL (D22) & SDA (D21) pins make firm contact.");
        delay(1500);
    }
    Serial.println("[MAIN] SUCCESS: MPU-6050 is connected and working!");

    // 2. Connect to WiFi
    setupWiFi();

    // 3. Sync time via NTP (required before sending timestamped data)
    syncNTP();
    if (!isTimeSynced()) {
        Serial.println("[MAIN] WARNING: Proceeding without NTP sync!");
    }

    // 4. Send initial heartbeat
    if (sendHeartbeat()) {
        Serial.println("[MAIN] Initial heartbeat sent");
    }

    Serial.println("[MAIN] Setup complete — entering main loop");
    Serial.println();
}

// ═══════════════════════════════════════════════════════════════════════════════
// Main Loop
// ═══════════════════════════════════════════════════════════════════════════════

void loop() {
    unsigned long now = millis();

    // ── WiFi health check ────────────────────────────────────────────────────
    if (!isWiFiConnected()) {
        handleReconnect();
        // Continue reading sensor even if WiFi is down
    }

    // ── Sample accelerometer at SAMPLE_RATE_HZ ──────────────────────────────
    if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
        lastSampleTime = now;

        VibrationReading reading = readVibration();

        // Print live reading to Serial every 500ms so user can see it react
        static unsigned long lastPrint = 0;
        if (now - lastPrint >= 500) {
            lastPrint = now;
            Serial.printf("[VIBRATION LIVE] X: %5.2f | Y: %5.2f | Z: %5.2f | Vibration Deviation: %5.2f m/s²\n",
                          reading.x, reading.y, reading.z, reading.deviation);
        }

        // Accumulate for averaging
        accX += reading.x;
        accY += reading.y;
        accZ += reading.z;
        accVib += reading.deviation;
        sampleCount++;

        // Evaluate state on every sample
        MachineState newState = evaluateState(reading.deviation);

        // ── Immediate send on state transition ───────────────────────────────
        if (newState != previousState) {
            Serial.printf("[MAIN] STATE TRANSITION: %s → %s\n",
                          stateToString(previousState), stateToString(newState));

            // Send this specific reading immediately
            TelemetryPayload payload;
            payload.x = reading.x;
            payload.y = reading.y;
            payload.z = reading.z;
            payload.vibration = reading.deviation;
            payload.status = stateToString(newState);

            sendTelemetry(payload);

            // Reset averages
            accX = accY = accZ = accVib = 0;
            sampleCount = 0;
            lastSendTime = now;

            previousState = newState;
        }
    }

    // ── Periodic telemetry send ──────────────────────────────────────────────
    if (now - lastSendTime >= SEND_INTERVAL_MS && sampleCount > 0) {
        lastSendTime = now;

        // Compute averages over the sample window
        TelemetryPayload payload;
        payload.x = accX / sampleCount;
        payload.y = accY / sampleCount;
        payload.z = accZ / sampleCount;
        payload.vibration = accVib / sampleCount;
        payload.status = stateToString(getCurrentState());

        bool sent = sendTelemetry(payload);

        Serial.printf("[MAIN] Telemetry %s: vib=%.3f, state=%s, samples=%d\n",
                      sent ? "SENT" : "BUFFERED",
                      payload.vibration, payload.status, sampleCount);

        // Reset accumulators
        accX = accY = accZ = accVib = 0;
        sampleCount = 0;
    }

    // ── Heartbeat (when idle, no telemetry sends happening) ──────────────────
    if (now - lastHeartbeatTime >= HEARTBEAT_INTERVAL_MS) {
        lastHeartbeatTime = now;

        if (sendHeartbeat()) {
            Serial.printf("[MAIN] Heartbeat sent (uptime: %lus, RSSI: %d dBm)\n",
                          now / 1000, WiFi.RSSI());
        }
    }

    // Small yield to prevent watchdog reset
    delay(1);
}
