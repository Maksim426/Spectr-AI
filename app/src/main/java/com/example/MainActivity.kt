package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val isThinking: Boolean = false
)

class MainActivity : ComponentActivity() {

    companion object {
        // Константа для ключа API. Сюда можно вставить свой ключ.
        const val SPECTR_API_KEY = "sk-751feae49fc34f648def1be21dcd1364"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpectrApp()
        }
    }
}

@Composable
fun SpectrApp() {
    // Состояние темы: true — тёмная тема, false — светлая тема
    var isDarkTheme by remember { mutableStateOf(true) }
    var userQuery by remember { mutableStateOf("") }
    
    // История сообщений
    var messages by remember { mutableStateOf(listOf(
        ChatMessage("Здравствуйте! Я Spectr AI. Чем я могу помочь вам сегодня?", isUser = false)
    )) }
    
    // История чатов (для примера)
    val chatHistory = remember { mutableStateOf(listOf("Чат 1", "Чат 2")) }

    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Настройка цветовой палитры в соответствии с требованиями к интерфейсу
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFF000000)
    val drawerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF0F0F0)
    val buttonColor = if (isDarkTheme) Color(0xFF333333) else Color(0xFFE0E0E0)
    
    // Специфические цвета бабблов и панелей по ТЗ
    val userBubbleColor = if (isDarkTheme) Color(0xFF222222) else Color(0xFFE0E0E0)
    val aiBubbleColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
    val inputBgColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFF0F0F0)

    // Автопрокрутка к последнему сообщению при добавлении новых сообщений
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 300.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(drawerColor)
                        .padding(16.dp)
                ) {
                    Text("Spectr AI", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor, modifier = Modifier.padding(bottom = 24.dp))
                    
                    Text("ЧАТЫ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chatHistory.value) { chat ->
                            Text(chat, color = textColor, modifier = Modifier.fillMaxWidth().clickable { /* Switch chat */ }.padding(8.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = Color.Gray)
                    
                    Text("Настройки", fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(vertical = 16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().clickable { isDarkTheme = !isDarkTheme }) {
                        Text("Тёмная тема", color = textColor)
                        // Simple toggle indicator
                        Text(if (isDarkTheme) "Вкл" else "Выкл", color = if (isDarkTheme) Color.Cyan else Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top=16.dp).clickable { messages = listOf(ChatMessage("Здравствуйте! Я Spectr AI. Чем я могу помочь вам сегодня?", isUser = false)) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистить", tint = Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Очистить историю", color = Color.Red)
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Меню", tint = textColor)
                    }
                    Text(
                        text = "Spectr AI",
                        color = textColor,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    IconButton(onClick = { messages = listOf(ChatMessage("Здравствуйте! Я Spectr AI. Чем я могу помочь вам сегодня?", isUser = false)) }) {
                        Icon(Icons.Default.Add, contentDescription = "Новый чат", tint = textColor)
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(innerPadding)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // 2. Область вывода ответов
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("chat_history_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                        val bubbleBgColor = if (msg.isUser) userBubbleColor else aiBubbleColor
                        
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = alignment
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bubbleBgColor)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = msg.content,
                                    color = textColor,
                                    style = TextStyle(
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 15.sp
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // 3 и 4. Панель ввода
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = userQuery,
                        onValueChange = { userQuery = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedContainerColor = inputBgColor,
                            unfocusedContainerColor = inputBgColor,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        textStyle = TextStyle(
                            color = textColor,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.sp
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("prompt_input_field"),
                        placeholder = {
                            Text(
                                text = "Сообщение...",
                                color = if (isDarkTheme) Color.Gray else Color.DarkGray,
                                fontSize = 15.sp
                            )
                        }
                    )

                    FilledIconButton(
                        onClick = {
                            if (userQuery.isNotBlank() && !isLoading) {
                                val queryToSend = userQuery
                                userQuery = ""
                                
                                val updatedMessages = messages.toMutableList()
                                updatedMessages.add(ChatMessage(queryToSend, isUser = true))
                                updatedMessages.add(ChatMessage("Думаю...", isUser = false, isThinking = true))
                                messages = updatedMessages
                                isLoading = true

                                val apiKey = if (MainActivity.SPECTR_API_KEY != "PLACEHOLDER") {
                                    MainActivity.SPECTR_API_KEY
                                } else {
                                    BuildConfig.SPECTR_API_KEY
                                }

                                if (apiKey == "PLACEHOLDER" || apiKey.isBlank()) {
                                    val finalMessages = messages.toMutableList()
                                    if (finalMessages.isNotEmpty()) {
                                        finalMessages.removeAt(finalMessages.size - 1)
                                    }
                                    finalMessages.add(ChatMessage("Ошибка: Настройте SPECTR_API_KEY в коде или в panel Secrets.", isUser = false))
                                    messages = finalMessages
                                    isLoading = false
                                } else {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        callSpectrWithContext(messages.filter { !it.isThinking }, apiKey) { result ->
                                            coroutineScope.launch(Dispatchers.Main) {
                                                val finalMessages = messages.toMutableList()
                                                if (finalMessages.isNotEmpty() && finalMessages.last().isThinking) {
                                                    finalMessages.removeAt(finalMessages.size - 1)
                                                }
                                                finalMessages.add(ChatMessage(result, isUser = false))
                                                messages = finalMessages
                                                isLoading = false
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isDarkTheme) Color(0xFF007AFF) else Color(0xFF007AFF),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                    }
                }
            }
        }
    }
}

private fun callSpectrWithContext(
    history: List<ChatMessage>,
    apiKey: String,
    onResult: (String) -> Unit
) {
    val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    try {
        val requestJson = JSONObject().apply {
            put("model", "deepseek-chat")
            
            // Превращаем историю диалога в массив messages для API
            val messagesArray = JSONArray()
            for (msg in history) {
                messagesArray.put(JSONObject().apply {
                    put("role", if (msg.isUser) "user" else "assistant")
                    put("content", msg.content)
                })
            }
            put("messages", messagesArray)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorString = response.body?.string() ?: ""
                if (response.code == 402) {
                    onResult("Ошибка: Средства на счете API исчерпаны (402). Пожалуйста, пополните баланс.")
                } else {
                    onResult("Ошибка Spectr AI API (${response.code}):\n$errorString")
                }
            } else {
                val responseBodyString = response.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBodyString)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val message = choice.getJSONObject("message")
                    val content = message.getString("content")
                    onResult(content)
                } else {
                    onResult("Пустой ответ от Spectr AI.")
                }
            }
        }
    } catch (e: IOException) {
        onResult("Ошибка сети: ${e.localizedMessage ?: e.toString()}")
    } catch (e: Exception) {
        onResult("Ошибка: ${e.localizedMessage ?: e.toString()}")
    }
}


