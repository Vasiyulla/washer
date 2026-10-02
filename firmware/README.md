# SmartWash ESP32 Firmware

High-frequency vibration monitoring and telemetry firmware for ESP32 and MPU-6050 / ADXL345 accelerometers.

## Hardware Connections & Pinout

### MPU-6050 (GY-521 Module) — Recommended
| Sensor Pin | ESP32 Pin | Function / Description |
| :--- | :--- | :--- |
| **VCC** | **3V3** *(or 5V/VIN)* | Power Supply (use VIN if module has 662K 3.3V LDO) |
| **GND** | **GND** | Common Ground Reference |
| **SCL** | **GPIO 22** (`D22`) | I2C Serial Clock (100 kHz) |
| **SDA** | **GPIO 21** (`D21`) | I2C Serial Data |
| **AD0** | **GND** | Locks I2C Address to `0x68` |
| **INT / XDA / XCL** | *Not Connected* | Leave floating |

### ADXL345 (Alternative)
| Sensor Pin | ESP32 Pin | Function / Description |
| :--- | :--- | :--- |
| **VCC** | **3V3** | 3.3V Power |
| **GND** | **GND** | Common Ground |
| **CS** | **3V3** | Selects I2C Mode |
| **SCL** | **GPIO 22** (`D22`) | I2C Clock |
| **SDA** | **GPIO 21** (`D21`) | I2C Data |
| **SDO** | **GND** | Address `0x53` |

## Electrical & Signal Integrity Best Practices
1. **Pull-Up Resistors**: If jumper leads exceed 15 cm, place external 4.7 kΩ pull-up resistors from `SDA` and `SCL` to `3.3V`.
2. **Solder Joints**: Header pins **must be soldered** to the sensor PCB. Loose friction-fit breadboard pins cause high-frequency I2C communication failures.
3. **Decoupling**: Solder a 100 nF ceramic capacitor directly across `VCC` and `GND` at the sensor terminals.
4. **RF TX Power**: Firmware limits RF transmission to 17 dBm to avoid power droop and brownout resets when powered via standard USB ports.

## File Structure
```
firmware/
├── platformio.ini         # PlatformIO environment, board settings, baud rate (115200)
├── include/               # Custom headers
└── src/
    ├── config.h           # Central configuration: Wi-Fi, backend URLs, device code & thresholds
    ├── main.cpp           # Setup, boot sequence, FreeRTOS main loop
    ├── vibration.h/.cpp   # Sensor I2C driver & rolling-window vibration analysis
    └── network.h/.cpp     # Wi-Fi station mode, NTP time sync, HTTPS POST & ring buffer
```

## Flashing & Monitoring
```bash
# Compile and flash via PlatformIO CLI
pio run -d firmware -t upload

# Open Serial Monitor (115200 baud)
pio device monitor -d firmware
```
