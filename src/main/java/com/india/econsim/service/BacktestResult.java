package com.india.econsim.service;

import java.util.List;

/**
 * Structured result of a single Time Machine backtest run.
 *
 * <p>Holds per-indicator accuracy rows and aggregate statistics for the
 * comparison between the simulator's forward projection and the actual
 * historical data read from {@code india_historical_economy.json}.</p>
 *
 * @param rows            One row per compared indicator.
 * @param baselineYear    Baseline year used as the simulation start point (e.g., 2015).
 * @param targetYear      The target year being forecasted and validated (baselineYear + 1).
 * @param meanAccuracyPct Mean accuracy across all indicators: 100 - meanAbsoluteErrorPct.
 * @param passCount       Number of indicators with error ≤ 5%.
 * @param warnCount       Number of indicators with error > 5% and ≤ 15%.
 * @param failCount       Number of indicators with error > 15%.
 * @param summary         Human-readable one-line summary of the backtest outcome.
 */
@SuppressWarnings("ALL")
public record BacktestResult(int baselineYear, int targetYear, List<IndicatorResult> rows, double meanAccuracyPct,
                             long passCount, long warnCount, long failCount, String summary) {

    public BacktestResult(
            int baselineYear,
            int targetYear,
            List<IndicatorResult> rows,
            double meanAccuracyPct,
            long passCount,
            long warnCount,
            long failCount,
            String summary) {
        this.baselineYear = baselineYear;
        this.targetYear = targetYear;
        this.rows = List.copyOf(rows);
        this.meanAccuracyPct = meanAccuracyPct;
        this.passCount = passCount;
        this.warnCount = warnCount;
        this.failCount = failCount;
        this.summary = summary;
    }

    // -------------------------------------------------------------------------

    /**
     * @return
     */
    @Override
    public String toString() {
        return summary;
    }

    /**
     * Accuracy result for a single economic indicator.
     */
    public static final class IndicatorResult {

        /**
         * Human-readable indicator name, e.g. "GDP Annual Growth Rate (%)".
         */
        public final String indicatorName;

        /**
         * Actual historical value sourced from World Bank / CSO / RBI.
         */
        public final double actualValue;

        /**
         * Value produced by the simulation engine for the same period.
         */
        public final double simulatedValue;

        /**
         * Unit label, e.g. "%", "USD bn", "INR".
         */
        public final String unit;

        /**
         * Absolute percentage error: {@code |simulated - actual| / |actual| × 100}.
         * Zero when actual is effectively zero (< 0.001) to avoid division by zero.
         */
        public final double absoluteErrorPct;

        /**
         * PASS (≤ 5%), WARN (≤ 15%), or FAIL (> 15%).
         */
        public final String verdict;

        /**
         * Brief causal note explaining the expected direction of the metric.
         */
        public final String note;

        public IndicatorResult(
                String indicatorName,
                double actualValue,
                double simulatedValue,
                String unit,
                String note) {
            this.indicatorName = indicatorName;
            this.actualValue = actualValue;
            this.simulatedValue = simulatedValue;
            this.unit = unit;
            this.note = note;

            // Compute error — guard against near-zero actuals
            if (Math.abs(actualValue) < 0.001) {
                this.absoluteErrorPct = 0.0;
            } else {
                this.absoluteErrorPct = Math.round(
                        Math.abs(simulatedValue - actualValue) / Math.abs(actualValue) * 10000.0
                ) / 100.0;
            }

            this.verdict = absoluteErrorPct <= 5.0 ? "PASS"
                    : absoluteErrorPct <= 15.0 ? "WARN"
                    : "FAIL";
        }

        @Override
        public String toString() {
            return String.format("%-40s | Actual: %8.2f %s | Sim: %8.2f %s | Error: %5.1f%% | %s",
                    indicatorName, actualValue, unit, simulatedValue, unit, absoluteErrorPct, verdict);
        }
    }
}
