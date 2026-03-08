package com.example.edgefood.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.edgefood.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "user_profile")

class ProfileRepository(private val context: Context) {

    private object Keys {
        val AGE = intPreferencesKey("age")
        val GENDER = stringPreferencesKey("gender")
        val ALLERGIES = stringPreferencesKey("allergies")
        val CONDITIONS = stringPreferencesKey("conditions")
        val DIET = stringPreferencesKey("diet")
        val MEDICATIONS = stringPreferencesKey("medications")
        val GOAL = stringPreferencesKey("goal")
    }

    val profileFlow: Flow<UserProfile> = context.profileDataStore.data.map { prefs ->
        UserProfile(
            age = prefs[Keys.AGE] ?: 25,
            gender = prefs[Keys.GENDER] ?: "unknown",
            allergies = prefs[Keys.ALLERGIES].orEmpty().splitCsv(),
            conditions = prefs[Keys.CONDITIONS].orEmpty().splitCsv(),
            dietPreferences = prefs[Keys.DIET].orEmpty().splitCsv(),
            medications = prefs[Keys.MEDICATIONS].orEmpty().splitCsv(),
            goal = prefs[Keys.GOAL] ?: "general healthy eating",
        )
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.profileDataStore.edit { prefs ->
            prefs[Keys.AGE] = profile.age
            prefs[Keys.GENDER] = profile.gender
            prefs[Keys.ALLERGIES] = profile.allergies.joinToString(",")
            prefs[Keys.CONDITIONS] = profile.conditions.joinToString(",")
            prefs[Keys.DIET] = profile.dietPreferences.joinToString(",")
            prefs[Keys.MEDICATIONS] = profile.medications.joinToString(",")
            prefs[Keys.GOAL] = profile.goal
        }
    }

    private fun String.splitCsv(): List<String> =
        split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
}
