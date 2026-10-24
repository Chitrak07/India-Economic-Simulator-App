package com.india.econsim.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Fetches 20 years (2006–2026) of India macroeconomic data from the
 * World Bank Open Data API v2 and persists the result as a structured
 * JSON file named {@code india_historical_economy.json}.
 *
 * <h2>Indicators fetched</h2>
 * <ul>
 *   <li>Real GDP Growth – {@code NY.GDP.MKTP.KD.ZG}</li>
 *   <li>CPI Inflation   – {@code FP.CPI.TOTL.ZG}</li>
 *   <li>Unemployment    – {@code SL.UEM.TOTL.ZS}</li>
 * </ul>
 *
 * <h2>Output schema  (india_historical_economy.json)</h2>
 * <pre>{@code
 * {
 *   "metadata": { "country": "IND", "fetchedAt": "...", "yearRange": "2006-2026" },
 *   "indicators": {
 *     "gdpGrowthPct": {
 *       "worldBankCode": "NY.GDP.MKTP.KD.ZG",
 *       "description": "Real GDP Growth (%)",
 *       "series": [ { "year": 2006, "value": 9.26 }, ... ]
 *     },
 *     "cpiInflationPct": { ... },
 *     "unemploymentPct": { ... }
 *   }
 * }
 * }</pre>
 *
 * <h2>Required Maven dependency</h2>
 * <pre>{@code
 * <dependency>
 *     <groupId>com.google.code.gson</groupId>
 *     <artifactId>gson</artifactId>
 *     <version>2.11.0</version>
 * </dependency>
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Path out = Path.of("src/main/resources/india_historical_economy.json");
 * HistoricalDataFetcher.fetchAndSave(out);
 * }</pre>
 */
public final class HistoricalDataFetcher {

    private static final Logger LOG = Logger.getLogger(HistoricalDataFetcher.class.getName());

    // ── World Bank API configuration ─────────────────────────────────────────

    private static final String WB_BASE   = "https://api.worldbank.org/v2";
    private static final String COUNTRY   = "IND";
    private static final int    YEAR_FROM = 2006;
    private static final int    YEAR_TO   = 2026;
    /** Per-page limit — 100 rows safely covers the 20-year window in one request. */
    private static final int    PAGE_SIZE = 100;

    // ── HTTP client settings ─────────────────────────────────────────────────

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT  = Duration.ofSeconds(30);
    private static final int      MAX_RETRIES      = 3;
    private static final long     RETRY_BACKOFF_MS = 2_000L;

    // ── Indicator catalogue ──────────────────────────────────────────────────

    /**
     * Maps output JSON key → World Bank indicator code + description.
     * Insertion order is preserved (LinkedHashMap) so the output file is stable.
     */
    private static final Map<String, IndicatorSpec> INDICATORS = new LinkedHashMap<>();

    static {
        // Core macro
        INDICATORS.put("gdpGrowthPct",        new IndicatorSpec("NY.GDP.MKTP.KD.ZG", "Real GDP Growth (%)"));
        INDICATORS.put("cpiInflationPct",      new IndicatorSpec("FP.CPI.TOTL.ZG",    "CPI Inflation Rate (%)"));
        INDICATORS.put("unemploymentPct",      new IndicatorSpec("SL.UEM.TOTL.ZS",    "Unemployment Rate (%)"));
        // GDP level & per-capita
        INDICATORS.put("gdpUsdBillion",        new IndicatorSpec("NY.GDP.MKTP.CD",     "GDP (current USD)"));
        INDICATORS.put("gdpPerCapitaUsd",      new IndicatorSpec("NY.GDP.PCAP.CD",     "GDP per Capita (current USD)"));
        // External sector
        INDICATORS.put("fxUsdLcu",             new IndicatorSpec("PA.NUS.FCRF",        "Official Exchange Rate (LCU per USD)"));
        INDICATORS.put("exportsUsdBillion",    new IndicatorSpec("NE.EXP.GNFS.CD",     "Exports of Goods & Services (current USD)"));
        INDICATORS.put("importsUsdBillion",    new IndicatorSpec("NE.IMP.GNFS.CD",     "Imports of Goods & Services (current USD)"));
        INDICATORS.put("currentAccountPctGdp", new IndicatorSpec("BN.CAB.XOKA.GD.ZS", "Current Account Balance (% of GDP)"));
        INDICATORS.put("fdiNetPctGdp",         new IndicatorSpec("BX.KLT.DINV.WD.GD.ZS","FDI Net Inflows (% of GDP)"));
        // Government
        INDICATORS.put("govtDebtPctGdp",       new IndicatorSpec("GC.DOD.TOTL.GD.ZS", "Government Debt (% of GDP)"));
        // Money & credit
        INDICATORS.put("broadMoneyPctGdp",     new IndicatorSpec("FM.LBL.BMNY.GD.ZS", "Broad Money M3 (% of GDP)"));
        INDICATORS.put("lendingRatePct",       new IndicatorSpec("FR.INR.LEND",        "Lending Interest Rate (%)"));
        // Labour
        INDICATORS.put("laborForcePctPop",     new IndicatorSpec("SL.TLF.ACTI.ZS",    "Labor Force Participation Rate (%)"));
    }

