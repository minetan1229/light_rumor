#include "light_rumor/Common.h"
#include "light_rumor/RawDecoder.h"
#include "light_rumor/ExportPipeline.h"
#include "light_rumor/WaveformEngine.h"
#include "light_rumor/MaskEngine.h"
#include "light_rumor/FieldScopesEngine.h"
#include "light_rumor/ColorCheckerCalibration.h"
#include "light_rumor/SoftProofingEngine.h"
#include "light_rumor/TetherManager.h"
#include "light_rumor/FocusStacker.h"
#include "light_rumor/AstroAligner.h"
#include "light_rumor/ImageWriter.h"
#include <iostream>
#include <string>
#include <vector>
#include <algorithm>

#if defined(__ANDROID__) || defined(LIGHT_RUMOR_ENABLE_JNI)
#include <jni.h>

namespace {

struct JniStringUtfGuard {
    JNIEnv* env = nullptr;
    jstring jstr = nullptr;
    const char* chars = nullptr;

    JniStringUtfGuard(JNIEnv* e, jstring s) : env(e), jstr(s) {
        if (env && jstr) {
            chars = env->GetStringUTFChars(jstr, nullptr);
        }
    }
    ~JniStringUtfGuard() {
        release();
    }
    void release() {
        if (env && jstr && chars) {
            env->ReleaseStringUTFChars(jstr, chars);
            chars = nullptr;
        }
    }
    const char* c_str() const { return chars; }
    explicit operator bool() const { return chars != nullptr; }

    JniStringUtfGuard(const JniStringUtfGuard&) = delete;
    JniStringUtfGuard& operator=(const JniStringUtfGuard&) = delete;
    JniStringUtfGuard(JniStringUtfGuard&& other) noexcept : env(other.env), jstr(other.jstr), chars(other.chars) {
        other.chars = nullptr;
        other.jstr = nullptr;
        other.env = nullptr;
    }
    JniStringUtfGuard& operator=(JniStringUtfGuard&& other) noexcept {
        if (this != &other) {
            release();
            env = other.env;
            jstr = other.jstr;
            chars = other.chars;
            other.chars = nullptr;
            other.jstr = nullptr;
            other.env = nullptr;
        }
        return *this;
    }
};

struct JniGlobalRefGuard {
    JNIEnv* env = nullptr;
    jobject ref = nullptr;

    JniGlobalRefGuard(JNIEnv* e = nullptr, jobject r = nullptr) : env(e) {
        if (env && r) {
            ref = env->NewGlobalRef(r);
        }
    }
    ~JniGlobalRefGuard() {
        reset();
    }
    void reset(jobject newRef = nullptr) {
        if (env && ref) {
            env->DeleteGlobalRef(ref);
        }
        ref = newRef;
    }
    jobject get() const { return ref; }
    explicit operator bool() const { return ref != nullptr; }

    JniGlobalRefGuard(const JniGlobalRefGuard&) = delete;
    JniGlobalRefGuard& operator=(const JniGlobalRefGuard&) = delete;
    JniGlobalRefGuard(JniGlobalRefGuard&& other) noexcept : env(other.env), ref(other.ref) {
        other.ref = nullptr;
        other.env = nullptr;
    }
    JniGlobalRefGuard& operator=(JniGlobalRefGuard&& other) noexcept {
        if (this != &other) {
            reset();
            env = other.env;
            ref = other.ref;
            other.ref = nullptr;
            other.env = nullptr;
        }
        return *this;
    }
};

template <typename JArrayType, typename JElementType>
struct JniArrayGuard {
    JNIEnv* env = nullptr;
    JArrayType jarray = nullptr;
    JElementType* elements = nullptr;
    jint mode = JNI_ABORT;

    JniArrayGuard(JNIEnv* e, JArrayType a, jint m = JNI_ABORT) : env(e), jarray(a), mode(m) {
        if (env && jarray) {
            init();
        }
    }
    ~JniArrayGuard() {
        release();
    }
    void init();
    void release();
    
    JElementType* get() const { return elements; }
    explicit operator bool() const { return elements != nullptr; }

