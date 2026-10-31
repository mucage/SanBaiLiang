package com.mucage.rmb

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mucage.rmb.auto.AutoPrefs
import com.mucage.rmb.auto.DailyLimit
import com.mucage.rmb.auto.MonthlyLimit
import com.mucage.rmb.auto.NotificationHelper
import com.mucage.rmb.data.RecordEntity
import com.mucage.rmb.data.Repository
import com.mucage.rmb.ui.add.AddRecordDialog
import com.mucage.rmb.ui.assets.AssetsScreen
import com.mucage.rmb.ui.assets.BalanceCheck
import com.mucage.rmb.ui.assets.BalanceCheckDialog
import com.mucage.rmb.ui.assets.InitialBalanceScreen
import com.mucage.rmb.ui.records.RecordsScreen
import com.mucage.rmb.ui.settings.SettingsScreen
import com.mucage.rmb.ui.stats.StatsScreen
import com.mucage.rmb.ui.theme.AppBackground
import com.mucage.rmb.ui.theme.BackgroundState
import com.mucage.rmb.ui.theme.JianZhangTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Repository.init(this)
        NotificationHelper.ensureChannel(this)
        BackgroundState.load(this)
        setContent {
            JianZhangTheme {
                AppBackground {
                    AppRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // vivo 等 ROM 在应用更新后会解绑通知监听服务且不自动重绑，每次启动确保重新绑定
        com.mucage.rmb.auto.AutoBookkeepingService.ensureBound(this)
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    // 首次启动：未完成初始余额输入前阻断使用
    val balance by Repository.balanceDao.observe().collectAsState(initial = null)
    val b = balance

    when {
        b == null -> Box(Modifier.fillMaxSize()) // 数据库加载中，短暂留白
        !b.initialized -> InitialBalanceScreen(
            onDone = {
                // 初始输入即本月余额确认，完成后再到下月才提示核对
                BalanceCheck.markShown(context)
            }
        )
        else -> MainApp()
    }
}

@Composable
private fun MainApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? ComponentActivity
    val initialTab = remember {
        activity?.intent?.getIntExtra(NotificationHelper.EXTRA_OPEN_TAB, -1) ?: -1
    }
    val tabs = listOf(
        TabItem("明细", Icons.Outlined.ReceiptLong),
        TabItem("统计", Icons.Outlined.PieChart),
        TabItem("资产", Icons.Outlined.AccountBalanceWallet),
        TabItem("设置", Icons.Outlined.Settings),
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(if (initialTab in 0..3) initialTab else 0) }
    var editingRecord by remember { mutableStateOf<RecordEntity?>(null) }
    var addVisible by remember { mutableStateOf(false) }

    // 有新的待确认记账时，进入应用即关闭通知提醒；并检查月度/每日支出限额
    LifecycleResumeEffect(Unit) {
        NotificationHelper.cancel(context)
        scope.launch {
            MonthlyLimit.check(context)
            DailyLimit.check(context)
        }
        onPauseOrDispose { }
    }

    // Android 13+ 首次启动申请通知权限（用于待确认记账提醒）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !AutoPrefs.wasNotificationRequested(context)
        ) {
            AutoPrefs.markNotificationRequested(context)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 每月 1 日（月份变化后首次打开）核对当前总余额
    var showBalanceCheck by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showBalanceCheck = BalanceCheck.shouldShow(context)
        if (showBalanceCheck) BalanceCheck.markShown(context)
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingRecord = null
                addVisible = true
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "记一笔")
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> RecordsScreen(
                contentPadding = padding,
                onAdd = {
                    editingRecord = null
                    addVisible = true
                },
                onEdit = { record ->
                    editingRecord = record
                    addVisible = true
                }
            )
            1 -> StatsScreen(contentPadding = padding)
            2 -> AssetsScreen(contentPadding = padding)
            3 -> SettingsScreen(contentPadding = padding)
        }
    }

    if (addVisible) {
        AddRecordDialog(
            editing = editingRecord,
            onDismiss = { addVisible = false },
            onSaved = { addVisible = false }
        )
    }

    if (showBalanceCheck) {
        BalanceCheckDialog(onDismiss = { showBalanceCheck = false })
    }
}
