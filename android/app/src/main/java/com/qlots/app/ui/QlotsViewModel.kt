package com.qlots.app.ui

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.qlots.app.data.ApiException
import com.qlots.app.data.Assumptions
import com.qlots.app.data.EntryRecord
import com.qlots.app.data.FinancialDashboard
import com.qlots.app.data.HistorySnapshot
import com.qlots.app.data.ProjectionResponse
import com.qlots.app.data.QlotsApi
import com.qlots.app.data.SecureTokenStore
import com.qlots.app.data.UserProfile
import kotlinx.coroutines.async
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
    HISTORY("History", Icons.Rounded.History),
    PLAN("Plan", Icons.AutoMirrored.Rounded.TrendingUp),
}

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
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    fun selectTab(tab: AppTab) {
        mutableState.value = mutableState.value.copy(selectedTab = tab)
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
            val me = UserProfile.fromJson(meRequest.await())
            val dashboard = FinancialDashboard.fromJson(dashboardRequest.await())
            val entries = entriesRequest.await()
            val assumptions = Assumptions.fromJson(assumptionsRequest.await())
            val history = historyRequest.await()
            mutableState.value = mutableState.value.copy(
                token = token,
                user = me,
                dashboard = dashboard,
                entries = entries,
                assumptions = assumptions,
                history = history,
            )
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

    private fun notify(message: String, isError: Boolean) {
        mutableState.value = mutableState.value.copy(message = message, messageIsError = isError)
    }
}
