package com.byd.tripstats.data.config

enum class Drivetrain {
    FWD,
    RWD,
    AWD
}

data class CarConfig(
    val id: String,
    val displayName: String,
    val drivetrain: Drivetrain,
    val batteryKwh: Double,
    /** Approximate EU kerb mass used for gradient-energy estimation. */
    val estimatedKerbMassKg: Double? = null,
    val wltpKm: Int,
    val referenceConsumptionKwhPer100km: Double,
    val frontTyrePressureBar: Double,
    val rearTyrePressureBar: Double,
    val frontMotorRatedKw: Int? = null,
    val rearMotorRatedKw: Int? = null,
    val cellCount: Int = 0,
    /**
     * Aerodynamic drag area: drag coefficient (Cd) × frontal area (m²).
     * Used for aerodynamic drag energy estimation in the trip energy breakdown.
     * Typical range for BYD EVs: 0.55–0.75 m².
     */
    val cdA: Double? = null,
    /** True for plug-in hybrids. Gates fuel/ICE fields throughout the app. */
    val isPhev: Boolean = false,
    /**
     * Usable traction battery capacity in kWh for PHEV models.
     * Null on BEVs (batteryKwh is the full pack there).
     * On PHEVs, batteryKwh is the gross pack size; this is the usable EV-only portion.
     */
    val phevUsableBatteryKwh: Double? = null,
)

// The reference consumption (kWh/100km) is taken via ev-database.org
object CarCatalog {

    val BYD_SEAL_DYNAMIC_RWD = CarConfig(
        id = "BYD_SEAL_DYNAMIC",
        displayName = "BYD Seal Dynamic",
        drivetrain = Drivetrain.RWD,
        batteryKwh = 61.4,
        estimatedKerbMassKg = 2045.0,
        wltpKm = 460,
        referenceConsumptionKwhPer100km = 17.2,
        frontTyrePressureBar = 2.6,
        rearTyrePressureBar = 2.9,
        cellCount = 128,  // 61.4 kWh @ ~410V → 128S LFP
        cdA = 0.568       // Cd 0.219 × A 2.59 m²
    )

    val BYD_SEAL_PREMIUM_RWD = CarConfig(
        id = "BYD_SEAL_PREMIUM",
        displayName = "BYD Seal Premium",
        drivetrain = Drivetrain.RWD,
        batteryKwh = 82.5,
        estimatedKerbMassKg = 2130.0,
        wltpKm = 570,
        referenceConsumptionKwhPer100km = 17.2,
        frontTyrePressureBar = 2.6,
        rearTyrePressureBar = 2.9,
        cellCount = 172,  // 82.5 kWh @ ~554V → 172S LFP
        cdA = 0.568       // Cd 0.219 × A 2.59 m²
    )

    val BYD_SEAL_EXCELLENCE = CarConfig(
        id = "BYD_SEAL_EXCELLENCE",
        displayName = "BYD Seal Excellence",
        drivetrain = Drivetrain.AWD,
        batteryKwh = 82.5,
        estimatedKerbMassKg = 2260.0,
        wltpKm = 520,
        referenceConsumptionKwhPer100km = 18.5,
        frontTyrePressureBar = 2.6,
        rearTyrePressureBar = 2.9,
        frontMotorRatedKw = 160,
        rearMotorRatedKw = 230,
        cellCount = 172,  // 82.5 kWh @ ~554V → 172S LFP
        cdA = 0.568       // Cd 0.219 × A 2.59 m²
    )

    val BYD_DOLPHIN_STANDARD = CarConfig(
        id = "BYD_DOLPHIN_STANDARD",
        displayName = "BYD Dolphin Active / Boost",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 44.9,
        estimatedKerbMassKg = 1650.0,
        wltpKm = 340,
        referenceConsumptionKwhPer100km = 17.3,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        cellCount = 104,  // 44.9 kWh @ ~333V → 104S LFP
        cdA = 0.682       // Cd 0.29 × A 2.35 m²
    )

    val BYD_DOLPHIN_EXTENDED = CarConfig(
        id = "BYD_DOLPHIN_EXTENDED",
        displayName = "BYD Dolphin Extended / Comfort / Design",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 60.4,
        estimatedKerbMassKg = 1733.0,
        wltpKm = 427,
        referenceConsumptionKwhPer100km = 17.3,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        cellCount = 126,  // 60.4 kWh @ ~403V → 126S LFP
        cdA = 0.682       // Cd 0.29 × A 2.35 m²
    )

