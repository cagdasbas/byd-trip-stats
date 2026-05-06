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

    val BYD_M6 = CarConfig(
        id = "BYD_M6",
        displayName = "BYD M6",
        drivetrain = Drivetrain.FWD,
        batteryKwh = 71.8,
        estimatedKerbMassKg = 1915.0,
        wltpKm = 440,
        referenceConsumptionKwhPer100km = 18.7,
        frontTyrePressureBar = 2.5,
        rearTyrePressureBar = 2.5,
        frontMotorRatedKw = 100,
        cellCount = 184,  // 71.8 kWh @ ~589V → 184S LFP (estimate, same pack as Seal U Comfort)
        cdA = 0.868       // Cd 0.33 × A ~2.63 m² (1.81 × 1.69 × 0.86)
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
        BYD_ATTO_2_COMFORT,
        BYD_ATTO_3,
        BYD_SEAL_U_COMFORT,
        BYD_SEAL_U_DESIGN,
        BYD_DOLPHIN_SURF_ACTIVE,
        BYD_DOLPHIN_SURF_BOOST,
        BYD_DOLPHIN_SURF_COMFORT,
        BYD_M6,
        BYD_SEAL_U_DM_I,
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
        "BYD Atto 2" to listOf(BYD_ATTO_2_ACTIVE, BYD_ATTO_2_COMFORT),
        "BYD Atto 3" to listOf(BYD_ATTO_3),
        "BYD Seal U" to listOf(BYD_SEAL_U_COMFORT, BYD_SEAL_U_DESIGN),
        "BYD Seagull / Dolphin Surf" to listOf(BYD_DOLPHIN_SURF_ACTIVE, BYD_DOLPHIN_SURF_BOOST, BYD_DOLPHIN_SURF_COMFORT),
        "BYD M6" to listOf(BYD_M6),
    )

    val groupedPhev: LinkedHashMap<String, List<CarConfig>> = linkedMapOf(
        "BYD Seal U DM-i" to listOf(BYD_SEAL_U_DM_I),
        "BYD Song Plus DM-i" to listOf(BYD_SONG_PLUS_DM_I),
        "BYD Han DM-i" to listOf(BYD_HAN_DM_I),
        "BYD Tang DM-i" to listOf(BYD_TANG_DM_I),
        "BYD Seal 5 / Sealion 5 DM-i" to listOf(BYD_SEALION_5_DMI_COMFORT, BYD_SEALION_5_DMI_DESIGN),
    )
}
