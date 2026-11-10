package com.qlots.app.ui

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.qlots.app.data.ApiException
import com.qlots.app.data.Assumptions
import com.qlots.app.data.EntryRecord
import com.qlots.app.data.FinancialDashboard
import com.qlots.app.data.BreakdownData
import com.qlots.app.data.EmergencyPlan
import com.qlots.app.data.GoalCalcResult
import com.qlots.app.data.GoalItem
import com.qlots.app.data.HistorySnapshot
import com.qlots.app.data.InsightsData
import com.qlots.app.data.LoanPayoffResult
import com.qlots.app.data.OutlookData
import com.qlots.app.data.PlanComparison
import com.qlots.app.data.PlanInfo
import com.qlots.app.data.WhatIfResult
import com.qlots.app.data.ProjectionResponse
import com.qlots.app.data.QlotsApi
import com.qlots.app.data.SecureTokenStore
import com.qlots.app.data.UserProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

enum class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OVERVIEW("Overview", Icons.Rounded.PieChart),
    WEALTH("Wealth", Icons.Rounded.AccountBalanceWallet),
    CASHFLOW("Cash flow", Icons.Rounded.BarChart),
    INSIGHTS("Insights", Icons.Rounded.Insights),
    PLAN("Plan", Icons.AutoMirrored.Rounded.TrendingUp),
}

data class OutlookAdjust(val extraSave: Double = 0.0, val extraLoan: Double = 0.0, val equity: Double? = null)

data class OutlookRequest(val years: Int, val adjust: OutlookAdjust = OutlookAdjust())

