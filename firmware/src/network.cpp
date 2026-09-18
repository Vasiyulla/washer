/*
 * SmartWash ESP32 Firmware — Network Module Implementation
 *
 * WiFiManager captive portal, NTP sync, HTTPS telemetry POST,
 * ring buffer for offline resilience, exponential backoff reconnection.
 */

#include "network.h"
#include "config.h"
#include <WiFi.h>
#include <WiFiManager.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <time.h>

// ── Module State ─────────────────────────────────────────────────────────────

static bool ntpSynced = false;
static unsigned long reconnectDelay = RECONNECT_MIN_MS;
static unsigned long lastReconnectAttempt = 0;

// Ring buffer for offline telemetry storage
struct BufferedPayload {
    TelemetryPayload data;
    char timestamp[30];
    bool occupied;
};

static BufferedPayload ringBuffer[RING_BUFFER_SIZE];
static int ringHead = 0;
static int ringCount = 0;

// ── WiFi Setup ───────────────────────────────────────────────────────────────

void setupWiFi() {
    Serial.println("[NETWORK] Starting WiFi setup...");

    WiFiManager wm;

    // Set timeout for the captive portal (seconds).
    // If no one configures it within 180s, fall back to hardcoded creds.
    wm.setConfigPortalTimeout(180);

    // Auto-connect: tries saved credentials first, opens portal if they fail.
    bool connected = wm.autoConnect("SmartWash-Setup", "smartwash");

    if (!connected) {
        Serial.println("[NETWORK] WiFiManager failed, trying hardcoded credentials...");
        WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

        int attempts = 0;
        while (WiFi.status() != WL_CONNECTED && attempts < 30) {
            delay(500);
            Serial.print(".");
            attempts++;
        }

        if (WiFi.status() != WL_CONNECTED) {
            Serial.println("\n[NETWORK] ERROR: WiFi connection failed! Restarting...");
            ESP.restart();
        }
    }

    Serial.printf("[NETWORK] WiFi connected! IP: %s, RSSI: %d dBm\n",
                  WiFi.localIP().toString().c_str(), WiFi.RSSI());
    reconnectDelay = RECONNECT_MIN_MS;
}

// ── NTP Time Sync ────────────────────────────────────────────────────────────

void syncNTP() {
    Serial.println("[NETWORK] Syncing time via NTP...");
    configTime(NTP_GMT_OFFSET, NTP_DST_OFFSET, NTP_SERVER_1, NTP_SERVER_2);

    // Wait up to 10 seconds for NTP sync
    struct tm timeinfo;
    int retries = 0;
    while (!getLocalTime(&timeinfo) && retries < 20) {
        delay(500);
        retries++;
    }

    if (getLocalTime(&timeinfo)) {
        ntpSynced = true;
        char buf[30];
        strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
        Serial.printf("[NETWORK] NTP synced: %s\n", buf);
    } else {
        Serial.println("[NETWORK] WARNING: NTP sync failed! Timestamps may be inaccurate.");
    }
}

const char* getISOTimestamp() {
    static char buf[30];
    struct tm timeinfo;
    if (getLocalTime(&timeinfo)) {
        strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
    } else {
        snprintf(buf, sizeof(buf), "1970-01-01T00:00:00Z");
    }
    return buf;
}

bool isTimeSynced() {
    return ntpSynced;
}

// ── HTTPS POST ───────────────────────────────────────────────────────────────

static bool postJSON(const char* path, const JsonDocument& doc) {
    if (WiFi.status() != WL_CONNECTED) {
        return false;
    }

    WiFiClientSecure client;
    // For development: skip certificate verification.
    // For production: load the server's CA certificate here.
    client.setInsecure();

    HTTPClient http;
    String url = String(API_BASE_URL) + path;
    http.begin(client, url);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-Device-API-Key", DEVICE_API_KEY);

    String body;
    serializeJson(doc, body);

    int httpCode = http.POST(body);
    bool success = (httpCode >= 200 && httpCode < 300);

    if (!success) {
        Serial.printf("[NETWORK] POST %s failed: HTTP %d\n", path, httpCode);
        if (httpCode > 0) {
            Serial.printf("[NETWORK] Response: %s\n", http.getString().c_str());
        }
    }

    http.end();
    return success;
}

