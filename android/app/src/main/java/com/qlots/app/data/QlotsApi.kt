package com.qlots.app.data

import com.qlots.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class ApiException(val statusCode: Int, override val message: String) : IOException(message)

class QlotsApi(baseUrl: String = BuildConfig.API_BASE_URL, interceptor: Interceptor? = null) {
    private val base = baseUrl.trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .apply { if (interceptor != null) addInterceptor(interceptor) }
        .build()

    suspend fun get(path: String, token: String): JSONObject = jsonCall("GET", path, token, null)
    suspend fun post(path: String, token: String, body: JSONObject = JSONObject()): JSONObject = jsonCall("POST", path, token, body)
    suspend fun put(path: String, token: String, body: JSONObject): JSONObject = jsonCall("PUT", path, token, body)
    suspend fun delete(path: String, token: String) { jsonCall("DELETE", path, token, null) }

    suspend fun publicPost(path: String, body: JSONObject): JSONObject = jsonCall("POST", path, null, body)
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try { execute("GET", "/health", null, null, false); true } catch (_: Exception) { false }
    }

    suspend fun entries(token: String): List<EntryRecord> = withContext(Dispatchers.IO) {
        val response = execute("GET", "/api/entries", token, null, false)
        JSONArray(String(response, Charsets.UTF_8)).toEntryList()
    }

    suspend fun history(token: String): List<HistorySnapshot> = withContext(Dispatchers.IO) {
        val response = execute("GET", "/api/history", token, null, false)
        val json = JSONArray(String(response, Charsets.UTF_8))
        (0 until json.length()).map { index -> HistorySnapshot.fromJson(json.getJSONObject(index)) }
    }

    suspend fun pdfReport(token: String): ByteArray = withContext(Dispatchers.IO) {
        execute("POST", "/api/reports/pdf", token, JSONObject(), true)
    }

    private suspend fun jsonCall(method: String, path: String, token: String?, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val bytes = execute(method, path, token, body, false)
        if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun execute(method: String, path: String, token: String?, body: JSONObject?, pdf: Boolean): ByteArray {
        val builder = Request.Builder()
            .url("$base$path")
            .header("Accept", if (pdf) "application/pdf" else "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        val request = builder.method(method, requestBody).build()
        client.newCall(request).execute().use { response ->
            val responseBytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                val detail = try {
                    JSONObject(String(responseBytes, Charsets.UTF_8)).optString("detail").takeIf { it.isNotBlank() }
                } catch (_: Exception) { null }
                throw ApiException(response.code, detail ?: "Request failed (${response.code})")
            }
            if (pdf && !String(responseBytes.take(5).toByteArray(), Charsets.US_ASCII).startsWith("%PDF-")) {
                throw IOException("The server response was not a valid PDF report.")
            }
            return responseBytes
        }
    }
}

internal fun JSONArray.toEntryList(): List<EntryRecord> =
    (0 until length()).map { EntryRecord.fromJson(getJSONObject(it)) }

internal fun JSONObject.stringValue(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)

