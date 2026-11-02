package com.mykolzyai.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Ink = Color(0xFF10121A)
private val Panel = Color(0xFF202638)
private val Accent = Color(0xFF9B87F5)
private val Muted = Color(0xFFB8C1D1)
private const val LTX_URL = "https://arpitkgoyal-ltx-2-5-demo.static.hf.space/index.html"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudioScreen(this) }
    }
}

private fun improvePrompt(raw: String): String =
    "${raw.trim()}. Cinematic realistic video, clear subject and environment, natural detailed motion, " +
    "smooth deliberate camera movement, consistent lighting, rich visual detail, realistic physics, " +
    "coherent action from beginning to end, high-quality film look."

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Video prompt", text))
    Toast.makeText(context, "Промпт скопирован", Toast.LENGTH_SHORT).show()
}

private fun openGenerator(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LTX_URL))
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(context: Context) {
    var prompt by remember { mutableStateOf("") }
    var improved by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(5) }
    var ratio by remember { mutableStateOf("9:16") }
    var mode by remember { mutableStateOf("Текст → видео") }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Ink, surface = Panel,
        onBackground = Color.White, onSurface = Color.White)) {
        Scaffold(containerColor = Ink, topBar = {
            TopAppBar(title = { Column {
                Text("My Kolzy AI", fontWeight = FontWeight.Bold)
                Text("AI-видеостудия • Android 14", style = MaterialTheme.typography.labelSmall, color = Muted)
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink, titleContentColor = Color.White))
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Movie, null, tint = Accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Создать видео", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text("Опиши свою идею. Помощник добавит кинематографические детали к промпту.", color = Muted)
                        OutlinedTextField(value = prompt, onValueChange = { prompt = it; improved = "" },
                            modifier = Modifier.fillMaxWidth(), minLines = 4,
                            label = { Text("Идея видео") },
                            placeholder = { Text("Например: белый волк идёт по заснеженному лесу на рассвете") })
                        Text("Тип генерации", fontWeight = FontWeight.SemiBold)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("Текст → видео", "Фото → видео").forEachIndexed { i, item ->
                                SegmentedButton(selected = mode == item, onClick = { mode = item },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(item, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        if (mode == "Фото → видео") Text("Фото выберешь уже на странице LTX-2.5.", color = Muted)
                        Text("Длительность в демо выбирается на странице генератора. Здесь желаемая длительность: $duration сек.")
                        Slider(value = duration.toFloat(), onValueChange = { duration = it.toInt() }, valueRange = 2f..10f, steps = 7)
                        Text("Формат кадра: $ratio")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("9:16", "16:9", "1:1").forEachIndexed { i, item ->
                                SegmentedButton(selected = ratio == item, onClick = { ratio = item },
                                    shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(item) }
                            }
                        }
                        Button(onClick = { improved = improvePrompt(prompt) }, enabled = prompt.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Улучшить промпт")
                        }
                        if (improved.isNotBlank()) {
                            Text("Готовый промпт", fontWeight = FontWeight.Bold)
                            Text(improved, color = Muted)
                            OutlinedButton(onClick = { copyText(context, improved) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Скопировать промпт")
                            }
                            Button(onClick = { openGenerator(context) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("Открыть бесплатный LTX-2.5")
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Как получить видео", fontWeight = FontWeight.Bold)
                        Text(
                            """1. Нажми «Улучшить промпт».
2. Скопируй готовое описание.
3. Открой LTX-2.5.
4. Вставь текст, при желании добавь фото и нажми Generate.
5. Дождись очереди и сохрани MP4.""",
                            color = Muted
                        )
                        Text("Бесплатное демо использует общую очередь Hugging Face. Возможны ожидание, лимиты и временная недоступность GPU. Приложение не генерирует видео самостоятельно.", color = Color(0xFFFFD18A), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("My-kolzy AI • прототип бесплатной связки", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