// ── Telemetry ────────────────────────────────────────────────────────────────

static void bufferTelemetry(const TelemetryPayload& payload) {
    ringBuffer[ringHead].data = payload;
    strncpy(ringBuffer[ringHead].timestamp, getISOTimestamp(), 29);
    ringBuffer[ringHead].timestamp[29] = '\0';
    ringBuffer[ringHead].occupied = true;
    ringHead = (ringHead + 1) % RING_BUFFER_SIZE;
    if (ringCount < RING_BUFFER_SIZE) ringCount++;
    Serial.printf("[NETWORK] Buffered telemetry (buffer: %d/%d)\n", ringCount, RING_BUFFER_SIZE);
}

bool sendTelemetry(const TelemetryPayload& payload) {
    if (!isWiFiConnected()) {
        bufferTelemetry(payload);
        return false;
    }

    JsonDocument doc;
    doc["device_id"] = DEVICE_CODE;
    doc["timestamp"] = getISOTimestamp();
    doc["x"] = payload.x;
    doc["y"] = payload.y;
    doc["z"] = payload.z;
    doc["vibration"] = payload.vibration;
    doc["status"] = payload.status;

    bool ok = postJSON(TELEMETRY_PATH, doc);
    if (!ok) {
        bufferTelemetry(payload);
    }
    return ok;
}

bool sendHeartbeat() {
    if (!isWiFiConnected()) return false;

    JsonDocument doc;
    doc["wifi_rssi"] = WiFi.RSSI();
    doc["uptime_seconds"] = (int)(millis() / 1000);
    doc["firmware_version"] = FIRMWARE_VER;

    return postJSON(HEARTBEAT_PATH, doc);
}

void flushBufferedTelemetry() {
    if (ringCount == 0 || !isWiFiConnected()) return;

    Serial.printf("[NETWORK] Flushing %d buffered readings...\n", ringCount);

    // Flush from oldest to newest
    int start = (ringHead - ringCount + RING_BUFFER_SIZE) % RING_BUFFER_SIZE;
    int flushed = 0;

    for (int i = 0; i < ringCount; i++) {
        int idx = (start + i) % RING_BUFFER_SIZE;
        if (!ringBuffer[idx].occupied) continue;

        JsonDocument doc;
        doc["device_id"] = DEVICE_CODE;
        doc["timestamp"] = ringBuffer[idx].timestamp;
        doc["x"] = ringBuffer[idx].data.x;
        doc["y"] = ringBuffer[idx].data.y;
        doc["z"] = ringBuffer[idx].data.z;
        doc["vibration"] = ringBuffer[idx].data.vibration;
        doc["status"] = ringBuffer[idx].data.status;

        if (postJSON(TELEMETRY_PATH, doc)) {
            ringBuffer[idx].occupied = false;
            flushed++;
        } else {
            break;  // Stop flushing on first failure
        }
        delay(100);  // Small delay to avoid overwhelming the backend
    }

    ringCount -= flushed;
    Serial.printf("[NETWORK] Flushed %d readings, %d remaining\n", flushed, ringCount);
}

// ── Reconnection ─────────────────────────────────────────────────────────────

bool handleReconnect() {
    if (WiFi.status() == WL_CONNECTED) {
        reconnectDelay = RECONNECT_MIN_MS;
        return true;
    }

    unsigned long now = millis();
    if (now - lastReconnectAttempt < reconnectDelay) {
        return false;
    }

    lastReconnectAttempt = now;
    Serial.printf("[NETWORK] Attempting WiFi reconnection (backoff: %lums)...\n", reconnectDelay);

    WiFi.reconnect();
    delay(1000);

    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("[NETWORK] Reconnected! IP: %s\n", WiFi.localIP().toString().c_str());
        reconnectDelay = RECONNECT_MIN_MS;
        // Flush buffered telemetry
        flushBufferedTelemetry();
        return true;
    }

    // Exponential backoff
    reconnectDelay = min(reconnectDelay * 2, (unsigned long)RECONNECT_MAX_MS);
    Serial.printf("[NETWORK] Reconnection failed, next attempt in %lums\n", reconnectDelay);
    return false;
}

bool isWiFiConnected() {
    return WiFi.status() == WL_CONNECTED;
}
