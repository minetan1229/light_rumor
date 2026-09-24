package com.lightrumor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debounceJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /**
     * Determines the sidecar file location for a given image path.
     * E.g. "/path/DSC01234.ARW" -> "/path/DSC01234.xmp"
     */
    fun getSidecarFile(imageFilePath: String): File {
        val file = File(imageFilePath)
        val nameWithoutExt = file.nameWithoutExtension
        val parent = file.parentFile
        return if (parent != null) {
            File(parent, "$nameWithoutExt.xmp")
        } else {
            File("$nameWithoutExt.xmp")
        }
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

            // Helper to extract value from either attribute syntax (tag="val") or element syntax (<tag>val</tag>)
            fun extractTag(tag: String): String? {
                val attrPattern = Regex("""\b$tag="([^"]*)"""")
                val attrMatch = attrPattern.find(xml)
                if (attrMatch != null) return attrMatch.groupValues[1]

                val elemPattern = Regex("""<$tag>([^<]*)</$tag>""")
                val elemMatch = elemPattern.find(xml)
                if (elemMatch != null) return elemMatch.groupValues[1]

                return null
            }

            // 1. Parse Rating: xmp:Rating="3" or <xmp:Rating>3</xmp:Rating>
            extractTag("xmp:Rating")?.toIntOrNull()?.let {
                meta.rating = it
            }

            // 2. Parse Color Label: xmp:Label="Red" or <xmp:Label>Red</xmp:Label>
            extractTag("xmp:Label")?.let { labelStr ->
                meta.colorLabel = ColorLabel.entries.firstOrNull { it.labelName.equals(labelStr, ignoreCase = true) } ?: ColorLabel.NONE
            }

            // 3. Parse Pick Status: crs:Pick="1" or photoshop:Urgency="1" or element equivalents
            val pickStr = extractTag("crs:Pick") ?: extractTag("photoshop:Urgency")
            if (pickStr != null) {
                val pickVal = pickStr.toIntOrNull() ?: 0
                meta.pickStatus = when (pickVal) {
                    1 -> PickStatus.PICKED
                    -1 -> PickStatus.REJECTED
                    else -> PickStatus.NONE
                }
            }

            // 3.5 Parse Dates
            val dateStr = extractTag("xmp:CreateDate") ?: extractTag("photoshop:DateCreated") ?: extractTag("exif:DateTimeOriginal")
            if (dateStr != null) {
                meta.captureDate = dateStr
            }

            // Parse EXIF & Camera info if present
            extractTag("tiff:Make")?.let { meta.cameraMake = it }
            extractTag("tiff:Model")?.let { meta.cameraModel = it }
            (extractTag("aux:Lens") ?: extractTag("exif:LensModel"))?.let { meta.lensModel = it }
            extractTag("exif:FNumber")?.let { meta.fNumber = it }
            extractTag("exif:ExposureTime")?.let { meta.exposureTime = it }
            extractTag("exif:ISOSpeedRatings")?.let { meta.isoSpeed = it }
            extractTag("exif:FocalLength")?.let { meta.focalLength = it }

            // 4. Parse Develop Parameters: e.g. crs:Exposure2012="+0.50" or <crs:Exposure2012>+0.50</crs:Exposure2012>
            extractTag("crs:Exposure2012")?.toFloatOrNull()?.let { params.exposureEV = it }
            extractTag("crs:Temperature")?.toFloatOrNull()?.let { params.kelvin = it }
            extractTag("crs:Tint")?.toFloatOrNull()?.let { params.tint = it }
            extractTag("crs:Contrast2012")?.toFloatOrNull()?.let { params.contrast = it }
            extractTag("crs:Highlights2012")?.toFloatOrNull()?.let { params.highlights = it }
            extractTag("crs:Shadows2012")?.toFloatOrNull()?.let { params.shadows = it }
            extractTag("crs:Whites2012")?.toFloatOrNull()?.let { params.whites = it }
            extractTag("crs:Blacks2012")?.toFloatOrNull()?.let { params.blacks = it }
            extractTag("crs:Vibrance")?.toFloatOrNull()?.let { params.vibrance = it }
            extractTag("crs:Saturation")?.toFloatOrNull()?.let { params.saturation = it }
            extractTag("crs:Clarity2012")?.toFloatOrNull()?.let { params.clarity = it }
            extractTag("crs:Dehaze")?.toFloatOrNull()?.let { params.dehaze = it }
            extractTag("crs:Sharpness")?.toFloatOrNull()?.let { params.sharpeningAmount = it }
            extractTag("crs:LuminanceSmoothing")?.toFloatOrNull()?.let { params.luminanceNR = it }
            extractTag("crs:CameraProfile")?.let { if (it.isNotEmpty()) params.colorProfile = it }

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
        debounceMs: Long = 300L,
        modifiedFields: Set<String> = emptySet()
    ) {
        if (imageFilePath.isEmpty()) return

        synchronized(debounceJobs) {
            debounceJobs[imageFilePath]?.cancel()
            debounceJobs[imageFilePath] = scope.launch {
                delay(debounceMs)
                writeSidecarDirect(imageFilePath, meta, params, modifiedFields)
            }
        }
    }

    /**
     * Directly writes standard Adobe Camera Raw / Lightroom compatible XMP packet.
     */
    suspend fun writeSidecarDirect(
        imageFilePath: String,
        meta: CullingItemMetadata,
        params: DevelopmentParams,
        modifiedFields: Set<String> = emptySet()
    ) = withContext(Dispatchers.IO) {
        try {
            val sidecar = getSidecarFile(imageFilePath)
            val pickVal = when (meta.pickStatus) {
                PickStatus.PICKED -> 1
                PickStatus.REJECTED -> -1
                PickStatus.NONE -> 0
            }

            // Map of attributes to update/insert
            val updates = mutableMapOf<String, String>()
            updates["xmp:Rating"] = "${meta.rating}"
            if (meta.colorLabel != ColorLabel.NONE) {
                updates["xmp:Label"] = escapeXml(meta.colorLabel.labelName)
            }
            updates["photoshop:Urgency"] = "$pickVal"
            updates["crs:Pick"] = "$pickVal"

            if (modifiedFields.contains("captureDate") && meta.captureDate.isNotEmpty()) {
                val escapedDate = escapeXml(meta.captureDate)
                updates["xmp:CreateDate"] = escapedDate
                updates["photoshop:DateCreated"] = escapedDate
                updates["exif:DateTimeOriginal"] = escapedDate
            }
            if (modifiedFields.contains("cameraModel") && meta.cameraModel.isNotEmpty()) {
                updates["tiff:Model"] = escapeXml(meta.cameraModel)
            }
            if (modifiedFields.contains("cameraMake") && meta.cameraMake.isNotEmpty()) {
                updates["tiff:Make"] = escapeXml(meta.cameraMake)
            }
            if (modifiedFields.contains("lensModel") && meta.lensModel.isNotEmpty()) {
                updates["aux:Lens"] = escapeXml(meta.lensModel)
            }
            if (modifiedFields.contains("fNumber") && meta.fNumber.isNotEmpty()) {
                updates["exif:FNumber"] = meta.fNumber.removePrefix("f/")
            }
            if (modifiedFields.contains("exposureTime") && meta.exposureTime.isNotEmpty()) {
                updates["exif:ExposureTime"] = meta.exposureTime.removeSuffix("s")
            }
            if (modifiedFields.contains("isoSpeed") && meta.isoSpeed.isNotEmpty()) {
                updates["exif:ISOSpeedRatings"] = meta.isoSpeed.replace(Regex("[^0-9]"), "")
            }
            if (modifiedFields.contains("focalLength") && meta.focalLength.isNotEmpty()) {
                updates["exif:FocalLength"] = meta.focalLength.replace(Regex("[^0-9.]"), "")
            }

            updates["crs:Temperature"] = "${params.kelvin.toInt()}"
            updates["crs:Tint"] = String.format(java.util.Locale.US, "%.1f", params.tint)
            updates["crs:Exposure2012"] = String.format(java.util.Locale.US, "%+.2f", params.exposureEV)
            updates["crs:Contrast2012"] = "${params.contrast.toInt()}"
            updates["crs:Highlights2012"] = "${params.highlights.toInt()}"
            updates["crs:Shadows2012"] = "${params.shadows.toInt()}"
            updates["crs:Whites2012"] = "${params.whites.toInt()}"
            updates["crs:Blacks2012"] = "${params.blacks.toInt()}"
            updates["crs:Vibrance"] = "${params.vibrance.toInt()}"
            updates["crs:Saturation"] = "${params.saturation.toInt()}"
            updates["crs:Clarity2012"] = "${params.clarity.toInt()}"
            updates["crs:Dehaze"] = "${params.dehaze.toInt()}"
            if (params.colorProfile.isNotEmpty()) {
                updates["crs:CameraProfile"] = escapeXml(params.colorProfile)
            }
            updates["crs:Sharpness"] = "${params.sharpeningAmount.toInt()}"
            updates["crs:LuminanceSmoothing"] = "${params.luminanceNR.toInt()}"

            if (sidecar.exists()) {
                val existingXml = sidecar.readText()
                if (existingXml.contains("<rdf:Description")) {
                    var mergedXml = existingXml
                    for ((attr, value) in updates) {
                        val regexAttr = Regex("""\b$attr="[^"]*"""")
                        val regexElem = Regex("""<$attr>([^<]*)</$attr>""")
                        if (regexAttr.containsMatchIn(mergedXml)) {
                            mergedXml = regexAttr.replace(mergedXml, "$attr=\"$value\"")
                        } else if (regexElem.containsMatchIn(mergedXml)) {
                            mergedXml = regexElem.replace(mergedXml, "<$attr>$value</$attr>")
                        } else {
                            // Insert before closing tag of rdf:Description
                            val descRegex = Regex("""(<rdf:Description[^>]*?)(\s*(?:/>|>))""")
                            val match = descRegex.find(mergedXml)
                            if (match != null) {
                                val prefix = match.groupValues[1]
                                val suffix = match.groupValues[2]
                                mergedXml = mergedXml.replaceRange(match.range, "$prefix\n   $attr=\"$value\"$suffix")
                            }
                        }
                    }
                    sidecar.writeText(mergedXml)
                    return@withContext
                }
            }

            // Create new clean XMP packet if sidecar doesn't exist
            val xmpPacket = buildString {
                append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
                append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
                append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
                append("  <rdf:Description rdf:about=\"\"\n")
                append("    xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\"\n")
                append("    xmlns:photoshop=\"http://ns.adobe.com/photoshop/1.0/\"\n")
                append("    xmlns:crs=\"http://ns.adobe.com/camera-raw-settings/1.0/\"\n")
                append("    xmlns:exif=\"http://ns.adobe.com/exif/1.0/\"\n")
                append("    xmlns:tiff=\"http://ns.adobe.com/tiff/1.0/\"\n")
                append("    xmlns:aux=\"http://ns.adobe.com/exif/1.0/aux/\"\n")
                for ((k, v) in updates) {
                    append("   $k=\"$v\"\n")
                }
                append("  >\n")
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

    private fun escapeXml(str: String): String = str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

