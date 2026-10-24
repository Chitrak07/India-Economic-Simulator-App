package com.india.econsim.controller;

import com.india.econsim.model.EconomyState;
import com.india.econsim.service.SimulationEngine;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "SimulatorServlet", urlPatterns = {"/simulate"})
public class SimulatorServlet extends HttpServlet {
    @Serial
    private static final long serialVersionUID = -6852783344442364087L;

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
        if (activeTab == null || activeTab.isEmpty()) {
            activeTab = "Overview";
        }
        req.setAttribute("activeTab", activeTab);

        // 1. Handle Reset Action
        if ("reset".equals(action)) {
            session.setAttribute("econ", new EconomyState());
            req.getRequestDispatcher("/simulator.jsp").forward(req, resp);
            return;
        }

        // 2. Handle Manual Indicator Override (backtesting seed)
        if ("editIndicator".equals(action)) {
            EconomyState econ = (EconomyState) session.getAttribute("econ");
            if (econ == null) econ = new EconomyState();
            String fieldName = req.getParameter("fieldName");
            double newVal = parseDouble(req, "fieldValue", Double.NaN);
            if (fieldName != null && !Double.isNaN(newVal)) {
                applyIndicatorOverride(econ, fieldName, newVal);
                econ.setLastTurnSummary(String.format(
                    "[Manual Override] %s set to %.4f for backtesting. Adjust policy sliders and click +1 Quarter / +1 Year to simulate forward.",
                    fieldName, newVal));
            }
            session.setAttribute("econ", econ);
            req.getRequestDispatcher("/simulator.jsp").forward(req, resp);
            return;
        }

        EconomyState econ = (EconomyState) session.getAttribute("econ");
        if (econ == null) {
            econ = new EconomyState();
        }

        // 2. Snapshot current indicator values BEFORE advancing time
        // This guarantees that "+1 Year" (4 quarters) computes true 4-quarter net deltas
        Map<String, Double> preTurnSnapshot = captureSnapshot(econ);

        // 3. Track which policy sliders the user modified this turn
        List<String> policyChanges = new ArrayList<>();

        // Update & Audit Tax Levers
        econ.setCorporateTaxRate(parseAndTrack(req, "corporateTaxRate", "Corporate Tax", "%", econ.getCorporateTaxRate(), policyChanges));
        econ.setPersonalIncomeTaxRate(parseAndTrack(req, "personalIncomeTaxRate", "Personal Income Tax", "%", econ.getPersonalIncomeTaxRate(), policyChanges));
        econ.setSalesTaxGstRate(parseAndTrack(req, "salesTaxGstRate", "Sales Tax / GST", "%", econ.getSalesTaxGstRate(), policyChanges));
        econ.setSocialSecurityRate(parseAndTrack(req, "socialSecurityRate", "Social Security (EPFO)", "%", econ.getSocialSecurityRate(), policyChanges));
        econ.setWithholdingTaxRate(parseAndTrack(req, "withholdingTaxRate", "Withholding Tax", "%", econ.getWithholdingTaxRate(), policyChanges));
        econ.setCustomsDutyRate(parseAndTrack(req, "customsDutyRate", "Customs & Import Duty", "%", econ.getCustomsDutyRate(), policyChanges));

        // Update & Audit RBI Monetary Levers
        econ.setInterestRate(parseAndTrack(req, "interestRate", "RBI Repo Rate", "%", econ.getInterestRate(), policyChanges));
        econ.setCashReserveRatio(parseAndTrack(req, "cashReserveRatio", "Cash Reserve Ratio (CRR)", "%", econ.getCashReserveRatio(), policyChanges));

        // Update & Audit Sectoral Budget Allocations (% of GDP)
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

        // 4. Advance Simulation Steps (1 Quarter or 4 Quarters for +1 Year)
        int steps = "advanceYear".equals(action) ? 4 : 1;
        for (int i = 0; i < steps; i++) {
            SimulationEngine.advanceQuarter(econ);
        }

        // 5. Reconcile Multi-Step Deltas (so +1 Year displays full 4-quarter net change)
        if (steps > 1) {
            reconcileMultiQuarterDeltas(econ, preTurnSnapshot);
        }

        // 6. Prepend User's Policy Changes & Append Causal Reason to Each Indicator
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
                "%sIn %d Q%d, Real Annual GDP Growth is %.2f%% (%+.2f%%), CPI Inflation is %.2f%% (%+.2f%%), Unemployment is %.2f%% (%+.2f%%), and Fiscal Budget is %.2f%% of GDP (%+.2f%%). Click any indicator row below to inspect its exact causal breakdown.",
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

