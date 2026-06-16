package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.SyllabusRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StudyViewModel(
    application: Application,
    private val repository: SyllabusRepository
) : AndroidViewModel(application) {

    // --- State Variables ---
    val syllabi: StateFlow<List<SyllabusEntity>> = repository.allSyllabi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSyllabusId = MutableStateFlow<Long?>(null)
    val selectedSyllabusId: StateFlow<Long?> = _selectedSyllabusId.asStateFlow()

    val currentSyllabus: StateFlow<SyllabusEntity?> = _selectedSyllabusId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getSyllabusById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentModules: StateFlow<List<ModuleEntity>> = _selectedSyllabusId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getModulesForSyllabus(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentTopics: StateFlow<List<TopicEntity>> = _selectedSyllabusId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getTopicsForSyllabus(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTopicId = MutableStateFlow<Long?>(null)
    val activeTopicId: StateFlow<Long?> = _activeTopicId.asStateFlow()

    val activeTopic: StateFlow<TopicEntity?> = _activeTopicId
        .map { id ->
            if (id == null) null
            else currentTopics.value.find { it.id == id }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val doubtHistory: StateFlow<List<DoubtEntity>> = _activeTopicId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getDoubtsForTopic(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeModuleId = MutableStateFlow<Long?>(null)
    val activeModuleId: StateFlow<Long?> = _activeModuleId.asStateFlow()

    val activeQuizzes: StateFlow<List<QuizEntity>> = _activeModuleId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getQuizzesForModule(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pcqList: StateFlow<List<PcqEntity>> = _selectedSyllabusId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getPcqsForSyllabus(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Loading and Error Indicators ---
    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _quizLoading = MutableStateFlow(false)
    val quizLoading: StateFlow<Boolean> = _quizLoading.asStateFlow()

    private val _pcqLoading = MutableStateFlow(false)
    val pcqLoading: StateFlow<Boolean> = _pcqLoading.asStateFlow()

    private val _doubtSolving = MutableStateFlow(false)
    val doubtSolving: StateFlow<Boolean> = _doubtSolving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Actions ---

    fun selectSyllabus(id: Long) {
        _selectedSyllabusId.value = id
        _activeModuleId.value = null
        _activeTopicId.value = null
    }

    fun selectModule(id: Long) {
        _activeModuleId.value = id
    }

    fun selectTopic(id: Long) {
        _activeTopicId.value = id
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Extracts study modules, topics and internet links from a syllabus raw text using Gemini AI
     */
    fun uploadAndExtractSyllabus(
        text: String,
        examDate: String,
        hours: Int,
        source: String,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            _isExtracting.value = true
            _errorMessage.value = null
            try {
                val dbId = repository.extractAndSaveSyllabus(
                    rawSyllabusText = text,
                    examDate = examDate,
                    studyHoursPerDay = hours,
                    sourceType = source
                )
                _selectedSyllabusId.value = dbId
                onSuccess(dbId)
            } catch (e: Exception) {
                Log.e("StudyViewModel", "Syllabus Extraction Error", e)
                _errorMessage.value = "Failed to parse study syllabus: ${e.message}"
            } finally {
                _isExtracting.value = false
            }
        }
    }

    /**
     * Updates study completion status of a topic
     */
    fun toggleTopicStatus(topicId: Long, completed: Boolean) {
        viewModelScope.launch {
            try {
                val nextStatus = if (completed) "COMPLETED" else "PENDING"
                repository.updateTopicStatus(topicId, nextStatus)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update study status"
            }
        }
    }

    /**
     * Loads or generates quizzes dynamically via AI for the active module
     */
    fun generateOrLoadQuizzes(moduleId: Long, title: String, desc: String, subject: String) {
        viewModelScope.launch {
            _activeModuleId.value = moduleId
            _quizLoading.value = true
            _errorMessage.value = null
            try {
                // Read from DB first
                val existing = repository.getQuizzesForModule(moduleId).first()
                if (existing.isEmpty()) {
                    repository.generateModuleQuizzes(moduleId, title, desc, subject)
                }
            } catch (e: Exception) {
                Log.e("StudyViewModel", "Quiz Generation Error", e)
                _errorMessage.value = "Failed to generate dynamic study questions: ${e.message}"
            } finally {
                _quizLoading.value = false
            }
        }
    }

    /**
     * Submits and registers an answer on a study quiz item
     */
    fun selectQuizAnswer(quizId: Long, selectedAnswer: String, daoId: Long) {
        viewModelScope.launch {
            try {
                repository.submitQuizAnswer(quizId, selectedAnswer, daoId)
            } catch (e: Exception) {
                _errorMessage.value = "Unable to process study answer"
            }
        }
    }

    /**
     * Loads Previous Year Question analysis from DB or triggers AI study prediction
     */
    fun loadOrGeneratePcqAnalysis(syllabusId: Long, title: String, subject: String) {
        viewModelScope.launch {
            _pcqLoading.value = true
            _errorMessage.value = null
            try {
                val existing = repository.getPcqsForSyllabus(syllabusId).first()
                if (existing.isEmpty()) {
                    repository.generatePcqAnalysisForSyllabus(syllabusId, title, subject)
                }
            } catch (e: Exception) {
                Log.e("StudyViewModel", "Exam Prediction Error", e)
                _errorMessage.value = "Could not verify previous-year paper targets: ${e.message}"
            } finally {
                _pcqLoading.value = false
            }
        }
    }

    /**
     * Submits a study doubt query to be answered context-aware by the AI
     */
    fun submitDoubtQuery(topicId: Long, question: String, topicTitle: String, topicDesc: String) {
        viewModelScope.launch {
            _doubtSolving.value = true
            try {
                repository.resolveStudyDoubt(topicId, question, topicTitle, topicDesc)
            } catch (e: Exception) {
                _errorMessage.value = "Doubt Solver currently offline: ${e.message}"
            } finally {
                _doubtSolving.value = false
            }
        }
    }

    /**
     * Deletes a syllabus and all nested entities
     */
    fun deleteSyllabusCascade(syllabusId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteSyllabusCascade(syllabusId)
                if (_selectedSyllabusId.value == syllabusId) {
                    _selectedSyllabusId.value = null
                    _activeModuleId.value = null
                    _activeTopicId.value = null
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete syllabus"
            }
        }
    }

    /**
     * Parses resources helper
     */
    fun getTopicResources(topic: TopicEntity): List<StudyResource> {
        return repository.parseResourcesJson(topic.resourcesJson)
    }

    // --- Factory ---
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
                val database = StudyDatabase.getDatabase(application)
                val repository = SyllabusRepository(database.syllabusDao())
                @Suppress("UNCHECKED_CAST")
                return StudyViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
