package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Content
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.local.*
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SyllabusRepository(private val syllabusDao: SyllabusDao) {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // --- Local DB Streams ---
    val allSyllabi: Flow<List<SyllabusEntity>> = syllabusDao.getAllSyllabi()

    fun getSyllabusById(id: Long): Flow<SyllabusEntity?> = syllabusDao.getSyllabusById(id)

    fun getModulesForSyllabus(syllabusId: Long): Flow<List<ModuleEntity>> =
        syllabusDao.getModulesForSyllabus(syllabusId)

    fun getTopicsForModule(moduleId: Long): Flow<List<TopicEntity>> =
        syllabusDao.getTopicsForModule(moduleId)

    fun getTopicsForSyllabus(syllabusId: Long): Flow<List<TopicEntity>> =
        syllabusDao.getTopicsForSyllabus(syllabusId)

    fun getDoubtsForTopic(topicId: Long): Flow<List<DoubtEntity>> =
        syllabusDao.getDoubtsForTopic(topicId)

    fun getQuizzesForModule(moduleId: Long): Flow<List<QuizEntity>> =
        syllabusDao.getQuizzesForModule(moduleId)

    fun getPcqsForSyllabus(syllabusId: Long): Flow<List<PcqEntity>> =
        syllabusDao.getPcqsForSyllabus(syllabusId)

    // --- Status and Progression updates ---
    suspend fun updateTopicStatus(topicId: Long, newStatus: String) = withContext(Dispatchers.IO) {
        val topic = syllabusDao.getTopicById(topicId) ?: return@withContext
        val updatedTopic = topic.copy(status = newStatus)
        syllabusDao.updateTopic(updatedTopic)

        // Trigger updates for Module progress & overall Syllabus progress
        recalculateProgressForSyllabus(topic.syllabusId)
    }

    suspend fun submitQuizAnswer(quizId: Long, answer: String, quizDaoId: Long) = withContext(Dispatchers.IO) {
        syllabusDao.updateQuizAnswer(quizDaoId, answer)
    }

    suspend fun deleteSyllabusCascade(syllabusId: Long) = withContext(Dispatchers.IO) {
        syllabusDao.deleteFullSyllabusCascade(syllabusId)
    }

    private suspend fun recalculateProgressForSyllabus(syllabusId: Long) {
        val allTopics = syllabusDao.getTopicsForSyllabusOneShot(syllabusId)
        val modules = syllabusDao.getModulesForSyllabusOneShot(syllabusId)

        if (allTopics.isEmpty()) return

        // Recalculate Module Progresses
        for (module in modules) {
            val moduleTopics = allTopics.filter { it.moduleId == module.id }
            if (moduleTopics.isNotEmpty()) {
                val completedCount = moduleTopics.count { it.status == "COMPLETED" }
                val progress = (completedCount.toFloat() / moduleTopics.size.toFloat()) * 100f
                syllabusDao.updateModuleProgress(module.id, progress)
            }
        }

        // Recalculate overall Syllabus Progress
        val completedTopicsCount = allTopics.count { it.status == "COMPLETED" }
        val overallSyllabusProgress = (completedTopicsCount.toFloat() / allTopics.size.toFloat()) * 100f
        syllabusDao.updateSyllabusProgress(syllabusId, overallSyllabusProgress)
    }

    // --- Gemini AI Core Methods ---

    /**
     * Extracts full modules, topics, and study resources from a syllabus text.
     */
    suspend fun extractAndSaveSyllabus(
        rawSyllabusText: String,
        examDate: String,
        studyHoursPerDay: Int,
        sourceType: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            Log.e("SyllabusRepository", "API key is default or empty! Please configure it.")
        }

        val prompt = """
            You are SyllabusAI, a premium educational curriculum parser. Parse the following college syllabus raw text and extract clear subjects, chapters/modules, and study topics.
            
            Based on the exam date ($examDate) and the user's available study time ($studyHoursPerDay hours per day), organize the syllabus into modules with high-quality curated study resources for each topic.
            
            Find and specify realistic, high-quality, actual study resource details across the internet:
            - YouTube playlists / individual videos: Recommend true legendary teachers by name (e.g., Abdul Bari, Jenny's Lectures, Neso Academy, MyCodeschool, MIT OpenCourseWare, Khan Academy, freeCodeCamp, etc.) based on the subject matter.
            - NPTEL Course links (e.g. nptel.ac.in courses), Reddit revision notes, Quora Q&As, GitHub code repositories, and prominent educational websites (e.g., GeeksforGeeks, TutorialsPoint).
            - Filter out useless, low-quality, or bloated search results. Prioritize concise, student-proven content.
            - Assign a 'rank' (1 to 5, where 1 is the primary/best match) and summarize reviews, views, or feedback (e.g., "Student favorite for simple examples" or "Best for university exam theory").
            
            Return ONLY a valid JSON object matching this exact Kotlin class schema:
            ```json
            {
              "title": "[A short catchy name of the Syllabus, e.g., Data Structures and Algorithms - Semester IV]",
              "subject": "[Subject name, e.g., Computer Science / Organic Chemistry / Calculus]",
              "modules": [
                {
                  "number": 1,
                  "title": "[Title of Module, e.g., Module 1: Introduction to Trees]",
                  "description": "[A concise explanation of what they will study]",
                  "topics": [
                    {
                      "title": "[Topic Title, e.g., Binary Search Trees]",
                      "description": "[Topic brief overview, e.g., BST properties, insertion, deletion operations]",
                      "resources": [
                        {
                          "title": "[YouTube: Abdul Bari Binary Search Tree Playlist]",
                          "url": "https://www.youtube.com/results?search_query=abdul+bari+binary+search+tree",
                          "platform": "YouTube Playlist",
                          "creator": "Abdul Bari",
                          "rank": 1,
                          "reviews": "9.8/10 rating by 150+ reviews on Reddit. Incredibly clear animations.",
                          "description": "Essential tutorial series covering tree structures visually."
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            ```
            
            Guidelines:
            - Return ONLY valid JSON, no trailing commas, no extra words, no introductory lines, wrapped in code blocks or plain text.
            - Make sure you curate relevant resources for every topic! Do not leave resources empty.
            
            Here is the raw syllabus input representing college material:
            $rawSyllabusText
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json")
        )

        val rawResponse = RetrofitClient.service.generateContent(key, request)
        val textResponse = rawResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from AI engine")

        val sanitizedJson = sanitizeJsonBlock(textResponse)
        val extractionAdapter = moshi.adapter(SyllabusExtractionResponse::class.java)
        val extractedData = extractionAdapter.fromJson(sanitizedJson)
            ?: throw Exception("Could not parse AI response into Syllabus extraction format")

        // Save to Database
        val syllabusId = syllabusDao.insertSyllabus(
            SyllabusEntity(
                title = extractedData.title,
                subject = extractedData.subject,
                uploadedDate = System.currentTimeMillis(),
                examDate = examDate,
                studyHoursPerDay = studyHoursPerDay,
                syllabusSource = sourceType ?: "Uploaded Text"
            )
        )

        for (netModule in extractedData.modules) {
            val moduleId = syllabusDao.insertModule(
                ModuleEntity(
                    syllabusId = syllabusId,
                    number = netModule.number,
                    title = netModule.title,
                    description = netModule.description
                )
            )

            for (netTopic in netModule.topics) {
                val studyResources = netTopic.resources.map {
                    StudyResource(
                        title = it.title,
                        url = it.url,
                        platform = it.platform,
                        creator = it.creator,
                        rank = it.rank,
                        reviews = it.reviews,
                        description = it.description
                    )
                }
                val resourcesJsonStr = moshi.adapter(List::class.java).toJson(studyResources)

                syllabusDao.insertTopic(
                    TopicEntity(
                        moduleId = moduleId,
                        syllabusId = syllabusId,
                        title = netTopic.title,
                        description = netTopic.description,
                        resourcesJson = resourcesJsonStr
                    )
                )
            }
        }

        syllabusId
    }

    /**
     * Generates a topic quiz with options, correct answer indicator, and explanations for a module.
     */
    suspend fun generateModuleQuizzes(moduleId: Long, moduleTitle: String, description: String, subjectName: String) = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        val prompt = """
            You are SyllabusAI's Smart Quiz Engine. Create 5 engaging multiple-choice questions (MCQs) for the university syllabus topic: "$moduleTitle".
            Module description: $description
            Syllabus Subject: $subjectName
            
            Return ONLY a valid JSON object matching this schema:
            {
              "quizzes": [
                {
                  "question": "[Standard exam question statement]",
                  "optionA": "[Option A option]",
                  "optionB": "[Option B option]",
                  "optionC": "[Option C option]",
                  "optionD": "[Option D option]",
                  "correctAnswer": "[A, B, C, or D]",
                  "explanation": "[Detailed academic explanation of why this answer is correct]"
                }
              ]
            }
            Ensure answer choices are tricky but scientifically accurate. Only return JSON.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json")
        )

        val rawResponse = RetrofitClient.service.generateContent(key, request)
        val textResponse = rawResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response received from quiz generation")

        val sanitizedJson = sanitizeJsonBlock(textResponse)
        val responseAdapter = moshi.adapter(QuizListResponse::class.java)
        val extractedQuizzes = responseAdapter.fromJson(sanitizedJson) ?: return@withContext

        for (q in extractedQuizzes.quizzes) {
            syllabusDao.insertQuiz(
                QuizEntity(
                    moduleId = moduleId,
                    question = q.question,
                    optionA = q.optionA,
                    optionB = q.optionB,
                    optionC = q.optionC,
                    optionD = q.optionD,
                    correctAnswer = q.correctAnswer,
                    explanation = q.explanation
                )
            )
        }
    }

    /**
     * Generates Previous Year Question priority analysis and expected questions list.
     */
    suspend fun generatePcqAnalysisForSyllabus(syllabusId: Long, syllabusTitle: String, subjectName: String) = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        val prompt = """
            You are SyllabusAI's previous-year exam question validator. Analyze typical previous exam papers for the university subject: "$syllabusTitle" ($subjectName).
            Identify key high-priority topics most likely to appear on future papers. Predict core exam concepts, frequency, and prioritize them (HIGH, MEDIUM, LOW).
            
            Return ONLY a JSON matching:
            {
              "analyses": [
                {
                  "topicTitle": "[Concrete exam topic name]",
                  "analysis": "[Review of previous years papers, typical questions asked, and critical insights]",
                  "importance": "[HIGH, MEDIUM, or LOW]"
                }
              ]
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json")
        )

        val rawResponse = RetrofitClient.service.generateContent(key, request)
        val textResponse = rawResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response for previous year papers analysis")

        val sanitizedJson = sanitizeJsonBlock(textResponse)
        val responseAdapter = moshi.adapter(PcqListResponse::class.java)
        val extractedPcqs = responseAdapter.fromJson(sanitizedJson) ?: return@withContext

        // Clear existing first
        syllabusDao.deletePcqsBySyllabusId(syllabusId)

        for (p in extractedPcqs.analyses) {
            syllabusDao.insertPcq(
                PcqEntity(
                    syllabusId = syllabusId,
                    topicTitle = p.topicTitle,
                    analysis = p.analysis,
                    importance = p.importance
                )
            )
        }
    }

    /**
     * Answers student study doubts instantly context-aware of the topic.
     */
    suspend fun resolveStudyDoubt(
        topicId: Long,
        question: String,
        topicTitle: String,
        topicDesc: String
    ): String = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        val prompt = """
            You are SyllabusAI's personal 24/7 tutor assistant. Answer the student's concept study doubt in a clear, academic but easy-to-understand student-friendly way.
            Topic they are studying: "$topicTitle"
            Topic Context: $topicDesc
            
            Student Question: "$question"
            
            Provide deep explanation. Break it down using formulas, analogies, step-by-step logical chains, and example scenarios. Make sure it is completely complete and direct.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        val rawResponse = RetrofitClient.service.generateContent(key, request)
        val answer = rawResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: "Apologies, I couldn't process your question at this moment. Please double check your internet and try again."

        syllabusDao.insertDoubt(
            DoubtEntity(
                topicId = topicId,
                question = question,
                answer = answer,
                timestamp = System.currentTimeMillis()
            )
        )

        answer
    }

    /**
     * Helper to parse StudyResource list from stored topic entity strings.
     */
    fun parseResourcesJson(resourcesJson: String): List<StudyResource> {
        return try {
            val listType = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                .adapter(List::class.java)
            val rawList = listType.fromJson(resourcesJson) as? List<Map<String, Any>> ?: return emptyList()

            rawList.map {
                StudyResource(
                    title = it["title"] as? String ?: "",
                    url = it["url"] as? String ?: "",
                    platform = it["platform"] as? String ?: "Other",
                    creator = it["creator"] as? String ?: "",
                    rank = (it["rank"] as? Double)?.toInt() ?: 1,
                    reviews = it["reviews"] as? String ?: "",
                    description = it["description"] as? String ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e("SyllabusRepository", "Error parsing resources json", e)
            emptyList()
        }
    }

    private fun sanitizeJsonBlock(responseStr: String): String {
        var str = responseStr.trim()
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
            if (str.endsWith("```")) {
                str = str.removeSuffix("```")
            }
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```")
            if (str.endsWith("```")) {
                str = str.removeSuffix("```")
            }
        }
        return str.trim()
    }
}
