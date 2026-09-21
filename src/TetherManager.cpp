#include "apex/TetherManager.h"
#include "apex/RawDecoder.h"
#include "apex/ExportPipeline.h"
#include <chrono>
#include <cstring>
#include <iostream>
#include <thread>
#include <algorithm>

namespace apex {

// PTP (Picture Transfer Protocol) v1.1 USB Container definition
#pragma pack(push, 1)
struct PtpContainerHeader {
    uint32_t length;          // Total container length in bytes
    uint16_t type;            // 1: Command, 2: Data, 3: Response, 4: Event
    uint16_t code;            // Operation/Response/Event code
    uint32_t transactionId;   // Transaction ID
};
#pragma pack(pop)

// PTP Standard Codes
namespace ptp {
    constexpr uint16_t TYPE_COMMAND  = 1;
    constexpr uint16_t TYPE_DATA     = 2;
    constexpr uint16_t TYPE_RESPONSE = 3;
    constexpr uint16_t TYPE_EVENT    = 4;

    constexpr uint16_t OP_GET_DEVICE_INFO   = 0x1001;
    constexpr uint16_t OP_OPEN_SESSION      = 0x1002;
    constexpr uint16_t OP_CLOSE_SESSION     = 0x1003;
    constexpr uint16_t OP_GET_STORAGE_IDS   = 0x1004;
    constexpr uint16_t OP_GET_OBJECT_HANDLES= 0x1007;
    constexpr uint16_t OP_GET_OBJECT_INFO   = 0x1008;
    constexpr uint16_t OP_GET_OBJECT        = 0x1009;
    constexpr uint16_t OP_INITIATE_CAPTURE  = 0x100E;

    constexpr uint16_t RESP_OK              = 0x2001;
    constexpr uint16_t RESP_GENERAL_ERROR   = 0x2002;
    constexpr uint16_t RESP_SESSION_OPEN    = 0x201E;

    constexpr uint16_t EVENT_OBJECT_ADDED   = 0x4002;
    constexpr uint16_t EVENT_DEVICE_PROP_CHG= 0x4006;
    constexpr uint16_t EVENT_CAPTURE_COMPL  = 0x400D;
}

struct TetherManager::Impl {
    std::atomic<TetherState> state{TetherState::Disconnected};
    TetherCameraBrand brand{TetherCameraBrand::Unknown};
    std::string deviceId;
    CameraExposureSettings settings;
    DevelopmentParams autoDevelopParams;

    TransferCallback transferCallback;
    SettingsCallback settingsCallback;
    LiveViewCallback liveViewCallback;

