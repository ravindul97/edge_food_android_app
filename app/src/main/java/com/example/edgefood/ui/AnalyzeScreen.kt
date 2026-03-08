package com.example.edgefood.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.example.edgefood.MainViewModel
import com.example.edgefood.model.AnalysisResult

@Composable
fun AnalyzeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Analyze Ingredients", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (state.modelReady) "Local model is ready." else "Local model is still loading or failed.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = state.ingredientsText,
                onValueChange = viewModel::updateIngredients,
                label = { Text("Ingredients (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )

            Button(
                onClick = { viewModel.analyze() },
                enabled = state.modelReady && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                if (state.isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Analyse")
            }

            TextButton(onClick = onBack) {
                Text("Back to profile")
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.result?.let { ResultCard(it) }
        }
    }
}

@Composable
private fun ResultCard(result: AnalysisResult) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AssistChip(onClick = {}, label = { Text("Suitability: ${result.suitability}") })
            Text(result.summary, style = MaterialTheme.typography.bodyLarge)

            if (result.mainReasons.isNotEmpty()) {
                Text("Main reasons", style = MaterialTheme.typography.titleMedium)
                result.mainReasons.forEach { Text("• $it") }
            }

            if (result.cautions.isNotEmpty()) {
                Text("Cautions", style = MaterialTheme.typography.titleMedium)
                result.cautions.forEach { Text("• $it") }
            }

            if (result.betterOptions.isNotEmpty()) {
                Text("Better options", style = MaterialTheme.typography.titleMedium)
                result.betterOptions.forEach { Text("• $it") }
            }
        }
    }
}