internal fun JSONObject.nullableStringValue(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.numberValue(key: String, fallback: Double = 0.0): Double = optDouble(key, fallback).let { if (it.isFinite()) it else fallback }

internal fun JSONObject.objectValue(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

internal fun JSONObject.arrayValue(key: String): JSONArray = optJSONArray(key) ?: JSONArray()

data class UserProfile(
    val id: Int = 0,
    val name: String = "",
    val email: String = "",
) {
    companion object {
        fun fromJson(json: JSONObject) = UserProfile(
            id = json.optInt("id", 0),
            name = json.stringValue("name"),
            email = json.stringValue("email"),
        )
    }
}

data class EntryRecord(
    val id: Int = 0,
    val kind: String = "asset",
    val category: String = "cash",
    val name: String = "",
    val institution: String = "",
    val amount: Double = 0.0,
    val principal: Double = 0.0,
    val annualRate: Double = 0.0,
    val frequency: String = "monthly",
    val emi: Double = 0.0,
    val tenureMonths: Int = 0,
    val startDate: String? = null,
    val maturityDate: String? = null,
    val growthRate: Double = 0.0,
    val notes: String = "",
) {
    val isDeposit: Boolean get() = kind == "asset" && (category == "fixed_deposit" || category == "recurring_deposit")

    fun toRequestJson(): JSONObject = JSONObject()
        .put("kind", kind)
        .put("category", category)
        .put("name", name.trim())
        .put("institution", institution.trim())
        .put("amount", amount)
        .put("principal", principal)
        .put("annual_rate", annualRate)
        .put("frequency", frequency)
        .put("emi", emi)
        .put("tenure_months", tenureMonths)
        .put("start_date", startDate?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        .put("maturity_date", maturityDate?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        .put("growth_rate", growthRate)
        .put("notes", notes)

    companion object {
        fun fromJson(json: JSONObject) = EntryRecord(
            id = json.optInt("id", 0),
            kind = json.stringValue("kind", "asset"),
            category = json.stringValue("category", "other"),
            name = json.stringValue("name"),
            institution = json.stringValue("institution"),
            amount = json.numberValue("amount"),
            principal = json.numberValue("principal"),
            annualRate = json.numberValue("annual_rate"),
            frequency = json.stringValue("frequency", "monthly"),
            emi = json.numberValue("emi"),
            tenureMonths = json.optInt("tenure_months", 0),
            startDate = json.nullableStringValue("start_date"),
            maturityDate = json.nullableStringValue("maturity_date"),
            growthRate = json.numberValue("growth_rate"),
            notes = json.stringValue("notes"),
        )
    }
}

data class MonthlyTotals(
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val emi: Double = 0.0,
    val surplus: Double = 0.0,
) {
    companion object {
        fun fromJson(json: JSONObject) = MonthlyTotals(
            income = json.numberValue("income"),
            expenses = json.numberValue("expenses"),
            emi = json.numberValue("emi"),
            surplus = json.numberValue("surplus"),
        )
    }
}

data class HealthMetrics(
    val savingsRatePct: Double = 0.0,
    val debtToIncomePct: Double = 0.0,
    val emergencyFundMonths: Double = 0.0,
    val monthlySurplus: Double = 0.0,
) {
    companion object {
        fun fromJson(json: JSONObject) = HealthMetrics(
            savingsRatePct = json.numberValue("savings_rate_pct"),
            debtToIncomePct = json.numberValue("debt_to_income_pct"),
            emergencyFundMonths = json.numberValue("emergency_fund_months"),
            monthlySurplus = json.numberValue("monthly_surplus"),
        )
    }
}

data class HealthSummary(
    val score: Int = 0,
    val label: String = "Building",
    val metrics: HealthMetrics = HealthMetrics(),
    val disclaimer: String = "",
) {
    companion object {
        fun fromJson(json: JSONObject) = HealthSummary(
            score = json.optInt("score", 0),
            label = json.stringValue("label", "Building"),
            metrics = HealthMetrics.fromJson(json.objectValue("metrics")),
            disclaimer = json.stringValue("disclaimer"),
        )
    }
}

data class AssetAllocation(val category: String, val value: Double)

data class NetWorthProjection(
    val year: Int = 0,
    val assets: Double = 0.0,
    val liabilities: Double = 0.0,
    val netWorth: Double = 0.0,
    val date: String = "",
    val financialYearEnd: String = "",
    val financialYear: String = "",
    val realNetWorth: Double = 0.0,
) {
    companion object {
        fun fromJson(json: JSONObject) = NetWorthProjection(
            year = json.optInt("year", 0),
            assets = json.numberValue("assets"),
            liabilities = json.numberValue("liabilities"),
            netWorth = json.numberValue("net_worth"),
            date = json.stringValue("date"),
            financialYearEnd = json.stringValue("financial_year_end"),
            financialYear = json.stringValue("financial_year"),
            realNetWorth = json.numberValue("real_net_worth", json.numberValue("net_worth")),
        )
    }
}

data class DashboardProjectionSet(
    val oneYear: NetWorthProjection = NetWorthProjection(year = 1),
    val fiveYear: NetWorthProjection = NetWorthProjection(year = 5),
    val horizons: List<NetWorthProjection> = emptyList(),
    val disclaimer: String = "",
)

data class FinancialDashboard(
    val asOf: String = "",
    val totalAssets: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val netWorth: Double = 0.0,
    val monthly: MonthlyTotals = MonthlyTotals(),
    val assetBreakdown: List<AssetAllocation> = emptyList(),
    val health: HealthSummary = HealthSummary(),
    val projections: DashboardProjectionSet = DashboardProjectionSet(),
) {
    companion object {
        fun fromJson(json: JSONObject): FinancialDashboard {
            val totals = json.objectValue("totals")
            val projectionJson = json.objectValue("projections")
            val breakdownJson = json.arrayValue("asset_breakdown")
            val breakdown = (0 until breakdownJson.length()).map { index ->
                val row = breakdownJson.getJSONObject(index)
                AssetAllocation(row.stringValue("category"), row.numberValue("value"))
            }
            return FinancialDashboard(
                asOf = json.stringValue("as_of"),
                totalAssets = totals.numberValue("assets"),
                totalLiabilities = totals.numberValue("liabilities"),
                netWorth = totals.numberValue("net_worth"),
                monthly = MonthlyTotals.fromJson(json.objectValue("monthly")),
                assetBreakdown = breakdown,
                health = HealthSummary.fromJson(json.objectValue("health")),
                projections = DashboardProjectionSet(
                    oneYear = NetWorthProjection.fromJson(projectionJson.objectValue("one_year")),
                    fiveYear = NetWorthProjection.fromJson(projectionJson.objectValue("five_year")),
                    horizons = projectionJson.arrayValue("horizons").let { arr -> (0 until arr.length()).map { NetWorthProjection.fromJson(arr.getJSONObject(it)) } },
                    disclaimer = projectionJson.stringValue("disclaimer"),
                ),
            )
        }
    }
}

data class Assumptions(
    val salaryGrowth: Double = 8.0,
    val equityReturn: Double = 10.0,
    val propertyGrowth: Double = 6.0,
    val inflation: Double = 5.0,
    val monthlyInvestment: Double = 0.0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("salary_growth", salaryGrowth)
        .put("equity_return", equityReturn)
        .put("property_growth", propertyGrowth)
        .put("inflation", inflation)
        .put("monthly_investment", monthlyInvestment)

    companion object {
        fun fromJson(json: JSONObject) = Assumptions(
            salaryGrowth = json.numberValue("salary_growth", 8.0),
            equityReturn = json.numberValue("equity_return", 10.0),
            propertyGrowth = json.numberValue("property_growth", 6.0),
            inflation = json.numberValue("inflation", 5.0),
            monthlyInvestment = json.numberValue("monthly_investment"),
        )
    }
}

data class HistorySnapshot(
    val date: String,
    val netWorth: Double,
    val id: Int = 0,
    val assets: Double? = null,
    val liabilities: Double? = null,
    val savingsRate: Double? = null,
    val source: String = "auto",
    val note: String = "",
) {
    companion object {
        fun fromJson(row: JSONObject) = HistorySnapshot(
            date = row.stringValue("date"),
            netWorth = row.numberValue("net_worth"),
            id = row.optInt("id", 0),
            assets = if (row.isNull("assets")) null else row.numberValue("assets"),
            liabilities = if (row.isNull("liabilities")) null else row.numberValue("liabilities"),
            savingsRate = if (row.isNull("savings_rate")) null else row.numberValue("savings_rate"),
            source = row.stringValue("source", "auto"),
            note = row.stringValue("note"),
        )
    }
}

data class ProjectionResponse(
    val scenario: String = "base",
    val assumptions: Assumptions = Assumptions(),
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val monthlyEmi: Double = 0.0,
    val projections: List<NetWorthProjection> = emptyList(),
) {
    companion object {
        fun fromJson(json: JSONObject): ProjectionResponse {
            val rows = json.arrayValue("projections")
            return ProjectionResponse(
                scenario = json.stringValue("scenario", "base"),
                assumptions = Assumptions.fromJson(json.objectValue("assumptions")),
                monthlyIncome = json.numberValue("monthly_income"),
                monthlyExpenses = json.numberValue("monthly_expenses"),
                monthlyEmi = json.numberValue("monthly_emi"),
                projections = (0 until rows.length()).map { NetWorthProjection.fromJson(rows.getJSONObject(it)) },
            )
        }
    }
}
