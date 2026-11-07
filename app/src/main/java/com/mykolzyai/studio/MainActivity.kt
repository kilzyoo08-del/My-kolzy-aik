package com.mykolzyai.studio

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage

private val Bg = Color(0xFFFFFFFF)
private val SurfaceDark = Color(0xFFF4F4F4)
private val TextMain = Color(0xFF202123)
private val TextMuted = Color(0xFF737373)
private val Accent = Color(0xFF202123)

data class ChatMessage(val text: String, val fromUser: Boolean, val imageUri: android.net.Uri? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KolzaiChatScreen() }
    }
}

/**
 * Локальный справочный ответ без сетевого запроса и без имитации работы LLM.
 * Использует только встроенные уроки RobloxKnowledge.
 */
private fun offlineKnowledgeReply(query: String, hasImage: Boolean = false): String {
    if (hasImage) return "Фото прикреплено, но визуальная ИИ-модель пока не подключена. Я могу обработать текст и локальную базу Roblox, но не буду утверждать, что распознал изображение."

    LocalReasoningEngine.solveMathQuestion(query)?.let { result ->
        return buildString {
            appendLine("Решение")
            appendLine()
            appendLine("Разберём выражение по порядку действий:")
            result.steps.forEachIndexed { index, step ->
                appendLine("${index + 1}. $step")
            }
            appendLine()
            append("Ответ: ${result.answer}")
        }
    }

    val matches = RobloxKnowledge.search(query, limit = 3)
    if (matches.isEmpty()) {
        return "Пока не могу надёжно ответить на этот вопрос в автономном режиме. " +
            "Сейчас Kolzai умеет пошагово решать простые арифметические выражения " +
            "и искать сведения во встроенной базе Roblox. Полноценная языковая модель ещё не подключена."
    }

    return buildString {
        appendLine("Справка по Roblox · найдено: ${matches.size}")
        matches.forEachIndexed { index, lesson ->
            if (index > 0) appendLine()
            appendLine("${index + 1}. ${lesson.titleRu}")
            appendLine(lesson.explanationRu)
            appendLine("Пример Luau:")
            appendLine(lesson.exampleLuau)
            appendLine("Совет для Building: ${lesson.buildingTipRu}")
        }
        appendLine()
        append("Это проверяемая справка из локальной базы, а не свободное рассуждение языковой модели.")
    }
}

private fun wantsWebSearch(query: String): Boolean {
    val text = query.lowercase()
    return listOf("найди", "поищи", "поиск в интернете", "в интернете", "на сайтах",
        "источники", "ссылки", "последние новости", "актуальная информация",
        "что сейчас", "сегодня").any { it in text }
}

