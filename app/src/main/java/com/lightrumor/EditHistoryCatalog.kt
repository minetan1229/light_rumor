package com.lightrumor

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 編集履歴カタログ。
 * 編集した写真のURI、ファイル名、最終編集日時、現像パラメータを
 * SharedPreferencesにJSONで保存するシンプルなカタログマネージャー。
 * 最大50件の最近の編集履歴を保持。
 */
class EditHistoryCatalog(context: Context) {

    companion object {
        private const val PREFS_NAME = "light_rumor_catalog"
        private const val KEY_HISTORY = "edit_history"
        private const val MAX_ENTRIES = 50
        fun paramsToJson(params: DevelopmentParams): String {
            val obj = JSONObject()
            obj.put("exposureEV", params.exposureEV.toDouble())
            obj.put("kelvin", params.kelvin.toDouble())
            obj.put("tint", params.tint.toDouble())
            obj.put("shadowTintR", params.shadowTintR.toDouble())
            obj.put("shadowTintG", params.shadowTintG.toDouble())
            obj.put("shadowTintB", params.shadowTintB.toDouble())
            obj.put("contrast", params.contrast.toDouble())
            obj.put("highlights", params.highlights.toDouble())
            obj.put("shadows", params.shadows.toDouble())
            obj.put("whites", params.whites.toDouble())
            obj.put("blacks", params.blacks.toDouble())
            obj.put("vibrance", params.vibrance.toDouble())
            obj.put("saturation", params.saturation.toDouble())
            obj.put("dehaze", params.dehaze.toDouble())
            obj.put("clarity", params.clarity.toDouble())
            obj.put("texture", params.texture.toDouble())
            obj.put("colorProfile", params.colorProfile)
            obj.put("isMonochrome", params.isMonochrome)

            // HSL Bands
            val hslArray = JSONArray()
            for (band in params.hslBands) {
                val bObj = JSONObject()
                bObj.put("h", band.hueShift.toDouble())
                bObj.put("s", band.saturation.toDouble())
                bObj.put("l", band.luminance.toDouble())
                hslArray.put(bObj)
            }
            obj.put("hslBands", hslArray)

            // Monochrome Weights
            val mwArray = JSONArray()
            for (w in params.monochromeWeights) {
                mwArray.put(w.toDouble())
            }
            obj.put("monochromeWeights", mwArray)

            // Noise Reduction & Sharpening
            obj.put("luminanceNR", params.luminanceNR.toDouble())
            obj.put("luminanceNRDetail", params.luminanceNRDetail.toDouble())
            obj.put("luminanceNRContrast", params.luminanceNRContrast.toDouble())
            obj.put("chromaNR", params.chromaNR.toDouble())
            obj.put("chromaNRDetail", params.chromaNRDetail.toDouble())
            obj.put("chromaNRSmoothness", params.chromaNRSmoothness.toDouble())
            obj.put("sharpeningAmount", params.sharpeningAmount.toDouble())
            obj.put("sharpeningRadius", params.sharpeningRadius.toDouble())
            obj.put("sharpeningDetail", params.sharpeningDetail.toDouble())
            obj.put("sharpeningMasking", params.sharpeningMasking.toDouble())
            obj.put("sharpeningPreviewMask", params.sharpeningPreviewMask)

            // Tone Curve LUT (if defined)
            val tcArray = JSONArray()
            for (v in params.toneCurveLUT) {
                tcArray.put(v.toDouble())
            }
            obj.put("toneCurveLUT", tcArray)

            // Primary Calibration
            val prObj = JSONObject().apply {
                put("h", params.primaryRed.hueShift.toDouble())
                put("s", params.primaryRed.saturationShift.toDouble())
            }
            obj.put("primaryRed", prObj)
            val pgObj = JSONObject().apply {
                put("h", params.primaryGreen.hueShift.toDouble())
                put("s", params.primaryGreen.saturationShift.toDouble())
            }
            obj.put("primaryGreen", pgObj)
            val pbObj = JSONObject().apply {
                put("h", params.primaryBlue.hueShift.toDouble())
                put("s", params.primaryBlue.saturationShift.toDouble())
            }
            obj.put("primaryBlue", pbObj)

            // Split Toning
            val stObj = JSONObject().apply {
                put("hh", params.splitToning.highlightsHue.toDouble())
                put("hs", params.splitToning.highlightsSat.toDouble())
                put("sh", params.splitToning.shadowsHue.toDouble())
                put("ss", params.splitToning.shadowsSat.toDouble())
                put("bal", params.splitToning.balance.toDouble())
            }
            obj.put("splitToning", stObj)

            // Lens Correction
            val lcObj = JSONObject().apply {
                put("enable", params.lensCorrection.enableProfileCorrection)
                put("dist", params.lensCorrection.distortionCorrection.toDouble())
                put("vig", params.lensCorrection.vignettingCorrection.toDouble())
                put("ca", params.lensCorrection.chromaticAberration.toDouble())
                put("dfp", params.lensCorrection.defringePurple.toDouble())
                put("dfg", params.lensCorrection.defringeGreen.toDouble())
            }
            obj.put("lensCorrection", lcObj)

            // Geometry
            val gObj = JSONObject().apply {
                put("x", params.geometry.cropX.toDouble())
                put("y", params.geometry.cropY.toDouble())
                put("w", params.geometry.cropW.toDouble())
                put("h", params.geometry.cropH.toDouble())
                put("ratio", params.geometry.aspectRatio.name)
                put("rot", params.geometry.rotationDegrees.toDouble())
                put("steps", params.geometry.rotationSteps)
                put("flipH", params.geometry.flipHorizontal)
                put("flipV", params.geometry.flipVertical)
                put("perspV", params.geometry.perspectiveVertical.toDouble())
                put("perspH", params.geometry.perspectiveHorizontal.toDouble())
                put("dist", params.geometry.distortion.toDouble())
                put("guide", params.geometry.guide.name)
            }
            obj.put("geometry", gObj)

            obj.put("outputColorSpace", params.outputColorSpace.name)
            obj.put("enableDithering", params.enableDithering)
            return obj.toString()
        }

        fun jsonToParams(json: String): DevelopmentParams {
            val params = DevelopmentParams()
            try {
                val obj = JSONObject(json)
                if (obj.has("exposureEV")) params.exposureEV = obj.getDouble("exposureEV").toFloat()
                if (obj.has("kelvin")) params.kelvin = obj.getDouble("kelvin").toFloat()
                if (obj.has("tint")) params.tint = obj.getDouble("tint").toFloat()
                if (obj.has("shadowTintR")) params.shadowTintR = obj.getDouble("shadowTintR").toFloat()
                if (obj.has("shadowTintG")) params.shadowTintG = obj.getDouble("shadowTintG").toFloat()
                if (obj.has("shadowTintB")) params.shadowTintB = obj.getDouble("shadowTintB").toFloat()
                if (obj.has("contrast")) params.contrast = obj.getDouble("contrast").toFloat()
                if (obj.has("highlights")) params.highlights = obj.getDouble("highlights").toFloat()
                if (obj.has("shadows")) params.shadows = obj.getDouble("shadows").toFloat()
                if (obj.has("whites")) params.whites = obj.getDouble("whites").toFloat()
                if (obj.has("blacks")) params.blacks = obj.getDouble("blacks").toFloat()
                if (obj.has("vibrance")) params.vibrance = obj.getDouble("vibrance").toFloat()
                if (obj.has("saturation")) params.saturation = obj.getDouble("saturation").toFloat()
                if (obj.has("dehaze")) params.dehaze = obj.getDouble("dehaze").toFloat()
                if (obj.has("clarity")) params.clarity = obj.getDouble("clarity").toFloat()
                if (obj.has("texture")) params.texture = obj.getDouble("texture").toFloat()
                if (obj.has("colorProfile")) params.colorProfile = obj.getString("colorProfile")
                if (obj.has("isMonochrome")) params.isMonochrome = obj.getBoolean("isMonochrome")

                if (obj.has("hslBands")) {
                    val arr = obj.getJSONArray("hslBands")
                    for (i in 0 until minOf(arr.length(), params.hslBands.size)) {
                        val bObj = arr.getJSONObject(i)
                        params.hslBands[i] = HSLBandAdjust(
                            hueShift = bObj.optDouble("h", 0.0).toFloat(),
                            saturation = bObj.optDouble("s", 0.0).toFloat(),
                            luminance = bObj.optDouble("l", 0.0).toFloat()
                        )
                    }
                }

                if (obj.has("monochromeWeights")) {
                    val arr = obj.getJSONArray("monochromeWeights")
                    val weights = FloatArray(arr.length()) { idx -> arr.optDouble(idx, 0.0).toFloat() }
                    if (weights.size == 8) {
                        params.monochromeWeights = weights
                    }
                }

                if (obj.has("luminanceNR")) params.luminanceNR = obj.getDouble("luminanceNR").toFloat()
                if (obj.has("luminanceNRDetail")) params.luminanceNRDetail = obj.getDouble("luminanceNRDetail").toFloat()
                if (obj.has("luminanceNRContrast")) params.luminanceNRContrast = obj.getDouble("luminanceNRContrast").toFloat()
                if (obj.has("chromaNR")) params.chromaNR = obj.getDouble("chromaNR").toFloat()
                if (obj.has("chromaNRDetail")) params.chromaNRDetail = obj.getDouble("chromaNRDetail").toFloat()
                if (obj.has("chromaNRSmoothness")) params.chromaNRSmoothness = obj.getDouble("chromaNRSmoothness").toFloat()
                if (obj.has("sharpeningAmount")) params.sharpeningAmount = obj.getDouble("sharpeningAmount").toFloat()
                if (obj.has("sharpeningRadius")) params.sharpeningRadius = obj.getDouble("sharpeningRadius").toFloat()
                if (obj.has("sharpeningDetail")) params.sharpeningDetail = obj.getDouble("sharpeningDetail").toFloat()
                if (obj.has("sharpeningMasking")) params.sharpeningMasking = obj.getDouble("sharpeningMasking").toFloat()
                if (obj.has("sharpeningPreviewMask")) params.sharpeningPreviewMask = obj.getBoolean("sharpeningPreviewMask")

                if (obj.has("toneCurveLUT")) {
                    val arr = obj.getJSONArray("toneCurveLUT")
                    params.toneCurveLUT = FloatArray(arr.length()) { idx -> arr.optDouble(idx, 0.0).toFloat() }
                }

                if (obj.has("primaryRed")) {
                    val p = obj.getJSONObject("primaryRed")
                    params.primaryRed = PrimaryCalibration(p.optDouble("h", 0.0).toFloat(), p.optDouble("s", 0.0).toFloat())
                }
                if (obj.has("primaryGreen")) {
                    val p = obj.getJSONObject("primaryGreen")
                    params.primaryGreen = PrimaryCalibration(p.optDouble("h", 0.0).toFloat(), p.optDouble("s", 0.0).toFloat())
                }
                if (obj.has("primaryBlue")) {
                    val p = obj.getJSONObject("primaryBlue")
                    params.primaryBlue = PrimaryCalibration(p.optDouble("h", 0.0).toFloat(), p.optDouble("s", 0.0).toFloat())
                }

                if (obj.has("splitToning")) {
                    val st = obj.getJSONObject("splitToning")
                    params.splitToning = SplitToningParams(
                        highlightsHue = st.optDouble("hh", 0.0).toFloat(),
                        highlightsSat = st.optDouble("hs", 0.0).toFloat(),
                        shadowsHue = st.optDouble("sh", 0.0).toFloat(),
                        shadowsSat = st.optDouble("ss", 0.0).toFloat(),
                        balance = st.optDouble("bal", 0.0).toFloat()
                    )
                }

                if (obj.has("lensCorrection")) {
                    val lc = obj.getJSONObject("lensCorrection")
                    params.lensCorrection = LensCorrectionParams(
                        enableProfileCorrection = lc.optBoolean("enable", false),
                        distortionCorrection = lc.optDouble("dist", 100.0).toFloat(),
                        vignettingCorrection = lc.optDouble("vig", 100.0).toFloat(),
                        chromaticAberration = lc.optDouble("ca", 100.0).toFloat(),
                        defringePurple = lc.optDouble("dfp", 0.0).toFloat(),
                        defringeGreen = lc.optDouble("dfg", 0.0).toFloat()
                    )
                }

                if (obj.has("geometry")) {
                    val g = obj.getJSONObject("geometry")
                    val aspect = try {
                        AspectRatioMode.valueOf(g.optString("ratio", AspectRatioMode.ORIGINAL.name))
                    } catch (_: Exception) {
                        AspectRatioMode.ORIGINAL
                    }
                    val guide = try {
                        CompositionGuide.valueOf(g.optString("guide", CompositionGuide.NONE.name))
                    } catch (_: Exception) {
                        CompositionGuide.NONE
                    }
                    params.geometry = CropTransformParams(
                        cropX = g.optDouble("x", 0.0).toFloat(),
                        cropY = g.optDouble("y", 0.0).toFloat(),
                        cropW = g.optDouble("w", 1.0).toFloat(),
                        cropH = g.optDouble("h", 1.0).toFloat(),
                        aspectRatio = aspect,
                        rotationDegrees = g.optDouble("rot", 0.0).toFloat(),
                        rotationSteps = g.optInt("steps", 0),
                        flipHorizontal = g.optBoolean("flipH", false),
                        flipVertical = g.optBoolean("flipV", false),
                        perspectiveVertical = g.optDouble("perspV", 0.0).toFloat(),
                        perspectiveHorizontal = g.optDouble("perspH", 0.0).toFloat(),
                        distortion = g.optDouble("dist", 0.0).toFloat(),
                        guide = guide
                    )
                }

                if (obj.has("outputColorSpace")) {
                    params.outputColorSpace = try {
                        ColorSpace.valueOf(obj.getString("outputColorSpace"))
                    } catch (_: Exception) {
                        ColorSpace.sRGB
                    }
                }
                if (obj.has("enableDithering")) params.enableDithering = obj.getBoolean("enableDithering")

            } catch (e: Exception) {
                e.printStackTrace()
            }
            return params
        }
    }

