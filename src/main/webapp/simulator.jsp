<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.india.econsim.model.EconomyState" %>
<%@ page import="com.india.econsim.service.BacktestResult" %>
<%@ page import="com.india.econsim.service.RealityCheckService" %>
<%@ page import="java.util.List" %>
<%
    EconomyState econ = (EconomyState) session.getAttribute("econ");
    if (econ == null) {
        econ = new EconomyState();
        session.setAttribute("econ", econ);
    }
    String activeTab = (String) request.getAttribute("activeTab");
    if (activeTab == null) activeTab = "Overview";
%>
<%!
    public String fmtDelta(EconomyState e, String key, boolean invertColor) {
        double d = e.getDelta(key);
        if (Math.abs(d) < 0.001) return "<span style='color:#64748b;'>0.00</span>";
        boolean isGood = invertColor ? (d < 0) : (d > 0);
        String color = isGood ? "#22c55e" : "#ef4444";
        String arrow = d > 0 ? "&#9650; +" : "&#9660; ";
        return "<span style='color:" + color + ";font-weight:700;'>" + arrow + String.format("%.2f", d) + "</span>";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>India Economy Simulator — Interactive Pros/Cons & Causal Attribution</title>
    <style>
        :root {
            --bg: #0b1120; --panel: #1e293b; --border: #334155;
            --text: #f8fafc; --muted: #94a3b8;
            --accent: #f97316; --blue: #38bdf8; --green: #22c55e; --red: #ef4444; --purple: #c084fc;
        }
        * { box-sizing: border-box; font-family: 'Segoe UI', system-ui, sans-serif; }
        body { margin: 0; background: var(--bg); color: var(--text); padding: 18px; }
        .top-bar {
            display: flex; justify-content: space-between; align-items: center;
            border-bottom: 1px solid var(--border); padding-bottom: 12px; margin-bottom: 12px;
            flex-wrap: wrap; gap: 8px;
        }
        .top-bar h1 { margin: 0; font-size: 1.32rem; color: var(--accent); }
        .top-bar-right { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
        .status-pill {
            background: var(--panel); border: 1px solid var(--blue);
            padding: 6px 14px; border-radius: 6px; font-weight: 700; color: var(--blue); font-size: 0.88rem;
        }
        /* KPI strip below top bar */
        .kpi-strip {
            display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 14px;
        }
        .kpi-card {
            background: var(--panel); border: 1px solid var(--border); border-radius: 8px;
            padding: 10px 16px; flex: 1; min-width: 130px; text-align: center;
        }
        .kpi-card .kpi-label { font-size: 0.72rem; color: var(--muted); text-transform: uppercase; margin-bottom: 3px; }
        .kpi-card .kpi-val   { font-size: 1.22rem; font-weight: 800; color: #fff; }
        .kpi-card .kpi-delta { font-size: 0.75rem; margin-top: 2px; }

        .summary-banner {
            background: rgba(56, 189, 248, 0.08); border-left: 4px solid var(--blue);
            padding: 10px 14px; border-radius: 6px; margin-bottom: 14px; font-size: 0.85rem; line-height: 1.45;
        }

        /* MAIN LAYOUT: indicators (1fr) LEFT, levers panel (340px) RIGHT */
        .main-grid { display: grid; grid-template-columns: 1fr 340px; gap: 18px; align-items: start; }
        .card { background: var(--panel); border: 1px solid var(--border); border-radius: 8px; padding: 14px; }
        .card h3 {
            margin: 0 0 10px 0; font-size: 0.82rem; text-transform: uppercase;
            color: var(--blue); border-bottom: 1px solid var(--border); padding-bottom: 6px;
        }

        /* Sticky levers panel */
        .levers-panel {
            position: sticky; top: 18px; max-height: calc(100vh - 36px);
            overflow-y: auto; scrollbar-width: thin; scrollbar-color: var(--border) transparent;
        }
        .levers-panel::-webkit-scrollbar { width: 5px; }
        .levers-panel::-webkit-scrollbar-thumb { background: var(--border); border-radius: 99px; }

        /* Collapsible lever sections */
        .lever-section { margin-bottom: 4px; border: 1px solid var(--border); border-radius: 6px; overflow: hidden; }
        .lever-section-header {
            display: flex; justify-content: space-between; align-items: center;
            padding: 8px 12px; background: rgba(56,189,248,0.06); cursor: pointer;
            font-size: 0.78rem; font-weight: 700; text-transform: uppercase; color: var(--blue);
            user-select: none; border: none; width: 100%; text-align: left;
        }
        .lever-section-header:hover { background: rgba(56,189,248,0.12); }
        .lever-section-header .chevron { transition: transform 0.2s; font-style: normal; }
        .lever-section-header.collapsed .chevron { transform: rotate(-90deg); }
        .lever-section-body { padding: 10px 12px 6px; }
        .lever-section-body.hidden { display: none; }

        .slider-row { margin-bottom: 9px; }
        .slider-row label { display: flex; justify-content: space-between; font-size: 0.79rem; margin-bottom: 2px; color: var(--text); }
        .slider-row label span { color: var(--muted); }
        .slider-row label strong { color: var(--accent); min-width: 44px; text-align: right; }
        .slider-row input[type=range] { width: 100%; accent-color: var(--accent); cursor: pointer; height: 4px; }

        /* Action buttons */
        .action-bar {
            display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin: 12px 0 4px;
        }
        .action-bar button {
            padding: 9px 6px; border: none; border-radius: 5px;
            font-weight: 700; cursor: pointer; color: #fff; font-size: 0.8rem; letter-spacing: 0.01em;
        }
        .b-qtr { background: var(--accent); }
        .b-yr  { background: #2563eb; }
        .b-rst { background: #475569; }
        .b-cwr { background: #0d9488; }
        .btn-group { display: flex; gap: 8px; margin-top: 12px; }
        .btn-group button {
            flex: 1; padding: 9px; border: none; border-radius: 5px;
            font-weight: 700; cursor: pointer; color: #fff; font-size: 0.82rem;
        }

        /* Trading Economics Tab Bar */
        .te-tabs {
            display: flex; flex-wrap: wrap; background: #1e293b; border-radius: 6px 6px 0 0;
            overflow: hidden; border: 1px solid var(--border); gap: 2px; padding: 4px 4px 0;
        }
        .te-tab-btn {
            background: #0f172a; color: var(--muted); border: 1px solid transparent;
            border-radius: 5px 5px 0 0; padding: 8px 13px; font-size: 0.82rem; font-weight: 600; cursor: pointer;
            transition: color 0.15s, background 0.15s;
        }
        .te-tab-btn:hover { color: var(--text); background: #334155; }
        .te-tab-btn.active {
            background: var(--panel); color: var(--text); font-weight: 700;
            border-color: var(--border); border-bottom-color: var(--panel);
            box-shadow: 0 -2px 0 var(--accent);
        }
        .tab-pane { display: none; background: var(--panel); border: 1px solid var(--border); border-top: none; border-radius: 0 0 8px 8px; padding: 14px; }
        .tab-pane.active { display: block; }

        .te-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
        .te-table th {
            text-align: left; padding: 9px; background: #0f172a; color: var(--muted);
            font-size: 0.73rem; text-transform: uppercase; border-bottom: 1px solid var(--border);
            position: sticky; top: 0; z-index: 1;
        }
        .te-table td { padding: 9px; border-bottom: 1px solid var(--border); vertical-align: middle; }
        .te-table tbody tr { cursor: pointer; transition: background 0.12s; }
        .te-table tbody tr:hover { background: rgba(249,115,22,0.07); }
        .val-col { font-weight: 700; color: #fff; font-variant-numeric: tabular-nums; }
        .why-btn {
            background: #334155; color: var(--blue); border: 1px solid var(--blue);
            border-radius: 4px; padding: 2px 8px; font-size: 0.73rem; cursor: pointer; font-weight: 600;
        }
        .badge { font-size: 0.68rem; padding: 2px 6px; border-radius: 4px; font-weight: 700; display: inline-block; }
        .b-lead { background: rgba(56,189,248,0.15); color: var(--blue); border: 1px solid var(--blue); }
        .b-coin { background: rgba(34,197,94,0.15); color: var(--green); border: 1px solid var(--green); }
        .b-lag  { background: rgba(192,132,252,0.15); color: var(--purple); border: 1px solid var(--purple); }

        /* Time machine section in levers panel */
        .tm-section {
            margin-top: 10px; border-top: 1px solid var(--border); padding-top: 10px;
        }
        .tm-section h4 {
            margin: 0 0 6px; font-size: 0.78rem; text-transform: uppercase; color: #a78bfa; letter-spacing: 0.04em;
        }

        /* LIVE PROS & CONS POPUP TOAST */
        #policyPopup {
            display: none; position: fixed; bottom: 22px; right: 22px; width: 430px;
            background: #0f172a; border: 2px solid var(--accent); border-radius: 10px;
            box-shadow: 0 12px 32px rgba(0,0,0,0.75); padding: 16px; z-index: 9999;
        }
        #policyPopup h4 { margin: 0 0 8px 0; color: var(--accent); font-size: 1rem; display: flex; justify-content: space-between; }
        .close-x { cursor: pointer; color: var(--muted); font-size: 1.1rem; }
        .pc-section { margin-top: 8px; font-size: 0.82rem; line-height: 1.45; }
        .pc-pro { color: var(--green); font-weight: 700; }
        .pc-con { color: var(--red); font-weight: 700; }
        .pc-ind { color: var(--blue); font-weight: 700; }

        /* INDICATOR CAUSAL REASON MODAL */
        #reasonModalOverlay {
            display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0.65); z-index: 10000; align-items: center; justify-content: center;
        }
        .reason-modal {
            background: var(--panel); border: 2px solid var(--blue); border-radius: 10px;
            width: 520px; max-width: 92%; padding: 20px; box-shadow: 0 15px 40px rgba(0,0,0,0.8);
        }
        .reason-modal h3 { margin-top: 0; color: var(--blue); }

        /* EDIT INDICATOR MODAL */
        #editModalOverlay {
            display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0.72); z-index: 10001; align-items: center; justify-content: center;
        }
        .edit-modal {
            background: var(--panel); border: 2px solid var(--accent); border-radius: 10px;
            width: 420px; max-width: 92%; padding: 22px; box-shadow: 0 15px 40px rgba(0,0,0,0.85);
        }
        .edit-modal h3 { margin-top: 0; color: var(--accent); font-size: 1rem; }
        .edit-modal input[type=number] {
            width: 100%; padding: 9px 12px; background: #0f172a; border: 1px solid var(--border);
            border-radius: 5px; color: var(--text); font-size: 1rem; margin: 10px 0 6px 0;
        }
        .edit-modal .hint { font-size: 0.78rem; color: var(--muted); margin-bottom: 14px; }
        .edit-modal .btn-row { display: flex; gap: 10px; justify-content: flex-end; }
        .edit-modal .btn-row button {
            padding: 7px 18px; border: none; border-radius: 5px; font-weight: 700;
            cursor: pointer; font-size: 0.85rem; color: #fff;
        }
        .btn-save { background: var(--accent); }
        .btn-cancel { background: #475569; }
        .edit-btn {
            background: none; border: 1px solid #f97316; color: #f97316;
            border-radius: 4px; padding: 2px 6px; font-size: 0.70rem; cursor: pointer;
            font-weight: 700; opacity: 0.75;
        }
        .edit-btn:hover { opacity: 1; }

        /* EDIT INDICATOR MODAL */
        #editModalOverlay {
            display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0.72); z-index: 10001; align-items: center; justify-content: center;
        }
        .edit-modal {
            background: var(--panel); border: 2px solid var(--accent); border-radius: 10px;
            width: 420px; max-width: 92%; padding: 22px; box-shadow: 0 15px 40px rgba(0,0,0,0.85);
        }
        .edit-modal h3 { margin-top: 0; color: var(--accent); font-size: 1rem; }
        .edit-modal input[type=number] {
            width: 100%; padding: 9px 12px; background: #0f172a; border: 1px solid var(--border);
            border-radius: 5px; color: var(--text); font-size: 1rem; margin: 10px 0 14px 0;
        }
        .edit-modal .hint { font-size: 0.78rem; color: var(--muted); margin-bottom: 14px; }
        .edit-modal .btn-row { display: flex; gap: 10px; justify-content: flex-end; }
        .edit-modal .btn-row button {
            padding: 7px 18px; border: none; border-radius: 5px; font-weight: 700;
            cursor: pointer; font-size: 0.85rem; color: #fff;
        }
        .btn-save { background: var(--accent); }
        .btn-cancel { background: #475569; }
        .edit-btn {
            background: none; border: 1px solid var(--accent); color: var(--accent);
            border-radius: 4px; padding: 2px 7px; font-size: 0.72rem; cursor: pointer;
            font-weight: 600; margin-left: 4px;
        }
    </style>
</head>
<body>

<div class="top-bar">
    <div>
        <h1>🇮🇳 India Macroeconomic Simulator</h1>
        <small style="color:var(--muted);">Move any lever to see Live Pros/Cons &bull; Click any indicator row to see why it changed</small>
    </div>
    <div class="top-bar-right">
        <div class="status-pill">
            📅 <%= econ.getYear() %> Q<%= econ.getQuarter() %>
        </div>
        <div class="status-pill" style="border-color:var(--green);color:var(--green);">
            📈 GDP $<%= String.format("%.0f", econ.getGdpUsdBillion()) %>B
        </div>
    </div>
</div>

<!-- KPI STRIP -->
<%
    String gdpDeltaColor = econ.getDelta("gdpAnnualGrowthRate") >= 0 ? "#22c55e" : "#ef4444";
    String infDeltaColor = econ.getDelta("inflationRate") <= 0 ? "#22c55e" : "#ef4444";
    String uemDeltaColor = econ.getDelta("unemploymentRate") <= 0 ? "#22c55e" : "#ef4444";
    String senDeltaColor = econ.getDelta("stockMarketSensex") >= 0 ? "#22c55e" : "#ef4444";
    String fxDeltaColor  = econ.getDelta("currencyUsdInr") <= 0 ? "#22c55e" : "#ef4444";
%>
<div class="kpi-strip">
    <div class="kpi-card" style="border-color:rgba(34,197,94,0.4);">
        <div class="kpi-label">GDP Growth</div>
        <div class="kpi-val" style="color:#22c55e;"><%= econ.getGdpAnnualGrowthRate() %>%</div>
        <div class="kpi-delta" style="color:<%= gdpDeltaColor %>;"><%= fmtDelta(econ,"gdpAnnualGrowthRate",false) %></div>
    </div>
    <div class="kpi-card" style="border-color:rgba(251,191,36,0.4);">
        <div class="kpi-label">CPI Inflation</div>
        <div class="kpi-val" style="color:#fbbf24;"><%= econ.getInflationRate() %>%</div>
        <div class="kpi-delta" style="color:<%= infDeltaColor %>;"><%= fmtDelta(econ,"inflationRate",true) %></div>
    </div>
    <div class="kpi-card" style="border-color:rgba(192,132,252,0.4);">
        <div class="kpi-label">Unemployment</div>
        <div class="kpi-val" style="color:#c084fc;"><%= econ.getUnemploymentRate() %>%</div>
        <div class="kpi-delta" style="color:<%= uemDeltaColor %>;"><%= fmtDelta(econ,"unemploymentRate",true) %></div>
    </div>
    <div class="kpi-card" style="border-color:rgba(56,189,248,0.4);">
        <div class="kpi-label">BSE Sensex</div>
        <div class="kpi-val" style="color:#38bdf8;"><%= String.format("%,.0f", econ.getStockMarketSensex()) %></div>
        <div class="kpi-delta" style="color:<%= senDeltaColor %>;"><%= fmtDelta(econ,"stockMarketSensex",false) %></div>
    </div>
    <div class="kpi-card" style="border-color:rgba(249,115,22,0.4);">
        <div class="kpi-label">USD/INR</div>
        <div class="kpi-val" style="color:#f97316;"><%= econ.getCurrencyUsdInr() %></div>
        <div class="kpi-delta" style="color:<%= fxDeltaColor %>;"><%= fmtDelta(econ,"currencyUsdInr",true) %></div>
    </div>
    <div class="kpi-card" style="border-color:rgba(239,68,68,0.4);">
        <div class="kpi-label">Fiscal Deficit</div>
        <div class="kpi-val" style="color:#ef4444;"><%= econ.getGovernmentBudget() %>%</div>
        <div class="kpi-delta"><%= fmtDelta(econ,"governmentBudget",false) %></div>
    </div>
</div>

<div class="summary-banner">
    <strong>📊 Executive Turn Diagnostic:</strong> <%= econ.getLastTurnSummary() %>
</div>

<div class="main-grid">

    <!-- LEFT: ALL 11 INDICATOR TABS (wide, primary view) -->
    <div>

        <div class="te-tabs">
            <% String[] tabs = {"Overview", "GDP", "Labour", "Prices", "Money", "Trade", "Government", "Business", "Consumer", "Housing", "Health"};
               for (String t : tabs) { %>
                <button type="button" id="btn-<%= t %>" class="te-tab-btn <%= t.equals(activeTab) ? "active" : "" %>"
                        onclick="openTab('<%= t %>')"><%= t %></button>
            <% } %>
        </div>

        <!-- TAB 1: OVERVIEW -->
        <div id="tab-Overview" class="tab-pane <%= "Overview".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Overview Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why? / Edit</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Currency (USD/INR)', 'currencyUsdInr')"><td>Currency (USD/INR)</td><td class="val-col"><%= econ.getCurrencyUsdInr() %></td><td><%= fmtDelta(econ, "currencyUsdInr", true) %></td><td>INR</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('currencyUsdInr','Currency USD/INR','<%= econ.getCurrencyUsdInr() %>','INR')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Stock Market (BSE Sensex)', 'stockMarketSensex')"><td>Stock Market (BSE Sensex)</td><td class="val-col"><%= econ.getStockMarketSensex() %></td><td><%= fmtDelta(econ, "stockMarketSensex", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('stockMarketSensex','BSE Sensex','<%= econ.getStockMarketSensex() %>','pts')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('GDP Growth Rate QoQ', 'gdpGrowthRate')"><td>GDP Growth Rate</td><td class="val-col"><%= econ.getGdpGrowthRate() %></td><td><%= fmtDelta(econ, "gdpGrowthRate", false) %></td><td>% QoQ</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('gdpGrowthRate','GDP Growth Rate QoQ','<%= econ.getGdpGrowthRate() %>','%')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('GDP Annual Growth Rate YoY', 'gdpAnnualGrowthRate')"><td>GDP Annual Growth Rate</td><td class="val-col"><%= econ.getGdpAnnualGrowthRate() %></td><td><%= fmtDelta(econ, "gdpAnnualGrowthRate", false) %></td><td>% YoY</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('gdpAnnualGrowthRate','GDP Annual Growth YoY','<%= econ.getGdpAnnualGrowthRate() %>','%')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Unemployment Rate', 'unemploymentRate')"><td>Unemployment Rate</td><td class="val-col"><%= econ.getUnemploymentRate() %></td><td><%= fmtDelta(econ, "unemploymentRate", true) %></td><td>%</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('unemploymentRate','Unemployment Rate','<%= econ.getUnemploymentRate() %>','%')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('CPI Inflation Rate YoY', 'inflationRate')"><td>Inflation Rate</td><td class="val-col"><%= econ.getInflationRate() %></td><td><%= fmtDelta(econ, "inflationRate", true) %></td><td>% YoY</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('inflationRate','Inflation Rate','<%= econ.getInflationRate() %>','%')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('RBI Repo Interest Rate', 'interbankRate')"><td>Interest Rate (RBI Repo)</td><td class="val-col"><%= econ.getInterestRate() %></td><td>—</td><td>%</td><td><span class="badge b-lag">Policy</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Cash Reserve Ratio (CRR)', 'moneySupplyM1')"><td>Cash Reserve Ratio</td><td class="val-col"><%= econ.getCashReserveRatio() %></td><td>—</td><td>%</td><td><span class="badge b-lag">Policy</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Balance of Trade', 'balanceOfTrade')"><td>Balance of Trade</td><td class="val-col"><%= econ.getBalanceOfTrade() %></td><td><%= fmtDelta(econ, "balanceOfTrade", false) %></td><td>USD Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('balanceOfTrade','Balance of Trade','<%= econ.getBalanceOfTrade() %>','$B')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Current Account', 'currentAccount')"><td>Current Account</td><td class="val-col"><%= econ.getCurrentAccount() %></td><td><%= fmtDelta(econ, "currentAccount", false) %></td><td>USD Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('currentAccount','Current Account','<%= econ.getCurrentAccount() %>','$B')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Current Account to GDP', 'currentAccountToGdp')"><td>Current Account to GDP</td><td class="val-col"><%= econ.getCurrentAccountToGdp() %></td><td><%= fmtDelta(econ, "currentAccountToGdp", false) %></td><td>% of GDP</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('currentAccountToGdp','Current Account/GDP','<%= econ.getCurrentAccountToGdp() %>','%')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Government Debt to GDP', 'governmentDebtToGdp')"><td>Government Debt to GDP</td><td class="val-col"><%= econ.getGovernmentDebtToGdp() %></td><td><%= fmtDelta(econ, "governmentDebtToGdp", true) %></td><td>% of GDP</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('governmentDebtToGdp','Govt Debt/GDP','<%= econ.getGovernmentDebtToGdp() %>','%')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Government Budget Balance', 'governmentBudget')"><td>Government Budget</td><td class="val-col"><%= econ.getGovernmentBudget() %></td><td><%= fmtDelta(econ, "governmentBudget", false) %></td><td>% of GDP</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('governmentBudget','Govt Budget Balance','<%= econ.getGovernmentBudget() %>','%')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Business Confidence Index', 'businessConfidence')"><td>Business Confidence</td><td class="val-col"><%= econ.getBusinessConfidence() %></td><td><%= fmtDelta(econ, "businessConfidence", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('businessConfidence','Business Confidence','<%= econ.getBusinessConfidence() %>','pts')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Manufacturing PMI', 'manufacturingPMI')"><td>Manufacturing PMI</td><td class="val-col"><%= econ.getManufacturingPMI() %></td><td><%= fmtDelta(econ, "manufacturingPMI", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('manufacturingPMI','Manufacturing PMI','<%= econ.getManufacturingPMI() %>','pts')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Services PMI', 'servicesPMI')"><td>Services PMI</td><td class="val-col"><%= econ.getServicesPMI() %></td><td><%= fmtDelta(econ, "servicesPMI", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('servicesPMI','Services PMI','<%= econ.getServicesPMI() %>','pts')">&#9998;</button></td></tr>
                    <tr onclick="showWhy('Consumer Confidence Index', 'consumerConfidence')"><td>Consumer Confidence</td><td class="val-col"><%= econ.getConsumerConfidence() %></td><td><%= fmtDelta(econ, "consumerConfidence", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button> <button type="button" class="edit-btn" onclick="event.stopPropagation();openEditModal('consumerConfidence','Consumer Confidence','<%= econ.getConsumerConfidence() %>','pts')">&#9998;</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 2: GDP -->
        <div id="tab-GDP" class="tab-pane <%= "GDP".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>GDP Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('GDP Growth Rate QoQ', 'gdpGrowthRate')"><td>GDP Growth Rate</td><td class="val-col"><%= econ.getGdpGrowthRate() %></td><td><%= fmtDelta(econ, "gdpGrowthRate", false) %></td><td>% QoQ</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP Annual Growth Rate YoY', 'gdpAnnualGrowthRate')"><td>GDP Annual Growth Rate</td><td class="val-col"><%= econ.getGdpAnnualGrowthRate() %></td><td><%= fmtDelta(econ, "gdpAnnualGrowthRate", false) %></td><td>% YoY</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Full Year GDP Growth', 'fullYearGdpGrowth')"><td>Full Year GDP Growth</td><td class="val-col"><%= econ.getFullYearGdpGrowth() %></td><td><%= fmtDelta(econ, "fullYearGdpGrowth", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP Nominal ($B)', 'gdpUsdBillion')"><td>GDP (Nominal)</td><td class="val-col"><%= econ.getGdpUsdBillion() %></td><td><%= fmtDelta(econ, "gdpUsdBillion", false) %></td><td>USD Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP Constant Prices', 'gdpConstantPrices')"><td>GDP Constant Prices</td><td class="val-col"><%= econ.getGdpConstantPrices() %></td><td><%= fmtDelta(econ, "gdpConstantPrices", false) %></td><td>INR Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Gross Fixed Capital Formation', 'grossFixedCapitalFormation')"><td>Gross Fixed Capital Formation</td><td class="val-col"><%= econ.getGrossFixedCapitalFormation() %></td><td><%= fmtDelta(econ, "grossFixedCapitalFormation", false) %></td><td>INR Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP per Capita', 'gdpPerCapita')"><td>GDP per Capita</td><td class="val-col"><%= econ.getGdpPerCapita() %></td><td><%= fmtDelta(econ, "gdpPerCapita", false) %></td><td>USD</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP per Capita PPP', 'gdpPerCapitaPpp')"><td>GDP per Capita PPP</td><td class="val-col"><%= econ.getGdpPerCapitaPpp() %></td><td><%= fmtDelta(econ, "gdpPerCapitaPpp", false) %></td><td>USD PPP</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Gross National Product (GNP)', 'gnpInrCrore')"><td>Gross National Product (GNP)</td><td class="val-col"><%= String.format("%.0f", econ.getGnpInrCrore()) %></td><td><%= fmtDelta(econ, "gnpInrCrore", false) %></td><td>INR Crore</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP from Agriculture', 'gdpFromAgriculture')"><td>GDP from Agriculture</td><td class="val-col"><%= econ.getGdpFromAgriculture() %></td><td><%= fmtDelta(econ, "gdpFromAgriculture", false) %></td><td>INR Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP from Manufacturing', 'gdpFromManufacturing')"><td>GDP from Manufacturing</td><td class="val-col"><%= econ.getGdpFromManufacturing() %></td><td><%= fmtDelta(econ, "gdpFromManufacturing", false) %></td><td>INR Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP from Construction', 'gdpFromConstruction')"><td>GDP from Construction</td><td class="val-col"><%= econ.getGdpFromConstruction() %></td><td><%= fmtDelta(econ, "gdpFromConstruction", false) %></td><td>INR Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP from Public Administration', 'gdpFromPublicAdmin')"><td>GDP from Public Administration</td><td class="val-col"><%= econ.getGdpFromPublicAdmin() %></td><td><%= fmtDelta(econ, "gdpFromPublicAdmin", false) %></td><td>INR Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP from Utilities', 'gdpFromUtilities')"><td>GDP from Utilities</td><td class="val-col"><%= econ.getGdpFromUtilities() %></td><td><%= fmtDelta(econ, "gdpFromUtilities", false) %></td><td>INR Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP from Mining', 'gdpFromMining')"><td>GDP from Mining</td><td class="val-col"><%= econ.getGdpFromMining() %></td><td><%= fmtDelta(econ, "gdpFromMining", false) %></td><td>INR Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 3: LABOUR -->
        <div id="tab-Labour" class="tab-pane <%= "Labour".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Labour Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Unemployment Rate', 'unemploymentRate')"><td>Unemployment Rate</td><td class="val-col"><%= econ.getUnemploymentRate() %></td><td><%= fmtDelta(econ, "unemploymentRate", true) %></td><td>%</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Employment Rate', 'employmentRate')"><td>Employment Rate</td><td class="val-col"><%= econ.getEmploymentRate() %></td><td><%= fmtDelta(econ, "employmentRate", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Labor Force Participation Rate', 'laborForceParticipation')"><td>Labor Force Participation Rate</td><td class="val-col"><%= econ.getLaborForceParticipation() %></td><td><%= fmtDelta(econ, "laborForceParticipation", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Average Monthly Wages', 'monthlyWages')"><td>Wages</td><td class="val-col"><%= econ.getMonthlyWages() %></td><td><%= fmtDelta(econ, "monthlyWages", false) %></td><td>INR / Month</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Minimum Wages', 'minimumWages')"><td>Minimum Wages</td><td class="val-col"><%= econ.getMinimumWages() %></td><td><%= fmtDelta(econ, "minimumWages", false) %></td><td>INR / Day</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Population', 'populationMillion')"><td>Population</td><td class="val-col"><%= econ.getPopulationMillion() %></td><td><%= fmtDelta(econ, "populationMillion", false) %></td><td>Million</td><td><span class="badge b-coin">Demographic</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 4: PRICES -->
        <div id="tab-Prices" class="tab-pane <%= "Prices".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Prices Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Inflation Rate YoY', 'inflationRate')"><td>Inflation Rate YoY</td><td class="val-col"><%= econ.getInflationRate() %></td><td><%= fmtDelta(econ, "inflationRate", true) %></td><td>%</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Consumer Price Index CPI', 'cpiPoints')"><td>Consumer Price Index CPI</td><td class="val-col"><%= econ.getCpiPoints() %></td><td><%= fmtDelta(econ, "cpiPoints", true) %></td><td>points</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Consumer Food Price Index (CFPI)', 'consumerFoodPriceIndex')"><td>Consumer Food Price Index</td><td class="val-col"><%= econ.getConsumerFoodPriceIndex() %></td><td><%= fmtDelta(econ, "consumerFoodPriceIndex", true) %></td><td>points</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('CPI Housing and Utilities', 'cpiHousingUtilities')"><td>CPI Housing and Utilities</td><td class="val-col"><%= econ.getCpiHousingUtilities() %></td><td><%= fmtDelta(econ, "cpiHousingUtilities", true) %></td><td>points</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('CPI Transportation', 'cpiTransportation')"><td>CPI Transportation</td><td class="val-col"><%= econ.getCpiTransportation() %></td><td><%= fmtDelta(econ, "cpiTransportation", true) %></td><td>points</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('CPI Education Services', 'cpiEducation')"><td>CPI Education Services</td><td class="val-col"><%= econ.getCpiEducation() %></td><td><%= fmtDelta(econ, "cpiEducation", true) %></td><td>points</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('CPI Restaurants & Hotels', 'cpiRestaurants')"><td>CPI Restaurants & Accommodation</td><td class="val-col"><%= econ.getCpiRestaurants() %></td><td><%= fmtDelta(econ, "cpiRestaurants", true) %></td><td>points</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Wholesale Price Index (WPI)', 'wholesalePriceIndex')"><td>Wholesale Price Index (WPI)</td><td class="val-col"><%= econ.getWholesalePriceIndex() %></td><td><%= fmtDelta(econ, "wholesalePriceIndex", true) %></td><td>points</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('WPI Food Index YoY', 'wpiFoodYoY')"><td>WPI Food Index YoY</td><td class="val-col"><%= econ.getWpiFoodYoY() %></td><td><%= fmtDelta(econ, "wpiFoodYoY", true) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Producer Prices (PPI)', 'producerPrices')"><td>Producer Prices (PPI)</td><td class="val-col"><%= econ.getProducerPrices() %></td><td><%= fmtDelta(econ, "producerPrices", true) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Export Prices', 'exportPrices')"><td>Export Prices</td><td class="val-col"><%= econ.getExportPrices() %></td><td><%= fmtDelta(econ, "exportPrices", false) %></td><td>points</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Import Prices', 'importPrices')"><td>Import Prices</td><td class="val-col"><%= econ.getImportPrices() %></td><td><%= fmtDelta(econ, "importPrices", true) %></td><td>points</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 5: MONEY -->
        <div id="tab-Money" class="tab-pane <%= "Money".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Money & Banking Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Interbank Rate', 'interbankRate')"><td>Interbank Rate</td><td class="val-col"><%= econ.getInterbankRate() %></td><td><%= fmtDelta(econ, "interbankRate", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('10Y–2Y G-Sec Yield Curve Spread', 'yieldCurveSpread')"><td>10Y–2Y G-Sec Yield Curve Spread</td><td class="val-col"><%= econ.getYieldCurveSpread() %></td><td><%= fmtDelta(econ, "yieldCurveSpread", false) %></td><td>%</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Bank Loan Growth YoY', 'bankLoanGrowthYoY')"><td>Bank Loan Growth YoY</td><td class="val-col"><%= econ.getBankLoanGrowthYoY() %></td><td><%= fmtDelta(econ, "bankLoanGrowthYoY", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Money Supply M1', 'moneySupplyM1')"><td>Money Supply M1</td><td class="val-col"><%= econ.getMoneySupplyM1() %></td><td><%= fmtDelta(econ, "moneySupplyM1", false) %></td><td>INR Billion</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Foreign Exchange Reserves', 'foreignExchangeReserves')"><td>Foreign Exchange Reserves</td><td class="val-col"><%= String.format("%.0f", econ.getForeignExchangeReserves()) %></td><td><%= fmtDelta(econ, "foreignExchangeReserves", false) %></td><td>USD Million</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 6: TRADE -->
        <div id="tab-Trade" class="tab-pane <%= "Trade".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Trade Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Balance of Trade', 'balanceOfTrade')"><td>Balance of Trade</td><td class="val-col"><%= econ.getBalanceOfTrade() %></td><td><%= fmtDelta(econ, "balanceOfTrade", false) %></td><td>USD Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Exports', 'exportsUsdBillion')"><td>Exports</td><td class="val-col"><%= econ.getExportsUsdBillion() %></td><td><%= fmtDelta(econ, "exportsUsdBillion", false) %></td><td>USD Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Imports', 'importsUsdBillion')"><td>Imports</td><td class="val-col"><%= econ.getImportsUsdBillion() %></td><td><%= fmtDelta(econ, "importsUsdBillion", true) %></td><td>USD Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Current Account', 'currentAccount')"><td>Current Account</td><td class="val-col"><%= econ.getCurrentAccount() %></td><td><%= fmtDelta(econ, "currentAccount", false) %></td><td>USD Million</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Current Account to GDP', 'currentAccountToGdp')"><td>Current Account to GDP</td><td class="val-col"><%= econ.getCurrentAccountToGdp() %></td><td><%= fmtDelta(econ, "currentAccountToGdp", false) %></td><td>% of GDP</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Terms of Trade', 'termsOfTrade')"><td>Terms of Trade</td><td class="val-col"><%= econ.getTermsOfTrade() %></td><td><%= fmtDelta(econ, "termsOfTrade", false) %></td><td>points</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Foreign Direct Investment (FDI)', 'foreignDirectInvestment')"><td>Foreign Direct Investment (FDI)</td><td class="val-col"><%= econ.getForeignDirectInvestment() %></td><td><%= fmtDelta(econ, "foreignDirectInvestment", false) %></td><td>USD Billion/Qtr</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Remittances', 'remittancesUsdBillion')"><td>Remittances</td><td class="val-col"><%= econ.getRemittancesUsdBillion() %></td><td><%= fmtDelta(econ, "remittancesUsdBillion", false) %></td><td>USD Billion/Qtr</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('External Debt', 'externalDebtUsdBillion')"><td>External Debt</td><td class="val-col"><%= econ.getExternalDebtUsdBillion() %></td><td><%= fmtDelta(econ, "externalDebtUsdBillion", true) %></td><td>USD Billion</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Tourist Arrivals', 'touristArrivals')"><td>Tourist Arrivals</td><td class="val-col"><%= String.format("%.0f", econ.getTouristArrivals()) %></td><td><%= fmtDelta(econ, "touristArrivals", false) %></td><td>Persons/Month</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 7: GOVERNMENT -->
        <div id="tab-Government" class="tab-pane <%= "Government".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Government & Fiscal Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Government Budget Balance', 'governmentBudget')"><td>Government Budget</td><td class="val-col"><%= econ.getGovernmentBudget() %></td><td><%= fmtDelta(econ, "governmentBudget", false) %></td><td>% of GDP</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Government Debt to GDP', 'governmentDebtToGdp')"><td>Government Debt to GDP</td><td class="val-col"><%= econ.getGovernmentDebtToGdp() %></td><td><%= fmtDelta(econ, "governmentDebtToGdp", true) %></td><td>% of GDP</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Government Spending to GDP', 'governmentSpendingToGdp')"><td>Government Spending to GDP</td><td class="val-col"><%= econ.getGovernmentSpendingToGdp() %></td><td><%= fmtDelta(econ, "governmentSpendingToGdp", false) %></td><td>% of GDP</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Government Revenues', 'governmentRevenues')"><td>Government Revenues</td><td class="val-col"><%= String.format("%.0f", econ.getGovernmentRevenues()) %></td><td><%= fmtDelta(econ, "governmentRevenues", false) %></td><td>INR Crore</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Fiscal Expenditure', 'fiscalExpenditure')"><td>Fiscal Expenditure</td><td class="val-col"><%= String.format("%.0f", econ.getFiscalExpenditure()) %></td><td><%= fmtDelta(econ, "fiscalExpenditure", false) %></td><td>INR Crore</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Corruption Index', 'corruptionIndex')"><td>Corruption Index</td><td class="val-col"><%= econ.getCorruptionIndex() %></td><td><%= fmtDelta(econ, "corruptionIndex", false) %></td><td>Points</td><td><span class="badge b-lag">Institutional</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 8: BUSINESS -->
        <div id="tab-Business" class="tab-pane <%= "Business".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Business Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Business Confidence', 'businessConfidence')"><td>Business Confidence</td><td class="val-col"><%= econ.getBusinessConfidence() %></td><td><%= fmtDelta(econ, "businessConfidence", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Manufacturing PMI', 'manufacturingPMI')"><td>Manufacturing PMI</td><td class="val-col"><%= econ.getManufacturingPMI() %></td><td><%= fmtDelta(econ, "manufacturingPMI", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Services PMI', 'servicesPMI')"><td>Services PMI</td><td class="val-col"><%= econ.getServicesPMI() %></td><td><%= fmtDelta(econ, "servicesPMI", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Composite PMI', 'compositePMI')"><td>Composite PMI</td><td class="val-col"><%= econ.getCompositePMI() %></td><td><%= fmtDelta(econ, "compositePMI", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Net Business Formations', 'netBusinessFormations')"><td>Net Business Formations</td><td class="val-col"><%= econ.getNetBusinessFormations() %></td><td><%= fmtDelta(econ, "netBusinessFormations", false) %></td><td>Thousands/Qtr</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Industrial Production YoY', 'industrialProductionYoY')"><td>Industrial Production YoY</td><td class="val-col"><%= econ.getIndustrialProductionYoY() %></td><td><%= fmtDelta(econ, "industrialProductionYoY", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Manufacturing Production YoY', 'manufacturingProductionYoY')"><td>Manufacturing Production YoY</td><td class="val-col"><%= econ.getManufacturingProductionYoY() %></td><td><%= fmtDelta(econ, "manufacturingProductionYoY", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Capacity Utilization', 'capacityUtilization')"><td>Capacity Utilization</td><td class="val-col"><%= econ.getCapacityUtilization() %></td><td><%= fmtDelta(econ, "capacityUtilization", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Passenger Vehicle Sales', 'passengerVehicleSales')"><td>Passenger Vehicle Sales</td><td class="val-col"><%= String.format("%.0f", econ.getPassengerVehicleSales()) %></td><td><%= fmtDelta(econ, "passengerVehicleSales", false) %></td><td>Units/Month</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Deposit Growth YoY', 'depositGrowthYoY')"><td>Deposit Growth YoY</td><td class="val-col"><%= econ.getDepositGrowthYoY() %></td><td><%= fmtDelta(econ, "depositGrowthYoY", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 9: CONSUMER -->
        <div id="tab-Consumer" class="tab-pane <%= "Consumer".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Consumer Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Consumer Confidence (CSI)', 'consumerConfidence')"><td>Consumer Confidence (RBI CSI)</td><td class="val-col"><%= econ.getConsumerConfidence() %></td><td><%= fmtDelta(econ, "consumerConfidence", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Consumer Durables Index', 'consumerDurablesIndex')"><td>Consumer Durables Index</td><td class="val-col"><%= econ.getConsumerDurablesIndex() %></td><td><%= fmtDelta(econ, "consumerDurablesIndex", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Retail Sales Growth YoY', 'retailSalesGrowthYoY')"><td>Retail Sales / Spending Growth</td><td class="val-col"><%= econ.getRetailSalesGrowthYoY() %></td><td><%= fmtDelta(econ, "retailSalesGrowthYoY", false) %></td><td>% YoY</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Bank Lending Rate', 'bankLendingRate')"><td>Bank Lending Rate</td><td class="val-col"><%= econ.getBankLendingRate() %></td><td><%= fmtDelta(econ, "bankLendingRate", true) %></td><td>%</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Gasoline Prices', 'gasolinePrices')"><td>Gasoline Prices</td><td class="val-col"><%= econ.getGasolinePrices() %></td><td><%= fmtDelta(econ, "gasolinePrices", true) %></td><td>USD/Liter</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Households Debt to GDP', 'householdsDebtToGdp')"><td>Households Debt to GDP</td><td class="val-col"><%= econ.getHouseholdsDebtToGdp() %></td><td><%= fmtDelta(econ, "householdsDebtToGdp", true) %></td><td>% of GDP</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 10: HOUSING -->
        <div id="tab-Housing" class="tab-pane <%= "Housing".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Housing Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Housing Index (NHB Residex)', 'housingIndex')"><td>Housing Index (NHB Residex)</td><td class="val-col"><%= econ.getHousingIndex() %></td><td><%= fmtDelta(econ, "housingIndex", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Infrastructure Output YoY', 'infrastructureOutputYoY')"><td>Infrastructure Output YoY</td><td class="val-col"><%= econ.getInfrastructureOutputYoY() %></td><td><%= fmtDelta(econ, "infrastructureOutputYoY", false) %></td><td>%</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Residential Property Prices YoY', 'residentialPropertyPricesYoY')"><td>Residential Property Prices YoY</td><td class="val-col"><%= econ.getResidentialPropertyPricesYoY() %></td><td><%= fmtDelta(econ, "residentialPropertyPricesYoY", false) %></td><td>%</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP from Construction', 'gdpFromConstruction')"><td>GDP from Construction</td><td class="val-col"><%= econ.getGdpFromConstruction() %></td><td><%= fmtDelta(econ, "gdpFromConstruction", false) %></td><td>INR Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>

        <!-- TAB 11: HEALTH -->
        <div id="tab-Health" class="tab-pane <%= "Health".equals(activeTab) ? "active" : "" %>">
            <table class="te-table">
                <thead><tr><th>Health Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Hospital Beds per 1,000', 'hospitalBedsPer1000')"><td>Hospital Beds</td><td class="val-col"><%= econ.getHospitalBedsPer1000() %></td><td><%= fmtDelta(econ, "hospitalBedsPer1000", false) %></td><td>per 1,000 people</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Public Health Readiness Index', 'publicHealthReadinessIndex')"><td>Public Health Readiness Index</td><td class="val-col"><%= econ.getPublicHealthReadinessIndex() %></td><td><%= fmtDelta(econ, "publicHealthReadinessIndex", false) %></td><td>points (0–100)</td><td><span class="badge b-lag">Structural</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                </tbody>
            </table>
        </div>
    </div>
    <!-- END LEFT: INDICATOR TABS -->

    <!-- RIGHT: STICKY POLICY LEVERS PANEL -->
    <div class="levers-panel">
      <form action="<%= request.getContextPath() %>/simulate" method="post" class="card" style="padding:12px;">
        <input type="hidden" id="activeTabInput" name="activeTab" value="<%= activeTab %>">

        <!-- ACTION BUTTONS -->
        <div style="margin-bottom:10px;padding-bottom:10px;border-bottom:1px solid var(--border);">
            <div style="font-size:0.69rem;color:var(--muted);text-transform:uppercase;font-weight:700;letter-spacing:0.05em;margin-bottom:6px;">⚙️ Simulation Controls</div>
            <div class="action-bar">
                <button type="submit" name="action" value="advanceQuarter" class="b-qtr">▶ +1 Quarter</button>
                <button type="submit" name="action" value="advanceYear"    class="b-yr" >▶▶ +1 Year</button>
                <button type="submit" name="action" value="checkReality"   class="b-cwr">🔬 Reality Check</button>
                <button type="submit" name="action" value="reset"          class="b-rst">↺ Reset</button>
            </div>
            <div class="action-bar" style="margin-top:6px;">
                <button type="submit" name="action" value="dynamicRealityCheck"
                        style="background:#b45309;font-size:0.76rem;width:100%;"
                        title="Compare your simulation against World Bank actuals for the current year">
                    🔬 Dynamic Reality Check
                </button>
            </div>
        </div>

        <!-- TAXES & TARIFFS -->
        <div class="lever-section">
            <button type="button" class="lever-section-header" onclick="toggleSection(this)">
                🏦 Taxes &amp; Tariffs <i class="chevron">▾</i>
            </button>
            <div class="lever-section-body">
                <div class="slider-row"><label><span>Corporate Tax</span><strong id="lbl_corp"><%= econ.getCorporateTaxRate() %>%</strong></label><input type="range" name="corporateTaxRate" min="15" max="45" step="0.5" data-baseline="<%= econ.getCorporateTaxRate() %>" value="<%= econ.getCorporateTaxRate() %>" oninput="onPolicyAdjust('corporateTaxRate', this, '%')"></div>
                <div class="slider-row"><label><span>Personal Income Tax</span><strong id="lbl_pit"><%= econ.getPersonalIncomeTaxRate() %>%</strong></label><input type="range" name="personalIncomeTaxRate" min="5" max="40" step="0.5" data-baseline="<%= econ.getPersonalIncomeTaxRate() %>" value="<%= econ.getPersonalIncomeTaxRate() %>" oninput="onPolicyAdjust('personalIncomeTaxRate', this, '%')"></div>
                <div class="slider-row"><label><span>GST / Sales Tax</span><strong id="lbl_gst"><%= econ.getSalesTaxGstRate() %>%</strong></label><input type="range" name="salesTaxGstRate" min="8" max="28" step="0.5" data-baseline="<%= econ.getSalesTaxGstRate() %>" value="<%= econ.getSalesTaxGstRate() %>" oninput="onPolicyAdjust('salesTaxGstRate', this, '%')"></div>
                <div class="slider-row"><label><span>Social Security (EPFO)</span><strong id="lbl_soc"><%= econ.getSocialSecurityRate() %>%</strong></label><input type="range" name="socialSecurityRate" min="10" max="35" step="0.5" data-baseline="<%= econ.getSocialSecurityRate() %>" value="<%= econ.getSocialSecurityRate() %>" oninput="onPolicyAdjust('socialSecurityRate', this, '%')"></div>
                <div class="slider-row"><label><span>Withholding Tax</span><strong id="lbl_wht"><%= econ.getWithholdingTaxRate() %>%</strong></label><input type="range" name="withholdingTaxRate" min="5" max="35" step="0.5" data-baseline="<%= econ.getWithholdingTaxRate() %>" value="<%= econ.getWithholdingTaxRate() %>" oninput="onPolicyAdjust('withholdingTaxRate', this, '%')"></div>
                <div class="slider-row"><label><span>Customs &amp; Import Duty</span><strong id="lbl_cst"><%= econ.getCustomsDutyRate() %>%</strong></label><input type="range" name="customsDutyRate" min="0" max="30" step="0.5" data-baseline="<%= econ.getCustomsDutyRate() %>" value="<%= econ.getCustomsDutyRate() %>" oninput="onPolicyAdjust('customsDutyRate', this, '%')"></div>
            </div>
        </div>

        <!-- RBI MONETARY -->
        <div class="lever-section">
            <button type="button" class="lever-section-header" onclick="toggleSection(this)">
                🏛️ RBI Monetary <i class="chevron">▾</i>
            </button>
            <div class="lever-section-body">
                <div class="slider-row"><label><span>Repo Rate</span><strong id="lbl_repo"><%= econ.getInterestRate() %>%</strong></label><input type="range" name="interestRate" min="3.0" max="10.0" step="0.25" data-baseline="<%= econ.getInterestRate() %>" value="<%= econ.getInterestRate() %>" oninput="onPolicyAdjust('interestRate', this, '%')"></div>
                <div class="slider-row"><label><span>Cash Reserve Ratio (CRR)</span><strong id="lbl_crr"><%= econ.getCashReserveRatio() %>%</strong></label><input type="range" name="cashReserveRatio" min="2.0" max="8.0" step="0.25" data-baseline="<%= econ.getCashReserveRatio() %>" value="<%= econ.getCashReserveRatio() %>" oninput="onPolicyAdjust('cashReserveRatio', this, '%')"></div>
            </div>
        </div>

        <!-- SECTORAL BUDGETS -->
        <div class="lever-section">
            <button type="button" class="lever-section-header" onclick="toggleSection(this)">
                📊 Sectoral Budgets (% GDP) <i class="chevron">▾</i>
            </button>
            <div class="lever-section-body">
                <div class="slider-row"><label><span>Infrastructure</span><strong id="lbl_inf"><%= econ.getInfraSpend() %>%</strong></label><input type="range" name="infraSpend" min="1.0" max="8.0" step="0.1" data-baseline="<%= econ.getInfraSpend() %>" value="<%= econ.getInfraSpend() %>" oninput="onPolicyAdjust('infraSpend', this, '%')"></div>
                <div class="slider-row"><label><span>Education</span><strong id="lbl_edu"><%= econ.getEducationSpend() %>%</strong></label><input type="range" name="educationSpend" min="1.0" max="6.0" step="0.1" data-baseline="<%= econ.getEducationSpend() %>" value="<%= econ.getEducationSpend() %>" oninput="onPolicyAdjust('educationSpend', this, '%')"></div>
                <div class="slider-row"><label><span>Healthcare</span><strong id="lbl_hlt"><%= econ.getHealthSpend() %>%</strong></label><input type="range" name="healthSpend" min="0.8" max="5.0" step="0.1" data-baseline="<%= econ.getHealthSpend() %>" value="<%= econ.getHealthSpend() %>" oninput="onPolicyAdjust('healthSpend', this, '%')"></div>
                <div class="slider-row"><label><span>Tech &amp; R&amp;D</span><strong id="lbl_tch"><%= econ.getTechSpend() %>%</strong></label><input type="range" name="techSpend" min="0.2" max="3.5" step="0.05" data-baseline="<%= econ.getTechSpend() %>" value="<%= econ.getTechSpend() %>" oninput="onPolicyAdjust('techSpend', this, '%')"></div>
                <div class="slider-row"><label><span>Machinery / PLI</span><strong id="lbl_mch"><%= econ.getMachinerySpend() %>%</strong></label><input type="range" name="machinerySpend" min="0.5" max="4.0" step="0.1" data-baseline="<%= econ.getMachinerySpend() %>" value="<%= econ.getMachinerySpend() %>" oninput="onPolicyAdjust('machinerySpend', this, '%')"></div>
                <div class="slider-row"><label><span>IT &amp; Digital Services</span><strong id="lbl_it"><%= econ.getItServicesSpend() %>%</strong></label><input type="range" name="itServicesSpend" min="0.3" max="3.5" step="0.1" data-baseline="<%= econ.getItServicesSpend() %>" value="<%= econ.getItServicesSpend() %>" oninput="onPolicyAdjust('itServicesSpend', this, '%')"></div>
                <div class="slider-row"><label><span>Tourism</span><strong id="lbl_trm"><%= econ.getTourismSpend() %>%</strong></label><input type="range" name="tourismSpend" min="0.1" max="2.5" step="0.1" data-baseline="<%= econ.getTourismSpend() %>" value="<%= econ.getTourismSpend() %>" oninput="onPolicyAdjust('tourismSpend', this, '%')"></div>
            </div>
        </div>

        <!-- EXOGENOUS SHOCKS — collapsed by default -->
        <div class="lever-section">
            <button type="button" class="lever-section-header collapsed" onclick="toggleSection(this)">
                ⚡ Exogenous Shocks <i class="chevron">▾</i>
            </button>
            <div class="lever-section-body hidden">
                <div class="slider-row"><label><span>Brent Crude ($/bbl)</span><strong id="lbl_oil"><%= econ.getBrentCrudePrice() %></strong></label><input type="range" name="brentCrudePrice" min="20" max="150" step="1" data-baseline="<%= econ.getBrentCrudePrice() %>" value="<%= econ.getBrentCrudePrice() %>" oninput="syncLabel('lbl_oil', this.value, '')"></div>
                <div class="slider-row"><label><span>Monsoon Index (100=Normal)</span><strong id="lbl_mon"><%= econ.getMonsoonIndex() %></strong></label><input type="range" name="monsoonIndex" min="70" max="130" step="1" data-baseline="<%= econ.getMonsoonIndex() %>" value="<%= econ.getMonsoonIndex() %>" oninput="syncLabel('lbl_mon', this.value, '')"></div>
                <div class="slider-row"><label><span>M1 Shock % (0=none)</span><strong id="lbl_m1s"><%= econ.getM1ShockPct() %></strong></label><input type="range" name="m1ShockPct" min="-25" max="10" step="1" data-baseline="0" value="<%= econ.getM1ShockPct() %>" oninput="syncLabel('lbl_m1s', this.value, '')"></div>
                <div class="slider-row"><label><span>Banking NPA %</span><strong id="lbl_npa"><%= econ.getBankingNpaPct() %>%</strong></label><input type="range" name="bankingNpaPct" min="2" max="20" step="0.5" data-baseline="<%= econ.getBankingNpaPct() %>" value="<%= econ.getBankingNpaPct() %>" oninput="syncLabel('lbl_npa', this.value, '%')"></div>
                <div class="slider-row"><label><span>Tax Buoyancy Coeff</span><strong id="lbl_tbc"><%= econ.getTaxBuoyancyCoeff() %></strong></label><input type="range" name="taxBuoyancyCoeff" min="0.6" max="1.5" step="0.05" data-baseline="<%= econ.getTaxBuoyancyCoeff() %>" value="<%= econ.getTaxBuoyancyCoeff() %>" oninput="syncLabel('lbl_tbc', this.value, '')"></div>
                <div class="slider-row"><label><span>Neutral Repo r* (%)</span><strong id="lbl_rstar"><%= econ.getNeutralRepoRate() %>%</strong></label><input type="range" name="neutralRepoRate" min="4.0" max="9.0" step="0.25" data-baseline="<%= econ.getNeutralRepoRate() %>" value="<%= econ.getNeutralRepoRate() %>" oninput="syncLabel('lbl_rstar', this.value, '%')"></div>
            </div>
        </div>

        <!-- LOAD HISTORICAL YEAR — collapsed by default -->
        <div class="lever-section">
            <button type="button" class="lever-section-header collapsed" onclick="toggleSection(this)">
                📅 Load Historical Year <i class="chevron">▾</i>
            </button>
            <div class="lever-section-body hidden">
                <p style="font-size:0.75rem;color:var(--muted);margin:0 0 10px;line-height:1.5;">
                    Seeds <strong style="color:var(--text);">all indicators</strong> from real World Bank data for the chosen year.
                    Then adjust policy levers, click <strong>▶▶ +1 Year</strong> to simulate forward,
                    and finally click <strong>🔬 Dynamic Reality Check</strong> to compare against WB actuals.
                </p>
                <div style="background:rgba(251,191,36,0.07);border:1px solid rgba(251,191,36,0.2);border-radius:6px;padding:8px 10px;margin-bottom:10px;font-size:0.73rem;color:var(--muted);line-height:1.5;">
                    <strong style="color:#fbbf24;">Workflow:</strong><br>
                    1. Click <strong>🌐 Fetch WB Data</strong> once (Dynamic WB Backtest section)<br>
                    2. Pick a year below and click <strong>📥 Load Year</strong><br>
                    3. Adjust policy levers → <strong>▶▶ +1 Year</strong><br>
                    4. Click <strong>🔬 Dynamic Reality Check</strong> above
                </div>
                <div style="margin-bottom:8px;">
                    <label style="font-size:0.76rem;color:var(--muted);display:block;margin-bottom:4px;">Historical Year to load:</label>
                    <select name="histYear" id="histYearSelect" style="width:100%;background:#0f172a;color:var(--text);border:1px solid var(--border);border-radius:4px;padding:6px 8px;font-size:0.83rem;" onchange="updateHistLabel(this.value)">
                        <option value="2006">2006</option>
                        <option value="2007">2007</option>
                        <option value="2008">2008 (GFC)</option>
                        <option value="2009">2009</option>
                        <option value="2010">2010</option>
                        <option value="2011">2011</option>
                        <option value="2012">2012</option>
                        <option value="2013">2013 (Taper Tantrum)</option>
                        <option value="2014" selected>2014</option>
                        <option value="2015">2015 (Demonetisation yr)</option>
                        <option value="2016">2016</option>
                        <option value="2017">2017 (GST launch)</option>
                        <option value="2018">2018 (NBFC crisis)</option>
                        <option value="2019">2019 (COVID shadow)</option>
                        <option value="2020">2020 (COVID)</option>
                        <option value="2021">2021 (Recovery)</option>
                        <option value="2022">2022 (Inflation surge)</option>
                        <option value="2023">2023</option>
                    </select>
                </div>
                <div id="histYearLabel" style="font-size:0.72rem;color:#fbbf24;margin-bottom:8px;text-align:center;">
                    Loads WB 2014 actuals → simulate → compare vs WB 2015
                </div>
                <div class="action-bar">
                    <button type="submit" name="action" value="loadHistoricalYear" style="background:#b45309;font-size:0.76rem;width:100%;" title="Seed all indicators from World Bank data for the chosen year">
                        📥 Load Year into Simulator
                    </button>
                </div>
            </div>
        </div>

        <!-- DYNAMIC WORLD BANK BACKTEST — collapsed by default -->
        <div class="lever-section">
            <button type="button" class="lever-section-header collapsed" onclick="toggleSection(this)">
                🌍 Dynamic WB Backtest <i class="chevron">▾</i>
            </button>
            <div class="lever-section-body hidden">
                <p style="font-size:0.75rem;color:var(--muted);margin:0 0 10px;line-height:1.5;">
                    Pick any year. The engine fetches <strong style="color:var(--text);">real World Bank data</strong> for that year, simulates forward, then compares against the <em>next year's</em> actual WB data — fully automatic, no hard-coded values.
                </p>
                <div style="background:rgba(56,189,248,0.07);border:1px solid rgba(56,189,248,0.2);border-radius:6px;padding:8px 10px;margin-bottom:10px;font-size:0.73rem;color:var(--muted);line-height:1.5;">
                    <strong style="color:var(--blue);">How it works:</strong><br>
                    1. Click <strong>🌐 Fetch</strong> once to cache 20 yrs of WB data<br>
                    2. Pick a <strong>Baseline Year</strong> (e.g. 2014)<br>
                    3. Click <strong>▶ Simulate &amp; Compare</strong><br>
                    → Seeds from WB 2014 actuals → simulates → compares vs WB 2015 actuals
                </div>
                <div style="margin-bottom:8px;">
                    <label style="font-size:0.76rem;color:var(--muted);display:block;margin-bottom:4px;">Baseline Year (seeds simulation):</label>
                    <select name="dynYear" id="dynYearSelect" style="width:100%;background:#0f172a;color:var(--text);border:1px solid var(--border);border-radius:4px;padding:6px 8px;font-size:0.83rem;" onchange="updateDynLabel(this.value)">
                        <option value="2006">2006 → 2007</option>
                        <option value="2007">2007 → 2008</option>
                        <option value="2008">2008 → 2009 (GFC)</option>
                        <option value="2009">2009 → 2010</option>
                        <option value="2010">2010 → 2011</option>
                        <option value="2011">2011 → 2012</option>
                        <option value="2012">2012 → 2013</option>
                        <option value="2013">2013 → 2014 (Taper Tantrum)</option>
                        <option value="2014" selected>2014 → 2015</option>
                        <option value="2015">2015 → 2016 (Demonetisation)</option>
                        <option value="2016">2016 → 2017</option>
                        <option value="2017">2017 → 2018 (GST launch)</option>
                        <option value="2018">2018 → 2019 (NBFC crisis)</option>
                        <option value="2019">2019 → 2020 (COVID shadow)</option>
                        <option value="2020">2020 → 2021 (COVID)</option>
                        <option value="2021">2021 → 2022 (Recovery)</option>
                        <option value="2022">2022 → 2023 (Inflation surge)</option>
                        <option value="2023">2023 → 2024</option>
                    </select>
                </div>
                <div id="dynYearLabel" style="font-size:0.72rem;color:var(--blue);margin-bottom:8px;text-align:center;">
                    Seed from WB 2014 actuals → Simulate → Compare vs WB 2015 actuals
                </div>
                <div class="action-bar">
                    <button type="submit" name="action" value="fetchHistory" style="background:#7c3aed;font-size:0.76rem;" title="Download 20 years of India data from World Bank API (once)">
                        🌐 Fetch WB Data
                    </button>
                    <button type="submit" name="action" value="dynamicBacktest" style="background:#059669;font-size:0.76rem;" title="Seed from selected year, simulate, compare vs next year">
                        ▶ Simulate &amp; Compare
                    </button>
                </div>
            </div>
        </div>

      </form>
    </div>
    <!-- END RIGHT: LEVERS PANEL -->

