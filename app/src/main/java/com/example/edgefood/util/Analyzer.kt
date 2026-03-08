package com.example.edgefood.util

import com.example.edgefood.model.AnalysisResult
import com.example.edgefood.model.RagDoc
import com.example.edgefood.model.RuleConfig
import com.example.edgefood.model.RuleFlags
import com.example.edgefood.model.UserProfile
import org.json.JSONObject

class Analyzer(
    private val ragDocs: List<RagDoc>,
    private val ruleConfig: RuleConfig,
) {
    fun buildPrompt(profile: UserProfile, ingredients: List<String>): String {
        val cleanProfile = cleanProfile(profile)
        val cleanIngredients = ingredients.map { it.trim() }.filter { it.isNotBlank() }
        val retrieved = keywordRetrieve(cleanProfile, cleanIngredients)
        val ragEvidence = if (retrieved.isEmpty()) {
            "- Prefer minimally processed foods when possible."
        } else {
            retrieved.joinToString("
") { "- ${it.text}" }
        }

        val flags = evaluateRules(cleanProfile, cleanIngredients)
        val ruleLines = buildList {
            addAll(flags.allergyConflicts.map { "- $it" })
            addAll(flags.conditionConflicts.map { "- $it" })
            if (isEmpty()) add("- No strong direct conflicts detected by rule checks.")
        }.joinToString("
")

        return """
            You are a food health assistant.
            Return a JSON object with these keys only:
            "suitability", "summary", "main_reasons", "cautions", "better_options"
            Allowed suitability values:
            "good", "moderate", "poor", "avoid"
            Keep the answer short.
            - main_reasons: max 3 items
            - cautions: max 2 items
            - better_options: max 2 items
            Use only the information explicitly supported by:
            1. the profile
            2. the ingredients
            3. the rule findings
            4. the RAG evidence
            Do not mention allergies, gluten, sodium, sugar, medications, or conditions unless they are supported by the inputs or evidence.
            Allergy conflicts have highest priority.
            If any ingredient matches a listed allergy, suitability should be "avoid".

            PROFILE:
            age: ${cleanProfile.age}
            gender: ${cleanProfile.gender}
            allergies: ${cleanProfile.allergies}
            conditions: ${cleanProfile.conditions}
            diet_preferences: ${cleanProfile.dietPreferences}
            medications: ${cleanProfile.medications}
            goal: ${cleanProfile.goal}

            INGREDIENTS:
            ${cleanIngredients.joinToString(", ")}

            RULE_FINDINGS:
            $ruleLines

            RULE_SUGGESTED_SUITABILITY:
            ${flags.suggestedSuitability}

            RAG_EVIDENCE:
            $ragEvidence
        """.trimIndent()
    }

    fun parseAndValidate(rawText: String, profile: UserProfile, ingredients: List<String>): AnalysisResult {
        val parsed = extractJson(rawText)
        val validated = validateAndCleanOutput(parsed, profile, ingredients)
        return applySafetyOverride(validated, profile, ingredients)
    }

    fun cleanProfile(profile: UserProfile): UserProfile = profile.copy(
        gender = profile.gender.ifBlank { "unknown" },
        goal = profile.goal.ifBlank { "general healthy eating" },
        allergies = profile.allergies.map { it.trim() }.filter { it.isNotBlank() },
        conditions = profile.conditions.map { it.trim() }.filter { it.isNotBlank() },
        dietPreferences = profile.dietPreferences.map { it.trim() }.filter { it.isNotBlank() },
        medications = profile.medications.map { it.trim() }.filter { it.isNotBlank() },
    )

    private fun keywordRetrieve(profile: UserProfile, ingredients: List<String>, topK: Int = 4): List<RagDoc> {
        val queryTerms = mutableSetOf<String>()
        queryTerms += normalizeTerms(profile.conditions)
        queryTerms += normalizeTerms(profile.allergies)
        queryTerms += normalizeTerms(ingredients)

        return ragDocs.mapNotNull { doc ->
            val hay = (doc.title + " " + doc.text + " " + doc.tags.joinToString(" ")).lowercase()
            val score = queryTerms.count { it != "none" && hay.contains(it) }
            if (score > 0) score to doc else null
        }.sortedByDescending { it.first }
            .take(topK)
            .map { it.second }
    }

    fun evaluateRules(profile: UserProfile, ingredients: List<String>): RuleFlags {
        val terms = normalizeTerms(ingredients)
        val allergies = profile.allergies.map { it.lowercase() }
        val conditions = profile.conditions.map { it.lowercase() }

        val allergyConflicts = mutableListOf<String>()
        val conditionConflicts = mutableListOf<String>()
        var suggested = "good"

        allergies.forEach { allergy ->
            if (allergy != "none" && terms.contains(allergy)) {
                allergyConflicts += "Contains $allergy, which conflicts with the allergy"
                suggested = "avoid"
            }
        }

        if ("type 2 diabetes" in conditions) {
            val hits = ruleConfig.highSugarTerms.filter { terms.contains(it.lowercase()) }.sorted()
            if (hits.isNotEmpty()) {
                conditionConflicts += "Contains high-glycemic or added-sugar ingredients: ${hits.joinToString(", ")}"
                if (suggested != "avoid") suggested = "poor"
            }
        }

        if ("high blood pressure" in conditions) {
            val hits = ruleConfig.highSodiumTerms.filter { terms.contains(it.lowercase()) }.sorted()
            if (hits.isNotEmpty()) {
                conditionConflicts += "Contains high-sodium ingredients: ${hits.joinToString(", ")}"
                suggested = when (suggested) {
                    "good" -> "moderate"
                    "moderate" -> "poor"
                    else -> suggested
                }
            }
        }

        if ("high cholesterol" in conditions) {
            val hits = ruleConfig.highSatFatTerms.filter { terms.contains(it.lowercase()) }.sorted()
            if (hits.isNotEmpty()) {
                conditionConflicts += "Contains saturated-fat-related ingredients: ${hits.joinToString(", ")}"
                if (suggested == "good") suggested = "moderate"
            }
        }

        return RuleFlags(allergyConflicts, conditionConflicts, suggested)
    }

    private fun normalizeTerms(items: List<String>): Set<String> {
        val out = mutableSetOf<String>()
        items.forEach { raw ->
            val item = raw.lowercase().trim()
            if (item.isBlank()) return@forEach
            Regex("[a-z0-9]+").findAll(item).forEach { out += it.value }
            out += item
        }
        return out
    }

    private fun extractJson(text: String): AnalysisResult {
        val trimmed = text.trim()
        val start = trimmed.indexOf('{')
        if (start == -1) {
            return AnalysisResult(summary = if (trimmed.isBlank()) "No clear summary generated." else trimmed.take(120))
        }

        val candidate = trimmed.substring(start)
        return try {
            val obj = JSONObject(candidate)
            jsonToResult(obj)
        } catch (_: Exception) {
            val obj = JSONObject()
            regexField(candidate, "suitability")?.let { obj.put("suitability", it) }
            regexField(candidate, "summary")?.let { obj.put("summary", it) }
            obj.put("main_reasons", regexList(candidate, "main_reasons"))
            obj.put("cautions", regexList(candidate, "cautions"))
            obj.put("better_options", regexList(candidate, "better_options"))
            jsonToResult(obj)
        }
    }

    private fun jsonToResult(obj: JSONObject): AnalysisResult {
        val suitability = obj.optString("suitability", "moderate").let {
            if (it in setOf("good", "moderate", "poor", "avoid")) it else "moderate"
        }
        return AnalysisResult(
            suitability = suitability,
            summary = obj.optString("summary", "No clear summary generated."),
            mainReasons = jsonArrayToList(obj.optJSONArray("main_reasons")).take(3),
            cautions = jsonArrayToList(obj.optJSONArray("cautions")).take(2),
            betterOptions = jsonArrayToList(obj.optJSONArray("better_options")).take(2),
        )
    }

    private fun regexField(text: String, field: String): String? =
        Regex(""$field"\s*:\s*"([^"]*)"", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.getOrNull(1)

    private fun regexList(text: String, field: String): org.json.JSONArray {
        val frag = Regex(""$field"\s*:\s*\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.getOrNull(1)
        val arr = org.json.JSONArray()
        if (!frag.isNullOrBlank()) {
            Regex(""([^"]*)"").findAll(frag).forEach { arr.put(it.groupValues[1]) }
        }
        return arr
    }

    private fun jsonArrayToList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    }

    private fun validateAndCleanOutput(
        parsed: AnalysisResult,
        profile: UserProfile,
        ingredients: List<String>,
    ): AnalysisResult {
        val ingredientTerms = normalizeTerms(ingredients)
        val allergyTerms = normalizeTerms(profile.allergies)
        val conditionTerms = normalizeTerms(profile.conditions)

        val supported = mutableSetOf<String>()
        supported += ingredientTerms
        supported += allergyTerms
        supported += conditionTerms

        if (ruleConfig.highSugarTerms.any { ingredientTerms.contains(it.lowercase()) }) {
            supported += setOf("sugar", "added-sugar", "glycemic", "carbohydrate")
        }
        if (ruleConfig.highSodiumTerms.any { ingredientTerms.contains(it.lowercase()) }) {
            supported += setOf("salt", "sodium")
        }
        if (ruleConfig.highSatFatTerms.any { ingredientTerms.contains(it.lowercase()) }) {
            supported += setOf("fat", "saturated-fat", "palm oil")
        }

        fun isSupported(text: String): Boolean {
            val lower = text.lowercase()
            val blocked = listOf(
                "gluten" to supported.contains("gluten"),
                "caffeine" to supported.contains("caffeine"),
                "alcohol" to supported.contains("alcohol"),
                "medication" to supported.contains("medication"),
                "allergy" to profile.allergies.isNotEmpty(),
                "sodium" to (supported.contains("sodium") || supported.contains("salt")),
                "salt" to (supported.contains("sodium") || supported.contains("salt")),
                "sugar" to (supported.contains("sugar") || supported.contains("glycemic")),
                "gluten intolerance" to supported.contains("gluten"),
            )
            return blocked.none { (term, allowed) -> lower.contains(term) && !allowed }
        }

        val cleanedReasons = parsed.mainReasons.filter(::isSupported).take(3)
        val cleanedCautions = parsed.cautions.filter(::isSupported).take(2)
        val cleanedSummary = if (parsed.summary.isNotBlank() && isSupported(parsed.summary)) {
            parsed.summary
        } else {
            "This appears generally acceptable based on the provided inputs."
        }

        return parsed.copy(
            summary = cleanedSummary,
            mainReasons = cleanedReasons,
            cautions = cleanedCautions,
        )
    }

    private fun applySafetyOverride(
        parsed: AnalysisResult,
        profile: UserProfile,
        ingredients: List<String>,
    ): AnalysisResult {
        val flags = evaluateRules(profile, ingredients)
        var suitability = parsed.suitability
        var mainReasons = parsed.mainReasons.toMutableList()
        var cautions = parsed.cautions.toMutableList()
        var betterOptions = parsed.betterOptions.toMutableList()

        if (flags.allergyConflicts.isNotEmpty()) {
            suitability = "avoid"
            flags.allergyConflicts.reversed().forEach {
                if (it !in mainReasons) mainReasons.add(0, it)
            }
            if ("Avoid due to allergy risk" !in cautions) cautions.add(0, "Avoid due to allergy risk")
        } else {
            val order = mapOf("good" to 0, "moderate" to 1, "poor" to 2, "avoid" to 3)
            if ((order[flags.suggestedSuitability] ?: 0) > (order[suitability] ?: 0)) {
                suitability = flags.suggestedSuitability
            }
            flags.conditionConflicts.forEach {
                if (it !in mainReasons) mainReasons += it
            }
        }

        if (mainReasons.isEmpty()) {
            if (flags.conditionConflicts.isNotEmpty()) {
                mainReasons = flags.conditionConflicts.take(3).toMutableList()
            } else {
                mainReasons = mutableListOf("No strong direct conflicts were found for the provided profile and ingredients.")
            }
        }

        if (betterOptions.isEmpty()) {
            betterOptions = when (suitability) {
                "avoid" -> if (profile.allergies.any { it.contains("peanut", ignoreCase = true) }) {
                    mutableListOf("Choose a peanut-free alternative with lower sugar and lower sodium")
                } else {
                    mutableListOf("Choose a simpler, minimally processed alternative that better matches the health profile")
                }
                "poor" -> mutableListOf("Choose lower-sugar and lower-sodium alternatives")
                "moderate" -> mutableListOf("Prefer less processed options with lower sugar and salt")
                else -> mutableListOf("Maintain balanced portions and prefer minimally processed foods")
            }
        }

        return parsed.copy(
            suitability = suitability,
            mainReasons = mainReasons.take(3),
            cautions = cautions.take(2),
            betterOptions = betterOptions.take(2),
        )
    }
}
