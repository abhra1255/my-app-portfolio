package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyllabusDao {

    // --- Syllabus Queries ---
    @Query("SELECT * FROM syllabi ORDER BY uploadedDate DESC")
    fun getAllSyllabi(): Flow<List<SyllabusEntity>>

    @Query("SELECT * FROM syllabi WHERE id = :id LIMIT 1")
    fun getSyllabusById(id: Long): Flow<SyllabusEntity?>

    @Query("SELECT * FROM syllabi WHERE id = :id LIMIT 1")
    suspend fun getSyllabusByIdOneShot(id: Long): SyllabusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyllabus(syllabus: SyllabusEntity): Long

    @Query("UPDATE syllabi SET overallProgress = :progress WHERE id = :id")
    suspend fun updateSyllabusProgress(id: Long, progress: Float)

    @Query("DELETE FROM syllabi WHERE id = :id")
    suspend fun deleteSyllabusById(id: Long)

    // --- Module Queries ---
    @Query("SELECT * FROM modules WHERE syllabusId = :syllabusId ORDER BY number ASC")
    fun getModulesForSyllabus(syllabusId: Long): Flow<List<ModuleEntity>>

    @Query("SELECT * FROM modules WHERE syllabusId = :syllabusId ORDER BY number ASC")
    suspend fun getModulesForSyllabusOneShot(syllabusId: Long): List<ModuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModule(module: ModuleEntity): Long

    @Query("UPDATE modules SET moduleProgress = :progress WHERE id = :id")
    suspend fun updateModuleProgress(id: Long, progress: Float)

    @Query("DELETE FROM modules WHERE syllabusId = :syllabusId")
    suspend fun deleteModulesBySyllabusId(syllabusId: Long)

    // --- Topic Queries ---
    @Query("SELECT * FROM topics WHERE moduleId = :moduleId ORDER BY id ASC")
    fun getTopicsForModule(moduleId: Long): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE moduleId = :moduleId ORDER BY id ASC")
    suspend fun getTopicsForModuleOneShot(moduleId: Long): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE syllabusId = :syllabusId")
    fun getTopicsForSyllabus(syllabusId: Long): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE syllabusId = :syllabusId")
    suspend fun getTopicsForSyllabusOneShot(syllabusId: Long): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: Long): TopicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: TopicEntity): Long

    @Update
    suspend fun updateTopic(topic: TopicEntity)

    @Query("DELETE FROM topics WHERE syllabusId = :syllabusId")
    suspend fun deleteTopicsBySyllabusId(syllabusId: Long)

    // --- Doubt Queries ---
    @Query("SELECT * FROM doubts WHERE topicId = :topicId ORDER BY timestamp ASC")
    fun getDoubtsForTopic(topicId: Long): Flow<List<DoubtEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoubt(doubt: DoubtEntity): Long

    // --- Quiz Queries ---
    @Query("SELECT * FROM quizzes WHERE moduleId = :moduleId")
    fun getQuizzesForModule(moduleId: Long): Flow<List<QuizEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity): Long

    @Query("UPDATE quizzes SET userAnswer = :userAnswer WHERE id = :quizId")
    suspend fun updateQuizAnswer(quizId: Long, userAnswer: String)

    @Query("DELETE FROM quizzes WHERE moduleId = :moduleId")
    suspend fun deleteQuizzesByModuleId(moduleId: Long)

    // --- PCQ (Previous Year Questions) ---
    @Query("SELECT * FROM pcqs WHERE syllabusId = :syllabusId")
    fun getPcqsForSyllabus(syllabusId: Long): Flow<List<PcqEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPcq(pcq: PcqEntity): Long

    @Query("DELETE FROM pcqs WHERE syllabusId = :syllabusId")
    suspend fun deletePcqsBySyllabusId(syllabusId: Long)

    @Transaction
    suspend fun deleteFullSyllabusCascade(syllabusId: Long) {
        val modules = getModulesForSyllabusOneShot(syllabusId)
        for (module in modules) {
            deleteQuizzesByModuleId(module.id)
        }
        deleteTopicsBySyllabusId(syllabusId)
        deleteModulesBySyllabusId(syllabusId)
        deletePcqsBySyllabusId(syllabusId)
        deleteSyllabusById(syllabusId)
    }
}
