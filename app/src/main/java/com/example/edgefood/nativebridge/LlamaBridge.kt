package com.example.edgefood.nativebridge

object LlamaBridge {
    init {
        System.loadLibrary("edgefood")
    }

    external fun initModel(modelPath: String, nThreads: Int = 4, contextSize: Int = 1024): Boolean
    external fun analyze(prompt: String, maxTokens: Int = 160): String
    external fun releaseModel()
}
