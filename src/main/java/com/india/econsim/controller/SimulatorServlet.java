package com.india.econsim.controller;

import com.google.gson.JsonObject;
import com.india.econsim.model.EconomyState;
import com.india.econsim.service.DynamicBacktestService;
import com.india.econsim.service.HistoricalDataFetcher;
import com.india.econsim.service.RealityCheckService;
import com.india.econsim.service.SimulationEngine;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@WebServlet(name = "SimulatorServlet", urlPatterns = {"/simulate"})
public class SimulatorServlet extends HttpServlet {
    @Serial
    private static final long serialVersionUID = -6852783344442364087L;

    private static final Logger LOG = Logger.getLogger(SimulatorServlet.class.getName());
    private static final Path HISTORY_FILE = Path.of("src/main/resources/india_historical_economy.json");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(true);
        if (session.getAttribute("econ") == null) {
            session.setAttribute("econ", new EconomyState());
        }
        if (req.getAttribute("activeTab") == null) {
            req.setAttribute("activeTab", "Overview");
        }
        req.getRequestDispatcher("/simulator.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(true);
        String action = req.getParameter("action");
        String activeTab = req.getParameter("activeTab");
        if (activeTab == null || activeTab.isEmpty()) activeTab = "Overview";
        req.setAttribute("activeTab", activeTab);

        if ("reset".equals(action)) {
            session.setAttribute("econ", new EconomyState());
            req.getRequestDispatcher("/simulator.jsp").forward(req, resp);
            return;
        }

        // ── LOAD HISTORICAL YEAR ─────────────────────────────────────────────
        if ("loadHistoricalYear".equals(action)) {
            int loadYear = 2011;
            try {
                String y = req.getParameter("histYear");
                if (y != null && !y.isEmpty()) loadYear = Integer.parseInt(y);
            } catch (NumberFormatException ignored) {}

            try {
                com.google.gson.JsonObject root = HistoricalDataFetcher.loadFromFile(HISTORY_FILE);
                EconomyState seeded = DynamicBacktestService.seedFromWorldBank(root, loadYear);
                seeded.setLastTurnSummary(String.format(
                        "📅 Loaded World Bank actuals for %d. Adjust your policy levers, then click +1 Year to simulate %d. " +
                                "Finally, click '🔬 Check With Reality' to dynamically compare your projection against WB %d actuals.",
                        loadYear, loadYear + 1, loadYear + 1));
                session.setAttribute("econ", seeded);
                req.setAttribute("loadYearMsg", "✅ Loaded WB actuals for " + loadYear + ". Simulate forward, then click Check With Reality.");
            } catch (HistoricalDataFetcher.HistoricalDataException e) {
                LOG.warning("loadHistoricalYear failed: " + e.getMessage());
                req.setAttribute("loadYearMsg", "❌ " + e.getMessage() + " — Click '🌐 Fetch WB Data' first.");
            }
            req.getRequestDispatcher("/simulator.jsp").forward(req, resp);
            return;
        }

        // ── DYNAMIC REALITY CHECK (Replaces Hardcoded 2015 Check) ─────────────
        if ("dynamicRealityCheck".equals(action)) {
            EconomyState econ = (EconomyState) session.getAttribute("econ");
            if (econ == null) econ = new EconomyState();

            int compareYear = econ.getYear(); // Evaluates the CURRENT simulated year against JSON

            try {
                com.google.gson.JsonObject root = HistoricalDataFetcher.loadFromFile(HISTORY_FILE);
                List<RealityCheckService.RealityRow> rows = buildDynamicRealityRows(econ, root, compareYear);

                double totalErr = rows.stream().mapToDouble(r -> r.errorPct).sum();
                double meanAcc  = rows.isEmpty() ? 0.0 : Math.round((100.0 - totalErr / rows.size()) * 100.0) / 100.0;
                long pass = rows.stream().filter(r -> "PASS".equals(r.verdict)).count();
                long warn = rows.stream().filter(r -> "WARN".equals(r.verdict)).count();
                long fail = rows.stream().filter(r -> "FAIL".equals(r.verdict)).count();

                RealityCheckService.RealityCheckResult result = new RealityCheckService.RealityCheckResult();
                result.rows.addAll(rows);
                result.meanAccuracy = meanAcc;
                result.summary = String.format(
                        "Simulated Results vs World Bank Actuals for %d | " +
                                "Mean Accuracy: %.1f%% | ✅ PASS: %d | ⚠️ WARN: %d | ❌ FAIL: %d",
                        compareYear, meanAcc, pass, warn, fail);

                req.setAttribute("dynRealityResult", result);
                req.setAttribute("dynRealityYear", compareYear);
            } catch (HistoricalDataFetcher.HistoricalDataException e) {
                req.setAttribute("loadYearMsg", "❌ " + e.getMessage() + " — Fetch WB data first.");
            }

            req.getRequestDispatcher("/simulator.jsp").forward(req, resp);
            return;
        }

        if ("fetchHistory".equals(action)) {
            try {
                HistoricalDataFetcher.fetchAndSave(HISTORY_FILE);
                req.setAttribute("loadYearMsg", "✅ World Bank data fetched successfully! You can now load any historical year.");
            } catch (HistoricalDataFetcher.HistoricalDataException e) {
                req.setAttribute("loadYearMsg", "❌ Fetch failed: " + e.getMessage());
            }
            req.getRequestDispatcher("/simulator.jsp").forward(req, resp);
            return;
        }

        if ("editIndicator".equals(action)) {
            EconomyState econ = (EconomyState) session.getAttribute("econ");
            if (econ == null) econ = new EconomyState();
            String fieldName = req.getParameter("fieldName");
            double newVal = parseDouble(req, "fieldValue", Double.NaN);
            if (fieldName != null && !Double.isNaN(newVal)) {
                applyIndicatorOverride(econ, fieldName, newVal);
                econ.setLastTurnSummary(String.format(
                        "[Manual Override] %s set to %.4f. Adjust policy sliders and click +1 Quarter / +1 Year.",
                        fieldName, newVal));
            }
            session.setAttribute("econ", econ);
            req.getRequestDispatcher("/simulator.jsp").forward(req, resp);
            return;
        }

        EconomyState econ = (EconomyState) session.getAttribute("econ");
        if (econ == null) econ = new EconomyState();

        Map<String, Double> preTurnSnapshot = captureSnapshot(econ);
        List<String> policyChanges = new ArrayList<>();

        // Tax Levers
        econ.setCorporateTaxRate(parseAndTrack(req, "corporateTaxRate", "Corporate Tax", "%", econ.getCorporateTaxRate(), policyChanges));
        econ.setPersonalIncomeTaxRate(parseAndTrack(req, "personalIncomeTaxRate", "Personal Income Tax", "%", econ.getPersonalIncomeTaxRate(), policyChanges));
        econ.setSalesTaxGstRate(parseAndTrack(req, "salesTaxGstRate", "Sales Tax / GST", "%", econ.getSalesTaxGstRate(), policyChanges));
        econ.setSocialSecurityRate(parseAndTrack(req, "socialSecurityRate", "Social Security (EPFO)", "%", econ.getSocialSecurityRate(), policyChanges));
        econ.setWithholdingTaxRate(parseAndTrack(req, "withholdingTaxRate", "Withholding Tax", "%", econ.getWithholdingTaxRate(), policyChanges));
        econ.setCustomsDutyRate(parseAndTrack(req, "customsDutyRate", "Customs & Import Duty", "%", econ.getCustomsDutyRate(), policyChanges));

        // RBI Monetary Levers
        econ.setInterestRate(parseAndTrack(req, "interestRate", "RBI Repo Rate", "%", econ.getInterestRate(), policyChanges));
        econ.setCashReserveRatio(parseAndTrack(req, "cashReserveRatio", "Cash Reserve Ratio (CRR)", "%", econ.getCashReserveRatio(), policyChanges));

        // Sectoral Budget Allocations
        econ.setInfraSpend(parseAndTrack(req, "infraSpend", "Infrastructure Spend", "% of GDP", econ.getInfraSpend(), policyChanges));
        econ.setEducationSpend(parseAndTrack(req, "educationSpend", "Education Spend", "% of GDP", econ.getEducationSpend(), policyChanges));
        econ.setHealthSpend(parseAndTrack(req, "healthSpend", "Healthcare Spend", "% of GDP", econ.getHealthSpend(), policyChanges));
        econ.setTechSpend(parseAndTrack(req, "techSpend", "Tech & R&D Spend", "% of GDP", econ.getTechSpend(), policyChanges));
        econ.setMachinerySpend(parseAndTrack(req, "machinerySpend", "Machinery / PLI Spend", "% of GDP", econ.getMachinerySpend(), policyChanges));
        econ.setItServicesSpend(parseAndTrack(req, "itServicesSpend", "IT & Digital Services Spend", "% of GDP", econ.getItServicesSpend(), policyChanges));
        econ.setTourismSpend(parseAndTrack(req, "tourismSpend", "Tourism Spend", "% of GDP", econ.getTourismSpend(), policyChanges));

        // Exogenous shock levers
        econ.setBrentCrudePrice(parseDouble(req, "brentCrudePrice", econ.getBrentCrudePrice()));
        econ.setMonsoonIndex(parseDouble(req, "monsoonIndex", econ.getMonsoonIndex()));
        econ.setM1ShockPct(parseDouble(req, "m1ShockPct", econ.getM1ShockPct()));
        econ.setBankingNpaPct(parseDouble(req, "bankingNpaPct", econ.getBankingNpaPct()));
        econ.setTaxBuoyancyCoeff(parseDouble(req, "taxBuoyancyCoeff", econ.getTaxBuoyancyCoeff()));
        econ.setNeutralRepoRate(parseDouble(req, "neutralRepoRate", econ.getNeutralRepoRate()));

        int steps = "advanceYear".equals(action) ? 4 : 1;
        for (int i = 0; i < steps; i++) {
            SimulationEngine.advanceQuarter(econ);
        }

        if (steps > 1) reconcileMultiQuarterDeltas(econ, preTurnSnapshot);

        String policyAuditSummary;
        if (!policyChanges.isEmpty()) {
            policyAuditSummary = "Policy Levers Changed (" + (steps == 4 ? "+1 Year" : "+1 Quarter") + "): "
                    + String.join(" | ", policyChanges) + ". ";
            for (Map.Entry<String, String> entry : econ.getReasons().entrySet()) {
                entry.setValue("[" + policyAuditSummary.trim() + "] — " + entry.getValue());
            }
        } else {
            policyAuditSummary = "No Policy Sliders Changed (" + (steps == 4 ? "+1 Year" : "+1 Quarter")
                    + " autonomous time-lagged transmission). ";
        }

        econ.setLastTurnSummary(String.format(
                "%sIn %d Q%d, Real Annual GDP Growth is %.2f%% (%+.2f%%), CPI Inflation is %.2f%% (%+.2f%%), Unemployment is %.2f%% (%+.2f%%), and Fiscal Budget is %.2f%% of GDP (%+.2f%%).",
                policyAuditSummary,
                econ.getYear(), econ.getQuarter(),
                econ.getGdpAnnualGrowthRate(), econ.getDelta("gdpAnnualGrowthRate"),
                econ.getInflationRate(), econ.getDelta("inflationRate"),
                econ.getUnemploymentRate(), econ.getDelta("unemploymentRate"),
                econ.getGovernmentBudget(), econ.getDelta("governmentBudget")
        ));

        session.setAttribute("econ", econ);
        req.getRequestDispatcher("/simulator.jsp").forward(req, resp);
    }

