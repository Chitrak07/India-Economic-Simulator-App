<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.india.econsim.model.EconomyState" %>
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
        }
        .top-bar h1 { margin: 0; font-size: 1.32rem; color: var(--accent); }
        .status-pill {
            background: var(--panel); border: 1px solid var(--blue);
            padding: 6px 14px; border-radius: 6px; font-weight: 700; color: var(--blue); font-size: 0.88rem;
        }
        .summary-banner {
            background: rgba(56, 189, 248, 0.1); border-left: 4px solid var(--blue);
            padding: 10px 14px; border-radius: 6px; margin-bottom: 16px; font-size: 0.86rem; line-height: 1.4;
        }
        .main-grid { display: grid; grid-template-columns: 360px 1fr; gap: 18px; align-items: start; }
        .card { background: var(--panel); border: 1px solid var(--border); border-radius: 8px; padding: 14px; }
        .card h3 {
            margin: 0 0 10px 0; font-size: 0.9rem; text-transform: uppercase;
            color: var(--blue); border-bottom: 1px solid var(--border); padding-bottom: 6px;
        }
        .slider-row { margin-bottom: 9px; }
        .slider-row label { display: flex; justify-content: space-between; font-size: 0.8rem; margin-bottom: 2px; }
        .slider-row input[type=range] { width: 100%; accent-color: var(--accent); cursor: pointer; height: 5px; }
        .btn-group { display: flex; gap: 8px; margin-top: 12px; }
        .btn-group button {
            flex: 1; padding: 9px; border: none; border-radius: 5px;
            font-weight: 700; cursor: pointer; color: #fff; font-size: 0.82rem;
        }
        .b-qtr { background: var(--accent); }
        .b-yr { background: #2563eb; }
        .b-rst { background: #475569; }

        /* Trading Economics Tab Bar */
        .te-tabs {
            display: flex; flex-wrap: wrap; background: #e2e8f0; border-radius: 6px 6px 0 0;
            overflow: hidden; border: 1px solid var(--border);
        }
        .te-tab-btn {
            background: #e2e8f0; color: #1e293b; border: none; border-right: 1px solid #cbd5e1;
            padding: 10px 14px; font-size: 0.84rem; font-weight: 600; cursor: pointer;
        }
        .te-tab-btn.active { background: #ffffff; color: #0f172a; font-weight: 700; box-shadow: inset 0 -3px 0 var(--accent); }
        .tab-pane { display: none; background: var(--panel); border: 1px solid var(--border); border-top: none; border-radius: 0 0 8px 8px; padding: 14px; }
        .tab-pane.active { display: block; }

        .te-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
        .te-table th {
            text-align: left; padding: 9px; background: #0f172a; color: var(--muted);
            font-size: 0.76rem; text-transform: uppercase; border-bottom: 1px solid var(--border);
        }
        .te-table td { padding: 9px; border-bottom: 1px solid var(--border); }
        .te-table tr { cursor: pointer; transition: background 0.12s; }
        .te-table tr:hover { background: rgba(56, 189, 248, 0.08); }
        .val-col { font-weight: 700; color: #fff; }
        .why-btn {
            background: #334155; color: var(--blue); border: 1px solid var(--blue);
            border-radius: 4px; padding: 2px 8px; font-size: 0.73rem; cursor: pointer; font-weight: 600;
        }
        .badge { font-size: 0.68rem; padding: 2px 6px; border-radius: 4px; font-weight: 700; display: inline-block; }
        .b-lead { background: rgba(56, 189, 248, 0.15); color: var(--blue); border: 1px solid var(--blue); }
        .b-coin { background: rgba(34, 197, 94, 0.15); color: var(--green); border: 1px solid var(--green); }
        .b-lag  { background: rgba(192, 132, 252, 0.15); color: var(--purple); border: 1px solid var(--purple); }

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
    </style>
</head>
<body>

<div class="top-bar">
    <div>
        <h1>🇮🇳 India Macroeconomic Simulator — Live Pros/Cons & Causal Engine</h1>
        <small style="color:var(--muted);">Move any slider to see a Live Pros/Cons Popup &bull; Click any Indicator Row to see why it increased or decreased</small>
    </div>
    <div class="status-pill">
        Period: <%= econ.getYear() %> Q<%= econ.getQuarter() %> &bull; GDP: $<%= econ.getGdpUsdBillion() %>B &bull; Growth: <%= econ.getGdpAnnualGrowthRate() %>%
    </div>
</div>

<div class="summary-banner">
    <strong>📊 Executive Turn Diagnostic:</strong> <%= econ.getLastTurnSummary() %>
</div>

<div class="main-grid">
    <!-- LEFT SIDEBAR: POLICY SLIDERS WITH ONINPUT PROS/CONS POPUP TRIGGER -->
    <form action="<%= request.getContextPath() %>/simulate" method="post" class="card">
        <input type="hidden" id="activeTabInput" name="activeTab" value="<%= activeTab %>">

        <h3>Taxes & Tariffs Levers</h3>
        <div class="slider-row">
            <label><span>Corporate Tax Rate</span><strong id="lbl_corp"><%= econ.getCorporateTaxRate() %>%</strong></label>
            <input type="range" name="corporateTaxRate" min="15" max="45" step="0.5"
                   data-baseline="<%= econ.getCorporateTaxRate() %>" value="<%= econ.getCorporateTaxRate() %>"
                   oninput="onPolicyAdjust('corporateTaxRate', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Personal Income Tax Rate</span><strong id="lbl_pit"><%= econ.getPersonalIncomeTaxRate() %>%</strong></label>
            <input type="range" name="personalIncomeTaxRate" min="15" max="50" step="0.5"
                   data-baseline="<%= econ.getPersonalIncomeTaxRate() %>" value="<%= econ.getPersonalIncomeTaxRate() %>"
                   oninput="onPolicyAdjust('personalIncomeTaxRate', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Sales Tax / GST Rate</span><strong id="lbl_gst"><%= econ.getSalesTaxGstRate() %>%</strong></label>
            <input type="range" name="salesTaxGstRate" min="8" max="28" step="0.5"
                   data-baseline="<%= econ.getSalesTaxGstRate() %>" value="<%= econ.getSalesTaxGstRate() %>"
                   oninput="onPolicyAdjust('salesTaxGstRate', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Social Security Rate (EPFO)</span><strong id="lbl_soc"><%= econ.getSocialSecurityRate() %>%</strong></label>
            <input type="range" name="socialSecurityRate" min="10" max="35" step="0.5"
                   data-baseline="<%= econ.getSocialSecurityRate() %>" value="<%= econ.getSocialSecurityRate() %>"
                   oninput="onPolicyAdjust('socialSecurityRate', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Withholding Tax Rate</span><strong id="lbl_wht"><%= econ.getWithholdingTaxRate() %>%</strong></label>
            <input type="range" name="withholdingTaxRate" min="5" max="35" step="0.5"
                   data-baseline="<%= econ.getWithholdingTaxRate() %>" value="<%= econ.getWithholdingTaxRate() %>"
                   oninput="onPolicyAdjust('withholdingTaxRate', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Customs & Import Duty</span><strong id="lbl_cst"><%= econ.getCustomsDutyRate() %>%</strong></label>
            <input type="range" name="customsDutyRate" min="0" max="30" step="0.5"
                   data-baseline="<%= econ.getCustomsDutyRate() %>" value="<%= econ.getCustomsDutyRate() %>"
                   oninput="onPolicyAdjust('customsDutyRate', this, '%')">
        </div>

        <h3 style="margin-top:12px;">RBI Monetary Levers</h3>
        <div class="slider-row">
            <label><span>RBI Repo Interest Rate</span><strong id="lbl_repo"><%= econ.getInterestRate() %>%</strong></label>
            <input type="range" name="interestRate" min="3.0" max="10.0" step="0.25"
                   data-baseline="<%= econ.getInterestRate() %>" value="<%= econ.getInterestRate() %>"
                   oninput="onPolicyAdjust('interestRate', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Cash Reserve Ratio (CRR)</span><strong id="lbl_crr"><%= econ.getCashReserveRatio() %>%</strong></label>
            <input type="range" name="cashReserveRatio" min="2.0" max="8.0" step="0.25"
                   data-baseline="<%= econ.getCashReserveRatio() %>" value="<%= econ.getCashReserveRatio() %>"
                   oninput="onPolicyAdjust('cashReserveRatio', this, '%')">
        </div>

        <h3 style="margin-top:12px;">Sectoral Budgets (% of GDP)</h3>
        <div class="slider-row">
            <label><span>Infrastructure Capex</span><strong id="lbl_inf"><%= econ.getInfraSpend() %>%</strong></label>
            <input type="range" name="infraSpend" min="1.0" max="7.0" step="0.1"
                   data-baseline="<%= econ.getInfraSpend() %>" value="<%= econ.getInfraSpend() %>"
                   oninput="onPolicyAdjust('infraSpend', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Education & Skills</span><strong id="lbl_edu"><%= econ.getEducationSpend() %>%</strong></label>
            <input type="range" name="educationSpend" min="1.0" max="6.0" step="0.1"
                   data-baseline="<%= econ.getEducationSpend() %>" value="<%= econ.getEducationSpend() %>"
                   oninput="onPolicyAdjust('educationSpend', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Healthcare & Hospitals</span><strong id="lbl_hlt"><%= econ.getHealthSpend() %>%</strong></label>
            <input type="range" name="healthSpend" min="0.8" max="5.0" step="0.1"
                   data-baseline="<%= econ.getHealthSpend() %>" value="<%= econ.getHealthSpend() %>"
                   oninput="onPolicyAdjust('healthSpend', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Technology & Deep-Tech R&D</span><strong id="lbl_tch"><%= econ.getTechSpend() %>%</strong></label>
            <input type="range" name="techSpend" min="0.2" max="3.5" step="0.05"
                   data-baseline="<%= econ.getTechSpend() %>" value="<%= econ.getTechSpend() %>"
                   oninput="onPolicyAdjust('techSpend', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Machinery & PLI Capital Goods</span><strong id="lbl_mch"><%= econ.getMachinerySpend() %>%</strong></label>
            <input type="range" name="machinerySpend" min="0.5" max="4.0" step="0.1"
                   data-baseline="<%= econ.getMachinerySpend() %>" value="<%= econ.getMachinerySpend() %>"
                   oninput="onPolicyAdjust('machinerySpend', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>IT & Digital Services</span><strong id="lbl_it"><%= econ.getItServicesSpend() %>%</strong></label>
            <input type="range" name="itServicesSpend" min="0.3" max="3.5" step="0.1"
                   data-baseline="<%= econ.getItServicesSpend() %>" value="<%= econ.getItServicesSpend() %>"
                   oninput="onPolicyAdjust('itServicesSpend', this, '%')">
        </div>
        <div class="slider-row">
            <label><span>Tourism & Hospitality</span><strong id="lbl_trm"><%= econ.getTourismSpend() %>%</strong></label>
            <input type="range" name="tourismSpend" min="0.1" max="2.5" step="0.1"
                   data-baseline="<%= econ.getTourismSpend() %>" value="<%= econ.getTourismSpend() %>"
                   oninput="onPolicyAdjust('tourismSpend', this, '%')">
        </div>

        <div class="btn-group">
            <button type="submit" name="action" value="advanceQuarter" class="b-qtr">+1 Quarter</button>
            <button type="submit" name="action" value="advanceYear" class="b-yr">+1 Year</button>
            <button type="submit" name="action" value="reset" class="b-rst">Reset</button>
        </div>
    </form>

    <!-- RIGHT AREA: ALL 11 TRADING ECONOMICS TABS WITH DELTA & CAUSAL CLICK -->
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
                <thead><tr><th>Overview Indicator</th><th>Value</th><th>Change (&Delta;)</th><th>Unit</th><th>Class</th><th>Why?</th></tr></thead>
                <tbody>
                    <tr onclick="showWhy('Currency (USD/INR)', 'currencyUsdInr')"><td>Currency (USD/INR)</td><td class="val-col"><%= econ.getCurrencyUsdInr() %></td><td><%= fmtDelta(econ, "currencyUsdInr", true) %></td><td>INR</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Stock Market (BSE Sensex)', 'stockMarketSensex')"><td>Stock Market (BSE Sensex)</td><td class="val-col"><%= econ.getStockMarketSensex() %></td><td><%= fmtDelta(econ, "stockMarketSensex", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP Growth Rate QoQ', 'gdpGrowthRate')"><td>GDP Growth Rate</td><td class="val-col"><%= econ.getGdpGrowthRate() %></td><td><%= fmtDelta(econ, "gdpGrowthRate", false) %></td><td>% QoQ</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('GDP Annual Growth Rate YoY', 'gdpAnnualGrowthRate')"><td>GDP Annual Growth Rate</td><td class="val-col"><%= econ.getGdpAnnualGrowthRate() %></td><td><%= fmtDelta(econ, "gdpAnnualGrowthRate", false) %></td><td>% YoY</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Unemployment Rate', 'unemploymentRate')"><td>Unemployment Rate</td><td class="val-col"><%= econ.getUnemploymentRate() %></td><td><%= fmtDelta(econ, "unemploymentRate", true) %></td><td>%</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('CPI Inflation Rate YoY', 'inflationRate')"><td>Inflation Rate</td><td class="val-col"><%= econ.getInflationRate() %></td><td><%= fmtDelta(econ, "inflationRate", true) %></td><td>% YoY</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('RBI Repo Interest Rate', 'interbankRate')"><td>Interest Rate (RBI Repo)</td><td class="val-col"><%= econ.getInterestRate() %></td><td>—</td><td>%</td><td><span class="badge b-lag">Policy</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Cash Reserve Ratio (CRR)', 'moneySupplyM1')"><td>Cash Reserve Ratio</td><td class="val-col"><%= econ.getCashReserveRatio() %></td><td>—</td><td>%</td><td><span class="badge b-lag">Policy</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Balance of Trade', 'balanceOfTrade')"><td>Balance of Trade</td><td class="val-col"><%= econ.getBalanceOfTrade() %></td><td><%= fmtDelta(econ, "balanceOfTrade", false) %></td><td>USD Billion</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Current Account', 'currentAccount')"><td>Current Account</td><td class="val-col"><%= econ.getCurrentAccount() %></td><td><%= fmtDelta(econ, "currentAccount", false) %></td><td>USD Million</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Current Account to GDP', 'currentAccountToGdp')"><td>Current Account to GDP</td><td class="val-col"><%= econ.getCurrentAccountToGdp() %></td><td><%= fmtDelta(econ, "currentAccountToGdp", false) %></td><td>% of GDP</td><td><span class="badge b-coin">Coincident</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Government Debt to GDP', 'governmentDebtToGdp')"><td>Government Debt to GDP</td><td class="val-col"><%= econ.getGovernmentDebtToGdp() %></td><td><%= fmtDelta(econ, "governmentDebtToGdp", true) %></td><td>% of GDP</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Government Budget Balance', 'governmentBudget')"><td>Government Budget</td><td class="val-col"><%= econ.getGovernmentBudget() %></td><td><%= fmtDelta(econ, "governmentBudget", false) %></td><td>% of GDP</td><td><span class="badge b-lag">Lagging</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Business Confidence Index', 'businessConfidence')"><td>Business Confidence</td><td class="val-col"><%= econ.getBusinessConfidence() %></td><td><%= fmtDelta(econ, "businessConfidence", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Manufacturing PMI', 'manufacturingPMI')"><td>Manufacturing PMI</td><td class="val-col"><%= econ.getManufacturingPMI() %></td><td><%= fmtDelta(econ, "manufacturingPMI", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Services PMI', 'servicesPMI')"><td>Services PMI</td><td class="val-col"><%= econ.getServicesPMI() %></td><td><%= fmtDelta(econ, "servicesPMI", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
                    <tr onclick="showWhy('Consumer Confidence Index', 'consumerConfidence')"><td>Consumer Confidence</td><td class="val-col"><%= econ.getConsumerConfidence() %></td><td><%= fmtDelta(econ, "consumerConfidence", false) %></td><td>points</td><td><span class="badge b-lead">Leading</span></td><td><button type="button" class="why-btn">Why?</button></td></tr>
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

</body>
</html>