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

jboolean Impl_nativeInit(JNIEnv* /*env*/, jobject /*thiz*/) {
    std::cout << "[JNI] LightRumorNativeEngine initialized." << std::endl;
    return JNI_TRUE;
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
    jfloat jExposureEV,
    jfloat jContrast,
    jfloat jHighlights,
    jfloat jShadows,
    jfloat jWhites,
    jfloat jBlacks,
    jfloat jVibrance,
    jfloat jSaturation,
    jboolean jIsMonochrome,
    jfloat jLuminanceNR,
    jfloat jChromaNR,
    jfloat jSharpeningAmount,
    jint jOutputColorSpace,
    jboolean jEnableDithering,
    jobject jCallback) {

    const char* inputChars = env->GetStringUTFChars(jInputPath, nullptr);
    const char* outputChars = env->GetStringUTFChars(jOutputPath, nullptr);
    std::string inputPath(inputChars);
    std::string outputPath(outputChars);
    env->ReleaseStringUTFChars(jInputPath, inputChars);
    env->ReleaseStringUTFChars(jOutputPath, outputChars);

    light_rumor::RawDecoder decoder;
    if (!decoder.openFile(inputPath)) {
        std::cerr << "[JNI] Failed to open RAW: " << inputPath << std::endl;
        return JNI_FALSE;
    }

    light_rumor::DevelopmentParams params;
    params.kelvin = jKelvin;
    params.tint = jTint;
    params.exposureEV = jExposureEV;
    params.contrast = jContrast;
    params.highlights = jHighlights;
    params.shadows = jShadows;
    params.whites = jWhites;
    params.blacks = jBlacks;
    params.vibrance = jVibrance;
    params.saturation = jSaturation;
    params.isMonochrome = (jIsMonochrome == JNI_TRUE);
    params.luminanceNR = jLuminanceNR;
    params.chromaNR = jChromaNR;
    params.sharpeningAmount = jSharpeningAmount;
    params.outputColorSpace = static_cast<light_rumor::ColorSpace>(jOutputColorSpace);
    params.enableDithering = (jEnableDithering == JNI_TRUE);

    light_rumor::ExportOptions options;
    options.format = static_cast<light_rumor::ExportFormat>(jFormat);
    options.jpegQuality = jJpegQuality;
    options.chromaSubsampling = static_cast<light_rumor::ChromaSubsampling>(jChromaSubsampling);
    options.embedExif = true;
    options.tileSize = 2048;
    options.tilePadding = 16;

    jclass callbackClass = nullptr;
    jmethodID onProgressMethod = nullptr;
    if (jCallback) {
        callbackClass = env->GetObjectClass(jCallback);
        if (callbackClass) {
            onProgressMethod = env->GetMethodID(callbackClass, "onProgress", "(FLjava/lang/String;)V");
        }
    }

    light_rumor::ProgressCallback progressCb = nullptr;
    if (jCallback && onProgressMethod) {
        progressCb = [&](float pct, const std::string& status) {
            jstring jStatus = env->NewStringUTF(status.c_str());
            env->CallVoidMethod(jCallback, onProgressMethod, pct, jStatus);
            env->DeleteLocalRef(jStatus);
        };
    }

    light_rumor::ExportPipeline pipeline;
    bool success = pipeline.processImage(decoder, params, options, outputPath, progressCb);
    return success ? JNI_TRUE : JNI_FALSE;
}

jbyteArray Impl_nativeExtractThumbnail(JNIEnv* env, jobject /*thiz*/, jstring jFilePath) {
    if (!jFilePath) return nullptr;
    const char* pathChars = env->GetStringUTFChars(jFilePath, nullptr);
    std::string filePath(pathChars);
    env->ReleaseStringUTFChars(jFilePath, pathChars);

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
}

jintArray Impl_nativeComputeWaveform(
    JNIEnv* env, jobject /*thiz*/,
    jbyteArray jRgbaBytes,
    jint jWidth, jint jHeight,
    jint jMode,
    jint jWaveW, jint jWaveH) {
    if (!jRgbaBytes || jWidth <= 0 || jHeight <= 0 || jWaveW <= 0 || jWaveH <= 0) {
        return nullptr;
    }

    jsize len = env->GetArrayLength(jRgbaBytes);
    if (len < jWidth * jHeight * 4) return nullptr;

    jbyte* bytes = env->GetByteArrayElements(jRgbaBytes, nullptr);
    if (!bytes) return nullptr;

    static light_rumor::WaveformEngine s_engine;
    light_rumor::WaveformData outData;
    s_engine.computeFromRGBA8(
        reinterpret_cast<const uint8_t*>(bytes),
        jWidth, jHeight,
        static_cast<light_rumor::WaveformMode>(jMode),
        jWaveW, jWaveH,
        outData
    );

    env->ReleaseByteArrayElements(jRgbaBytes, bytes, JNI_ABORT);

    if (outData.rgbaPixels.empty()) return nullptr;

    jintArray result = env->NewIntArray(static_cast<jsize>(outData.rgbaPixels.size()));
    if (!result) return nullptr;

    env->SetIntArrayRegion(result, 0, static_cast<jsize>(outData.rgbaPixels.size()),
                           reinterpret_cast<const jint*>(outData.rgbaPixels.data()));
    return result;
}