    private List<RealityCheckService.RealityRow> buildDynamicRealityRows(
            EconomyState econ, JsonObject root, int compareYear) {

        List<RealityCheckService.RealityRow> rows = new ArrayList<>();

        double gdpGrowth = HistoricalDataFetcher.getValueForYear(root, "gdpGrowthPct", compareYear);
        if (!Double.isNaN(gdpGrowth)) rows.add(new RealityCheckService.RealityRow("GDP Annual Growth Rate (%)", gdpGrowth, econ.getGdpAnnualGrowthRate(), "%"));

        double cpi = HistoricalDataFetcher.getValueForYear(root, "cpiInflationPct", compareYear);
        if (!Double.isNaN(cpi)) rows.add(new RealityCheckService.RealityRow("CPI Inflation Rate (%)", cpi, econ.getInflationRate(), "%"));

        double unemp = HistoricalDataFetcher.getValueForYear(root, "unemploymentPct", compareYear);
        if (!Double.isNaN(unemp)) rows.add(new RealityCheckService.RealityRow("Unemployment Rate (%)", unemp, econ.getUnemploymentRate(), "%"));

        double fx = HistoricalDataFetcher.getValueForYear(root, "fxUsdLcu", compareYear);
        if (!Double.isNaN(fx)) rows.add(new RealityCheckService.RealityRow("USD/INR Exchange Rate", fx, econ.getCurrencyUsdInr(), "INR"));

        double expRaw = HistoricalDataFetcher.getValueForYear(root, "exportsUsdBillion", compareYear);
        if (!Double.isNaN(expRaw)) rows.add(new RealityCheckService.RealityRow("Exports of G&S (USD bn/yr)", expRaw * 1e-9, econ.getExportsUsdBillion() * 4.0, "USD bn"));

        double ca = HistoricalDataFetcher.getValueForYear(root, "currentAccountPctGdp", compareYear);
        if (!Double.isNaN(ca)) rows.add(new RealityCheckService.RealityRow("Current Account (% of GDP)", ca, econ.getCurrentAccountToGdp(), "% GDP"));

        double fdi = HistoricalDataFetcher.getValueForYear(root, "fdiNetPctGdp", compareYear);
        if (!Double.isNaN(fdi) && !Double.isNaN(expRaw)) {
            double actualFdiBn = (fdi / 100.0) * (HistoricalDataFetcher.getValueForYear(root, "gdpUsdBillion", compareYear) * 1e-9) / 4.0;
            rows.add(new RealityCheckService.RealityRow("FDI Net Inflows (USD bn/qtr)", actualFdiBn, econ.getForeignDirectInvestment(), "USD bn"));
        }

        double debt = HistoricalDataFetcher.getValueForYear(root, "govtDebtPctGdp", compareYear);
        if (!Double.isNaN(debt)) rows.add(new RealityCheckService.RealityRow("Government Debt (% of GDP)", debt, econ.getGovernmentDebtToGdp(), "% GDP"));

        double lending = HistoricalDataFetcher.getValueForYear(root, "lendingRatePct", compareYear);
        if (!Double.isNaN(lending)) rows.add(new RealityCheckService.RealityRow("Lending Interest Rate (%)", lending, econ.getBankLendingRate(), "%"));

        return rows;
    }

