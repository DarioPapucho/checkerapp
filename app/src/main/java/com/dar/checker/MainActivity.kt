package com.dar.checker

import android.os.Bundle
import android.provider.Settings
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dar.checker.ui.theme.CheckerTheme
import com.dar.checker.logging.LogBus
import com.dar.checker.data.SettingsRepository
import com.dar.checker.data.NotificationStore
import com.dar.checker.data.SentStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

// Colores futuristas
val DarkBackground = Color(0xFF0A0E27)
val CardBackground = Color(0xFF151B3D)
val AccentCyan = Color(0xFF00F5FF)
val AccentPurple = Color(0xFF9D4EDD)
val AccentGreen = Color(0xFF39FF14)
val TextPrimary = Color(0xFFE8F1F5)
val TextSecondary = Color(0xFF8B95A5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheckerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onOpenSettings = { navController.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasAccess by remember { mutableStateOf(false) }
    val settings = remember { SettingsRepository(context) }
    val sentStore = remember { SentStore(context) }
    var sent by remember { mutableStateOf(sentStore.getAll()) }

    // Animación de pulso para el estado
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        hasAccess = isNotificationServiceEnabled(context)
    }

    LaunchedEffect(Unit) {
        LogBus.enabled = settings.isLoggingEnabled()
        LogBus.logs.collectLatest {
            sent = sentStore.getAll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CHECKER",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { inner ->
        Column(
            modifier = modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ESTADO DEL SISTEMA",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (hasAccess) "OPERATIVO" else "INACTIVO",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasAccess) AccentGreen else Color(0xFFFF3B3B)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                if (hasAccess)
                                    AccentGreen.copy(alpha = alpha * 0.3f)
                                else
                                    Color(0xFFFF3B3B).copy(alpha = alpha * 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (hasAccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasAccess) AccentGreen else Color(0xFFFF3B3B),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Botón de permisos (solo si no tiene acceso)
            if (!hasAccess) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple
                    )
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "CONCEDER PERMISOS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Notificaciones enviadas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "NOTIFICACIONES ENVIADAS",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = AccentCyan.copy(alpha = 0.2f)
                        ) {
                            Text(
                                sent.size.toString(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (sent.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = TextSecondary.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Sin notificaciones",
                                    color = TextSecondary,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sent.takeLast(50).reversed()) { record ->
                                NotificationItem(record)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(record: com.dar.checker.data.SentRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DarkBackground.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Parse JSON to extract title and text
            val title = try {
                val json = org.json.JSONObject(record.text)
                json.optString("title", "").ifBlank { json.optString("package", "Unknown") }
            } catch (e: Exception) {
                "Unknown"
            }
            
            val text = try {
                val json = org.json.JSONObject(record.text)
                json.optString("text", "")
            } catch (e: Exception) {
                record.text
            }
            
            Text(
                text = title,
                color = AccentCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            
            val formatted = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(record.time))
            Text(
                text = formatted,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val store = remember { NotificationStore(context) }
    val sentStore = remember { SentStore(context) }
    var host by remember { mutableStateOf(settings.getServerHost()) }
    var newPkg by remember { mutableStateOf("") }
    var allowed by remember { mutableStateOf(settings.getAllowedPackages()) }
    var filterEnabled by remember { mutableStateOf(settings.isPackageFilterEnabled()) }
    var sendAll by remember { mutableStateOf(settings.isSendAllNotifications()) }
    var loggingEnabled by remember { mutableStateOf(settings.isLoggingEnabled()) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var saved by remember { mutableStateOf(store.getAll()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        LogBus.logs.collectLatest { msg ->
            logs = (listOf(msg) + logs).take(100)
            saved = store.getAll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AJUSTES",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Configuración del servidor
            item {
                SettingsSection(title = "SERVIDOR") {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("URL del servidor", color = TextSecondary) },
                        placeholder = { Text("http://192.168.1.10:4444", color = TextSecondary.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { settings.setServerHost(host) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GUARDAR HOST")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val client = okhttp3.OkHttpClient()
                                    val req = okhttp3.Request.Builder()
                                        .url(host + "/health")
                                        .build()
                                    client.newCall(req).execute().use { resp ->
                                        LogBus.log("✓ Health ${host}/health -> ${resp.code}")
                                    }
                                } catch (t: Throwable) {
                                    val name = t::class.simpleName ?: "Exception"
                                    LogBus.log("✗ Health ${host}/health -> ${name}: ${t.message ?: t.toString()}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PROBAR CONEXIÓN")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = filterEnabled, onCheckedChange = {
                            filterEnabled = it
                            settings.setPackageFilterEnabled(it)
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Habilitar filtro por paquete", color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sendAll, onCheckedChange = {
                            sendAll = it
                            settings.setSendAllNotifications(it)
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enviar todas las notificaciones", color = TextPrimary)
                    }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = loggingEnabled, onCheckedChange = {
                    loggingEnabled = it
                    settings.setLoggingEnabled(it)
                    LogBus.enabled = it
                })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Habilitar logs", color = TextPrimary)
            }
                }
            }

            // Paquetes permitidos
            item {
                SettingsSection(title = "PAQUETES PERMITIDOS") {
                    OutlinedTextField(
                        value = newPkg,
                        onValueChange = { newPkg = it },
                        label = { Text("Nombre del paquete", color = TextSecondary) },
                        placeholder = { Text("com.example.app", color = TextSecondary.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newPkg.isNotBlank()) {
                                settings.addAllowedPackage(newPkg)
                                allowed = settings.getAllowedPackages()
                                newPkg = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AGREGAR PAQUETE")
                    }
                }
            }

            // Lista de paquetes
            items(allowed.toList()) { pkg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = CardBackground
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            pkg,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                settings.removeAllowedPackage(pkg)
                                allowed = settings.getAllowedPackages()
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = Color(0xFFFF3B3B)
                            )
                        }
                    }
                }
            }

            // Datos almacenados
            item {
                SettingsSection(title = "DATOS ALMACENADOS") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                store.clear()
                                saved = emptyList()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B3B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Borrar guardadas (${saved.size})")
                        }
                        Button(
                            onClick = {
                                sentStore.clear()
                                logs = emptyList()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B3B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Borrar enviadas")
                        }
                    }
                }
            }

            // Logs
            item {
                SettingsSection(title = "REGISTRO DE ACTIVIDAD") {
                    if (logs.isEmpty()) {
                        Text(
                            "Sin actividad reciente",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            items(logs.take(20)) { log ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = DarkBackground.copy(alpha = 0.5f)
                ) {
                    Text(
                        log,
                        color = TextPrimary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    Text(
                title,
                fontSize = 14.sp,
                color = AccentCyan,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.split(":")?.any { it.contains(pkgName) } == true
}