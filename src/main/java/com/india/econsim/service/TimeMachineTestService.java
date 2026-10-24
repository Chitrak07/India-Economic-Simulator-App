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
 * Time Machine Backtest Engine for the India Macroeconomic Simulator.
 *
 * <p>Given a {@code baselineYear} T and an {@link EconomyState} pre-seeded with
 * actuals for year T, this service:</p>
 * <ol>
 *   <li>Advances the simulation forward by exactly 4 quarters (one fiscal year).</li>
 *   <li>Reads {@code india_historical_economy.json} to obtain World Bank actuals
 *       for year T+1.</li>
 *   <li>Compares the three core indicators (GDP growth, CPI inflation,
 *       unemployment) against the simulation output.</li>
 *   <li>Returns a structured {@link BacktestResult} with per-indicator accuracy
 *       scores and an aggregate mean accuracy percentage.</li>
 * </ol>
 *
 * <h2>Supported baseline years</h2>
 * <p>Any year for which {@code india_historical_economy.json} contains data for
 * both year T and year T+1 (nominally 2006–2025 once the file has been fetched
 * via {@link HistoricalDataFetcher#fetchAndSave(Path)}).
 *
 * <h2>Pre-built policy profiles</h2>
 * <p>For convenience, {@link #runBacktest(EconomyState, int)} automatically
 * applies a historically accurate policy configuration for each supported
 * baseline year (see {@link #applyHistoricalPolicyProfile(EconomyState, int)}).
 * This mirrors the approach used in {@link RealityCheckService} but is driven
 * by the fetched World Bank ground-truth values rather than hard-coded actuals.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Step 1 — fetch data once (can be done at startup or on demand)
 * Path dataFile = Path.of("src/main/resources/india_historical_economy.json");
 * HistoricalDataFetcher.fetchAndSave(dataFile);
 *
 * // Step 2 — seed a baseline state for year T and run
 * EconomyState baseline = TimeMachineTestService.seedBaselineState(2015);
 * BacktestResult result  = TimeMachineTestService.runBacktest(baseline, 2015);
 *
 * // Step 3 — inspect results
 * System.out.println(result.summary);
 * result.rows.forEach(System.out::println);
 * }</pre>
 */
public final class TimeMachineTestService {

    private static final Logger LOG = Logger.getLogger(TimeMachineTestService.class.getName());

    /** Default path where {@link HistoricalDataFetcher} writes the JSON file. */
    public static final Path DEFAULT_DATA_FILE =
            Path.of("src/main/resources/india_historical_economy.json");

    // ── Private constructor (utility class) ──────────────────────────────────

    private TimeMachineTestService() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Runs a full one-year backtest starting from {@code baselineYear}.
     *
     * <p>The method:</p>
     * <ol>
     *   <li>Applies the historical policy profile for {@code baselineYear} to
     *       {@code state} (repo rate, infra spend, Brent crude, monsoon,
     *       M1 shock for demonetisation year, etc.).</li>
     *   <li>Calls {@link SimulationEngine#advanceQuarter(EconomyState)} 4×.</li>
     *   <li>Loads {@link #DEFAULT_DATA_FILE} and reads World Bank actuals for
     *       {@code baselineYear + 1}.</li>
     *   <li>Compares GDP growth, CPI inflation, and unemployment.</li>
     * </ol>
     *
     * @param state        an {@link EconomyState} pre-seeded with actuals for
     *                     {@code baselineYear} (use {@link #seedBaselineState(int)}
     *                     for a ready-made seed)
     * @param baselineYear the starting year T; the engine will forecast T+1
     * @return structured {@link BacktestResult} with per-indicator accuracy
     * @throws HistoricalDataException if the data file is missing or malformed;
     *                                 call {@link HistoricalDataFetcher#fetchAndSave(Path)} first
     * @throws IllegalArgumentException if {@code baselineYear} is outside 2006–2025
     */
    @SuppressWarnings("unused") // Public API convenience overload — consumed externally
    public static BacktestResult runBacktest(EconomyState state, int baselineYear)
            throws HistoricalDataException {
        return runBacktest(state, baselineYear, DEFAULT_DATA_FILE);
    }

    /**
     * Same as {@link #runBacktest(EconomyState, int)} but accepts an explicit
     * path to the historical data file — useful in tests or when the working
     * directory is not the project root.
     *
     * @param state        pre-seeded baseline state
     * @param baselineYear starting year T
     * @param dataFile     path to {@code india_historical_economy.json}
     * @return structured {@link BacktestResult}
     * @throws HistoricalDataException  on data file errors
     * @throws IllegalArgumentException if {@code baselineYear} is out of range
     */
    public static BacktestResult runBacktest(
            EconomyState state,
            int          baselineYear,
            Path         dataFile) throws HistoricalDataException {

        if (baselineYear < 2006 || baselineYear > 2025) {
            throw new IllegalArgumentException(
                "baselineYear must be between 2006 and 2025 (got " + baselineYear + ")");
        }

        int targetYear = baselineYear + 1;
        LOG.info("TimeMachine backtest: %d → %d".formatted(baselineYear, targetYear));

        // ── Step 1: Pin state to the baseline year ────────────────────────────
        state.setYear(baselineYear);
        state.setQuarter(4);   // start from Q4 of baseline year (end of fiscal year T)

        // ── Step 2: Apply historically accurate policy levers for this year ───
        applyHistoricalPolicyProfile(state, baselineYear);

        // ── Step 3: Simulate 4 quarters forward ──────────────────────────────
        advanceOneYear(state, baselineYear);

        // ── Step 4: Load World Bank ground truth for targetYear ───────────────
        JsonObject dataRoot = HistoricalDataFetcher.loadFromFile(dataFile);

        double actualGdp   = HistoricalDataFetcher.getValueForYear(dataRoot, "gdpGrowthPct",    targetYear);
        double actualCpi   = HistoricalDataFetcher.getValueForYear(dataRoot, "cpiInflationPct", targetYear);
        double actualUnemp = HistoricalDataFetcher.getValueForYear(dataRoot, "unemploymentPct", targetYear);

        // ── Step 5: Build accuracy rows ───────────────────────────────────────
        List<IndicatorResult> rows = new ArrayList<>();

        if (!Double.isNaN(actualGdp)) {
            rows.add(new IndicatorResult(
                "GDP Annual Growth Rate",
                actualGdp,
                state.getGdpAnnualGrowthRate(),
                "%",
                "Real GDP growth: World Bank NY.GDP.MKTP.KD.ZG vs. SimulationEngine output"));
        } else {
            LOG.warning("No World Bank GDP growth data for year %d — skipping row".formatted(targetYear));
        }

        if (!Double.isNaN(actualCpi)) {
            rows.add(new IndicatorResult(
                "CPI Inflation Rate",
                actualCpi,
                state.getInflationRate(),
                "%",
                "CPI inflation: World Bank FP.CPI.TOTL.ZG vs. SimulationEngine output"));
        } else {
            LOG.warning("No World Bank CPI data for year %d — skipping row".formatted(targetYear));
        }

        if (!Double.isNaN(actualUnemp)) {
            rows.add(new IndicatorResult(
                "Unemployment Rate",
                actualUnemp,
                state.getUnemploymentRate(),
                "%",
                "Unemployment: World Bank SL.UEM.TOTL.ZS vs. SimulationEngine output"));
        } else {
            LOG.warning("No World Bank unemployment data for year %d — skipping row".formatted(targetYear));
        }

        if (rows.isEmpty()) {
            throw new HistoricalDataException(
                "No World Bank data found for target year " + targetYear +
                ". The data file may be stale — re-run HistoricalDataFetcher.fetchAndSave().");
        }

        // ── Step 6: Aggregate statistics ─────────────────────────────────────
        double meanError = rows.stream()
                .mapToDouble(r -> r.absoluteErrorPct)
                .average()
                .orElse(0.0);
        double meanAccuracy = Math.round((100.0 - meanError) * 100.0) / 100.0;

        long passCount = rows.stream().filter(r -> "PASS".equals(r.verdict)).count();
        long warnCount = rows.stream().filter(r -> "WARN".equals(r.verdict)).count();
        long failCount = rows.stream().filter(r -> "FAIL".equals(r.verdict)).count();

        String summary = ("TimeMachine Backtest %d→%d | Mean Accuracy: %.1f%% | " +
                "✅ PASS (≤5%% err): %d | ⚠️ WARN (≤15%% err): %d | ❌ FAIL (>15%% err): %d")
                .formatted(baselineYear, targetYear, meanAccuracy, passCount, warnCount, failCount);

        LOG.info(summary);
        rows.forEach(r -> LOG.info("  " + r));

        return new BacktestResult(
                baselineYear, targetYear, rows,
                meanAccuracy, passCount, warnCount, failCount, summary);
    }

    // =========================================================================
    // Convenience factory — pre-seeded baseline states
    // =========================================================================

    /**
     * Returns an {@link EconomyState} pre-seeded with historically accurate
     * indicator values for the given {@code baselineYear}.
     *
     * <p>Profiles are available for 2010, 2013, 2015, 2018, and 2019.
     * For all other years a generic baseline derived from the current default
     * {@link EconomyState} is returned with only the year overridden.</p>
     *
     * @param baselineYear year to seed (2006–2025)
     * @return ready-to-use {@link EconomyState}
     */
    public static EconomyState seedBaselineState(int baselineYear) {
        EconomyState s = new EconomyState();
        s.setYear(baselineYear);
        s.setQuarter(4);

        switch (baselineYear) {

            // ── FY 2010-11 ─────────────────────────────────────────────────
            case 2010 -> {
                s.setGdpAnnualGrowthRate(8.40);  s.setPrevAnnualGdpGrowth(8.40);
                s.setGdpUsdBillion(1708.0);
                s.setInflationRate(12.00);
                s.setUnemploymentRate(5.80);
                s.setCurrencyUsdInr(45.73);
                s.setInterestRate(6.25);          s.setPrevInterestRate(4.75);
                s.setMoneySupplyM1(13500.0);      s.setMoneySupplyM3(65000.0);
                s.setForeignExchangeReserves(298000.0);
                s.setGovernmentBudget(-4.80);
                s.setManufacturingPMI(57.5);      s.setServicesPMI(56.8);
                s.setBankLoanGrowthYoY(21.0);
                s.setBankingNpaPct(2.40);
                s.setNeutralRepoRate(6.00);        s.setNeutralGdpGrowth(8.50);
                s.setNeutralInfraSpend(3.00);
            }

            // ── FY 2013-14 ─────────────────────────────────────────────────
            case 2013 -> {
                s.setGdpAnnualGrowthRate(6.40);  s.setPrevAnnualGdpGrowth(5.50);
                s.setGdpUsdBillion(1856.0);
                s.setInflationRate(9.50);
                s.setUnemploymentRate(5.40);
                s.setCurrencyUsdInr(60.10);
                s.setInterestRate(7.75);          s.setPrevInterestRate(7.25);
                s.setMoneySupplyM1(20000.0);      s.setMoneySupplyM3(90000.0);
                s.setForeignExchangeReserves(294000.0);
                s.setGovernmentBudget(-4.50);
                s.setManufacturingPMI(51.4);      s.setServicesPMI(48.3);
                s.setBankLoanGrowthYoY(15.5);
                s.setBankingNpaPct(4.00);
                s.setNeutralRepoRate(7.00);        s.setNeutralGdpGrowth(6.50);
                s.setNeutralInfraSpend(3.10);
            }

            // ── FY 2015-16  (used by RealityCheckService) ──────────────────
            case 2015 -> {
                s.setGdpAnnualGrowthRate(7.55);  s.setPrevAnnualGdpGrowth(7.55);
                s.setGdpUsdBillion(2103.59);
                s.setGdpConstantPrices(113647.0);
                s.setInflationRate(4.90);
                s.setUnemploymentRate(5.40);
                s.setCurrencyUsdInr(65.46);
                s.setStockMarketSensex(25341.0);
                s.setInterestRate(6.75);          s.setPrevInterestRate(7.50);
                s.setMoneySupplyM1(28500.0);      s.setMoneySupplyM3(120000.0);
                s.setForeignExchangeReserves(360000.0);
                s.setExportsUsdBillion(66.84);
                s.setImportsUsdBillion(96.42);
                s.setBalanceOfTrade(-29.58);
                s.setCurrentAccountToGdp(-1.10);
                s.setGovernmentBudget(-3.90);
                s.setGovernmentDebtToGdp(68.50);
                s.setManufacturingPMI(51.1);      s.setServicesPMI(53.8);
                s.setBankLoanGrowthYoY(10.9);
                s.setBankingNpaPct(9.50);
                s.setNeutralRepoRate(6.50);        s.setNeutralGdpGrowth(7.00);
                s.setNeutralInfraSpend(3.40);
                s.setInfraSpend(3.40);            s.setPrevInfraSpend(3.40);
                s.setBrentCrudePrice(48.0);
                s.setMonsoonIndex(107.0);
            }

            // ── FY 2018-19 ─────────────────────────────────────────────────
            case 2018 -> {
                s.setGdpAnnualGrowthRate(6.50);  s.setPrevAnnualGdpGrowth(7.20);
                s.setGdpUsdBillion(2700.0);
                s.setInflationRate(3.40);
                s.setUnemploymentRate(6.10);
                s.setCurrencyUsdInr(69.90);
                s.setInterestRate(6.50);          s.setPrevInterestRate(6.25);
                s.setMoneySupplyM1(36000.0);      s.setMoneySupplyM3(145000.0);
                s.setForeignExchangeReserves(398000.0);
                s.setGovernmentBudget(-3.40);
                s.setManufacturingPMI(53.5);      s.setServicesPMI(52.6);
                s.setBankLoanGrowthYoY(13.5);
                s.setBankingNpaPct(9.30);
                s.setNeutralRepoRate(6.00);        s.setNeutralGdpGrowth(7.00);
                s.setNeutralInfraSpend(3.60);
                s.setBrentCrudePrice(74.0);
                s.setMonsoonIndex(91.0);          // deficient monsoon FY19
            }

            // ── FY 2019-20 ─────────────────────────────────────────────────
            case 2019 -> {
                s.setGdpAnnualGrowthRate(4.00);  s.setPrevAnnualGdpGrowth(6.50);
                s.setGdpUsdBillion(2869.0);
                s.setInflationRate(4.80);
                s.setUnemploymentRate(7.60);
                s.setCurrencyUsdInr(70.88);
                s.setInterestRate(5.15);          s.setPrevInterestRate(6.50);
                s.setMoneySupplyM1(38000.0);      s.setMoneySupplyM3(158000.0);
                s.setForeignExchangeReserves(476000.0);
                s.setGovernmentBudget(-3.80);
                s.setManufacturingPMI(51.2);      s.setServicesPMI(50.8);
                s.setBankLoanGrowthYoY(7.10);
                s.setBankingNpaPct(8.20);
                s.setNeutralRepoRate(5.50);        s.setNeutralGdpGrowth(6.00);
                s.setNeutralInfraSpend(3.60);
                s.setCorporateTaxRate(22.0);       // Sep 2019 corporate tax cut
                s.setBrentCrudePrice(62.0);
                s.setMonsoonIndex(110.0);
            }

            // ── Generic fallback for any other year ───────────────────────
            default -> {
                // Use current-state defaults; caller may override specific fields
                s.setYear(baselineYear);
                LOG.warning("No pre-built profile for year %d — using default EconomyState baseline"
                        .formatted(baselineYear));
            }
        }
        return s;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Applies the historically accurate policy lever configuration for
     * {@code baselineYear} directly onto {@code state}.
     *
     * <p>Policy levers set here: repo rate and its delta, infra spend and its
     * delta, corporate tax rate, Brent crude price, monsoon index, M1 shock
     * percentage (demonetisation 2016), and banking NPA ratio.</p>
     */
    static void applyHistoricalPolicyProfile(EconomyState state, int baselineYear) {
        switch (baselineYear) {

            case 2010 -> {
                // RBI aggressive tightening cycle (raised repo 4.75→8.5% over FY11-12)
                state.setInterestRate(7.25);      state.setPrevInterestRate(6.25);
                state.setInfraSpend(3.20);        state.setPrevInfraSpend(3.00);
                state.setCorporateTaxRate(30.0);
                state.setBrentCrudePrice(80.0);
                state.setMonsoonIndex(102.0);
                state.setM1ShockPct(0.0);
                state.setBankingNpaPct(2.40);
            }

            case 2013 -> {
                // RBI hawkish to defend INR (Taper Tantrum); CAD crisis
                state.setInterestRate(8.00);      state.setPrevInterestRate(7.75);
                state.setInfraSpend(3.00);        state.setPrevInfraSpend(3.10);
                state.setCorporateTaxRate(30.0);
                state.setBrentCrudePrice(105.0);  // oil elevated FY14
                state.setMonsoonIndex(106.0);
                state.setM1ShockPct(0.0);
                state.setBankingNpaPct(4.50);
            }

            case 2015 -> {
                // RBI rate cuts begin (6.75→6.25); Demonetisation Q3 FY17
                state.setInterestRate(6.25);      state.setPrevInterestRate(6.75);
                state.setInfraSpend(3.70);        state.setPrevInfraSpend(3.40);
                state.setCorporateTaxRate(30.0);
                state.setBrentCrudePrice(48.0);
                state.setMonsoonIndex(107.0);
                // M1 shock applied in Q3 via advanceOneYear — set to 0 at start
                state.setM1ShockPct(0.0);
                state.setBankingNpaPct(9.50);
                state.setTaxBuoyancyCoeff(1.08);
            }

            case 2018 -> {
                // RBI pivoting back to cuts; NBFC liquidity stress
                state.setInterestRate(6.25);      state.setPrevInterestRate(6.50);
                state.setInfraSpend(3.80);        state.setPrevInfraSpend(3.60);
                state.setCorporateTaxRate(30.0);
                state.setBrentCrudePrice(70.0);
                state.setMonsoonIndex(110.0);
                state.setM1ShockPct(0.0);
                state.setBankingNpaPct(8.50);
            }

            case 2019 -> {
                // Five rate cuts totalling 135 bps; corporate tax cut Sep 2019; COVID shadow
                state.setInterestRate(4.40);      state.setPrevInterestRate(5.15);
                state.setInfraSpend(3.60);        state.setPrevInfraSpend(3.60);
                state.setCorporateTaxRate(22.0);
                state.setBrentCrudePrice(45.0);   // crude crashed Mar 2020
                state.setMonsoonIndex(110.0);
                state.setM1ShockPct(0.0);
                state.setBankingNpaPct(7.50);
            }

            default ->
                // No-op for unrecognised years; caller or seedBaselineState already configured levers
                LOG.fine("No specific policy overrides for year %d — using state as-is".formatted(baselineYear));
        }
    }

    /**
     * Advances {@code state} by exactly 4 quarters, injecting the demonetisation
     * M1 shock in Q3 for baseline year 2015 (FY 2016-17) to replicate the
     * behaviour of {@link RealityCheckService}.
     */
    private static void advanceOneYear(EconomyState state, int baselineYear) {
        // Q1 — normal
        SimulationEngine.advanceQuarter(state);

        // Q2 — normal
        SimulationEngine.advanceQuarter(state);

        // Q3 — apply any mid-year structural shock
        applyMidYearShock(state, baselineYear);
        SimulationEngine.advanceQuarter(state);

        // Q4 — recovery / normalise
        SimulationEngine.advanceQuarter(state);
    }

    /**
     * Injects Q3 shocks that are known to have occurred in specific years.
     * Currently handles the FY 2016-17 demonetisation (Nov 2016, Q3).
     */
    private static void applyMidYearShock(EconomyState state, int baselineYear) {
        if (baselineYear == 2015) {
            // Demonetisation of ₹500/₹1000 notes — November 8, 2016 (Q3 FY17)
            // RBI estimated ~19% contraction in M1 in the weeks following the announcement.
            state.setM1ShockPct(-19.0);
            LOG.info("Applied demonetisation M1 shock (-19%%) for Q3 FY2016-17");
        }
        // Add future mid-year shocks here (e.g., COVID lockdown Q4 FY2019-20)
    }
}
