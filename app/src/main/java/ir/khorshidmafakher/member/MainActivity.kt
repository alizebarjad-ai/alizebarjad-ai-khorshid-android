package ir.khorshidmafakher.member

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Navy = Color(0xFF14243A)
private val Gold = Color(0xFFC7A45B)
private val Cream = Color(0xFFF7F5EF)

class MainActivity : ComponentActivity() {
    private val api by lazy { PortalApi(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Navy, secondary = Gold, background = Cream, surface = Color.White
            )) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = Cream) { MemberApp(api) }
                }
            }
        }
    }
}

private enum class MemberTab(val label: String) {
    HOME("خانه"), NOTICES("اطلاعیه‌ها"), MESSAGES("پیام‌ها"), PROJECTS("پروژه‌ها"), ACTIVITY("فعالیت‌ها"), PROFILE("پروفایل")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberApp(api: PortalApi) {
    var authenticated by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var dashboard by remember { mutableStateOf(JSONObject()) }
    var notices by remember { mutableStateOf(JSONArray()) }
    var conversations by remember { mutableStateOf(JSONArray()) }
    var projects by remember { mutableStateOf(JSONArray()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(MemberTab.HOME) }
    val scope = rememberCoroutineScope()

    suspend fun refreshAll() {
        busy = true
        error = null
        try {
            dashboard = api.dashboard()
            val dashboardNotices = dashboard.optJSONArray("notices") ?: JSONArray()
            notices = runCatching { mergeNotices(dashboardNotices, api.publicNotices()) }.getOrElse { dashboardNotices }
            conversations = api.conversations()
            projects = api.projects()
        } catch (e: Exception) {
            error = e.message ?: "دریافت اطلاعات ناموفق بود."
            if (e is PortalException && e.status == 401) authenticated = false
        } finally { busy = false }
    }

    LaunchedEffect(Unit) {
        authenticated = api.checkSession()
        checking = false
        if (authenticated) refreshAll()
    }

    when {
        checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        !authenticated -> LoginScreen(api) {
            authenticated = true
            scope.launch { refreshAll() }
        }
        else -> Scaffold(
            containerColor = Cream,
            topBar = {
                TopAppBar(
                    title = { Text("خورشید مفاخر", color = Color.White) },
                    actions = {
                        TextButton(onClick = {
                            scope.launch { runCatching { api.logout() }; authenticated = false }
                        }) { Text("خروج", color = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    MemberTab.values().forEach { tab ->
                        val icon = when (tab) {
                            MemberTab.HOME -> Icons.Default.Home
                            MemberTab.NOTICES -> Icons.Default.Campaign
                            MemberTab.MESSAGES -> Icons.Default.Chat
                            MemberTab.PROJECTS -> Icons.Default.Lightbulb
                            MemberTab.ACTIVITY -> Icons.Default.DateRange
                            MemberTab.PROFILE -> Icons.Default.AccountCircle
                        }
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (error != null) {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8E6))) {
                        Column(Modifier.padding(12.dp)) {
                            Text(error ?: "", color = Color(0xFF9C2F25))
                            TextButton(onClick = { scope.launch { refreshAll() } }) { Text("تلاش دوباره") }
                        }
                    }
                }
                if (busy && dashboard.length() == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else when (selectedTab) {
                    MemberTab.HOME -> DashboardScreen(dashboard, notices, conversations.length(), projects.length(),
                        onRefresh = { scope.launch { refreshAll() } })
                    MemberTab.NOTICES -> NoticesScreen(notices, dashboard, api) { scope.launch { refreshAll() } }
                    MemberTab.MESSAGES -> MessagesScreen(conversations, api) { scope.launch { refreshAll() } }
                    MemberTab.PROJECTS -> ProjectsScreen(projects, api) { scope.launch { refreshAll() } }
                    MemberTab.ACTIVITY -> MemberActivityScreen(api) { scope.launch { refreshAll() } }
                    MemberTab.PROFILE -> ProfileScreen(api, dashboard) { scope.launch { refreshAll() } }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(api: PortalApi, onSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("خورشید مفاخر", style = MaterialTheme.typography.headlineMedium, color = Navy)
        Spacer(Modifier.height(8.dp))
        Text("ورود به پنل اختصاصی اعضا")
        Spacer(Modifier.height(16.dp))
        NetworkEntryCard()
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(),
            label = { Text("نام کاربری یا ایمیل") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
            label = { Text("گذرواژه") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            scope.launch {
                busy = true; error = null
                runCatching { api.login(username.trim(), password) }
                    .onSuccess { onSuccess() }
                    .onFailure { error = it.message ?: "ورود ناموفق بود." }
                busy = false
            }
        }, enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("ورود")
        }
    }
}

@Composable
private fun DashboardScreen(data: JSONObject, notices: JSONArray, messageCount: Int, projectCount: Int, onRefresh: () -> Unit) {
    val member = data.optJSONObject("member") ?: JSONObject()
    val notifications = data.optJSONArray("notifications") ?: JSONArray()
    val advertisements = data.optJSONArray("advertisements") ?: JSONArray()
    val programs = data.optJSONArray("programs") ?: JSONArray()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("داشبورد عضو", style = MaterialTheme.typography.headlineSmall, color = Navy)
                TextButton(onClick = onRefresh) { Text("به‌روزرسانی") }
            }
        }
        item { NetworkEntryCard() }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Navy)) {
                Column(Modifier.padding(20.dp)) {
                    Text("خوش آمدید", color = Gold, style = MaterialTheme.typography.labelLarge)
                    Text(member.optString("fullName").ifBlank { "عضو گرامی" },
                        color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("وضعیت عضویت: ${member.optString("status").ifBlank { "در حال بررسی" }}",
                        color = Color.White)
                    Text("امتیاز: ${member.optInt("points", 0)}", color = Color.White)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("پیام‌ها", messageCount.toString(), Modifier.weight(1f))
                MetricCard("پروژه‌ها", projectCount.toString(), Modifier.weight(1f))
                MetricCard("اطلاعیه‌ها", notices.length().toString(), Modifier.weight(1f))
            }
        }
        item { Text("آخرین اطلاعیه‌ها", style = MaterialTheme.typography.titleLarge, color = Navy) }
        if (notices.length() == 0) item { EmptyCard("اطلاعیه فعالی برای نمایش وجود ندارد.") }
        else items(notices.takeObjects(3), key = { it.optString("id") + it.optString("title") }) { NoticeCard(it) }
        item { Text("اعلان‌های شخصی", style = MaterialTheme.typography.titleLarge, color = Navy) }
        if (notifications.length() == 0) item { EmptyCard("اعلان جدیدی ندارید.") }
        else items(notifications.takeObjects(3), key = { it.optString("id") }) { NotificationCard(it) }

        item { Text("فرصت‌ها و آگهی‌ها", style = MaterialTheme.typography.titleLarge, color = Navy) }
        if (advertisements.length() == 0) item { EmptyCard("در حال حاضر فرصت یا آگهی فعالی ثبت نشده است.") }
        else items(advertisements.takeObjects(4), key = { it.optString("id").ifBlank { it.optString("title") } }) {
            DashboardContentCard(it, "فرصت یا آگهی")
        }

        item { Text("برنامه‌ها و رویدادها", style = MaterialTheme.typography.titleLarge, color = Navy) }
        if (programs.length() == 0) item { EmptyCard("برنامه یا رویداد قابل نمایشی وجود ندارد.") }
        else items(programs.takeObjects(4), key = { it.optString("id").ifBlank { it.optString("title") } }) {
            DashboardContentCard(it, "برنامه یا رویداد")
        }
    }
}

@Composable
private fun DashboardContentCard(item: JSONObject, fallbackTitle: String) {
    val title = item.optString("title")
        .ifBlank { item.optString("name") }
        .ifBlank { item.optString("subject") }
        .ifBlank { fallbackTitle }
    val description = item.optString("summary")
        .ifBlank { item.optString("description") }
        .ifBlank { item.optString("body") }
        .ifBlank { item.optString("content") }
    val date = item.optString("startsAt")
        .ifBlank { item.optString("eventDate") }
        .ifBlank { item.optString("publishedAt") }
        .ifBlank { item.optString("createdAt") }
    val endDate = item.optString("endsAt")
    val clubName = item.optJSONObject("club")?.optString("name").orEmpty()
    val destination = item.optString("destinationUrl")
    val context = LocalContext.current
    val clickableModifier = if (destination.startsWith("https://", ignoreCase = true)) {
        Modifier.fillMaxWidth().clickable {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination))) }
        }
    } else Modifier.fillMaxWidth()
    Card(clickableModifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Navy)
            if (description.isNotBlank()) Text(description)
            if (date.isNotBlank()) Text("شروع / انتشار: $date", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            if (endDate.isNotBlank()) Text("پایان: $endDate", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            if (clubName.isNotBlank()) Text("باشگاه: $clubName", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            if (destination.startsWith("https://", ignoreCase = true)) {
                Text("مشاهده جزئیات ↗", color = Gold, style = MaterialTheme.typography.labelLarge)
            }
            val status = item.optString("status")
            if (status.isNotBlank()) Text("وضعیت: $status", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = Navy)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NoticesScreen(notices: JSONArray, data: JSONObject, api: PortalApi, onChanged: () -> Unit) {
    val notifications = data.optJSONArray("notifications") ?: JSONArray()
    var busyId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Text("اطلاعیه‌ها", style = MaterialTheme.typography.headlineSmall, color = Navy) }
        item { Text("اطلاعیه‌های عمومی و متناسب با عضویت شما", color = Color.DarkGray) }
        if (notices.length() == 0) item { EmptyCard("اطلاعیه‌ای برای نمایش وجود ندارد.") }
        items(notices.takeObjects(), key = { it.optString("id") + it.optString("title") }) { NoticeCard(it) }
        item { Divider(); Text("اعلان‌های شخصی", style = MaterialTheme.typography.titleLarge, color = Navy) }
        if (notifications.length() == 0) item { EmptyCard("اعلان شخصی جدیدی ندارید.") }
        items(notifications.takeObjects(), key = { it.optString("id") }) { notification ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(notification.optString("title"), style = MaterialTheme.typography.titleMedium)
                    Text(notification.optString("body"))
                    val id = notification.optString("id")
                    if (id.isNotBlank() && notification.isNull("readAt")) {
                        TextButton(enabled = busyId != id, onClick = {
                            scope.launch {
                                busyId = id
                                runCatching { api.markNotificationRead(id) }
                                busyId = ""
                                onChanged()
                            }
                        }) { Text("علامت‌گذاری به‌عنوان خوانده‌شده") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(item: JSONObject) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(item.optString("title").ifBlank { "اطلاعیه" }, style = MaterialTheme.typography.titleMedium, color = Navy)
            Spacer(Modifier.height(6.dp))
            Text(item.optString("body").ifBlank { item.optString("content") })
            val date = item.optString("publishedAt").ifBlank { item.optString("createdAt") }
            if (date.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(date, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
        }
    }
}

@Composable
private fun NotificationCard(item: JSONObject) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(item.optString("title"), style = MaterialTheme.typography.titleSmall)
            Text(item.optString("body"))
        }
    }
}

@Composable
private fun MessagesScreen(conversationItems: JSONArray, api: PortalApi, onChanged: () -> Unit) {
    var showNew by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("MEMBERS_DEPUTY") }
    var newBody by remember { mutableStateOf("") }
    var replyBody by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val recipientOptions = listOf(
        "MEMBERS_DEPUTY" to "معاونت اعضا", "ARTS_DEPUTY" to "معاونت هنری",
        "CSR_DEPUTY" to "معاونت اجتماعی", "EDUCATION_DEPUTY" to "معاونت آموزش",
        "ECONOMIC_DEPUTY" to "معاونت اقتصادی", "MEDIA_DEPUTY" to "معاونت رسانه", "OWNER" to "مدیریت پورتال"
    )
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("مکاتبات", style = MaterialTheme.typography.headlineSmall, color = Navy)
                Button(onClick = { showNew = !showNew }) { Text(if (showNew) "بستن فرم" else "پیام جدید") }
            }
        }
        if (showNew) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ارسال پیام به", style = MaterialTheme.typography.titleMedium)
                    recipientOptions.forEach { (key, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { recipient = key }) {
                            RadioButton(selected = recipient == key, onClick = { recipient = key })
                            Text(label)
                        }
                    }
                    OutlinedTextField(newBody, { newBody = it }, Modifier.fillMaxWidth(),
                        label = { Text("متن پیام") }, minLines = 3)
                    Button(enabled = !busy && newBody.trim().length >= 3, onClick = {
                        scope.launch {
                            busy = true; feedback = null
                            runCatching { api.sendMessage(recipient, newBody.trim()) }
                                .onSuccess { newBody = ""; showNew = false; feedback = "پیام ارسال شد."; onChanged() }
                                .onFailure { feedback = it.message }
                            busy = false
                        }
                    }) { Text(if (busy) "در حال ارسال..." else "ارسال پیام") }
                }
            }
        }
        feedback?.let { message -> item { Text(message, color = Navy) } }
        if (conversationItems.length() == 0) item { EmptyCard("هنوز مکاتبه‌ای ثبت نکرده‌اید.") }
        items(conversationItems.takeObjects(), key = { it.optString("id") }) { conversation ->
            val id = conversation.optString("id")
            val messages = conversation.optJSONArray("messages") ?: JSONArray()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(conversation.optString("subject").ifBlank { "مکاتبه با ${recipientOptions.firstOrNull { it.first == conversation.optString("recipientRole") }?.second ?: "واحد سازمانی"}" },
                        style = MaterialTheme.typography.titleMedium, color = Navy)
                    Text("وضعیت: ${conversation.optString("status").ifBlank { "ثبت‌شده" }}", style = MaterialTheme.typography.labelSmall)
                    for (index in 0 until messages.length()) {
                        val message = messages.optJSONObject(index) ?: continue
                        Divider()
                        Text(if (message.optString("senderType") == "MEMBER") "شما" else "پاسخ سازمان",
                            style = MaterialTheme.typography.labelMedium, color = Gold)
                        Text(message.optString("body"))
                    }
                    TextButton(onClick = { expandedId = if (expandedId == id) "" else id }) {
                        Text(if (expandedId == id) "بستن پاسخ" else "پاسخ به مکاتبه")
                    }
                    if (expandedId == id && conversation.optString("status") != "CLOSED") {
                        OutlinedTextField(replyBody, { replyBody = it }, Modifier.fillMaxWidth(),
                            label = { Text("متن پاسخ") }, minLines = 2)
                        Button(enabled = !busy && replyBody.trim().length >= 2, onClick = {
                            scope.launch {
                                busy = true; feedback = null
                                runCatching { api.reply(id, replyBody.trim()) }
                                    .onSuccess { replyBody = ""; expandedId = ""; feedback = "پاسخ ارسال شد."; onChanged() }
                                    .onFailure { feedback = it.message }
                                busy = false
                            }
                        }) { Text("ارسال پاسخ") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsScreen(projectItems: JSONArray, api: PortalApi, onChanged: () -> Unit) {
    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var objective by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var impact by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var timeline by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("پیشنهادهای پروژه", style = MaterialTheme.typography.headlineSmall, color = Navy)
                Button(onClick = { showForm = !showForm }) { Text(if (showForm) "بستن" else "پیشنهاد جدید") }
            }
        }
        if (showForm) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ثبت پیشنهاد پروژه", style = MaterialTheme.typography.titleLarge)
                    FormField("عنوان طرح", title, { title = it })
                    FormField("هدف اصلی (حداقل ۱۰ نویسه)", objective, { objective = it }, 2)
                    FormField("شرح کامل (حداقل ۲۰ نویسه)", description, { description = it }, 4)
                    FormField("اثر مورد انتظار (حداقل ۱۰ نویسه)", impact, { impact = it }, 2)
                    FormField("جامعه هدف", audience, { audience = it })
                    FormField("محدوده اجرا", location, { location = it })
                    FormField("زمان‌بندی", timeline, { timeline = it })
                    FormField("بودجه برآوردی", budget, { budget = it })
                    Text("این پیشنهاد طبق فرایند پورتال به معاونت اجتماعی و مسئولیت اجتماعی ارجاع می‌شود.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Button(enabled = !busy && title.trim().length >= 3 && objective.trim().length >= 10 &&
                        description.trim().length >= 20 && impact.trim().length >= 10,
                        onClick = {
                            scope.launch {
                                busy = true; feedback = null
                                runCatching { api.submitProposal(title, objective, description, impact, audience, location, timeline, budget) }
                                    .onSuccess {
                                        feedback = "پیشنهاد با موفقیت ثبت شد."
                                        showForm = false
                                        title = ""; objective = ""; description = ""; impact = ""
                                        audience = ""; location = ""; timeline = ""; budget = ""
                                        onChanged()
                                    }.onFailure { feedback = it.message }
                                busy = false
                            }
                        }) { Text(if (busy) "در حال ثبت..." else "ثبت پیشنهاد") }
                }
            }
        }
        feedback?.let { item { Text(it, color = Navy) } }
        if (projectItems.length() == 0) item { EmptyCard("هنوز پیشنهادی ثبت نکرده‌اید.") }
        items(projectItems.takeObjects(), key = { it.optString("id") }) { project ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(project.optString("title"), style = MaterialTheme.typography.titleMedium, color = Navy)
                    Text("کد پرونده: ${project.optString("code").ifBlank { "در حال تخصیص" }}")
                    Text("مرحله: ${project.optString("workflowStage").ifBlank { project.optString("status") }}")
                    Text("پیشرفت: ${project.optInt("progressPercent", 0)}٪")
                    if (project.optString("proposalObjective").isNotBlank()) Text("هدف: ${project.optString("proposalObjective")}")
                }
            }
        }
    }
}

