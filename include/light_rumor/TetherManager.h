#pragma once

#include "light_rumor/Common.h"
#include <string>
#include <vector>
#include <memory>
#include <functional>
#include <atomic>
#include <mutex>

namespace apex {

/**
 * High-speed USB-C Direct Tethered Shooting Manager.
 * Supports Sony, Canon, Nikon, Fujifilm, and Panasonic PTP/IP & USB Host API.
 * Automatically transfers RAW images upon shutter release and triggers rapid development preview (< 0.5s).
 */
class TetherManager {
public:
    // Event callback for photo capture and transfer completion
    using TransferCallback = std::function<void(const TetherTransferEvent& event, const std::vector<uint8_t>& rawBytes)>;
    // Callback for real-time camera settings updates (ISO, Aperture, Shutter, Battery)
    using SettingsCallback = std::function<void(const CameraExposureSettings& settings)>;
    // Callback for live-view stream frame ingestion
    using LiveViewCallback = std::function<void(const uint8_t* jpegData, size_t size, int width, int height)>;

    TetherManager();
    ~TetherManager();

    // Start tethering session
    bool startSession(TetherCameraBrand brand, const std::string& deviceId = "USB_CAMERA_0");

    // Terminate tethering session
    void stopSession();

    // Check if camera is tethered and ready
    bool isConnected() const;

    // Current tether status
    TetherState getState() const;

    // Camera Brand
    TetherCameraBrand getBrand() const;

    // Get current exposure parameters & status
    CameraExposureSettings getCameraSettings() const;

    // Remote camera control: adjust ISO, Shutter, Aperture, EV
    bool updateCameraSetting(const std::string& paramName, double value);

    // Remote shutter release trigger
    bool triggerRemoteShutter();

    // Android USB Host API direct file descriptor hook
    bool attachAndroidUsbEndpoints(int fd, int inEndpoint, int outEndpoint, int interruptEndpoint);

    // Register event listeners
    void setTransferCallback(TransferCallback cb);
    void setSettingsCallback(SettingsCallback cb);
    void setLiveViewCallback(LiveViewCallback cb);

    // Set auto-apply development preset parameters
    void setAutoDevelopParams(const DevelopmentParams& params);

    // Inject simulated RAW payload (for automated desktop verification and unit testing)
    bool simulateCameraShutterRelease(const std::string& filename, const std::vector<uint8_t>& rawPayload);

    // Process incoming PTP packet buffer
    bool handlePtpDataPacket(const uint8_t* packetData, size_t length);

private:
    struct Impl;
    std::unique_ptr<Impl> m_impl;
};

} // namespace apex
