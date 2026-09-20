#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include "ApexEngine.h"

#define TAG "ApexNativeBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT void JNICALL
Java_com_apex_field_engine_ApexNativeEngine_nativeProcessBuffer(
    JNIEnv* env,
    jobject /* this */,
    jfloatArray buffer,
    jint width,
    jint height,
    jfloat exposureEV,
    jfloat contrast,
    jfloat highlights,
    jfloat shadows,
    jfloat kelvin,
    jfloat tint,
    jfloat vibrance,
    jfloat saturation,
    jboolean isMonochrome
) {
    jfloat* rawBuffer = env->GetFloatArrayElements(buffer, nullptr);
    if (!rawBuffer) {
        LOGE("Failed to get float buffer elements");
        return;
    }

    apex::DevelopParameters params;
    params.exposureEV = exposureEV;
    params.contrast = contrast;
    params.highlights = highlights;
    params.shadows = shadows;
    params.temperatureKelvin = kelvin;
    params.tint = tint;
    params.vibrance = vibrance;
    params.saturation = saturation;
    params.isMonochrome = isMonochrome;

    // 32bitリニア現像パイプラインを適用
    apex::ColorPipeline::processTile(rawBuffer, width, height, params);

    env->ReleaseFloatArrayElements(buffer, rawBuffer, 0);
    LOGI("Processed buffer %dx%d successfully via C++ pipeline", width, height);
}