    val BYD_ATTO_2_ACTIVE = CarConfig(
        id = "BYD_ATTO_2_ACTIVE",
        displayName = "BYD Atto 2 Active",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 45.1,
        estimatedKerbMassKg = 1570.0,
        wltpKm = 312,
        referenceConsumptionKwhPer100km = 18.7,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        frontMotorRatedKw = 130,
        cellCount = 94,   // EV Database: 45.1 kWh usable, 94 cells, 400V architecture
        cdA = 0.700       // estimate for compact SUV body
    )

    val BYD_ATTO_2_BOOST = CarConfig(
        id = "BYD_ATTO_2_BOOST",
        displayName = "BYD Atto 2 Boost",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 51.1,
        estimatedKerbMassKg = 1610.0,
        wltpKm = 344,
        referenceConsumptionKwhPer100km = 18.7,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        frontMotorRatedKw = 130,
        cellCount = 100,  // estimate: interpolated between Active (94S) and Comfort (112S); 51.1 kWh @ ~320V → 100S LFP
        cdA = 0.700       // estimate for compact SUV body
    )

    val BYD_ATTO_2_COMFORT = CarConfig(
        id = "BYD_ATTO_2_COMFORT",
        displayName = "BYD Atto 2 Comfort",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 64.8,
        estimatedKerbMassKg = 1720.0,
        wltpKm = 430,
        referenceConsumptionKwhPer100km = 19.1,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        frontMotorRatedKw = 150,
        cellCount = 112,  // EV Database: 64.8 kWh usable, 112 cells, 400V architecture
        cdA = 0.700       // estimate for compact SUV body
    )

    val BYD_ATTO_3 = CarConfig(
        id = "BYD_ATTO_3",
        displayName = "BYD Atto 3",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 60.4,
        estimatedKerbMassKg = 1825.0,
        wltpKm = 420,
        referenceConsumptionKwhPer100km = 18.3,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        cellCount = 126,  // 60.4 kWh @ ~403V → 126S LFP
        cdA = 0.694       // Cd 0.272 × A 2.55 m²
    )

    val BYD_SEAL_U_COMFORT = CarConfig(
        id = "BYD_SEAL_U_COMFORT",
        displayName = "BYD Seal U Comfort",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 71.8,
        estimatedKerbMassKg = 2095.0,
        wltpKm = 420,
        referenceConsumptionKwhPer100km = 19.9,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.9,
        cellCount = 184,  // 71.8 kWh @ ~589V → 184S LFP
        cdA = 0.762       // Cd 0.28 × A 2.72 m²
    )

    val BYD_SEAL_U_DESIGN = CarConfig(
        id = "BYD_SEAL_U_DESIGN",
        displayName = "BYD Seal U Design",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 87.0,
        estimatedKerbMassKg = 2222.0,
        wltpKm = 500,
        referenceConsumptionKwhPer100km = 20.5,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.9,
        cellCount = 207,  // 87.0 kWh @ ~663V → 207S LFP (estimate)
        cdA = 0.762       // Cd 0.28 × A 2.72 m² (estimate, same body as Comfort)
    )

    val BYD_DOLPHIN_SURF_ACTIVE = CarConfig(
        id = "BYD_DOLPHIN_SURF_ACTIVE",
        displayName = "BYD Seagull / Dolphin Surf Active",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 30.0,
        estimatedKerbMassKg = 1294.0,
        wltpKm = 220,
        referenceConsumptionKwhPer100km = 13.6,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        cellCount = 94,   // 30.0 kWh @ 301V → 99 Ah (ev-database)
        cdA = 0.653       // Cd 0.29 × A 2.25 m² (estimate)
    )

    val BYD_DOLPHIN_SURF_BOOST = CarConfig(
        id = "BYD_DOLPHIN_SURF_BOOST",
        displayName = "BYD Seagull / Dolphin Surf Boost",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 43.2,
        estimatedKerbMassKg = 1370.0,
        wltpKm = 322,
        referenceConsumptionKwhPer100km = 13.4,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        cellCount = 90,   // 43.2 kWh @ 288V → 150 Ah (ev-database)
        cdA = 0.653       // Cd 0.29 × A 2.25 m² (estimate)
    )

    val BYD_DOLPHIN_SURF_COMFORT = CarConfig(
        id = "BYD_DOLPHIN_SURF_COMFORT",
        displayName = "BYD Seagull / Dolphin Surf Comfort",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 43.2,
        estimatedKerbMassKg = 1390.0,
        wltpKm = 310,
        referenceConsumptionKwhPer100km = 13.9,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        cellCount = 90,   // 43.2 kWh @ 288V → 150 Ah (ev-database)
        cdA = 0.653       // Cd 0.29 × A 2.25 m² (estimate)
    )