    JniArrayGuard(const JniArrayGuard&) = delete;
    JniArrayGuard& operator=(const JniArrayGuard&) = delete;
    JniArrayGuard(JniArrayGuard&& other) noexcept : env(other.env), jarray(other.jarray), elements(other.elements), mode(other.mode) {
        other.elements = nullptr;
        other.jarray = nullptr;
        other.env = nullptr;
    }
    JniArrayGuard& operator=(JniArrayGuard&& other) noexcept {
        if (this != &other) {
            release();
            env = other.env;
            jarray = other.jarray;
            elements = other.elements;
            mode = other.mode;
            other.elements = nullptr;
            other.jarray = nullptr;
            other.env = nullptr;
        }
        return *this;
    }
};

template<> void JniArrayGuard<jbyteArray, jbyte>::init() { elements = env->GetByteArrayElements(jarray, nullptr); }
template<> void JniArrayGuard<jbyteArray, jbyte>::release() { if (env && jarray && elements) { env->ReleaseByteArrayElements(jarray, elements, mode); elements = nullptr; } }

template<> void JniArrayGuard<jintArray, jint>::init() { elements = env->GetIntArrayElements(jarray, nullptr); }
template<> void JniArrayGuard<jintArray, jint>::release() { if (env && jarray && elements) { env->ReleaseIntArrayElements(jarray, elements, mode); elements = nullptr; } }

template<> void JniArrayGuard<jfloatArray, jfloat>::init() { elements = env->GetFloatArrayElements(jarray, nullptr); }
template<> void JniArrayGuard<jfloatArray, jfloat>::release() { if (env && jarray && elements) { env->ReleaseFloatArrayElements(jarray, elements, mode); elements = nullptr; } }

jboolean Impl_nativeInit(JNIEnv* /*env*/, jobject /*thiz*/) {
    try {
        std::cout << "[JNI] LightRumorNativeEngine initialized." << std::endl;
        return JNI_TRUE;
    } catch (const std::exception& e) {
        std::cerr << "[JNI] nativeInit exception: " << e.what() << std::endl;
        return JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

jboolean Impl_nativeProcessRaw(
    JNIEnv* env, jobject /*thiz*/,
    jstring jInputPath,
    jstring jOutputPath,
    jint jFormat,
    jint jJpegQuality,
    jint jChromaSubsampling,
    jfloat jKelvin,
    jfloat jTint,
    jfloat jShadowTintR,
    jfloat jShadowTintG,
    jfloat jShadowTintB,
    jfloat jExposureEV,
    jfloat jContrast,
    jfloat jHighlights,
    jfloat jShadows,
    jfloat jWhites,
    jfloat jBlacks,
    jfloat jVibrance,
    jfloat jSaturation,
    jfloat jDehaze,
    jfloat jClarity,
    jfloat jTexture,
    jboolean jIsMonochrome,
    jfloatArray jMonochromeWeights,
    jfloatArray jHslBands,
    jfloatArray jPrimaryCalibration,
    jfloatArray jSplitToning,
    jfloatArray jToneCurveLUT,
    jfloat jLuminanceNR,
    jfloat jLuminanceNRDetail,
    jfloat jLuminanceNRContrast,
    jfloat jChromaNR,
    jfloat jChromaNRDetail,
    jfloat jChromaNRSmoothness,
    jfloat jSharpeningAmount,
    jfloat jSharpeningRadius,
    jfloat jSharpeningDetail,
    jfloat jSharpeningMasking,
    jint jOutputColorSpace,
    jboolean jEnableDithering,
    jboolean jEnableLensCorrection,
    jfloat jDistortionCorrection,
    jfloat jVignettingCorrection,
    jfloat jChromaticAberration,
    jfloat jDefringePurple,
    jfloat jDefringeGreen,
    jfloat jCropX,
    jfloat jCropY,
    jfloat jCropW,
    jfloat jCropH,
    jfloat jRotationDegrees,
    jint jRotationSteps,
    jboolean jFlipHorizontal,
    jboolean jFlipVertical,
    jfloat jPerspectiveVertical,
    jfloat jPerspectiveHorizontal,
    jfloat jDistortion,
    jint jMaxLongEdge,
    jboolean jEnableWatermark,
    jstring jWatermarkText,
    jobject jCallback) {

    try {
        if (!jInputPath || !jOutputPath) return JNI_FALSE;

        JniStringUtfGuard inputChars(env, jInputPath);
        JniStringUtfGuard outputChars(env, jOutputPath);
        if (!inputChars || !outputChars) {
            return JNI_FALSE;
        }

        std::string inputPath(inputChars.c_str());
        std::string outputPath(outputChars.c_str());
        inputChars.release();
        outputChars.release();

        light_rumor::RawDecoder decoder;
        if (!decoder.openFile(inputPath)) {
            std::cerr << "[JNI] Failed to open RAW: " << inputPath << std::endl;
            return JNI_FALSE;
        }

        light_rumor::DevelopmentParams params;
        params.kelvin = jKelvin;
        params.tint = jTint;
        params.shadowTintR = jShadowTintR;
        params.shadowTintG = jShadowTintG;
        params.shadowTintB = jShadowTintB;
        params.exposureEV = jExposureEV;
        params.contrast = jContrast;
        params.highlights = jHighlights;
        params.shadows = jShadows;
        params.whites = jWhites;
        params.blacks = jBlacks;
        params.vibrance = jVibrance;
        params.saturation = jSaturation;
        params.dehaze = jDehaze;
        params.clarity = jClarity;
        params.texture = jTexture;
        params.isMonochrome = (jIsMonochrome == JNI_TRUE);

        if (jMonochromeWeights) {
            jsize len = env->GetArrayLength(jMonochromeWeights);
            if (len >= 8) {
                JniArrayGuard<jfloatArray, jfloat> mwGuard(env, jMonochromeWeights);
                if (mwGuard) {
                    const jfloat* data = mwGuard.get();
                    for (size_t i = 0; i < 8; ++i) {
                        params.monochromeWeights[i] = data[i];
                    }
                }
            }
        }

        if (jHslBands) {
            jsize len = env->GetArrayLength(jHslBands);
            if (len >= 24) {
                JniArrayGuard<jfloatArray, jfloat> hslGuard(env, jHslBands);
                if (hslGuard) {
                    const jfloat* data = hslGuard.get();
                    for (size_t i = 0; i < 8; ++i) {
                        params.hslBands[i].hueShift = data[i * 3 + 0];
                        params.hslBands[i].saturation = data[i * 3 + 1];
                        params.hslBands[i].luminance = data[i * 3 + 2];
                    }
                }
            }
        }

        if (jPrimaryCalibration) {
            jsize len = env->GetArrayLength(jPrimaryCalibration);
            if (len >= 6) {
                JniArrayGuard<jfloatArray, jfloat> pcGuard(env, jPrimaryCalibration);
                if (pcGuard) {
                    const jfloat* data = pcGuard.get();
                    params.primaryRed.hueShift = data[0];
                    params.primaryRed.saturationShift = data[1];
                    params.primaryGreen.hueShift = data[2];
                    params.primaryGreen.saturationShift = data[3];
                    params.primaryBlue.hueShift = data[4];
                    params.primaryBlue.saturationShift = data[5];
                }
            }
        }

        if (jSplitToning) {
            jsize len = env->GetArrayLength(jSplitToning);
            if (len >= 5) {
                JniArrayGuard<jfloatArray, jfloat> stGuard(env, jSplitToning);
                if (stGuard) {
                    const jfloat* data = stGuard.get();
                    params.splitToning.highlightsHue = data[0];
                    params.splitToning.highlightsSat = data[1];
                    params.splitToning.shadowsHue = data[2];
                    params.splitToning.shadowsSat = data[3];
                    params.splitToning.balance = data[4];
                }
            }
        }

        if (jToneCurveLUT) {
            jsize len = env->GetArrayLength(jToneCurveLUT);
            if (len > 0) {
                JniArrayGuard<jfloatArray, jfloat> tcGuard(env, jToneCurveLUT);
                if (tcGuard) {
                    const jfloat* data = tcGuard.get();
                    params.toneCurveLUT.assign(data, data + len);
                }
            }
        }

        params.luminanceNR = jLuminanceNR;
        params.luminanceNRDetail = jLuminanceNRDetail;
        params.luminanceNRContrast = jLuminanceNRContrast;
        params.chromaNR = jChromaNR;
        params.chromaNRDetail = jChromaNRDetail;
        params.chromaNRSmoothness = jChromaNRSmoothness;
        params.sharpeningAmount = jSharpeningAmount;
        params.sharpeningRadius = jSharpeningRadius;
        params.sharpeningDetail = jSharpeningDetail;
        params.sharpeningMasking = jSharpeningMasking;
        params.outputColorSpace = static_cast<light_rumor::ColorSpace>(jOutputColorSpace);
        params.enableDithering = (jEnableDithering == JNI_TRUE);

        params.lensCorrection.enableProfileCorrection = (jEnableLensCorrection == JNI_TRUE);
        params.lensCorrection.distortionCorrection = jDistortionCorrection;
        params.lensCorrection.vignettingCorrection = jVignettingCorrection;
        params.lensCorrection.chromaticAberration = jChromaticAberration;
        params.lensCorrection.defringePurple = jDefringePurple;
        params.lensCorrection.defringeGreen = jDefringeGreen;

        params.geometry.cropX = jCropX;
        params.geometry.cropY = jCropY;
        params.geometry.cropW = jCropW;
        params.geometry.cropH = jCropH;
        params.geometry.rotationDegrees = jRotationDegrees;
        params.geometry.rotationSteps = jRotationSteps;
        params.geometry.flipHorizontal = (jFlipHorizontal == JNI_TRUE);
        params.geometry.flipVertical = (jFlipVertical == JNI_TRUE);
        params.geometry.perspectiveVertical = jPerspectiveVertical;
        params.geometry.perspectiveHorizontal = jPerspectiveHorizontal;
        params.geometry.distortion = jDistortion;

        light_rumor::ExportOptions options;
        options.format = static_cast<light_rumor::ExportFormat>(jFormat);
        options.jpegQuality = jJpegQuality;
        options.chromaSubsampling = static_cast<light_rumor::ChromaSubsampling>(jChromaSubsampling);
        options.embedExif = true;
        options.tileSize = 2048;
        options.tilePadding = 16;
        options.maxLongEdge = jMaxLongEdge;
        options.enableWatermark = (jEnableWatermark == JNI_TRUE);
        if (jWatermarkText) {
            JniStringUtfGuard watermarkChars(env, jWatermarkText);
            if (watermarkChars) {
                options.watermarkText = watermarkChars.c_str();
            }
        }

        jclass callbackClass = nullptr;
        jmethodID onProgressMethod = nullptr;
        JniGlobalRefGuard globalCallback(env, nullptr);
        JavaVM* jvm = nullptr;

        if (jCallback) {
            env->GetJavaVM(&jvm);
            callbackClass = env->GetObjectClass(jCallback);
            if (callbackClass) {
                onProgressMethod = env->GetMethodID(callbackClass, "onProgress", "(FLjava/lang/String;)V");
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    onProgressMethod = nullptr;
                }
                env->DeleteLocalRef(callbackClass);
            }
            if (onProgressMethod && jvm) {
                globalCallback = JniGlobalRefGuard(env, jCallback);
            }
        }

        light_rumor::ProgressCallback progressCb = nullptr;
        if (globalCallback && onProgressMethod && jvm) {
            jobject cbRef = globalCallback.get();
            progressCb = [jvm, cbRef, onProgressMethod](float pct, const std::string& status) {
                JNIEnv* curEnv = nullptr;
                bool didAttach = false;
                jint envRes = jvm->GetEnv(reinterpret_cast<void**>(&curEnv), JNI_VERSION_1_6);
                if (envRes == JNI_EDETACHED) {
#if defined(__ANDROID__)
                    if (jvm->AttachCurrentThread(&curEnv, nullptr) == JNI_OK) {
                        didAttach = true;
                    }
#else
                    if (jvm->AttachCurrentThread(reinterpret_cast<void**>(&curEnv), nullptr) == JNI_OK) {
                        didAttach = true;
                    }
#endif
                }
                if (curEnv) {
                    jstring jStatus = curEnv->NewStringUTF(status.c_str());
                    if (jStatus) {
                        curEnv->CallVoidMethod(cbRef, onProgressMethod, pct, jStatus);
                        if (curEnv->ExceptionCheck()) {
                            curEnv->ExceptionClear();
                        }
                        curEnv->DeleteLocalRef(jStatus);
                    }
                }
                if (didAttach) {
                    jvm->DetachCurrentThread();
                }
            };
        }

        light_rumor::ExportPipeline pipeline;
        bool success = pipeline.processImage(decoder, params, options, outputPath, progressCb);
        return success ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        std::cerr << "[JNI] nativeProcessRaw exception: " << e.what() << std::endl;
        return JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

jbyteArray Impl_nativeExtractThumbnail(JNIEnv* env, jobject /*thiz*/, jstring jFilePath) {
    try {
        if (!jFilePath) return nullptr;
        JniStringUtfGuard pathChars(env, jFilePath);
        if (!pathChars) return nullptr;
        std::string filePath(pathChars.c_str());
        pathChars.release();

        std::vector<uint8_t> jpegBytes;
        int32_t width = 0, height = 0;
        bool success = light_rumor::RawDecoder::extractEmbeddedThumbnail(filePath, jpegBytes, width, height);
        if (!success || jpegBytes.empty()) {
            return nullptr;
        }

        jbyteArray result = env->NewByteArray(static_cast<jsize>(jpegBytes.size()));
        if (!result) return nullptr;
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(jpegBytes.size()),
                                reinterpret_cast<const jbyte*>(jpegBytes.data()));
        return result;
    } catch (const std::exception& e) {
        std::cerr << "[JNI] nativeExtractThumbnail exception: " << e.what() << std::endl;
        return nullptr;
    } catch (...) {
        return nullptr;
    }
}

jintArray Impl_nativeComputeWaveform(
    JNIEnv* env, jobject /*thiz*/,
    jbyteArray jRgbaBytes,
    jint jWidth, jint jHeight,
    jint jMode,
    jint jWaveW, jint jWaveH) {
    try {
        if (!jRgbaBytes || jWidth <= 0 || jHeight <= 0 || jWaveW <= 0 || jWaveH <= 0) {
            return nullptr;
        }

        jsize len = env->GetArrayLength(jRgbaBytes);
        int64_t requiredBytes = static_cast<int64_t>(jWidth) * static_cast<int64_t>(jHeight) * 4;
        if (static_cast<int64_t>(len) < requiredBytes) return nullptr;

        JniArrayGuard<jbyteArray, jbyte> bytesGuard(env, jRgbaBytes, JNI_ABORT);
        if (!bytesGuard) return nullptr;
        jbyte* bytes = bytesGuard.get();

        light_rumor::WaveformEngine engine;
        light_rumor::WaveformData outData;
        engine.computeFromRGBA8(
            reinterpret_cast<const uint8_t*>(bytes),
            jWidth, jHeight,
            static_cast<light_rumor::WaveformMode>(jMode),
            jWaveW, jWaveH,
            outData
        );

        if (outData.rgbaPixels.empty()) return nullptr;

        jintArray result = env->NewIntArray(static_cast<jsize>(outData.rgbaPixels.size()));
        if (!result) return nullptr;

        env->SetIntArrayRegion(result, 0, static_cast<jsize>(outData.rgbaPixels.size()),
                               reinterpret_cast<const jint*>(outData.rgbaPixels.data()));
        return result;
    } catch (const std::exception& e) {
        std::cerr << "[JNI] nativeComputeWaveform exception: " << e.what() << std::endl;
        return nullptr;
    } catch (...) {
        return nullptr;
    }
}

jfloatArray Impl_nativeEvaluateRadialMask(
    JNIEnv* env, jobject /*thiz*/,
    jint jWidth, jint jHeight,
    jfloat jCenterX, jfloat jCenterY,
    jfloat jRadiusX, jfloat jRadiusY,
    jfloat jAngleRad, jfloat jFeather,
    jboolean jInvert) {
    try {
        if (jWidth <= 0 || jHeight <= 0) return nullptr;

        light_rumor::MaskLayer layer;
        layer.type = light_rumor::MaskType::RadialGradient;
        layer.radialCenter = light_rumor::Point2D(jCenterX, jCenterY);
        layer.radialRadiusX = jRadiusX;
        layer.radialRadiusY = jRadiusY;
        layer.radialAngle = jAngleRad;
        layer.radialFeather = jFeather;
        layer.inverted = (jInvert == JNI_TRUE);

        std::vector<light_rumor::FloatRGBA> emptyPixels;
        std::vector<float> outMask;
        light_rumor::MaskEngine::evaluateSingleMask(layer, emptyPixels, jWidth, jHeight, {}, outMask);

        jfloatArray result = env->NewFloatArray(static_cast<jsize>(outMask.size()));
        if (!result) return nullptr;
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(outMask.size()), outMask.data());
        return result;
    } catch (const std::exception& e) {
        std::cerr << "[JNI] nativeEvaluateRadialMask exception: " << e.what() << std::endl;
        return nullptr;
    } catch (...) {
        return nullptr;
    }
}

jintArray Impl_nativeApplyPoissonHeal(
    JNIEnv* env, jobject /*thiz*/,
    jintArray jPixels,
    jint jWidth, jint jHeight,
    jfloat jSrcX, jfloat jSrcY,
    jfloat jDstX, jfloat jDstY,
    jfloat jRadius, jfloat jFeather,
    jint jIterations) {
    try {
        if (!jPixels || jWidth <= 0 || jHeight <= 0) return nullptr;

        jsize len = env->GetArrayLength(jPixels);
        int64_t requiredPixels = static_cast<int64_t>(jWidth) * static_cast<int64_t>(jHeight);
        if (static_cast<int64_t>(len) < requiredPixels) return nullptr;

        JniArrayGuard<jintArray, jint> pixelsGuard(env, jPixels, JNI_ABORT);
        if (!pixelsGuard) return nullptr;
        jint* pData = pixelsGuard.get();

        std::vector<light_rumor::FloatRGBA> fPixels(static_cast<size_t>(jWidth) * jHeight);
        for (size_t i = 0; i < fPixels.size(); ++i) {
            uint32_t c = static_cast<uint32_t>(pData[i]);
            float a = ((c >> 24) & 0xFF) / 255.0f;
            float r = ((c >> 16) & 0xFF) / 255.0f;
            float g = ((c >> 8) & 0xFF) / 255.0f;
            float b = (c & 0xFF) / 255.0f;
            fPixels[i] = light_rumor::FloatRGBA(r, g, b, a);
        }

        light_rumor::RetouchOperation op;
        op.isHeal = true;
        op.sourcePos = light_rumor::Point2D(jSrcX, jSrcY);
        op.targetPos = light_rumor::Point2D(jDstX, jDstY);
        op.radius = jRadius;
        op.feather = jFeather;
        op.opacity = 1.0f;

        jIterations = std::clamp(jIterations, 1, 100);
        light_rumor::MaskEngine::applyPoissonHeal(fPixels, jWidth, jHeight, op, jIterations);

        std::vector<jint> outInts(fPixels.size());
        for (size_t i = 0; i < fPixels.size(); ++i) {
            const auto& p = fPixels[i];
            uint32_t a = static_cast<uint32_t>(std::clamp(p.a * 255.0f, 0.0f, 255.0f));
            uint32_t r = static_cast<uint32_t>(std::clamp(p.r * 255.0f, 0.0f, 255.0f));
            uint32_t g = static_cast<uint32_t>(std::clamp(p.g * 255.0f, 0.0f, 255.0f));
            uint32_t b = static_cast<uint32_t>(std::clamp(p.b * 255.0f, 0.0f, 255.0f));
            outInts[i] = static_cast<jint>((a << 24) | (r << 16) | (g << 8) | b);
        }

        jintArray result = env->NewIntArray(static_cast<jsize>(outInts.size()));
        if (!result) return nullptr;
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(outInts.size()), outInts.data());
        return result;
    } catch (const std::exception& e) {
        std::cerr << "[JNI] nativeApplyPoissonHeal exception: " << e.what() << std::endl;
        return nullptr;
    } catch (...) {
        return nullptr;
    }
}

jintArray Impl_nativeComputeFieldScope(
    JNIEnv* env, jobject /*thiz*/,
    jintArray jPixels,
    jint jWidth, jint jHeight,
    jint jMode,
    jfloat jZebraThresholdIRE,
    jint jPeakingColor,
    jfloat jPeakingThreshold) {
    try {
        if (!jPixels || jWidth <= 0 || jHeight <= 0) return nullptr;

        jsize len = env->GetArrayLength(jPixels);
        int64_t requiredPixels = static_cast<int64_t>(jWidth) * static_cast<int64_t>(jHeight);
        if (static_cast<int64_t>(len) < requiredPixels) return nullptr;

        JniArrayGuard<jintArray, jint> pixelsGuard(env, jPixels, JNI_ABORT);
        if (!pixelsGuard) return nullptr;
        jint* pData = pixelsGuard.get();

        size_t total = static_cast<size_t>(jWidth) * jHeight;
        std::vector<light_rumor::FloatRGBA> fPixels(total);
        for (size_t i = 0; i < total; ++i) {
            uint32_t c = static_cast<uint32_t>(pData[i]);
            float a = ((c >> 24) & 0xFF) / 255.0f;
            float r = ((c >> 16) & 0xFF) / 255.0f;
            float g = ((c >> 8) & 0xFF) / 255.0f;
            float b = (c & 0xFF) / 255.0f;
            fPixels[i] = light_rumor::FloatRGBA(r, g, b, a);
        }

        std::vector<light_rumor::FloatRGBA> outPixels;
        if (jMode == 1) { // False Color
            light_rumor::FieldScopesEngine::generateFalseColor(fPixels.data(), jWidth, jHeight, outPixels);
        } else if (jMode == 2) { // Zebra
            light_rumor::FieldScopesEngine::generateZebra(fPixels.data(), jWidth, jHeight, jZebraThresholdIRE, 0.0f, outPixels);
        } else if (jMode == 3) { // Peaking
            light_rumor::FieldScopesEngine::generateFocusPeaking(fPixels.data(), jWidth, jHeight,
                                                                 static_cast<light_rumor::PeakingColor>(jPeakingColor),
                                                                 jPeakingThreshold, outPixels);
        } else {
            outPixels = std::move(fPixels);
        }

        std::vector<jint> outInts(total);
        for (size_t i = 0; i < total; ++i) {
            const auto& p = outPixels[i];
            uint32_t a = static_cast<uint32_t>(std::clamp(p.a * 255.0f, 0.0f, 255.0f));
            uint32_t r = static_cast<uint32_t>(std::clamp(p.r * 255.0f, 0.0f, 255.0f));
            uint32_t g = static_cast<uint32_t>(std::clamp(p.g * 255.0f, 0.0f, 255.0f));
            uint32_t b = static_cast<uint32_t>(std::clamp(p.b * 255.0f, 0.0f, 255.0f));
            outInts[i] = static_cast<jint>((a << 24) | (r << 16) | (g << 8) | b);
        }

        jintArray result = env->NewIntArray(static_cast<jsize>(total));
        if (!result) return nullptr;
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(total), outInts.data());
        return result;
    } catch (const std::exception& e) {
        std::cerr << "[JNI] nativeComputeFieldScope exception: " << e.what() << std::endl;
        return nullptr;
    } catch (...) {
        return nullptr;
    }
}

