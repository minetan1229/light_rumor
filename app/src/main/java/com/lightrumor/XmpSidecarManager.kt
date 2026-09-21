package com.lightrumor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Non-destructive XMP Sidecar Metadata Manager.
 * Reads and writes standard Adobe-compatible XMP sidecars (.xmp) asynchronously
 * with debouncing to prevent storage I/O bottlenecks during rapid culling.
 */
object XmpSidecarManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val debounceJobs = mutableMapOf<String, Job>()

    /**
     * Determines the sidecar file location for a given image path.
     * E.g. "/path/DSC01234.ARW" -> "/path/DSC01234.xmp"
     */
    fun getSidecarFile(imageFilePath: String): File {
        val file = File(imageFilePath)
        val nameWithoutExt = file.nameWithoutExtension
        return File(file.parentFile, "$nameWithoutExt.xmp")
    }

    /**
     * Reads rating, pick status, color label, and develop parameters from .xmp sidecar if it exists.
     */
    suspend fun readSidecar(imageFilePath: String): Pair<CullingItemMetadata, DevelopmentParams>? = withContext(Dispatchers.IO) {
        if (imageFilePath.isEmpty()) return@withContext null
        val sidecar = getSidecarFile(imageFilePath)
        if (!sidecar.exists()) return@withContext null

        try {
            val xml = sidecar.readText()
            val meta = CullingItemMetadata(id = imageFilePath, filePath = imageFilePath)
            val params = DevelopmentParams()

            // 1. Parse Rating: <xmp:Rating>3</xmp:Rating>
            val ratingMatch = Regex("<xmp:Rating>(\\d+)</xmp:Rating>").find(xml)
            if (ratingMatch != null) {
                meta.rating = ratingMatch.groupValues[1].toIntOrNull() ?: 0
            }

            // 2. Parse Color Label: <xmp:Label>Red</xmp:Label>
            val labelMatch = Regex("<xmp:Label>([A-Za-z]+)</xmp:Label>").find(xml)
            if (labelMatch != null) {
                val labelStr = labelMatch.groupValues[1]
                meta.colorLabel = ColorLabel.values().firstOrNull { it.labelName.equals(labelStr, ignoreCase = true) } ?: ColorLabel.NONE
            }

            // 3. Parse Pick Status: <photoshop:Urgency>1</photoshop:Urgency> or <crs:Pick>1</crs:Pick>
            val pickMatch = Regex("<(photoshop:Urgency|crs:Pick)>(-?\\d+)</(photoshop:Urgency|crs:Pick)>").find(xml)
            if (pickMatch != null) {
                val pickVal = pickMatch.groupValues[2].toIntOrNull() ?: 0
                meta.pickStatus = when (pickVal) {
                    1 -> PickStatus.PICKED
                    -1 -> PickStatus.REJECTED
                    else -> PickStatus.NONE
                }
            }

            // 4. Parse Develop Parameters: e.g. crs:Exposure2012="+0.50"
            val expMatch = Regex("crs:Exposure2012=\"([+-]?\\d*\\.?\\d+)\"").find(xml)
            if (expMatch != null) {
                params.exposureEV = expMatch.groupValues[1].toFloatOrNull() ?: 0.0f
            }
            val tempMatch = Regex("crs:Temperature=\"(\\d+)\"").find(xml)
            if (tempMatch != null) {
                params.kelvin = tempMatch.groupValues[1].toFloatOrNull() ?: 5500.0f
            }
            val tintMatch = Regex("crs:Tint=\"([+-]?\\d*\\.?\\d+)\"").find(xml)
            if (tintMatch != null) {
                params.tint = tintMatch.groupValues[1].toFloatOrNull() ?: 0.0f
            }

            Pair(meta, params)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Debounced asynchronous save of rating and develop adjustments to .xmp sidecar.
     * Default debounce delay is 300ms to allow smooth, rapid flicking without writing to disk on every swipe.
     */
    fun scheduleSaveSidecar(
        imageFilePath: String,
        meta: CullingItemMetadata,
        params: DevelopmentParams,
        debounceMs: Long = 300L
    ) {
        if (imageFilePath.isEmpty()) return

        debounceJobs[imageFilePath]?.cancel()
        debounceJobs[imageFilePath] = scope.launch {
            delay(debounceMs)
            writeSidecarDirect(imageFilePath, meta, params)
        }
    }

    /**
     * Directly writes standard Adobe Camera Raw / Lightroom compatible XMP packet.
     */
    suspend fun writeSidecarDirect(
        imageFilePath: String,
        meta: CullingItemMetadata,
        params: DevelopmentParams
    ) = withContext(Dispatchers.IO) {
        try {
            val sidecar = getSidecarFile(imageFilePath)
            val pickVal = when (meta.pickStatus) {
                PickStatus.PICKED -> 1
                PickStatus.REJECTED -> -1
                PickStatus.NONE -> 0
            }

            val xmpPacket = buildString {
                append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
                append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
                append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
                append("  <rdf:Description rdf:about=\"\"\n")
                append("    xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\"\n")
                append("    xmlns:photoshop=\"http://ns.adobe.com/photoshop/1.0/\"\n")
                append("    xmlns:crs=\"http://ns.adobe.com/camera-raw-settings/1.0/\"\n")
                append("   xmp:Rating=\"${meta.rating}\"\n")
                if (meta.colorLabel != ColorLabel.NONE) {
                    append("   xmp:Label=\"${meta.colorLabel.labelName}\"\n")
                }
                append("   photoshop:Urgency=\"$pickVal\"\n")
                append("   crs:Pick=\"$pickVal\"\n")
                append("   crs:Temperature=\"${params.kelvin.toInt()}\"\n")
                append("   crs:Tint=\"${"%.1f".format(params.tint)}\"\n")
                append("   crs:Exposure2012=\"${"%+.2f".format(params.exposureEV)}\"\n")
                append("   crs:Contrast2012=\"${params.contrast.toInt()}\"\n")
                append("   crs:Highlights2012=\"${params.highlights.toInt()}\"\n")
                append("   crs:Shadows2012=\"${params.shadows.toInt()}\"\n")
                append("   crs:Whites2012=\"${params.whites.toInt()}\"\n")
                append("   crs:Blacks2012=\"${params.blacks.toInt()}\"\n")
                append("   crs:Vibrance=\"${params.vibrance.toInt()}\"\n")
                append("   crs:Saturation=\"${params.saturation.toInt()}\"\n")
                append("   crs:Sharpness=\"${params.sharpeningAmount.toInt()}\"\n")
                append("   crs:LuminanceSmoothing=\"${params.luminanceNR.toInt()}\">\n")
                append("  </rdf:Description>\n")
                append(" </rdf:RDF>\n")
                append("</x:xmpmeta>\n")
                append("<?xpacket end=\"w\"?>\n")
            }

            sidecar.writeText(xmpPacket)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