    // id kept as "BYD_M6" for backward-compatibility with stored user preferences
    val BYD_M6_SUPERIOR_100KW = CarConfig(
        id = "BYD_M6",
        displayName = "BYD M6 Superior 100kW",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 71.8,
        estimatedKerbMassKg = 1915.0,
        wltpKm = 440,
        referenceConsumptionKwhPer100km = 18.7,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        frontMotorRatedKw = 100,
        cellCount = 132,  // confirmed: 170Ah × 422.4V = 71.8kWh → 422.4/3.2 = 132S Blade LFP
        cdA = 0.868       // Cd 0.33 × A ~2.63 m² (1.81 × 1.69 × 0.86)
    )

    val BYD_M6_SUPERIOR_150KW = CarConfig(
        id = "BYD_M6_SUPERIOR_150KW",
        displayName = "BYD M6 Superior 150kW",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 71.8,
        estimatedKerbMassKg = 1925.0,
        wltpKm = 420,
        referenceConsumptionKwhPer100km = 17.1,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        frontMotorRatedKw = 150,
        cellCount = 132,  // same 71.8kWh pack as Superior 100kW: 132S Blade LFP
        cdA = 0.868
    )

    val BYD_M6_STANDARD_120KW = CarConfig(
        id = "BYD_M6_STANDARD",
        displayName = "BYD M6 Standard 120kW",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 55.4,
        estimatedKerbMassKg = 1870.0,
        wltpKm = 340,
        referenceConsumptionKwhPer100km = 16.5,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        frontMotorRatedKw = 120,
        cellCount = 102,  // estimate: 55.4kWh / (170Ah × 3.2V) ≈ 102S Blade LFP
        cdA = 0.868
    )

    val BYD_SEAL_6_PREMIUM_95KW = CarConfig(
        id = "BYD_SEAL_6_PREMIUM_95KW",
        displayName = "BYD Seal 6 Premium 95kW",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 56.64,
        estimatedKerbMassKg = 1750.0,
        wltpKm = 425,               // WLTC figure; WLTP not published
        referenceConsumptionKwhPer100km = 13.3,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        frontMotorRatedKw = 95,
        cellCount = 118,  // confirmed: 150Ah × 377.6V = 56.64kWh → 377.6/3.2 = 118S Blade LFP
    )

    val BYD_SEAL_6_PREMIUM_160KW = CarConfig(
        id = "BYD_SEAL_6_PREMIUM_160KW",
        displayName = "BYD Seal 6 Premium 160kW",
        drivetrain = Drivetrain.RWD,
        batteryKwh = 56.64,
        estimatedKerbMassKg = 1780.0,
        wltpKm = 405,               // estimate; WLTP not published
        referenceConsumptionKwhPer100km = 14.0,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        rearMotorRatedKw = 160,
        cellCount = 118,  // same 56.64kWh pack as 95kW: 118S Blade LFP
    )

    val BYD_TANG_EV = CarConfig(
        id = "BYD_TANG_EV",
        displayName = "BYD Tang EV",
        drivetrain = Drivetrain.AWD,
        batteryKwh = 108.8,
        estimatedKerbMassKg = 2635.0,
        wltpKm = 530,
        referenceConsumptionKwhPer100km = 22.3,
        frontTyrePressureBar = 2.6,
        rearTyrePressureBar = 2.9,
        frontMotorRatedKw = 163,
        rearMotorRatedKw = 200,
        cellCount = 204,  // 108.8 kWh @ ~640V → 204S Blade LFP (estimate)
        cdA = 0.827       // Cd 0.29 × A 2.85 m² (same body as Tang DM-i)
    )

    val BYD_SEAL_U_DM_I = CarConfig(
        id = "BYD_SEAL_U_DM_I",
        displayName = "BYD Seal U DM-i",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 18.3,
        estimatedKerbMassKg = 2140.0,
        wltpKm = 80,            // EV-only WLTP range
        referenceConsumptionKwhPer100km = 19.0,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.9,
        cellCount = 96,         // 18.3 kWh @ ~307V → 96S LFP
        cdA = 0.925,            // Cd 0.34 × A 2.72 m²
        isPhev = true,
        phevUsableBatteryKwh = 15.2
    )

