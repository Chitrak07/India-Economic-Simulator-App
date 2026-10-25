package com.india.econsim.service;

import com.india.econsim.model.EconomyState;

import java.util.ArrayList;
import java.util.List;

public class RealityCheckService {

    public static class RealityRow {
        public final String indicator;
        public final double actual;       // CSO/RBI published value
        public final double simulated;    // what the engine produced
        public final double errorPct;     // |sim-actual|/|actual| * 100
        public final String unit;
        public final String verdict;      // PASS / WARN / FAIL

        public RealityRow(String indicator, double actual, double simulated, String unit) {
            this.indicator = indicator;
            this.actual    = actual;
            this.simulated = simulated;
            this.unit      = unit;
            this.errorPct  = Math.abs(actual) < 0.001 ? 0.0
                    : Math.round(Math.abs(simulated - actual) / Math.abs(actual) * 10000.0) / 100.0;
            this.verdict   = errorPct <= 5.0 ? "PASS" : errorPct <= 15.0 ? "WARN" : "FAIL";
        }
    }

    public static class RealityCheckResult {
        public final List<RealityRow> rows = new ArrayList<>();
        public double meanAccuracy;   // 100 - mean error %
        public String summary;
    }

    /**
     * Seeds FY 2015-16 actuals as baseline, applies FY 2016-17 policy regime,
     * advances 4 quarters, then compares simulated outputs to CSO/RBI actuals.
     * Sources:
     *  GDP growth  : CSO First Advance Estimate — 7.1% (FY2016-17)
     *  CPI         : RBI Annual Report — 4.5% avg (FY2016-17)
     *  Unemployment: CMIE PLFS — ~5.4%
     *  Fiscal def  : Union Budget Actuals — -3.5% of GDP
     *  WPI         : DIPP — +1.7% avg (FY2016-17, recovered from -2.5%)
     *  INR         : RBI — 67.07 avg USD/INR
     *  Exports     : DGCI&S — $274B annual → ~$68.5B/qtr
     *  Current Acct: RBI BOP — -0.7% of GDP
     *  Sensex      : BSE — ~27,000 (Mar 2017)
     *  Bank loans  : RBI — ~8% YoY (post-demonetisation drag)
     */
    public static RealityCheckResult run() {
        // ── Step 1: Seed FY 2015-16 baseline state ──────────────────────────
        EconomyState s = new EconomyState();
        s.setYear(2015);
        s.setQuarter(4);

        // GDP
        s.setGdpAnnualGrowthRate(7.55);
        s.setPrevAnnualGdpGrowth(7.55);
        s.setGdpUsdBillion(2103.59);
        s.setGdpConstantPrices(113647.0);
        s.setGdpFromAgriculture(15717.0);
        s.setGdpFromManufacturing(17474.0);
        s.setGdpFromConstruction(8621.0);
        s.setGdpFromPublicAdmin(11389.0);
        s.setGdpFromUtilities(1842.0);
        s.setGdpFromMining(1621.0);
        s.setGrossFixedCapitalFormation(32000.0);
        s.setGdpPerCapita(1606.0);
        s.setGnpInrCrore(13576000.0);

        // Labour
        s.setUnemploymentRate(5.40);
        s.setEmploymentRate(94.60);
        s.setLaborForceParticipation(53.8);
        s.setPopulationMillion(1310.0);
        s.setMonthlyWages(17000.0);

        // Prices
        s.setInflationRate(4.90);
        s.setCpiPoints(119.0);
        s.setConsumerFoodPriceIndex(122.0);
        s.setWholesalePriceIndex(99.5);   // WPI was negative/flat in FY16

        // Money
        s.setCurrencyUsdInr(65.46);
        s.setStockMarketSensex(25341.0);
        s.setMoneySupplyM1(28500.0);
        s.setMoneySupplyM3(120000.0);
        s.setBankLoanGrowthYoY(10.9);
        s.setInterbankRate(7.88);
        s.setForeignExchangeReserves(360000.0);

        // Trade
        s.setExportsUsdBillion(66.84);
        s.setImportsUsdBillion(96.42);
        s.setBalanceOfTrade(-29.58);
        s.setCurrentAccount(-22.15);
        s.setCurrentAccountToGdp(-1.10);
        s.setForeignDirectInvestment(9.50);
        s.setExternalDebtUsdBillion(485.0);

        // Government
        s.setGovernmentBudget(-3.90);
        s.setGovernmentDebtToGdp(68.50);

        // Business / Consumer
        s.setManufacturingPMI(51.1);
        s.setServicesPMI(53.8);
        s.setCompositePMI(52.6);
        s.setBusinessConfidence(108.0);
        s.setConsumerConfidence(85.0);
        s.setIndustrialProductionYoY(2.4);
        s.setCapacityUtilization(72.0);

        // ── Step 2: Apply FY 2016-17 policy regime ──────────────────────────
        // RBI cut repo 6.75% → 6.25% (Apr 2016), held rest of year
        s.setInterestRate(6.25);
        s.setPrevInterestRate(6.75);   // delta = -0.50 (rate cut)
        s.setCashReserveRatio(4.00);

        // Neutral rates for FY16-17 context
        s.setNeutralRepoRate(6.50);
        s.setNeutralGdpGrowth(7.00);
        s.setNeutralInfraSpend(3.40);

        // Union Budget FY17: rail+road capex push
        s.setInfraSpend(3.70);
        s.setPrevInfraSpend(3.40);
        s.setEducationSpend(2.80);
        s.setHealthSpend(1.40);
        s.setTechSpend(0.80);
        s.setMachinerySpend(1.50);
        s.setItServicesSpend(1.10);
        s.setTourismSpend(0.40);

        // Tax rates FY17
        s.setCorporateTaxRate(30.0);
        s.setPersonalIncomeTaxRate(30.0);
        s.setSalesTaxGstRate(14.5);   // pre-GST service tax
        s.setCustomsDutyRate(10.0);
        s.setWithholdingTaxRate(20.0);
        s.setSocialSecurityRate(24.0);

        // Exogenous: crude crashed $115→$48 avg FY17
        s.setBrentCrudePrice(48.0);
        // Record monsoon: IMD declared 97% of LPA (above normal)
        s.setMonsoonIndex(107.0);
        // Demonetisation Q3 FY17 (Nov 2016): -19% M1 shock
        // We apply it as a one-time shock in Q3 — simulate 2 normal qtrs, then shock, then recovery
        s.setM1ShockPct(0.0);
        s.setBankingNpaPct(9.5);      // NPA peaked ~9.5% in FY17
        s.setTaxBuoyancyCoeff(1.08);  // IDS 2016 + excise windfall

        // ── Step 3: Advance 4 quarters (Q1→Q4 FY2016-17) ───────────────────
        // Q1 FY17 — normal
        SimulationEngine.advanceQuarter(s);
        // Q2 FY17 — normal
        SimulationEngine.advanceQuarter(s);
        // Q3 FY17 — demonetisation shock
        s.setM1ShockPct(-19.0);
        SimulationEngine.advanceQuarter(s);
        // Q4 FY17 — recovery (M1 auto-reset to 0 by engine)
        SimulationEngine.advanceQuarter(s);

        // ── Step 4: Compare to CSO/RBI FY2016-17 actuals ────────────────────
        RealityCheckResult result = new RealityCheckResult();
        List<RealityRow> rows = result.rows;

        rows.add(new RealityRow("GDP Annual Growth Rate (%)",       7.10,  s.getGdpAnnualGrowthRate(),   "%"));
        rows.add(new RealityRow("CPI Inflation Rate (%)",           4.50,  s.getInflationRate(),          "%"));
        rows.add(new RealityRow("WPI Inflation (%)",                1.70,  s.getWpiFoodYoY() * 0.6,      "%"));
        rows.add(new RealityRow("Unemployment Rate (%)",            5.40,  s.getUnemploymentRate(),       "%"));
        rows.add(new RealityRow("Fiscal Deficit (% of GDP)",       -3.50,  s.getGovernmentBudget(),       "% GDP"));
        rows.add(new RealityRow("USD/INR Exchange Rate",           67.07,  s.getCurrencyUsdInr(),         "INR"));
        rows.add(new RealityRow("Exports (USD Billion/Qtr)",       68.50,  s.getExportsUsdBillion(),      "$B"));
        rows.add(new RealityRow("Current Account (% of GDP)",      -0.70,  s.getCurrentAccountToGdp(),   "% GDP"));
        rows.add(new RealityRow("BSE Sensex",                    27000.0,  s.getStockMarketSensex(),      "pts"));
        rows.add(new RealityRow("Bank Loan Growth YoY (%)",         8.00,  s.getBankLoanGrowthYoY(),      "%"));
        rows.add(new RealityRow("Manufacturing PMI",               50.40,  s.getManufacturingPMI(),       "pts"));
        rows.add(new RealityRow("Services PMI",                    48.70,  s.getServicesPMI(),            "pts"));

        // ── Step 5: Compute mean accuracy ───────────────────────────────────
        double totalError = rows.stream().mapToDouble(r -> r.errorPct).sum();
        result.meanAccuracy = Math.round((100.0 - totalError / rows.size()) * 100.0) / 100.0;

        long pass = rows.stream().filter(r -> r.verdict.equals("PASS")).count();
        long warn = rows.stream().filter(r -> r.verdict.equals("WARN")).count();
        long fail = rows.stream().filter(r -> r.verdict.equals("FAIL")).count();

        result.summary = String.format(
            "FY 2015-16 → 2016-17 Backtest | Mean Model Accuracy: %.1f%% | " +
            "✅ PASS (≤5%% error): %d | ⚠️ WARN (≤15%%): %d | ❌ FAIL (>15%%): %d | " +
            "Policy inputs: Repo 6.25%%, Brent $48, Monsoon 107, Demonetisation Q3 (-19%% M1), NPA 9.5%%",
            result.meanAccuracy, pass, warn, fail);

        return result;
    }
}
