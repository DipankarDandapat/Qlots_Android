package com.qlots.app.data

import org.json.JSONArray
import org.json.JSONObject

private fun <T> JSONObject.list(key: String, parse: (JSONObject) -> T): List<T> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { parse(array.getJSONObject(it)) }
}

private fun JSONObject.stringList(key: String): List<String> {
    val array: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optString(it) }
}

private fun JSONObject.optNumber(key: String): Double? = if (!has(key) || isNull(key)) null else numberValue(key)
private fun JSONObject.optText(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject.optFlag(key: String): Boolean? = if (!has(key) || isNull(key)) null else optBoolean(key)

data class DatedValue(val date: String, val value: Double)

// ---------------------------------------------------------------- insights
data class AllocationGroup(val group: String, val value: Double, val pct: Double, val categories: List<AssetAllocation>)

data class CashflowMonth(
    val month: String, val label: String, val year: Int, val isCurrent: Boolean, val isFuture: Boolean,
    val income: Double, val expenses: Double, val emi: Double, val surplus: Double, val spike: Boolean, val seasonal: List<String>,
)

data class CashflowData(val months: List<CashflowMonth>, val averageIncome: Double, val averageExpenses: Double, val emi: Double, val highlights: List<String>, val note: String)

data class SavingsPoint(val label: String, val rate: Double, val saved: Double)

data class SavingsData(
    val currentRate: Double?, val monthlySaved: Double, val monthlyIncome: Double, val monthlyInvestment: Double,
    val investedShare: Double?, val trendChange: Double?, val points: List<SavingsPoint>, val benchmark: Double, val note: String,
)

data class DebtLoan(
    val id: Int, val name: String, val category: String, val balance: Double, val original: Double, val rate: Double, val emi: Double,
    val paidPct: Double, val monthsLeft: Int?, val payoffDate: String?, val interestRemaining: Double?,
)

data class DebtData(
    val totalBalance: Double, val peakBalance: Double, val paidPct: Double, val debtFreeDate: String?, val monthlyEmi: Double,
    val loans: List<DebtLoan>, val history: List<DatedValue>, val projection: List<DatedValue>, val note: String,
)

data class DepositRow(
    val id: Int, val name: String, val institution: String, val type: String, val principal: Double, val annualRate: Double, val compounding: String,
    val startDate: String?, val maturityDate: String?, val daysToMaturity: Int?, val status: String, val investedSoFar: Double, val currentValue: Double,
    val interestSoFar: Double, val maturityValue: Double?, val interestAtMaturity: Double?, val formula: String,
)

data class DepositsData(val rows: List<DepositRow>, val currentValue: Double, val maturityValue: Double, val nextMaturity: String?, val note: String)

data class InsightsData(
    val allocationTotal: Double, val allocation: List<AllocationGroup>, val cashflow: CashflowData,
    val savings: SavingsData, val debt: DebtData, val deposits: DepositsData,
) {
    companion object {
        fun fromJson(json: JSONObject): InsightsData {
            val alloc = json.objectValue("allocation")
            val cash = json.objectValue("cashflow")
            val sav = json.objectValue("savings_rate")
            val debt = json.objectValue("debt")
            val dep = json.objectValue("deposits")
            val depSummary = dep.objectValue("summary")
            return InsightsData(
                allocationTotal = alloc.numberValue("total"),
                allocation = alloc.list("groups") { g ->
                    AllocationGroup(g.stringValue("group"), g.numberValue("value"), g.numberValue("pct"), g.list("categories") { AssetAllocation(it.stringValue("category"), it.numberValue("value")) })
                },
                cashflow = CashflowData(
                    months = cash.list("months") { m ->
                        CashflowMonth(
                            m.stringValue("month"), m.stringValue("label"), m.optInt("year"), m.optBoolean("is_current"), m.optBoolean("is_future"),
                            m.numberValue("income"), m.numberValue("expenses"), m.numberValue("emi"), m.numberValue("surplus"), m.optBoolean("spike"), m.stringList("seasonal_items"),
                        )
                    },
                    averageIncome = cash.numberValue("average_income"), averageExpenses = cash.numberValue("average_expenses"), emi = cash.numberValue("emi"),
                    highlights = cash.stringList("highlights"), note = cash.stringValue("note"),
                ),
                savings = SavingsData(
                    currentRate = sav.optNumber("current_rate"), monthlySaved = sav.numberValue("monthly_saved"), monthlyIncome = sav.numberValue("monthly_income"),
                    monthlyInvestment = sav.numberValue("monthly_investment"), investedShare = sav.optNumber("invested_share_of_income"), trendChange = sav.optNumber("trend_change"),
                    points = sav.list("points") { SavingsPoint(it.stringValue("label"), it.numberValue("rate"), it.numberValue("saved")) },
                    benchmark = sav.numberValue("benchmark", 20.0), note = sav.stringValue("note"),
                ),
                debt = DebtData(
                    totalBalance = debt.numberValue("total_balance"), peakBalance = debt.numberValue("peak_balance"), paidPct = debt.numberValue("paid_pct"),
                    debtFreeDate = debt.optText("debt_free_date"), monthlyEmi = debt.numberValue("monthly_emi"),
                    loans = debt.list("loans") { l ->
                        DebtLoan(
                            l.optInt("id"), l.stringValue("name"), l.stringValue("category"), l.numberValue("balance"), l.numberValue("original"), l.numberValue("rate"),
                            l.numberValue("emi"), l.numberValue("paid_pct"), if (l.isNull("months_left")) null else l.optInt("months_left"), l.optText("payoff_date"), l.optNumber("interest_remaining"),
                        )
                    },
                    history = debt.list("history") { DatedValue(it.stringValue("date"), it.numberValue("balance")) },
                    projection = debt.list("projection") { DatedValue(it.stringValue("date"), it.numberValue("balance")) },
                    note = debt.stringValue("note"),
                ),
                deposits = DepositsData(
                    rows = dep.list("deposits") { d -> parseDeposit(d) },
                    currentValue = depSummary.numberValue("current_value"), maturityValue = depSummary.numberValue("maturity_value"),
                    nextMaturity = depSummary.optText("next_maturity"), note = dep.stringValue("note"),
                ),
            )
        }

        fun parseDeposit(d: JSONObject) = DepositRow(
            d.optInt("id"), d.stringValue("name"), d.stringValue("institution"), d.stringValue("type"), d.numberValue("principal"), d.numberValue("annual_rate"),
            d.stringValue("compounding"), d.optText("start_date"), d.optText("maturity_date"), if (d.isNull("days_to_maturity")) null else d.optInt("days_to_maturity"),
            d.stringValue("status"), d.numberValue("invested_so_far"), d.numberValue("current_value"), d.numberValue("interest_so_far"),
            d.optNumber("maturity_value"), d.optNumber("interest_at_maturity"), d.stringValue("formula"),
        )
    }
}

// ---------------------------------------------------------------- goals
data class GoalItem(
    val id: Int, val name: String, val kind: String, val target: Double, val current: Double, val start: Double, val monthly: Double,
    val expectedReturn: Double, val targetDate: String?, val autoLink: Boolean, val progressPct: Double, val remaining: Double,
    val monthsLeft: Int?, val monthlyRequired: Double?, val projectedCompletion: String?, val onTrack: Boolean?,
    val history: List<DatedValue>, val projection: List<DatedValue>,
) {
    companion object {
        fun fromJson(g: JSONObject) = GoalItem(
            g.optInt("id"), g.stringValue("name"), g.stringValue("kind", "custom"), g.numberValue("target_amount"), g.numberValue("current_amount"),
            g.numberValue("start_amount"), g.numberValue("monthly_contribution"), g.numberValue("expected_return"), g.optText("target_date"),
            g.optBoolean("auto_link_cash"), g.numberValue("progress_pct"), g.numberValue("remaining"), if (g.isNull("months_left")) null else g.optInt("months_left"),
            g.optNumber("monthly_required"), g.optText("projected_completion"), g.optFlag("on_track"),
            g.list("history") { DatedValue(it.stringValue("date"), it.numberValue("amount")) },
            g.list("projection") { DatedValue(it.stringValue("date"), it.numberValue("amount")) },
        )
    }
}

// ---------------------------------------------------------------- saved projections vs actual
data class PlanInfo(val id: Int, val label: String, val createdOn: String, val source: String, val startingNetWorth: Double) {
    companion object {
        fun fromJson(p: JSONObject) = PlanInfo(p.optInt("id"), p.stringValue("label"), p.stringValue("created_on"), p.stringValue("source"), p.numberValue("starting_net_worth"))
    }
}

data class PlanPoint(val date: String, val base: Double, val conservative: Double, val optimistic: Double)
data class ActualPoint(val date: String, val netWorth: Double, val projectedBase: Double)

data class PlanSummary(
    val asOf: String, val actual: Double, val projectedBase: Double, val low: Double, val high: Double, val variance: Double,
    val variancePct: Double, val status: String, val withinRange: Boolean, val hint: String,
)

data class PlanComparison(val plan: PlanInfo, val projected: List<PlanPoint>, val actual: List<ActualPoint>, val summary: PlanSummary?) {
    companion object {
        fun fromJson(json: JSONObject): PlanComparison {
            val s = json.optJSONObject("summary")
            return PlanComparison(
                plan = PlanInfo.fromJson(json.objectValue("plan")),
                projected = json.list("projected") { PlanPoint(it.stringValue("date"), it.numberValue("base"), it.numberValue("conservative"), it.numberValue("optimistic")) },
                actual = json.list("actual") { ActualPoint(it.stringValue("date"), it.numberValue("net_worth"), it.numberValue("projected_base")) },
                summary = s?.let {
                    PlanSummary(
                        it.stringValue("as_of"), it.numberValue("actual"), it.numberValue("projected_base"), it.numberValue("projected_low"), it.numberValue("projected_high"),
                        it.numberValue("variance"), it.numberValue("variance_pct"), it.stringValue("status"), it.optBoolean("within_range"), it.stringValue("hint"),
                    )
                },
            )
        }
    }
}

// ---------------------------------------------------------------- outlook (adjustable projections) and breakdown
data class OutlookPoint(val month: Int, val date: String, val assets: Double, val liabilities: Double, val netWorth: Double, val realNetWorth: Double)

data class OutlookData(val years: Int, val endDate: String, val inflation: Double, val scenarios: Map<String, List<OutlookPoint>>) {
    companion object {
        fun fromJson(json: JSONObject): OutlookData {
            val sc = json.objectValue("scenarios")
            val map = listOf("conservative", "base", "optimistic").associateWith { name ->
                sc.list(name) { OutlookPoint(it.optInt("month"), it.stringValue("date"), it.numberValue("assets"), it.numberValue("liabilities"), it.numberValue("net_worth"), it.numberValue("real_net_worth")) }
            }
            return OutlookData(json.optInt("years"), json.stringValue("end_date"), json.numberValue("inflation"), map)
        }
    }
}

data class BreakdownRow(val name: String, val category: String, val today: Double, val projected: Double, val ratePct: Double, val formula: String, val note: String)
data class LabelValue(val label: String, val value: String)

data class BreakdownData(
    val years: Double, val scenario: String, val asOf: String, val endDate: String, val financialYear: String, val financialYearEnd: String,
    val assetRows: List<BreakdownRow>, val liabilityRows: List<BreakdownRow>, val cashFlow: Map<String, Double>,
    val totalAssets: Double, val totalLiabilities: Double, val netWorth: Double, val realNetWorth: Double, val realFormula: String,
    val healthScore: Int, val healthLabel: String, val assumptions: List<LabelValue>, val disclaimer: String,
) {
    companion object {
        private fun row(r: JSONObject) = BreakdownRow(r.stringValue("name"), r.stringValue("category"), r.numberValue("today"), r.numberValue("projected"), r.numberValue("rate_pct"), r.stringValue("formula"), r.stringValue("note"))

        fun fromJson(json: JSONObject): BreakdownData {
            val totals = json.objectValue("totals")
            val real = json.objectValue("inflation_adjusted")
            val health = json.objectValue("projected_health")
            val flows = json.objectValue("cash_flow")
            return BreakdownData(
                years = json.numberValue("years"), scenario = json.stringValue("scenario"), asOf = json.stringValue("as_of"), endDate = json.stringValue("end_date"),
                financialYear = json.stringValue("financial_year"), financialYearEnd = json.stringValue("financial_year_end"),
                assetRows = json.list("asset_rows") { row(it) }, liabilityRows = json.list("liability_rows") { row(it) },
                cashFlow = flows.keys().asSequence().associateWith { flows.numberValue(it) },
                totalAssets = totals.numberValue("assets"), totalLiabilities = totals.numberValue("liabilities"), netWorth = totals.numberValue("net_worth"),
                realNetWorth = real.numberValue("net_worth"), realFormula = real.stringValue("formula"),
                healthScore = health.optInt("score"), healthLabel = health.stringValue("label"),
                assumptions = json.list("assumptions") { LabelValue(it.stringValue("label"), it.stringValue("value")) }, disclaimer = json.stringValue("disclaimer"),
            )
        }
    }
}

// ---------------------------------------------------------------- planners
data class WhatIfYear(val year: Int, val date: String, val netWorth: Double, val realNetWorth: Double, val liabilities: Double)
data class WhatIfRun(val key: String, val label: String, val description: String, val yearly: List<WhatIfYear>, val finalNetWorth: Double, val finalLiabilities: Double, val delta: Double)
data class WhatIfResult(val years: Int, val endDate: String, val runs: List<WhatIfRun>) {
    companion object {
        fun fromJson(json: JSONObject) = WhatIfResult(
            json.optInt("years"), json.stringValue("end_date"),
            json.list("runs") { r ->
                WhatIfRun(
                    r.stringValue("key"), r.stringValue("label"), r.stringValue("description"),
                    r.list("yearly") { y -> WhatIfYear(y.optInt("year"), y.stringValue("date"), y.numberValue("net_worth"), y.numberValue("real_net_worth"), y.numberValue("liabilities")) },
                    r.numberValue("final_net_worth"), r.numberValue("final_liabilities"), r.numberValue("delta_vs_baseline"),
                )
            },
        )
    }
}

data class PayoffStrategy(
    val key: String, val label: String, val description: String, val debtFree: Boolean, val months: Int?, val debtFreeDate: String?,
    val totalInterest: Double, val interestSaved: Double?, val monthsSaved: Int?, val remaining: Double, val order: List<Pair<String, String>>, val series: List<Pair<Int, Double>>,
)

data class LoanOption(val id: Int, val name: String, val balance: Double, val rate: Double, val emi: Double)

data class LoanPayoffResult(val loans: List<LoanOption>, val strategies: List<PayoffStrategy>, val extraMonthly: Double, val note: String) {
    companion object {
        fun fromJson(json: JSONObject) = LoanPayoffResult(
            json.list("loans") { LoanOption(it.optInt("id"), it.stringValue("name"), it.numberValue("balance"), it.numberValue("rate"), it.numberValue("emi")) },
            json.list("strategies") { s ->
                PayoffStrategy(
                    s.stringValue("key"), s.stringValue("label"), s.stringValue("description"), s.optBoolean("debt_free"), if (s.isNull("months")) null else s.optInt("months"),
                    s.optText("debt_free_date"), s.numberValue("total_interest"), s.optNumber("interest_saved"), if (s.isNull("months_saved")) null else s.optInt("months_saved"),
                    s.numberValue("remaining_balance"),
                    s.list("payoff_order") { it.stringValue("name") to it.stringValue("date") },
                    s.list("series") { it.optInt("month") to it.numberValue("balance") },
                )
            },
            json.numberValue("extra_monthly"), json.stringValue("note"),
        )
    }
}

data class EmergencyOption(val months: Int, val target: Double, val gap: Double)

data class EmergencyPlan(
    val months: Int, val includeEmi: Boolean, val monthlyExpenses: Double, val monthlyEmi: Double, val monthlyEssential: Double, val target: Double,
    val currentLiquid: Double, val gap: Double, val coveredMonths: Double, val progressPct: Double, val buildInMonths: Int, val monthlyNeeded: Double,
    val options: List<EmergencyOption>, val note: String,
) {
    companion object {
        fun fromJson(j: JSONObject) = EmergencyPlan(
            j.optInt("months"), j.optBoolean("include_emi"), j.numberValue("monthly_expenses"), j.numberValue("monthly_emi"), j.numberValue("monthly_essential"),
            j.numberValue("target"), j.numberValue("current_liquid"), j.numberValue("gap"), j.numberValue("covered_months"), j.numberValue("progress_pct"),
            j.optInt("build_in_months"), j.numberValue("monthly_needed"),
            j.list("options") { EmergencyOption(it.optInt("months"), it.numberValue("target"), it.numberValue("gap")) }, j.stringValue("note"),
        )
    }
}

data class GoalCalcResult(
    val target: Double, val current: Double, val months: Int, val targetDate: String, val monthlyRequired: Double, val totalContributions: Double,
    val growth: Double, val alreadyCovered: Boolean, val formula: String, val note: String, val path: List<DatedValue>,
) {
    companion object {
        fun fromJson(j: JSONObject) = GoalCalcResult(
            j.numberValue("target"), j.numberValue("current"), j.optInt("months"), j.stringValue("target_date"), j.numberValue("monthly_required"),
            j.numberValue("total_contributions"), j.numberValue("growth_on_money"), j.optBoolean("already_covered"), j.stringValue("formula"), j.stringValue("note"),
            j.list("path") { DatedValue(it.stringValue("date"), it.numberValue("amount")) },
        )
    }
}
