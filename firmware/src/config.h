/*
 * SmartWash ESP32 Firmware — Configuration
 *
 * All tunable parameters are defined here as named constants.
 * Change these when deploying to a different washing machine.
 *
 * CALIBRATION GUIDE:
 * ──────────────────
 * 1. Mount the ESP32 + MPU6050 firmly on the washing machine body.
 * 2. With the machine OFF, observe the serial monitor vibration readings for
 *    ~30 seconds. Note the baseline vibration deviation (should be ~0.0-0.3).
 *    Set STOP_THRESHOLD slightly above this baseline (e.g., 0.5).
 * 3. Run a normal wash cycle and note the vibration during the wash/spin.
 *    Typical values might be 2.0-8.0 depending on machine and mount point.
 *    Set START_THRESHOLD to a value that's clearly above IDLE noise but below
 *    the lowest vibration during an active cycle (e.g., 2.0).
 * 4. If the machine takes a long time to spin up, increase START_DURATION_SEC.
 * 5. If the machine has brief pauses between wash phases that shouldn't end
 *    the cycle, increase STOP_DURATION_SEC (e.g., 30 seconds).
 */

#ifndef CONFIG_H
#define CONFIG_H

// ═══════════════════════════════════════════════════════════════════════════════
// WiFi Credentials (fallback if WiFiManager portal is not used)
// ═══════════════════════════════════════════════════════════════════════════════
#define WIFI_SSID       "YOUR_SSID"
#define WIFI_PASSWORD   "YOUR_PASSWORD"

// ═══════════════════════════════════════════════════════════════════════════════
// Backend API
// ═══════════════════════════════════════════════════════════════════════════════
#define API_BASE_URL    "https://washer-u2y9.onrender.com"
#define TELEMETRY_PATH  "/api/v1/telemetry"
#define HEARTBEAT_PATH  "/api/v1/devices/" DEVICE_CODE "/heartbeat"

// ═══════════════════════════════════════════════════════════════════════════════
// Device Identity
// ═══════════════════════════════════════════════════════════════════════════════
#define DEVICE_CODE     "ESP32-001"
#define DEVICE_API_KEY  "smartwash-device-secret-key-prod"
#define FIRMWARE_VER    "1.0.0"

// ═══════════════════════════════════════════════════════════════════════════════
// Accelerometer (ADXL345) I2C Pin Mapping
// ═══════════════════════════════════════════════════════════════════════════════
// Default I2C pins on most ESP32 dev boards (SDA=21, SCL=22).
#define ADXL_SDA_PIN    21
#define ADXL_SCL_PIN    22
#define MPU_SDA_PIN     ADXL_SDA_PIN
#define MPU_SCL_PIN     ADXL_SCL_PIN

// ═══════════════════════════════════════════════════════════════════════════════
// Sampling & Reporting
// ═══════════════════════════════════════════════════════════════════════════════
// Internal accelerometer sampling rate (readings/sec). Higher = smoother but
// more CPU. 50 Hz is a good balance for vibration detection.
#define SAMPLE_RATE_HZ      50
#define SAMPLE_INTERVAL_MS  (1000 / SAMPLE_RATE_HZ)

// How often to POST telemetry to the backend (milliseconds).
// Don't set this too low — the backend rate-limits at 60/minute by default.
#define SEND_INTERVAL_MS    3000

// Heartbeat interval when no state change has occurred (milliseconds).
#define HEARTBEAT_INTERVAL_MS 30000

// ═══════════════════════════════════════════════════════════════════════════════
// Vibration Thresholds — CALIBRATE THESE PER MACHINE
// ═══════════════════════════════════════════════════════════════════════════════
// Vibration deviation above this = machine is running.
// "Deviation" = abs(sqrt(x²+y²+z²) - 9.81), i.e., how far from stationary.
#define START_THRESHOLD     2.0f

// Vibration deviation below this for sustained period = cycle ended.
#define STOP_THRESHOLD      0.5f

// How many seconds vibration must stay above START_THRESHOLD to confirm start.
#define START_DURATION_SEC  5

// How many seconds vibration must stay below STOP_THRESHOLD to confirm stop.
#define STOP_DURATION_SEC   15

// ═══════════════════════════════════════════════════════════════════════════════
// Network Resilience
// ═══════════════════════════════════════════════════════════════════════════════
// Ring buffer size for offline telemetry storage (number of readings).
// At 1 reading per 3 seconds, 16 entries = ~48 seconds of buffer.
#define RING_BUFFER_SIZE    16

// WiFi reconnection backoff (milliseconds). Doubles on each failure up to max.
#define RECONNECT_MIN_MS    1000
#define RECONNECT_MAX_MS    30000

// ═══════════════════════════════════════════════════════════════════════════════
// NTP
// ═══════════════════════════════════════════════════════════════════════════════
#define NTP_SERVER_1    "pool.ntp.org"
#define NTP_SERVER_2    "time.google.com"
#define NTP_GMT_OFFSET  0       // UTC
#define NTP_DST_OFFSET  0       // No DST adjustment

#endif // CONFIG_H
