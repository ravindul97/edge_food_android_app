package com.example.edgefood.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.example.edgefood.MainViewModel
import com.example.edgefood.model.UserProfile

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onContinue: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    var age by remember(state.profile.age) { mutableStateOf(state.profile.age.toString()) }
    var gender by remember(state.profile.gender) { mutableStateOf(state.profile.gender) }
    var allergies by remember(state.profile.allergies) { mutableStateOf(state.profile.allergies.joinToString(", ")) }
    var conditions by remember(state.profile.conditions) { mutableStateOf(state.profile.conditions.joinToString(", ")) }
    var diet by remember(state.profile.dietPreferences) { mutableStateOf(state.profile.dietPreferences.joinToString(", ")) }
    var medications by remember(state.profile.medications) { mutableStateOf(state.profile.medications.joinToString(", ")) }
    var goal by remember(state.profile.goal) { mutableStateOf(state.profile.goal) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Profile", style = MaterialTheme.typography.headlineMedium)
            Text("Save the user profile locally in the app.", style = MaterialTheme.typography.bodyMedium)

            InputCard {
                OutlinedTextField(age, { age = it }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(gender, { gender = it }, label = { Text("Gender") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(allergies, { allergies = it }, label = { Text("Allergies (comma separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(conditions, { conditions = it }, label = { Text("Conditions (comma separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(diet, { diet = it }, label = { Text("Diet preferences (comma separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(medications, { medications = it }, label = { Text("Medications (comma separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(goal, { goal = it }, label = { Text("Goal") }, modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = {
                    viewModel.saveProfile(
                        UserProfile(
                            age = age.toIntOrNull() ?: 25,
                            gender = gender,
                            allergies = splitCsv(allergies),
                            conditions = splitCsv(conditions),
                            dietPreferences = splitCsv(diet),
                            medications = splitCsv(medications),
                            goal = goal,
                        )
                    )
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text("Save Profile")
            }
        }
    }
}

@Composable
private fun InputCard(content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun splitCsv(text: String): List<String> =
    text.split(",").map { it.trim() }.filter { it.isNotBlank() }