    private double parseDouble(HttpServletRequest req, String param, double fallback) {
        try {
            String val = req.getParameter(param);
            return (val != null && !val.isEmpty()) ? Double.parseDouble(val) : fallback;
        } catch (NumberFormatException e) { return fallback; }
    }

    private double parseAndTrack(HttpServletRequest req, String paramName, String label, String unit, double oldValue, List<String> changesLog) {
        try {
            String raw = req.getParameter(paramName);
            if (raw != null && !raw.isEmpty()) {
                double newVal = Double.parseDouble(raw);
                if (Math.abs(newVal - oldValue) >= 0.01) {
                    String dir = newVal > oldValue ? "↑" : "↓";
                    changesLog.add(String.format("%s %s (%.2f → %.2f%s)", label, dir, oldValue, newVal, unit));
                }
                return newVal;
            }
        } catch (NumberFormatException ignored) {}
        return oldValue;
    }

    private Map<String, Double> captureSnapshot(EconomyState s) {
        Map<String, Double> map = new HashMap<>();
        map.put("currencyUsdInr", s.getCurrencyUsdInr());
        map.put("stockMarketSensex", s.getStockMarketSensex());
        map.put("gdpGrowthRate", s.getGdpGrowthRate());
        map.put("gdpAnnualGrowthRate", s.getGdpAnnualGrowthRate());
        map.put("unemploymentRate", s.getUnemploymentRate());
        map.put("inflationRate", s.getInflationRate());
        map.put("balanceOfTrade", s.getBalanceOfTrade());
        map.put("governmentBudget", s.getGovernmentBudget());
        return map;
    }

