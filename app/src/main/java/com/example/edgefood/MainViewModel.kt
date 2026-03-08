package com.example.edgefood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.edgefood.data.AssetRepository
import com.example.edgefood.data.ProfileRepository
import com.example.edgefood.model.AnalysisResult
import com.example.edgefood.model.UserProfile
import com.example.edgefood.nativebridge.LlamaBridge
import com.example.edgefood.util.Analyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val profile: UserProfile = UserProfile(),
    val ingredientsText: String = "",
    val result: AnalysisResult? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val modelReady: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository(application)
    private val assetRepository = AssetRepository(application)
    private val analyzer = Analyzer(
        ragDocs = assetRepository.loadRagDocs(),
        ruleConfig = assetRepository.loadRuleConfig(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.profileFlow.collect { profile ->
                _uiState.value = _uiState.value.copy(profile = profile)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val modelFile = assetRepository.ensureModelCopied()
                val ok = LlamaBridge.initModel(modelFile.absolutePath, 4, 1024)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        modelReady = ok,
                        error = if (ok) null else "Failed to initialize the local model."
                    )
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(error = it.message ?: "Model init failed")
                }
            }
        }
    }

    fun updateIngredients(text: String) {
        _uiState.value = _uiState.value.copy(ingredientsText = text)
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            profileRepository.saveProfile(profile)
        }
    }

    fun analyze() {
        val ingredients = _uiState.value.ingredientsText
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (ingredients.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Enter at least one ingredient.")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val prompt = analyzer.buildPrompt(_uiState.value.profile, ingredients)
                val raw = LlamaBridge.analyze(prompt, 180)
                analyzer.parseAndValidate(raw, _uiState.value.profile, ingredients)
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        result = result,
                        error = null,
                    )
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = it.message ?: "Analysis failed"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        LlamaBridge.releaseModel()
    }
}
