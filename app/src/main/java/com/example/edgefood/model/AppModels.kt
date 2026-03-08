package com.example.edgefood.model

data class UserProfile(
    val age: Int = 25,
    val gender: String = "unknown",
    val allergies: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    val dietPreferences: List<String> = emptyList(),
    val medications: List<String> = emptyList(),
    val goal: String = "general healthy eating",
)

data class AnalysisResult(
    val suitability: String = "moderate",
    val summary: String = "No clear summary generated.",
    val mainReasons: List<String> = emptyList(),
    val cautions: List<String> = emptyList(),
    val betterOptions: List<String> = emptyList(),
)

data class RagDoc(
    val id: String,
    val title: String,
    val text: String,
    val tags: List<String>,
)

data class RuleConfig(
    val highSugarTerms: List<String>,
    val highSodiumTerms: List<String>,
    val highSatFatTerms: List<String>,
)

data class RuleFlags(
    val allergyConflicts: List<String> = emptyList(),
    val conditionConflicts: List<String> = emptyList(),
    val suggestedSuitability: String = "good",
)