data class QlotsUiState(
    val booting: Boolean = true,
    val serverReachable: Boolean = false,
    val busy: Boolean = false,
    val token: String? = null,
    val user: UserProfile? = null,
    val selectedTab: AppTab = AppTab.OVERVIEW,
    val dashboard: FinancialDashboard? = null,
    val entries: List<EntryRecord> = emptyList(),
    val assumptions: Assumptions = Assumptions(),
    val scenario: String = "base",
    val forecast: ProjectionResponse? = null,
    val history: List<HistorySnapshot> = emptyList(),
    val helperAnswer: String = "",
    val insights: InsightsData? = null,
    val goals: List<GoalItem> = emptyList(),
    val plans: List<PlanInfo> = emptyList(),
    val comparison: PlanComparison? = null,
    val outlook: OutlookData? = null,
    val breakdown: BreakdownData? = null,
    val whatIf: WhatIfResult? = null,
    val payoff: LoanPayoffResult? = null,
    val emergency: EmergencyPlan? = null,
    val goalCalc: GoalCalcResult? = null,
    val lastRefreshed: Long = 0L,
    val loadedDate: String = "",
    val sectionRequest: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

class QlotsViewModel(application: Application) : AndroidViewModel(application) {
    private val api = QlotsApi(interceptor = ChuckerInterceptor.Builder(application).build())
    private val tokenStore = SecureTokenStore(application)
    private val mutableState = MutableStateFlow(QlotsUiState())
    val state: StateFlow<QlotsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            awaitServer()
            restoreSession()
        }
        // Net worth is re-valued every night at 12:00 AM on the server; the app reloads at the same moment
        // so "as of today" never shows yesterday's figure.
        viewModelScope.launch {
            while (true) {
                delay(millisUntilNextMidnight() + 4_000)
                silentReload()
            }
        }
    }

    /** Reload quietly (no spinner or toast) when the calendar day changed while the app was in the background. */
    fun refreshIfDateChanged() {
        val current = mutableState.value
        if (current.token != null && current.loadedDate.isNotEmpty() && current.loadedDate != todayIso()) silentReload()
    }

    private fun silentReload() {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            try { loadAccount(token) } catch (error: Exception) { if ((error as? ApiException)?.statusCode == 401) handleError(error) }
        }
    }

    fun consumeSection() {
        mutableState.value = mutableState.value.copy(sectionRequest = null)
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    fun selectTab(tab: AppTab, section: String? = null) {
        mutableState.value = mutableState.value.copy(selectedTab = tab, sectionRequest = section)
        if (tab == AppTab.PLAN && mutableState.value.forecast == null && mutableState.value.token != null) {
            runScenario("base")
        }
    }

    private suspend fun awaitServer() {
        while (!api.healthCheck()) {
            kotlinx.coroutines.delay(3_000)
        }
        mutableState.value = mutableState.value.copy(serverReachable = true)
    }

    private suspend fun restoreSession() {
        val saved = tokenStore.read()
        if (saved.isNullOrBlank()) {
            mutableState.value = mutableState.value.copy(booting = false)
            return
        }
        try {
            val refreshed = api.post("/api/auth/refresh", saved)
            val newToken = refreshed.optString("access_token", saved)
            tokenStore.write(newToken)
            mutableState.value = mutableState.value.copy(token = newToken)
            loadAccount(newToken)
        } catch (error: Exception) {
            if ((error as? ApiException)?.statusCode == 401) {
                tokenStore.clear()
                mutableState.value = mutableState.value.copy(token = null, user = null)
            } else {
                try {
                    mutableState.value = mutableState.value.copy(token = saved)
                    loadAccount(saved)
                } catch (fallback: Exception) {
                    handleError(fallback)
                }
            }
        } finally {
            mutableState.value = mutableState.value.copy(booting = false)
        }
    }

    private suspend fun loadAccount(token: String) {
        coroutineScope {
            val meRequest = async { api.get("/api/me", token) }
            val dashboardRequest = async { api.get("/api/dashboard", token) }
            val entriesRequest = async { api.entries(token) }
            val assumptionsRequest = async { api.get("/api/assumptions", token) }
            val historyRequest = async { api.history(token) }
            val insightsRequest = async { optional { InsightsData.fromJson(api.get("/api/insights", token)) } }
            val goalsRequest = async { optional { api.get("/api/goals", token).let { j -> j.optJSONArray("goals")?.let { a -> (0 until a.length()).map { GoalItem.fromJson(a.getJSONObject(it)) } } ?: emptyList() } } }
            val plansRequest = async { optional { api.get("/api/projection/plans", token).let { j -> j.optJSONArray("plans")?.let { a -> (0 until a.length()).map { PlanInfo.fromJson(a.getJSONObject(it)) } } ?: emptyList() } } }
            val me = UserProfile.fromJson(meRequest.await())
            val dashboard = FinancialDashboard.fromJson(dashboardRequest.await())
            val entries = entriesRequest.await()
            val assumptions = Assumptions.fromJson(assumptionsRequest.await())
            val history = historyRequest.await()
            val plans = plansRequest.await()
            val previous = mutableState.value
            mutableState.value = previous.copy(
                token = token,
                user = me,
                dashboard = dashboard,
                entries = entries,
                assumptions = assumptions,
                history = history,
                insights = insightsRequest.await() ?: previous.insights,
                goals = goalsRequest.await() ?: previous.goals,
                plans = plans ?: previous.plans,
                lastRefreshed = System.currentTimeMillis(),
                loadedDate = todayIso(),
            )
            val wanted = previous.comparison?.plan?.id?.takeIf { id -> plans?.any { it.id == id } == true } ?: plans?.firstOrNull()?.id
            if (wanted != null) optional { loadComparison(token, wanted) }
        }
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (error: ApiException) {
        if (error.statusCode == 401) throw error else null
    } catch (_: Exception) {
        null
    }

    private suspend fun loadComparison(token: String, planId: Int) {
        val comparison = PlanComparison.fromJson(api.get("/api/projection/plans/$planId/vs-actual", token))
        mutableState.value = mutableState.value.copy(comparison = comparison)
    }

    private fun launchApi(block: suspend (String) -> Unit) {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            setBusy(true)
            try { block(token) } catch (error: Exception) { handleError(error) } finally { setBusy(false) }
        }
    }

    fun authenticate(register: Boolean, name: String, email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.isEmpty()) {
            notify("Enter your email and password.", true)
            return
        }
        if (register && name.isBlank()) {
            notify("Add your name to create an account.", true)
            return
        }
        if (register && password.length < 10) {
            notify("Password must be at least 10 characters.", true)
            return
        }
        viewModelScope.launch {
            setBusy(true)
            try {
                val body = JSONObject()
                    .put("email", cleanEmail)
                    .put("password", password)
                if (register) body.put("name", name.trim())
                val result = api.publicPost(if (register) "/api/auth/register" else "/api/auth/login", body)
                val token = result.optString("access_token")
                if (token.isBlank()) throw IOException("The server did not return a sign-in token.")
                tokenStore.write(token)
                val userJson = result.optJSONObject("user") ?: JSONObject()
                mutableState.value = QlotsUiState(
                    booting = false,
                    busy = true,
                    token = token,
                    user = UserProfile.fromJson(userJson),
                    selectedTab = AppTab.OVERVIEW,
                )
                loadAccount(token)
                notify(if (register) "Welcome to Qlots." else "Signed in.", false)
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun reload() {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            setBusy(true)
            try {
                loadAccount(token)
                notify("Your information is up to date.", false)
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun saveEntry(entry: EntryRecord, entryId: Int? = null, onSaved: () -> Unit) {
        val token = mutableState.value.token ?: return
        if (entry.name.isBlank()) {
            notify("Add a name to continue.", true)
            return
        }
        if (!entry.isDeposit && entry.amount <= 0.0) {
            notify("Enter an amount greater than zero.", true)
            return
        }
        if (entry.isDeposit && (entry.principal <= 0.0 || entry.annualRate <= 0.0 || entry.startDate.isNullOrBlank())) {
            notify("Enter principal, interest rate and start date.", true)
            return
        }
        viewModelScope.launch {
            setBusy(true)
            try {
                val path = if (entryId == null) "/api/entries" else "/api/entries/$entryId"
                if (entryId == null) api.post(path, token, entry.toRequestJson())
                else api.put(path, token, entry.toRequestJson())
                loadAccount(token)
                notify("Record saved.", false)
                onSaved()
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun deleteEntry(entryId: Int) {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            setBusy(true)
            try {
                api.delete("/api/entries/$entryId", token)
                loadAccount(token)
                notify("Record deleted.", false)
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun saveAssumptionsAndCalculate(assumptions: Assumptions) {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            setBusy(true)
            try {
                val saved = api.put("/api/assumptions", token, assumptions.toJson())
                val parsed = Assumptions.fromJson(saved)
                val scenario = mutableState.value.scenario
                val result = api.post("/api/projection/calculate?scenario=$scenario", token)
                val forecast = ProjectionResponse.fromJson(result)
                loadAccount(token)
                mutableState.value = mutableState.value.copy(assumptions = parsed, forecast = forecast)
                notify("Projection updated.", false)
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun runScenario(scenario: String) {
        val token = mutableState.value.token ?: return
        if (scenario !in setOf("conservative", "base", "optimistic")) return
        viewModelScope.launch {
            setBusy(true)
            try {
                val result = api.post("/api/projection/calculate?scenario=$scenario", token)
                mutableState.value = mutableState.value.copy(
                    scenario = scenario,
                    forecast = ProjectionResponse.fromJson(result),
                )
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun askHelper(question: String) {
        val token = mutableState.value.token ?: return
        if (question.isBlank()) {
            notify("Enter a question about your numbers.", true)
            return
        }
        viewModelScope.launch {
            setBusy(true)
            mutableState.value = mutableState.value.copy(helperAnswer = "")
            try {
                val response = api.post("/api/ai/chat", token, JSONObject().put("question", question.trim()))
                val answer = response.optString("answer")
                val disclaimer = response.optString("disclaimer")
                mutableState.value = mutableState.value.copy(
                    helperAnswer = if (disclaimer.isBlank()) answer else "$answer\n\n$disclaimer",
                )
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    // ------------------------------------------------------------------ snapshots
    fun recordSnapshot(date: String?, assets: Double?, liabilities: Double?, note: String, onDone: () -> Unit) = launchApi { token ->
        val body = JSONObject().put("note", note.trim())
        if (!date.isNullOrBlank()) body.put("snapshot_date", date)
        if (assets != null) body.put("assets", assets)
        if (liabilities != null) body.put("liabilities", liabilities)
        api.post("/api/snapshots", token, body)
        loadAccount(token)
        notify("Snapshot recorded.", false)
        onDone()
    }

    fun deleteSnapshot(id: Int) = launchApi { token ->
        api.delete("/api/snapshots/$id", token)
        loadAccount(token)
        notify("Snapshot removed.", false)
    }

    // ------------------------------------------------------------------ projections
    fun selectPlan(id: Int) = launchApi { token -> loadComparison(token, id) }

    fun savePlan(label: String) = launchApi { token ->
        val created = api.post("/api/projection/plans", token, JSONObject().put("label", label.trim()))
        loadAccount(token)
        loadComparison(token, created.optInt("id"))
        notify("Projection saved. New snapshots will be compared with it.", false)
    }

    fun deletePlan(id: Int) = launchApi { token ->
        api.delete("/api/projection/plans/$id", token)
        mutableState.value = mutableState.value.copy(comparison = null)
        loadAccount(token)
        notify("Saved projection removed.", false)
    }

    private fun adjustBody(years: Int, extraSave: Double, extraLoan: Double, equity: Double?, salary: Double?, inflation: Double?): JSONObject {
        val body = JSONObject().put("years", years).put("extra_monthly_saving", extraSave).put("extra_loan_payment", extraLoan)
        if (equity != null) body.put("equity_return", equity)
        if (salary != null) body.put("salary_growth", salary)
        if (inflation != null) body.put("inflation", inflation)
        return body
    }

    fun runOutlook(years: Int, extraSave: Double, extraLoan: Double, equity: Double?, salary: Double?, inflation: Double?) = launchApi { token ->
        val result = api.post("/api/planning/outlook", token, adjustBody(years, extraSave, extraLoan, equity, salary, inflation))
        mutableState.value = mutableState.value.copy(outlook = OutlookData.fromJson(result))
    }

    fun loadBreakdown(years: Int, scenario: String, adjust: OutlookAdjust = OutlookAdjust()) {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            try {
                val body = adjustBody(years, adjust.extraSave, adjust.extraLoan, adjust.equity, null, null)
                val result = api.post("/api/planning/breakdown?scenario=$scenario", token, body)
                mutableState.value = mutableState.value.copy(breakdown = BreakdownData.fromJson(result))
            } catch (error: Exception) {
                handleError(error)
            }
        }
    }

    fun clearBreakdown() {
        mutableState.value = mutableState.value.copy(breakdown = null)
    }

    // ------------------------------------------------------------------ planners
    fun runWhatIf(years: Int, extraSave: Double, extraLoan: Double, purchase: Double, purchaseIn: Int, delay: Int) = launchApi { token ->
        val body = JSONObject().put("years", years).put("scenario", mutableState.value.scenario).put("extra_monthly_saving", extraSave)
            .put("extra_loan_payment", extraLoan).put("purchase_amount", purchase).put("purchase_in_months", purchaseIn).put("delay_months", delay)
        mutableState.value = mutableState.value.copy(whatIf = WhatIfResult.fromJson(api.post("/api/planning/what-if", token, body)))
    }

    fun runLoanPayoff(extra: Double, targetLoanId: Int?) = launchApi { token ->
        val body = JSONObject().put("extra_monthly", extra)
        if (targetLoanId != null) body.put("target_loan_id", targetLoanId)
        mutableState.value = mutableState.value.copy(payoff = LoanPayoffResult.fromJson(api.post("/api/planning/loan-payoff", token, body)))
    }

    fun loadEmergency(months: Int, includeEmi: Boolean, buildIn: Int) {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            try {
                val result = api.get("/api/planning/emergency-fund?months=$months&include_emi=$includeEmi&build_in_months=$buildIn", token)
                mutableState.value = mutableState.value.copy(emergency = EmergencyPlan.fromJson(result))
            } catch (error: Exception) {
                handleError(error)
            }
        }
    }

    fun runGoalCalc(target: Double, current: Double, annualReturn: Double, months: Int) = launchApi { token ->
        val body = JSONObject().put("target_amount", target).put("current_amount", current).put("annual_return", annualReturn).put("months", months)
        mutableState.value = mutableState.value.copy(goalCalc = GoalCalcResult.fromJson(api.post("/api/planning/goal-contribution", token, body)))
    }

    // ------------------------------------------------------------------ goals
    fun saveGoal(
        id: Int?, name: String, kind: String, target: Double, current: Double, monthly: Double, expectedReturn: Double,
        targetDate: String?, autoLink: Boolean, onDone: () -> Unit,
    ) = launchApi { token ->
        val body = JSONObject().put("name", name.trim()).put("kind", kind).put("target_amount", target).put("current_amount", current)
            .put("monthly_contribution", monthly).put("expected_return", expectedReturn).put("auto_link_cash", autoLink)
            .put("target_date", targetDate?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        if (id == null) api.post("/api/goals", token, body) else api.put("/api/goals/$id", token, body)
        loadAccount(token)
        notify("Goal saved.", false)
        onDone()
    }

    fun updateGoalProgress(id: Int, amount: Double, onDone: () -> Unit) = launchApi { token ->
        api.post("/api/goals/$id/progress", token, JSONObject().put("current_amount", amount))
        loadAccount(token)
        notify("Progress updated.", false)
        onDone()
    }

    fun deleteGoal(id: Int) = launchApi { token ->
        api.delete("/api/goals/$id", token)
        loadAccount(token)
        notify("Goal removed.", false)
    }

    fun createPdf(onReady: (ByteArray) -> Unit) {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            setBusy(true)
            try {
                val bytes = api.pdfReport(token)
                onReady(bytes)
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun saveProfile(name: String, password: String, onSaved: () -> Unit) {
        val token = mutableState.value.token ?: return
        if (name.isBlank()) {
            notify("Enter your name.", true)
            return
        }
        if (password.isNotEmpty() && password.length < 10) {
            notify("A new password must contain at least 10 characters.", true)
            return
        }
        viewModelScope.launch {
            setBusy(true)
            try {
                val body = JSONObject().put("name", name.trim())
                if (password.isNotEmpty()) body.put("password", password)
                val profile = UserProfile.fromJson(api.put("/api/me", token, body))
                mutableState.value = mutableState.value.copy(user = profile)
                notify("Profile updated.", false)
                onSaved()
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            setBusy(true)
            try {
                api.delete("/api/account", token)
                tokenStore.clear()
                mutableState.value = QlotsUiState(booting = false)
                notify("Your account was deleted.", false)
                onDeleted()
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun deleteAllFinancialData(onDeleted: () -> Unit) {
        val token = mutableState.value.token ?: return
        viewModelScope.launch {
            setBusy(true)
            try {
                api.delete("/api/account/data", token)
                loadAccount(token)
                notify("Your financial records were deleted.", false)
                onDeleted()
            } catch (error: Exception) {
                handleError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    fun signOut() {
        tokenStore.clear()
        mutableState.value = QlotsUiState(booting = false)
        notify("You have signed out.", false)
    }

    private fun handleError(error: Exception) {
        if ((error as? ApiException)?.statusCode == 401) {
            tokenStore.clear()
            mutableState.value = QlotsUiState(booting = false)
            notify("Your session expired. Please sign in again.", true)
        } else {
            notify(error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again.", true)
        }
    }

    private fun setBusy(busy: Boolean) {
        mutableState.value = mutableState.value.copy(busy = busy)
    }

    fun notifyError(message: String) = notify(message, true)

    private fun notify(message: String, isError: Boolean) {
        mutableState.value = mutableState.value.copy(message = message, messageIsError = isError)
    }
}
