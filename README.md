# V2V App (Android)

Production-oriented Android demo for real-time Vehicle-to-Vehicle (V2V) telemetry sharing and on-device trajectory prediction.

## Executive Summary
`V2V App` turns Android phones into cooperative vehicle nodes. Each node publishes local motion telemetry, discovers nearby peers, exchanges state at low latency, and runs local ML inference to predict short-horizon trajectory.

This is designed for demonstrations, prototyping, and field-style validation of multi-device V2V behavior.

## Key Capabilities
- Real-time telemetry loop at `10 Hz` (`100 ms` cadence)
- Peer discovery + connection using Google Nearby Connections (`P2P_CLUSTER`)
- Bi-directional state exchange (JSON payloads)
- On-device TensorFlow Lite inference (`vehicle_trajectory.tflite`)
- Premium multi-page live UI (cosmic/neon theme):
  - `Dashboard` tab: live radar + network telemetry + model summary
  - `Devices` tab: exact connected Nearby endpoint IDs
  - `Coordinates` tab: exact `30` predicted `(x, y)` coordinates

## Direct APK Download
[Download latest APK](https://github.com/aryanipb/DriveSmart/releases/latest/download/app-debug.apk)

For one-click download to work, your latest GitHub Release must include an asset named exactly:
- `app-debug.apk`

## Technology Stack
- Kotlin + Android SDK (minSdk `26`, targetSdk `34`)
- Google Play Services Nearby (`play-services-nearby`)
- Google Play Services Location (`play-services-location`)
- TensorFlow Lite Runtime (`com.google.ai.edge.litert`)

## System Architecture
1. **Telemetry Ingestion**
- GPS (`FusedLocationProviderClient`) for position/speed
- Device orientation (`TYPE_ROTATION_VECTOR`) for heading
- Linear acceleration (`TYPE_LINEAR_ACCELERATION`) for acceleration magnitude

2. **V2V Networking Layer**
- Every device advertises and discovers peers simultaneously
- Connection arbitration avoids dual connection races
- Local state broadcast to connected peers at `10 Hz`
- Peer tracks retained in bounded history buffers (`50` states)

3. **Inference Layer**
- Builds ego/node/edge tensors from recent history
- Runs TFLite model inference on-device
- De-normalizes output to world coordinates for rendering

4. **UI Layer**
- Styled cosmic-metallic experience with animated glow accents
- `Dashboard` page for radar + health counters + output summary
- `Devices` page for exact connected endpoint IDs
- `Coordinates` page for exact 30 live trajectory points

## Runtime Permissions
Required for full operation:
- `ACCESS_FINE_LOCATION`
- `BLUETOOTH_SCAN` (Android 12+)
- `BLUETOOTH_CONNECT` (Android 12+)
- `BLUETOOTH_ADVERTISE` (Android 12+)
- `NEARBY_WIFI_DEVICES` (Android 13+)

If denied, V2V discovery/exchange will not initialize.

## Multi-Device Demo SOP (Standard Operating Procedure)
Use `2+` Android phones (recommended `3-5` for stronger demo impact).

1. Install the APK on all devices.
2. Enable Bluetooth and Location on all devices.
3. Keep devices within close proximity during discovery.
4. Open app on each device and grant all requested permissions.
5. Wait 5-20 seconds for discovery and connection handshake.
6. Validate live network health in status text:
- `found > 0`: peers discovered
- `conn > 0`: active links established
- `tx/rx` counters increasing: telemetry flowing
7. Open `Devices` tab to verify exact connected endpoint IDs.
8. Open `Coordinates` tab to monitor exact 30 predicted points.
9. Move devices and observe live radar + prediction updates.

## Build and Release
### Local debug build
```bash
./gradlew :app:assembleDebug
```

Output APK:
- `app/build/outputs/apk/debug/app-debug.apk`

### GitHub Release flow (for direct download link)
1. Build APK.
2. Create/update a GitHub Release.
3. Upload asset as `app-debug.apk`.
4. Verify download URL:
- `https://github.com/aryanipb/DriveSmart/releases/latest/download/app-debug.apk`

## Operational Guidance
- Keep screens awake during demonstration to reduce background throttling.
- Prefer outdoor/open spaces for better GPS stability.
- Ensure Google Play Services is available/updated on test devices.
- Use consistent app version across all participating devices.
- Swipe across tabs during demo:
  - `Dashboard`: operational command view
  - `Devices`: connectivity validation view
  - `Coordinates`: model precision view

## Troubleshooting
- **No peers found (`found=0`)**:
  - Check Bluetooth/Location enabled on all devices.
  - Re-launch app on all nodes.
  - Bring devices closer.
- **No active connection (`conn=0`)**:
  - Wait for discovery handshake; watch `err` in status.
  - Confirm all runtime permissions are granted.
- **Prediction not updating**:
  - Verify telemetry is changing (move device).
  - Check `rx/tx` counters for data flow.

## Current Scope and Limits
- Optimized for prototype/demo usage, not certified vehicular safety deployment.
- Uses mobile sensors and Nearby transport, so performance depends on device hardware and environment.
- Model quality depends on training/export quality of bundled `vehicle_trajectory.tflite`.

## Repository Structure
- `app/src/main/kotlin/com/aryan/v2v/MainActivity.kt` - app orchestration loops
- `app/src/main/kotlin/com/aryan/v2v/V2VManager.kt` - Nearby networking + peer tracking
- `app/src/main/kotlin/com/aryan/v2v/TelemetryProvider.kt` - sensor + location ingestion
- `app/src/main/kotlin/com/aryan/v2v/TrajectoryPredictor.kt` - model input/output pipeline
- `app/src/main/kotlin/com/aryan/v2v/RadarView.kt` - visualization layer
- `app/src/main/kotlin/com/aryan/v2v/ui/*` - multi-tab premium UI fragments + adapters

## License
Add your project license information here.
