package com.india.econsim.service;

import com.google.gson.JsonObject;
import com.india.econsim.model.EconomyState;
import com.india.econsim.service.BacktestResult.IndicatorResult;
import com.india.econsim.service.HistoricalDataFetcher.HistoricalDataException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class DynamicBacktestService {

    private static final Logger LOG = Logger.getLogger(DynamicBacktestService.class.getName());

    /** Default data file — used by the single-arg convenience overload. */
    public static final Path DEFAULT_DATA_FILE =
            Path.of("src/main/resources/india_historical_economy.json");

    private static final double USD_TO_BILLION = 1e-9;

    private DynamicBacktestService() {}

    /**
     * Convenience overload — uses the default cached data file.
     */
    public static BacktestResult runDynamicBacktest(int baselineYear)
            throws HistoricalDataException {
        return runDynamicBacktest(baselineYear, DEFAULT_DATA_FILE);
    }

    public static BacktestResult runDynamicBacktest(int baselineYear, Path dataFile) throws HistoricalDataException {
        if (baselineYear < 2006 || baselineYear > 2023) {
            throw new IllegalArgumentException("baselineYear must be 2006–2023 (got " + baselineYear + ")");
        }

        int targetYear = baselineYear + 1;
        LOG.info("DynamicBacktest: seeding from WB data for %d, forecasting %d".formatted(baselineYear, targetYear));
        JsonObject root = HistoricalDataFetcher.loadFromFile(dataFile);
        EconomyState state = seedFromWorldBank(root, baselineYear);

        for (int q = 0; q < 4; q++) {
            SimulationEngine.advanceQuarter(state);
        }

        double actualGdp     = get(root, "gdpGrowthPct",        targetYear);
        double actualCpi     = get(root, "cpiInflationPct",      targetYear);
        double actualUnemp   = get(root, "unemploymentPct",      targetYear);
        double actualFx      = get(root, "fxUsdLcu",             targetYear);
        double actualExp     = get(root, "exportsUsdBillion",  targetYear) * USD_TO_BILLION;
        double actualCa      = get(root, "currentAccountPctGdp", targetYear);
        double actualDebt    = get(root, "govtDebtPctGdp",       targetYear);
        double actualLending = get(root, "lendingRatePct",       targetYear);

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

        rows.removeIf(r -> Double.isNaN(r.actualValue));

        if (rows.isEmpty()) {
            throw new HistoricalDataException("No World Bank data found for target year " + targetYear);
        }

        double meanError    = rows.stream().mapToDouble(r -> r.absoluteErrorPct).average().orElse(0.0);
        double meanAccuracy = Math.round((100.0 - meanError) * 100.0) / 100.0;
        long   passCount    = rows.stream().filter(r -> "PASS".equals(r.verdict)).count();
        long   warnCount    = rows.stream().filter(r -> "WARN".equals(r.verdict)).count();
        long   failCount    = rows.stream().filter(r -> "FAIL".equals(r.verdict)).count();

        String summary = ("Dynamic Backtest %d→%d | Seeded from World Bank API | " +
                "Mean Accuracy: %.1f%% | ✅ PASS: %d | ⚠️ WARN: %d | ❌ FAIL: %d")
                .formatted(baselineYear, targetYear, meanAccuracy, passCount, warnCount, failCount);

        LOG.info(summary);
        return new BacktestResult(baselineYear, targetYear, rows, meanAccuracy, passCount, warnCount, failCount, summary);
    }

    public static EconomyState seedFromWorldBank(JsonObject root, int year) {
        EconomyState s = new EconomyState();
        s.setYear(year);
        s.setQuarter(4);

        // Core WB Overrides
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
            // fxUsdLcu is fetched properly below; use 67.0 as fallback if WB has no FX data for this year
            double fxForGdp = get(root, "fxUsdLcu", year);
            double fxRate = Double.isNaN(fxForGdp) ? 67.0 : fxForGdp;
            s.setGdpConstantPrices(round2(gdpBn * fxRate / 100.0));
        }

        double gdpPc = get(root, "gdpPerCapitaUsd", year);
        if (!Double.isNaN(gdpPc)) s.setGdpPerCapita(round2(gdpPc));

        double cpi = get(root, "cpiInflationPct", year);
        if (!Double.isNaN(cpi)) s.setInflationRate(round2(cpi));

        double unemp = get(root, "unemploymentPct", year);
        if (!Double.isNaN(unemp)) {
            s.setUnemploymentRate(round2(unemp));
            s.setEmploymentRate(round2(100.0 - unemp));
        }

        double fx = get(root, "fxUsdLcu", year);
        if (!Double.isNaN(fx)) s.setCurrencyUsdInr(round2(fx));

        double expRaw = get(root, "exportsUsdBillion", year);
        if (!Double.isNaN(expRaw)) s.setExportsUsdBillion(round2(expRaw * USD_TO_BILLION / 4.0));

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
            if (!Double.isNaN(gdpRaw)) s.setCurrentAccount(round2(ca / 100.0 * gdpRaw * USD_TO_BILLION * 1000.0));
        }

        double fdi = get(root, "fdiNetPctGdp", year);
        if (!Double.isNaN(fdi) && !Double.isNaN(gdpRaw)) {
            s.setForeignDirectInvestment(round2(fdi / 100.0 * gdpRaw * USD_TO_BILLION / 4.0));
        }

        double debt = get(root, "govtDebtPctGdp", year);
        if (!Double.isNaN(debt)) s.setGovernmentDebtToGdp(round2(debt));

        double bm = get(root, "broadMoneyPctGdp", year);
        if (!Double.isNaN(bm) && !Double.isNaN(gdpRaw)) {
            double fxRate = Double.isNaN(fx) ? 67.0 : fx;
            s.setMoneySupplyM3(round2(bm / 100.0 * gdpRaw * USD_TO_BILLION * fxRate / 100.0));
            s.setMoneySupplyM1(round2(s.getMoneySupplyM3() * 0.28));
        }

        double lending = get(root, "lendingRatePct", year);
        if (!Double.isNaN(lending)) {
            s.setBankLendingRate(round2(lending));
            double crrSpread = (s.getCashReserveRatio() - 3.0) * 0.12;
            double impliedRepo = round2(Math.max(3.5, lending - 3.46 - crrSpread));
            s.setInterestRate(impliedRepo);
            s.setPrevInterestRate(impliedRepo);
            s.setNeutralRepoRate(impliedRepo);
        }

        double lfpr = get(root, "laborForcePctPop", year);
        if (!Double.isNaN(lfpr)) s.setLaborForceParticipation(round2(lfpr));

        // Era Default Overrides (replaces 2026 hardcodes)
        s.setStockMarketSensex(round2(10000.0 + Math.max(0, year - 2005) * 2500.0)); // Era-appropriate Sensex
        s.setGovernmentBudget(round2(-4.50 + Math.max(0, year - 2010) * 0.1));       // Era-appropriate deficit

        double growth = Double.isNaN(gdpGrowth) ? 6.5 : gdpGrowth;
        s.setNeutralGdpGrowth(round2(growth));

        double pmiBase = 48.0 + Math.min(6.0, Math.max(0.0, growth));
        s.setManufacturingPMI(round2(pmiBase + 0.5));
        s.setServicesPMI(round2(pmiBase + 1.0));
        s.setCompositePMI(round2(pmiBase + 1.5));
        s.setBusinessConfidence(round2(100.0 + (growth - 5.0) * 3.0));
        s.setConsumerConfidence(round2(85.0 + (growth - 5.0) * 2.0));

        double cpiRate = Double.isNaN(cpi) ? 5.0 : cpi;
        s.setCpiPoints(round2(100.0 + (year - 2012) * cpiRate * 0.8));

        s.setMonsoonIndex(100.0);
        s.setBrentCrudePrice(eraCrude(year));
        s.setBankingNpaPct(eraNpa(year));
        s.setTaxBuoyancyCoeff(1.05);
        s.setM1ShockPct(0.0);
        s.setInfraSpend(eraInfra(year));
        s.setPrevInfraSpend(eraInfra(year - 1));
        s.setNeutralInfraSpend(eraInfra(year));

        return s;
    }

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

    private static double eraNpa(int year) {
        if (year <= 2012) return 3.0;
        if (year <= 2015) return 5.5;
        if (year <= 2018) return 10.0;
        if (year <= 2020) return 8.0;
        return 4.5;
    }

    private static double eraInfra(int year) {
        if (year <= 2010) return 2.8;
        if (year <= 2014) return 3.2;
        if (year <= 2018) return 3.6;
        if (year <= 2021) return 3.8;
        return 4.1;
    }

    private static void addRow(List<IndicatorResult> rows, String name, double actual, double simulated, String unit, String note) {
        if (!Double.isNaN(actual)) rows.add(new IndicatorResult(name, actual, simulated, unit, note));
    }

    private static double get(JsonObject root, String key, int year) {
        return HistoricalDataFetcher.getValueForYear(root, key, year);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}