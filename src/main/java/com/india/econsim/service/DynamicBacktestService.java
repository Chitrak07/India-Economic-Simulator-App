package com.india.econsim.service;

import com.google.gson.JsonObject;
import com.india.econsim.model.EconomyState;
import com.india.econsim.service.BacktestResult.IndicatorResult;
import com.india.econsim.service.HistoricalDataFetcher.HistoricalDataException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Fully dynamic World Bank-driven backtest engine.
 *
 * <p>Unlike {@link TimeMachineTestService} (which uses hand-coded baseline profiles),
 * this service seeds <em>every</em> indicator directly from real World Bank API data
 * for the chosen {@code baselineYear}, simulates one year forward, then fetches
 * actual data for {@code baselineYear + 1} and compares the two side-by-side.</p>
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Call {@link HistoricalDataFetcher#fetchAndSave(Path)} once to populate the
 *       JSON cache (or reuse an existing cache file).</li>
 *   <li>Call {@link #runDynamicBacktest(int, Path)} with any year 2006–2023.</li>
 *   <li>The engine seeds an {@link EconomyState} from the WB actuals for year T,
 *       advances 4 quarters, then reads WB actuals for year T+1 and builds a
 *       {@link BacktestResult} with 8 indicator accuracy rows.</li>
 * </ol>
 *
 * <h2>Indicators compared</h2>
 * <ul>
 *   <li>GDP Growth Rate (%)</li>
 *   <li>CPI Inflation Rate (%)</li>
 *   <li>Unemployment Rate (%)</li>
 *   <li>USD/INR Exchange Rate</li>
 *   <li>Exports of Goods &amp; Services (USD bn)</li>
 *   <li>Current Account Balance (% of GDP)</li>
 *   <li>Government Debt (% of GDP)</li>
 *   <li>Lending Interest Rate (%)</li>
 * </ul>
 */
public final class DynamicBacktestService {

    private static final Logger LOG = Logger.getLogger(DynamicBacktestService.class.getName());

    /** Default data file path — same as {@link TimeMachineTestService}. */
    public static final Path DEFAULT_DATA_FILE =
            Path.of("src/main/resources/india_historical_economy.json");

    // USD conversion scale factors for WB raw values
    private static final double USD_TO_BILLION = 1e-9;

    private DynamicBacktestService() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Runs a fully data-driven backtest for {@code baselineYear → baselineYear+1}.
     *
     * @param baselineYear year T to seed from (2006–2023)
     * @return {@link BacktestResult} with real vs simulated comparison
     * @throws HistoricalDataException  if the data file is missing — call
     *         {@link HistoricalDataFetcher#fetchAndSave(Path)} first
     * @throws IllegalArgumentException if year is outside 2006–2023
     */
    public static BacktestResult runDynamicBacktest(int baselineYear)
            throws HistoricalDataException {
        return runDynamicBacktest(baselineYear, DEFAULT_DATA_FILE);
    }

    /**
     * Same as {@link #runDynamicBacktest(int)} but with an explicit data file path.
     */
    public static BacktestResult runDynamicBacktest(int baselineYear, Path dataFile)
            throws HistoricalDataException {

        if (baselineYear < 2006 || baselineYear > 2023) {
            throw new IllegalArgumentException(
                "baselineYear must be 2006–2023 (got " + baselineYear + ")");
        }

        int targetYear = baselineYear + 1;
        LOG.info("DynamicBacktest: seeding from WB data for %d, forecasting %d"
                .formatted(baselineYear, targetYear));

        // ── Step 1: Load the JSON cache ───────────────────────────────────────
        JsonObject root = HistoricalDataFetcher.loadFromFile(dataFile);

        // ── Step 2: Seed EconomyState from WB actuals for baselineYear ────────
        EconomyState state = seedFromWorldBank(root, baselineYear);

        // ── Step 3: Simulate 4 quarters forward ──────────────────────────────
        for (int q = 0; q < 4; q++) {
            SimulationEngine.advanceQuarter(state);
        }

        // ── Step 4: Read WB actuals for targetYear ────────────────────────────
        double actualGdp     = get(root, "gdpGrowthPct",        targetYear);
        double actualCpi     = get(root, "cpiInflationPct",      targetYear);
        double actualUnemp   = get(root, "unemploymentPct",      targetYear);
        double actualFx      = get(root, "fxUsdLcu",             targetYear);
        double actualExp     = rawToUsdBn(get(root, "exportsUsdBillion",  targetYear));
        double actualCa      = get(root, "currentAccountPctGdp", targetYear);
        double actualDebt    = get(root, "govtDebtPctGdp",       targetYear);
        double actualLending = get(root, "lendingRatePct",       targetYear);

        // ── Step 5: Build accuracy rows ───────────────────────────────────────
        List<IndicatorResult> rows = new ArrayList<>();

        addRow(rows, "GDP Annual Growth Rate",      actualGdp,     state.getGdpAnnualGrowthRate(), "%",
               "WB NY.GDP.MKTP.KD.ZG vs SimulationEngine");
        addRow(rows, "CPI Inflation Rate",          actualCpi,     state.getInflationRate(),        "%",
               "WB FP.CPI.TOTL.ZG vs SimulationEngine");
        addRow(rows, "Unemployment Rate",           actualUnemp,   state.getUnemploymentRate(),     "%",
               "WB SL.UEM.TOTL.ZS (ILO modelled) vs SimulationEngine");
        addRow(rows, "USD/INR Exchange Rate",       actualFx,      state.getCurrencyUsdInr(),       "INR",
               "WB PA.NUS.FCRF vs SimulationEngine");
        addRow(rows, "Exports of G&S (USD bn/yr)",  actualExp,     state.getExportsUsdBillion() * 4, "USD bn",
               "WB NE.EXP.GNFS.CD (annual) vs 4× quarterly sim");
        addRow(rows, "Current Account (% GDP)",     actualCa,      state.getCurrentAccountToGdp(), "% GDP",
               "WB BN.CAB.XOKA.GD.ZS vs SimulationEngine");
        addRow(rows, "Government Debt (% GDP)",     actualDebt,    state.getGovernmentDebtToGdp(), "% GDP",
               "WB GC.DOD.TOTL.GD.ZS vs SimulationEngine");
        addRow(rows, "Lending Interest Rate",       actualLending, state.getBankLendingRate(),      "%",
               "WB FR.INR.LEND vs SimulationEngine");

        // Remove rows where WB had no data for that year
        rows.removeIf(r -> Double.isNaN(r.actualValue));

        if (rows.isEmpty()) {
            throw new HistoricalDataException(
                "No World Bank data found for target year " + targetYear +
                ". Re-run HistoricalDataFetcher.fetchAndSave() to refresh the cache.");
        }

        // ── Step 6: Aggregate ─────────────────────────────────────────────────
        double meanError    = rows.stream().mapToDouble(r -> r.absoluteErrorPct).average().orElse(0.0);
        double meanAccuracy = Math.round((100.0 - meanError) * 100.0) / 100.0;
        long   passCount    = rows.stream().filter(r -> "PASS".equals(r.verdict)).count();
        long   warnCount    = rows.stream().filter(r -> "WARN".equals(r.verdict)).count();
        long   failCount    = rows.stream().filter(r -> "FAIL".equals(r.verdict)).count();

        String summary = ("Dynamic Backtest %d→%d | Seeded from World Bank API | " +
                "Mean Accuracy: %.1f%% | ✅ PASS: %d | ⚠️ WARN: %d | ❌ FAIL: %d")
                .formatted(baselineYear, targetYear, meanAccuracy, passCount, warnCount, failCount);

        LOG.info(summary);
        return new BacktestResult(baselineYear, targetYear, rows,
                meanAccuracy, passCount, warnCount, failCount, summary);
    }

    // =========================================================================
    // Seeding helper
    // =========================================================================

    /**
     * Builds an {@link EconomyState} seeded with World Bank actuals for {@code year}.
     *
     * <p>Only the indicators that WB tracks are overridden; the rest of the state
     * retains era-appropriate defaults derived from the GDP growth level
     * (scaling PMIs, confidence indices, M1 etc. proportionally).</p>
     */
    public static EconomyState seedFromWorldBank(JsonObject root, int year) {
        EconomyState s = new EconomyState();
        s.setYear(year);
        s.setQuarter(4);

        // GDP
        double gdpGrowth = get(root, "gdpGrowthPct", year);
        if (!Double.isNaN(gdpGrowth)) {
            s.setGdpAnnualGrowthRate(gdpGrowth);
            s.setFullYearGdpGrowth(gdpGrowth);
            s.setPrevAnnualGdpGrowth(gdpGrowth);
        }

        double gdpRaw = get(root, "gdpUsdBillion", year);
        if (!Double.isNaN(gdpRaw)) {
            double gdpBn = gdpRaw * USD_TO_BILLION;
            s.setGdpUsdBillion(round2(gdpBn));
            // Approximate constant-price GDP (₹ Lakh Cr) — WB nominal USD × avg FX / 1e7
            double fx = getOrDefault(root, "fxUsdLcu", year, 67.0);
            s.setGdpConstantPrices(round2(gdpBn * fx / 100.0));  // rough lakh cr
        }

        double gdpPc = get(root, "gdpPerCapitaUsd", year);
        if (!Double.isNaN(gdpPc)) {
            s.setGdpPerCapita(round2(gdpPc));
        }

        // Inflation & Unemployment
        double cpi = get(root, "cpiInflationPct", year);
        if (!Double.isNaN(cpi))  s.setInflationRate(round2(cpi));

        double unemp = get(root, "unemploymentPct", year);
        if (!Double.isNaN(unemp)) {
            s.setUnemploymentRate(round2(unemp));
            s.setEmploymentRate(round2(100.0 - unemp));
        }

        // FX rate
        double fx = get(root, "fxUsdLcu", year);
        if (!Double.isNaN(fx)) s.setCurrencyUsdInr(round2(fx));

        // External sector
        double expRaw = get(root, "exportsUsdBillion", year);
        if (!Double.isNaN(expRaw)) {
            // WB gives annual USD; divide by 4 for quarterly
            s.setExportsUsdBillion(round2(expRaw * USD_TO_BILLION / 4.0));
        }

        double impRaw = get(root, "importsUsdBillion", year);
        if (!Double.isNaN(impRaw)) {
            double impBn = impRaw * USD_TO_BILLION / 4.0;
            s.setImportsUsdBillion(round2(impBn));
            if (!Double.isNaN(expRaw)) {
                s.setBalanceOfTrade(round2(s.getExportsUsdBillion() - impBn));
            }
        }

        double ca = get(root, "currentAccountPctGdp", year);
        if (!Double.isNaN(ca)) {
            s.setCurrentAccountToGdp(round2(ca));
            // Approximate USD mn: CA% × GDP USD bn × 10
            if (!Double.isNaN(gdpRaw)) {
                s.setCurrentAccount(round2(ca / 100.0 * gdpRaw * USD_TO_BILLION * 1000.0));
            }
        }

        double fdi = get(root, "fdiNetPctGdp", year);
        if (!Double.isNaN(fdi) && !Double.isNaN(gdpRaw)) {
            s.setForeignDirectInvestment(round2(fdi / 100.0 * gdpRaw * USD_TO_BILLION / 4.0));
        }

        // Government
        double debt = get(root, "govtDebtPctGdp", year);
        if (!Double.isNaN(debt)) s.setGovernmentDebtToGdp(round2(debt));

        // Money & lending
        double bm = get(root, "broadMoneyPctGdp", year);
        if (!Double.isNaN(bm) && !Double.isNaN(gdpRaw)) {
            // M3 in ₹ thousand crore: broad money % × GDP USD bn × FX / 1e5
            double fxRate = Double.isNaN(fx) ? 67.0 : fx;
            s.setMoneySupplyM3(round2(bm / 100.0 * gdpRaw * USD_TO_BILLION * fxRate / 100.0));
            s.setMoneySupplyM1(round2(s.getMoneySupplyM3() * 0.28)); // M1 ≈ 28% of M3 historically
        }

        double lending = get(root, "lendingRatePct", year);
        if (!Double.isNaN(lending)) {
            s.setBankLendingRate(round2(lending));
            // Repo ≈ lending - 3% spread (rough historical average)
            double impliedRepo = round2(Math.max(4.0, lending - 3.0));
            s.setInterestRate(impliedRepo);
            s.setPrevInterestRate(impliedRepo + 0.25);  // assume mild cut direction
            s.setNeutralRepoRate(round2(impliedRepo - 0.5));
        }

        // Labour force participation
        double lfpr = get(root, "laborForcePctPop", year);
        if (!Double.isNaN(lfpr)) s.setLaborForceParticipation(round2(lfpr));

        // Era-scaled neutral rates
        double growth = Double.isNaN(gdpGrowth) ? 6.5 : gdpGrowth;
        s.setNeutralGdpGrowth(round2(growth));

        // Scale PMIs and confidence from GDP growth (baseline = 6.5% → PMI 52)
        double pmiBase = 48.0 + Math.min(6.0, Math.max(0.0, growth));
        s.setManufacturingPMI(round2(pmiBase + 0.5));
        s.setServicesPMI(round2(pmiBase + 1.0));
        s.setCompositePMI(round2(pmiBase + 1.5));
        s.setBusinessConfidence(round2(100.0 + (growth - 5.0) * 3.0));
        s.setConsumerConfidence(round2(85.0 + (growth - 5.0) * 2.0));

        // Inflation-scaled CPI index (2012=100 base, add ~5 per year from 2012)
        double cpiRate = Double.isNaN(cpi) ? 5.0 : cpi;
        s.setCpiPoints(round2(100.0 + (year - 2012) * cpiRate * 0.8));

        // Era-appropriate monsoon and oil defaults (neutral)
        s.setMonsoonIndex(100.0);
        s.setBrentCrudePrice(eraCrude(year));
        s.setBankingNpaPct(eraNpa(year));
        s.setTaxBuoyancyCoeff(1.05);
        s.setM1ShockPct(0.0);

        // Infra spend defaults by era
        s.setInfraSpend(eraInfra(year));
        s.setPrevInfraSpend(eraInfra(year - 1));
        s.setNeutralInfraSpend(eraInfra(year));

        LOG.info("Seeded EconomyState for year %d: GDP=%.2f%%, CPI=%.2f%%, FX=%.2f"
                .formatted(year, s.getGdpAnnualGrowthRate(), s.getInflationRate(), s.getCurrencyUsdInr()));
        return s;
    }

    // =========================================================================
    // Era defaults (for fields WB doesn't directly provide)
    // =========================================================================

    /** Historical average Brent crude price by year. */
    private static double eraCrude(int year) {
        if (year <= 2008) return 97.0;
        if (year <= 2012) return 110.0;
        if (year <= 2014) return 99.0;
        if (year <= 2016) return 50.0;
        if (year <= 2018) return 71.0;
        if (year <= 2020) return 42.0;
        if (year <= 2022) return 100.0;
        return 83.0;
    }

    /** Historical gross NPA ratio by era. */
    private static double eraNpa(int year) {
        if (year <= 2012) return 3.0;
        if (year <= 2015) return 5.5;
        if (year <= 2018) return 10.0;
        if (year <= 2020) return 8.0;
        return 4.5;
    }

    /** Infrastructure spend as % of GDP by era. */
    private static double eraInfra(int year) {
        if (year <= 2010) return 2.8;
        if (year <= 2014) return 3.2;
        if (year <= 2018) return 3.6;
        if (year <= 2021) return 3.8;
        return 4.1;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void addRow(List<IndicatorResult> rows, String name,
                                double actual, double simulated,
                                String unit, String note) {
        if (!Double.isNaN(actual)) {
            rows.add(new IndicatorResult(name, actual, simulated, unit, note));
        }
    }

    /** Reads a value from the JSON root; returns NaN if absent or null. */
    private static double get(JsonObject root, String key, int year) {
        return HistoricalDataFetcher.getValueForYear(root, key, year);
    }

    /** Like {@link #get} but returns a fallback instead of NaN. */
    private static double getOrDefault(JsonObject root, String key, int year, double fallback) {
        double v = get(root, key, year);
        return Double.isNaN(v) ? fallback : v;
    }

    /** Converts a raw WB USD value to USD billions (WB stores in current USD). */
    private static double rawToUsdBn(double rawUsd) {
        return Double.isNaN(rawUsd) ? Double.NaN : rawUsd;  // already converted in seeding
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
