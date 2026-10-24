package com.india.econsim.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("unused") // Public model API — getters/setters consumed via JSP EL & reflection
public class EconomyState implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    private int year = 2025;
    private int quarter = 4;  // Q4 FY2025-26 (Jan-Mar 2026)

    // --- 1. POLICY LEVERS: TAXES, MONETARY & SECTORAL BUDGETS ---
    // Sources: RBI MPC Apr 2025, Union Budget 2025-26
    private double corporateTaxRate = 25.17;         // % unchanged post-2019
    private double personalIncomeTaxRate = 15.50;     // % effective avg rate (new regime, CBDT FY2025-26)
    private double salesTaxGstRate = 18.00;           // % standard GST rate
    private double socialSecurityRate = 24.00;        // % EPFO employer+employee
    private double withholdingTaxRate = 20.00;        // % TDS on dividends/royalties
    private double customsDutyRate = 10.00;           // % basic customs duty avg

    private double interestRate = 6.00;               // % RBI Repo (post Apr-2025 cut from 6.25)
    private double reverseRepoRate = 3.35;            // % SDF rate
    private double cashReserveRatio = 4.00;           // % (restored from 3% in Feb 2025)
    private double brentCrudePrice = 73.0;            // USD/barrel avg FY2025-26

    // Union Budget 2025-26 allocations (% of GDP)
    private double educationSpend = 2.90;             // % GDP (BE ₹1.28L cr)
    private double healthSpend = 2.10;                // % GDP (BE ₹98k cr)
    private double infraSpend = 4.10;                 // % GDP capex ₹11.21L cr (record)
    private double techSpend = 0.90;                  // % GDP (PLI + Digital India)
    private double machinerySpend = 1.60;             // % GDP (PLI schemes)
    private double itServicesSpend = 1.20;            // % GDP
    private double tourismSpend = 0.55;               // % GDP

    // --- 2. ALL 11 TAB INDICATORS ---
    // GDP Tab — NSO Second Advance Estimate FY2025-26 (Feb 2026)
    private double gdpGrowthRate = 1.60;              // % QoQ Q3 FY26
    private double gdpAnnualGrowthRate = 6.50;        // % real YoY NSO 2nd AE FY2025-26
    private double nominalGdpGrowthRate = 9.70;       // % nominal YoY (real + GDP deflator ~3.2%)
    private double fullYearGdpGrowth = 6.50;          // % same
    private double gdpUsdBillion = 4187.0;            // USD bn (₹353L cr / 84.3)
    private double gdpConstantPrices = 184.88;        // ₹ Lakh Cr at 2011-12 prices
    private double grossFixedCapitalFormation = 55.20;// ₹ Lakh Cr (GFCF)
    private double gdpPerCapita = 2947.0;             // USD (IMF WEO Apr 2025)
    private double gdpPerCapitaPpp = 11100.0;         // USD PPP (IMF WEO Apr 2025)
    private double gnpInrCrore = 34800000.0;          // ₹ Crore (GNP at market prices)
    private double gdpFromAgriculture = 20.20;        // ₹ Lakh Cr (GVA share ~17%)
    private double gdpFromManufacturing = 35.80;      // ₹ Lakh Cr (GVA share ~17%)
    private double gdpFromConstruction = 14.50;       // ₹ Lakh Cr
    private double gdpFromPublicAdmin = 22.10;        // ₹ Lakh Cr
    private double gdpFromUtilities = 5.20;           // ₹ Lakh Cr
    private double gdpFromMining = 4.10;              // ₹ Lakh Cr

    // Labour Tab — PLFS Annual 2024-25, CMIE Apr 2025
    private double unemploymentRate = 7.80;           // % CMIE Apr 2025
    private double employmentRate = 92.20;            // %
    private double laborForceParticipation = 42.70;   // % PLFS 2024-25
    private double populationMillion = 1441.0;        // mn (UN 2025)
    private double minimumWages = 400.00;             // ₹/day (central sphere floor wage)
    private double monthlyWages = 26000.0;            // ₹/month avg formal sector

    // Prices Tab — MoSPI CPI Apr 2025
    private double inflationRate = 3.34;              // % CPI YoY Apr 2025 (4-yr low)
    private double cpiPoints = 193.70;                // CPI index (2012=100) Apr 2025
    private double consumerFoodPriceIndex = 188.40;   // CFPI Apr 2025
    private double cpiHousingUtilities = 178.20;      // CPI Housing Apr 2025
    private double cpiTransportation = 165.30;        // CPI Transport Apr 2025
    private double cpiEducation = 210.50;             // CPI Education Apr 2025
    private double cpiRestaurants = 205.80;           // CPI Restaurants Apr 2025
    private double wholesalePriceIndex = 115.80;      // WPI Apr 2025 (2011-12=100)
    private double wpiFoodYoY = 1.40;                 // % WPI Food YoY Apr 2025
    private double producerPrices = 115.80;           // same as WPI
    private double exportPrices = 172.40;             // index
    private double importPrices = 168.90;             // index

    // Money & Markets Tab — RBI / BSE Apr-May 2025
    private double currencyUsdInr = 84.50;            // USD/INR avg Apr 2025
    private double stockMarketSensex = 80218.0;       // BSE Sensex close Apr 30 2025
    private double yieldCurveSpread = 0.28;           // % 10Y(6.63) - 2Y(6.35) Apr 2025
    private double interbankRate = 6.10;              // % MIBOR overnight Apr 2025
    private double bankLoanGrowthYoY = 11.10;         // % RBI SCB credit growth Mar 2025
    private double moneySupplyM1 = 6450.0;            // ₹ Thousand Cr (RBI Mar 2025)
    private double moneySupplyM2 = 6680.0;            // ₹ Thousand Cr
    private double moneySupplyM3 = 23180.0;           // ₹ Thousand Cr (RBI Mar 2025)
    private double centralBankBalanceSheet = 7020.0;  // ₹ Thousand Cr (RBI BS Mar 2025)
    private double foreignExchangeReserves = 688000.0;// USD mn (RBI Apr 25 2025: $688bn)

    // Trade Tab — DGCI&S / RBI BOP FY2025-26
    private double balanceOfTrade = -21.54;           // USD bn monthly avg (Apr 2025)
    private double exportsUsdBillion = 38.49;         // USD bn Apr 2025 (DGCI&S)
    private double importsUsdBillion = 60.03;         // USD bn Apr 2025
    private double currentAccount = -11800.0;         // USD mn Q3 FY26 (RBI BOP)
    private double currentAccountToGdp = -1.10;       // % GDP FY2025-26 estimate
    private double termsOfTrade = 101.90;             // index
    private double foreignDirectInvestment = 6.50;    // USD bn quarterly net FDI
    private double remittancesUsdBillion = 32.00;     // USD bn quarterly (RBI)
    private double externalDebtUsdBillion = 717.90;   // USD bn Mar 2025 (RBI)
    private double goldReservesTonnes = 879.60;       // tonnes (RBI Apr 2025)
    private double crudeOilProduction = 540.0;        // thousand bbl/day
    private double touristArrivals = 680000.0;        // monthly avg (MoT 2025)

    // Government Tab — Union Budget 2025-26 (BE)
    private double governmentBudget = -4.40;          // % GDP fiscal deficit BE FY26
    private double governmentDebtToGdp = 83.50;       // % (central + state combined)
    private double governmentSpendingToGdp = 15.00;   // % GDP total expenditure
    private double governmentRevenues = 3383000.0;    // ₹ Cr BE FY26 (gross tax rev)
    private double fiscalExpenditure = 5024000.0;     // ₹ Cr BE FY26 total expenditure
    private double corruptionIndex = 38.00;           // CPI score 2024 (TI)
    private int corruptionRank = 96;                  // rank 2024 (TI)

    // Business Tab — S&P Global PMI / DPIIT Apr 2025
    private double businessConfidence = 113.50;       // NCAER BCI Q4 FY26
    private double manufacturingPMI = 58.20;          // S&P Global Mfg PMI Apr 2025
    private double servicesPMI = 58.70;               // S&P Global Svc PMI Apr 2025
    private double compositePMI = 59.70;              // S&P Global Composite Apr 2025
    private double netBusinessFormations = 55.80;     // index
    private double industrialProductionYoY = 3.00;    // % IIP Feb 2025 (MoSPI)
    private double manufacturingProductionYoY = 2.90; // % Mfg IIP Feb 2025
    private double capacityUtilization = 75.80;       // % RBI OBICUS Q3 FY26
    private double passengerVehicleSales = 390000.0;  // units/month SIAM FY26 avg
    private double depositGrowthYoY = 10.60;          // % RBI SCB deposits Mar 2025

    // Consumer Tab — RBI / CMIE Apr 2025
    private double consumerConfidence = 91.20;        // RBI CCS Mar 2025 (current)
    private double consumerDurablesIndex = 118.50;    // IIP Consumer Durables Feb 2025
    private double bankLendingRate = 9.40;            // % WALR on fresh loans Mar 2025
    private double gasolinePrices = 1.04;             // USD/litre petrol avg India Apr 2025
    private double householdsDebtToGdp = 42.90;       // % RBI FSR Dec 2024
    private double retailSalesGrowthYoY = 8.50;       // % Retailers Assoc India FY26

    // Housing Tab — NHB / RBI
    private double housingIndex = 113.50;             // NHB RESIDEX Q3 FY26
    private double infrastructureOutputYoY = 5.80;    // % Core sector IIP Feb 2025
    private double residentialPropertyPricesYoY = 6.50; // % NHB RESIDEX YoY Q3 FY26

    // Health Tab — NHP 2025
    private double hospitalBedsPer1000 = 0.55;        // per 1000 (NHP 2025 estimate)
    private double publicHealthReadinessIndex = 78.00;// composite index

    // --- 3. DYNAMIC NEUTRAL RATES ---
    private double neutralRepoRate = 5.50;            // r* = RBI neutral (inflation 4% + real 1.5%)
    private double neutralGdpGrowth = 6.50;           // India potential growth FY26
    private double neutralInfraSpend = 4.10;          // baseline = current budget

    // --- 4. EXOGENOUS SHOCK LEVERS ---
    private double monsoonIndex = 105.0;              // IMD 2025 forecast: 105% of LPA (above normal)
    private double m1ShockPct = 0.0;

    // --- 5. BANKING TRANSMISSION ---
    private double bankingNpaPct = 2.60;              // % Gross NPA Mar 2025 (RBI FSR — 12-yr low)

    // --- 6. TAX BUOYANCY ---
    private double taxBuoyancyCoeff = 1.10;           // strong GST + direct tax buoyancy FY26

    // --- 7. CAUSAL ATTRIBUTION & DELTA TRACKING ---
    private double prevLeadingMomentum = 1.0;
    private double prevAnnualGdpGrowth = 6.50;
    private double prevInterestRate = 6.25;           // repo before Apr 2025 cut
    private double prevInfraSpend = 3.80;             // FY2024-25 capex % GDP
    private String lastTurnSummary = "Baseline initialized to FY 2025-26 actuals (NSO 2nd AE, RBI MPC Apr 2025, Union Budget 2025-26). Adjust any policy slider to inspect Pros & Cons, or advance a quarter to see causal explanations.";
    private final Map<String, Double> deltas = new HashMap<>();
    private final Map<String, String> reasons = new LinkedHashMap<>();

    public EconomyState() {}

    public void recordChange(String key, double oldVal, double newVal, String reasonText) {
        double d = Math.round((newVal - oldVal) * 100.0) / 100.0;
        deltas.put(key, d);
        reasons.put(key, reasonText);
    }

    public double getDelta(String key) {
        return deltas.getOrDefault(key, 0.0);
    }

    public String getReason(String key) {
        return reasons.getOrDefault(key, "Baseline steady state. Run a simulation step (+1 Quarter or +1 Year) to generate causal attribution.");
    }

    // Standard Getters & Setters
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public int getQuarter() { return quarter; }
    public void setQuarter(int quarter) { this.quarter = quarter; }

    public double getCorporateTaxRate() { return corporateTaxRate; }
    public void setCorporateTaxRate(double corporateTaxRate) { this.corporateTaxRate = corporateTaxRate; }
    public double getPersonalIncomeTaxRate() { return personalIncomeTaxRate; }
    public void setPersonalIncomeTaxRate(double personalIncomeTaxRate) { this.personalIncomeTaxRate = personalIncomeTaxRate; }
    public double getSalesTaxGstRate() { return salesTaxGstRate; }
    public void setSalesTaxGstRate(double salesTaxGstRate) { this.salesTaxGstRate = salesTaxGstRate; }
    public double getSocialSecurityRate() { return socialSecurityRate; }
    public void setSocialSecurityRate(double socialSecurityRate) { this.socialSecurityRate = socialSecurityRate; }
    public double getWithholdingTaxRate() { return withholdingTaxRate; }
    public void setWithholdingTaxRate(double withholdingTaxRate) { this.withholdingTaxRate = withholdingTaxRate; }
    public double getCustomsDutyRate() { return customsDutyRate; }
    public void setCustomsDutyRate(double customsDutyRate) { this.customsDutyRate = customsDutyRate; }

    public double getInterestRate() { return interestRate; }
    public void setInterestRate(double interestRate) { this.interestRate = interestRate; }
    public double getReverseRepoRate() { return reverseRepoRate; }
    public void setReverseRepoRate(double reverseRepoRate) { this.reverseRepoRate = reverseRepoRate; }
    public double getCashReserveRatio() { return cashReserveRatio; }
    public void setCashReserveRatio(double cashReserveRatio) { this.cashReserveRatio = cashReserveRatio; }
    public double getBrentCrudePrice() { return brentCrudePrice; }
    public void setBrentCrudePrice(double brentCrudePrice) { this.brentCrudePrice = brentCrudePrice; }

    public double getEducationSpend() { return educationSpend; }
    public void setEducationSpend(double educationSpend) { this.educationSpend = educationSpend; }
    public double getHealthSpend() { return healthSpend; }
    public void setHealthSpend(double healthSpend) { this.healthSpend = healthSpend; }
    public double getInfraSpend() { return infraSpend; }
    public void setInfraSpend(double infraSpend) { this.infraSpend = infraSpend; }
    public double getTechSpend() { return techSpend; }
    public void setTechSpend(double techSpend) { this.techSpend = techSpend; }
    public double getMachinerySpend() { return machinerySpend; }
    public void setMachinerySpend(double machinerySpend) { this.machinerySpend = machinerySpend; }
    public double getItServicesSpend() { return itServicesSpend; }
    public void setItServicesSpend(double itServicesSpend) { this.itServicesSpend = itServicesSpend; }
    public double getTourismSpend() { return tourismSpend; }
    public void setTourismSpend(double tourismSpend) { this.tourismSpend = tourismSpend; }

    public double getGdpGrowthRate() { return gdpGrowthRate; }
    public void setGdpGrowthRate(double gdpGrowthRate) { this.gdpGrowthRate = gdpGrowthRate; }
    public double getGdpAnnualGrowthRate() { return gdpAnnualGrowthRate; }
    public void setGdpAnnualGrowthRate(double gdpAnnualGrowthRate) { this.gdpAnnualGrowthRate = gdpAnnualGrowthRate; }
    public double getNominalGdpGrowthRate() { return nominalGdpGrowthRate; }
    public void setNominalGdpGrowthRate(double nominalGdpGrowthRate) { this.nominalGdpGrowthRate = nominalGdpGrowthRate; }
    public double getFullYearGdpGrowth() { return fullYearGdpGrowth; }
    public void setFullYearGdpGrowth(double fullYearGdpGrowth) { this.fullYearGdpGrowth = fullYearGdpGrowth; }
    public double getGdpUsdBillion() { return gdpUsdBillion; }
    public void setGdpUsdBillion(double gdpUsdBillion) { this.gdpUsdBillion = gdpUsdBillion; }
    public double getGdpConstantPrices() { return gdpConstantPrices; }
    public void setGdpConstantPrices(double gdpConstantPrices) { this.gdpConstantPrices = gdpConstantPrices; }
    public double getGrossFixedCapitalFormation() { return grossFixedCapitalFormation; }
    public void setGrossFixedCapitalFormation(double grossFixedCapitalFormation) { this.grossFixedCapitalFormation = grossFixedCapitalFormation; }
    public double getGdpPerCapita() { return gdpPerCapita; }
    public void setGdpPerCapita(double gdpPerCapita) { this.gdpPerCapita = gdpPerCapita; }
    public double getGdpPerCapitaPpp() { return gdpPerCapitaPpp; }
    public void setGdpPerCapitaPpp(double gdpPerCapitaPpp) { this.gdpPerCapitaPpp = gdpPerCapitaPpp; }
    public double getGnpInrCrore() { return gnpInrCrore; }
    public void setGnpInrCrore(double gnpInrCrore) { this.gnpInrCrore = gnpInrCrore; }
    public double getGdpFromAgriculture() { return gdpFromAgriculture; }
    public void setGdpFromAgriculture(double gdpFromAgriculture) { this.gdpFromAgriculture = gdpFromAgriculture; }
    public double getGdpFromManufacturing() { return gdpFromManufacturing; }
    public void setGdpFromManufacturing(double gdpFromManufacturing) { this.gdpFromManufacturing = gdpFromManufacturing; }
    public double getGdpFromConstruction() { return gdpFromConstruction; }
    public void setGdpFromConstruction(double gdpFromConstruction) { this.gdpFromConstruction = gdpFromConstruction; }
    public double getGdpFromPublicAdmin() { return gdpFromPublicAdmin; }
    public void setGdpFromPublicAdmin(double gdpFromPublicAdmin) { this.gdpFromPublicAdmin = gdpFromPublicAdmin; }
    public double getGdpFromUtilities() { return gdpFromUtilities; }
    public void setGdpFromUtilities(double gdpFromUtilities) { this.gdpFromUtilities = gdpFromUtilities; }
    public double getGdpFromMining() { return gdpFromMining; }
    public void setGdpFromMining(double gdpFromMining) { this.gdpFromMining = gdpFromMining; }

    public double getUnemploymentRate() { return unemploymentRate; }
    public void setUnemploymentRate(double unemploymentRate) { this.unemploymentRate = unemploymentRate; }
    public double getEmploymentRate() { return employmentRate; }
    public void setEmploymentRate(double employmentRate) { this.employmentRate = employmentRate; }
    public double getLaborForceParticipation() { return laborForceParticipation; }
    public void setLaborForceParticipation(double laborForceParticipation) { this.laborForceParticipation = laborForceParticipation; }
    public double getPopulationMillion() { return populationMillion; }
    public void setPopulationMillion(double populationMillion) { this.populationMillion = populationMillion; }
    public double getMinimumWages() { return minimumWages; }
    public void setMinimumWages(double minimumWages) { this.minimumWages = minimumWages; }
    public double getMonthlyWages() { return monthlyWages; }
    public void setMonthlyWages(double monthlyWages) { this.monthlyWages = monthlyWages; }

    public double getInflationRate() { return inflationRate; }
    public void setInflationRate(double inflationRate) { this.inflationRate = inflationRate; }
    public double getCpiPoints() { return cpiPoints; }
    public void setCpiPoints(double cpiPoints) { this.cpiPoints = cpiPoints; }
    public double getConsumerFoodPriceIndex() { return consumerFoodPriceIndex; }
    public void setConsumerFoodPriceIndex(double consumerFoodPriceIndex) { this.consumerFoodPriceIndex = consumerFoodPriceIndex; }
    public double getCpiHousingUtilities() { return cpiHousingUtilities; }
    public void setCpiHousingUtilities(double cpiHousingUtilities) { this.cpiHousingUtilities = cpiHousingUtilities; }
    public double getCpiTransportation() { return cpiTransportation; }
    public void setCpiTransportation(double cpiTransportation) { this.cpiTransportation = cpiTransportation; }
    public double getCpiEducation() { return cpiEducation; }
    public void setCpiEducation(double cpiEducation) { this.cpiEducation = cpiEducation; }
    public double getCpiRestaurants() { return cpiRestaurants; }
    public void setCpiRestaurants(double cpiRestaurants) { this.cpiRestaurants = cpiRestaurants; }
    public double getWholesalePriceIndex() { return wholesalePriceIndex; }
    public void setWholesalePriceIndex(double wholesalePriceIndex) { this.wholesalePriceIndex = wholesalePriceIndex; }
    public double getWpiFoodYoY() { return wpiFoodYoY; }
    public void setWpiFoodYoY(double wpiFoodYoY) { this.wpiFoodYoY = wpiFoodYoY; }
    public double getProducerPrices() { return producerPrices; }
    public void setProducerPrices(double producerPrices) { this.producerPrices = producerPrices; }
    public double getExportPrices() { return exportPrices; }
    public void setExportPrices(double exportPrices) { this.exportPrices = exportPrices; }
    public double getImportPrices() { return importPrices; }
    public void setImportPrices(double importPrices) { this.importPrices = importPrices; }

    public double getCurrencyUsdInr() { return currencyUsdInr; }
    public void setCurrencyUsdInr(double currencyUsdInr) { this.currencyUsdInr = currencyUsdInr; }
    public double getStockMarketSensex() { return stockMarketSensex; }
    public void setStockMarketSensex(double stockMarketSensex) { this.stockMarketSensex = stockMarketSensex; }
    public double getYieldCurveSpread() { return yieldCurveSpread; }
    public void setYieldCurveSpread(double yieldCurveSpread) { this.yieldCurveSpread = yieldCurveSpread; }
    public double getInterbankRate() { return interbankRate; }
    public void setInterbankRate(double interbankRate) { this.interbankRate = interbankRate; }
    public double getBankLoanGrowthYoY() { return bankLoanGrowthYoY; }
    public void setBankLoanGrowthYoY(double bankLoanGrowthYoY) { this.bankLoanGrowthYoY = bankLoanGrowthYoY; }
    public double getMoneySupplyM1() { return moneySupplyM1; }
    public void setMoneySupplyM1(double moneySupplyM1) { this.moneySupplyM1 = moneySupplyM1; }
    public double getMoneySupplyM2() { return moneySupplyM2; }
    public void setMoneySupplyM2(double moneySupplyM2) { this.moneySupplyM2 = moneySupplyM2; }
    public double getMoneySupplyM3() { return moneySupplyM3; }
    public void setMoneySupplyM3(double moneySupplyM3) { this.moneySupplyM3 = moneySupplyM3; }
    public double getCentralBankBalanceSheet() { return centralBankBalanceSheet; }
    public void setCentralBankBalanceSheet(double centralBankBalanceSheet) { this.centralBankBalanceSheet = centralBankBalanceSheet; }
    public double getForeignExchangeReserves() { return foreignExchangeReserves; }
    public void setForeignExchangeReserves(double foreignExchangeReserves) { this.foreignExchangeReserves = foreignExchangeReserves; }

    public double getBalanceOfTrade() { return balanceOfTrade; }
    public void setBalanceOfTrade(double balanceOfTrade) { this.balanceOfTrade = balanceOfTrade; }
    public double getExportsUsdBillion() { return exportsUsdBillion; }
    public void setExportsUsdBillion(double exportsUsdBillion) { this.exportsUsdBillion = exportsUsdBillion; }
    public double getImportsUsdBillion() { return importsUsdBillion; }
    public void setImportsUsdBillion(double importsUsdBillion) { this.importsUsdBillion = importsUsdBillion; }
    public double getCurrentAccount() { return currentAccount; }
    public void setCurrentAccount(double currentAccount) { this.currentAccount = currentAccount; }
    public double getCurrentAccountToGdp() { return currentAccountToGdp; }
    public void setCurrentAccountToGdp(double currentAccountToGdp) { this.currentAccountToGdp = currentAccountToGdp; }
    public double getTermsOfTrade() { return termsOfTrade; }
    public void setTermsOfTrade(double termsOfTrade) { this.termsOfTrade = termsOfTrade; }
    public double getForeignDirectInvestment() { return foreignDirectInvestment; }
    public void setForeignDirectInvestment(double foreignDirectInvestment) { this.foreignDirectInvestment = foreignDirectInvestment; }
    public double getRemittancesUsdBillion() { return remittancesUsdBillion; }
    public void setRemittancesUsdBillion(double remittancesUsdBillion) { this.remittancesUsdBillion = remittancesUsdBillion; }
    public double getExternalDebtUsdBillion() { return externalDebtUsdBillion; }
    public void setExternalDebtUsdBillion(double externalDebtUsdBillion) { this.externalDebtUsdBillion = externalDebtUsdBillion; }
    public double getGoldReservesTonnes() { return goldReservesTonnes; }
    public void setGoldReservesTonnes(double goldReservesTonnes) { this.goldReservesTonnes = goldReservesTonnes; }
    public double getCrudeOilProduction() { return crudeOilProduction; }
    public void setCrudeOilProduction(double crudeOilProduction) { this.crudeOilProduction = crudeOilProduction; }
    public double getTouristArrivals() { return touristArrivals; }
    public void setTouristArrivals(double touristArrivals) { this.touristArrivals = touristArrivals; }

    public double getGovernmentBudget() { return governmentBudget; }
    public void setGovernmentBudget(double governmentBudget) { this.governmentBudget = governmentBudget; }
    public double getGovernmentDebtToGdp() { return governmentDebtToGdp; }
    public void setGovernmentDebtToGdp(double governmentDebtToGdp) { this.governmentDebtToGdp = governmentDebtToGdp; }
    public double getGovernmentSpendingToGdp() { return governmentSpendingToGdp; }
    public void setGovernmentSpendingToGdp(double governmentSpendingToGdp) { this.governmentSpendingToGdp = governmentSpendingToGdp; }
    public double getGovernmentRevenues() { return governmentRevenues; }
    public void setGovernmentRevenues(double governmentRevenues) { this.governmentRevenues = governmentRevenues; }
    public double getFiscalExpenditure() { return fiscalExpenditure; }
    public void setFiscalExpenditure(double fiscalExpenditure) { this.fiscalExpenditure = fiscalExpenditure; }
    public double getCorruptionIndex() { return corruptionIndex; }
    public void setCorruptionIndex(double corruptionIndex) { this.corruptionIndex = corruptionIndex; }
    public int getCorruptionRank() { return corruptionRank; }
    public void setCorruptionRank(int corruptionRank) { this.corruptionRank = corruptionRank; }

    public double getBusinessConfidence() { return businessConfidence; }
    public void setBusinessConfidence(double businessConfidence) { this.businessConfidence = businessConfidence; }
    public double getManufacturingPMI() { return manufacturingPMI; }
    public void setManufacturingPMI(double manufacturingPMI) { this.manufacturingPMI = manufacturingPMI; }
    public double getServicesPMI() { return servicesPMI; }
    public void setServicesPMI(double servicesPMI) { this.servicesPMI = servicesPMI; }
    public double getCompositePMI() { return compositePMI; }
    public void setCompositePMI(double compositePMI) { this.compositePMI = compositePMI; }
    public double getNetBusinessFormations() { return netBusinessFormations; }
    public void setNetBusinessFormations(double netBusinessFormations) { this.netBusinessFormations = netBusinessFormations; }
    public double getIndustrialProductionYoY() { return industrialProductionYoY; }
    public void setIndustrialProductionYoY(double industrialProductionYoY) { this.industrialProductionYoY = industrialProductionYoY; }
    public double getManufacturingProductionYoY() { return manufacturingProductionYoY; }
    public void setManufacturingProductionYoY(double manufacturingProductionYoY) { this.manufacturingProductionYoY = manufacturingProductionYoY; }
    public double getCapacityUtilization() { return capacityUtilization; }
    public void setCapacityUtilization(double capacityUtilization) { this.capacityUtilization = capacityUtilization; }
    public double getPassengerVehicleSales() { return passengerVehicleSales; }
    public void setPassengerVehicleSales(double passengerVehicleSales) { this.passengerVehicleSales = passengerVehicleSales; }
    public double getDepositGrowthYoY() { return depositGrowthYoY; }
    public void setDepositGrowthYoY(double depositGrowthYoY) { this.depositGrowthYoY = depositGrowthYoY; }

    public double getConsumerConfidence() { return consumerConfidence; }
    public void setConsumerConfidence(double consumerConfidence) { this.consumerConfidence = consumerConfidence; }
    public double getConsumerDurablesIndex() { return consumerDurablesIndex; }
    public void setConsumerDurablesIndex(double consumerDurablesIndex) { this.consumerDurablesIndex = consumerDurablesIndex; }
    public double getBankLendingRate() { return bankLendingRate; }
    public void setBankLendingRate(double bankLendingRate) { this.bankLendingRate = bankLendingRate; }
    public double getGasolinePrices() { return gasolinePrices; }
    public void setGasolinePrices(double gasolinePrices) { this.gasolinePrices = gasolinePrices; }
    public double getHouseholdsDebtToGdp() { return householdsDebtToGdp; }
    public void setHouseholdsDebtToGdp(double householdsDebtToGdp) { this.householdsDebtToGdp = householdsDebtToGdp; }
    public double getRetailSalesGrowthYoY() { return retailSalesGrowthYoY; }
    public void setRetailSalesGrowthYoY(double retailSalesGrowthYoY) { this.retailSalesGrowthYoY = retailSalesGrowthYoY; }

    public double getHousingIndex() { return housingIndex; }
    public void setHousingIndex(double housingIndex) { this.housingIndex = housingIndex; }
    public double getInfrastructureOutputYoY() { return infrastructureOutputYoY; }
    public void setInfrastructureOutputYoY(double infrastructureOutputYoY) { this.infrastructureOutputYoY = infrastructureOutputYoY; }
    public double getResidentialPropertyPricesYoY() { return residentialPropertyPricesYoY; }
    public void setResidentialPropertyPricesYoY(double residentialPropertyPricesYoY) { this.residentialPropertyPricesYoY = residentialPropertyPricesYoY; }

    public double getHospitalBedsPer1000() { return hospitalBedsPer1000; }
    public void setHospitalBedsPer1000(double hospitalBedsPer1000) { this.hospitalBedsPer1000 = hospitalBedsPer1000; }
    public double getPublicHealthReadinessIndex() { return publicHealthReadinessIndex; }
    public void setPublicHealthReadinessIndex(double publicHealthReadinessIndex) { this.publicHealthReadinessIndex = publicHealthReadinessIndex; }

    public double getPrevLeadingMomentum() { return prevLeadingMomentum; }
    public void setPrevLeadingMomentum(double prevLeadingMomentum) { this.prevLeadingMomentum = prevLeadingMomentum; }
    public double getPrevAnnualGdpGrowth() { return prevAnnualGdpGrowth; }
    public void setPrevAnnualGdpGrowth(double prevAnnualGdpGrowth) { this.prevAnnualGdpGrowth = prevAnnualGdpGrowth; }
    public double getPrevInterestRate() { return prevInterestRate; }
    public void setPrevInterestRate(double prevInterestRate) { this.prevInterestRate = prevInterestRate; }
    public double getPrevInfraSpend() { return prevInfraSpend; }
    public void setPrevInfraSpend(double prevInfraSpend) { this.prevInfraSpend = prevInfraSpend; }
    public double getNeutralRepoRate() { return neutralRepoRate; }
    public void setNeutralRepoRate(double neutralRepoRate) { this.neutralRepoRate = neutralRepoRate; }
    public double getNeutralGdpGrowth() { return neutralGdpGrowth; }
    public void setNeutralGdpGrowth(double neutralGdpGrowth) { this.neutralGdpGrowth = neutralGdpGrowth; }
    public double getNeutralInfraSpend() { return neutralInfraSpend; }
    public void setNeutralInfraSpend(double neutralInfraSpend) { this.neutralInfraSpend = neutralInfraSpend; }
    public double getMonsoonIndex() { return monsoonIndex; }
    public void setMonsoonIndex(double monsoonIndex) { this.monsoonIndex = monsoonIndex; }
    public double getM1ShockPct() { return m1ShockPct; }
    public void setM1ShockPct(double m1ShockPct) { this.m1ShockPct = m1ShockPct; }
    public double getBankingNpaPct() { return bankingNpaPct; }
    public void setBankingNpaPct(double bankingNpaPct) { this.bankingNpaPct = bankingNpaPct; }
    public double getTaxBuoyancyCoeff() { return taxBuoyancyCoeff; }
    public void setTaxBuoyancyCoeff(double taxBuoyancyCoeff) { this.taxBuoyancyCoeff = taxBuoyancyCoeff; }
    public String getLastTurnSummary() { return lastTurnSummary; }
    public void setLastTurnSummary(String lastTurnSummary) { this.lastTurnSummary = lastTurnSummary; }

    public Map<String, Double> getDeltas() { return deltas; }
    public Map<String, String> getReasons() { return reasons; }
}