</div>

<!-- 1. FLOATING LIVE PROS & CONS POPUP WHEN ADJUSTING ANY SLIDER -->
<div id="policyPopup">
    <h4>
        <span id="popTitle">Policy Impact Preview</span>
        <span class="close-x" onclick="document.getElementById('policyPopup').style.display='none'">&times;</span>
    </h4>
    <div id="popDirection" style="font-size:0.8rem; color:var(--muted); margin-bottom:6px;"></div>
    <div class="pc-section"><span class="pc-pro">✅ PROS:</span> <span id="popPros"></span></div>
    <div class="pc-section"><span class="pc-con">⚠️ CONS:</span> <span id="popCons"></span></div>
    <div class="pc-section"><span class="pc-ind">📈 INDICATOR CASCADE:</span> <span id="popInds"></span></div>
</div>

<!-- 2. MODAL POPUP EXPLAINING WHY AN INDICATOR GREW OR FELL -->
<div id="reasonModalOverlay" onclick="this.style.display='none'">
    <div class="reason-modal" onclick="event.stopPropagation()">
        <h3 id="modalIndTitle">Indicator Causal Analysis</h3>
        <p id="modalIndBody" style="line-height:1.6; font-size:0.92rem; color:#e2e8f0;"></p>
        <div style="text-align:right; margin-top:14px;">
            <button type="button" class="why-btn" style="padding:6px 14px; font-size:0.85rem;"
                    onclick="document.getElementById('reasonModalOverlay').style.display='none'">Close</button>
        </div>
    </div>
