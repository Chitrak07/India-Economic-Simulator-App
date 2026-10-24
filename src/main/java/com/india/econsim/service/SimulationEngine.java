package com.india.econsim.service;

import com.india.econsim.model.EconomyState;

public class SimulationEngine {

    public static void advanceQuarter(EconomyState s) {
        if (s.getQuarter() == 4) {
            s.setQuarter(1);
            s.setYear(s.getYear() + 1);
        } else {
            s.setQuarter(s.getQuarter() + 1);
        }

        double prevGrowth = s.getPrevAnnualGdpGrowth();

        // FIX #1: Dynamic neutral rates — policy stance measured relative to r* not hardcoded 5.5%
        double rStar = s.getNeutralRepoRate();                          // user-settable neutral repo
        double gStar = s.getNeutralGdpGrowth();                         // user-settable potential growth
        double infraStar = s.getNeutralInfraSpend();                    // user-settable neutral infra

        // FIX #1: Period-over-period policy deltas (direction of change matters, not level vs 2026)
        double deltaRepo  = s.getInterestRate() - s.getPrevInterestRate();   // +ve = tightening
        double deltaInfra = s.getInfraSpend()   - s.getPrevInfraSpend();     // +ve = stimulus
        // M1 velocity ratio: ongoing money supply growth relative to nominal GDP growth (not one-time shock)
        double m1Velocity = s.getMoneySupplyM1() / Math.max(1.0, s.getGdpUsdBillion() * 84.4); // M1/NGDP proxy

        // FIX #2: Real interest rate = nominal repo - current CPI inflation
        double realRate = s.getInterestRate() - s.getInflationRate();
        // NPA transmission efficiency: high NPA (>7%) chokes credit even when repo is cut
        double npaDrag = Math.max(0.0, (s.getBankingNpaPct() - 7.0) * 0.08);

        // FIX #3: Exogenous shocks
        // Oil shock: neutral = $85/bbl
        double oilShock = (s.getBrentCrudePrice() - 85.0) / 10.0;
        // Monsoon shock: affects food inflation and agriculture GDP (neutral = 100)
        double monsoonShock = (s.getMonsoonIndex() - 100.0) / 10.0;    // +ve = bumper harvest
        // M1 velocity shock (demonetisation-style): contracts informal consumption & services PMI
        double m1Shock = s.getM1ShockPct() / 100.0;                    // e.g. -0.19 for demonetisation

        // =====================================================================
        // 1. LAGGING INDICATORS & CAUSAL REASONS
        // =====================================================================
        double oldUnemp = s.getUnemploymentRate();
        double jobCreationBoost = (s.getInfraSpend() - 3.4) * 0.15
                + (s.getMachinerySpend() - 1.5) * 0.20
                + (s.getEducationSpend() - 2.9) * 0.12
                + (s.getItServicesSpend() - 1.1) * 0.15
                + (s.getTourismSpend() - 0.5) * 0.10
                - (s.getSocialSecurityRate() - 24.0) * 0.05;
        // Fix #1: Okun coefficient 0.10 (informal sector dampener), baseline 6.8%
        // Fix #9: Non-linear recession amplifier
        double recessionAmplifier = prevGrowth < 0 ? Math.abs(prevGrowth) * 0.3 : 0.0;
        double okunEffect = 0.10 * (prevGrowth - 6.8);
        double newUnemp = round(clamp(oldUnemp - okunEffect - jobCreationBoost + recessionAmplifier, 2.5, 15.0));
        s.setUnemploymentRate(newUnemp);
        s.recordChange("unemploymentRate", oldUnemp, newUnemp,
                String.format("Lagging Indicator: Moved from %.2f%% to %.2f%% due to Okun's Law lagged GDP growth effect (%+.2f%%) and sectoral job creation from Infra, Machinery, Education & IT budgets (%+.2f%%), offset by EPFO Social Security payroll burden (%.1f%%).",
                        oldUnemp, newUnemp, -okunEffect, -jobCreationBoost, s.getSocialSecurityRate()));

        double oldEmp = s.getEmploymentRate();
        s.setEmploymentRate(round(100.0 - newUnemp));
        s.recordChange("employmentRate", oldEmp, s.getEmploymentRate(),
                "Coincident Indicator: Inverse of the Unemployment Rate, reflecting real-time payroll absorption across manufacturing, construction, and services.");

        double oldLfpr = s.getLaborForceParticipation();
        s.setLaborForceParticipation(round(clamp(55.5 + (s.getEducationSpend() - 2.9) * 0.4 + (6.8 - newUnemp) * 0.2, 48.0, 68.0)));
        s.recordChange("laborForceParticipation", oldLfpr, s.getLaborForceParticipation(),
                String.format("Driven by Education & Skill spend (%.2f%% of GDP) expanding workforce readiness and job market tightness.", s.getEducationSpend()));

        double oldPop = s.getPopulationMillion();
        s.setPopulationMillion(round(s.getPopulationMillion() * 1.002));
        s.recordChange("populationMillion", oldPop, s.getPopulationMillion(), "Natural demographic expansion (+0.2% per quarter).");

        double oldWages = s.getMonthlyWages();
        s.setMonthlyWages(round(oldWages * (1.0 + ((prevGrowth + s.getInflationRate()) / 400.0))));
        s.recordChange("monthlyWages", oldWages, s.getMonthlyWages(),
                "Lagging Indicator: Adjusted upward for nominal GDP productivity gains and lagged CPI cost-of-living indexation.");

        double oldMinWage = s.getMinimumWages();
        s.setMinimumWages(round(oldMinWage * (1.0 + (s.getInflationRate() / 500.0))));
        s.recordChange("minimumWages", oldMinWage, s.getMinimumWages(), "Statutory dearness allowance linked to lagging CPI inflation.");

        // Inflation & Prices
        double oldInf = s.getInflationRate();
        // Fix #3: demand heat baseline corrected to 6.8%
        // FIX #3: Monsoon shock on food inflation (46% CPI weight)
        double oilInflationPush  = oilShock * 0.18;
        double monsoonFoodRelief = monsoonShock * 0.30;   // bumper harvest cools food CPI
        double newInflation = round(clamp(
                0.65 * oldInf + 0.35 * 4.0
                + (prevGrowth - gStar) * 0.20
                - (realRate - 1.5) * 0.30          // FIX #2: real rate gap vs 1.5% neutral real
                - deltaRepo * 0.15                 // FIX #1: rate-cut direction also cools inflation
                + (s.getSalesTaxGstRate() - 18.0) * 0.16
                + (s.getCustomsDutyRate() - 10.0) * 0.08
                - (s.getInfraSpend() - infraStar) * 0.18
                - (s.getMachinerySpend() - 1.5) * 0.10
                + oilInflationPush
                - monsoonFoodRelief,
                1.5, 14.0));
        s.setInflationRate(newInflation);
        s.recordChange("inflationRate", oldInf, newInflation,
                String.format("CPI: real rate gap (%+.2f%%), oil $%.0f/bbl (%+.2f%%), monsoon index %.0f (%+.2f%%), demand heat (%+.2f%%).",
                        -(realRate-1.5)*0.30, s.getBrentCrudePrice(), oilInflationPush, s.getMonsoonIndex(), -monsoonFoodRelief, (prevGrowth-gStar)*0.20));

        double qtrInflationFactor = 1.0 + (newInflation / 400.0);
        double oldCpi = s.getCpiPoints();
        s.setCpiPoints(round(oldCpi * qtrInflationFactor));
        s.recordChange("cpiPoints", oldCpi, s.getCpiPoints(), "Cumulative Consumer Price Index level compounding at the quarterly CPI inflation rate.");

        double oldCfpi = s.getConsumerFoodPriceIndex();
        s.setConsumerFoodPriceIndex(round(oldCfpi * (qtrInflationFactor + 0.001)));
        s.recordChange("consumerFoodPriceIndex", oldCfpi, s.getConsumerFoodPriceIndex(), "Food price basket impacted by cold-chain Infrastructure spend and overall inflation.");

        double oldCpiHous = s.getCpiHousingUtilities();
        s.setCpiHousingUtilities(round(oldCpiHous * qtrInflationFactor));
        s.recordChange("cpiHousingUtilities", oldCpiHous, s.getCpiHousingUtilities(), "Tracks urban rental demand and utility tariff adjustments.");

        double oldCpiTrans = s.getCpiTransportation();
        s.setCpiTransportation(round(oldCpiTrans * (1.0 + ((newInflation + (s.getSalesTaxGstRate() - 18.0) * 0.2) / 400.0))));
        s.recordChange("cpiTransportation", oldCpiTrans, s.getCpiTransportation(), "Reflects fuel tax/GST pass-through and logistics costs.");

        double oldCpiEdu = s.getCpiEducation();
        s.setCpiEducation(round(oldCpiEdu * (1.0 + ((newInflation - (s.getEducationSpend() - 2.9) * 0.5) / 400.0))));
        s.recordChange("cpiEducation", oldCpiEdu, s.getCpiEducation(),
                String.format("Public Education budget (%.2f%% of GDP) subsidizes tuition and moderates education price inflation.", s.getEducationSpend()));

        double oldCpiRest = s.getCpiRestaurants();
        s.setCpiRestaurants(round(oldCpiRest * qtrInflationFactor));
        s.recordChange("cpiRestaurants", oldCpiRest, s.getCpiRestaurants(), "Hospitality & dining prices driven by food CPI, GST rate, and tourism demand.");

        double oldWpi = s.getWholesalePriceIndex();
        // Oil shock on WPI: fuel ~15% weight; negative oil = WPI can go negative
        s.setWholesalePriceIndex(round(oldWpi * (1.0 + ((newInflation - oilShock * 0.25) * 0.85 / 400.0))));
        s.recordChange("wholesalePriceIndex", oldWpi, s.getWholesalePriceIndex(),
                String.format("WPI driven by factory-gate costs. Brent crude at $%.0f/bbl contributes %+.2f%% to WPI momentum.", s.getBrentCrudePrice(), -oilShock * 0.25));

        double oldWpiFood = s.getWpiFoodYoY();
        s.setWpiFoodYoY(round(clamp(newInflation * 1.35 - (s.getInfraSpend() - 3.4) * 0.4, 0.5, 18.0)));
        s.recordChange("wpiFoodYoY", oldWpiFood, s.getWpiFoodYoY(), "Wholesale food inflation moderated by agricultural logistics and rural road infrastructure.");

        s.setProducerPrices(s.getWholesalePriceIndex());
        s.recordChange("producerPrices", oldWpi, s.getProducerPrices(), "Producer Price Index (PPI) moves with input tariffs (Customs Duty) and industrial power costs.");

        // Health Indicators
        double oldBeds = s.getHospitalBedsPer1000();
        s.setHospitalBedsPer1000(round(clamp(oldBeds + (s.getHealthSpend() - 2.0) * 0.015, 0.40, 3.50)));
        s.recordChange("hospitalBedsPer1000", oldBeds, s.getHospitalBedsPer1000(),
                String.format("Lagging Structural Indicator: Driven by Public Healthcare allocation (%.2f%% of GDP vs 2.0%% replacement threshold).", s.getHealthSpend()));

        double oldHealthIdx = s.getPublicHealthReadinessIndex();
        s.setPublicHealthReadinessIndex(round(clamp(76.5 + (s.getHealthSpend() - 2.1) * 4.5 + (s.getTechSpend() - 0.85) * 2.0, 50.0, 99.0)));
        s.recordChange("publicHealthReadinessIndex", oldHealthIdx, s.getPublicHealthReadinessIndex(),
                "Reflects combined Healthcare spending and Digital Health / MedTech R&D investment.");

        // =====================================================================
        // 2. COINCIDENT INDICATORS & CAUSAL REASONS
        // =====================================================================
        double oldAnnualGdp = s.getGdpAnnualGrowthRate();
        double capexMultiplier = (s.getInfraSpend() - 3.4) * 0.45
                + (s.getMachinerySpend() - 1.5) * 0.38
                + (s.getTechSpend() - 0.85) * 0.42
                + (s.getItServicesSpend() - 1.1) * 0.32
                + (s.getEducationSpend() - 2.9) * 0.18
                + (s.getHealthSpend() - 2.1) * 0.15
                + (s.getTourismSpend() - 0.5) * 0.22;

        // FIX #1: monetary brake uses dynamic rStar, not hardcoded 5.5
        // FIX #2: NPA drag chokes credit transmission even when repo is cut
        double taxDrag = (s.getCorporateTaxRate() - 25.17) * 0.07
                + (s.getPersonalIncomeTaxRate() - 30.0) * 0.05
                + (s.getSalesTaxGstRate() - 18.0) * 0.08
                + (s.getWithholdingTaxRate() - 20.0) * 0.03;

        double monetaryBrake = (s.getInterestRate() - rStar) * 0.26
                + deltaRepo * 0.12                 // FIX #1: rate-cut direction stimulates GDP
                + (s.getCashReserveRatio() - 3.0) * 0.18
                + npaDrag;
        double leadingCarryover = (s.getPrevLeadingMomentum() - 1.0) * 3.8;

        // Oil shock on GDP: lower crude = household disposable income boost (India 80% oil importer)
        // Each $10 fall in Brent = ~+0.15% real GDP boost via consumption channel
        double oilGdpBoost = -oilShock * 0.15;
        // FIX #1: infra delta stimulus — ramping up infra spend has multiplier beyond level effect
        double infraDeltaBoost = deltaInfra * 0.20;
        double newAnnualGrowth = round(clamp(6.80 + leadingCarryover + capexMultiplier - taxDrag - monetaryBrake + oilGdpBoost + infraDeltaBoost, -3.0, 12.5));
        s.setGdpAnnualGrowthRate(newAnnualGrowth);
        s.recordChange("gdpAnnualGrowthRate", oldAnnualGdp, newAnnualGrowth,
                String.format("Coincident YoY Real GDP Growth: %.2f%%. Breakdown: Leading momentum (%+.2f%%), Capex multiplier (%+.2f%%), Tax drag (%+.2f%%), Monetary brake (%+.2f%%), Oil $%.0f/bbl (%+.2f%%), Infra delta (%+.2f%%).",
                        newAnnualGrowth, leadingCarryover, capexMultiplier, -taxDrag, -monetaryBrake, s.getBrentCrudePrice(), oilGdpBoost, infraDeltaBoost));

        double oldQtrGdp = s.getGdpGrowthRate();
        s.setGdpGrowthRate(round(newAnnualGrowth / 4.0));
        s.recordChange("gdpGrowthRate", oldQtrGdp, s.getGdpGrowthRate(),
                "Quarterly QoQ GDP growth rate derived from annualized real output expansion (" + newAnnualGrowth + "% / 4).");

        double oldFyGdp = s.getFullYearGdpGrowth();
        s.setFullYearGdpGrowth(round(0.7 * newAnnualGrowth + 0.3 * prevGrowth));
        s.recordChange("fullYearGdpGrowth", oldFyGdp, s.getFullYearGdpGrowth(), "Moving fiscal-year average combining current and prior quarter output.");

        double qtrRealFactor = 1.0 + (s.getGdpGrowthRate() / 100.0);
        double oldConstGdp = s.getGdpConstantPrices();
        s.setGdpConstantPrices(round(oldConstGdp * qtrRealFactor));
        s.recordChange("gdpConstantPrices", oldConstGdp, s.getGdpConstantPrices(), "Real inflation-adjusted GDP in INR Billion expanding at the quarterly real growth rate.");

        double oldGfcf = s.getGrossFixedCapitalFormation();
        s.setGrossFixedCapitalFormation(round(oldGfcf * (qtrRealFactor + (s.getInfraSpend() + s.getMachinerySpend() - 4.9) * 0.004)));
        s.recordChange("grossFixedCapitalFormation", oldGfcf, s.getGrossFixedCapitalFormation(),
                String.format("Driven by real GDP growth plus public Infrastructure (%.1f%%) and Machinery/PLI (%.1f%%) capital formation.", s.getInfraSpend(), s.getMachinerySpend()));

        // FIX #3: Monsoon shock on agriculture GDP
        double oldAgri = s.getGdpFromAgriculture();
        s.setGdpFromAgriculture(round(oldAgri * (1.0 + 0.009 + monsoonShock * 0.025)));
        s.recordChange("gdpFromAgriculture", oldAgri, s.getGdpFromAgriculture(),
                String.format("Agriculture GDP: monsoon index %.0f (%+.2f%% seasonal impact).", s.getMonsoonIndex(), monsoonShock * 0.025));

        double oldMfgGdp = s.getGdpFromManufacturing();
        s.setGdpFromManufacturing(round(oldMfgGdp * (qtrRealFactor + (s.getMachinerySpend() - 1.5) * 0.005)));
        s.recordChange("gdpFromManufacturing", oldMfgGdp, s.getGdpFromManufacturing(),
                "Boosted by PLI Machinery subsidies, lower Corporate Tax, and factory credit availability.");

        double oldConst = s.getGdpFromConstruction();
        s.setGdpFromConstruction(round(oldConst * (qtrRealFactor + (s.getInfraSpend() - 3.4) * 0.006)));
        s.recordChange("gdpFromConstruction", oldConst, s.getGdpFromConstruction(),
                "Directly accelerated by public Infrastructure budget (" + s.getInfraSpend() + "% of GDP) and housing demand.");

        double oldPub = s.getGdpFromPublicAdmin();
        s.setGdpFromPublicAdmin(round(oldPub * qtrRealFactor));
        s.recordChange("gdpFromPublicAdmin", oldPub, s.getGdpFromPublicAdmin(), "Expands with government public services and defense outlays.");

        double oldUtil = s.getGdpFromUtilities();
        s.setGdpFromUtilities(round(oldUtil * (qtrRealFactor + (s.getInfraSpend() - 3.4) * 0.004)));
        s.recordChange("gdpFromUtilities", oldUtil, s.getGdpFromUtilities(), "Power grid, water, and renewable energy expansion tied to Infrastructure capex.");

        double oldMin = s.getGdpFromMining();
        s.setGdpFromMining(round(oldMin * qtrRealFactor));
        s.recordChange("gdpFromMining", oldMin, s.getGdpFromMining(), "Tracks domestic coal, iron ore, and mineral extraction demand.");

        double oldNomGdp = s.getGdpUsdBillion();
        s.setGdpUsdBillion(round(oldNomGdp * (1.0 + ((newAnnualGrowth + newInflation * 0.5) / 400.0))));
        s.recordChange("gdpUsdBillion", oldNomGdp, s.getGdpUsdBillion(), "Nominal USD GDP grows with Real GDP expansion plus domestic price deflator.");

        double oldPerCap = s.getGdpPerCapita();
        s.setGdpPerCapita(round((s.getGdpUsdBillion() * 1000.0) / s.getPopulationMillion()));
        s.recordChange("gdpPerCapita", oldPerCap, s.getGdpPerCapita(), "Nominal GDP divided by total population (" + s.getPopulationMillion() + "M).");

        double oldPpp = s.getGdpPerCapitaPpp();
        s.setGdpPerCapitaPpp(round(s.getGdpPerCapita() * 3.98));
        s.recordChange("gdpPerCapitaPpp", oldPpp, s.getGdpPerCapitaPpp(), "Purchasing Power Parity per capita income adjusted for domestic price levels.");

        double oldGnp = s.getGnpInrCrore();
        s.setGnpInrCrore(round(oldGnp * (1.0 + ((newAnnualGrowth + newInflation) / 400.0))));
        s.recordChange("gnpInrCrore", oldGnp, s.getGnpInrCrore(), "Lagging Gross National Product in INR Crore including net factor income from abroad.");

        // Money & Banking
        s.setReverseRepoRate(round(Math.max(2.0, s.getInterestRate() - 2.15)));
        double oldIbr = s.getInterbankRate();
        s.setInterbankRate(round(s.getInterestRate() + 1.13 + (s.getCashReserveRatio() - 3.0) * 0.15));
        s.recordChange("interbankRate", oldIbr, s.getInterbankRate(), "Call money rate anchored to RBI Repo Rate (" + s.getInterestRate() + "%) and CRR liquidity drain.");

        double oldLend = s.getBankLendingRate();
        s.setBankLendingRate(round(s.getInterestRate() + 3.46 + (s.getCashReserveRatio() - 3.0) * 0.12));
        s.recordChange("bankLendingRate", oldLend, s.getBankLendingRate(), "Commercial bank MCLR lending rate transmitted from RBI Repo Rate and CRR.");

        // FIX #2: credit growth uses real rate gap vs neutral; NPA drag reduces transmission
        double oldLoan = s.getBankLoanGrowthYoY();
        s.setBankLoanGrowthYoY(round(clamp(12.8 + (newAnnualGrowth - gStar) * 0.8 - (realRate - 1.5) * 1.1 - npaDrag * 2.0, 4.0, 22.0)));
        s.recordChange("bankLoanGrowthYoY", oldLoan, s.getBankLoanGrowthYoY(),
                String.format("Credit growth: real rate gap (%+.2f%%), NPA drag -%.2f%%. High NPAs choke transmission even when repo is cut.", -(realRate-1.5)*1.1, npaDrag*2.0));

        // FIX #3: M1 shock (demonetisation-style) applied to money supply
        double oldDep = s.getDepositGrowthYoY();
        s.setDepositGrowthYoY(round(clamp(11.2 + (s.getInterestRate() - rStar) * 0.65 + (newAnnualGrowth - gStar) * 0.4, 5.0, 18.0)));
        s.recordChange("depositGrowthYoY", oldDep, s.getDepositGrowthYoY(), "Higher interest rates incentivize household term deposits in banks.");

        // Trade & External
        double oldExp = s.getExportsUsdBillion();
        double exportExpansion = (s.getItServicesSpend() - 1.1) * 1.8
                + (s.getMachinerySpend() - 1.5) * 1.5
                + (s.getTechSpend() - 0.85) * 1.4
                + (s.getTourismSpend() - 0.5) * 1.2;
        s.setExportsUsdBillion(round(clamp(44.40 + exportExpansion + (s.getCurrencyUsdInr() - 84.4) * 0.25, 25.0, 95.0)));
        s.recordChange("exportsUsdBillion", oldExp, s.getExportsUsdBillion(),
                "Boosted by IT/Digital Services, Machinery PLI manufacturing, Tech R&D competitiveness, and INR exchange rate valuation.");

        double oldImp = s.getImportsUsdBillion();
        // Oil shock on imports: India's crude import bill ~$130B/yr at $85. Each $10 = ~$6.5B/qtr shift
        s.setImportsUsdBillion(round(clamp(74.61 + (newAnnualGrowth - 7.8) * 1.1 - (s.getCustomsDutyRate() - 10.0) * 0.45 + oilShock * 1.6, 35.0, 140.0)));
        s.recordChange("importsUsdBillion", oldImp, s.getImportsUsdBillion(),
                String.format("Crude oil at $%.0f/bbl adds $%.1fB to quarterly import bill vs $85 baseline. GDP growth and Customs Duty also affect import demand.", s.getBrentCrudePrice(), oilShock * 1.6));

        double oldBot = s.getBalanceOfTrade();
        s.setBalanceOfTrade(round(s.getExportsUsdBillion() - s.getImportsUsdBillion()));
        s.recordChange("balanceOfTrade", oldBot, s.getBalanceOfTrade(),
                String.format("Exports ($%.2fB) minus Imports ($%.2fB).", s.getExportsUsdBillion(), s.getImportsUsdBillion()));

        double oldTour = s.getTouristArrivals();
        s.setTouristArrivals(Math.round(clamp(584000.0 * (1.0 + (s.getTourismSpend() - 0.5) * 0.25 + (s.getInfraSpend() - 3.4) * 0.08), 250000, 1500000)));
        s.recordChange("touristArrivals", oldTour, s.getTouristArrivals(),
                String.format("Directly propelled by Tourism budget (%.2f%% of GDP) and airport/rail Infrastructure spend (%.2f%%).", s.getTourismSpend(), s.getInfraSpend()));

        double oldRemit = s.getRemittancesUsdBillion();
        s.setRemittancesUsdBillion(round(29.50 + (s.getItServicesSpend() - 1.1) * 1.2));
        s.recordChange("remittancesUsdBillion", oldRemit, s.getRemittancesUsdBillion(), "Global diaspora inflows supported by India's IT & skilled workforce mobility.");

        double oldFdi = s.getForeignDirectInvestment();
        // Fix #4: FDI corporate tax baseline 25.17%
        s.setForeignDirectInvestment(round(clamp(6.80 + (s.getMachinerySpend() + s.getTechSpend() - 2.35) * 1.5 - (s.getCorporateTaxRate() - 25.17) * 0.25 - (s.getWithholdingTaxRate() - 20.0) * 0.15, 1.0, 25.0)));
        s.recordChange("foreignDirectInvestment", oldFdi, s.getForeignDirectInvestment(),
                "Leading Indicator: Foreign investors respond positively to PLI Machinery & Tech incentives and negatively to high Corporate or Withholding taxes.");

        // Fix #5: currentAccount in USD Billion (consistent units)
        double oldCa = s.getCurrentAccount();
        double servicesNetBillion = (s.getItServicesSpend() * 14.5) + (s.getTourismSpend() * 4.2) + 7.9;
        double newCurrentAccountBillion = round(s.getBalanceOfTrade() + servicesNetBillion);
        s.setCurrentAccount(newCurrentAccountBillion);
        s.recordChange("currentAccount", oldCa, newCurrentAccountBillion,
                "Combines Merchandise Trade Balance with India's strong IT Services & Tourism surplus (USD Billion).");

        double oldCaGdp = s.getCurrentAccountToGdp();
        s.setCurrentAccountToGdp(round((newCurrentAccountBillion * 4.0 / s.getGdpUsdBillion()) * 100.0));
        s.recordChange("currentAccountToGdp", oldCaGdp, s.getCurrentAccountToGdp(), "Annualized Current Account balance as a percentage of Nominal GDP.");

        double oldExpP = s.getExportPrices();
        s.setExportPrices(round(163.50 + (newInflation - 5.5) * 0.8));
        s.recordChange("exportPrices", oldExpP, s.getExportPrices(), "Reflects domestic producer inflation passed into export invoice prices.");

        double oldImpP = s.getImportPrices();
        s.setImportPrices(round(159.80 + (s.getCustomsDutyRate() - 10.0) * 0.9));
        s.recordChange("importPrices", oldImpP, s.getImportPrices(), "Landed import price index affected by Customs Duty tariffs (" + s.getCustomsDutyRate() + "%).");

        double oldTot = s.getTermsOfTrade();
        s.setTermsOfTrade(round((s.getExportPrices() / s.getImportPrices()) * 100.0));
        s.recordChange("termsOfTrade", oldTot, s.getTermsOfTrade(), "Ratio of Export Prices to Import Prices.");

        double oldFxRes = s.getForeignExchangeReserves();
        // Fix #5: FX reserves and external debt use consistent billion units
        s.setForeignExchangeReserves(round(oldFxRes + (s.getForeignDirectInvestment() * 1000.0) + (newCurrentAccountBillion * 350.0)));
        s.recordChange("foreignExchangeReserves", oldFxRes, s.getForeignExchangeReserves(), "RBI Forex reserves accumulate via strong FDI inflows and narrower Current Account deficits.");

        double oldExtDebt = s.getExternalDebtUsdBillion();
        s.setExternalDebtUsdBillion(round(oldExtDebt - (newCurrentAccountBillion / 2.5)));
        s.recordChange("externalDebtUsdBillion", oldExtDebt, s.getExternalDebtUsdBillion(), "External borrowing rises when Current Account Deficit widens.");

        double oldInr = s.getCurrencyUsdInr();
        // FIX #1: INR uses rStar gap + oil shock + CAD pressure
        double fxShift = (newInflation - 4.0) * 0.20
                - (s.getInterestRate() - rStar) * 0.28
                - (s.getForeignDirectInvestment() - 6.8) * 0.15
                - (s.getCurrentAccountToGdp() + 1.5) * 0.12
                + oilShock * 0.20;
        s.setCurrencyUsdInr(round(clamp(oldInr + fxShift, 60.0, 110.0)));
        s.recordChange("currencyUsdInr", oldInr, s.getCurrencyUsdInr(),
                String.format("INR: inflation gap (%+.2f), rate vs r* (%+.2f), FDI (%+.2f), CAD (%+.2f), Brent $%.0f/bbl (%+.2f).",
                        (newInflation-4.0)*0.20, -(s.getInterestRate()-rStar)*0.28,
                        -(s.getForeignDirectInvestment()-6.8)*0.15, -(s.getCurrentAccountToGdp()+1.5)*0.12,
                        s.getBrentCrudePrice(), oilShock*0.20));

        // Business, Consumer & Housing Coincident
        double oldIip = s.getIndustrialProductionYoY();
        s.setIndustrialProductionYoY(round(clamp(4.40 + (s.getMachinerySpend() - 1.5) * 1.6 + (s.getInfraSpend() - 3.4) * 1.2 - (s.getInterestRate() - rStar) * 0.5 - deltaRepo * 0.18, -2.0, 14.0)));
        s.recordChange("industrialProductionYoY", oldIip, s.getIndustrialProductionYoY(), "IIP growth driven by Machinery & Infrastructure spending, moderated by borrowing costs.");

        double oldMfgProd = s.getManufacturingProductionYoY();
        s.setManufacturingProductionYoY(round(clamp(s.getIndustrialProductionYoY() + 0.4, -2.0, 15.0)));
        s.recordChange("manufacturingProductionYoY", oldMfgProd, s.getManufacturingProductionYoY(), "Tracks factory output supported by PLI Machinery schemes and Corporate Tax rates.");

        double oldCapUtil = s.getCapacityUtilization();
        s.setCapacityUtilization(round(clamp(74.5 + (newAnnualGrowth - 7.8) * 1.1, 60.0, 92.0)));
        s.recordChange("capacityUtilization", oldCapUtil, s.getCapacityUtilization(), "Industrial plant utilization rises with aggregate GDP demand.");

        double oldInfraOut = s.getInfrastructureOutputYoY();
        s.setInfrastructureOutputYoY(round(clamp(7.90 + (s.getInfraSpend() - 3.4) * 2.1, 1.0, 16.0)));
        s.recordChange("infrastructureOutputYoY", oldInfraOut, s.getInfrastructureOutputYoY(), "Core 8-industries output (cement, steel, power) directly responds to Infrastructure budget.");

        double oldRetail = s.getRetailSalesGrowthYoY();
        // Fix #4: PIT baseline corrected to 30%; FIX #3: M1 velocity drives informal consumption
        s.setRetailSalesGrowthYoY(round(clamp(newAnnualGrowth - 0.8 - (s.getPersonalIncomeTaxRate() - 30.0) * 0.12 - (s.getSalesTaxGstRate() - 18.0) * 0.15 + m1Shock * 15.0 + (m1Velocity - 0.22) * 8.0, 0.5, 14.0)));
        s.recordChange("retailSalesGrowthYoY", oldRetail, s.getRetailSalesGrowthYoY(),
                String.format("Coincident household consumption: GDP growth, PIT/GST rates, M1 shock %.1f%%, M1 velocity %.3f (informal cash economy channel).", s.getM1ShockPct(), m1Velocity));

        double oldGas = s.getGasolinePrices();
        // Oil shock on gasoline: $10 Brent change = ~$0.07/liter at pump
        s.setGasolinePrices(round(clamp(1.06 + (s.getSalesTaxGstRate() - 18.0) * 0.015 + (s.getCustomsDutyRate() - 10.0) * 0.01 + oilShock * 0.07, 0.55, 2.20)));
        s.recordChange("gasolinePrices", oldGas, s.getGasolinePrices(),
                String.format("Pump price: Brent crude at $%.0f/bbl contributes %+.2f USD/liter vs $85 baseline.", s.getBrentCrudePrice(), oilShock * 0.07));

        double oldHhDebt = s.getHouseholdsDebtToGdp();
        s.setHouseholdsDebtToGdp(round(clamp(oldHhDebt + (s.getBankLoanGrowthYoY() - newAnnualGrowth) * 0.08, 35.0, 68.0)));
        s.recordChange("householdsDebtToGdp", oldHhDebt, s.getHouseholdsDebtToGdp(), "Rises when retail bank loan growth outpaces nominal income growth.");

        // Government Fiscal Accounts
        double oldGovtSpend = s.getGovernmentSpendingToGdp();
        double totalSectorSpendPct = s.getEducationSpend() + s.getHealthSpend() + s.getInfraSpend()
                + s.getTechSpend() + s.getMachinerySpend() + s.getItServicesSpend() + s.getTourismSpend();
        double debtServiceAndFixedPct = 4.60 + (s.getGovernmentDebtToGdp() * (s.getInterestRate() / 100.0) * 0.35);
        double totalGovtSpendPct = round(totalSectorSpendPct + debtServiceAndFixedPct);
        s.setGovernmentSpendingToGdp(totalGovtSpendPct);
        s.recordChange("governmentSpendingToGdp", oldGovtSpend, totalGovtSpendPct,
                String.format("Sum of all 7 Sectoral Budgets (%.2f%% of GDP) plus debt-service interest and defense outlays (%.2f%%).", totalSectorSpendPct, debtServiceAndFixedPct));

        // FIX #4: Tax buoyancy coefficient — formalization & digital spend raises effective tax yield
        double taxBuoyancy = s.getTaxBuoyancyCoeff();
        double taxRevPctGdp = taxBuoyancy * (
                (s.getCorporateTaxRate() * 0.105)
                + (s.getPersonalIncomeTaxRate() * 0.085)
                + (s.getSalesTaxGstRate() * 0.31)
                + (s.getCustomsDutyRate() * 0.065)
                + (s.getWithholdingTaxRate() * 0.04))
                + (newAnnualGrowth - gStar) * 0.16
                + (s.getTechSpend() - 0.85) * 0.12;  // digitization raises compliance

        double oldBudget = s.getGovernmentBudget();
        double fiscalBalancePct = round(taxRevPctGdp - totalGovtSpendPct);
        s.setGovernmentBudget(fiscalBalancePct);
        s.recordChange("governmentBudget", oldBudget, fiscalBalancePct,
                String.format("Fiscal Balance = Tax Revenues (%.2f%% of GDP) minus Total Government Spending (%.2f%% of GDP). Higher tax rates or faster GDP growth narrow the deficit; higher sectoral spending widens it.", taxRevPctGdp, totalGovtSpendPct));

        double oldDebt = s.getGovernmentDebtToGdp();
        double nominalGrowth = (newAnnualGrowth + newInflation) / 100.0;
        double nextDebtToGdp = round(clamp((oldDebt / (1.0 + nominalGrowth * 0.25)) - (fiscalBalancePct * 0.25), 40.0, 130.0));
        s.setGovernmentDebtToGdp(nextDebtToGdp);
        s.recordChange("governmentDebtToGdp", oldDebt, nextDebtToGdp,
                "Lagging Sovereign Debt ratio: Improves when Nominal GDP growth (Real GDP + Inflation) outpaces the Fiscal Deficit; worsens when deficits are large or interest rates rise.");

        double oldRev = s.getGovernmentRevenues();
        s.setGovernmentRevenues(round(s.getGnpInrCrore() * (taxRevPctGdp / 100.0)));
        s.recordChange("governmentRevenues", oldRev, s.getGovernmentRevenues(), "Total direct and indirect tax collections in INR Crore.");

        double oldExpnd = s.getFiscalExpenditure();
        s.setFiscalExpenditure(round(s.getGnpInrCrore() * (totalGovtSpendPct / 100.0)));
        s.recordChange("fiscalExpenditure", oldExpnd, s.getFiscalExpenditure(), "Total Union + State budget expenditure in INR Crore.");

        // Fix #10: Corruption index is slow-moving (3% convergence per quarter ~5-10yr lag)
        double oldCorr = s.getCorruptionIndex();
        double targetCorrIndex = clamp(39.0 + (s.getTechSpend() - 0.85) * 3.5 + (s.getEducationSpend() - 2.9) * 1.5, 25.0, 65.0);
        double newCorrIndex = round(0.97 * oldCorr + 0.03 * targetCorrIndex);
        s.setCorruptionIndex(newCorrIndex);
        s.recordChange("corruptionIndex", oldCorr, newCorrIndex, "Transparency score improves with e-Governance Technology spend and Education.");
        s.setCorruptionRank((int) Math.round(clamp(91 - (newCorrIndex - 39.0) * 1.8, 30, 140)));

        // =====================================================================
        // 3. LEADING INDICATORS & CAUSAL REASONS
        // =====================================================================
        // FIX #1: yield curve uses rStar gap; FIX #3: M1 shock contracts PMI
        double oldYield = s.getYieldCurveSpread();
        double yieldSpread = round(clamp(0.45 - (s.getInterestRate() - rStar) * 0.32 + (capexMultiplier * 0.18) - (fiscalBalancePct + 4.4) * 0.07, -1.5, 2.5));
        s.setYieldCurveSpread(yieldSpread);
        s.recordChange("yieldCurveSpread", oldYield, yieldSpread,
                "10Y-2Y G-Sec: steepens with growth expectations; inverts when repo exceeds r* aggressively.");

        double oldMfgPmi = s.getManufacturingPMI();
        double mfgPmi = round(clamp(54.9 + (s.getMachinerySpend() - 1.5) * 2.2 + (s.getInfraSpend() - infraStar) * 1.6
                - (s.getCorporateTaxRate() - 25.17) * 0.22 - (s.getInterestRate() - rStar) * 0.75
                + m1Shock * 30.0, 42.0, 66.0));  // M1 shock crushes factory orders
        s.setManufacturingPMI(mfgPmi);
        s.recordChange("manufacturingPMI", oldMfgPmi, mfgPmi,
                String.format("Mfg PMI: Machinery/Infra capex, rate vs r* (%+.2f), M1 shock %.1f%%.", -(s.getInterestRate()-rStar)*0.75, s.getM1ShockPct()));

        double oldSvcPmi = s.getServicesPMI();
        double svcPmi = round(clamp(56.2 + (s.getItServicesSpend() - 1.1) * 2.4 + (s.getTourismSpend() - 0.5) * 1.8
                + (s.getTechSpend() - 0.85) * 1.6 - (s.getSalesTaxGstRate() - 18.0) * 0.28
                - (s.getInterestRate() - rStar) * 0.55
                + m1Shock * 25.0                  // one-time M1 shock hits informal services hard
                + (m1Velocity - 0.22) * 12.0, 42.0, 68.0));  // FIX #3: ongoing M1 velocity channel
        s.setServicesPMI(svcPmi);
        s.recordChange("servicesPMI", oldSvcPmi, svcPmi,
                String.format("Services PMI: IT/Tourism/Tech spend, rate vs r* (%+.2f), M1 shock %.1f%%, M1 velocity %.3f.", -(s.getInterestRate()-rStar)*0.55, s.getM1ShockPct(), m1Velocity));

        double oldCompPmi = s.getCompositePMI();
        double compPmi = round(0.45 * mfgPmi + 0.55 * svcPmi);
        s.setCompositePMI(compPmi);
        s.recordChange("compositePMI", oldCompPmi, compPmi, "Weighted average of Manufacturing PMI (45%) and Services PMI (55%).");

        double oldBizConf = s.getBusinessConfidence();
        // Fix #4: Business confidence corporate tax baseline corrected to 25.17%
        double bizConf = round(clamp(111.1 + (compPmi - 55.5) * 2.4 - (s.getCorporateTaxRate() - 25.17) * 0.55 + (s.getInfraSpend() - 3.4) * 2.5, 75.0, 148.0));
        s.setBusinessConfidence(bizConf);
        s.recordChange("businessConfidence", oldBizConf, bizConf,
                "Leading Indicator: Rises with Composite PMI and Infrastructure capex; falls when Corporate Tax increases.");

        double oldConsConf = s.getConsumerConfidence();
        // Fix #4: Consumer confidence PIT baseline corrected to 30%
        double consConf = round(clamp(87.9 - (newInflation - 5.54) * 1.6 - (newUnemp - 5.0) * 1.5
                - (s.getPersonalIncomeTaxRate() - 30.0) * 0.35 + (s.getHealthSpend() + s.getEducationSpend() - 5.0) * 1.8, 55.0, 125.0));
        s.setConsumerConfidence(consConf);
        s.recordChange("consumerConfidence", oldConsConf, consConf,
                "Leading Indicator: Strengthened by low Inflation, low Unemployment, Personal Income Tax relief, and social Health/Education spending.");

        double oldDur = s.getConsumerDurablesIndex();
        s.setConsumerDurablesIndex(round(clamp(oldDur * (1.0 + (consConf - 87.0) * 0.0018 - (s.getInterestRate() - rStar) * 0.004), 75.0, 210.0)));
        s.recordChange("consumerDurablesIndex", oldDur, s.getConsumerDurablesIndex(),
                "Leading Indicator: Household purchases of appliances and electronics depend on Consumer Confidence and EMI interest rates.");

        double oldForm = s.getNetBusinessFormations();
        s.setNetBusinessFormations(round(clamp(52.4 + (bizConf - 111.0) * 0.45 + (s.getTechSpend() - 0.85) * 5.5, 18.0, 110.0)));
        s.recordChange("netBusinessFormations", oldForm, s.getNetBusinessFormations(),
                "Leading Indicator: Startup and MSME registrations driven by Business Confidence and Tech R&D grants.");

        double oldPv = s.getPassengerVehicleSales();
        s.setPassengerVehicleSales(Math.round(clamp(355000.0 * (1.0 + (consConf - 87.9) * 0.003 - (s.getSalesTaxGstRate() - 18.0) * 0.008), 180000, 650000)));
        s.recordChange("passengerVehicleSales", oldPv, s.getPassengerVehicleSales(), "Auto sales respond to Consumer Confidence, auto loan rates, and GST rates.");

        double oldHousIdx = s.getHousingIndex();
        s.setHousingIndex(round(clamp(oldHousIdx * (1.0 + 0.01 + (consConf - 87.9) * 0.0005 - (s.getInterestRate() - rStar) * 0.004), 90.0, 180.0)));
        s.recordChange("housingIndex", oldHousIdx, s.getHousingIndex(), "Leading NHB Residex index sensitive to mortgage interest rates and urban income confidence.");

        double oldProp = s.getResidentialPropertyPricesYoY();
        s.setResidentialPropertyPricesYoY(round(clamp(4.21 + (s.getInfraSpend() - infraStar) * 0.6 - (s.getInterestRate() - rStar) * 0.55, -2.0, 12.0)));
        s.recordChange("residentialPropertyPricesYoY", oldProp, s.getResidentialPropertyPricesYoY(), "Lagging property price appreciation driven by urban transit infrastructure and home loan rates.");

        // FIX #3: M1 shock applied to money supply (demonetisation = m1ShockPct = -19)
        double mGrowth = (1.0 + m1Shock) * (1.0 + ((newAnnualGrowth + newInflation - (s.getCashReserveRatio() - 3.0) * 0.6) / 400.0));
        double oldM1 = s.getMoneySupplyM1();
        s.setMoneySupplyM1(round(oldM1 * mGrowth));
        s.recordChange("moneySupplyM1", oldM1, s.getMoneySupplyM1(),
                String.format("M1 liquidity: nominal growth channel + M1 shock of %.1f%% (0=normal, -19=demonetisation).", s.getM1ShockPct()));
        s.setMoneySupplyM2(round(s.getMoneySupplyM2() * mGrowth));
        s.setMoneySupplyM3(round(s.getMoneySupplyM3() * mGrowth));
        s.setCentralBankBalanceSheet(round(s.getCentralBankBalanceSheet() * 1.012));
        // Reset M1 shock after applying (one-time shock)
        s.setM1ShockPct(0.0);

        double oldSensex = s.getStockMarketSensex();
        // Fix #8: Sensex base return 0.008/qtr (3.2% annualized); Fix #4: tax baseline 25.17%
        double sensexReturn = 0.008 + (compPmi - 55.5) * 0.004 - (s.getCorporateTaxRate() - 25.17) * 0.003 - (s.getInterestRate() - rStar) * 0.007 + (yieldSpread * 0.008);
        s.setStockMarketSensex(round(clamp(oldSensex * (1.0 + sensexReturn), 35000.0, 160000.0)));
        s.recordChange("stockMarketSensex", oldSensex, s.getStockMarketSensex(),
                "Leading Equity Benchmark (BSE Sensex): Driven by forward earnings (Composite PMI), Corporate Tax cuts, lower RBI discount rates, and positive Yield Curve spread.");

        // Executive Turn Summary
        s.setLastTurnSummary(String.format(
                "In %d Q%d, Real Annual GDP Growth moved to %.2f%% (%+.2f%%), CPI Inflation moved to %.2f%% (%+.2f%%), Unemployment is %.2f%% (%+.2f%%), and Fiscal Budget is %.2f%% of GDP. Click any indicator row in the table to inspect its exact causal breakdown.",
                s.getYear(), s.getQuarter(),
                s.getGdpAnnualGrowthRate(), s.getDelta("gdpAnnualGrowthRate"),
                s.getInflationRate(), s.getDelta("inflationRate"),
                s.getUnemploymentRate(), s.getDelta("unemploymentRate"),
                s.getGovernmentBudget()
        ));

        double leadingMomentum = ((compPmi / 56.0) * 0.50) + ((bizConf / 111.1) * 0.25) + ((consConf / 87.9) * 0.25);
        // FIX #1: save period-over-period policy values for next quarter's delta calculations
        s.setPrevInterestRate(s.getInterestRate());
        s.setPrevInfraSpend(s.getInfraSpend());
        s.setPrevLeadingMomentum(leadingMomentum);
        s.setPrevAnnualGdpGrowth(newAnnualGrowth);
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private static double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}