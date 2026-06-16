package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.*
import com.example.ui.viewmodel.StudyViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

// Define beautiful local UI color accents matching "Professional Polish" theme
private val AcademicIndigo = Color(0xFF4459A9) // Polish Primary Blue / Indigo
private val DeepAmber = Color(0xFFFF5449) // Polished Coral / Deep Amber
private val SlateDark = Color(0xFF1B1B1F) // Near-black for rich high-contrast text
private val LightMint = Color(0xFFE8F5E9)
private val MintGreen = Color(0xFF388E3C) // Richer polished green

// Professional Polish Color Tokens
private val PolishBackground = Color(0xFFFBFCFF)
private val PolishLightBlue = Color(0xFFDDE1FF)
private val PolishTextNavy = Color(0xFF161B2C)
private val PolishBorderGray = Color(0xFFC5C6D0)
private val PolishGrayCard = Color(0xFFF3F0F5)
private val PolishGrayBorder = Color(0xFFE1E2EC)

// Standard Navigation host structure custom mapped to current screen state
@Composable
fun SyllabusAppMain(viewModel: StudyViewModel) {
    var currentScreen by remember { mutableStateOf("dashboard") }
    var selectedSyllabusIdInput by remember { mutableStateOf<Long?>(null) }
    var selectedModuleIdInput by remember { mutableStateOf<Long?>(null) }
    var selectedTopicIdInput by remember { mutableStateOf<Long?>(null) }

    val context = LocalContext.current
    val syllabi by viewModel.syllabi.collectAsState()
    val activeSyllabus by viewModel.currentSyllabus.collectAsState()
    val modules by viewModel.currentModules.collectAsState()
    val topics by viewModel.currentTopics.collectAsState()
    val activeTopic by viewModel.activeTopic.collectAsState()
    val errors by viewModel.errorMessage.collectAsState()

    // Error toast handler
    LaunchedEffect(errors) {
        errors?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PolishBackground)
    ) {
        when (currentScreen) {
            "dashboard" -> {
                DashboardScreen(
                    syllabi = syllabi,
                    onNavigateToUpload = { currentScreen = "upload" },
                    onNavigateToDetails = { id ->
                        viewModel.selectSyllabus(id)
                        currentScreen = "details"
                    },
                    onDeleteSyllabus = { id ->
                        viewModel.deleteSyllabusCascade(id)
                    }
                )
            }

            "upload" -> {
                UploadSyllabusScreen(
                    isExtracting = viewModel.isExtracting.collectAsState().value,
                    onUploadClicked = { text, examDate, hours, source ->
                        viewModel.uploadAndExtractSyllabus(text, examDate, hours, source) { id ->
                            currentScreen = "details"
                        }
                    },
                    onBackClicked = { currentScreen = "dashboard" }
                )
            }

            "details" -> {
                SyllabusDetailsScreen(
                    syllabus = activeSyllabus,
                    modules = modules,
                    onBackClicked = { currentScreen = "dashboard" },
                    onModuleClicked = { moduleId ->
                        viewModel.selectModule(moduleId)
                        selectedModuleIdInput = moduleId
                        currentScreen = "module_details"
                    },
                    viewModel = viewModel
                )
            }

            "module_details" -> {
                val activeModule = modules.find { it.id == selectedModuleIdInput }
                ModuleDetailsScreen(
                    syllabus = activeSyllabus,
                    module = activeModule,
                    topics = topics.filter { it.moduleId == selectedModuleIdInput },
                    onBackClicked = { currentScreen = "details" },
                    onTopicClicked = { topicId ->
                        viewModel.selectTopic(topicId)
                        selectedTopicIdInput = topicId
                        currentScreen = "topic_study"
                    },
                    onPlayQuizClicked = {
                        activeModule?.let { m ->
                            viewModel.generateOrLoadQuizzes(m.id, m.title, m.description, activeSyllabus?.subject ?: "")
                            currentScreen = "quiz"
                        }
                    },
                    onToggleTopicStatus = { topicId, isCompleted ->
                        viewModel.toggleTopicStatus(topicId, isCompleted)
                    }
                )
            }

            "topic_study" -> {
                val activeModule = modules.find { it.id == selectedModuleIdInput }
                TopicStudyScreen(
                    syllabus = activeSyllabus,
                    module = activeModule,
                    topic = activeTopic,
                    viewModel = viewModel,
                    onBackClicked = { currentScreen = "module_details" }
                )
            }

            "quiz" -> {
                val activeModule = modules.find { it.id == selectedModuleIdInput }
                val quizzes by viewModel.activeQuizzes.collectAsState()
                QuizScreen(
                    module = activeModule,
                    quizzes = quizzes,
                    isLoading = viewModel.quizLoading.collectAsState().value,
                    onBackClicked = { currentScreen = "module_details" },
                    onAnswerSelected = { quizId, selectedOpt, daoId ->
                        viewModel.selectQuizAnswer(quizId, selectedOpt, daoId)
                    },
                    onRefreshClicked = {
                        activeModule?.let { m ->
                            viewModel.generateOrLoadQuizzes(m.id, m.title, m.description, activeSyllabus?.subject ?: "")
                        }
                    }
                )
            }
        }
    }
}