    val BYD_SEAL_U_DM_I_COMFORT = CarConfig(
        id = "BYD_SEAL_U_DM_I_COMFORT",
        displayName = "BYD Seal U DM-i Comfort",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 27.3,
        estimatedKerbMassKg = 2210.0,
        wltpKm = 125,           // EV-only WLTP range
        referenceConsumptionKwhPer100km = 19.5,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.9,
        frontMotorRatedKw = 145,
        cellCount = 144,        // 27.3 kWh @ ~461V → 144S LFP (estimate)
        cdA = 0.925,            // Cd 0.34 × A 2.72 m² (same body as DM-i FWD)
        isPhev = true,
        phevUsableBatteryKwh = 26.6
    )

    val BYD_SEAL_U_DM_I_DESIGN_AWD = CarConfig(
        id = "BYD_SEAL_U_DM_I_DESIGN_AWD",
        displayName = "BYD Seal U DM-i Design AWD",
        drivetrain = Drivetrain.AWD,
        batteryKwh = 18.3,
        estimatedKerbMassKg = 2100.0,
        wltpKm = 70,            // EV-only WLTP range
        referenceConsumptionKwhPer100km = 20.0,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.9,
        frontMotorRatedKw = 150,
        rearMotorRatedKw = 120,
        cellCount = 96,         // 18.3 kWh @ ~307V → 96S LFP (same pack as FWD)
        cdA = 0.870,            // Cd 0.32 × A 2.72 m²
        isPhev = true,
        phevUsableBatteryKwh = 15.2
    )

    val BYD_SONG_PLUS_DM_I = CarConfig(
        id = "BYD_SONG_PLUS_DM_I",
        displayName = "BYD Song Plus DM-i",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 18.3,
        estimatedKerbMassKg = 1880.0,
        wltpKm = 100,           // EV-only WLTP range
        referenceConsumptionKwhPer100km = 16.0,
        frontTyrePressureBar = 2.4,
        rearTyrePressureBar = 2.4,
        cellCount = 96,         // 18.3 kWh @ ~307V → 96S LFP
        cdA = 0.774,            // Cd 0.29 × A 2.67 m²
        isPhev = true,
        phevUsableBatteryKwh = 15.2
    )

    val BYD_HAN_DM_I = CarConfig(
        id = "BYD_HAN_DM_I",
        displayName = "BYD Han DM-i",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 37.5,
        estimatedKerbMassKg = 2065.0,
        wltpKm = 202,           // EV-only WLTP range
        referenceConsumptionKwhPer100km = 15.8,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        cellCount = 168,        // 37.5 kWh @ ~537V → 168S LFP (estimate)
        cdA = 0.601,            // Cd 0.233 × A 2.58 m²
        isPhev = true,
        phevUsableBatteryKwh = 34.0
    )

    val BYD_TANG_DM_I = CarConfig(
        id = "BYD_TANG_DM_I",
        displayName = "BYD Tang DM-i",
        drivetrain = Drivetrain.AWD,
        batteryKwh = 49.0,
        estimatedKerbMassKg = 2690.0,
        wltpKm = 235,           // EV-only WLTP range
        referenceConsumptionKwhPer100km = 19.0,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.7,
        frontMotorRatedKw = 160,
        rearMotorRatedKw = 160,
        cellCount = 168,        // 49.0 kWh @ ~537V → 168S LFP (estimate)
        cdA = 0.827,            // Cd 0.29 × A 2.85 m²
        isPhev = true,
        phevUsableBatteryKwh = 44.0
    )

    val BYD_SEALION_5_DMI_COMFORT = CarConfig(
        id = "BYD_SEALION_5_DMI_COMFORT",
        displayName = "BYD Seal 5 / Sealion 5 DM-i Comfort",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 15.0,
        estimatedKerbMassKg = 1700.0,
        wltpKm = 61,            // EV-only WLTP range
        referenceConsumptionKwhPer100km = 21.2,
        frontTyrePressureBar = 2.4,
        rearTyrePressureBar = 2.4,
        cellCount = 80,         // 15.0 kWh @ ~258V → 80S LFP (estimate)
        cdA = 0.620,            // estimate — BYD has not published Cd for Sealion 5
        isPhev = true,
        phevUsableBatteryKwh = 12.96
    )

