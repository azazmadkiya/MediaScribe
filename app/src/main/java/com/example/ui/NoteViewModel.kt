package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiRepository
import com.example.data.AppDatabase
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    private val geminiRepository = GeminiRepository()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _targetLanguage = MutableStateFlow("English")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _translateEnabled = MutableStateFlow(true)
    val translateEnabled: StateFlow<Boolean> = _translateEnabled.asStateFlow()

    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val notes: StateFlow<List<NoteEntity>>

    init {
        val noteDao = AppDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(noteDao)

        notes = combine(
            repository.allNotes,
            _searchQuery,
            _selectedCategory
        ) { allNotes, query, category ->
            allNotes.filter { note ->
                val matchesQuery = query.isBlank() ||
                        note.title.contains(query, ignoreCase = true) ||
                        note.content.contains(query, ignoreCase = true) ||
                        note.tags.contains(query, ignoreCase = true)

                val matchesCategory = category == "All" || note.sourceType.equals(category, ignoreCase = true)

                matchesQuery && matchesCategory
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setTargetLanguage(language: String) {
        _targetLanguage.value = language
    }

    fun setTranslateEnabled(enabled: Boolean) {
        _translateEnabled.value = enabled
    }

    /**
     * Validates and saves the API key format before saving.
     * Verification rules:
     * - Must not be blank.
     * - Must start with "AIza".
     * - Must have a valid length (>= 35 characters).
     */
    fun validateAndSaveApiKey(key: String): Pair<Boolean, String> {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            return Pair(false, "API key cannot be empty.")
        }
        if (!trimmed.startsWith("AIza")) {
            return Pair(false, "Invalid format: Gemini API key must start with 'AIza'.")
        }
        if (trimmed.length < 35) {
            return Pair(false, "Invalid length: Gemini API key is too short (must be at least 35 characters).")
        }
        _customApiKey.value = trimmed
        return Pair(true, "API key format is valid and saved successfully!")
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun insertNote(title: String, content: String, sourceType: String, sourceUrl: String? = null, tags: String = "") {
        viewModelScope.launch {
            repository.insertNote(
                NoteEntity(
                    title = title,
                    content = content,
                    sourceType = sourceType,
                    sourceUrl = sourceUrl,
                    tags = tags
                )
            )
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun extractFromYouTube(url: String, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = geminiRepository.extractFromYouTube(url, _targetLanguage.value, _translateEnabled.value, _customApiKey.value)
            _isLoading.value = false

            result.onSuccess { (title, content) ->
                val id = repository.insertNote(
                    NoteEntity(
                        title = title,
                        content = content,
                        sourceType = "YouTube",
                        sourceUrl = url,
                        tags = "YouTube, ${_targetLanguage.value}"
                    )
                )
                onComplete(id)
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to extract from YouTube"
            }
        }
    }

    fun transcribeMedia(mimeType: String, base64Data: String, fileName: String, sourceType: String, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = geminiRepository.transcribeMedia(mimeType, base64Data, fileName, _targetLanguage.value, _translateEnabled.value, _customApiKey.value)
            _isLoading.value = false

            result.onSuccess { (title, content) ->
                val id = repository.insertNote(
                    NoteEntity(
                        title = title,
                        content = content,
                        sourceType = sourceType,
                        sourceUrl = fileName,
                        tags = "$sourceType, ${_targetLanguage.value}"
                    )
                )
                onComplete(id)
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to transcribe media"
            }
        }
    }

    fun refineNoteWithAI(note: NoteEntity, onRefined: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = geminiRepository.refineWithHighThinking(note.content, _targetLanguage.value, _translateEnabled.value, _customApiKey.value)
            _isLoading.value = false

            result.onSuccess { refinedContent ->
                val updated = note.copy(content = refinedContent)
                repository.updateNote(updated)
                onRefined(refinedContent)
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to refine with AI"
            }
        }
    }
}