</div>

<script>
    // Server-generated map of exact causal reasons for every indicator
const indicatorReasons = {
    <% for (java.util.Map.Entry<String, String> entry : econ.getReasons().entrySet()) { %>
        "<%= entry.getKey() %>": "<%= entry.getValue().replace("\"", "\\\"") %>",
    <% } %>
};

    // Inject ✏️ edit buttons into every table row that has a Why? button
    document.querySelectorAll('.te-table tbody tr').forEach(function(row) {
        const whyBtn = row.querySelector('.why-btn');
        if (!whyBtn) return;
        const valCell = row.querySelector('.val-col');
        if (!valCell) return;
        const labelCell = row.cells[0];
        const currentVal = valCell.innerText.trim();
        const label = labelCell ? labelCell.innerText.trim() : '';
        // derive field key from the row's onclick attribute
        const onclk = row.getAttribute('onclick') || '';
        const m = onclk.match(/'([a-zA-Z]+)'\.?\s*\)\s*$/);
        if (!m) return;
        const fieldKey = m[1];
        const editBtn = document.createElement('button');
        editBtn.type = 'button';
        editBtn.className = 'edit-btn';
        editBtn.innerHTML = '&#9998;';
        editBtn.title = 'Override value for backtesting';
        editBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            openEditModal(fieldKey, label, currentVal, '');
        });
        whyBtn.parentNode.insertBefore(editBtn, whyBtn.nextSibling);
    });

    function showWhy(title, key) {
        document.getElementById('modalIndTitle').innerText = "🔍 Why Did " + title + " Change?";
        document.getElementById('modalIndBody').innerText = indicatorReasons[key] ||
            "No simulation steps run yet, or parameter remained at baseline. Click '+1 Quarter' or '+1 Year' to compute causal attribution.";
        document.getElementById('reasonModalOverlay').style.display = 'flex';
    }

    function openTab(tabName) {
        document.getElementById('activeTabInput').value = tabName;
        document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
        document.querySelectorAll('.te-tab-btn').forEach(b => b.classList.remove('active'));
        document.getElementById('tab-' + tabName).classList.add('active');
        document.getElementById('btn-' + tabName).classList.add('active');
    }

    function toggleSection(btn) {
        btn.classList.toggle('collapsed');
        const body = btn.nextElementSibling;
        body.classList.toggle('hidden');
    }

    function updateDynLabel(year) {
        const next = parseInt(year) + 1;
        const el = document.getElementById('dynYearLabel');
        if (el) el.innerText = 'Seed from WB ' + year + ' actuals → Simulate → Compare vs WB ' + next + ' actuals';
    }

    function updateHistLabel(year) {
        const next = parseInt(year) + 1;
        const el = document.getElementById('histYearLabel');
        if (el) el.innerText = 'Loads WB ' + year + ' actuals → simulate → compare vs WB ' + next;
    }

    function syncLabel(id, val, unit) {
        document.getElementById(id).innerText = val + unit;
    }

    // Comprehensive Pros, Cons & Indicator Cascade Dictionary for all 15 Levers
    const policyKnowledgeBase = {
        corporateTaxRate: {
            name: "Corporate Tax Rate", labelId: "lbl_corp",
            up: {
                pros: "Increases direct Government Tax Revenues immediately, narrowing the Fiscal Deficit and lowering Government Debt-to-GDP.",
                cons: "Reduces post-tax corporate earnings, discourages private Capex (GFCF), slows FDI inflows, and depresses stock market valuations.",
                inds: "Leading: Manufacturing PMI ↓, Business Confidence ↓, Sensex ↓, Net Business Formations ↓ | Coincident: GDP Growth ↓, FDI ↓ | Lagging: Govt Budget ↑, Unemployment ↑"
            },
            down: {
                pros: "Boosts corporate profitability, ignites private factory Capex, attracts foreign FDI, and triggers a rally in BSE Sensex and Business Confidence.",
                cons: "Reduces short-term direct tax revenue collection, widening the Government Fiscal Deficit unless offset by higher GDP growth.",
                inds: "Leading: Manufacturing PMI ↑, Business Confidence ↑, Sensex ↑ | Coincident: GDP Growth ↑, Manufacturing GDP ↑, FDI ↑ | Lagging: Unemployment ↓, Govt Budget ↓"
            }
        },
        personalIncomeTaxRate: {
            name: "Personal Income Tax Rate", labelId: "lbl_pit",
            up: {
                pros: "Strengthens central government income tax collections, improving fiscal consolidation and cooling excessive urban demand inflation.",
                cons: "Cuts household disposable income, hurting Consumer Confidence, Retail Sales, Auto Sales, and Housing demand.",
                inds: "Leading: Consumer Confidence ↓, Consumer Durables ↓, Vehicle Sales ↓ | Coincident: Retail Sales ↓, GDP Growth ↓ | Lagging: Govt Budget ↑, CPI Inflation ↓"
            },
            down: {
                pros: "Directly boosts middle-class disposable income, accelerating Retail Sales, Consumer Durables, Passenger Vehicle Sales, and Housing Index.",
                cons: "Lowers personal tax receipts and can add mild demand-pull inflationary pressure.",
                inds: "Leading: Consumer Confidence ↑, Consumer Durables ↑, Housing Index ↑ | Coincident: Retail Sales ↑, GDP Growth ↑ | Lagging: Govt Budget ↓, CPI Inflation ↑"
            }
        },
        salesTaxGstRate: {
            name: "Sales Tax / GST Rate", labelId: "lbl_gst",
            up: {
                pros: "Generates rapid indirect tax revenue across goods and services, substantially improving Government Revenues and Fiscal Balance.",
                cons: "Directly pushes up retail prices (CPI & WPI inflation), reduces household purchasing power, and slows Services PMI and Retail Sales.",
                inds: "Leading: Services PMI ↓, Consumer Confidence ↓ | Coincident: Retail Sales ↓, Govt Revenues ↑ | Lagging: CPI Inflation ↑, Govt Budget ↑"
            },
            down: {
                pros: "Immediately cools CPI Inflation, lowers transportation and restaurant prices, and stimulates broad-based mass consumption and Services PMI.",
                cons: "Reduces monthly GST collections, widening the Government Budget deficit.",
                inds: "Leading: Services PMI ↑, Consumer Durables ↑, Vehicle Sales ↑ | Coincident: Retail Sales ↑, GDP Growth ↑ | Lagging: CPI Inflation ↓, Govt Budget ↓"
            }
        },
        socialSecurityRate: {
            name: "Social Security (EPFO) Rate", labelId: "lbl_soc",
            up: {
                pros: "Builds stronger long-term retirement savings and social safety nets for formal sector workers.",
                cons: "Increases payroll cost per employee for companies, slowing formal hiring and nudging Unemployment slightly higher.",
                inds: "Leading: Business Confidence ↓ | Coincident: Employment Rate ↓ | Lagging: Unemployment Rate ↑"
            },
            down: {
                pros: "Lowers formal payroll compliance costs for employers and increases take-home pay for workers, accelerating job creation.",
                cons: "Reduces long-term mandatory pension/provident fund accumulation.",
                inds: "Leading: Business Confidence ↑ | Coincident: Employment Rate ↑ | Lagging: Unemployment Rate ↓"
            }
        },
        withholdingTaxRate: {
            name: "Withholding Tax Rate", labelId: "lbl_wht",
            up: {
                pros: "Captures higher tax revenue at source on cross-border royalties, interest, and dividends.",
                cons: "Deters foreign institutional and direct investors (FDI), pressuring the INR currency.",
                inds: "Leading: FDI ↓ | Coincident: Currency USD/INR ↑ (depreciation), Govt Revenues ↑"
            },
            down: {
                pros: "Makes India more attractive to global capital and tech licensing, boosting FDI and strengthening the Rupee.",
                cons: "Modest reduction in withholding tax collections.",
                inds: "Leading: FDI ↑ | Coincident: Currency USD/INR ↓ (appreciation), Forex Reserves ↑"
            }
        },
        customsDutyRate: {
            name: "Customs & Import Duty", labelId: "lbl_cst",
            up: {
                pros: "Protects domestic manufacturers from cheap imports, narrows the Merchandise Trade Deficit, and raises tariff revenue.",
                cons: "Raises landed Import Prices and input costs for Indian factories, adding cost-push CPI/WPI inflation and hurting Terms of Trade.",
                inds: "Coincident: Imports ↓, Balance of Trade ↑, Import Prices ↑ | Lagging: CPI Inflation ↑"
            },
            down: {
                pros: "Lowers cost of imported machinery, electronics components, and crude derivatives, cooling inflation and integrating India into global supply chains.",
                cons: "Increases total imports, which can widen the Balance of Trade deficit in the short term.",
                inds: "Coincident: Import Prices ↓, Terms of Trade ↑, Imports ↑ | Lagging: CPI Inflation ↓"
            }
        },
        interestRate: {
            name: "RBI Repo Interest Rate", labelId: "lbl_repo",
            up: {
                pros: "Powerful brake on CPI Inflation, attracts foreign portfolio yields to strengthen the Rupee (USD/INR), and encourages bank deposit growth.",
                cons: "Increases Bank Lending Rates (EMIs), flattens/inverts the 10Y-2Y Yield Curve, slows Bank Loan Growth, Housing, and GDP Growth, and raises Govt debt service costs.",
                inds: "Leading: Yield Curve ↓, Sensex ↓, Housing Index ↓, PMI ↓ | Coincident: GDP Growth ↓, Bank Loan Growth ↓, INR Currency ↑ | Lagging: CPI Inflation ↓, Bank Lending Rate ↑"
            },
            down: {
                pros: "Lowers borrowing costs for businesses and homebuyers, steepens the Yield Curve, boosts Stock Market, Housing, Consumer Durables, and accelerates GDP Growth.",
                cons: "Can stoke demand-pull CPI Inflation and put mild depreciation pressure on the Rupee.",
                inds: "Leading: Yield Curve ↑, Sensex ↑, Housing Index ↑, PMI ↑ | Coincident: GDP Growth ↑, Bank Loan Growth ↑ | Lagging: CPI Inflation ↑, Bank Lending Rate ↓"
            }
        },
        cashReserveRatio: {
            name: "Cash Reserve Ratio (CRR)", labelId: "lbl_crr",
            up: {
                pros: "Drains excess liquidity from the banking system (slowing Money Supply M1/M3), helping RBI contain stubborn inflation.",
                cons: "Leaves banks with less lendable capital, pushing up Interbank and Bank Lending Rates and slowing credit-driven GDP growth.",
                inds: "Leading: Money Supply M1/M3 ↓ | Coincident: Interbank Rate ↑, GDP Growth ↓ | Lagging: CPI Inflation ↓"
            },
            down: {
                pros: "Injects immediate high-powered liquidity into commercial banks, expanding Money Supply M1/M3, lowering Interbank rates, and supporting credit growth.",
                cons: "Excess banking liquidity can feed into higher asset and consumer inflation over subsequent quarters.",
                inds: "Leading: Money Supply M1/M3 ↑ | Coincident: Interbank Rate ↓, GDP Growth ↑ | Lagging: CPI Inflation ↑"
            }
        },
        infraSpend: {
            name: "Infrastructure Budget (% of GDP)", labelId: "lbl_inf",
            up: {
                pros: "High fiscal multiplier! Boosts Construction & Utilities GDP, Gross Fixed Capital Formation, Manufacturing PMI, eases supply-chain bottlenecks (lowering food/logistics inflation), and creates massive employment.",
                cons: "Increases total Fiscal Expenditure, widening the Government Budget Deficit if not matched by tax buoyancy.",
                inds: "Leading: Manufacturing PMI ↑, Business Confidence ↑ | Coincident: GDP Growth ↑, Construction GDP ↑, Infra Output ↑ | Lagging: Unemployment ↓, CPI Inflation ↓, Govt Budget ↓"
            },
            down: {
                pros: "Saves immediate fiscal outlay, helping narrow the Government Budget Deficit in the short term.",
                cons: "Cuts Construction GDP and core industrial demand (steel, cement), worsens logistics bottlenecks (raising inflation), and slows job creation.",
                inds: "Leading: Manufacturing PMI ↓ | Coincident: GDP Growth ↓, Construction GDP ↓, Infra Output ↓ | Lagging: Unemployment ↑, CPI Inflation ↑"
            }
        },
        educationSpend: {
            name: "Education & Skills Budget (% of GDP)", labelId: "lbl_edu",
            up: {
                pros: "Expands Labor Force Participation, raises long-term human capital productivity, lowers CPI Education inflation for families, and improves institutional governance.",
                cons: "Increases government social expenditure, widening the near-term fiscal deficit before long-run productivity pays off.",
                inds: "Leading: Consumer Confidence ↑ | Coincident: Labor Force Participation ↑, GDP Growth ↑ | Lagging: Unemployment ↓, CPI Education ↓, Corruption Index ↑"
            },
            down: {
                pros: "Reduces immediate government budget outlays.",
                cons: "Weakens skill formation, lowers Labor Force Participation, increases household tuition burden (CPI Education), and raises structural unemployment.",
                inds: "Leading: Consumer Confidence ↓ | Coincident: Labor Force Participation ↓ | Lagging: Unemployment ↑, CPI Education ↑"
            }
        },
        healthSpend: {
            name: "Healthcare Budget (% of GDP)", labelId: "lbl_hlt",
            up: {
                pros: "Increases Hospital Beds per 1,000 people, raises the Public Health Readiness Index, lowers out-of-pocket medical stress (boosting Consumer Confidence), and supports workforce productivity.",
                cons: "Expands public expenditure, requiring fiscal space or tax revenues.",
                inds: "Leading: Consumer Confidence ↑ | Coincident: GDP Growth ↑ | Lagging: Hospital Beds ↑, Public Health Readiness ↑, Govt Budget ↓"
            },
            down: {
                pros: "Lowers government social sector spending.",
                cons: "Stagnates Hospital Beds capacity and Public Health Readiness, and increases household medical vulnerability (lowering Consumer Confidence).",
                inds: "Leading: Consumer Confidence ↓ | Lagging: Hospital Beds ↓, Public Health Readiness ↓"
            }
        },
        techSpend: {
            name: "Technology & Deep-Tech R&D (% of GDP)", labelId: "lbl_tch",
            up: {
                pros: "Catalyzes Net Business Formations (startups), attracts high-value FDI, boosts high-tech Exports, improves e-Governance (Corruption Index), and raises Services & Manufacturing PMI.",
                cons: "Adds to central R&D grant expenditure.",
                inds: "Leading: Net Business Formations ↑, FDI ↑, Services PMI ↑ | Coincident: GDP Growth ↑, Exports ↑ | Lagging: Corruption Index ↑"
            },
            down: {
                pros: "Trims R&D grant expenditure from the union budget.",
                cons: "Slows startup formation, reduces innovation competitiveness in exports, and dampens foreign tech investment.",
                inds: "Leading: Net Business Formations ↓, FDI ↓ | Coincident: GDP Growth ↓, Exports ↓"
            }
        },
        machinerySpend: {
            name: "Machinery & PLI Manufacturing (% of GDP)", labelId: "lbl_mch",
            up: {
                pros: "Supercharges Manufacturing PMI, Industrial Production (IIP), Gross Fixed Capital Formation, Merchandise Exports, FDI inflows, and factory employment.",
                cons: "Requires fiscal outlay for Production-Linked Incentive (PLI) subsidies.",
                inds: "Leading: Manufacturing PMI ↑, FDI ↑ | Coincident: Manufacturing GDP ↑, Industrial Production ↑, Exports ↑, Balance of Trade ↑ | Lagging: Unemployment ↓"
            },
            down: {
                pros: "Reduces industrial subsidy expenditure.",
                cons: "Slows domestic capital goods production, weakens Manufacturing PMI and Exports, and increases reliance on imported machinery.",
                inds: "Leading: Manufacturing PMI ↓, FDI ↓ | Coincident: Manufacturing GDP ↓, Industrial Production ↓, Balance of Trade ↓"
            }
        },
        itServicesSpend: {
            name: "IT & Digital Services (% of GDP)", labelId: "lbl_it",
            up: {
                pros: "Expands India's flagship Services PMI, generates massive Services Exports & Current Account surplus, boosts white-collar employment, and supports Forex Reserves.",
                cons: "Increases digital infrastructure and IT park incentives outlay.",
                inds: "Leading: Services PMI ↑, Composite PMI ↑ | Coincident: Exports ↑, Current Account ↑, GDP Growth ↑ | Lagging: Unemployment ↓"
            },
            down: {
                pros: "Reduces sectoral support spending.",
                cons: "Slows Services PMI, weakens India's Services trade surplus, and widens the Current Account Deficit.",
                inds: "Leading: Services PMI ↓ | Coincident: Current Account ↓, Exports ↓, GDP Growth ↓"
            }
        },
        tourismSpend: {
            name: "Tourism & Hospitality (% of GDP)", labelId: "lbl_trm",
            up: {
                pros: "Drives rapid growth in Foreign Tourist Arrivals, earns foreign exchange (improving Current Account), boosts Services PMI, and creates labor-intensive hospitality jobs.",
                cons: "Increases cultural and tourism promotion budget.",
                inds: "Leading: Services PMI ↑ | Coincident: Tourist Arrivals ↑, Current Account ↑, GDP Growth ↑ | Lagging: Unemployment ↓"
            },
            down: {
                pros: "Saves minor budget allocation.",
                cons: "Reduces international Tourist Arrivals, slows hospitality job creation, and reduces services forex earnings.",
                inds: "Leading: Services PMI ↓ | Coincident: Tourist Arrivals ↓, Current Account ↓ | Lagging: Unemployment ↑"
            }
        }
    };

    function syncLabel(id, val, suffix) {
        document.getElementById(id).innerText = val + suffix;
    }

    function onPolicyAdjust(key, inputEl, unit) {
        const info = policyKnowledgeBase[key];
        if (!info) return;

        const newVal = parseFloat(inputEl.value);
        const baseVal = parseFloat(inputEl.getAttribute('data-baseline'));
        document.getElementById(info.labelId).innerText = newVal + unit;

        const popup = document.getElementById('policyPopup');
        const dirBox = document.getElementById('popDirection');
        const prosBox = document.getElementById('popPros');
        const consBox = document.getElementById('popCons');
        const indsBox = document.getElementById('popInds');

        const diff = Math.round((newVal - baseVal) * 100) / 100;
        if (Math.abs(diff) < 0.001) {
            popup.style.display = 'none';
            return;
        }

        const isIncrease = diff > 0;
        const impact = isIncrease ? info.up : info.down;

        document.getElementById('popTitle').innerText = (isIncrease ? "🔼 Increasing " : "🔽 Decreasing ") + info.name;
        dirBox.innerHTML = "Changed from <strong>" + baseVal + unit + "</strong> to <strong>" + newVal + unit + "</strong> (" + (isIncrease ? "+" : "") + diff + unit + ")";
        prosBox.innerText = impact.pros;
        consBox.innerText = impact.cons;
        indsBox.innerText = impact.inds;

        popup.style.display = 'block';
    }