    // ── Private constructor (utility class) ──────────────────────────────────

    private HistoricalDataFetcher() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Fetches all configured indicators from the World Bank API and writes
     * the structured result to {@code outputPath}.
     *
     * <p>Parent directories are created automatically if absent.
     * Any existing file at {@code outputPath} is overwritten.</p>
     *
     * @param outputPath destination file path for the JSON output
     * @throws HistoricalDataException on unrecoverable network, HTTP, or parse errors
     */
    public static void fetchAndSave(Path outputPath) throws HistoricalDataException {
        LOG.info("Starting World Bank historical data fetch — India %d–%d".formatted(YEAR_FROM, YEAR_TO));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        Map<String, List<YearValue>> indicatorData = new LinkedHashMap<>();

        for (Map.Entry<String, IndicatorSpec> entry : INDICATORS.entrySet()) {
            String        key  = entry.getKey();
            IndicatorSpec spec = entry.getValue();
            LOG.info("  Fetching: %s  [%s]".formatted(spec.description(), spec.worldBankCode()));
            List<YearValue> series = fetchWithRetry(client, spec.worldBankCode());
            indicatorData.put(key, series);
            LOG.info("  → %d data points received".formatted(series.size()));
        }

        JsonObject root = buildOutputJson(indicatorData);
        writeJsonFile(root, outputPath);
        LOG.info("Saved to: %s".formatted(outputPath.toAbsolutePath()));
    }

    /**
     * Loads a previously saved {@code india_historical_economy.json} and
     * returns the parsed root {@link JsonObject}.
     *
     * @param filePath path to the JSON file
     * @return parsed root object
     * @throws HistoricalDataException if the file is missing or malformed
     */
    public static JsonObject loadFromFile(Path filePath) throws HistoricalDataException {
        if (!Files.exists(filePath)) {
            throw new HistoricalDataException(
                "Historical data file not found: " + filePath.toAbsolutePath() +
                ". Call HistoricalDataFetcher.fetchAndSave() first.");
        }
        try (InputStream in    = Files.newInputStream(filePath);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new HistoricalDataException("Failed to read file: " + filePath, e);
        } catch (Exception e) {
            throw new HistoricalDataException("Malformed JSON in file: " + filePath, e);
        }
    }

    /**
     * Reads a single indicator value for the given year from a loaded root object.
     *
     * @param root         parsed root from {@link #loadFromFile(Path)}
     * @param indicatorKey one of {@code gdpGrowthPct}, {@code cpiInflationPct},
     *                     {@code unemploymentPct}
     * @param year         calendar year (e.g. 2016)
     * @return the historical value, or {@code Double.NaN} if the year is absent
     *         or the World Bank reported no observation for that year
     */
    public static double getValueForYear(JsonObject root, String indicatorKey, int year) {
        try {
            JsonArray series = root
                    .getAsJsonObject("indicators")
                    .getAsJsonObject(indicatorKey)
                    .getAsJsonArray("series");

            for (JsonElement el : series) {
                JsonObject point = el.getAsJsonObject();
                if (point.get("year").getAsInt() == year) {
                    JsonElement val = point.get("value");
                    if (val == null || val.isJsonNull()) return Double.NaN;
                    return val.getAsDouble();
                }
            }
        } catch (Exception e) {
            LOG.warning("Could not read %s for year %d: %s".formatted(indicatorKey, year, e.getMessage()));
        }
        return Double.NaN;
    }