jfloatArray Impl_nativeEvaluateRadialMask(
    JNIEnv* env, jobject /*thiz*/,
    jint jWidth, jint jHeight,
    jfloat jCenterX, jfloat jCenterY,
    jfloat jRadiusX, jfloat jRadiusY,
    jfloat jAngleRad, jfloat jFeather,
    jboolean jInvert) {
    if (jWidth <= 0 || jHeight <= 0) return nullptr;

    light_rumor::MaskLayer layer;
    layer.type = light_rumor::MaskType::RadialGradient;
    layer.radialCenter = light_rumor::Point2D(jCenterX, jCenterY);
    layer.radialRadiusX = jRadiusX;
    layer.radialRadiusY = jRadiusY;
    layer.radialAngle = jAngleRad;
    layer.radialFeather = jFeather;
    layer.inverted = (jInvert == JNI_TRUE);

    std::vector<light_rumor::FloatRGBA> dummyPixels(static_cast<size_t>(jWidth) * jHeight);
    std::vector<float> outMask;
    light_rumor::MaskEngine::evaluateSingleMask(layer, dummyPixels, jWidth, jHeight, {}, outMask);

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(outMask.size()));
    if (!result) return nullptr;
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(outMask.size()), outMask.data());
    return result;
}

jintArray Impl_nativeApplyPoissonHeal(
    JNIEnv* env, jobject /*thiz*/,
    jintArray jPixels,
    jint jWidth, jint jHeight,
    jfloat jSrcX, jfloat jSrcY,
    jfloat jDstX, jfloat jDstY,
    jfloat jRadius, jfloat jFeather,
    jint jIterations) {
    if (!jPixels || jWidth <= 0 || jHeight <= 0) return nullptr;

    jsize len = env->GetArrayLength(jPixels);
    if (len < jWidth * jHeight) return nullptr;

    jint* pData = env->GetIntArrayElements(jPixels, nullptr);
    if (!pData) return nullptr;

    std::vector<light_rumor::FloatRGBA> fPixels(static_cast<size_t>(jWidth) * jHeight);
    for (size_t i = 0; i < fPixels.size(); ++i) {
        uint32_t c = static_cast<uint32_t>(pData[i]);
        float a = ((c >> 24) & 0xFF) / 255.0f;
        float r = ((c >> 16) & 0xFF) / 255.0f;
        float g = ((c >> 8) & 0xFF) / 255.0f;
        float b = (c & 0xFF) / 255.0f;
        fPixels[i] = light_rumor::FloatRGBA(r, g, b, a);
    }
    env->ReleaseIntArrayElements(jPixels, pData, JNI_ABORT);

    light_rumor::RetouchOperation op;
    op.isHeal = true;
    op.sourcePos = light_rumor::Point2D(jSrcX, jSrcY);
    op.targetPos = light_rumor::Point2D(jDstX, jDstY);
    op.radius = jRadius;
    op.feather = jFeather;
    op.opacity = 1.0f;

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
}

jintArray Impl_nativeComputeFieldScope(
    JNIEnv* env, jobject /*thiz*/,
    jintArray jPixels,
    jint jWidth, jint jHeight,
    jint jMode,
    jfloat jZebraThresholdIRE,
    jint jPeakingColor,
    jfloat jPeakingThreshold) {
    if (!jPixels || jWidth <= 0 || jHeight <= 0) return nullptr;

    jint* pData = env->GetIntArrayElements(jPixels, nullptr);
    if (!pData) return nullptr;

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
    env->ReleaseIntArrayElements(jPixels, pData, JNI_ABORT);

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
    if (!jPixels || jWidth <= 0 || jHeight <= 0) return nullptr;

    const char* pChars = jProfilePath ? env->GetStringUTFChars(jProfilePath, nullptr) : "";
    std::string profilePath = pChars ? pChars : "";
    if (jProfilePath && pChars) env->ReleaseStringUTFChars(jProfilePath, pChars);

    jint* pData = env->GetIntArrayElements(jPixels, nullptr);
    if (!pData) return nullptr;

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
    env->ReleaseIntArrayElements(jPixels, pData, JNI_ABORT);

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
    const char* inChars = env->GetStringUTFChars(jInputPath, nullptr);
    const char* outChars = env->GetStringUTFChars(jOutputPath, nullptr);
    const char* wmChars = jWatermarkText ? env->GetStringUTFChars(jWatermarkText, nullptr) : "";

    std::string inputPath = inChars;
    std::string outputPath = outChars;
    std::string watermarkText = wmChars ? wmChars : "";

    env->ReleaseStringUTFChars(jInputPath, inChars);
    env->ReleaseStringUTFChars(jOutputPath, outChars);
    if (jWatermarkText && wmChars) env->ReleaseStringUTFChars(jWatermarkText, wmChars);

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

    light_rumor::ExportPipeline pipeline;
    return pipeline.processImage(decoder, params, options, outputPath) ? JNI_TRUE : JNI_FALSE;
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
    jint jChromaSubsampling, jfloat jKelvin, jfloat jTint, jfloat jExposureEV,
    jfloat jContrast, jfloat jHighlights, jfloat jShadows, jfloat jWhites,
    jfloat jBlacks, jfloat jVibrance, jfloat jSaturation, jboolean jIsMonochrome,
    jfloat jLuminanceNR, jfloat jChromaNR, jfloat jSharpeningAmount,
    jint jOutputColorSpace, jboolean jEnableDithering, jobject jCallback) {
    return Impl_nativeProcessRaw(env, thiz, jInputPath, jOutputPath, jFormat, jJpegQuality,
                                jChromaSubsampling, jKelvin, jTint, jExposureEV,
                                jContrast, jHighlights, jShadows, jWhites,
                                jBlacks, jVibrance, jSaturation, jIsMonochrome,
                                jLuminanceNR, jChromaNR, jSharpeningAmount,
                                jOutputColorSpace, jEnableDithering, jCallback);
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
