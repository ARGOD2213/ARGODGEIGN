package com.mahindra.iot.enums;

public enum SensorType {
    // Environmental
    TEMPERATURE("°C", "Environmental"),
    HUMIDITY("%", "Environmental"),
    CO2("ppm", "Environmental"),
    AIR_QUALITY("AQI", "Environmental"),
    BAROMETRIC_PRESSURE("hPa", "Environmental"),

    // Machine Health
    VIBRATION("mm/s", "MachineHealth"),
    ACOUSTIC_EMISSION("dB", "MachineHealth"),
    MOTOR_CURRENT("A", "MachineHealth"),
    RPM("rpm", "MachineHealth"),
    OIL_PRESSURE("bar", "MachineHealth"),
    BEARING_TEMPERATURE("°C", "MachineHealth"),

    // Chemical & Safety
    GAS_LEAK("ppm", "ChemicalSafety"),
    SMOKE_DENSITY("%obs/m", "ChemicalSafety"),
    CHEMICAL_CONCENTRATION("mg/m³", "ChemicalSafety"),
    WATER_LEAK("binary", "ChemicalSafety"),

    // Energy & Power
    VOLTAGE("V", "EnergyPower"),
    POWER_CONSUMPTION("kWh", "EnergyPower"),
    POWER_FACTOR("", "EnergyPower"),
    BATTERY_LEVEL("%", "EnergyPower"),

    // Physical & Security
    MOTION("binary", "PhysicalSecurity"),
    DOOR_SENSOR("binary", "PhysicalSecurity"),
    LOAD_CELL("kg", "PhysicalSecurity");

    private final String unit;
    private final String category;

    SensorType(String unit, String category) {
        this.unit = unit;
        this.category = category;
    }

    public String getUnit() { return unit; }
    public String getCategory() { return category; }

    public static SensorType fromString(String value) {
        try {
            return SensorType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return TEMPERATURE; // safe default
        }
    }
}