    val BYD_SEALION_5_DMI_DESIGN = CarConfig(
        id = "BYD_SEALION_5_DMI_DESIGN",
        displayName = "BYD Seal 5 / Sealion 5 DM-i Design",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 21.5,
        estimatedKerbMassKg = 1785.0,
        wltpKm = 86,            // EV-only WLTP range
        referenceConsumptionKwhPer100km = 21.3,
        frontTyrePressureBar = 2.4,
        rearTyrePressureBar = 2.4,
        cellCount = 112,        // 21.5 kWh @ ~361V → 112S LFP (estimate)
        cdA = 0.620,            // estimate — BYD has not published Cd for Sealion 5
        isPhev = true,
        phevUsableBatteryKwh = 18.3
    )

    val allCars: List<CarConfig> = listOf(
        BYD_SEAL_DYNAMIC_RWD,
        BYD_SEAL_PREMIUM_RWD,
        BYD_SEAL_EXCELLENCE,
        BYD_DOLPHIN_STANDARD,
        BYD_DOLPHIN_EXTENDED,
        BYD_ATTO_2_ACTIVE,
        BYD_ATTO_2_BOOST,
        BYD_ATTO_2_COMFORT,
        BYD_ATTO_3,
        BYD_SEAL_U_COMFORT,
        BYD_SEAL_U_DESIGN,
        BYD_DOLPHIN_SURF_ACTIVE,
        BYD_DOLPHIN_SURF_BOOST,
        BYD_DOLPHIN_SURF_COMFORT,
        BYD_M6_STANDARD_120KW,
        BYD_M6_SUPERIOR_100KW,
        BYD_M6_SUPERIOR_150KW,
        BYD_SEAL_6_PREMIUM_95KW,
        BYD_SEAL_6_PREMIUM_160KW,
        BYD_TANG_EV,
        BYD_SEAL_U_DM_I,
        BYD_SEAL_U_DM_I_COMFORT,
        BYD_SEAL_U_DM_I_DESIGN_AWD,
        BYD_SONG_PLUS_DM_I,
        BYD_HAN_DM_I,
        BYD_TANG_DM_I,
        BYD_SEALION_5_DMI_COMFORT,
        BYD_SEALION_5_DMI_DESIGN,
    )

    fun fromId(id: String?): CarConfig? {
        return allCars.firstOrNull { it.id == id }
    }

    /**
     * Cars grouped for display in selection screens.
     * Two top-level categories (BEV / PHEV), each with named model groups.
     * Only DiLink 3 vehicles are listed — DiLink 4/5 are unsupported.
     */
    val groupedBev: LinkedHashMap<String, List<CarConfig>> = linkedMapOf(
        "BYD Seal" to listOf(BYD_SEAL_DYNAMIC_RWD, BYD_SEAL_PREMIUM_RWD, BYD_SEAL_EXCELLENCE),
        "BYD Dolphin" to listOf(BYD_DOLPHIN_STANDARD, BYD_DOLPHIN_EXTENDED),
        "BYD Atto 2" to listOf(BYD_ATTO_2_ACTIVE, BYD_ATTO_2_BOOST, BYD_ATTO_2_COMFORT),
        "BYD Atto 3" to listOf(BYD_ATTO_3),
        "BYD Seal U" to listOf(BYD_SEAL_U_COMFORT, BYD_SEAL_U_DESIGN),
        "BYD Seagull / Dolphin Surf" to listOf(BYD_DOLPHIN_SURF_ACTIVE, BYD_DOLPHIN_SURF_BOOST, BYD_DOLPHIN_SURF_COMFORT),
        "BYD M6" to listOf(BYD_M6_STANDARD_120KW, BYD_M6_SUPERIOR_100KW, BYD_M6_SUPERIOR_150KW),
        "BYD Seal 6" to listOf(BYD_SEAL_6_PREMIUM_95KW, BYD_SEAL_6_PREMIUM_160KW),
        "BYD Tang" to listOf(BYD_TANG_EV),
    )

    val groupedPhev: LinkedHashMap<String, List<CarConfig>> = linkedMapOf(
        "BYD Seal U DM-i" to listOf(BYD_SEAL_U_DM_I, BYD_SEAL_U_DM_I_COMFORT, BYD_SEAL_U_DM_I_DESIGN_AWD),
        "BYD Song Plus DM-i" to listOf(BYD_SONG_PLUS_DM_I),
        "BYD Han DM-i" to listOf(BYD_HAN_DM_I),
        "BYD Tang DM-i" to listOf(BYD_TANG_DM_I),
        "BYD Seal 5 / Sealion 5 DM-i" to listOf(BYD_SEALION_5_DMI_COMFORT, BYD_SEALION_5_DMI_DESIGN),
    )
}
