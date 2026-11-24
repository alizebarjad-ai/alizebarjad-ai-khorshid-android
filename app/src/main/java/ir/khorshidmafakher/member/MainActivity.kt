package ir.khorshidmafakher.member

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val api by lazy { PortalApi(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF14243A),
                secondary = androidx.compose.ui.graphics.Color(0xFFC7A45B),
                background = androidx.compose.ui.graphics.Color(0xFFF7F5EF)
            )) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MemberApp(api)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberApp(api: PortalApi) {
    var authenticated by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var memberName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        authenticated = runCatching { api.checkSession() }.getOrDefault(false)
        checking = false
        if (authenticated) memberName = runCatching { api.memberName() }.getOrDefault("")
    }
    when {
        checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        !authenticated -> LoginScreen(api) {
            authenticated = true
            scope.launch { memberName = runCatching { api.memberName() }.getOrDefault("") }
        }
        else -> HomeScreen(memberName.ifBlank { "عضو گرامی" }) {
            scope.launch { api.logout(); authenticated = false; memberName = "" }
        }
    }
}

@Composable
private fun LoginScreen(api: PortalApi, onSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("خورشید مفاخر", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("ورود به پنل اختصاصی اعضا")
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(),
            label = { Text("نام کاربری یا ایمیل") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
            label = { Text("گذرواژه") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation())
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            scope.launch {
                busy = true; error = null
                runCatching { api.login(username.trim(), password) }
                    .onSuccess { onSuccess() }
                    .onFailure { error = "ورود ناموفق بود. اطلاعات ورود یا اتصال را بررسی کنید." }
                busy = false
            }
        }, enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("ورود")
        }
    }
}

@Composable
private fun HomeScreen(memberName: String, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("پنل اعضا", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onLogout) { Text("خروج") }
        }
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("درود، $memberName", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("نسخه اولیه اپلیکیشن در حال آماده‌سازی است.")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("در گام بعد، داشبورد، اطلاعیه‌ها، مکاتبات و پیشنهاد پروژه به APIهای پورتال متصل می‌شوند.")
    }
}