    /**
     * Parses a double parameter and logs if the user moved the slider from its prior value.
     */
    private double parseDouble(HttpServletRequest req, String param, double fallback) {
        try {
            String val = req.getParameter(param);
            return (val != null && !val.isEmpty()) ? Double.parseDouble(val) : fallback;
        } catch (NumberFormatException e) { return fallback; }
    }

    private double parseAndTrack(HttpServletRequest req, String paramName, String label, String unit,
                                 double oldValue, List<String> changesLog) {
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
        } catch (NumberFormatException ignored) {
        }
        return oldValue;
    }

    /**
     * Captures all indicator values prior to running the simulation loop.
     */
    private Map<String, Double> captureSnapshot(EconomyState s) {
        Map<String, Double> map = new HashMap<>();
        map.put("currencyUsdInr", s.getCurrencyUsdInr());
        map.put("stockMarketSensex", s.getStockMarketSensex());
        map.put("gdpGrowthRate", s.getGdpGrowthRate());
        map.put("gdpAnnualGrowthRate", s.getGdpAnnualGrowthRate());
        map.put("fullYearGdpGrowth", s.getFullYearGdpGrowth());
        map.put("gdpUsdBillion", s.getGdpUsdBillion());
        map.put("gdpConstantPrices", s.getGdpConstantPrices());
        map.put("grossFixedCapitalFormation", s.getGrossFixedCapitalFormation());
        map.put("gdpPerCapita", s.getGdpPerCapita());
        map.put("gdpPerCapitaPpp", s.getGdpPerCapitaPpp());
        map.put("gnpInrCrore", s.getGnpInrCrore());
        map.put("gdpFromAgriculture", s.getGdpFromAgriculture());
        map.put("gdpFromManufacturing", s.getGdpFromManufacturing());
        map.put("gdpFromConstruction", s.getGdpFromConstruction());
        map.put("gdpFromPublicAdmin", s.getGdpFromPublicAdmin());
        map.put("gdpFromUtilities", s.getGdpFromUtilities());
        map.put("gdpFromMining", s.getGdpFromMining());

        map.put("unemploymentRate", s.getUnemploymentRate());
        map.put("employmentRate", s.getEmploymentRate());
        map.put("laborForceParticipation", s.getLaborForceParticipation());
        map.put("populationMillion", s.getPopulationMillion());
        map.put("minimumWages", s.getMinimumWages());
        map.put("monthlyWages", s.getMonthlyWages());

        map.put("inflationRate", s.getInflationRate());
        map.put("cpiPoints", s.getCpiPoints());
        map.put("consumerFoodPriceIndex", s.getConsumerFoodPriceIndex());
        map.put("cpiHousingUtilities", s.getCpiHousingUtilities());
        map.put("cpiTransportation", s.getCpiTransportation());
        map.put("cpiEducation", s.getCpiEducation());
        map.put("cpiRestaurants", s.getCpiRestaurants());
        map.put("wholesalePriceIndex", s.getWholesalePriceIndex());
        map.put("wpiFoodYoY", s.getWpiFoodYoY());
        map.put("producerPrices", s.getProducerPrices());
        map.put("exportPrices", s.getExportPrices());
        map.put("importPrices", s.getImportPrices());

        map.put("interbankRate", s.getInterbankRate());
        map.put("yieldCurveSpread", s.getYieldCurveSpread());
        map.put("bankLoanGrowthYoY", s.getBankLoanGrowthYoY());
        map.put("moneySupplyM1", s.getMoneySupplyM1());
        map.put("foreignExchangeReserves", s.getForeignExchangeReserves());

        map.put("balanceOfTrade", s.getBalanceOfTrade());
        map.put("exportsUsdBillion", s.getExportsUsdBillion());
        map.put("importsUsdBillion", s.getImportsUsdBillion());
        map.put("currentAccount", s.getCurrentAccount());
        map.put("currentAccountToGdp", s.getCurrentAccountToGdp());
        map.put("termsOfTrade", s.getTermsOfTrade());
        map.put("foreignDirectInvestment", s.getForeignDirectInvestment());
        map.put("remittancesUsdBillion", s.getRemittancesUsdBillion());
        map.put("externalDebtUsdBillion", s.getExternalDebtUsdBillion());
        map.put("touristArrivals", s.getTouristArrivals());

        map.put("governmentBudget", s.getGovernmentBudget());
        map.put("governmentDebtToGdp", s.getGovernmentDebtToGdp());
        map.put("governmentSpendingToGdp", s.getGovernmentSpendingToGdp());
        map.put("governmentRevenues", s.getGovernmentRevenues());
        map.put("fiscalExpenditure", s.getFiscalExpenditure());
        map.put("corruptionIndex", s.getCorruptionIndex());

        map.put("businessConfidence", s.getBusinessConfidence());
        map.put("manufacturingPMI", s.getManufacturingPMI());
        map.put("servicesPMI", s.getServicesPMI());
        map.put("compositePMI", s.getCompositePMI());
        map.put("netBusinessFormations", s.getNetBusinessFormations());
        map.put("industrialProductionYoY", s.getIndustrialProductionYoY());
        map.put("manufacturingProductionYoY", s.getManufacturingProductionYoY());
        map.put("capacityUtilization", s.getCapacityUtilization());
        map.put("passengerVehicleSales", s.getPassengerVehicleSales());
        map.put("depositGrowthYoY", s.getDepositGrowthYoY());

        map.put("consumerConfidence", s.getConsumerConfidence());
        map.put("consumerDurablesIndex", s.getConsumerDurablesIndex());
        map.put("retailSalesGrowthYoY", s.getRetailSalesGrowthYoY());
        map.put("bankLendingRate", s.getBankLendingRate());
        map.put("gasolinePrices", s.getGasolinePrices());
        map.put("householdsDebtToGdp", s.getHouseholdsDebtToGdp());

        map.put("housingIndex", s.getHousingIndex());
        map.put("infrastructureOutputYoY", s.getInfrastructureOutputYoY());
        map.put("residentialPropertyPricesYoY", s.getResidentialPropertyPricesYoY());

        map.put("hospitalBedsPer1000", s.getHospitalBedsPer1000());
        map.put("publicHealthReadinessIndex", s.getPublicHealthReadinessIndex());
        map.put("gasolinePrices", s.getGasolinePrices());
        map.put("moneySupplyM1", s.getMoneySupplyM1());
        return map;
    }

    /**
     * Overwrites single-quarter deltas with full multi-quarter deltas when advancing +1 Year.
     */
    private void reconcileMultiQuarterDeltas(EconomyState s, Map<String, Double> before) {
        Map<String, Double> after = captureSnapshot(s);
        Map<String, Double> deltas = s.getDeltas();
        for (Map.Entry<String, Double> entry : after.entrySet()) {
            String key = entry.getKey();
            double startVal = before.getOrDefault(key, entry.getValue());
            double endVal = entry.getValue();
            double netDelta = Math.round((endVal - startVal) * 100.0) / 100.0;
            deltas.put(key, netDelta);
        }
    }

    /**
     * Directly sets any EconomyState indicator field by name.
     * Used for manual backtesting overrides via the Edit modal.
     */
    private void applyIndicatorOverride(EconomyState s, String field, double v) {
        switch (field) {
            // GDP
            case "gdpGrowthRate":              s.setGdpGrowthRate(v); break;
            case "gdpAnnualGrowthRate":        s.setGdpAnnualGrowthRate(v); s.setPrevAnnualGdpGrowth(v); break;
            case "fullYearGdpGrowth":          s.setFullYearGdpGrowth(v); break;
            case "gdpUsdBillion":              s.setGdpUsdBillion(v); break;
            case "gdpConstantPrices":          s.setGdpConstantPrices(v); break;
            case "grossFixedCapitalFormation": s.setGrossFixedCapitalFormation(v); break;
            case "gdpPerCapita":               s.setGdpPerCapita(v); break;
            case "gdpPerCapitaPpp":            s.setGdpPerCapitaPpp(v); break;
            case "gnpInrCrore":                s.setGnpInrCrore(v); break;
            case "gdpFromAgriculture":         s.setGdpFromAgriculture(v); break;
            case "gdpFromManufacturing":       s.setGdpFromManufacturing(v); break;
            case "gdpFromConstruction":        s.setGdpFromConstruction(v); break;
            case "gdpFromPublicAdmin":         s.setGdpFromPublicAdmin(v); break;
            case "gdpFromUtilities":           s.setGdpFromUtilities(v); break;
            case "gdpFromMining":              s.setGdpFromMining(v); break;
            // Labour
            case "unemploymentRate":           s.setUnemploymentRate(v); break;
            case "employmentRate":             s.setEmploymentRate(v); break;
            case "laborForceParticipation":    s.setLaborForceParticipation(v); break;
            case "populationMillion":          s.setPopulationMillion(v); break;
            case "minimumWages":               s.setMinimumWages(v); break;
            case "monthlyWages":               s.setMonthlyWages(v); break;
            // Prices
            case "inflationRate":              s.setInflationRate(v); break;
            case "cpiPoints":                  s.setCpiPoints(v); break;
            case "consumerFoodPriceIndex":     s.setConsumerFoodPriceIndex(v); break;
            case "cpiHousingUtilities":        s.setCpiHousingUtilities(v); break;
            case "cpiTransportation":          s.setCpiTransportation(v); break;
            case "cpiEducation":               s.setCpiEducation(v); break;
            case "cpiRestaurants":             s.setCpiRestaurants(v); break;
            case "wholesalePriceIndex":        s.setWholesalePriceIndex(v); break;
            case "wpiFoodYoY":                 s.setWpiFoodYoY(v); break;
            case "producerPrices":             s.setProducerPrices(v); break;
            case "exportPrices":               s.setExportPrices(v); break;
            case "importPrices":               s.setImportPrices(v); break;
            // Money
            case "currencyUsdInr":             s.setCurrencyUsdInr(v); break;
            case "stockMarketSensex":          s.setStockMarketSensex(v); break;
            case "yieldCurveSpread":           s.setYieldCurveSpread(v); break;
            case "interbankRate":              s.setInterbankRate(v); break;
            case "bankLoanGrowthYoY":          s.setBankLoanGrowthYoY(v); break;
            case "moneySupplyM1":              s.setMoneySupplyM1(v); break;
            case "foreignExchangeReserves":    s.setForeignExchangeReserves(v); break;
            // Trade
            case "balanceOfTrade":             s.setBalanceOfTrade(v); break;
            case "exportsUsdBillion":          s.setExportsUsdBillion(v); break;
            case "importsUsdBillion":          s.setImportsUsdBillion(v); break;
            case "currentAccount":             s.setCurrentAccount(v); break;
            case "currentAccountToGdp":        s.setCurrentAccountToGdp(v); break;
            case "termsOfTrade":               s.setTermsOfTrade(v); break;
            case "foreignDirectInvestment":    s.setForeignDirectInvestment(v); break;
            case "remittancesUsdBillion":      s.setRemittancesUsdBillion(v); break;
            case "externalDebtUsdBillion":     s.setExternalDebtUsdBillion(v); break;
            case "touristArrivals":            s.setTouristArrivals(v); break;
            // Government
            case "governmentBudget":           s.setGovernmentBudget(v); break;
            case "governmentDebtToGdp":        s.setGovernmentDebtToGdp(v); break;
            case "governmentSpendingToGdp":    s.setGovernmentSpendingToGdp(v); break;
            case "governmentRevenues":         s.setGovernmentRevenues(v); break;
            case "fiscalExpenditure":          s.setFiscalExpenditure(v); break;
            case "corruptionIndex":            s.setCorruptionIndex(v); break;
            // Business
            case "businessConfidence":         s.setBusinessConfidence(v); break;
            case "manufacturingPMI":           s.setManufacturingPMI(v); break;
            case "servicesPMI":                s.setServicesPMI(v); break;
            case "compositePMI":               s.setCompositePMI(v); break;
            case "netBusinessFormations":      s.setNetBusinessFormations(v); break;
            case "industrialProductionYoY":    s.setIndustrialProductionYoY(v); break;
            case "manufacturingProductionYoY": s.setManufacturingProductionYoY(v); break;
            case "capacityUtilization":        s.setCapacityUtilization(v); break;
            case "passengerVehicleSales":       s.setPassengerVehicleSales(v); break;
            case "depositGrowthYoY":           s.setDepositGrowthYoY(v); break;
            // Consumer
            case "consumerConfidence":         s.setConsumerConfidence(v); break;
            case "consumerDurablesIndex":       s.setConsumerDurablesIndex(v); break;
            case "retailSalesGrowthYoY":        s.setRetailSalesGrowthYoY(v); break;
            case "bankLendingRate":             s.setBankLendingRate(v); break;
            case "gasolinePrices":              s.setGasolinePrices(v); break;
            case "householdsDebtToGdp":         s.setHouseholdsDebtToGdp(v); break;
            // Housing
            case "housingIndex":                s.setHousingIndex(v); break;
            case "infrastructureOutputYoY":     s.setInfrastructureOutputYoY(v); break;
            case "residentialPropertyPricesYoY":s.setResidentialPropertyPricesYoY(v); break;
            // Health
            case "hospitalBedsPer1000":         s.setHospitalBedsPer1000(v); break;
            case "publicHealthReadinessIndex":  s.setPublicHealthReadinessIndex(v); break;
        }
    }
}