    private void reconcileMultiQuarterDeltas(EconomyState s, Map<String, Double> before) {
        Map<String, Double> after = captureSnapshot(s);
        Map<String, Double> deltas = s.getDeltas();
        for (Map.Entry<String, Double> entry : after.entrySet()) {
            String key = entry.getKey();
            double startVal = before.getOrDefault(key, entry.getValue());
            double endVal = entry.getValue();
            deltas.put(key, Math.round((endVal - startVal) * 100.0) / 100.0);
        }
    }

    private void applyIndicatorOverride(EconomyState s, String field, double v) {
        switch (field) {
            case "gdpGrowthRate": s.setGdpGrowthRate(v); break;
            case "gdpAnnualGrowthRate": s.setGdpAnnualGrowthRate(v); s.setPrevAnnualGdpGrowth(v); break;
            case "unemploymentRate": s.setUnemploymentRate(v); break;
            case "inflationRate": s.setInflationRate(v); break;
            case "currencyUsdInr": s.setCurrencyUsdInr(v); break;
            case "stockMarketSensex": s.setStockMarketSensex(v); break;
            case "balanceOfTrade": s.setBalanceOfTrade(v); break;
            case "currentAccount": s.setCurrentAccount(v); break;
            case "currentAccountToGdp": s.setCurrentAccountToGdp(v); break;
            case "governmentDebtToGdp": s.setGovernmentDebtToGdp(v); break;
            case "governmentBudget": s.setGovernmentBudget(v); break;
            case "businessConfidence": s.setBusinessConfidence(v); break;
            case "manufacturingPMI": s.setManufacturingPMI(v); break;
            case "servicesPMI": s.setServicesPMI(v); break;
            case "consumerConfidence": s.setConsumerConfidence(v); break;
        }
    }
}