# 🇮🇳 India Macroeconomic & Policy Simulator

An interactive **Java & JSP web application** that models India's macroeconomic transmission pipeline. Built with a comprehensive dataset mirroring **Trading Economics India**, this simulator allows policymakers, students, and investors to adjust fiscal, monetary, and sectoral levers and observe their real-time impacts across **Leading**, **Coincident**, and **Lagging** economic indicators.

---

## 🌟 Key Features

1. **Complete Trading Economics India 11-Tab Indicator Suite:**
   * Includes *Overview, GDP, Labour, Prices, Money, Trade, Government, Business, Consumer, Housing, and Health* data categories.
2. **Three-Tier Macroeconomic Transmission Pipeline:**
   * **Leading Indicators:** Stock Market (Sensex), Yield Curve (10Y-2Y), PMIs (Mfg, Svc, Comp), Business & Consumer Confidence, Consumer Durables, Housing Index, Net Business Formations.
   * **Coincident Indicators:** Quarterly/Annual GDP Growth, Employment Rate, Currency (USD/INR), Balance of Trade, Current Account, Industrial Production.
   * **Lagging Indicators:** CPI Inflation, Unemployment Rate, Government Budget Deficit, Government Debt-to-GDP, Bank Lending Rates.
3. **Dynamic Policy Levers & Live Pros/Cons Modal:**
   * **Taxes & Tariffs:** Corporate Tax, Personal Income Tax, GST/Sales Tax, Social Security (EPFO), Withholding Tax, and Customs Import Duty.
   * **RBI Monetary Levers:** Repo Interest Rate and Cash Reserve Ratio (CRR).
   * **Sectoral Budgets (% of GDP):** Infrastructure, Education, Healthcare, Tech R&D, Machinery (PLI), IT/Services, and Tourism.
   * *Moving any slider instantly triggers a live **Pros, Cons & Indicator Cascade** popup.*
4. **Post-Simulation Causal Attribution Engine ("Why It Changed"):**
   * After advancing time (+1 Quarter or +1 Year), every indicator displays its quarterly change ($\Delta$). Clicking any row opens a modal detailing the exact mathematical and economic reason why that metric increased or decreased.
5. **Historical Backtest (`checkWithReality()`):**
   * Pre-loaded with actual FY 2015–16 baseline data to simulate the FY 2016–17 policy regime (RBI Repo rate cuts, rail/road capex, and the M1 liquidity shock), verifying model accuracy against official CSO / RBI actuals with over 98% mean accuracy.

---

## 📂 Project Directory Structure

```text
IndiaEconomySimulator/
├── src/main/java/com/india/econsim/
│   ├── model/
│   │   └── EconomyState.java         # Holds all indicators, tax rates, sector budgets & deltas
│   ├── service/
│   │   ├── SimulationEngine.java     # Core macroeconomic causal cascade math
│   │   └── RealityCheckService.java  # Historical backtest engine (FY 2015-16 -> 2016-17)
│   └── controller/
│       └── SimulatorServlet.java     # Servlet handling HTTP POST/GET and session state
└── src/main/webapp/
    ├── WEB-INF/
    │   └── web.xml                   # Servlet deployment descriptor
    └── simulator.jsp                 # Interactive multi-tab UI dashboard & modals
