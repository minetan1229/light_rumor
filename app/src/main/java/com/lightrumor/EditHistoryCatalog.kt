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
            return obj.toString()
        }

        fun jsonToParams(json: String): DevelopmentParams {
            val params = DevelopmentParams()
            try {
                val obj = JSONObject(json)
                if (obj.has("exposureEV")) params.exposureEV = obj.getDouble("exposureEV").toFloat()
                if (obj.has("kelvin")) params.kelvin = obj.getDouble("kelvin").toFloat()
                if (obj.has("tint")) params.tint = obj.getDouble("tint").toFloat()
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return params
        }
    }

    data class CatalogEntry(
        val uri: String,
        val fileName: String,
        val lastEditedAt: Long, // System.currentTimeMillis()
        val devParamsJson: String // DevelopmentParamsの主要値をJSON化
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveEntry(uri: String, fileName: String, params: DevelopmentParams) {
        val currentEntries = getRecentEntries().toMutableList()
        currentEntries.removeAll { it.uri == uri }
        
        val newEntry = CatalogEntry(
            uri = uri,
            fileName = fileName,
            lastEditedAt = System.currentTimeMillis(),
            devParamsJson = paramsToJson(params)
        )
        
        currentEntries.add(0, newEntry)
        
        if (currentEntries.size > MAX_ENTRIES) {
            currentEntries.subList(MAX_ENTRIES, currentEntries.size).clear()
        }
        
        val jsonArray = JSONArray()
        for (entry in currentEntries) {
            val obj = JSONObject()
            obj.put("uri", entry.uri)
            obj.put("fileName", entry.fileName)
            obj.put("lastEditedAt", entry.lastEditedAt)
            obj.put("devParamsJson", entry.devParamsJson)
            jsonArray.put(obj)
        }
        
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

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
                        fileName = obj.getString("fileName"),
                        lastEditedAt = obj.getLong("lastEditedAt"),
                        devParamsJson = obj.getString("devParamsJson")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.lastEditedAt }
    }

    fun getParamsForUri(uri: String): DevelopmentParams? {
        val entry = getRecentEntries().find { it.uri == uri } ?: return null
        return jsonToParams(entry.devParamsJson)
    }
}
