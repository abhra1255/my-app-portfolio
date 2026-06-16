package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyllabusExtractionResponse(
    val title: String,
    val subject: String,
    val modules: List<NetworkModule>
)

@JsonClass(generateAdapter = true)
data class NetworkModule(
    val number: Int,
    val title: String,
    val description: String,
    val topics: List<NetworkTopic>
)

@JsonClass(generateAdapter = true)
data class NetworkTopic(
    val title: String,
    val description: String,
    val resources: List<NetworkResource> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NetworkResource(
    val title: String,
    val url: String,
    val platform: String, // YouTube Playlist, YouTube Video, NPTEL Course, Reddit Notes, Quora Link, GitHub Repo, Website/PDF
    val creator: String,
    val rank: Int, // 1 to 5
    val reviews: String,
    val description: String = ""
)

@JsonClass(generateAdapter = true)
data class QuizListResponse(
    val quizzes: List<NetworkQuiz>
)

@JsonClass(generateAdapter = true)
data class NetworkQuiz(
    val question: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String, // A, B, C, D
    val explanation: String
)

@JsonClass(generateAdapter = true)
data class PcqListResponse(
    val analyses: List<NetworkPcq>
)

@JsonClass(generateAdapter = true)
data class NetworkPcq(
    val topicTitle: String,
    val analysis: String,
    val importance: String // HIGH, MEDIUM, LOW
)
