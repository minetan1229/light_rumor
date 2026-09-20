#include "apex/Common.h"
#include "apex/RawDecoder.h"
#include "apex/ExportPipeline.h"
#include <iostream>
#include <string>

#if defined(__ANDROID__) || defined(APEX_ENABLE_JNI)
#include <jni.h>

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_apexfield_engine_ApexNativeEngine_nativeInit(JNIEnv* env, jobject thiz) {
    std::cout << "[JNI] ApexNativeEngine initialized." << std::endl;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_apexfield_engine_ApexNativeEngine_nativeProcessRaw(
    JNIEnv* env, jobject thiz,
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

    apex::RawDecoder decoder;
    if (!decoder.openFile(inputPath)) {
        std::cerr << "[JNI] Failed to open RAW: " << inputPath << std::endl;
        return JNI_FALSE;
    }

    apex::DevelopmentParams params;
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
    params.outputColorSpace = static_cast<apex::ColorSpace>(jOutputColorSpace);
    params.enableDithering = (jEnableDithering == JNI_TRUE);

    apex::ExportOptions options;
    options.format = static_cast<apex::ExportFormat>(jFormat);
    options.jpegQuality = jJpegQuality;
    options.chromaSubsampling = static_cast<apex::ChromaSubsampling>(jChromaSubsampling);
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

    apex::ProgressCallback progressCb = nullptr;
    if (jCallback && onProgressMethod) {
        progressCb = [&](float pct, const std::string& status) {
            jstring jStatus = env->NewStringUTF(status.c_str());
            env->CallVoidMethod(jCallback, onProgressMethod, pct, jStatus);
            env->DeleteLocalRef(jStatus);
        };
    }

    apex::ExportPipeline pipeline;
    bool success = pipeline.processImage(decoder, params, options, outputPath, progressCb);
    return success ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
#endif