private fun webSearchReply(query: String): String {
    val results = WebSearchClient.search(query, limit = 5)
    if (results.isEmpty()) return "Поиск завершился, но поисковая система не вернула доступных результатов."
    return buildString {
        appendLine("Результаты поиска в интернете")
        appendLine("Нашёл ${results.size} страниц. Это выдержки из поисковой выдачи, а не проверенный фактологический вывод.")
        results.forEachIndexed { index, result ->
            appendLine()
            appendLine("${index + 1}. ${result.title}")
            if (result.snippet.isNotBlank()) appendLine(result.snippet)
            appendLine(result.url)
        }
        appendLine()
        append("Открой источники и сверяй важные сведения по нескольким независимым страницам.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KolzaiChatScreen() {
    var draft by remember { mutableStateOf("") }
    var chatPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isThinking by remember { mutableStateOf(false) }
    var thinkingStage by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Чат") }
    var showHistory by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var currentConversationId by remember { mutableStateOf<String?>(null) }
    var remoteConversations by remember { mutableStateOf(emptyList<CenterionApi.Conversation>()) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var authChecked by remember { mutableStateOf(false) }
    var videoPrompt by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("5 секунд") }
    var ratio by remember { mutableStateOf("16:9") }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationProgress by remember { mutableIntStateOf(0) }
    var generatedVideoPath by remember { mutableStateOf<String?>(null) }
    var generationError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        if (!CenterionApi.isConfigured()) {
            authChecked = true
        } else {
            Thread {
                val restored = CenterionApi.restoreSession(context)
                mainHandler.post {
                    isAuthenticated = restored
                    authChecked = true
                }
            }.start()
        }
    }

    if (CenterionApi.isConfigured() && !authChecked) {
        CenterionLoadingScreen()
        return
    }
    if (CenterionApi.isConfigured() && !isAuthenticated) {
        CenterionAuthScreen(onAuthenticated = { isAuthenticated = true })
        return
    }

    val chatPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> chatPhotoUri = uri }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photoUri = uri
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Bg, surface = SurfaceDark,
        onBackground = TextMain, onSurface = TextMain
    )) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Centerion", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                            Text(if (selectedTab == "Видео") "Создание видео" else "Новый чат", color = TextMuted, fontSize = 11.sp)
                        }
                    },
                    navigationIcon = {
                        Text("☰", color = TextMain, fontSize = 23.sp,
                            modifier = Modifier.clickable {
                                showHistory = !showHistory
                                if (showHistory && CenterionApi.isConfigured()) {
                                    Thread {
                                        val loaded = try { CenterionApi.listConversations(context) } catch (_: Exception) { emptyList() }
                                        mainHandler.post { remoteConversations = loaded }
                                    }.start()
                                }
                            }.padding(horizontal = 18.dp))
                    },
                    actions = {
                        if (CenterionApi.isConfigured()) {
                            Text("Выйти", color = TextMuted, fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    CenterionApi.signOut(context)
                                    isAuthenticated = false
                                    currentConversationId = null
                                    messages = emptyList()
                                }.padding(horizontal = 8.dp))
                        }
                        Text("＋", color = TextMain, fontSize = 27.sp,
                            modifier = Modifier.clickable {
                                messages = emptyList()
                                currentConversationId = null
                                videoPrompt = ""
                                photoUri = null
                            }.padding(horizontal = 18.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = TextMain)
                )
            },
            bottomBar = {
                if (selectedTab == "Чат") {
                    Column(Modifier.fillMaxWidth().background(Bg).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
                        Row(
                            Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(28.dp)).padding(horizontal = 15.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("＋", color = TextMuted, fontSize = 25.sp, modifier = Modifier.clickable { chatPhotoPicker.launch("image/*") }.padding(end = 10.dp))
                            Box(Modifier.weight(1f)) {
                                if (draft.isEmpty()) Text("Спроси Centerion", color = TextMuted)
                                BasicTextField(value = draft, onValueChange = { draft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(color = TextMain, fontSize = 16.sp), maxLines = 5)
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.size(38.dp).background(if (draft.isBlank() && chatPhotoUri == null) Color(0xFFD9D9D9) else TextMain, CircleShape)
                                .clickable {
                                    if (!isThinking && (draft.isNotBlank() || chatPhotoUri != null)) {
                                        val question = draft.trim().ifBlank { "Расскажи, что можно сделать с прикреплённым фото." }
                                        val image = chatPhotoUri
                                        val conversationSnapshot = currentConversationId
                                        messages = messages + ChatMessage(question, true, image)
                                        draft = ""
                                        chatPhotoUri = null
                                        isThinking = true
                                        thinkingStage = when {
                                            image != null -> "Подготовка изображения…"
                                            CenterionApi.isConfigured() -> "Centerion анализирует запрос…"
                                            wantsWebSearch(question) -> "Поиск страниц в интернете…"
                                            else -> "Поиск в локальной базе…"
                                        }
                                        Thread {
                                            var newConversationId: String? = null
                                            val answer = try {
                                                when {
                                                    image != null -> offlineKnowledgeReply(question, true)
                                                    CenterionApi.isConfigured() && !wantsWebSearch(question) -> {
                                                        val reply = CenterionApi.chat(context, question, conversationSnapshot)
                                                        newConversationId = reply.conversationId
                                                        reply.answer
                                                    }
                                                    wantsWebSearch(question) -> webSearchReply(question)
                                                    else -> offlineKnowledgeReply(question)
                                                }
                                            } catch (error: Exception) {
                                                "Не удалось связаться с Centerion API: ${error.message ?: "проверь подключение и настройки сервера"}."
                                            }
                                            mainHandler.post {
                                                if (!newConversationId.isNullOrBlank()) currentConversationId = newConversationId
                                                messages = messages + ChatMessage(answer, false)
                                                isThinking = false
                                                thinkingStage = ""
                                            }
                                        }.start()
                                    }
                                }, contentAlignment = Alignment.Center) {
                                Text("↑", color = Bg, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        chatPhotoUri?.let { selected ->
                            AsyncImage(model = selected, contentDescription = "Фото к сообщению",
                                modifier = Modifier.size(58.dp).padding(top = 5.dp))
                        }
                        Text(
                            if (isThinking) thinkingStage
                            else if (CenterionApi.isConfigured()) "Centerion · облачное ядро"
                            else "Centerion · автономный режим",
                            color = if (isThinking) Accent else TextMuted, fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp))
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (showHistory) {
                    Column(Modifier.width(210.dp).fillMaxHeight().background(Color(0xFFF7F7F7)).padding(14.dp)) {
                        Text("Новый чат", color = TextMain, modifier = Modifier.clickable {
                            messages = emptyList()
                            currentConversationId = null
                            showHistory = false
                        }.padding(vertical = 14.dp))
                        Divider(color = Color.LightGray)
                        Text("История", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                        if (remoteConversations.isEmpty()) {
                            Text("Сохранённые диалоги появятся здесь после первого сообщения.", color = TextMuted, fontSize = 12.sp)
                        } else {
                            remoteConversations.forEach { conversation ->
                                Text(
                                    conversation.title,
                                    color = TextMain,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        Thread {
                                            val loaded = try { CenterionApi.loadConversation(context, conversation.id) } catch (_: Exception) { emptyList() }
                                            mainHandler.post {
                                                currentConversationId = conversation.id
                                                messages = loaded.map { (role, content) -> ChatMessage(content, role == "user") }
                                                showHistory = false
                                            }
                                        }.start()
                                    }.padding(vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                        .background(SurfaceDark, RoundedCornerShape(24.dp)).padding(4.dp)) {
                        listOf("Чат", "Видео").forEach { tab ->
                            Box(Modifier.weight(1f).background(if (selectedTab == tab) Color(0xFFE8E8E8) else Color.Transparent,
                                RoundedCornerShape(20.dp)).clickable { selectedTab = tab }.padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center) {
                                Text(if (tab == "Видео") "🎬 Видео" else "✦ Чат",
                                    color = TextMain, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    if (selectedTab == "Видео") {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp),
                            contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                Text("Создай своё видео", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                Text("Опиши сцену или добавь фото, которое нужно оживить.", color = TextMuted,
                                    fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                            }
                            item {
                                Text("Описание видео", color = TextMain, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(16.dp)).padding(14.dp)) {
                                    if (videoPrompt.isEmpty()) Text("Например: кинематографичный вид футуристического города на закате…",
                                        color = TextMuted, fontSize = 14.sp)
                                    BasicTextField(videoPrompt, { videoPrompt = it }, Modifier.fillMaxWidth().heightIn(min = 110.dp),
                                        textStyle = LocalTextStyle.current.copy(color = TextMain, fontSize = 15.sp))
                                }
                            }
                            item {
                                Text("Фото для оживления (необязательно)", color = TextMain, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Surface(color = SurfaceDark, shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().clickable { photoPicker.launch("image/*") }) {
                                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (photoUri == null) "＋  Выбрать фото" else "✓  Фото выбрано",
                                            color = TextMain, fontWeight = FontWeight.Medium)
                                        Text(photoUri?.lastPathSegment ?: "Нажми, чтобы выбрать изображение на телефоне",
                                            color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                                        photoUri?.let { selectedPhoto ->
                                            AsyncImage(
                                                model = selectedPhoto,
                                                contentDescription = "Выбранное фото",
                                                modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Text("Длительность", color = TextMain, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    listOf("5 секунд", "10 секунд").forEach { value ->
                                        ChoicePill(value, duration == value) { duration = value }
                                    }
                                }
                            }
                            item {
                                Text("Формат видео", color = TextMain, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    listOf("16:9", "9:16", "1:1").forEach { value ->
                                        ChoicePill(value, ratio == value) { ratio = value }
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        if (!isGenerating && videoPrompt.isNotBlank()) {
                                            isGenerating = true
                                            generationProgress = 0
                                            generatedVideoPath = null
                                            generationError = null
                                            val promptSnapshot = videoPrompt.trim()
                                            val secondsSnapshot = duration.substringBefore(" ").toIntOrNull() ?: 5
                                            val ratioSnapshot = ratio
                                            val photoSnapshot = photoUri
                                            Thread {
                                                try {
                                                    val result = LocalVideoGenerator.generate(
                                                        context = context,
                                                        prompt = promptSnapshot,
                                                        durationSeconds = secondsSnapshot,
                                                        aspectRatio = ratioSnapshot,
                                                        imageUri = photoSnapshot
                                                    ) { value ->
                                                        mainHandler.post { generationProgress = value }
                                                    }
                                                    mainHandler.post {
                                                        generatedVideoPath = result.file.absolutePath
                                                        isGenerating = false
                                                    }
                                                } catch (error: Exception) {
                                                    mainHandler.post {
                                                        generationError = error.message ?: "Не удалось создать видео."
                                                        isGenerating = false
                                                    }
                                                }
                                            }.start()
                                        }
                                    },
                                    enabled = !isGenerating && videoPrompt.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(if (isGenerating) "Создаю… $generationProgress%" else "Создать видео",
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                if (isGenerating) {
                                    LinearProgressIndicator(progress = { generationProgress / 100f },
                                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                                    Text("Локальный рендеринг кадров и кодирование MP4. Не закрывай приложение.",
                                        color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                                }
                                generationError?.let { errorText ->
                                    Text("Ошибка: $errorText", color = Color(0xFFFF8A80),
                                        fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
                                }
                                generatedVideoPath?.let { path ->
                                    Text("Видео готово: $path", color = Color(0xFF9FE3B0),
                                        fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
                                }
                                Text("Локальный рендерер использует выбранное фото как фон и добавляет анимацию, текст и эффекты. Это процедурная анимация, а не нейросеть text-to-video.",
                                    color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
                            }
                        }
                    } else if (messages.isEmpty()) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(58.dp).background(SurfaceDark, CircleShape), contentAlignment = Alignment.Center) {
                                Text("K", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 29.sp)
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("Чем могу помочь?", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(9.dp))
                            Text("Centerion", color = TextMuted, fontSize = 14.sp)
                            Spacer(Modifier.height(26.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SuggestionChip("Напиши текст") { draft = "Помоги написать текст" }
                                SuggestionChip("Придумай идею") { draft = "Придумай интересную идею" }
                            }
                            Spacer(Modifier.height(8.dp))
                            SuggestionChip("Помоги с кодом") { draft = "Помоги разобраться с кодом" }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(messages) { message ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                                    Column(Modifier.widthIn(max = 300.dp)
                                        .background(if (message.fromUser) SurfaceDark else Bg, RoundedCornerShape(18.dp)).padding(13.dp)) {
                                        message.imageUri?.let { image ->
                                            AsyncImage(model = image, contentDescription = "Прикреплённое фото",
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
                                            Spacer(Modifier.height(8.dp))
                                        }
                                        Text(message.text, color = TextMain)
                                    }
                                }
                            }
                            item {
                                Text("Ответы могут быть ограничены текущими возможностями модели.", color = TextMuted,
                                    fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) Color(0xFFE8E8E8) else SurfaceDark,
        shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, color = TextMain, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(color = SurfaceDark, shape = RoundedCornerShape(20.dp), modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, color = TextMain, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp))
    }
}


@Composable
private fun CenterionLoadingScreen() {
    MaterialTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(14.dp))
                Text("Подключаем Centerion…")
            }
        }
    }
}

@Composable
private fun CenterionAuthScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Scaffold(containerColor = Bg) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Centerion", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Войди, чтобы пользоваться облачной языковой моделью и синхронизированной памятью.",
                    color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Электронная почта") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Пароль") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (email.isBlank() || password.length < 6) {
                            error = "Введи email и пароль не короче 6 символов."
                        } else {
                            busy = true
                            error = null
                            Thread {
                                val failure = try {
                                    if (isSignUp) CenterionApi.signUp(context, email, password)
                                    else CenterionApi.signIn(context, email, password)
                                    null
                                } catch (exception: Exception) {
                                    exception.message ?: "Не удалось выполнить вход."
                                }
                                handler.post {
                                    busy = false
                                    if (failure == null) onAuthenticated() else error = failure
                                }
                            }.start()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (isSignUp) "Создать аккаунт" else "Войти")
                }
                TextButton(onClick = { isSignUp = !isSignUp; error = null }) {
                    Text(if (isSignUp) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться")
                }
                Text(
                    "Ключ OpenAI и секретный ключ Supabase не хранятся в приложении.",
                    color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 20.dp)
                )
            }
        }
    }
}