    std::mutex mutex;
    uint32_t currentTransactionId = 1;
    int androidFd = -1;
    int epIn = -1;
    int epOut = -1;
    int epInterrupt = -1;
};

TetherManager::TetherManager() : m_impl(std::make_unique<Impl>()) {}

TetherManager::~TetherManager() {
    stopSession();
}

bool TetherManager::startSession(TetherCameraBrand brand, const std::string& deviceId) {
    std::lock_guard<std::mutex> lock(m_impl->mutex);
    m_impl->state = TetherState::Connecting;
    m_impl->brand = brand;
    m_impl->deviceId = deviceId;

    // Brand-specific baseline profiles
    switch (brand) {
        case TetherCameraBrand::Sony:
            m_impl->settings.cameraModel = "ILCE-7RM5";
            m_impl->settings.lensModel = "FE 24-70mm F2.8 GM II";
            m_impl->settings.shutterSpeed = 1.0 / 250.0;
            m_impl->settings.aperture = 2.8;
            m_impl->settings.iso = 100;
            m_impl->settings.batteryPercent = 94;
            break;
        case TetherCameraBrand::Canon:
            m_impl->settings.cameraModel = "EOS R5 Mark II";
            m_impl->settings.lensModel = "RF 24-70mm F2.8 L IS USM";
            m_impl->settings.shutterSpeed = 1.0 / 320.0;
            m_impl->settings.aperture = 2.8;
            m_impl->settings.iso = 100;
            m_impl->settings.batteryPercent = 88;
            break;
        case TetherCameraBrand::Nikon:
            m_impl->settings.cameraModel = "Z 8";
            m_impl->settings.lensModel = "NIKKOR Z 24-70mm f/2.8 S";
            m_impl->settings.shutterSpeed = 1.0 / 200.0;
            m_impl->settings.aperture = 4.0;
            m_impl->settings.iso = 64;
            m_impl->settings.batteryPercent = 91;
            break;
        case TetherCameraBrand::Fujifilm:
            m_impl->settings.cameraModel = "GFX100 II";
            m_impl->settings.lensModel = "GF 55mm F1.7 R WR";
            m_impl->settings.shutterSpeed = 1.0 / 160.0;
            m_impl->settings.aperture = 1.7;
            m_impl->settings.iso = 80;
            m_impl->settings.batteryPercent = 85;
            break;
        case TetherCameraBrand::Panasonic:
            m_impl->settings.cameraModel = "LUMIX S1R";
            m_impl->settings.lensModel = "Lumix S PRO 24-70mm F2.8";
            m_impl->settings.shutterSpeed = 1.0 / 250.0;
            m_impl->settings.aperture = 2.8;
            m_impl->settings.iso = 100;
            m_impl->settings.batteryPercent = 79;
            break;
        default:
            m_impl->settings.cameraModel = "Generic PTP Camera";
            m_impl->settings.lensModel = "Standard Lens";
            break;
    }

    m_impl->state = TetherState::Connected;
    if (m_impl->settingsCallback) {
        m_impl->settingsCallback(m_impl->settings);
    }

    return true;
}

void TetherManager::stopSession() {
    std::lock_guard<std::mutex> lock(m_impl->mutex);
    if (m_impl->state != TetherState::Disconnected) {
        m_impl->state = TetherState::Disconnected;
    }
}

bool TetherManager::isConnected() const {
    return m_impl->state == TetherState::Connected ||
           m_impl->state == TetherState::Transferring ||
           m_impl->state == TetherState::Processing ||
           m_impl->state == TetherState::Ready;
}

TetherState TetherManager::getState() const {
    return m_impl->state.load();
}

TetherCameraBrand TetherManager::getBrand() const {
    return m_impl->brand;
}

CameraExposureSettings TetherManager::getCameraSettings() const {
    std::lock_guard<std::mutex> lock(m_impl->mutex);
    return m_impl->settings;
}

bool TetherManager::updateCameraSetting(const std::string& paramName, double value) {
    std::lock_guard<std::mutex> lock(m_impl->mutex);
    if (!isConnected()) return false;

    if (paramName == "iso") {
        m_impl->settings.iso = static_cast<uint32_t>(value);
    } else if (paramName == "aperture") {
        m_impl->settings.aperture = value;
    } else if (paramName == "shutterSpeed") {
        m_impl->settings.shutterSpeed = value;
    } else if (paramName == "exposureBias") {
        m_impl->settings.exposureBias = value;
    } else {
        return false;
    }

    if (m_impl->settingsCallback) {
        m_impl->settingsCallback(m_impl->settings);
    }
    return true;
}

bool TetherManager::triggerRemoteShutter() {
    if (!isConnected()) return false;

    // Simulate sending PTP INITIATE_CAPTURE opcode (0x100E)
    std::cout << "[TetherManager] Sent PTP InitiateCapture (0x100E) to " 
              << m_impl->settings.cameraModel << std::endl;
    return true;
}

bool TetherManager::attachAndroidUsbEndpoints(int fd, int inEndpoint, int outEndpoint, int interruptEndpoint) {
    std::lock_guard<std::mutex> lock(m_impl->mutex);
    m_impl->androidFd = fd;
    m_impl->epIn = inEndpoint;
    m_impl->epOut = outEndpoint;
    m_impl->epInterrupt = interruptEndpoint;
    std::cout << "[TetherManager] Android USB Host endpoints attached (fd=" << fd << ")" << std::endl;
    return true;
}

void TetherManager::setTransferCallback(TransferCallback cb) {
    std::lock_guard<std::mutex> lock(m_impl->mutex);
    m_impl->transferCallback = std::move(cb);
}

void TetherManager::setSettingsCallback(SettingsCallback cb) {
    std::lock_guard<std::mutex> lock(m_impl->mutex);
    m_impl->settingsCallback = std::move(cb);
}

void TetherManager::setLiveViewCallback(LiveViewCallback cb) {
    std::lock_guard<std::mutex> lock(m_impl->mutex);
    m_impl->liveViewCallback = std::move(cb);
}

void TetherManager::setAutoDevelopParams(const DevelopmentParams& params) {
    std::lock_guard<std::mutex> lock(m_impl->mutex);
    m_impl->autoDevelopParams = params;
}

bool TetherManager::simulateCameraShutterRelease(const std::string& filename, const std::vector<uint8_t>& rawPayload) {
    if (!isConnected()) {
        std::cerr << "[TetherManager] Cannot release shutter: camera not connected." << std::endl;
        return false;
    }

    m_impl->state = TetherState::Transferring;
    auto tStart = std::chrono::high_resolution_clock::now();

    // 1. Emulate USB-C 10Gbps bulk transfer
    // A 45-60MB RAW file transfers in ~40-60ms over USB 3.2 Gen 2
    size_t payloadSize = rawPayload.size();
    if (payloadSize == 0) {
        payloadSize = 45 * 1024 * 1024; // 45 MB standard uncompressed/lossless RAW
    }

    auto tTransferred = std::chrono::high_resolution_clock::now();
    double transferTimeMs = std::chrono::duration<double, std::milli>(tTransferred - tStart).count();

    // 2. Rapid Handover to Development Engine (< 0.5s turnaround)
    m_impl->state = TetherState::Processing;

    // Fast preview tile synthesis with auto develop preset
    const int previewW = 512;
    const int previewH = 512;
    std::vector<FloatRGBA> dummyTile(previewW * previewH, FloatRGBA(0.45f, 0.45f, 0.48f, 1.0f));
    std::vector<FloatRGBA> outTile;

    ExportPipeline::processTileLinear(dummyTile, previewW, previewH, previewW, previewH, 0,
                                     m_impl->autoDevelopParams, outTile);

    auto tDeveloped = std::chrono::high_resolution_clock::now();
    double developTimeMs = std::chrono::duration<double, std::milli>(tDeveloped - tTransferred).count();

    TetherTransferEvent event;
    event.filename = filename;
    event.fileSize = payloadSize;
    event.transferTimeMs = transferTimeMs;
    event.developTimeMs = developTimeMs;
    event.success = true;
    event.previewId = "PREV_" + filename;

    m_impl->state = TetherState::Ready;

    if (m_impl->transferCallback) {
        m_impl->transferCallback(event, rawPayload);
    }

    return true;
}

bool TetherManager::handlePtpDataPacket(const uint8_t* packetData, size_t length) {
    if (!packetData || length < sizeof(PtpContainerHeader)) return false;

    const auto* header = reinterpret_cast<const PtpContainerHeader*>(packetData);
    if (header->length > length) return false;

    if (header->type == ptp::TYPE_EVENT) {
        if (header->code == ptp::EVENT_OBJECT_ADDED) {
            std::cout << "[TetherManager] Received PTP Event: ObjectAdded (0x4002)" << std::endl;
            // Trigger transfer
            std::vector<uint8_t> dummyRaw(1024 * 1024, 0x5A);
            return simulateCameraShutterRelease("DSC09420.ARW", dummyRaw);
        }
        else if (header->code == ptp::EVENT_DEVICE_PROP_CHG) {
            std::cout << "[TetherManager] Received PTP Event: DevicePropChanged" << std::endl;
            if (m_impl->settingsCallback) {
                m_impl->settingsCallback(m_impl->settings);
            }
        }
    }
    return true;
}

} // namespace apex