</script>

<!-- 4. CHECK WITH REALITY RESULTS MODAL -->
<%
    RealityCheckService.RealityCheckResult realityResult =
        (RealityCheckService.RealityCheckResult) request.getAttribute("realityResult");
    if (realityResult != null) {
%>
<div id="realityModalOverlay" onclick="this.style.display='none'" style="display:flex;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.75);z-index:10002;align-items:center;justify-content:center;">
    <div onclick="event.stopPropagation()" style="background:#0f172a;border:2px solid #0d9488;border-radius:10px;width:780px;max-width:95%;max-height:88vh;overflow-y:auto;padding:22px;box-shadow:0 15px 40px rgba(0,0,0,0.9);">
        <h3 style="margin-top:0;color:#0d9488;font-size:1.05rem;">&#128300; Check With Reality &mdash; FY 2015-16 &rarr; 2016-17 Backtest</h3>
        <div style="background:rgba(13,148,136,0.1);border-left:4px solid #0d9488;padding:10px 14px;border-radius:5px;margin-bottom:14px;font-size:0.83rem;line-height:1.5;">
            <%= realityResult.summary %>
        </div>
        <table style="width:100%;border-collapse:collapse;font-size:0.83rem;">
            <thead>
                <tr style="background:#1e293b;">
                    <th style="padding:8px;text-align:left;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">Indicator</th>
                    <th style="padding:8px;text-align:right;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">CSO/RBI Actual</th>
                    <th style="padding:8px;text-align:right;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">Simulated</th>
                    <th style="padding:8px;text-align:right;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">Error %</th>
                    <th style="padding:8px;text-align:center;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">Verdict</th>
                </tr>
            </thead>
            <tbody>
            <% for (RealityCheckService.RealityRow row : realityResult.rows) {
                String rowColor = row.verdict.equals("PASS") ? "rgba(34,197,94,0.08)" :
                                  row.verdict.equals("WARN") ? "rgba(251,191,36,0.08)" : "rgba(239,68,68,0.08)";
                String verdictColor = row.verdict.equals("PASS") ? "#22c55e" :
                                      row.verdict.equals("WARN") ? "#fbbf24" : "#ef4444";
                String verdictIcon  = row.verdict.equals("PASS") ? "&#10003; PASS" :
                                      row.verdict.equals("WARN") ? "&#9888; WARN" : "&#10007; FAIL";
            %>
                <tr style="background:<%= rowColor %>;border-bottom:1px solid #1e293b;">
                    <td style="padding:8px;color:#f8fafc;"><%= row.indicator %></td>
                    <td style="padding:8px;text-align:right;font-weight:700;color:#fff;"><%= String.format("%.2f", row.actual) %> <span style="color:#64748b;font-size:0.75rem;"><%= row.unit %></span></td>
                    <td style="padding:8px;text-align:right;font-weight:700;color:#38bdf8;"><%= String.format("%.2f", row.simulated) %> <span style="color:#64748b;font-size:0.75rem;"><%= row.unit %></span></td>
                    <td style="padding:8px;text-align:right;color:<%= verdictColor %>;"><%= String.format("%.1f", row.errorPct) %>%%</td>
                    <td style="padding:8px;text-align:center;font-weight:700;color:<%= verdictColor %>;font-size:0.8rem;"><%= verdictIcon %></td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <div style="margin-top:14px;font-size:0.75rem;color:#64748b;">
            PASS = error &le;5%% &bull; WARN = error &le;15%% &bull; FAIL = error &gt;15%% &bull; Sources: CSO First Advance Estimate, RBI Annual Report, CMIE PLFS, BSE
        </div>
        <div style="text-align:right;margin-top:14px;">
            <button onclick="document.getElementById('realityModalOverlay').style.display='none'" style="background:#475569;color:#fff;border:none;border-radius:5px;padding:7px 18px;font-weight:700;cursor:pointer;">Close</button>
        </div>
    </div>
</div>
<% } %>

<!-- 5. TIME MACHINE BACKTEST RESULT MODAL -->
<%
    String tmMessage = (String) request.getAttribute("tmMessage");
    BacktestResult timeMachineResult = (BacktestResult) request.getAttribute("timeMachineResult");
%>
<% if (tmMessage != null) { %>
<div id="tmToast" style="position:fixed;top:20px;left:50%;transform:translateX(-50%);background:#1e293b;border:1px solid #7c3aed;border-radius:8px;padding:12px 22px;z-index:10003;font-size:0.87rem;box-shadow:0 8px 24px rgba(0,0,0,0.7);max-width:600px;text-align:center;">
    <%= tmMessage %>
    <span onclick="document.getElementById('tmToast').style.display='none'" style="margin-left:14px;cursor:pointer;color:var(--muted);font-size:1rem;">&#x2715;</span>
</div>
<% } %>

<% if (timeMachineResult != null) { %>
<div id="tmModalOverlay" onclick="this.style.display='none'" style="display:flex;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.78);z-index:10002;align-items:center;justify-content:center;">
    <div onclick="event.stopPropagation()" style="background:#0f172a;border:2px solid #0891b2;border-radius:10px;width:820px;max-width:95%;max-height:88vh;overflow-y:auto;padding:22px;box-shadow:0 15px 40px rgba(0,0,0,0.9);">

        <h3 style="margin-top:0;color:#0891b2;font-size:1.05rem;">
            ⏳ Time Machine Backtest — <%= timeMachineResult.baselineYear %> &rarr; <%= timeMachineResult.targetYear %>
        </h3>

        <div style="background:rgba(8,145,178,0.1);border-left:4px solid #0891b2;padding:10px 14px;border-radius:5px;margin-bottom:16px;font-size:0.83rem;line-height:1.5;">
            <%= timeMachineResult.summary %>
        </div>

        <!-- Accuracy score bar -->
        <%
            double acc = timeMachineResult.meanAccuracyPct;
            String accColor = acc >= 90 ? "#22c55e" : acc >= 75 ? "#fbbf24" : "#ef4444";
        %>
        <div style="margin-bottom:16px;">
            <div style="display:flex;justify-content:space-between;font-size:0.78rem;color:var(--muted);margin-bottom:4px;">
                <span>Mean Model Accuracy</span>
                <span style="font-weight:700;color:<%= accColor %>;"><%= String.format("%.1f", acc) %>%</span>
            </div>
            <div style="background:#1e293b;border-radius:99px;height:8px;overflow:hidden;">
                <div style="height:100%;width:<%= Math.max(0, Math.min(100, acc)) %>%;background:<%= accColor %>;border-radius:99px;transition:width 0.5s;"></div>
            </div>
        </div>

        <!-- Per-indicator rows -->
        <table style="width:100%;border-collapse:collapse;font-size:0.84rem;">
            <thead>
                <tr style="background:#1e293b;">
                    <th style="padding:9px;text-align:left;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">Indicator</th>
                    <th style="padding:9px;text-align:right;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">World Bank Actual</th>
                    <th style="padding:9px;text-align:right;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">Simulated</th>
                    <th style="padding:9px;text-align:right;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">Abs. Error</th>
                    <th style="padding:9px;text-align:center;color:#94a3b8;font-size:0.75rem;text-transform:uppercase;">Verdict</th>
                </tr>
            </thead>
            <tbody>
            <% for (BacktestResult.IndicatorResult row : timeMachineResult.rows) {
                String rowBg   = "PASS".equals(row.verdict) ? "rgba(34,197,94,0.07)" :
                                 "WARN".equals(row.verdict) ? "rgba(251,191,36,0.07)" : "rgba(239,68,68,0.07)";
                String vColor  = "PASS".equals(row.verdict) ? "#22c55e" :
                                 "WARN".equals(row.verdict) ? "#fbbf24" : "#ef4444";
                String vIcon   = "PASS".equals(row.verdict) ? "&#10003; PASS" :
                                 "WARN".equals(row.verdict) ? "&#9888; WARN" : "&#10007; FAIL";
            %>
                <tr style="background:<%= rowBg %>;border-bottom:1px solid #1e293b;">
                    <td style="padding:9px;color:#f8fafc;">
                        <%= row.indicatorName %>
                        <div style="font-size:0.72rem;color:#64748b;margin-top:1px;"><%= row.note %></div>
                    </td>
                    <td style="padding:9px;text-align:right;font-weight:700;color:#fff;">
                        <%= String.format("%.2f", row.actualValue) %>
                        <span style="color:#64748b;font-size:0.75rem;"><%= row.unit %></span>
                    </td>
                    <td style="padding:9px;text-align:right;font-weight:700;color:#38bdf8;">
                        <%= String.format("%.2f", row.simulatedValue) %>
                        <span style="color:#64748b;font-size:0.75rem;"><%= row.unit %></span>
                    </td>
                    <td style="padding:9px;text-align:right;color:<%= vColor %>;font-weight:700;">
                        <%= String.format("%.1f", row.absoluteErrorPct) %>%%
                    </td>
                    <td style="padding:9px;text-align:center;font-weight:700;color:<%= vColor %>;font-size:0.8rem;">
                        <%= vIcon %>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>

        <div style="margin-top:14px;display:flex;justify-content:space-between;align-items:center;">
            <div style="font-size:0.73rem;color:#64748b;line-height:1.5;">
                Source: World Bank Open Data API v2 &bull;
                PASS&nbsp;&#8804;&nbsp;5%% error &bull; WARN&nbsp;&#8804;&nbsp;15%% &bull; FAIL&nbsp;&gt;&nbsp;15%%<br>
                Note: WB uses ILO modelled unemployment estimates which differ from CMIE/PLFS figures used in engine calibration.
            </div>
            <button onclick="document.getElementById('tmModalOverlay').style.display='none'"
                    style="background:#475569;color:#fff;border:none;border-radius:5px;padding:7px 18px;font-weight:700;cursor:pointer;white-space:nowrap;margin-left:16px;">
                Close
            </button>
        </div>
    </div>
</div>
<% } %>

<!-- 6. DYNAMIC WORLD BANK BACKTEST RESULT MODAL -->
<%
    String dynMessage = (String) request.getAttribute("dynMessage");
    BacktestResult dynamicBacktestResult = (BacktestResult) request.getAttribute("dynamicBacktestResult");
%>
<% if (dynMessage != null) { %>
<div id="dynToast" style="position:fixed;top:20px;left:50%;transform:translateX(-50%);background:#1e293b;border:1px solid #059669;border-radius:8px;padding:12px 22px;z-index:10003;font-size:0.87rem;box-shadow:0 8px 24px rgba(0,0,0,0.7);max-width:640px;text-align:center;">
    <%= dynMessage %>
    <span onclick="document.getElementById('dynToast').style.display='none'" style="margin-left:14px;cursor:pointer;color:var(--muted);font-size:1rem;">&#x2715;</span>
</div>
<% } %>

<% if (dynamicBacktestResult != null) {
    double dynAcc = dynamicBacktestResult.meanAccuracyPct;
    String dynAccColor = dynAcc >= 85 ? "#22c55e" : dynAcc >= 70 ? "#fbbf24" : "#ef4444";
%>
<div id="dynModalOverlay" onclick="this.style.display='none'" style="display:flex;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.82);z-index:10002;align-items:center;justify-content:center;">
  <div onclick="event.stopPropagation()" style="background:#0f172a;border:2px solid #059669;border-radius:12px;width:860px;max-width:96%;max-height:90vh;overflow-y:auto;padding:24px;box-shadow:0 20px 50px rgba(0,0,0,0.95);">

    <!-- Header -->
    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:16px;">
      <div>
        <h3 style="margin:0 0 4px;color:#059669;font-size:1.1rem;">
            🌍 Dynamic World Bank Backtest — <%= dynamicBacktestResult.baselineYear %> → <%= dynamicBacktestResult.targetYear %>
        </h3>
        <div style="font-size:0.78rem;color:var(--muted);">
            Seeded from real WB data for <strong style="color:var(--text);"><%= dynamicBacktestResult.baselineYear %></strong> &bull;
            Simulated 4 quarters &bull;
            Compared vs real WB data for <strong style="color:var(--text);"><%= dynamicBacktestResult.targetYear %></strong>
        </div>
      </div>
      <button onclick="document.getElementById('dynModalOverlay').style.display='none'" style="background:#334155;color:#fff;border:none;border-radius:5px;padding:6px 14px;cursor:pointer;font-weight:700;flex-shrink:0;margin-left:16px;">✕ Close</button>
    </div>

    <!-- Summary banner -->
    <div style="background:rgba(5,150,105,0.1);border-left:4px solid #059669;padding:10px 14px;border-radius:5px;margin-bottom:16px;font-size:0.82rem;line-height:1.5;">
        <%= dynamicBacktestResult.summary %>
    </div>

    <!-- Accuracy bar -->
    <div style="margin-bottom:16px;">
        <div style="display:flex;justify-content:space-between;font-size:0.78rem;color:var(--muted);margin-bottom:5px;">
            <span>Overall Model Accuracy (vs World Bank ground truth)</span>
            <span style="font-weight:800;font-size:0.95rem;color:<%= dynAccColor %>;"><%= String.format("%.1f", dynAcc) %>%</span>
        </div>
        <div style="background:#1e293b;border-radius:99px;height:10px;overflow:hidden;">
            <div style="height:100%;width:<%= Math.max(0, Math.min(100, dynAcc)) %>%;background:<%= dynAccColor %>;border-radius:99px;"></div>
        </div>
        <div style="display:flex;gap:16px;margin-top:6px;font-size:0.74rem;color:var(--muted);">
            <span>✅ PASS (≤5% err): <strong style="color:#22c55e;"><%= dynamicBacktestResult.passCount %></strong></span>
            <span>⚠️ WARN (≤15%): <strong style="color:#fbbf24;"><%= dynamicBacktestResult.warnCount %></strong></span>
            <span>❌ FAIL (>15%): <strong style="color:#ef4444;"><%= dynamicBacktestResult.failCount %></strong></span>
        </div>
    </div>

    <!-- Comparison table -->
    <table style="width:100%;border-collapse:collapse;font-size:0.83rem;">
        <thead>
            <tr style="background:#1e293b;">
                <th style="padding:9px 10px;text-align:left;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">Indicator</th>
                <th style="padding:9px 10px;text-align:right;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">WB Actual (<%= dynamicBacktestResult.targetYear %>)</th>
                <th style="padding:9px 10px;text-align:right;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">Simulated</th>
                <th style="padding:9px 10px;text-align:right;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">Diff</th>
                <th style="padding:9px 10px;text-align:right;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">Error</th>
                <th style="padding:9px 10px;text-align:center;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">Verdict</th>
            </tr>
        </thead>
        <tbody>
        <% for (BacktestResult.IndicatorResult row : dynamicBacktestResult.rows) {
            String rbg  = "PASS".equals(row.verdict) ? "rgba(34,197,94,0.06)"  :
                          "WARN".equals(row.verdict) ? "rgba(251,191,36,0.06)" : "rgba(239,68,68,0.06)";
            String rclr = "PASS".equals(row.verdict) ? "#22c55e" :
                          "WARN".equals(row.verdict) ? "#fbbf24" : "#ef4444";
            String rico = "PASS".equals(row.verdict) ? "✅" :
                          "WARN".equals(row.verdict) ? "⚠️" : "❌";
            double diff = row.simulatedValue - row.actualValue;
        %>
            <tr style="background:<%= rbg %>;border-bottom:1px solid #1e293b;">
                <td style="padding:9px 10px;">
                    <div style="color:#f8fafc;font-weight:600;"><%= row.indicatorName %></div>
                    <div style="font-size:0.7rem;color:#64748b;margin-top:1px;"><%= row.note %></div>
                </td>
                <td style="padding:9px 10px;text-align:right;font-weight:700;color:#fff;font-variant-numeric:tabular-nums;">
                    <%= String.format("%.2f", row.actualValue) %>
                    <span style="color:#475569;font-size:0.74rem;"> <%= row.unit %></span>
                </td>
                <td style="padding:9px 10px;text-align:right;font-weight:700;color:#38bdf8;font-variant-numeric:tabular-nums;">
                    <%= String.format("%.2f", row.simulatedValue) %>
                    <span style="color:#475569;font-size:0.74rem;"> <%= row.unit %></span>
                </td>
                <td style="padding:9px 10px;text-align:right;color:<%= diff >= 0 ? "#22c55e" : "#ef4444" %>;font-weight:600;font-variant-numeric:tabular-nums;">
                    <%= diff >= 0 ? "+" : "" %><%= String.format("%.2f", diff) %>
                </td>
                <td style="padding:9px 10px;text-align:right;color:<%= rclr %>;font-weight:700;">
                    <%= String.format("%.1f", row.absoluteErrorPct) %>%%
                </td>
                <td style="padding:9px 10px;text-align:center;font-size:1rem;" title="<%= row.verdict %>">
                    <%= rico %>
                </td>
            </tr>
        <% } %>
        </tbody>
    </table>

    <div style="margin-top:14px;font-size:0.72rem;color:#475569;line-height:1.6;">
        <strong style="color:#64748b;">Data sources:</strong>
        WB NY.GDP.MKTP.KD.ZG (GDP growth) &bull; FP.CPI.TOTL.ZG (CPI) &bull; SL.UEM.TOTL.ZS (unemployment, ILO modelled) &bull;
        PA.NUS.FCRF (FX) &bull; NE.EXP.GNFS.CD (exports) &bull; BN.CAB.XOKA.GD.ZS (current account) &bull;
        GC.DOD.TOTL.GD.ZS (govt debt) &bull; FR.INR.LEND (lending rate)<br>
        <em>Note: WB ILO unemployment estimates are typically 2–3pp higher than CMIE/PLFS figures — this creates structural divergence on that indicator.</em>
    </div>

  </div>
</div>
<% } %>

<!-- 7. LOAD YEAR MESSAGE TOAST -->
<%
    String loadYearMsg = (String) request.getAttribute("loadYearMsg");
%>
<% if (loadYearMsg != null) { %>
<div id="loadYearToast" style="position:fixed;top:20px;left:50%;transform:translateX(-50%);background:#1e293b;border:1px solid #b45309;border-radius:8px;padding:12px 22px;z-index:10003;font-size:0.87rem;box-shadow:0 8px 24px rgba(0,0,0,0.7);max-width:640px;text-align:center;">
    <%= loadYearMsg %>
    <span onclick="document.getElementById('loadYearToast').style.display='none'" style="margin-left:14px;cursor:pointer;color:var(--muted);font-size:1rem;">&#x2715;</span>
</div>
<% } %>

<!-- 8. DYNAMIC REALITY CHECK RESULT MODAL -->
<%
    RealityCheckService.RealityCheckResult dynRealityResult =
        (RealityCheckService.RealityCheckResult) request.getAttribute("dynRealityResult");
    Integer dynRealityYear = (Integer) request.getAttribute("dynRealityYear");
%>
<% if (dynRealityResult != null) {
    double drAcc = dynRealityResult.meanAccuracy;
    String drAccColor = drAcc >= 85 ? "#22c55e" : drAcc >= 70 ? "#fbbf24" : "#ef4444";
%>
<div id="dynRealityOverlay" onclick="this.style.display='none'" style="display:flex;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.82);z-index:10002;align-items:center;justify-content:center;">
  <div onclick="event.stopPropagation()" style="background:#0f172a;border:2px solid #b45309;border-radius:12px;width:860px;max-width:96%;max-height:90vh;overflow-y:auto;padding:24px;box-shadow:0 20px 50px rgba(0,0,0,0.95);">

    <!-- Header -->
    <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:16px;">
      <div>
        <h3 style="margin:0 0 4px;color:#b45309;font-size:1.1rem;">
            🔬 Dynamic Reality Check — Your Simulation vs WB Actuals <%= dynRealityYear != null ? "(" + dynRealityYear + ")" : "" %>
        </h3>
        <div style="font-size:0.78rem;color:var(--muted);">
            Compares your <strong style="color:var(--text);">simulated</strong> economy against real
            <strong style="color:var(--text);">World Bank</strong> data for the same year
        </div>
      </div>
      <button onclick="document.getElementById('dynRealityOverlay').style.display='none'" style="background:#334155;color:#fff;border:none;border-radius:5px;padding:6px 14px;cursor:pointer;font-weight:700;flex-shrink:0;margin-left:16px;">✕ Close</button>
    </div>

    <!-- Summary banner -->
    <div style="background:rgba(180,83,9,0.1);border-left:4px solid #b45309;padding:10px 14px;border-radius:5px;margin-bottom:16px;font-size:0.82rem;line-height:1.5;">
        <%= dynRealityResult.summary %>
    </div>

    <!-- Accuracy bar -->
    <div style="margin-bottom:16px;">
        <div style="display:flex;justify-content:space-between;font-size:0.78rem;color:var(--muted);margin-bottom:5px;">
            <span>Mean Model Accuracy (your simulation vs World Bank ground truth)</span>
            <span style="font-weight:800;font-size:0.95rem;color:<%= drAccColor %>;"><%= String.format("%.1f", drAcc) %>%</span>
        </div>
        <div style="background:#1e293b;border-radius:99px;height:10px;overflow:hidden;">
            <div style="height:100%;width:<%= Math.max(0, Math.min(100, drAcc)) %>%;background:<%= drAccColor %>;border-radius:99px;"></div>
        </div>
        <div style="display:flex;gap:16px;margin-top:6px;font-size:0.74rem;color:var(--muted);">
            <% long drPass = dynRealityResult.rows.stream().filter(r -> "PASS".equals(r.verdict)).count();
               long drWarn = dynRealityResult.rows.stream().filter(r -> "WARN".equals(r.verdict)).count();
               long drFail = dynRealityResult.rows.stream().filter(r -> "FAIL".equals(r.verdict)).count(); %>
            <span>✅ PASS (≤5% err): <strong style="color:#22c55e;"><%= drPass %></strong></span>
            <span>⚠️ WARN (≤15%): <strong style="color:#fbbf24;"><%= drWarn %></strong></span>
            <span>❌ FAIL (&gt;15%): <strong style="color:#ef4444;"><%= drFail %></strong></span>
        </div>
    </div>

    <!-- Comparison table -->
    <table style="width:100%;border-collapse:collapse;font-size:0.83rem;">
        <thead>
            <tr style="background:#1e293b;">
                <th style="padding:9px 10px;text-align:left;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">Indicator</th>
                <th style="padding:9px 10px;text-align:right;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">WB Actual</th>
                <th style="padding:9px 10px;text-align:right;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">Your Simulation</th>
                <th style="padding:9px 10px;text-align:right;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">Error %</th>
                <th style="padding:9px 10px;text-align:center;color:#94a3b8;font-size:0.72rem;text-transform:uppercase;border-bottom:1px solid #334155;">Verdict</th>
            </tr>
        </thead>
        <tbody>
        <% for (RealityCheckService.RealityRow row : dynRealityResult.rows) {
            String drbg = "PASS".equals(row.verdict) ? "rgba(34,197,94,0.06)"  :
                          "WARN".equals(row.verdict) ? "rgba(251,191,36,0.06)" : "rgba(239,68,68,0.06)";
            String drvc = "PASS".equals(row.verdict) ? "#22c55e" :
                          "WARN".equals(row.verdict) ? "#fbbf24" : "#ef4444";
            String drvi = "PASS".equals(row.verdict) ? "&#10003; PASS" :
                          "WARN".equals(row.verdict) ? "&#9888; WARN" : "&#10007; FAIL";
        %>
            <tr style="background:<%= drbg %>;border-bottom:1px solid #1e293b;">
                <td style="padding:9px 10px;color:#f8fafc;font-weight:600;"><%= row.indicator %></td>
                <td style="padding:9px 10px;text-align:right;font-weight:700;color:#fff;font-variant-numeric:tabular-nums;">
                    <%= String.format("%.2f", row.actual) %>
                    <span style="color:#475569;font-size:0.74rem;"> <%= row.unit %></span>
                </td>
                <td style="padding:9px 10px;text-align:right;font-weight:700;color:#38bdf8;font-variant-numeric:tabular-nums;">
                    <%= String.format("%.2f", row.simulated) %>
                    <span style="color:#475569;font-size:0.74rem;"> <%= row.unit %></span>
                </td>
                <td style="padding:9px 10px;text-align:right;color:<%= drvc %>;font-weight:700;">
                    <%= String.format("%.1f", row.errorPct) %>%%
                </td>
                <td style="padding:9px 10px;text-align:center;font-weight:700;color:<%= drvc %>;font-size:0.8rem;">
                    <%= drvi %>
                </td>
            </tr>
        <% } %>
        </tbody>
    </table>

    <div style="margin-top:14px;font-size:0.72rem;color:#475569;line-height:1.6;">
        PASS = error &le;5%% &bull; WARN = error &le;15%% &bull; FAIL &gt;15%% &bull;
        Source: World Bank Open Data API v2 &bull;
        ILO unemployment estimates may be 2–3pp higher than CMIE/PLFS, creating structural divergence on that indicator.
    </div>

    <div style="text-align:right;margin-top:14px;">
        <button onclick="document.getElementById('dynRealityOverlay').style.display='none'" style="background:#475569;color:#fff;border:none;border-radius:5px;padding:7px 18px;font-weight:700;cursor:pointer;">Close</button>
    </div>
  </div>
</div>
<% } %>

<!-- 3. EDIT INDICATOR MODAL (manual override for backtesting) -->
<div id="editModalOverlay" onclick="closeEditModal()">
    <div class="edit-modal" onclick="event.stopPropagation()">
        <h3>✏️ Override Indicator Value</h3>
        <div id="editModalDesc" style="font-size:0.83rem;color:var(--muted);margin-bottom:4px;"></div>
        <input type="number" id="editModalInput" step="any">
        <div class="hint">⚠️ This directly seeds the simulator state. Use historical actuals for backtesting, then click +1 Quarter / +1 Year to simulate forward.</div>
        <div class="btn-row">
            <button class="btn-cancel" onclick="closeEditModal()">Cancel</button>
            <button class="btn-save" onclick="submitEditIndicator()">Apply Override</button>
        </div>
    </div>
</div>
<!-- Hidden form for edit-indicator POST -->
<form id="editIndicatorForm" action="<%= request.getContextPath() %>/simulate" method="post" style="display:none">
    <input type="hidden" name="action" value="editIndicator">
    <input type="hidden" id="editActiveTab" name="activeTab" value="<%= activeTab %>">
    <input type="hidden" id="editFieldName" name="fieldName" value="">
    <input type="hidden" id="editFieldValue" name="fieldValue" value="">
</form>

<script>
    // ── Edit Indicator Modal ──────────────────────────────────────────────────
    function openEditModal(fieldKey, label, currentVal, unit) {
        document.getElementById('editModalDesc').innerText = label + '  (current: ' + currentVal + (unit ? ' ' + unit : '') + ')';
        document.getElementById('editModalInput').value = currentVal;
        document.getElementById('editModalInput').dataset.fieldKey = fieldKey;
        document.getElementById('editModalOverlay').style.display = 'flex';
        setTimeout(() => document.getElementById('editModalInput').focus(), 80);
    }
    function closeEditModal() {
        document.getElementById('editModalOverlay').style.display = 'none';
    }
    function submitEditIndicator() {
        const input = document.getElementById('editModalInput');
        const val = input.value.trim();
        if (val === '' || isNaN(parseFloat(val))) { alert('Please enter a valid number.'); return; }
        document.getElementById('editFieldName').value = input.dataset.fieldKey;
        document.getElementById('editFieldValue').value = val;
        document.getElementById('editActiveTab').value = document.getElementById('activeTabInput').value;
        document.getElementById('editIndicatorForm').submit();
    }
    document.getElementById('editModalInput').addEventListener('keydown', function(e) {
        if (e.key === 'Enter') submitEditIndicator();
        if (e.key === 'Escape') closeEditModal();
    });
</script>

</body>
</html>