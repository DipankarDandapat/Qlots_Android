package com.qlots.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qlots.app.data.EntryRecord
import com.qlots.app.data.GoalItem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File

@Composable
fun QlotsApp(viewModel: QlotsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var showProfile by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf("asset") }
    var editingEntry by remember { mutableStateOf<EntryRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<EntryRecord?>(null) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var showDeleteData by remember { mutableStateOf(false) }
    var showSnapshot by remember { mutableStateOf(false) }
    var showSavePlan by remember { mutableStateOf(false) }
    var showGoalEditor by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<GoalItem?>(null) }
    var goalSuggestion by remember { mutableStateOf<Double?>(null) }
    var progressGoal by remember { mutableStateOf<GoalItem?>(null) }
    var outlookRequest by remember { mutableStateOf<OutlookRequest?>(null) }

    // If the phone was asleep over midnight, reload as soon as the app is back so "as of today" is today.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfDateChanged() }

    LaunchedEffect(state.message) {
        val message = state.message
        if (!message.isNullOrBlank()) {
            snackbarHost.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    if (state.booting) {
        QlotsLoadingScreen(serverReachable = state.serverReachable)
        return
    }
    if (state.token.isNullOrBlank()) {
        Box(Modifier.fillMaxSize()) {
            AuthScreen(busy = state.busy) { register, name, email, password ->
                viewModel.authenticate(register, name, email, password)
            }
            SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
        }
        return
    }

    val openNew: (String) -> Unit = { kind ->
        createKind = kind
        editingEntry = null
        showEditor = true
    }
    val openEdit: (EntryRecord) -> Unit = { entry ->
        editingEntry = entry
        createKind = entry.kind
        showEditor = true
    }

    Scaffold(
        containerColor = QlotsColors.Canvas,
        topBar = {
            TopHeader(
                displayName = state.user?.name.orEmpty(),
                busy = state.busy,
                onRefresh = viewModel::reload,
                onProfile = { showProfile = true },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                AppTab.entries.forEach { tab ->
                    val selected = state.selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(26.dp),
                            )
                        },
                        label = {
                            Text(
                                tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = QlotsColors.Green,
                            selectedTextColor = QlotsColors.Green,
                            indicatorColor = QlotsColors.Mint,
                            unselectedIconColor = QlotsColors.Muted,
                            unselectedTextColor = QlotsColors.Muted,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHost, modifier = Modifier.padding(bottom = 8.dp)) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (state.selectedTab) {
                AppTab.OVERVIEW -> OverviewScreen(
                    state = state,
                    onOpenWealth = { viewModel.selectTab(AppTab.WEALTH) },
                    onOpenCashflow = { viewModel.selectTab(AppTab.CASHFLOW) },
                    onOpenPlan = { viewModel.selectTab(AppTab.PLAN) },
                    onOpenOutlook = { years -> outlookRequest = OutlookRequest(years) },
                )
                AppTab.WEALTH -> WealthScreen(
                    entries = state.entries,
                    onAdd = openNew,
                    onEdit = openEdit,
                    onDelete = { pendingDelete = it },
                )
                AppTab.CASHFLOW -> CashflowScreen(
                    entries = state.entries,
                    monthly = state.dashboard?.monthly,
                    onAdd = openNew,
                    onEdit = openEdit,
                    onDelete = { pendingDelete = it },
                )
                AppTab.INSIGHTS -> InsightsScreen(
                    state = state,
                    onRecordSnapshot = { showSnapshot = true },
                    onDeleteSnapshot = viewModel::deleteSnapshot,
                    onSelectPlan = viewModel::selectPlan,
                    onSavePlan = { showSavePlan = true },
                    onDeletePlan = viewModel::deletePlan,
                    onAddGoal = { editingGoal = null; goalSuggestion = null; showGoalEditor = true },
                    onEditGoal = { editingGoal = it; goalSuggestion = null; showGoalEditor = true },
                    onProgress = { progressGoal = it },
                    onDeleteGoal = { viewModel.deleteGoal(it.id) },
                    onConsumeSection = viewModel::consumeSection,
                )
                AppTab.PLAN -> PlanScreen(
                    state = state,
                    actions = PlanActions(
                        onSaveAssumptions = viewModel::saveAssumptionsAndCalculate,
                        onScenario = viewModel::runScenario,
                        onAskHelper = viewModel::askHelper,
                        onCreatePdf = { viewModel.createPdf { bytes -> sharePdf(context, bytes) } },
                        onRunOutlook = { years, save, loan, equity -> viewModel.runOutlook(years, save, loan, equity, null, null) },
                        onViewCalculation = { outlookRequest = it },
                        onWhatIf = viewModel::runWhatIf,
                        onEmergency = viewModel::loadEmergency,
                        onCreateEmergencyGoal = { target ->
                            editingGoal = null
                            goalSuggestion = target
                            showGoalEditor = true
                            viewModel.selectTab(AppTab.INSIGHTS, InsightSection.GOALS.name)
                        },
                        onGoalCalc = viewModel::runGoalCalc,
                        onLoanPayoff = viewModel::runLoanPayoff,
                        onOpenWealth = { viewModel.selectTab(AppTab.WEALTH) },
                        onConsumeSection = viewModel::consumeSection,
                    ),
                )
            }
            if (state.busy) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    color = QlotsColors.Green,
                    trackColor = QlotsColors.Mint,
                )
            }
        }
    }

    if (showEditor) {
        EntryEditorDialog(
            initialKind = createKind,
            existing = editingEntry,
            busy = state.busy,
            onDismiss = { showEditor = false },
            onSave = { entry -> viewModel.saveEntry(entry, editingEntry?.id) { showEditor = false } },
        )
    }
    if (showSnapshot) {
        SnapshotDialog(
            busy = state.busy,
            currentAssets = state.dashboard?.totalAssets ?: 0.0,
            currentLiabilities = state.dashboard?.totalLiabilities ?: 0.0,
            onDismiss = { showSnapshot = false },
            onSave = { date, assets, liabilities, note -> viewModel.recordSnapshot(date, assets, liabilities, note) { showSnapshot = false } },
        )
    }
    if (showSavePlan) {
        SavePlanDialog(state.busy, { showSavePlan = false }) { label -> viewModel.savePlan(label); showSavePlan = false }
    }
    if (showGoalEditor) {
        GoalDialog(
            existing = editingGoal, busy = state.busy, suggestedTarget = goalSuggestion,
            onDismiss = { showGoalEditor = false },
            onSave = { g ->
                if (g.name.isBlank() || g.target <= 0.0) viewModel.notifyError("Add a goal name and a target amount.")
                else viewModel.saveGoal(g.id, g.name, g.kind, g.target, g.current, g.monthly, g.expectedReturn, g.targetDate, g.autoLink) { showGoalEditor = false }
            },
        )
    }
    progressGoal?.let { goal ->
        ProgressDialog(goal, state.busy, { progressGoal = null }) { amount -> viewModel.updateGoalProgress(goal.id, amount) { progressGoal = null } }
    }
    outlookRequest?.let { request ->
        OutlookDetailSheet(
            request = request,
            breakdown = state.breakdown,
            onScenario = { scenario -> viewModel.loadBreakdown(request.years, scenario, request.adjust) },
            onDismiss = { outlookRequest = null; viewModel.clearBreakdown() },
        )
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove this record?") },
            text = { Text("“${entry.name}” will be removed from your Qlots account.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; viewModel.deleteEntry(entry.id) }) {
                    Text("Remove", color = QlotsColors.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep it") } },
            containerColor = Color.White,
        )
    }
    if (showProfile) {
        ProfileDialog(
            user = state.user,
            busy = state.busy,
            onDismiss = { showProfile = false },
            onSave = { name, password -> viewModel.saveProfile(name, password) { showProfile = false } },
            onSignOut = { showProfile = false; viewModel.signOut() },
            onDeleteAccount = { showProfile = false; showDeleteAccount = true },
            onDeleteData = { showProfile = false; showDeleteData = true },
        )
    }
    if (showDeleteAccount) {
        AlertDialog(
            onDismissRequest = { showDeleteAccount = false },
            title = { Text("Delete your account and all data?") },
            text = { Text("This permanently deletes your Qlots account, financial records and saved snapshots. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteAccount = false; viewModel.deleteAccount {} }) {
                    Text("Delete everything", color = QlotsColors.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccount = false }) { Text("Cancel") } },
            containerColor = Color.White,
        )
    }
    if (showDeleteData) {
        AlertDialog(
            onDismissRequest = { showDeleteData = false },
            title = { Text("Delete all financial records?") },
            text = { Text("Your sign-in will remain active, but assets, liabilities, income, expenses and history will be removed.") },
            confirmButton = {
                TextButton(onClick = { showDeleteData = false; viewModel.deleteAllFinancialData {} }) {
                    Text("Delete records", color = QlotsColors.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteData = false }) { Text("Cancel") } },
            containerColor = Color.White,
        )
    }
}

@Composable
private fun TopHeader(displayName: String, busy: Boolean, onRefresh: () -> Unit, onProfile: () -> Unit) {
    Surface(color = QlotsColors.Canvas, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 19.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark(size = 39.dp)
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Qlots", color = QlotsColors.Deep, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                        Text(".", color = QlotsColors.Green, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("Your money, in one view", color = QlotsColors.Muted, fontSize = 10.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Text("↻", color = QlotsColors.Green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Surface(
                    onClick = onProfile,
                    shape = RoundedCornerShape(14.dp),
                    color = QlotsColors.Mint,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(displayName.firstOrNull()?.uppercase() ?: "Q", color = QlotsColors.Green, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QlotsLoadingScreen(serverReachable: Boolean) {
    Box(Modifier.fillMaxSize().background(QlotsColors.Canvas), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(size = 72.dp)
            Text("Qlots", modifier = Modifier.padding(top = 14.dp), color = QlotsColors.Deep, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (serverReachable) "Restoring your session…" else "Connecting to server…",
                modifier = Modifier.padding(top = 6.dp),
                color = QlotsColors.Muted,
                fontSize = 13.sp,
            )
            CircularProgressIndicator(modifier = Modifier.padding(top = 18.dp).size(27.dp), color = QlotsColors.Green, strokeWidth = 3.dp)
        }
    }
}

private fun sharePdf(context: Context, bytes: ByteArray) {
    val reportDirectory = File(context.cacheDir, "reports").apply { mkdirs() }
    val report = File(reportDirectory, "qlots-financial-report.pdf")
    report.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, "Qlots financial report")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share your Qlots report"))
}