    // =========================================================================
    // Internal helpers — fetch & retry
    // =========================================================================

    /** Wraps {@link #fetchOnce} with exponential back-off retry. */
    private static List<YearValue> fetchWithRetry(
            HttpClient client, String indicatorCode) throws HistoricalDataException {

        HistoricalDataException lastEx = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return fetchOnce(client, indicatorCode);
            } catch (HistoricalDataException e) {
                lastEx = e;
                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_BACKOFF_MS * attempt;
                    LOG.warning("Attempt %d/%d failed for %s — retrying in %dms: %s"
                            .formatted(attempt, MAX_RETRIES, indicatorCode, delay, e.getMessage()));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new HistoricalDataException("Interrupted during retry backoff", ie);
                    }
                }
            }
        }
        throw new HistoricalDataException(
            "All %d attempts failed for indicator %s".formatted(MAX_RETRIES, indicatorCode), lastEx);
    }

    /** Performs a single HTTP GET and parses the World Bank v2 JSON envelope. */
    private static List<YearValue> fetchOnce(
            HttpClient client, String indicatorCode) throws HistoricalDataException {

        String url = buildUrl(indicatorCode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new HistoricalDataException(
                "Network error fetching " + indicatorCode + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HistoricalDataException("Request interrupted for " + indicatorCode, e);
        }

        int status = response.statusCode();
        if (status == 429) {
            throw new HistoricalDataException(
                "Rate-limited by World Bank API (HTTP 429) — back off and retry.");
        }
        if (status >= 500) {
            throw new HistoricalDataException(
                "World Bank server error HTTP %d for %s".formatted(status, indicatorCode));
        }
        if (status != 200) {
            throw new HistoricalDataException(
                "Unexpected HTTP %d for %s — URL: %s".formatted(status, indicatorCode, url));
        }

        return parseWorldBankResponse(response.body(), indicatorCode);
    }

    // =========================================================================
    // Internal helpers — parsing
    // =========================================================================

    /**
     * Parses the World Bank API v2 JSON response.
     *
     * <p>WB returns a 2-element array: {@code [ {metadata}, [{dataPoint}, ...] ]}.
     * Each data point has a {@code "date"} year string and a {@code "value"}
     * (Double or null). Null observations (no data yet for that year) are skipped.</p>
     */
    private static List<YearValue> parseWorldBankResponse(
            String json, String indicatorCode) throws HistoricalDataException {

        if (json == null || json.isBlank()) {
            throw new HistoricalDataException("Empty response body for " + indicatorCode);
        }

        JsonArray root;
        try {
            root = JsonParser.parseString(json).getAsJsonArray();
        } catch (Exception e) {
            throw new HistoricalDataException(
                "Malformed JSON root for " + indicatorCode + ": " + e.getMessage(), e);
        }

        // WB API error responses look like [ { "message": [...] } ]
        if (root.size() < 2) {
            String apiError = extractWbErrorMessage(root);
            throw new HistoricalDataException(
                "World Bank API error for " + indicatorCode + ": " +
                (apiError != null ? apiError : "unexpected response format (size=" + root.size() + ")"));
        }

        JsonArray dataArray;
        try {
            dataArray = root.get(1).getAsJsonArray();
        } catch (Exception e) {
            throw new HistoricalDataException(
                "Cannot extract data array from WB response for " + indicatorCode, e);
        }

        List<YearValue> results = new ArrayList<>();
        for (JsonElement el : dataArray) {
            try {
                JsonObject  point = el.getAsJsonObject();
                JsonElement valEl = point.get("value");
                if (valEl == null || valEl.isJsonNull()) continue;   // no observation — skip

                int    year  = Integer.parseInt(point.get("date").getAsString().trim());
                double value = valEl.getAsDouble();

                if (year >= YEAR_FROM && year <= YEAR_TO) {
                    results.add(new YearValue(year, value));
                }
            } catch (NumberFormatException | IllegalStateException e) {
                LOG.warning("Skipping malformed data point in %s response: %s"
                        .formatted(indicatorCode, el));
            }
        }

        // WB returns newest-first — sort chronologically for readability
        results.sort(Comparator.comparingInt(YearValue::year));
        return results;
    }

    /** Attempts to extract the human-readable error message embedded in WB error responses. */
    private static String extractWbErrorMessage(JsonArray root) {
        try {
            return root.get(0).getAsJsonObject()
                       .getAsJsonArray("message").get(0)
                       .getAsJsonObject().get("value").getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    // =========================================================================
    // Internal helpers — output construction
    // =========================================================================

    /**
     * Builds the full output {@link JsonObject} from the fetched series map.
     */
    private static JsonObject buildOutputJson(Map<String, List<YearValue>> indicatorData) {
        JsonObject root = new JsonObject();

        // -- metadata block --
        JsonObject metadata = new JsonObject();
        metadata.addProperty("country",     COUNTRY);
        metadata.addProperty("countryName", "India");
        metadata.addProperty("yearRange",   YEAR_FROM + "-" + YEAR_TO);
        metadata.addProperty("source",      "World Bank Open Data API v2");
        metadata.addProperty("fetchedAt",   java.time.Instant.now().toString());
        root.add("metadata", metadata);

        // -- indicators block --
        JsonObject indicators = new JsonObject();
        for (Map.Entry<String, List<YearValue>> entry : indicatorData.entrySet()) {
            IndicatorSpec spec = INDICATORS.get(entry.getKey());

            JsonObject block = new JsonObject();
            block.addProperty("worldBankCode", spec.worldBankCode());
            block.addProperty("description",   spec.description());

            JsonArray series = new JsonArray();
            for (YearValue yv : entry.getValue()) {
                JsonObject point = new JsonObject();
                point.addProperty("year",  yv.year());
                // Round to 4 decimal places — sufficient for % rates
                point.addProperty("value", Math.round(yv.value() * 10_000.0) / 10_000.0);
                series.add(point);
            }
            block.add("series", series);
            indicators.add(entry.getKey(), block);
        }
        root.add("indicators", indicators);

        return root;
    }

    /**
     * Pretty-prints a {@link JsonObject} and writes it to disk,
     * creating parent directories if they do not exist.
     */
    private static void writeJsonFile(JsonObject root, Path outputPath) throws HistoricalDataException {
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(outputPath, prettyGson.toJson(root),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new HistoricalDataException(
                "Failed to write output file: " + outputPath.toAbsolutePath(), e);
        }
    }

    /** Constructs the World Bank API URL for a single indicator, e.g.:
     *  {@code https://api.worldbank.org/v2/country/IND/indicator/NY.GDP.MKTP.KD.ZG?format=json&date=2006:2026&per_page=100}
     */
    private static String buildUrl(String indicatorCode) {
        return "%s/country/%s/indicator/%s?format=json&date=%d:%d&per_page=%d"
                .formatted(WB_BASE, COUNTRY, indicatorCode, YEAR_FROM, YEAR_TO, PAGE_SIZE);
    }

    // =========================================================================
    // Value types
    // =========================================================================

    /** A single (year → value) observation in a time series. */
    private record YearValue(int year, double value) {}

    /** Metadata for each World Bank indicator. */
    private record IndicatorSpec(String worldBankCode, String description) {}

    // =========================================================================
    // Checked exception
    // =========================================================================

    /**
     * Thrown when a fetch or file I/O operation fails unrecoverably.
     * Always wraps the root cause for full stack trace visibility in logs.
     */
    public static final class HistoricalDataException extends Exception {
        public HistoricalDataException(String message) {
            super(message);
        }
        public HistoricalDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
