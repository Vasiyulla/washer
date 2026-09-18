/*
 * SmartWash ESP32 Firmware — Network Module
 *
 * Handles WiFi connection (via WiFiManager captive portal), NTP time sync,
 * HTTPS POST for telemetry and heartbeat, and offline ring buffer.
 */

#ifndef NETWORK_H
#define NETWORK_H

#include <Arduino.h>
#include "vibration.h"

// ── Telemetry Payload ────────────────────────────────────────────────────────

struct TelemetryPayload {
    float x, y, z;
    float vibration;
    const char* status;
};

// ── Public API ───────────────────────────────────────────────────────────────

/**
 * Initialize WiFi using WiFiManager (captive portal on first boot).
 * Falls back to hardcoded credentials if WiFiManager fails.
 * Blocks until connected.
 */
void setupWiFi();

/**
 * Sync system time via NTP. Must be called after WiFi is connected.
 * Blocks until time is synced (up to 10 seconds).
 */
void syncNTP();

/**
 * Get the current UTC time as an ISO 8601 string.
 * Returns a pointer to a static buffer — do not store long-term.
 */
const char* getISOTimestamp();

/**
 * Check if NTP time has been synchronized.
 */
bool isTimeSynced();

/**
 * Send a telemetry reading to the backend via HTTPS POST.
 * If WiFi is disconnected, buffers the reading in a ring buffer.
 * Returns true if the POST succeeded (or was buffered).
 */
bool sendTelemetry(const TelemetryPayload& payload);

/**
 * Send a lightweight heartbeat to the backend.
 * Returns true if the POST succeeded.
 */
bool sendHeartbeat();

/**
 * Attempt to flush any buffered telemetry readings.
 * Call this periodically after WiFi reconnection.
 */
void flushBufferedTelemetry();

/**
 * Check WiFi connection and attempt reconnection with exponential backoff.
 * Returns true if connected, false if still disconnected.
 */
bool handleReconnect();

/**
 * Returns true if WiFi is currently connected.
 */
bool isWiFiConnected();

#endif // NETWORK_H
