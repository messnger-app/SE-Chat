package com.sechat.messenger

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

// ---------------- SUPABASE SETUP & MODELS ----------------
object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://pcpskdouwoeqnbpmlhnv.supabase.co",
        supabaseKey = "sb_publishable_IgA_3VYiGtj9rtTdwl83hw_vavWrDlU"
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("second_name") val secondName: String,
    val age: Int,
    val place: String
)

@Serializable
data class Message(
    val id: String? = null,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    @SerialName("message_text") val messageText: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("media_type") val mediaType: String = "text",
    @SerialName("created_at") val createdAt: String? = null
)

// ---------------- MAIN ACTIVITY ----------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SEChatApp()
                }
            }
        }
    }
}

@Composable
fun SEChatApp() {
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var selectedUserForChat by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(Unit) {
        currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id
    }

    when {
        currentUserId == null -> {
            AuthScreen(onLoginSuccess = { uid -> currentUserId = uid })
        }
        selectedUserForChat == null -> {
            UsersListScreen(
                currentUserId = currentUserId!!,
                onUserClick = { user -> selectedUserForChat = user },
                onLogout = { currentUserId = null }
            )
        }
        else -> {
            ChatScreen(
                currentUserId = currentUserId!!,
                receiver = selectedUserForChat!!,
                onBack = { selectedUserForChat = null }
            )
        }
    }
}

// ---------------- 1. SIGN UP & LOGIN SCREEN ----------------
@Composable
fun AuthScreen(onLoginSuccess: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoginMode by remember { mutableStateOf(true) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var secondName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SE Chat", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isLoginMode) "ലോഗിൻ ചെയ്യുക" else "പുതിയ അക്കൗണ്ട് തുടങ്ങുക",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (!isLoginMode) {
            OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = secondName, onValueChange = { secondName = it }, label = { Text("Second Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age (വയസ്സ്)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = place, onValueChange = { place = it }, label = { Text("സ്ഥലം (Place)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        if (isLoginMode) {
                            SupabaseClient.client.auth.signInWith(Email) {
                                this.email = email.trim()
                                this.password = password
                            }
                        } else {
                            SupabaseClient.client.auth.signUpWith(Email) {
                                this.email = email.trim()
                                this.password = password
                            }
                            val uid = SupabaseClient.client.auth.currentUserOrNull()?.id
                            if (uid != null) {
                                val profile = UserProfile(
                                    id = uid,
                                    email = email.trim(),
                                    firstName = firstName.trim(),
                                    secondName = secondName.trim(),
                                    age = age.toIntOrNull() ?: 0,
                                    place = place.trim()
                                )
                                SupabaseClient.client.from("profiles").insert(profile)
                            }
                        }
                        val uid = SupabaseClient.client.auth.currentUserOrNull()?.id
                        if (uid != null) onLoginSuccess(uid)
                    } catch (e: Exception) {
                        Toast.makeText(context, "പിശക്: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "കാത്തിരിക്കൂ..." else if (isLoginMode) "Login" else "Sign Up")
        }

        TextButton(onClick = { isLoginMode = !isLoginMode }) {
            Text(if (isLoginMode) "അക്കൗണ്ട് ഇല്ലേ? Sign Up ചെയ്യാം" else "അക്കൗണ്ട് ഉണ്ടോ? Login ചെയ്യാം")
        }
    }
}

// ---------------- 2. USERS LIST SCREEN ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersListScreen(currentUserId: String, onUserClick: (UserProfile) -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val allUsers = SupabaseClient.client.from("profiles").select().decodeList<UserProfile>()
            users = allUsers.filter { it.id != currentUserId }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SE Chat", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            SupabaseClient.client.auth.signOut()
                            onLogout()
                        }
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(users) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onUserClick(user) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "${user.firstName} ${user.secondName}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(text = "സ്ഥലം: ${user.place} | വയസ്സ്: ${user.age}", fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ---------------- 3. CHAT SCREEN ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(currentUserId: String, receiver: UserProfile, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var textMessage by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }

    suspend fun loadMessages() {
        val allMsgs = SupabaseClient.client.from("messages").select().decodeList<Message>()
        messages = allMsgs.filter {
            (it.senderId == currentUserId && it.receiverId == receiver.id) ||
            (it.senderId == receiver.id && it.receiverId == currentUserId)
        }
    }

    LaunchedEffect(receiver.id) {
        loadMessages()
        val channel = SupabaseClient.client.realtime.channel("chat_${currentUserId}_${receiver.id}")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }
        changeFlow.onEach { loadMessages() }.launchIn(scope)
        channel.subscribe()
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploading = true
                try {
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val isVideo = mimeType.startsWith("video")
                    val ext = if (isVideo) "mp4" else "jpg"
                    val fileName = "${UUID.randomUUID()}.$ext"

                    val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    if (bytes != null) {
                        val bucket = SupabaseClient.client.storage.from("chat_media")
                        bucket.upload(fileName, bytes)
                        val publicUrl = bucket.publicUrl(fileName)

                        val msg = Message(
                            senderId = currentUserId,
                            receiverId = receiver.id,
                            mediaUrl = publicUrl,
                            mediaType = if (isVideo) "video" else "image"
                        )
                        SupabaseClient.client.from("messages").insert(msg)
                        loadMessages()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "അപ്‌ലോഡ് പരാജയപ്പെട്ടു: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${receiver.firstName} (${receiver.place})") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isUploading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val isMine = msg.senderId == currentUserId
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isMine) Color(0xFFDCF8C6) else Color(0xFFECECEC))
                                .padding(10.dp)
                                .widthIn(max = 260.dp)
                        ) {
                            when (msg.mediaType) {
                                "image" -> {
                                    AsyncImage(
                                        model = msg.mediaUrl,
                                        contentDescription = "Photo",
                                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                "video" -> {
                                    msg.mediaUrl?.let { VideoPlayerItem(videoUrl = it) }
                                }
                                else -> {
                                    Text(text = msg.messageText ?: "", fontSize = 16.sp, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Attach Photo/Video")
                }

                OutlinedTextField(
                    value = textMessage,
                    onValueChange = { textMessage = it },
                    placeholder = { Text("മെസ്സേജ് ടൈപ്പ് ചെയ്യുക...") },
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = {
                    if (textMessage.isNotBlank()) {
                        val msgToSend = textMessage.trim()
                        textMessage = ""
                        scope.launch {
                            val msg = Message(
                                senderId = currentUserId,
                                receiverId = receiver.id,
                                messageText = msgToSend,
                                mediaType = "text"
                            )
                            SupabaseClient.client.from("messages").insert(msg)
                            loadMessages()
                        }
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun VideoPlayerItem(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { PlayerView(context).apply { player = exoPlayer } },
        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp))
    )
}