// --- SCREEN 1: DASHBOARD / HOMEPAGE ---
@Composable
fun DashboardScreen(
    syllabi: List<SyllabusEntity>,
    onNavigateToUpload: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onDeleteSyllabus: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AcademicIndigo)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = "SyllabusAI Logo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SyllabusAI",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = SlateDark
                            ),
                            modifier = Modifier.testTag("app_logo_title")
                        )
                        Text(
                            text = "A.I. College Study Assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E2E6))
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color(0xFF44474E),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToUpload,
                icon = { Icon(Icons.Default.Add, contentDescription = "Upload Syllabus") },
                text = { Text("Upload Syllabus") },
                containerColor = AcademicIndigo,
                contentColor = Color.White,
                modifier = Modifier
                    .testTag("upload_fab")
                    .padding(8.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Header
            item {
                DashboardHeaderStats(syllabi)
            }

            // Quick Tips
            item {
                StudyTipCard()
            }

            // Section Label
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Study Syllabi",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${syllabi.size} Saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (syllabi.isEmpty()) {
                item {
                    SyllabiEmptyState(onNavigateToUpload)
                }
            } else {
                items(syllabi) { syllabus ->
                    SyllabusRowCard(
                        syllabus = syllabus,
                        onRowClick = { onNavigateToDetails(syllabus.id) },
                        onDeleteSyllabus = { onDeleteSyllabus(syllabus.id) }
                    )
                }
            }

            // Extra space at bottom for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun DashboardHeaderStats(syllabi: List<SyllabusEntity>) {
    val totalCourses = syllabi.size
    val totalProgress = if (syllabi.isNotEmpty()) {
        syllabi.map { it.overallProgress }.average().toInt()
    } else 0

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_card"),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PolishBorderGray),
        colors = CardDefaults.outlinedCardColors(
            containerColor = PolishLightBlue
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AcademicIndigo)
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = "Stats",
                            tint = Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.Center)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Exam Study Metrics",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PolishTextNavy
                        )
                        Text(
                            text = "AI-Powered Course Tracker",
                            style = MaterialTheme.typography.bodySmall,
                            color = PolishTextNavy.copy(alpha = 0.7f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Active",
                        color = PolishTextNavy,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Stat 1
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "$totalCourses",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = PolishTextNavy
                    )
                    Text(text = "Active Syllabi", style = MaterialTheme.typography.bodySmall, color = PolishTextNavy.copy(alpha = 0.6f))
                }

                // Stat 2
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "$totalProgress%",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = PolishTextNavy
                    )
                    Text(text = "Avg Progress", style = MaterialTheme.typography.bodySmall, color = PolishTextNavy.copy(alpha = 0.6f))
                }

                // Stat 3
                val totalHours = syllabi.sumOf { it.studyHoursPerDay }
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "${totalHours}h",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = AcademicIndigo
                    )
                    Text(text = "Daily Goals", style = MaterialTheme.typography.bodySmall, color = PolishTextNavy.copy(alpha = 0.6f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                val progressFloat = if (totalProgress > 0) totalProgress / 100f else 0.05f
                LinearProgressIndicator(
                    progress = { progressFloat },
                    color = AcademicIndigo,
                    trackColor = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun StudyTipCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = "Tip",
                tint = DeepAmber,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "AI Smart Tip",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Curated study playlists are ranked by active student engagement to filter university exam bloating.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun SyllabiEmptyState(onNavigateToUpload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.LibraryBooks,
            contentDescription = "Empty",
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Syllabi Uploaded",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Upload a syllabus text, document reference or link to immediately generate an optimized resource schedule.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNavigateToUpload,
            colors = ButtonDefaults.buttonColors(containerColor = AcademicIndigo),
            modifier = Modifier.testTag("empty_state_action")
        ) {
            Text("Create First Syllabus Roadmap")
        }
    }
}

@Composable
fun SyllabusRowCard(
    syllabus: SyllabusEntity,
    onRowClick: () -> Unit,
    onDeleteSyllabus: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
            .testTag("syllabus_item_${syllabus.id}"),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PolishGrayBorder),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AcademicIndigo.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = syllabus.subject,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AcademicIndigo
                    )
                }

                IconButton(
                    onClick = onDeleteSyllabus,
                    modifier = Modifier.testTag("delete_${syllabus.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = syllabus.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = "Exam Date",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Exam: ${syllabus.examDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "Available hours",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${syllabus.studyHoursPerDay} hrs/day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar and info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { syllabus.overallProgress / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (syllabus.overallProgress >= 100f) MintGreen else AcademicIndigo,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${syllabus.overallProgress.toInt()}% Done",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (syllabus.overallProgress >= 100f) MintGreen else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}


// --- SCREEN 2: SYLLABUS UPLOAD FORM SCREEN ---
@Composable
fun UploadSyllabusScreen(
    isExtracting: Boolean,
    onUploadClicked: (text: String, examDate: String, hours: Int, source: String) -> Unit,
    onBackClicked: () -> Unit
) {
    val context = LocalContext.current
    var uploadMode by remember { mutableStateOf("paste") } // "paste", "link", "camera"
    var textInput by remember { mutableStateOf("") }
    var linkInput by remember { mutableStateOf("") }
    var examDateInput by remember { mutableStateOf("") }
    var dailyHoursInput by remember { mutableStateOf("3") }
    var showCameraPreview by remember { mutableStateOf(false) }

    // Date Picker Dialog setup
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedMonth = String.format("%02d", month + 1)
            val formattedDay = String.format("%02d", dayOfMonth)
            examDateInput = "$year-$formattedMonth-$formattedDay"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    if (isExtracting) {
        LoadingExtractionScreen()
    } else {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Build Study Roadmap",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Let our AI analyze your study syllabus. Input your college material details below:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Options Rows
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { uploadMode = "paste" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uploadMode == "paste") AcademicIndigo else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (uploadMode == "paste") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste")
                        }

                        Button(
                            onClick = { uploadMode = "link" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uploadMode == "link") AcademicIndigo else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (uploadMode == "link") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "Link", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Link")
                        }

                        Button(
                            onClick = { uploadMode = "camera" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uploadMode == "camera") AcademicIndigo else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (uploadMode == "camera") Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan")
                        }
                    }
                }

                // Input Box based on Upload Mode
                item {
                    when (uploadMode) {
                        "paste" -> {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                label = { Text("Paste college syllabus, modules or notes text") },
                                placeholder = { Text("Example: Module 1: Binary Trees and BST traversals. Module 2: Hash tables and Linked lists...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .testTag("syllabus_paste_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        "link" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = linkInput,
                                    onValueChange = { linkInput = it },
                                    label = { Text("Educational Syllabus Link (PDF/College Web)") },
                                    placeholder = { Text("https://www.collegedata.edu/curriculum") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("syllabus_link_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(2.dp, AcademicIndigo.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .background(AcademicIndigo.copy(alpha = 0.05f))
                                        .padding(20.dp)
                                        .clickable {
                                            linkInput = "https://www.mit.edu/~cse/cs101-syllabus-algorithms.pdf"
                                            textInput = "MIT CS-101 Course Agenda:\nModule 1: sorting bubble sort, heap sort. Module 2: heap structures and balanced trees.\nModule 3: graph traversals DFS and BFS algorithms."
                                            uploadMode = "paste"
                                            Toast.makeText(context, "MIT Syllabus link loaded!", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "✨ Quick Demo: Use MIT Algorithms Syllabus",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AcademicIndigo,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "Populates mock web syllabus URL parameters for evaluation.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        "camera" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCameraPreview = true },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(32.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera Icon", modifier = Modifier.size(60.dp), tint = AcademicIndigo)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Snap Syllabus Photo", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Use your phone camera to transcribe printed study material", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { showCameraPreview = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = AcademicIndigo)
                                    ) {
                                        Text("Launch Syllabus Scanner")
                                    }
                                }
                            }
                        }
                    }
                }

                // Exam date & hours
                item {
                    Text(
                        text = "Exam Planning Parameters",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                item {
                    OutlinedTextField(
                        value = examDateInput,
                        onValueChange = { examDateInput = it },
                        label = { Text("Target Exam Date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialog.show() }
                            .testTag("exam_date_picker_trigger"),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = MaterialTheme.colorScheme.primary,
                            disabledLabelColor = MaterialTheme.colorScheme.primary,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = "Calendar",
                                modifier = Modifier.clickable { datePickerDialog.show() }
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = dailyHoursInput,
                        onValueChange = { dailyHoursInput = it },
                        label = { Text("Daily Study Hours Commitment") },
                        placeholder = { Text("E.g., 3") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("study_hours_input"),
                        leadingIcon = { Icon(Icons.Default.AvTimer, contentDescription = "Timer") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Submit Button
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val finalSyllabusText = if (uploadMode == "paste") {
                                textInput
                            } else if (uploadMode == "link") {
                                "Syllabus extracted from link: $linkInput.\n$textInput"
                            } else {
                                textInput
                            }

                            if (finalSyllabusText.isBlank()) {
                                Toast.makeText(context, "Syllabus content cannot be empty", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (examDateInput.isBlank()) {
                                Toast.makeText(context, "Please select an Exam Date", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            val targetHours = dailyHoursInput.toIntOrNull() ?: 3
                            val sourceLabel = if (uploadMode == "paste") "Pasted Text" else if (uploadMode == "link") "Link: $linkInput" else "Camera Scan"

                            onUploadClicked(finalSyllabusText, examDateInput, targetHours, sourceLabel)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("submit_syllabus_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AcademicIndigo),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "AI Generate Study Roadmap",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Camera scanner modal
    if (showCameraPreview) {
        Dialog(onDismissRequest = { showCameraPreview = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Syllabus Vision Scanner",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AcademicIndigo
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Mock camera view finder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, AcademicIndigo, RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        // Scan line animation
                        var scanOffset by remember { mutableStateOf(0f) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                kotlinx.coroutines.delay(16)
                                scanOffset = (scanOffset + 3f) % 260f
                            }
                        }

                        // Scanning text preview card
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Scan target", modifier = Modifier.size(48.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("[ Syllabus Document: DSA CSE-305 ]", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Module 1: Binary Search Trees operations.\nModule 2: Quicksort and mergesort proofs.\nModule 3: Dynamic programming knapsack problems.", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, textAlign = TextAlign.Center)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .offset(y = scanOffset.dp)
                                .background(MintGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showCameraPreview = false }) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                textInput = """
                                    SYLLABUS DATA STRUCTURES & ALGORITHMS (CS-302)
                                    MODULE 1: Linear Lists, Stacks and Queues. Double-ended list structures.
                                    MODULE 2: Binary search tree recursion, balances, hash map indexing.
                                    MODULE 3: Sorting algorithms complexities, bubble, heap, quicksort.
                                    MODULE 4: Dynamic Programming concepts, Knapsack matrix solutions.
                                """.trimIndent()
                                uploadMode = "paste"
                                showCameraPreview = false
                                Toast.makeText(context, "Syllabus photo transcribed!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text("Capture & Transcribe")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingExtractionScreen() {
    val quotes = listOf(
        "\"The best way to study is to study with structure.\" — SyllabusAI",
        "Curating high-quality YouTube resources...",
        "Ranking legendary teachers: Abdul Bari, Neso Academy... ",
        "Assembling progress checkpoints...",
        "Evaluating exam targets and available study time...",
        "Translating university material into visual modular roadmaps..."
    )
    var quoteIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            quoteIndex = (quoteIndex + 1) % quotes.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("loading_screen")
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(60.dp),
            color = AcademicIndigo,
            strokeWidth = 6.dp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "AI is Extracting Syllabus...",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = AcademicIndigo
        )
        Spacer(modifier = Modifier.height(16.dp))
        AnimatedContent(
            targetState = quotes[quoteIndex],
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "Quote animation"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(4.dp)
                .clip(CircleShape),
            color = MintGreen
        )
    }
}


// --- SCREEN 3: SYLLABUS DETAILS & ROADMAP ---
@Composable
fun SyllabusDetailsScreen(
    syllabus: SyllabusEntity?,
    modules: List<ModuleEntity>,
    onBackClicked: () -> Unit,
    onModuleClicked: (Long) -> Unit,
    viewModel: StudyViewModel
) {
    if (syllabus == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showPyqPanel by remember { mutableStateOf(false) }
    val pcqLoaderState = viewModel.pcqLoading.collectAsState().value
    val pcqList by viewModel.pcqList.collectAsState()

    // Calculate days remaining
    val daysRemaining = remember(syllabus.examDate) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val exam = sdf.parse(syllabus.examDate)
            val diff = exam.time - System.currentTimeMillis()
            val days = (diff / (1000 * 60 * 60 * 24)).toInt()
            max(0, days)
        } catch (e: Exception) {
            15 // fallback default
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = syllabus.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Stats Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("syllabus_header_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AcademicIndigo)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = syllabus.subject,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = syllabus.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Exam Date", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                Text(syllabus.examDate, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                            Column {
                                Text("Daily Study", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                Text("${syllabus.studyHoursPerDay} Hours", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                            Column {
                                Text("Days Left", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                Text("$daysRemaining Days", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepAmber)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Syllabus wide progress bar
                        Text("Course Completion", style = MaterialTheme.typography.labelSmall, color = Color.LightGray, modifier = Modifier.padding(bottom = 6.dp))
                        LinearProgressIndicator(
                            progress = { syllabus.overallProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MintGreen,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // AI Previous Year Paper verification button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SlateDark)
                        .clickable {
                            showPyqPanel = !showPyqPanel
                            if (showPyqPanel && pcqList.isEmpty()) {
                                viewModel.loadOrGeneratePcqAnalysis(syllabus.id, syllabus.title, syllabus.subject)
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, contentDescription = "PYQ", tint = DeepAmber)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI Previous Year Question Analysis",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "See critical exam patterns and topic priorities",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showPyqPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = Color.White
                        )
                    }
                }
            }

            // Expose the expanded target list of Previous Year predictions
            if (showPyqPanel) {
                if (pcqLoaderState) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = DeepAmber)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Analyzing historic papers...", modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }
                    }
                } else {
                    items(pcqList) { pcq ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = pcq.topicTitle,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (pcq.importance == "HIGH") DeepAmber.copy(alpha = 0.15f)
                                                else AcademicIndigo.copy(alpha = 0.15f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${pcq.importance} PRIORITY",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (pcq.importance == "HIGH") DeepAmber else AcademicIndigo
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pcq.analysis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Segment Label
            item {
                Text(
                    text = "Personalized Study Roadmap",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Roadmap modules
            items(modules) { module ->
                ModuleRowItem(
                    module = module,
                    onModuleClicked = { onModuleClicked(module.id) }
                )
            }
        }
    }
}

@Composable
fun ModuleRowItem(
    module: ModuleEntity,
    onModuleClicked: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onModuleClicked() }
            .testTag("module_item_${module.id}"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chapter ball
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AcademicIndigo.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = "${module.number}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AcademicIndigo
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { module.moduleProgress / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (module.moduleProgress >= 100f) MintGreen else AcademicIndigo,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${module.moduleProgress.toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}


// --- SCREEN 4: MODULE DETAILS & TOPIC LIST ---
@Composable
fun ModuleDetailsScreen(
    syllabus: SyllabusEntity?,
    module: ModuleEntity?,
    topics: List<TopicEntity>,
    onBackClicked: () -> Unit,
    onTopicClicked: (Long) -> Unit,
    onPlayQuizClicked: () -> Unit,
    onToggleTopicStatus: (topicId: Long, isCompleted: Boolean) -> Unit
) {
    if (module == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPlayQuizClicked,
                icon = { Icon(Icons.Default.Quiz, contentDescription = "Practice Quiz") },
                text = { Text("Generate Practice Quiz") },
                containerColor = DeepAmber,
                contentColor = Color.White,
                modifier = Modifier
                    .testTag("practice_quiz_fab")
                    .padding(8.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Module Info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("module_header_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = module.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = module.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Module Progress", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("${module.moduleProgress.toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { module.moduleProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MintGreen
                        )
                    }
                }
            }

            // Roadmap topic label
            item {
                Text(
                    text = "Core Syllabus Topics (${topics.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            // List of topics
            items(topics) { topic ->
                StudyTopicRow(
                    topic = topic,
                    viewModel = null, // simplified helper
                    onTopicRowClick = { onTopicClicked(topic.id) },
                    onStatusToggled = { isCompleted ->
                        onToggleTopicStatus(topic.id, isCompleted)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun StudyTopicRow(
    topic: TopicEntity,
    viewModel: StudyViewModel?,
    onTopicRowClick: () -> Unit,
    onStatusToggled: (Boolean) -> Unit
) {
    val isDone = topic.status == "COMPLETED"

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTopicRowClick() }
            .testTag("topic_item_${topic.id}"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isDone,
                onCheckedChange = { onStatusToggled(it) },
                colors = CheckboxDefaults.colors(checkedColor = MintGreen),
                modifier = Modifier.testTag("topic_checkbox_${topic.id}")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isDone) Color.Gray else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = topic.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Doubt/Platform status indicators
            IconButton(onClick = onTopicRowClick) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Doubts solver",
                    tint = AcademicIndigo.copy(alpha = 0.8f)
                )
            }
        }
    }
}


// --- SCREEN 5: TOPIC DETAILS & RESOURCE PLAYER & AI DOUBT solver ---
@Composable
fun TopicStudyScreen(
    syllabus: SyllabusEntity?,
    module: ModuleEntity?,
    topic: TopicEntity?,
    viewModel: StudyViewModel,
    onBackClicked: () -> Unit
) {
    if (topic == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val resources = remember(topic) { viewModel.getTopicResources(topic) }
    val doubtHistory by viewModel.doubtHistory.collectAsState()
    val solverLoading = viewModel.doubtSolving.collectAsState().value
    var questionInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Topic Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("topic_details_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AcademicIndigo.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Concept Breakdown",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AcademicIndigo)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = topic.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Resource Recommendation engine
            item {
                Text(
                    text = "AI Curated study resources",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (resources.isEmpty()) {
                item {
                    Text("No resources compiled for this topic.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                items(resources) { res ->
                    InternetResourceRowCard(resource = res)
                }
            }

            // Doubt Solver Chat
            item {
                Text(
                    text = "Personal AI Study Tutor",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Have a study doubt? Ask a question for instant steps/code references:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Doubt lists
                        if (doubtHistory.isEmpty()) {
                            Text(
                                text = "Tutor: Hi there! Ask me any tricky doubt about ${topic.title} and I will break it down.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AcademicIndigo,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AcademicIndigo.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                doubtHistory.forEach { dbDoubt ->
                                    // User question
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .background(DeepAmber.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(text = "Student: ${dbDoubt.question}", style = MaterialTheme.typography.bodySmall, color = DeepAmber)
                                    }

                                    // Answer
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Start)
                                            .background(AcademicIndigo.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(text = dbDoubt.answer, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        if (solverLoading) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AcademicIndigo)
                                Text("Tutor is thinking...", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Input group
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = questionInput,
                                onValueChange = { questionInput = it },
                                label = { Text("Ask doubt...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("doubt_text_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    if (questionInput.isBlank()) return@Button
                                    viewModel.submitDoubtQuery(
                                        topicId = topic.id,
                                        question = questionInput,
                                        topicTitle = topic.title,
                                        topicDesc = topic.description
                                    )
                                    questionInput = ""
                                },
                                modifier = Modifier.testTag("ask_doubt_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AcademicIndigo),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun InternetResourceRowCard(resource: StudyResource) {
    val context = LocalContext.current
    val strokeColor = when (resource.platform) {
        "YouTube Playlist", "YouTube Video" -> Color(0xFFFF0000)
        "NPTEL Course" -> Color(0xFFFF9900)
        "Reddit Notes" -> Color(0xFFFF4500)
        "Quora Link" -> Color(0xFFB92B27)
        "GitHub Repo" -> Color(0xFF24292E)
        else -> AcademicIndigo
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, strokeColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resource.url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Url unavailable to open: ${resource.url}", Toast.LENGTH_SHORT).show()
                }
            }
            .testTag("resource_item_${resource.title}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Platform Logo box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(strokeColor.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = when (resource.platform) {
                        "YouTube Playlist", "YouTube Video" -> Icons.Default.PlayArrow
                        "NPTEL Course" -> Icons.Default.WorkspacePremium
                        "Reddit Notes" -> Icons.Default.ChatBubbleOutline
                        "Quora Link" -> Icons.Default.HelpOutline
                        "GitHub Repo" -> Icons.Default.Code
                        else -> Icons.Default.Launch
                    },
                    contentDescription = "Resource type",
                    tint = strokeColor,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RANK #${resource.rank} • ${resource.platform.uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = strokeColor
                    )
                    Text(
                        text = resource.creator,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = resource.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = resource.reviews,
                    style = MaterialTheme.typography.bodySmall,
                    color = MintGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


// --- SCREEN 6: DYNAMIC REVISION QUIZ PLAYER ---
@Composable
fun QuizScreen(
    module: ModuleEntity?,
    quizzes: List<QuizEntity>,
    isLoading: Boolean,
    onBackClicked: () -> Unit,
    onAnswerSelected: (quizId: Long, selected: String, daoId: Long) -> Unit,
    onRefreshClicked: () -> Unit
) {
    if (module == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var selectedAnswers = remember { mutableStateMapOf<Long, String>() }
    var score by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "${module.title} Study Quiz",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = DeepAmber, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("AI is compiling tricky exam MCQ questions...", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("Analysing concepts from syllabus text", style = MaterialTheme.typography.bodySmall)
            }
        } else if (quizzes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.HelpOutline, contentDescription = "Quiz empty", modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Study Quiz Generation Failed", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onRefreshClicked) {
                    Text("Retry dynamic creation")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Synthesizing 5 revision questions designed by AI to check concept retention:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(quizzes.indices.toList()) { index ->
                    val quiz = quizzes[index]
                    val isChecked = score != null
                    val selected = selectedAnswers[quiz.id]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quiz_card_$index"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Question ${index + 1} of ${quizzes.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = DeepAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = quiz.question,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Options mapping
                            val options = listOf(
                                "A" to quiz.optionA,
                                "B" to quiz.optionB,
                                "C" to quiz.optionC,
                                "D" to quiz.optionD
                            )

                            options.forEach { (optionKey, optionText) ->
                                val isSelected = selected == optionKey
                                val isCorrect = quiz.correctAnswer == optionKey
                                val optionColor = if (isChecked) {
                                    if (isCorrect) MintGreen else if (isSelected) Color.Red else MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    if (isSelected) AcademicIndigo else MaterialTheme.colorScheme.surfaceVariant
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable(enabled = !isChecked) {
                                            selectedAnswers[quiz.id] = optionKey
                                            onAnswerSelected(quiz.id, optionKey, quiz.id)
                                        },
                                    colors = CardDefaults.cardColors(containerColor = optionColor),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$optionKey.",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected || isCorrect && isChecked) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = optionText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected || isCorrect && isChecked) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Explanation visual if answers checked!
                            if (isChecked) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AcademicIndigo.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "Tutor Insight: ${quiz.explanation}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Quiz Action Footer
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (score == null) {
                        Button(
                            onClick = {
                                var computedScore = 0
                                quizzes.forEach { q ->
                                    val finalSelected = selectedAnswers[q.id]
                                    if (finalSelected == q.correctAnswer) {
                                        computedScore++
                                    }
                                }
                                score = computedScore
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("submit_quiz_choices"),
                            colors = ButtonDefaults.buttonColors(containerColor = AcademicIndigo),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Calculate retention score")
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SlateDark)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Your Score: ${score ?: 0} / ${quizzes.size}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if ((score ?: 0) >= 4) "Excellent concept retention!" else "Review tutoring insights below to address doubts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        score = null
                                        selectedAnswers.clear()
                                        onRefreshClicked()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepAmber),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Re-compile new questions")
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}
