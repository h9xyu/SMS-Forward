package com.example.smstopc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.smstopc.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmsToEmailTheme {
                SmsForwardApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmsForwardApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ntfyTopic by remember { mutableStateOf(AppPreferences.ntfyTopic) }
    var isEnabled by remember { mutableStateOf(AppPreferences.isEnabled) }

    var history by remember { mutableStateOf<List<ForwardRecord>>(emptyList()) }
    var forwardCount by remember { mutableIntStateOf(AppPreferences.forwardCount) }
    var lastForwardTime by remember { mutableLongStateOf(AppPreferences.lastForwardTime) }

    var showSaved by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun checkPermissions(): Triple<Boolean, Boolean, Boolean> {
        val sms = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val notif = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val battery = pm.isIgnoringBatteryOptimizations(context.packageName)
        return Triple(sms, notif, !battery)
    }

    var hasSmsPermission by remember { mutableStateOf(false) }
    var hasNotificationAccess by remember { mutableStateOf(false) }
    var isBatteryOptimized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (sms, notif, batt) = checkPermissions()
        hasSmsPermission = sms
        hasNotificationAccess = notif
        isBatteryOptimized = batt
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun startService() {
        val intent = Intent(context, ForwardService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopService() {
        context.stopService(Intent(context, ForwardService::class.java))
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        ForwardDatabase.getInstance().forwardDao().getRecentFlow().collectLatest { records ->
            history = records
            forwardCount = AppPreferences.forwardCount
            lastForwardTime = AppPreferences.lastForwardTime
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = AppPreferences.isEnabled
                forwardCount = AppPreferences.forwardCount
                lastForwardTime = AppPreferences.lastForwardTime
                val (sms, notif, batt) = checkPermissions()
                hasSmsPermission = sms
                hasNotificationAccess = notif
                isBatteryOptimized = batt
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("短信验证码转发", fontSize = 18.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Blue500,
                    titleContentColor = Color.White
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Instructions ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Gray100)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = Blue500)
                            Spacer(Modifier.width(8.dp))
                            Text("使用说明", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "1. 手机和 PC 已默认配置相同 ntfy.sh 主题，开箱即用\n" +
                                    "2. 如需私有主题，可在下方修改，PC 端右键托盘同步修改\n\n" +
                                    "实现熄屏转发（按品牌操作）:\n" +
                                    "华为/荣耀: 设置搜「应用启动管理」→ 搜「短信转发」→ 关闭自动管理 → 勾选「允许自启动」「允许后台活动」\n" +
                                    "小米/红米: 设置 → 应用设置 → 应用管理 → 搜「短信转发」→ 开启自启动；省电策略 → 无限制\n" +
                                    "OPPO/一加: 设置 → 应用 → 应用管理 → 搜「短信转发」→ 耗电保护 → 不限制\n" +
                                    "Vivo/iQOO: 设置搜「自启动」→ 搜「短信转发」→ 开启；i管家 → 后台活动 → 允许\n" +
                                    "三星: 设置 → 电池 → 后台使用限制 → 搜「短信转发」→ 不限制\n\n" +
                                    "提示:\n" +
                                    "- 验证码弹窗时自动复制到剪贴板，直接 Ctrl+V 粘贴\n" +
                                    "- 关闭电池优化是锦上添花，自启动才是熄屏转发的关键",
                            fontSize = 12.sp,
                            color = Gray600,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // --- SMS Permission Card ---
            if (!hasSmsPermission) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("需要短信权限", fontWeight = FontWeight.Bold)
                                Text("请授权短信读取权限，否则无法监听验证码", fontSize = 13.sp, color = Gray600)
                            }
                            Button(onClick = {
                                smsPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                                )
                            }) {
                                Text("授权")
                            }
                        }
                    }
                }
            }

            // --- Notification Access Card ---
            if (!hasNotificationAccess) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("需要通知读取权限", fontWeight = FontWeight.Bold)
                                Text("部分验证码被系统保护，无法通过短信权限读取。开启通知读取后可从通知栏抓取。", fontSize = 13.sp, color = Gray600)
                            }
                            Button(onClick = {
                                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                            }) {
                                Text("开启")
                            }
                        }
                    }
                }
            }

            // --- Battery Optimization Card ---
            if (isBatteryOptimized) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("建议关闭电池优化", fontWeight = FontWeight.Bold)
                                Text("非必要操作，但关闭后转发响应更快。熄屏转发的关键是上方的「自启动」设置。", fontSize = 13.sp, color = Gray600)
                            }
                            Button(onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }) {
                                Text("去关闭")
                            }
                        }
                    }
                }
            }

            // --- Enable Switch Card ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("启用转发", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                if (isEnabled) "服务运行中 · 正在监听短信" else "已暂停",
                                fontSize = 13.sp,
                                color = if (isEnabled) Green500 else Gray600
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                isEnabled = checked
                                AppPreferences.isEnabled = checked
                                if (checked) startService() else stopService()
                            }
                        )
                    }
                }
            }

            // --- ntfy.sh Config Card ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Cloud, contentDescription = null, tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(8.dp))
                            Text("ntfy.sh 云端转发（跨网络，免费免注册）", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("手机和 PC 已默认配置相同主题，开箱即用。如需私有主题可自行修改。", fontSize = 12.sp, color = Green500)
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = ntfyTopic,
                            onValueChange = {
                                ntfyTopic = it
                                AppPreferences.ntfyTopic = it
                            },
                            label = { Text("ntfy.sh 主题") },
                            placeholder = { Text("sms-forward-app") },
                            leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "自定义主题需同步更改 pc 端主题 →",
                                fontSize = 11.sp,
                                color = Gray600,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                ntfyTopic = AppPreferences.generateNtfyTopic()
                                AppPreferences.ntfyTopic = ntfyTopic
                            }) {
                                Text("重新生成", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // --- Save & Test Buttons ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            AppPreferences.ntfyTopic = ntfyTopic
                            showSaved = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue500)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("保存配置")
                    }

                    OutlinedButton(
                        onClick = {
                            if (!isEnabled) {
                                testResult = "请先启用转发"
                                return@OutlinedButton
                            }
                            if (ntfyTopic.isBlank()) {
                                testResult = "请先配置 ntfy.sh 主题"
                                return@OutlinedButton
                            }
                            testing = true
                            testResult = null
                            scope.launch {
                                val result = EmailSender.send(
                                    ntfyTopic = ntfyTopic,
                                    smsSender = "测试号码",
                                    smsContent = "这是一条测试短信",
                                    verificationCode = "123456"
                                )
                                testResult = if (result.isSuccess) {
                                    "测试发送成功！请检查 PC 弹窗"
                                } else {
                                    "发送失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"
                                }
                                testing = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !testing
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (testing) "发送中…" else "测试发送")
                    }
                }

                if (showSaved) {
                    Spacer(Modifier.height(4.dp))
                    Text("配置已保存", color = Green500, fontSize = 13.sp)
                }

                if (testResult != null) {
                    Spacer(Modifier.height(4.dp))
                    val isSuccess = testResult!!.startsWith("测试发送成功")
                    Text(
                        testResult!!,
                        color = if (isSuccess) Green500 else Red500,
                        fontSize = 13.sp
                    )
                }
            }

            // --- Status Card ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Green500)
                            Spacer(Modifier.width(8.dp))
                            Text("转发统计", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("累计转发: $forwardCount 条", fontSize = 14.sp)
                        if (lastForwardTime > 0) {
                            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            Text(
                                "最近转发: ${fmt.format(Date(lastForwardTime))}",
                                fontSize = 13.sp,
                                color = Gray600
                            )
                        } else {
                            Text("暂无转发记录", fontSize = 13.sp, color = Gray600)
                        }
                    }
                }
            }

            // --- History ---
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = Blue500)
                    Spacer(Modifier.width(8.dp))
                    Text("转发历史", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            if (history.isEmpty()) {
                item {
                    Text("暂无转发记录", fontSize = 13.sp, color = Gray600)
                }
            } else {
                items(history) { record ->
                    val bgColor = when {
                        record.pending -> Color(0xFFFFF8E1)
                        record.success -> Color.White
                        else -> Color(0xFFFFF3E0)
                    }
                    val icon = when {
                        record.pending -> Icons.Filled.Schedule
                        record.success -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.Error
                    }
                    val iconTint = when {
                        record.pending -> Color(0xFFFF9800)
                        record.success -> Green500
                        else -> Red500
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = bgColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                icon, contentDescription = null,
                                tint = iconTint, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "验证码: ${record.code}  ←  ${record.sender}",
                                    fontWeight = FontWeight.Medium, fontSize = 14.sp
                                )
                                Text(
                                    record.content,
                                    fontSize = 12.sp, color = Gray600, maxLines = 2
                                )
                                val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(fmt.format(Date(record.timestamp)), fontSize = 11.sp, color = Gray600)
                                    if (record.pending) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("等待重试", fontSize = 11.sp, color = Color(0xFFFF9800))
                                    }
                                }
                                if (record.errorMsg.isNotEmpty()) {
                                    Text(record.errorMsg, fontSize = 11.sp, color = Red500)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
