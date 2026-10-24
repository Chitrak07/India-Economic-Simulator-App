package com.india.econsim.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class EconomyState implements Serializable {
    private static final long serialVersionUID = 2L;

    private int year = 2026;
    private int quarter = 4;

    // --- 1. POLICY LEVERS: TAXES, MONETARY & SECTORAL BUDGETS ---
    private double corporateTaxRate = 25.17;         // % (Post-2019 effective rate)
    private double personalIncomeTaxRate = 30.00;     // % (New tax regime top rate)
    private double salesTaxGstRate = 18.00;
    private double socialSecurityRate = 24.00;
    private double withholdingTaxRate = 20.00;
    private double customsDutyRate = 10.00;

    private double interestRate = 5.50;
    private double reverseRepoRate = 3.35;
    private double cashReserveRatio = 3.00;
    private double brentCrudePrice = 85.0;           // USD/barrel (Brent crude benchmark)

    private double educationSpend = 2.90;
    private double healthSpend = 2.10;
    private double infraSpend = 3.40;
    private double techSpend = 0.85;
    private double machinerySpend = 1.50;
    private double itServicesSpend = 1.10;
    private double tourismSpend = 0.50;

    // --- 2. ALL 11 TAB INDICATORS ---
    // GDP Tab
    private double gdpGrowthRate = 1.80;
    private double gdpAnnualGrowthRate = 7.80;
    private double fullYearGdpGrowth = 7.70;
    private double gdpUsdBillion = 3956.07;
    private double gdpConstantPrices = 87771.04;
    private double grossFixedCapitalFormation = 27956.05;
    private double gdpPerCapita = 2523.44;
    private double gdpPerCapitaPpp = 10039.07;
    private double gnpInrCrore = 31811699.0;
    private double gdpFromAgriculture = 12406.12;
    private double gdpFromManufacturing = 10244.17;
    private double gdpFromConstruction = 7178.60;
    private double gdpFromPublicAdmin = 9664.68;
    private double gdpFromUtilities = 1921.38;
    private double gdpFromMining = 1555.84;

    // Labour Tab
    private double unemploymentRate = 5.00;
    private double employmentRate = 95.00;
    private double laborForceParticipation = 55.50;
    private double populationMillion = 1421.00;
    private double minimumWages = 178.00;
    private double monthlyWages = 22698.65;

    // Prices Tab
    private double inflationRate = 5.54;
    private double cpiPoints = 109.49;
    private double consumerFoodPriceIndex = 112.17;
    private double cpiHousingUtilities = 104.41;
    private double cpiTransportation = 106.12;
    private double cpiEducation = 109.05;
    private double cpiRestaurants = 114.79;
    private double wholesalePriceIndex = 112.50;
    private double wpiFoodYoY = 8.66;
    private double producerPrices = 112.50;
    private double exportPrices = 163.50;
    private double importPrices = 159.80;

    // Money & Markets Tab
    private double currencyUsdInr = 84.40;
    private double stockMarketSensex = 73123.83;
    private double yieldCurveSpread = 0.45;
    private double interbankRate = 6.63;
    private double bankLoanGrowthYoY = 12.80;
    private double moneySupplyM1 = 78942.86;
    private double moneySupplyM2 = 81229.90;
    private double moneySupplyM3 = 337664.75;
    private double centralBankBalanceSheet = 50938.53;
    private double foreignExchangeReserves = 726030.0;

    // Trade Tab
    private double balanceOfTrade = -30.21;
    private double exportsUsdBillion = 44.40;
    private double importsUsdBillion = 74.61;
    private double currentAccount = -4219.00;
    private double currentAccountToGdp = -0.60;
    private double termsOfTrade = 102.30;
    private double foreignDirectInvestment = 6.80;
    private double remittancesUsdBillion = 29.50;
    private double externalDebtUsdBillion = 663.80;
    private double goldReservesTonnes = 853.60;
    private double crudeOilProduction = 561.00;
    private double touristArrivals = 584000.0;

    // Government Tab
    private double governmentBudget = -4.40;
    private double governmentDebtToGdp = 81.85;
    private double governmentSpendingToGdp = 14.19;
    private double governmentRevenues = 1367709.0;
    private double fiscalExpenditure = 2077958.0;
    private double corruptionIndex = 39.00;
    private int corruptionRank = 91;

    // Business Tab
    private double businessConfidence = 111.10;
    private double manufacturingPMI = 54.90;
    private double servicesPMI = 56.20;
    private double compositePMI = 56.70;
    private double netBusinessFormations = 52.40;
    private double industrialProductionYoY = 4.40;
    private double manufacturingProductionYoY = 4.80;
    private double capacityUtilization = 74.50;
    private double passengerVehicleSales = 355000.0;
    private double depositGrowthYoY = 11.20;

    // Consumer Tab
    private double consumerConfidence = 87.90;
    private double consumerDurablesIndex = 114.20;
    private double bankLendingRate = 8.96;
    private double gasolinePrices = 1.06;
    private double householdsDebtToGdp = 49.10;
    private double retailSalesGrowthYoY = 6.90;

    // Housing Tab
    private double housingIndex = 106.93;
    private double infrastructureOutputYoY = 7.90;
    private double residentialPropertyPricesYoY = 4.21;

    // Health Tab
    private double hospitalBedsPer1000 = 0.52;
    private double publicHealthReadinessIndex = 76.50;

    // --- 3. DYNAMIC NEUTRAL RATES (Fix #1) ---
    private double neutralRepoRate = 6.50;            // r* = inflation target (4%) + real neutral (2.5%)
    private double neutralGdpGrowth = 6.80;           // India long-run potential growth
    private double neutralInfraSpend = 3.40;          // baseline infra % GDP

    // --- 4. EXOGENOUS SHOCK LEVERS (Fix #3) ---
    private double monsoonIndex = 100.0;              // 100 = normal; <95 = drought; >105 = bumper
    private double m1ShockPct = 0.0;                  // % one-time M1 shock (e.g. -19 for demonetisation)

    // --- 5. BANKING TRANSMISSION (Fix #2) ---
    private double bankingNpaPct = 7.5;               // Gross NPA % of advances (transmission efficiency)

    // --- 6. TAX BUOYANCY (Fix #4) ---
    private double taxBuoyancyCoeff = 1.0;            // >1 = buoyant (formalization); <1 = leakage

    // --- 7. CAUSAL ATTRIBUTION & DELTA TRACKING ---
    private double prevLeadingMomentum = 1.0;
    private double prevAnnualGdpGrowth = 6.80;        // corrected baseline
    private double prevInterestRate = 6.50;           // tracks period-over-period repo delta
    private double prevInfraSpend = 3.40;             // tracks period-over-period infra delta
    private String lastTurnSummary = "Baseline initialized to Trading Economics India benchmarks. Adjust any policy slider on the left to inspect Pros & Cons, or advance a quarter to see causal explanations.";
    private Map<String, Double> deltas = new HashMap<>();
    private Map<String, String> reasons = new LinkedHashMap<>();

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