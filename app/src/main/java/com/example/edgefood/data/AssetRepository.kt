package com.example.edgefood.data

import android.content.Context
import com.example.edgefood.model.RagDoc
import com.example.edgefood.model.RuleConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AssetRepository(private val context: Context) {

    fun loadRagDocs(): List<RagDoc> {
        val text = context.assets.open("rag_store.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    RagDoc(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        text = obj.getString("text"),
                        tags = jsonArrayToList(obj.getJSONArray("tags")),
                    )
                )
            }
        }
    }

    fun loadRuleConfig(): RuleConfig {
        val text = context.assets.open("rule_config.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(text)
        return RuleConfig(
            highSugarTerms = jsonArrayToList(obj.getJSONArray("high_sugar_terms")),
            highSodiumTerms = jsonArrayToList(obj.getJSONArray("high_sodium_terms")),
            highSatFatTerms = jsonArrayToList(obj.getJSONArray("high_sat_fat_terms")),
        )
    }

    fun ensureModelCopied(assetPath: String = "model/smollm2_food_q4.gguf"): File {
        val outFile = File(context.filesDir, "smollm2_food_q4.gguf")
        if (!outFile.exists()) {
            context.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile
    }

    private fun jsonArrayToList(arr: JSONArray): List<String> =
        buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
}