jintArray Impl_nativeApplySoftProof(
    JNIEnv* env, jobject /*thiz*/,
    jintArray jPixels,
    jint jWidth, jint jHeight,
    jstring jProfilePath,
    jint jIntent,
    jboolean jSimulatePaperWhite,
    jboolean jSimulateBlackInk,
    jboolean jShowGamutWarning,
    jint jGamutWarningColor) {
    try {
        if (!jPixels || jWidth <= 0 || jHeight <= 0) return nullptr;

        jsize len = env->GetArrayLength(jPixels);
        int64_t requiredPixels = static_cast<int64_t>(jWidth) * static_cast<int64_t>(jHeight);
        if (static_cast<int64_t>(len) < requiredPixels) return nullptr;

        std::string profilePath;
        if (jProfilePath) {
            JniStringUtfGuard pChars(env, jProfilePath);
            if (pChars) {
                profilePath = pChars.c_str();
            }
        }

        JniArrayGuard<jintArray, jint> pixelsGuard(env, jPixels, JNI_ABORT);
        if (!pixelsGuard) return nullptr;
        jint* pData = pixelsGuard.get();

        size_t total = static_cast<size_t>(jWidth) * jHeight;
        std::vector<light_rumor::FloatRGBA> fPixels(total);
        for (size_t i = 0; i < total; ++i) {
            uint32_t c = static_cast<uint32_t>(pData[i]);
            float a = ((c >> 24) & 0xFF) / 255.0f;
            float r = ((c >> 16) & 0xFF) / 255.0f;
            float g = ((c >> 8) & 0xFF) / 255.0f;
            float b = (c & 0xFF) / 255.0f;
            fPixels[i] = light_rumor::FloatRGBA(r, g, b, a);
        }

        light_rumor::SoftProofConfig config;
        config.paperProfilePath = profilePath;
        config.intent = static_cast<light_rumor::ProofingIntent>(jIntent);
        config.simulatePaperWhite = (jSimulatePaperWhite == JNI_TRUE);
        config.simulateBlackInk = (jSimulateBlackInk == JNI_TRUE);
        config.showGamutWarning = (jShowGamutWarning == JNI_TRUE);
        config.gamutWarningColor = static_cast<uint32_t>(jGamutWarningColor);

        std::vector<light_rumor::FloatRGBA> outProof;
        std::vector<float> outMask;
        light_rumor::SoftProofingEngine::applySoftProof(fPixels, jWidth, jHeight, config, outProof, outMask);

        std::vector<jint> outInts(total);
        for (size_t i = 0; i < total; ++i) {
            const auto& p = outProof[i];
            uint32_t a = static_cast<uint32_t>(std::clamp(p.a * 255.0f, 0.0f, 255.0f));
            uint32_t r = static_cast<uint32_t>(std::clamp(p.r * 255.0f, 0.0f, 255.0f));
            uint32_t g = static_cast<uint32_t>(std::clamp(p.g * 255.0f, 0.0f, 255.0f));
            uint32_t b = static_cast<uint32_t>(std::clamp(p.b * 255.0f, 0.0f, 255.0f));
            outInts[i] = static_cast<jint>((a << 24) | (r << 16) | (g << 8) | b);
        }

        jintArray result = env->NewIntArray(static_cast<jsize>(total));
        if (!result) return nullptr;
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(total), outInts.data());
        return result;
    } catch (const std::exception& e) {
        std::cerr << "[JNI] nativeApplySoftProof exception: " << e.what() << std::endl;
        return nullptr;
    } catch (...) {
        return nullptr;
    }
}