    data class CatalogEntry(
        val uri: String,
        val filePath: String = "",
        val fileName: String = "",
        val lastEditedAt: Long = System.currentTimeMillis(),
        val devParamsJson: String = "",
        val rating: Int = 0,
        val pickStatus: String = "NONE",
        val colorLabel: String = "NONE",
        val captureDate: String = "",
        val cameraModel: String = "",
        val lensModel: String = "",
        val fNumber: String = "",
        val exposureTime: String = "",
        val isoSpeed: String = "",
        val focalLength: String = ""
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun saveEntry(
        uri: String,
        fileName: String,
        params: DevelopmentParams,
        filePath: String = "",
        metadata: CullingItemMetadata? = null
    ) {
        val currentEntries = getRecentEntries().toMutableList()
        val existing = currentEntries.find { it.uri == uri }
        currentEntries.removeAll { it.uri == uri }

        val resolvedFilePath = if (filePath.isNotEmpty()) filePath else (existing?.filePath ?: "")
        val resolvedRating = metadata?.rating ?: existing?.rating ?: 0
        val resolvedPickStatus = metadata?.pickStatus?.name ?: existing?.pickStatus ?: "NONE"
        val resolvedColorLabel = metadata?.colorLabel?.name ?: existing?.colorLabel ?: "NONE"
        val resolvedCaptureDate = metadata?.captureDate?.ifEmpty { null } ?: existing?.captureDate ?: ""
        val resolvedCameraModel = metadata?.cameraModel?.ifEmpty { null } ?: existing?.cameraModel ?: ""
        val resolvedLensModel = metadata?.lensModel?.ifEmpty { null } ?: existing?.lensModel ?: ""
        val resolvedFNumber = metadata?.fNumber?.ifEmpty { null } ?: existing?.fNumber ?: ""
        val resolvedExposureTime = metadata?.exposureTime?.ifEmpty { null } ?: existing?.exposureTime ?: ""
        val resolvedIsoSpeed = metadata?.isoSpeed?.ifEmpty { null } ?: existing?.isoSpeed ?: ""
        val resolvedFocalLength = metadata?.focalLength?.ifEmpty { null } ?: existing?.focalLength ?: ""

        val newEntry = CatalogEntry(
            uri = uri,
            filePath = resolvedFilePath,
            fileName = fileName.ifEmpty { existing?.fileName ?: "" },
            lastEditedAt = System.currentTimeMillis(),
            devParamsJson = paramsToJson(params),
            rating = resolvedRating,
            pickStatus = resolvedPickStatus,
            colorLabel = resolvedColorLabel,
            captureDate = resolvedCaptureDate,
            cameraModel = resolvedCameraModel,
            lensModel = resolvedLensModel,
            fNumber = resolvedFNumber,
            exposureTime = resolvedExposureTime,
            isoSpeed = resolvedIsoSpeed,
            focalLength = resolvedFocalLength
        )

        currentEntries.add(0, newEntry)

        if (currentEntries.size > MAX_ENTRIES) {
            currentEntries.subList(MAX_ENTRIES, currentEntries.size).clear()
        }

        val jsonArray = JSONArray()
        for (entry in currentEntries) {
            val obj = JSONObject()
            obj.put("uri", entry.uri)
            obj.put("filePath", entry.filePath)
            obj.put("fileName", entry.fileName)
            obj.put("lastEditedAt", entry.lastEditedAt)
            obj.put("devParamsJson", entry.devParamsJson)
            obj.put("rating", entry.rating)
            obj.put("pickStatus", entry.pickStatus)
            obj.put("colorLabel", entry.colorLabel)
            obj.put("captureDate", entry.captureDate)
            obj.put("cameraModel", entry.cameraModel)
            obj.put("lensModel", entry.lensModel)
            obj.put("fNumber", entry.fNumber)
            obj.put("exposureTime", entry.exposureTime)
            obj.put("isoSpeed", entry.isoSpeed)
            obj.put("focalLength", entry.focalLength)
            jsonArray.put(obj)
        }

        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    suspend fun saveEntryAsync(
        uri: String,
        fileName: String,
        params: DevelopmentParams,
        filePath: String = "",
        metadata: CullingItemMetadata? = null
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        saveEntry(uri, fileName, params, filePath, metadata)
    }

    @Synchronized
    fun saveMetadataOnly(
        uri: String,
        fileName: String,
        filePath: String,
        metadata: CullingItemMetadata,
        params: DevelopmentParams? = null
    ) {
        val existing = getEntryForUri(uri)
        val devParams = params ?: (existing?.devParamsJson?.let { jsonToParams(it) } ?: DevelopmentParams())
        saveEntry(
            uri = uri,
            fileName = fileName.ifEmpty { existing?.fileName ?: "" },
            params = devParams,
            filePath = if (filePath.isNotEmpty()) filePath else (existing?.filePath ?: ""),
            metadata = metadata
        )
    }

    @Synchronized
    fun getRecentEntries(): List<CatalogEntry> {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<CatalogEntry>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    CatalogEntry(
                        uri = obj.getString("uri"),
                        filePath = if (obj.has("filePath")) obj.getString("filePath") else "",
                        fileName = if (obj.has("fileName")) obj.getString("fileName") else "",
                        lastEditedAt = if (obj.has("lastEditedAt")) obj.getLong("lastEditedAt") else 0L,
                        devParamsJson = if (obj.has("devParamsJson")) obj.getString("devParamsJson") else "",
                        rating = if (obj.has("rating")) obj.getInt("rating") else 0,
                        pickStatus = if (obj.has("pickStatus")) obj.getString("pickStatus") else "NONE",
                        colorLabel = if (obj.has("colorLabel")) obj.getString("colorLabel") else "NONE",
                        captureDate = if (obj.has("captureDate")) obj.getString("captureDate") else "",
                        cameraModel = if (obj.has("cameraModel")) obj.getString("cameraModel") else "",
                        lensModel = if (obj.has("lensModel")) obj.getString("lensModel") else "",
                        fNumber = if (obj.has("fNumber")) obj.getString("fNumber") else "",
                        exposureTime = if (obj.has("exposureTime")) obj.getString("exposureTime") else "",
                        isoSpeed = if (obj.has("isoSpeed")) obj.getString("isoSpeed") else "",
                        focalLength = if (obj.has("focalLength")) obj.getString("focalLength") else ""
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.lastEditedAt }
    }

    suspend fun getRecentEntriesAsync(): List<CatalogEntry> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        getRecentEntries()
    }

    fun getEntryForUri(uri: String): CatalogEntry? {
        return getRecentEntries().find { it.uri == uri }
    }

    fun getParamsForUri(uri: String): DevelopmentParams? {
        val entry = getEntryForUri(uri) ?: return null
        return if (entry.devParamsJson.isNotEmpty()) jsonToParams(entry.devParamsJson) else null
    }

    fun applyMetadataFromCatalog(uri: String, meta: CullingItemMetadata) {
        val entry = getEntryForUri(uri) ?: return
        if (entry.rating > 0) meta.rating = entry.rating
        if (entry.pickStatus != "NONE") {
            try {
                meta.pickStatus = PickStatus.valueOf(entry.pickStatus)
            } catch (_: Exception) {}
        }
        if (entry.colorLabel != "NONE") {
            try {
                meta.colorLabel = ColorLabel.valueOf(entry.colorLabel)
            } catch (_: Exception) {}
        }
        if (entry.cameraModel.isNotEmpty()) meta.cameraModel = entry.cameraModel
        if (entry.lensModel.isNotEmpty()) meta.lensModel = entry.lensModel
        if (entry.fNumber.isNotEmpty()) meta.fNumber = entry.fNumber
        if (entry.exposureTime.isNotEmpty()) meta.exposureTime = entry.exposureTime
        if (entry.isoSpeed.isNotEmpty()) meta.isoSpeed = entry.isoSpeed
        if (entry.focalLength.isNotEmpty()) meta.focalLength = entry.focalLength
        if (entry.captureDate.isNotEmpty() && meta.captureDate.isEmpty()) meta.captureDate = entry.captureDate
    }
}
