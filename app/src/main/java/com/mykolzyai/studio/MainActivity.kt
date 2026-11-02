package com.mykolzyai.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private val Bg = Color(0xFF212121)
private val SurfaceDark = Color(0xFF2F2F2F)
private val TextMain = Color(0xFFECECEC)
private val TextMuted = Color(0xFF9B9B9B)
private val Accent = Color(0xFFB9A4FF)

data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KolzaiChatScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KolzaiChatScreen() {
    var draft by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Чат") }
    var showHistory by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent,
        background = Bg,
        surface = SurfaceDark,
        onBackground = TextMain,
        onSurface = TextMain
    )) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Kolzai", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("⌄", color = TextMuted, fontSize = 20.sp)
                        }
                    },
                    navigationIcon = {
                        Text("☰", color = TextMain, fontSize = 23.sp,
                            modifier = Modifier.clickable { showHistory = !showHistory }.padding(horizontal = 18.dp))
                    },
                    actions = {
                        Text("＋", color = TextMain, fontSize = 27.sp,
                            modifier = Modifier.clickable { messages = emptyList() }.padding(horizontal = 18.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = TextMain)
                )
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().background(Bg).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(28.dp)).padding(horizontal = 15.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("＋", color = TextMuted, fontSize = 25.sp, modifier = Modifier.padding(end = 10.dp))
                        Box(Modifier.weight(1f)) {
                            if (draft.isEmpty()) Text("Спроси Kolzai", color = TextMuted)
                            BasicTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(color = TextMain, fontSize = 16.sp),
                                maxLines = 5
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(38.dp).background(if (draft.isBlank()) Color(0xFF555555) else TextMain, CircleShape)
                                .clickable {
                                    if (draft.isNotBlank()) {
                                        messages = messages + ChatMessage(draft.trim(), true)
                                        draft = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("↑", color = Bg, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                    }
                    Text("Kolzai может ошибаться. Подключение ИИ-модели добавим отдельно.", color = TextMuted,
                        fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp))
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (showHistory) {
                    Column(Modifier.width(230.dp).fillMaxHeight().background(Color(0xFF191919)).padding(14.dp)) {
                        Text("Новый чат", color = TextMain, modifier = Modifier.fillMaxWidth()
                            .clickable { messages = emptyList(); showHistory = false }.padding(vertical = 14.dp))
                        Divider(color = Color.DarkGray)
                        Text("История чатов появится здесь", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                        Spacer(Modifier.weight(1f))
                        Text("Настройки", color = TextMain, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (messages.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.size(58.dp).background(SurfaceDark, CircleShape), contentAlignment = Alignment.Center) {
                                Text("K", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 29.sp)
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("Чем могу помочь?", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(9.dp))
                            Text("Kolzai — твой ИИ-помощник", color = TextMuted, fontSize = 14.sp)
                            Spacer(Modifier.height(26.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SuggestionChip("Напиши текст") { draft = "Помоги написать текст" }
                                SuggestionChip("Придумай идею") { draft = "Придумай интересную идею" }
                            }
                            Spacer(Modifier.height(8.dp))
                            SuggestionChip("Помоги с кодом") { draft = "Помоги разобраться с кодом" }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(messages) { message ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                                    Text(message.text, color = TextMain, modifier = Modifier
                                        .widthIn(max = 300.dp)
                                        .background(if (message.fromUser) SurfaceDark else Bg, RoundedCornerShape(18.dp))
                                        .padding(13.dp))
                                }
                            }
                            item {
                                Text("Интерфейс чата готов. Чтобы Kolzai отвечал на сообщения, следующим шагом подключим языковую модель через API.",
                                    color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, color = TextMain, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp))
    }
}