jboolean Impl_nativeProcessRawMultiRecipe(
    JNIEnv* env, jobject /*thiz*/,
    jstring jInputPath,
    jstring jOutputPath,
    jint jFormat,
    jint jColorSpace,
    jint jQuality,
    jint jMaxDimension,
    jboolean jApplySharpening,
    jfloat jSharpeningAmount,
    jboolean jEnableWatermark,
    jstring jWatermarkText) {
    try {
        if (!jInputPath || !jOutputPath) return JNI_FALSE;

        JniStringUtfGuard inChars(env, jInputPath);
        if (!inChars) return JNI_FALSE;

        JniStringUtfGuard outChars(env, jOutputPath);
        if (!outChars) return JNI_FALSE;

        JniStringUtfGuard wmChars(env, jWatermarkText);

        std::string inputPath = inChars.c_str();
        std::string outputPath = outChars.c_str();
        std::string watermarkText = wmChars ? wmChars.c_str() : "";

        inChars.release();
        outChars.release();
        wmChars.release();

        light_rumor::RawDecoder decoder;
        if (!decoder.openFile(inputPath)) return JNI_FALSE;

        light_rumor::DevelopmentParams params;
        params.outputColorSpace = static_cast<light_rumor::ColorSpace>(jColorSpace);
        if (jApplySharpening == JNI_TRUE) {
            params.sharpeningAmount = jSharpeningAmount;
        }

        light_rumor::ExportOptions options;
        options.format = static_cast<light_rumor::ExportFormat>(jFormat);
        options.jpegQuality = jQuality;
        options.chromaSubsampling = light_rumor::ChromaSubsampling::YUV420;
        options.embedExif = true;
        options.tileSize = 2048;
        options.tilePadding = 16;
        options.maxLongEdge = jMaxDimension;
        options.enableWatermark = (jEnableWatermark == JNI_TRUE);
        options.watermarkText = watermarkText;

        light_rumor::ExportPipeline pipeline;
        return pipeline.processImage(decoder, params, options, outputPath) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        std::cerr << "[JNI] nativeProcessRawMultiRecipe exception: " << e.what() << std::endl;
        return JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

} // namespace

extern "C" {

// =========================================================================
// LightRumor Native Engine Bindings (Package: com.lightrumor)
// =========================================================================

JNIEXPORT jboolean JNICALL
Java_com_lightrumor_LightRumorNativeEngine_nativeInit(JNIEnv* env, jobject thiz) {
    return Impl_nativeInit(env, thiz);
}

JNIEXPORT jboolean JNICALL
Java_com_lightrumor_LightRumorNativeEngine_nativeProcessRaw(
    JNIEnv* env, jobject thiz,
    jstring jInputPath, jstring jOutputPath, jint jFormat, jint jJpegQuality,
    jint jChromaSubsampling, jfloat jKelvin, jfloat jTint,
    jfloat jShadowTintR, jfloat jShadowTintG, jfloat jShadowTintB,
    jfloat jExposureEV, jfloat jContrast, jfloat jHighlights, jfloat jShadows,
    jfloat jWhites, jfloat jBlacks, jfloat jVibrance, jfloat jSaturation,
    jfloat jDehaze, jfloat jClarity, jfloat jTexture,
    jboolean jIsMonochrome, jfloatArray jMonochromeWeights,
    jfloatArray jHslBands, jfloatArray jPrimaryCalibration,
    jfloatArray jSplitToning, jfloatArray jToneCurveLUT,
    jfloat jLuminanceNR, jfloat jLuminanceNRDetail, jfloat jLuminanceNRContrast,
    jfloat jChromaNR, jfloat jChromaNRDetail, jfloat jChromaNRSmoothness,
    jfloat jSharpeningAmount, jfloat jSharpeningRadius,
    jfloat jSharpeningDetail, jfloat jSharpeningMasking,
    jint jOutputColorSpace, jboolean jEnableDithering,
    jboolean jEnableLensCorrection, jfloat jDistortionCorrection,
    jfloat jVignettingCorrection, jfloat jChromaticAberration,
    jfloat jDefringePurple, jfloat jDefringeGreen,
    jfloat jCropX, jfloat jCropY, jfloat jCropW, jfloat jCropH,
    jfloat jRotationDegrees, jint jRotationSteps,
    jboolean jFlipHorizontal, jboolean jFlipVertical,
    jfloat jPerspectiveVertical, jfloat jPerspectiveHorizontal, jfloat jDistortion,
    jint jMaxLongEdge, jboolean jEnableWatermark,
    jstring jWatermarkText, jobject jCallback) {
    return Impl_nativeProcessRaw(env, thiz, jInputPath, jOutputPath, jFormat, jJpegQuality,
                                jChromaSubsampling, jKelvin, jTint,
                                jShadowTintR, jShadowTintG, jShadowTintB,
                                jExposureEV, jContrast, jHighlights, jShadows,
                                jWhites, jBlacks, jVibrance, jSaturation,
                                jDehaze, jClarity, jTexture,
                                jIsMonochrome, jMonochromeWeights,
                                jHslBands, jPrimaryCalibration,
                                jSplitToning, jToneCurveLUT,
                                jLuminanceNR, jLuminanceNRDetail, jLuminanceNRContrast,
                                jChromaNR, jChromaNRDetail, jChromaNRSmoothness,
                                jSharpeningAmount, jSharpeningRadius,
                                jSharpeningDetail, jSharpeningMasking,
                                jOutputColorSpace, jEnableDithering,
                                jEnableLensCorrection, jDistortionCorrection,
                                jVignettingCorrection, jChromaticAberration,
                                jDefringePurple, jDefringeGreen,
                                jCropX, jCropY, jCropW, jCropH,
                                jRotationDegrees, jRotationSteps,
                                jFlipHorizontal, jFlipVertical,
                                jPerspectiveVertical, jPerspectiveHorizontal, jDistortion,
                                jMaxLongEdge, jEnableWatermark,
                                jWatermarkText, jCallback);
}

JNIEXPORT jbyteArray JNICALL
Java_com_lightrumor_LightRumorNativeEngine_nativeExtractThumbnail(
    JNIEnv* env, jobject thiz, jstring jFilePath) {
    return Impl_nativeExtractThumbnail(env, thiz, jFilePath);
}

JNIEXPORT jintArray JNICALL
Java_com_lightrumor_LightRumorNativeEngine_nativeComputeWaveform(
    JNIEnv* env, jobject thiz, jbyteArray jRgbaBytes, jint jWidth, jint jHeight,
    jint jMode, jint jWaveW, jint jWaveH) {
    return Impl_nativeComputeWaveform(env, thiz, jRgbaBytes, jWidth, jHeight, jMode, jWaveW, jWaveH);
}

JNIEXPORT jfloatArray JNICALL
Java_com_lightrumor_LightRumorNativeEngine_nativeEvaluateRadialMask(
    JNIEnv* env, jobject thiz, jint jWidth, jint jHeight,
    jfloat jCenterX, jfloat jCenterY, jfloat jRadiusX, jfloat jRadiusY,
    jfloat jAngleRad, jfloat jFeather, jboolean jInvert) {
    return Impl_nativeEvaluateRadialMask(env, thiz, jWidth, jHeight, jCenterX, jCenterY,
                                        jRadiusX, jRadiusY, jAngleRad, jFeather, jInvert);
}

JNIEXPORT jintArray JNICALL
Java_com_lightrumor_LightRumorNativeEngine_nativeApplyPoissonHeal(
    JNIEnv* env, jobject thiz, jintArray jPixels, jint jWidth, jint jHeight,
    jfloat jSrcX, jfloat jSrcY, jfloat jDstX, jfloat jDstY,
    jfloat jRadius, jfloat jFeather, jint jIterations) {
    return Impl_nativeApplyPoissonHeal(env, thiz, jPixels, jWidth, jHeight,
                                      jSrcX, jSrcY, jDstX, jDstY, jRadius, jFeather, jIterations);
}

JNIEXPORT jintArray JNICALL
Java_com_lightrumor_LightRumorNativeEngine_nativeComputeFieldScope(
    JNIEnv* env, jobject thiz, jintArray jPixels, jint jWidth, jint jHeight,
    jint jMode, jfloat jZebraThresholdIRE, jint jPeakingColor, jfloat jPeakingThreshold) {
    return Impl_nativeComputeFieldScope(env, thiz, jPixels, jWidth, jHeight,
                                       jMode, jZebraThresholdIRE, jPeakingColor, jPeakingThreshold);
}

JNIEXPORT jintArray JNICALL
Java_com_lightrumor_LightRumorNativeEngine_nativeApplySoftProof(
    JNIEnv* env, jobject thiz, jintArray jPixels, jint jWidth, jint jHeight,
    jstring jProfilePath, jint jIntent, jboolean jSimulatePaperWhite,
    jboolean jSimulateBlackInk, jboolean jShowGamutWarning, jint jGamutWarningColor) {
    return Impl_nativeApplySoftProof(env, thiz, jPixels, jWidth, jHeight,
                                    jProfilePath, jIntent, jSimulatePaperWhite,
                                    jSimulateBlackInk, jShowGamutWarning, jGamutWarningColor);
}

JNIEXPORT jboolean JNICALL
Java_com_lightrumor_LightRumorNativeEngine_nativeProcessRawMultiRecipe(
    JNIEnv* env, jobject thiz, jstring jInputPath, jstring jOutputPath,
    jint jFormat, jint jColorSpace, jint jQuality, jint jMaxDimension,
    jboolean jApplySharpening, jfloat jSharpeningAmount,
    jboolean jEnableWatermark, jstring jWatermarkText) {
    return Impl_nativeProcessRawMultiRecipe(env, thiz, jInputPath, jOutputPath,
                                           jFormat, jColorSpace, jQuality, jMaxDimension,
                                           jApplySharpening, jSharpeningAmount,
                                           jEnableWatermark, jWatermarkText);
}

} // extern "C"
#endif