@Composable
private fun FormField(label: String, value: String, onValue: (String) -> Unit, lines: Int = 1) {
    OutlinedTextField(value, onValue, Modifier.fillMaxWidth(), label = { Text(label) }, minLines = lines)
}

@Composable
private fun MemberActivityScreen(api: PortalApi, onChanged: () -> Unit) {
    var data by remember { mutableStateOf(JSONObject()) }
    var loading by remember { mutableStateOf(true) }
    var busyProgramId by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    suspend fun refresh() {
        loading = true
        failure = null
        runCatching { api.memberGrowth() }
            .onSuccess { data = it }
            .onFailure { failure = it.message ?: "دریافت سوابق فعالیت ناموفق بود." }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }
    val programs = data.optJSONArray("programs") ?: JSONArray()
    val registrations = data.optJSONArray("registrations") ?: JSONArray()
    val interests = data.optJSONArray("interests") ?: JSONArray()
    val activities = data.optJSONArray("activities") ?: JSONArray()
    val pointsHistory = data.optJSONArray("pointsHistory") ?: JSONArray()
    val member = data.optJSONObject("member") ?: JSONObject()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("فعالیت‌ها و رویدادها", style = MaterialTheme.typography.headlineSmall, color = Navy)
                TextButton(onClick = { scope.launch { refresh() } }) { Text("به‌روزرسانی") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Navy)) {
                Column(Modifier.padding(16.dp)) {
                    Text("امتیاز کل فعالیت‌ها", color = Gold, style = MaterialTheme.typography.labelLarge)
                    Text(member.optInt("points", 0).toString(), color = Color.White, style = MaterialTheme.typography.headlineLarge)
                    Text("امتیازهای تأییدشده و سوابق حضور از پورتال دریافت می‌شوند.", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        feedback?.let { item { Text(it, color = Color(0xFF2E6B45)) } }
        failure?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (loading && data.length() == 0) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            item { Text("برنامه‌ها و رویدادهای منتشرشده", style = MaterialTheme.typography.titleLarge, color = Navy) }
            if (programs.length() == 0) item { EmptyCard("برنامه منتشرشده‌ای برای نمایش وجود ندارد.") }
            items(programs.takeObjects(), key = { it.optString("id") }) { program ->
                val id = program.optString("id")
                val registration = registrations.takeObjects().firstOrNull { it.optString("programId") == id }
                val isRegistered = registration?.optString("status") == "REGISTERED"
                val externalUrl = program.optString("registrationUrl")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(program.optString("title"), style = MaterialTheme.typography.titleMedium, color = Navy)
                        program.optString("description").takeIf { it.isNotBlank() }?.let { Text(it) }
                        program.optString("startsAt").takeIf { it.isNotBlank() }?.let { Text("زمان: ${it.replace("T", " ").take(16)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                        program.optString("location").takeIf { it.isNotBlank() }?.let { Text("محل برگزاری: $it", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                        if (externalUrl.isNotBlank()) {
                            Button(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl))) } }, modifier = Modifier.fillMaxWidth()) {
                                Text("ثبت‌نام / جزئیات در سایت برگزارکننده")
                            }
                        } else {
                            Text(if (isRegistered) "وضعیت: ثبت‌نام شما قطعی شده" else "وضعیت: هنوز ثبت‌نام نکرده‌اید",
                                style = MaterialTheme.typography.bodySmall, color = if (isRegistered) Color(0xFF2E6B45) else Color.Gray)
                            Button(enabled = id.isNotBlank() && busyProgramId.isBlank(), onClick = {
                                scope.launch {
                                    busyProgramId = id
                                    feedback = null
                                    runCatching { if (isRegistered) api.cancelProgramRegistration(id) else api.registerProgram(id) }
                                        .onSuccess {
                                            feedback = if (isRegistered) "ثبت‌نام برنامه لغو شد." else "ثبت‌نام شما در برنامه انجام شد."
                                            refresh()
                                            onChanged()
                                        }.onFailure { feedback = it.message ?: "عملیات ثبت‌نام انجام نشد." }
                                    busyProgramId = ""
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                if (busyProgramId == id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text(if (isRegistered) "لغو ثبت‌نام" else "ثبت‌نام در برنامه")
                            }
                        }
                    }
                }
            }
            item { Text("سوابق ثبت‌نام و حضور", style = MaterialTheme.typography.titleLarge, color = Navy) }
            if (registrations.length() == 0) item { EmptyCard("هنوز در برنامه‌ای ثبت‌نام نکرده‌اید.") }
            items(registrations.takeObjects(), key = { it.optString("programId") }) { registration ->
                val program = registration.optJSONObject("program") ?: JSONObject()
                val attendedAt = registration.optString("attendedAt")
                val status = registration.optString("status")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(program.optString("title").ifBlank { "برنامه" }, color = Navy, style = MaterialTheme.typography.titleMedium)
                        Text("وضعیت: ${if (attendedAt.isNotBlank()) "حضور ثبت‌شده" else if (status == "CANCELLED") "لغوشده" else if (status == "REGISTERED") "ثبت‌نام‌شده" else status}")
                        registration.optString("registeredAt").takeIf { it.isNotBlank() }?.let { Text("تاریخ ثبت‌نام: ${it.replace("T", " ").take(16)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                        if (attendedAt.isNotBlank()) Text("تاریخ حضور: ${attendedAt.replace("T", " ").take(16)}", color = Color(0xFF2E6B45), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("فرصت‌های مشارکت و همکاری", style = MaterialTheme.typography.titleLarge, color = Navy) }
            if (programs.length() == 0) item { EmptyCard("فرصتی برای اعلام علاقه وجود ندارد.") }
            items(programs.takeObjects(), key = { "interest-${it.optString("id")}" }) { program ->
                val id = program.optString("id")
                val interested = interests.takeObjects().any { it.optString("programId") == id && it.optString("status") == "INTERESTED" }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(program.optString("title"), color = Navy, style = MaterialTheme.typography.titleMedium)
                        Text("اعلام علاقه به همکاری، ثبت‌نام در رویداد محسوب نمی‌شود.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = busyProgramId.isBlank(), onClick = {
                            scope.launch {
                                busyProgramId = id
                                feedback = null
                                runCatching { if (interested) api.withdrawProgramInterest(id) else api.expressProgramInterest(id) }
                                    .onSuccess {
                                        feedback = if (interested) "اعلام علاقه لغو شد." else "علاقه‌مندی شما ثبت شد."
                                        refresh()
                                        onChanged()
                                    }.onFailure { feedback = it.message ?: "ثبت علاقه انجام نشد." }
                                busyProgramId = ""
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (interested) "لغو اعلام علاقه" else "اعلام علاقه به همکاری")
                        }
                    }
                }
            }
            item { Text("تاریخچه امتیازها", style = MaterialTheme.typography.titleLarge, color = Navy) }
            if (pointsHistory.length() == 0) item { EmptyCard("هنوز امتیازی برای محتوای شما ثبت نشده است.") }
            items(pointsHistory.takeObjects(), key = { it.optString("id") }) { point ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(point.optString("title"), color = Navy, style = MaterialTheme.typography.titleMedium)
                        Text("+${point.optInt("points")} امتیاز", color = Color(0xFF2E6B45))
                        point.optString("createdAt").takeIf { it.isNotBlank() }?.let { Text(it.replace("T", " ").take(16), color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Text("تاریخچه فعالیت", style = MaterialTheme.typography.titleLarge, color = Navy) }
            if (activities.length() == 0) item { EmptyCard("فعالیتی برای نمایش ثبت نشده است.") }
            items(activities.takeObjects(), key = { it.optString("id") }) { activity ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(activity.optString("title"), color = Navy, style = MaterialTheme.typography.titleMedium)
                        activity.optString("description").takeIf { it.isNotBlank() }?.let { Text(it) }
                        activity.optString("createdAt").takeIf { it.isNotBlank() }?.let { Text(it.replace("T", " ").take(16), color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(api: PortalApi, data: JSONObject, onChanged: () -> Unit) {
    val member = data.optJSONObject("member") ?: JSONObject()
    val clubs = member.optJSONArray("clubMemberships") ?: JSONArray()
    val availableClubs = data.optJSONArray("availableClubs") ?: JSONArray()
    val invoices = data.optJSONArray("membershipDuesInvoices") ?: JSONArray()
    val exitRequests = data.optJSONArray("exitRequests") ?: JSONArray()
    val titleApplications = member.optJSONArray("titleApplications") ?: JSONArray()
    val paymentGatewayReady = data.optBoolean("paymentGatewayReady")
    val activeClubIds = clubs.takeObjects()
        .mapNotNull { item -> item.optJSONObject("club")?.optString("id").orEmpty().ifBlank { item.optString("clubId") }.takeIf { it.isNotBlank() } }
        .toSet()
    var joiningClubId by remember { mutableStateOf<String?>(null) }
    var leavingClubId by remember { mutableStateOf<String?>(null) }
    var exitBusy by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var clubMessage by remember { mutableStateOf<String?>(null) }
    var clubError by remember { mutableStateOf<String?>(null) }
    var exitMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("پرونده عضویت", style = MaterialTheme.typography.headlineSmall, color = Navy) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileRow("نام و نام خانوادگی", member.optString("fullName"))
                        ProfileRow("نوع عضویت", member.optString("membershipType"))
                        ProfileRow("سطح مشارکت", member.optString("participationRank"))
                        ProfileRow("وضعیت", member.optString("status"))
                        ProfileRow("ایمیل", member.optString("email"))
                        ProfileRow("تلفن", member.optString("phone"))
                        ProfileRow("امتیاز", member.optInt("points", 0).toString())
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("اعتبارسنجی حرفه‌ای", color = Navy, style = MaterialTheme.typography.titleMedium)
                        Text("ثبت درخواست، بارگذاری مستندات و پیگیری تعرفه و نتیجه در فرایند رسمی پورتال انجام می‌شود.",
                            color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://portal.khorshid-mafakher.ir/workspace/member/professional-verification"))) }
                        }, modifier = Modifier.fillMaxWidth()) { Text("ورود به فرایند اعتبارسنجی") }
                    }
                }
            }
            item { Text("درخواست‌های عنوان حرفه‌ای", style = MaterialTheme.typography.titleLarge, color = Navy) }
            if (titleApplications.length() == 0) item { EmptyCard("درخواستی برای عنوان حرفه‌ای ثبت نشده است.") }
            items(titleApplications.takeObjects(5), key = { it.optString("id") }) { application ->
                val kind = if (application.optString("titleKind") == "ARTIST") "هنرمند" else "متخصص"
                val status = when (application.optString("status")) {
                    "SUBMITTED" -> "ثبت‌شده"
                    "NEEDS_CORRECTION" -> "نیازمند اصلاح"
                    "APPROVED" -> "تأییدشده"
                    "REJECTED" -> "ردشده"
                    else -> application.optString("status")
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("عنوان $kind", color = Navy, style = MaterialTheme.typography.titleMedium)
                        Text("وضعیت: $status")
                        application.optString("reviewReason").takeIf { it.isNotBlank() }?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Text("باشگاه‌های عضو", style = MaterialTheme.typography.titleLarge, color = Navy) }
            if (clubs.length() == 0) item { EmptyCard("هنوز عضو باشگاهی نیستید.") }
            else items(clubs.takeObjects(), key = { it.optJSONObject("club")?.optString("id").orEmpty() }) { membership ->
                val club = membership.optJSONObject("club") ?: membership
                val clubId = club.optString("id")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(club.optString("name").ifBlank { "باشگاه" }, color = Navy, style = MaterialTheme.typography.titleMedium)
                        club.optString("description").takeIf { it.isNotBlank() }?.let { Text(it, color = Color.Gray) }
                        OutlinedButton(enabled = clubId.isNotBlank() && leavingClubId == null, onClick = {
                            scope.launch {
                                leavingClubId = clubId
                                clubMessage = null
                                clubError = null
                                runCatching { api.cancelClubMembership(clubId) }
                                    .onSuccess { clubMessage = "عضویت شما در «${club.optString("name")}» لغو شد."; onChanged() }
                                    .onFailure { clubError = it.message ?: "لغو عضویت انجام نشد." }
                                leavingClubId = null
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            if (leavingClubId == clubId) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("لغو عضویت در این باشگاه")
                        }
                    }
                }
            }
            item {
                Text("باشگاه‌های فعال برای عضویت", style = MaterialTheme.typography.titleLarge, color = Navy)
                Text("می‌توانید هم‌زمان در چند باشگاه عضو باشید.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            if (availableClubs.length() == 0) item { EmptyCard("در حال حاضر باشگاه فعالی برای عضویت وجود ندارد.") }
            else items(availableClubs.takeObjects(), key = { it.optString("id") }) { club ->
                val clubId = club.optString("id")
                val isMember = clubId in activeClubIds
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(club.optString("name", "باشگاه"), color = Navy, style = MaterialTheme.typography.titleMedium)
                        club.optString("description").takeIf { it.isNotBlank() }?.let { Text(it, color = Color.Gray) }
                        Button(onClick = {
                            scope.launch {
                                joiningClubId = clubId
                                clubError = null
                                clubMessage = null
                                runCatching { api.requestClubMembership(clubId) }
                                    .onSuccess { clubMessage = "عضویت در «${club.optString("name")}» ثبت شد."; onChanged() }
                                    .onFailure { clubError = it.message ?: "ثبت عضویت ناموفق بود." }
                                joiningClubId = null
                            }
                        }, enabled = clubId.isNotBlank() && !isMember && joiningClubId == null, modifier = Modifier.fillMaxWidth()) {
                            when {
                                isMember -> Text("عضو هستید")
                                joiningClubId == clubId -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else -> Text("عضویت در باشگاه")
                            }
                        }
                    }
                }
            }
            item { Text("سوابق حق عضویت سالانه", style = MaterialTheme.typography.titleLarge, color = Navy) }
            if (invoices.length() == 0) item { EmptyCard("هنوز صورتحساب حق عضویت برای شما صادر نشده است.") }
            items(invoices.takeObjects(), key = { it.optString("id") }) { invoice ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        ProfileRow("شماره صورتحساب", invoice.optString("invoiceNumber"))
                        ProfileRow("مبلغ (تومان)", invoice.optString("amountToman"))
                        ProfileRow("وضعیت", invoice.optString("status"))
                        ProfileRow("اعتبار تا", invoice.optString("validUntil"))
                    }
                }
            }
            item { Text(if (paymentGatewayReady) "درگاه پرداخت فعال است." else "دریافت حق عضویت سالانه در حال حاضر از طریق درگاه آنلاین فعال نشده است.", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
            item { Text("درخواست خروج از عضویت", style = MaterialTheme.typography.titleLarge, color = Navy) }
            if (exitRequests.length() > 0) item { Text("آخرین درخواست: ${exitRequests.optJSONObject(0)?.optString("createdAt").orEmpty()}", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
            item {
                Text("ثبت این درخواست عضویت را فوراً غیرفعال نمی‌کند؛ درخواست برای بررسی به معاونت اعضا ارسال می‌شود.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showExitDialog = true }, enabled = !exitBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (exitBusy) "در حال ارسال..." else "درخواست خروج از عضویت")
                }
                exitMessage?.let { Text(it, color = Color(0xFF2E6B45)) }
                clubMessage?.let { Text(it, color = Color(0xFF2E6B45)) }
                clubError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (showExitDialog) AlertDialog(
            onDismissRequest = { if (!exitBusy) showExitDialog = false },
            title = { Text("درخواست خروج از عضویت") },
            text = { Text("این اقدام فقط درخواست بررسی خروج را ثبت می‌کند. دسترسی شما با ثبت این درخواست فوراً بسته نمی‌شود. درخواست به معاونت اعضا ارسال خواهد شد.") },
            confirmButton = {
                TextButton(enabled = !exitBusy, onClick = {
                    scope.launch {
                        exitBusy = true
                        exitMessage = null
                        runCatching { api.requestMembershipExit() }
                            .onSuccess { exitMessage = "درخواست خروج برای معاونت اعضا ارسال شد."; showExitDialog = false; onChanged() }
                            .onFailure { exitMessage = it.message ?: "ثبت درخواست خروج ناموفق بود." }
                        exitBusy = false
                    }
                }) { Text(if (exitBusy) "در حال ارسال..." else "ثبت درخواست") }
            },
            dismissButton = { TextButton(enabled = !exitBusy, onClick = { showExitDialog = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    if (value.isNotBlank()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.Gray)
            Spacer(Modifier.width(12.dp))
            Text(value, color = Navy)
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(16.dp), color = Color.Gray) }
}

@Composable
private fun NetworkEntryCard() {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth().clickable {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://network.khorshid-mafakher.ir"))) }
        },
        colors = CardDefaults.cardColors(containerColor = Navy)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("سامانه پیوند", color = Gold, style = MaterialTheme.typography.titleLarge)
                Text("ورود به شبکه ارتباطی اعضا، هنرمندان و متخصصان", color = Color.White)
            }
            Text("ورود  ↗", color = Gold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

internal fun mergeNotices(primary: JSONArray, secondary: JSONArray): JSONArray {
    val merged = LinkedHashMap<String, JSONObject>()
    for (source in listOf(primary, secondary)) {
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val key = item.optString("id").ifBlank {
                item.optString("title") + "|" + item.optString("createdAt") + "|" + item.optString("publishAt")
            }
            if (key.isNotBlank()) merged[key] = item
        }
    }
    return JSONArray(merged.values.toList())
}

private fun JSONArray.takeObjects(limit: Int = length()): List<JSONObject> =
    (0 until minOf(length(), limit)).mapNotNull { optJSONObject(it) }
