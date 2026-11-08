package com.mykolzyai.studio

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CenterionMemoryScreen() {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var query by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("android-note") }
    var content by remember { mutableStateOf("") }
    var documents by remember { mutableStateOf(emptyList<CenterionApi.MemoryDocument>()) }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CenterionApi.MemoryDocument?>(null) }
    var status by remember { mutableStateOf("Личная память аккаунта. Записи видны только владельцу.") }

    fun perform(action: () -> String) {
        if (busy) return
        busy = true
        status = "Выполняю запрос…"
        Thread {
            val result = try { action() } catch (e: Exception) {
                "Ошибка: ${e.message ?: "не удалось выполнить запрос"}"
            }
            handler.post {
                status = result
                busy = false
            }
        }.start()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Память Centerion", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Личные заметки аккаунта. Сохранение в память не изменяет веса модели.",
            fontSize = 13.sp
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Поиск по памяти") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = {
                perform {
                    val found = CenterionApi.searchMemory(context, query)
                    handler.post { documents = found }
                    "Найдено записей: ${found.size}"
                }
            },
            enabled = !busy && query.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Найти") }

        HorizontalDivider()
        Text("Новая запись", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = title, onValueChange = { title = it },
            label = { Text("Заголовок") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = source, onValueChange = { source = it },
            label = { Text("Источник / метка") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = content, onValueChange = { content = it },
            label = { Text("Текст заметки") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(
            onClick = {
                val titleToSave = title.trim()
                val sourceToSave = source.trim()
                val contentToSave = content.trim()
                perform {
                    val saved = CenterionApi.saveMemory(context, titleToSave, sourceToSave, contentToSave)
                    handler.post {
                        title = ""
                        content = ""
                        documents = listOf(saved) + documents.filterNot { it.id == saved.id }
                    }
                    "Запись сохранена."
                }
            },
            enabled = !busy && title.isNotBlank() && source.isNotBlank() && content.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить в память") }

        Text(status, fontSize = 12.sp)
        if (documents.isEmpty()) {
            Text(
                if (query.isBlank()) "Пока нет загруженных записей. Выполни поиск или сохрани первую заметку."
                else "По этому запросу записей не найдено.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        documents.forEach { doc ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(doc.title, style = MaterialTheme.typography.titleSmall)
                    Text("Источник: ${doc.sourcePath}", fontSize = 11.sp)
                    if (doc.content.isNotBlank()) Text(doc.content, fontSize = 13.sp)
                    Text("Обновлено: ${doc.updatedAt}", fontSize = 10.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { pendingDelete = doc }, enabled = !busy) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            title = { Text("Удалить запись?") },
            text = { Text("«${doc.title}» будет удалена из личной памяти этого аккаунта.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingDelete = null
                        perform {
                            CenterionApi.deleteMemory(context, doc.id)
                            handler.post { documents = documents.filterNot { it.id == doc.id } }
                            "Запись удалена."
                        }
                    }
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
