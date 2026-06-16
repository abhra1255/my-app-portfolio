package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "syllabi")
data class SyllabusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val uploadedDate: Long,
    val examDate: String,
    val studyHoursPerDay: Int,
    val overallProgress: Float = 0f,
    val syllabusSource: String? = null // Link or file path
)

@Entity(tableName = "modules")
data class ModuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syllabusId: Long,
    val number: Int,
    val title: String,
    val description: String,
    val moduleProgress: Float = 0f
)

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moduleId: Long,
    val syllabusId: Long,
    val title: String,
    val description: String,
    val status: String = "PENDING", // PENDING, IN_PROGRESS, COMPLETED
    val resourcesJson: String // Serialized list of StudyResource
)

@Entity(tableName = "doubts")
data class DoubtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: Long,
    val question: String,
    val answer: String,
    val timestamp: Long
)

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moduleId: Long,
    val question: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String, // A, B, C, D
    val explanation: String,
    val userAnswer: String? = null // Null if not taken
)

@Entity(tableName = "pcqs")
data class PcqEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syllabusId: Long,
    val topicTitle: String,
    val analysis: String,
    val importance: String = "MEDIUM" // HIGH, MEDIUM, LOW
)

// Data class representation of an extracted syllabus study resource
data class StudyResource(
    val title: String,
    val url: String,
    val platform: String, // YouTube Playlist, YouTube Video, NPTEL Course, Reddit Notes, Quora Link, GitHub Repo, Educational Web
    val creator: String,
    val rank: Int, // 1 is absolute best, up to 5
    val reviews: String,
    val description: String = ""
)
