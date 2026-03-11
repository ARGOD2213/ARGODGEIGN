param(
    [string]$OutputPath = "C:\Users\pavan\Desktop\ARGODREIGN\data\industrial_dummy_1gb.csv",
    [int]$TargetSizeMB = 1024,
    [int]$Seed = 2213,
    [string]$FacilityId = "FACTORY-HYD-001",
    [datetime]$StartTime = (Get-Date).AddDays(-30),
    [int]$EventsPerSecond = 90,
    [int]$ProgressEveryRows = 100000
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$targetBytes = [int64]$TargetSizeMB * 1024 * 1024
$rand = [System.Random]::new($Seed)

$outputDir = [System.IO.Path]::GetDirectoryName($OutputPath)
if (-not [string]::IsNullOrWhiteSpace($outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$sensorDefs = @{
    "TEMPERATURE" =         [pscustomobject]@{ Unit = "C";      Category = "Environmental"; NormalMin = 20.0; NormalMax = 34.0; Warn = 38.0; Crit = 50.0; LowIsBad = $false; Binary = $false }
    "HUMIDITY" =            [pscustomobject]@{ Unit = "%";      Category = "Environmental"; NormalMin = 40.0; NormalMax = 68.0; Warn = 75.0; Crit = 92.0; LowIsBad = $false; Binary = $false }
    "CO2" =                 [pscustomobject]@{ Unit = "ppm";    Category = "Environmental"; NormalMin = 380.0; NormalMax = 900.0; Warn = 1200.0; Crit = 2200.0; LowIsBad = $false; Binary = $false }
    "AIR_QUALITY" =         [pscustomobject]@{ Unit = "AQI";    Category = "Environmental"; NormalMin = 20.0; NormalMax = 85.0; Warn = 110.0; Crit = 220.0; LowIsBad = $false; Binary = $false }
    "BAROMETRIC_PRESSURE" = [pscustomobject]@{ Unit = "hPa";    Category = "Environmental"; NormalMin = 1004.0; NormalMax = 1018.0; Warn = 1025.0; Crit = 1040.0; LowIsBad = $false; Binary = $false }

    "VIBRATION" =           [pscustomobject]@{ Unit = "mm_s";   Category = "MachineHealth";  NormalMin = 0.4; NormalMax = 3.8; Warn = 4.5; Crit = 10.0; LowIsBad = $false; Binary = $false }
    "ACOUSTIC_EMISSION" =   [pscustomobject]@{ Unit = "dB";     Category = "MachineHealth";  NormalMin = 45.0; NormalMax = 76.0; Warn = 86.0; Crit = 101.0; LowIsBad = $false; Binary = $false }
    "MOTOR_CURRENT" =       [pscustomobject]@{ Unit = "A";      Category = "MachineHealth";  NormalMin = 38.0; NormalMax = 78.0; Warn = 88.0; Crit = 110.0; LowIsBad = $false; Binary = $false }
    "RPM" =                 [pscustomobject]@{ Unit = "rpm";    Category = "MachineHealth";  NormalMin = 2600.0; NormalMax = 3200.0; Warn = 3350.0; Crit = 3650.0; LowIsBad = $false; Binary = $false }
    "OIL_PRESSURE" =        [pscustomobject]@{ Unit = "bar";    Category = "MachineHealth";  NormalMin = 3.2; NormalMax = 5.8; Warn = 1.5; Crit = 0.5; LowIsBad = $true; Binary = $false }
    "BEARING_TEMPERATURE" = [pscustomobject]@{ Unit = "C";      Category = "MachineHealth";  NormalMin = 34.0; NormalMax = 64.0; Warn = 72.0; Crit = 92.0; LowIsBad = $false; Binary = $false }

    "GAS_LEAK" =             [pscustomobject]@{ Unit = "ppm";    Category = "ChemicalSafety"; NormalMin = 0.0; NormalMax = 45.0; Warn = 450.0; Crit = 950.0; LowIsBad = $false; Binary = $false }
    "SMOKE_DENSITY" =        [pscustomobject]@{ Unit = "obs_m";  Category = "ChemicalSafety"; NormalMin = 0.0; NormalMax = 5.0; Warn = 10.0; Crit = 25.0; LowIsBad = $false; Binary = $false }
    "CHEMICAL_CONCENTRATION"=[pscustomobject]@{ Unit = "mg_m3";  Category = "ChemicalSafety"; NormalMin = 0.0; NormalMax = 28.0; Warn = 50.0; Crit = 150.0; LowIsBad = $false; Binary = $false }
    "WATER_LEAK" =           [pscustomobject]@{ Unit = "binary"; Category = "ChemicalSafety"; NormalMin = 0.0; NormalMax = 0.0; Warn = 1.0; Crit = 1.0; LowIsBad = $false; Binary = $true }

    "VOLTAGE" =             [pscustomobject]@{ Unit = "V";      Category = "EnergyPower";    NormalMin = 218.0; NormalMax = 232.0; Warn = 242.0; Crit = 260.0; LowIsBad = $false; Binary = $false }
    "POWER_CONSUMPTION" =   [pscustomobject]@{ Unit = "kWh";    Category = "EnergyPower";    NormalMin = 30.0; NormalMax = 72.0; Warn = 82.0; Crit = 98.0; LowIsBad = $false; Binary = $false }
    "POWER_FACTOR" =        [pscustomobject]@{ Unit = "ratio";  Category = "EnergyPower";    NormalMin = 0.90; NormalMax = 0.99; Warn = 0.85; Crit = 0.70; LowIsBad = $true; Binary = $false }
    "BATTERY_LEVEL" =       [pscustomobject]@{ Unit = "%";      Category = "EnergyPower";    NormalMin = 72.0; NormalMax = 100.0; Warn = 30.0; Crit = 10.0; LowIsBad = $true; Binary = $false }

    "MOTION" =              [pscustomobject]@{ Unit = "binary"; Category = "PhysicalSecurity"; NormalMin = 0.0; NormalMax = 0.0; Warn = 1.0; Crit = 1.0; LowIsBad = $false; Binary = $true }
    "DOOR_SENSOR" =         [pscustomobject]@{ Unit = "binary"; Category = "PhysicalSecurity"; NormalMin = 0.0; NormalMax = 0.0; Warn = 1.0; Crit = 1.0; LowIsBad = $false; Binary = $true }
    "LOAD_CELL" =           [pscustomobject]@{ Unit = "kg";     Category = "PhysicalSecurity"; NormalMin = 380.0; NormalMax = 820.0; Warn = 900.0; Crit = 1000.0; LowIsBad = $false; Binary = $false }
}

$rotatingSensors = @("VIBRATION","ACOUSTIC_EMISSION","MOTOR_CURRENT","RPM","OIL_PRESSURE","BEARING_TEMPERATURE","VOLTAGE","POWER_CONSUMPTION","POWER_FACTOR")
$processSensors  = @("TEMPERATURE","CO2","CHEMICAL_CONCENTRATION","GAS_LEAK","SMOKE_DENSITY","POWER_CONSUMPTION")
$utilitySensors  = @("VOLTAGE","POWER_CONSUMPTION","POWER_FACTOR","TEMPERATURE","HUMIDITY")
$safetySensors   = @("GAS_LEAK","SMOKE_DENSITY","CHEMICAL_CONCENTRATION","WATER_LEAK","MOTION","DOOR_SENSOR")
$environmentSensors = @("TEMPERATURE","HUMIDITY","CO2","AIR_QUALITY","BAROMETRIC_PRESSURE")

$machineCatalog = @(
    @{ MachineId="AMM-FEED-GAS-COMP-001"; MachineClass="Compressor"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-DESULF-REACT-001"; MachineClass="Reactor"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-PRIM-REFORM-001"; MachineClass="Reformer"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-REFORM-IDFAN-001"; MachineClass="Fan"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-SEC-AIR-COMP-001"; MachineClass="Compressor"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-WH-BOILER-001"; MachineClass="Boiler"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Utility" },
    @{ MachineId="AMM-HTSHIFT-REACT-001"; MachineClass="Reactor"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-LTSHIFT-REACT-001"; MachineClass="Reactor"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-CO2-ABSORB-001"; MachineClass="Absorber"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-CO2-STRIP-001"; MachineClass="Stripper"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-METHANATOR-001"; MachineClass="Reactor"; Cell="Syngas"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-SYNGAS-COMP-001"; MachineClass="Compressor"; Cell="Synthesis"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-LOOP-COMP-001"; MachineClass="Compressor"; Cell="Synthesis"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-CONVERTER-001"; MachineClass="Converter"; Cell="Synthesis"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-CONDENSER-001"; MachineClass="Condenser"; Cell="Synthesis"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-REFRIG-COMP-001"; MachineClass="Compressor"; Cell="Synthesis"; Area="Ammonia-Unit"; ProductStream="Ammonia" },
    @{ MachineId="AMM-STORAGE-TANK-001"; MachineClass="Storage"; Cell="Storage"; Area="Tank-Farm"; ProductStream="Ammonia" },
    @{ MachineId="AMM-TRANSFER-PUMP-001"; MachineClass="Pump"; Cell="Storage"; Area="Tank-Farm"; ProductStream="Ammonia" },

    @{ MachineId="UREA-CO2-COMP-001"; MachineClass="Compressor"; Cell="Urea-HP"; Area="Urea-Unit"; ProductStream="Urea" },
    @{ MachineId="UREA-REACTOR-001"; MachineClass="Reactor"; Cell="Urea-HP"; Area="Urea-Unit"; ProductStream="Urea" },
    @{ MachineId="UREA-STRIPPER-001"; MachineClass="Stripper"; Cell="Urea-HP"; Area="Urea-Unit"; ProductStream="Urea" },
    @{ MachineId="UREA-CARB-CON-001"; MachineClass="Condenser"; Cell="Urea-HP"; Area="Urea-Unit"; ProductStream="Urea" },
    @{ MachineId="UREA-HPSCRUB-001"; MachineClass="Scrubber"; Cell="Urea-HP"; Area="Urea-Unit"; ProductStream="Urea" },
    @{ MachineId="UREA-DECOMP-001"; MachineClass="Reactor"; Cell="Urea-LP"; Area="Urea-Unit"; ProductStream="Urea" },
    @{ MachineId="UREA-EVAP-001"; MachineClass="Evaporator"; Cell="Urea-LP"; Area="Urea-Unit"; ProductStream="Urea" },
    @{ MachineId="UREA-PRILL-TWR-001"; MachineClass="Tower"; Cell="Finishing"; Area="Urea-Finishing"; ProductStream="Urea" },
    @{ MachineId="UREA-GRANULATOR-001"; MachineClass="Granulator"; Cell="Finishing"; Area="Urea-Finishing"; ProductStream="Urea" },
    @{ MachineId="UREA-DRYER-001"; MachineClass="Dryer"; Cell="Finishing"; Area="Urea-Finishing"; ProductStream="Urea" },
    @{ MachineId="UREA-BAGGING-001"; MachineClass="Packaging"; Cell="Finishing"; Area="Urea-Finishing"; ProductStream="Urea" },

    @{ MachineId="UTL-BOILER-001"; MachineClass="Boiler"; Cell="Utilities"; Area="Utility-Block"; ProductStream="Utility" },
    @{ MachineId="UTL-BFW-PUMP-001"; MachineClass="Pump"; Cell="Utilities"; Area="Utility-Block"; ProductStream="Utility" },
    @{ MachineId="UTL-STEAM-TURB-001"; MachineClass="Turbine"; Cell="Utilities"; Area="Utility-Block"; ProductStream="Utility" },
    @{ MachineId="UTL-CT-FAN-001"; MachineClass="Fan"; Cell="Utilities"; Area="Cooling-Tower"; ProductStream="Utility" },
    @{ MachineId="UTL-CW-PUMP-001"; MachineClass="Pump"; Cell="Utilities"; Area="Cooling-Tower"; ProductStream="Utility" },
    @{ MachineId="UTL-CHW-PUMP-001"; MachineClass="Pump"; Cell="Utilities"; Area="Cooling-Tower"; ProductStream="Utility" },
    @{ MachineId="UTL-IA-COMP-001"; MachineClass="Compressor"; Cell="Utilities"; Area="Instrument-Air"; ProductStream="Utility" },
    @{ MachineId="UTL-N2-PKG-001"; MachineClass="Package"; Cell="Utilities"; Area="Utility-Block"; ProductStream="Utility" },
    @{ MachineId="UTL-DM-PUMP-001"; MachineClass="Pump"; Cell="Utilities"; Area="DM-Plant"; ProductStream="Utility" },
    @{ MachineId="UTL-ETP-BLOWER-001"; MachineClass="Blower"; Cell="Utilities"; Area="ETP"; ProductStream="Utility" },
    @{ MachineId="UTL-SCRUBBER-FAN-001"; MachineClass="Fan"; Cell="Utilities"; Area="Stack-Treatment"; ProductStream="Utility" },
    @{ MachineId="UTL-FLARE-PUMP-001"; MachineClass="Pump"; Cell="Utilities"; Area="Flare-Area"; ProductStream="Utility" },
    @{ MachineId="UTL-FIRE-PUMP-001"; MachineClass="Pump"; Cell="Safety"; Area="Fire-Water"; ProductStream="Safety" },
    @{ MachineId="UTL-DG-SET-001"; MachineClass="Generator"; Cell="Safety"; Area="Power-Backup"; ProductStream="Safety" },

    @{ MachineId="SEC-MAIN-GATE-001"; MachineClass="Security"; Cell="Security"; Area="Perimeter"; ProductStream="Security" },
    @{ MachineId="SEC-SERVER-ROOM-001"; MachineClass="Security"; Cell="Security"; Area="Admin-Block"; ProductStream="Security" },
    @{ MachineId="ENV-PLANT-WEATHER-001"; MachineClass="Environmental"; Cell="Environment"; Area="Plant-Wide"; ProductStream="Environment" }
)

$baseLat = 17.3850
$baseLon = 78.4867
for ($i = 0; $i -lt $machineCatalog.Count; $i++) {
    $machineCatalog[$i]["Latitude"] = [math]::Round($baseLat + (($rand.NextDouble() - 0.5) * 0.020), 6)
    $machineCatalog[$i]["Longitude"] = [math]::Round($baseLon + (($rand.NextDouble() - 0.5) * 0.020), 6)
}

function Get-MachineSensors {
    param([string]$machineClass)

    if ($machineClass -in @("Compressor","Pump","Fan","Turbine","Blower","Generator")) { return $rotatingSensors }
    if ($machineClass -in @("Reactor","Converter","Stripper","Condenser","Absorber","Evaporator","Reformer","Scrubber","Tower","Granulator","Dryer")) { return $processSensors }
    if ($machineClass -in @("Storage","Security")) { return $safetySensors }
    if ($machineClass -in @("Boiler","Package","Packaging")) { return $utilitySensors }
    if ($machineClass -eq "Environmental") { return $environmentSensors }
    return $utilitySensors
}

$combos = New-Object System.Collections.Generic.List[object]
foreach ($m in $machineCatalog) {
    $sensors = Get-MachineSensors -machineClass $m.MachineClass
    foreach ($s in $sensors) {
        if ($sensorDefs.ContainsKey($s)) {
            $combos.Add([pscustomobject]@{ Machine = $m; SensorType = $s }) | Out-Null
        }
    }
}

if ($combos.Count -eq 0) {
    throw "No machine-sensor combinations built."
}

$scenarioNames = @("NORMAL","HEATWAVE","HEAVY_RAIN","STORM","COMPRESSOR_DEGRADE","GAS_LEAK")
$scenarioWindowMinutes = 180

function Get-Scenario {
    param([datetime]$ts, [datetime]$start)
    $index = [int][math]::Floor((($ts - $start).TotalMinutes / $scenarioWindowMinutes)) % $scenarioNames.Count
    if ($index -lt 0) { $index = 0 }
    return $scenarioNames[$index]
}

function Get-Weather {
    param([string]$scenario, [System.Random]$rng)
    switch ($scenario) {
        "HEATWAVE" { return [pscustomobject]@{ Temp = 41 + ($rng.NextDouble() * 5); Hum = 28 + ($rng.NextDouble() * 12); Wind = 2 + ($rng.NextDouble() * 3); Cond = "HOT_DRY"; Alert = $true; Note = "Heat stress watch" } }
        "HEAVY_RAIN" { return [pscustomobject]@{ Temp = 24 + ($rng.NextDouble() * 4); Hum = 80 + ($rng.NextDouble() * 15); Wind = 5 + ($rng.NextDouble() * 4); Cond = "HEAVY_RAIN"; Alert = $true; Note = "Rain impact on outdoor operations" } }
        "STORM" { return [pscustomobject]@{ Temp = 26 + ($rng.NextDouble() * 5); Hum = 70 + ($rng.NextDouble() * 20); Wind = 10 + ($rng.NextDouble() * 8); Cond = "STORM"; Alert = $true; Note = "High wind and lightning risk" } }
        "COMPRESSOR_DEGRADE" { return [pscustomobject]@{ Temp = 33 + ($rng.NextDouble() * 4); Hum = 50 + ($rng.NextDouble() * 20); Wind = 3 + ($rng.NextDouble() * 4); Cond = "HAZY"; Alert = $false; Note = "Rotating asset degradation trend" } }
        "GAS_LEAK" { return [pscustomobject]@{ Temp = 31 + ($rng.NextDouble() * 5); Hum = 45 + ($rng.NextDouble() * 20); Wind = 2 + ($rng.NextDouble() * 3); Cond = "INDUSTRIAL_HAZE"; Alert = $true; Note = "Gas incident response mode" } }
        default { return [pscustomobject]@{ Temp = 30 + ($rng.NextDouble() * 5); Hum = 45 + ($rng.NextDouble() * 20); Wind = 2 + ($rng.NextDouble() * 4); Cond = "CLEAR"; Alert = $false; Note = "Normal weather" } }
    }
}

function Get-BaseValue {
    param($def, [System.Random]$rng)

    if ($def.Binary) {
        return $(if ($rng.NextDouble() -lt 0.015) { 1.0 } else { 0.0 })
    }

    if ($def.LowIsBad) {
        $r = $rng.NextDouble()
        if ($r -lt 0.03) { return $def.Crit - ($rng.NextDouble() * [math]::Max(0.1, ($def.Warn - $def.Crit) * 0.4)) }
        if ($r -lt 0.12) { return $def.Warn - ($rng.NextDouble() * [math]::Max(0.1, ($def.Warn - $def.Crit) * 0.6)) }
        return $def.NormalMin + ($rng.NextDouble() * ($def.NormalMax - $def.NormalMin))
    }

    $r = $rng.NextDouble()
    if ($r -lt 0.03) { return $def.Crit + ($rng.NextDouble() * [math]::Max(1.0, ($def.Crit * 0.15))) }
    if ($r -lt 0.15) { return $def.Warn + ($rng.NextDouble() * [math]::Max(1.0, ($def.Crit - $def.Warn))) }
    return $def.NormalMin + ($rng.NextDouble() * ($def.NormalMax - $def.NormalMin))
}

function Apply-Scenario {
    param(
        [double]$value,
        [string]$sensorType,
        [string]$machineClass,
        [string]$area,
        [string]$scenario,
        [System.Random]$rng
    )

    switch ($scenario) {
        "HEATWAVE" {
            if ($sensorType -eq "TEMPERATURE") { $value += 5 + ($rng.NextDouble() * 4) }
            if ($sensorType -eq "BEARING_TEMPERATURE") { $value += 3 + ($rng.NextDouble() * 4) }
            if ($sensorType -eq "POWER_CONSUMPTION") { $value += 4 + ($rng.NextDouble() * 8) }
            if ($sensorType -eq "HUMIDITY") { $value -= 4 + ($rng.NextDouble() * 8) }
        }
        "HEAVY_RAIN" {
            if ($sensorType -eq "HUMIDITY") { $value += 12 + ($rng.NextDouble() * 18) }
            if ($sensorType -eq "BAROMETRIC_PRESSURE") { $value -= 5 + ($rng.NextDouble() * 8) }
            if ($sensorType -eq "WATER_LEAK" -and $rng.NextDouble() -lt 0.08) { $value = 1.0 }
            if ($sensorType -eq "AIR_QUALITY") { $value += 10 + ($rng.NextDouble() * 18) }
        }
        "STORM" {
            if ($sensorType -eq "VOLTAGE") { $value += (($rng.NextDouble() - 0.5) * 24) }
            if ($sensorType -eq "VIBRATION") { $value += 0.8 + ($rng.NextDouble() * 2.2) }
            if ($sensorType -eq "SMOKE_DENSITY") { $value += ($rng.NextDouble() * 2.0) }
        }
        "COMPRESSOR_DEGRADE" {
            if ($machineClass -eq "Compressor") {
                if ($sensorType -in @("VIBRATION","BEARING_TEMPERATURE","MOTOR_CURRENT","ACOUSTIC_EMISSION")) {
                    $value += 1.2 + ($rng.NextDouble() * 3.8)
                }
                if ($sensorType -eq "OIL_PRESSURE") {
                    $value -= 0.4 + ($rng.NextDouble() * 0.8)
                }
            }
        }
        "GAS_LEAK" {
            if ($area -match "Tank|Storage|Flare|Chemical|Ammonia" -and $sensorType -eq "GAS_LEAK") {
                $value += 500 + ($rng.NextDouble() * 900)
            }
            if ($sensorType -eq "CHEMICAL_CONCENTRATION") { $value += 20 + ($rng.NextDouble() * 80) }
            if ($sensorType -eq "SMOKE_DENSITY") { $value += 2 + ($rng.NextDouble() * 8) }
        }
    }

    return $value
}

function Get-Status {
    param($def, [double]$value)

    if ($def.Binary) {
        if ($value -ge 1.0) { return "WARNING" }
        return "NORMAL"
    }

    if ($def.LowIsBad) {
        if ($value -le $def.Crit) { return "CRITICAL" }
        if ($value -le $def.Warn) { return "WARNING" }
        return "NORMAL"
    }

    if ($value -ge $def.Crit) { return "CRITICAL" }
    if ($value -ge $def.Warn) { return "WARNING" }
    return "NORMAL"
}

function Get-AiPackage {
    param([string]$status, [string]$sensorType, [string]$machineId, [System.Random]$rng)

    switch ($status) {
        "CRITICAL" {
            $score = 86 + $rng.Next(0, 14)
            return [pscustomobject]@{
                Score = $score
                Level = "CRITICAL"
                Consensus = $(if ($rng.NextDouble() -lt 0.85) { "AGREE_HIGH" } else { "SPLIT_HIGH" })
                Summary = "Critical deviation on $sensorType for $machineId"
                Action = "Immediate inspection, reduce load, and initiate emergency checklist"
                Eta = "0-2h"
            }
        }
        "WARNING" {
            $score = 62 + $rng.Next(0, 22)
            return [pscustomobject]@{
                Score = $score
                Level = "HIGH"
                Consensus = $(if ($rng.NextDouble() -lt 0.80) { "AGREE_MEDIUM" } else { "SPLIT_MEDIUM" })
                Summary = "Early warning trend on $sensorType for $machineId"
                Action = "Plan maintenance in current shift and monitor trend frequency"
                Eta = "4-12h"
            }
        }
        default {
            $score = 22 + $rng.Next(0, 30)
            return [pscustomobject]@{
                Score = $score
                Level = "LOW"
                Consensus = "AGREE_LOW"
                Summary = "No immediate anomaly detected"
                Action = "Continue normal monitoring"
                Eta = ""
            }
        }
    }
}

function Convert-ToCsvField {
    param($value)

    if ($null -eq $value) {
        return ""
    }

    $text = [string]$value
    if ($text.Contains('"')) {
        $text = $text.Replace('"', '""')
    }
    if ($text.Contains(',') -or $text.Contains('"') -or $text.Contains("`n") -or $text.Contains("`r")) {
        return '"' + $text + '"'
    }
    return $text
}

$state = @{}
$header = "event_id,timestamp,facility_id,area,cell_name,machine_id,machine_class,product_stream,sensor_type,sensor_category,unit,value,status,warning_threshold,critical_threshold,min_value,max_value,avg_value,delta_from_previous,weather_temp_c,weather_humidity_pct,weather_condition,weather_wind_speed_ms,weather_correlation_note,weather_alert_active,ai_risk_score,ai_risk_level,llm_consensus,ai_incident_summary,ai_recommended_action,ai_predicted_failure_eta,sns_message_id,sqs_message_id,latitude,longitude"

$encoding = [System.Text.UTF8Encoding]::new($false)
$bufferSize = 1024 * 1024
$writer = [System.IO.StreamWriter]::new($OutputPath, $false, $encoding, $bufferSize)

$rowCount = 0L
$comboIndex = 0
$startWall = Get-Date
$scenarioCounter = @{}
$newlineBytes = 2
$estimatedBytes = [int64]0

try {
    $writer.WriteLine($header)
    $estimatedBytes = [System.Text.Encoding]::UTF8.GetByteCount($header) + $newlineBytes

    while ($true) {
        $combo = $combos[$comboIndex]
        $comboIndex = ($comboIndex + 1) % $combos.Count

        $machine = $combo.Machine
        $sensorType = $combo.SensorType
        $def = $sensorDefs[$sensorType]

        $timestamp = $StartTime.AddSeconds([math]::Floor($rowCount / $EventsPerSecond))
        $scenario = Get-Scenario -ts $timestamp -start $StartTime
        if (-not $scenarioCounter.ContainsKey($scenario)) { $scenarioCounter[$scenario] = 0 }
        $scenarioCounter[$scenario]++

        $weather = Get-Weather -scenario $scenario -rng $rand

        $value = Get-BaseValue -def $def -rng $rand
        $value = Apply-Scenario -value $value -sensorType $sensorType -machineClass $machine.MachineClass -area $machine.Area -scenario $scenario -rng $rand

        if ($def.Binary) {
            $value = [math]::Round($(if ($value -ge 1.0) { 1.0 } else { 0.0 }), 0)
        } else {
            $value = [math]::Round($value, 2)
        }

        $status = Get-Status -def $def -value $value

        $stateKey = "$($machine.MachineId)|$sensorType"
        if (-not $state.ContainsKey($stateKey)) {
            $state[$stateKey] = [pscustomobject]@{ Prev = $value }
        }
        $prev = [double]$state[$stateKey].Prev
        $delta = [math]::Round(($value - $prev), 2)
        $minValue = [math]::Round([math]::Min($value, $prev), 2)
        $maxValue = [math]::Round([math]::Max($value, $prev), 2)
        $avgValue = [math]::Round((($value + $prev) / 2.0), 2)
        $state[$stateKey].Prev = $value

        $ai = Get-AiPackage -status $status -sensorType $sensorType -machineId $machine.MachineId -rng $rand

        $snsId = ""
        $sqsId = ""
        if ($status -ne "NORMAL") {
            $snsId = [guid]::NewGuid().ToString()
            $sqsId = [guid]::NewGuid().ToString()
        }

        $rowValues = @(
            ($rowCount + 1),
            $timestamp.ToString("yyyy-MM-ddTHH:mm:ssZ"),
            $FacilityId,
            $machine.Area,
            $machine.Cell,
            $machine.MachineId,
            $machine.MachineClass,
            $machine.ProductStream,
            $sensorType,
            $def.Category,
            $def.Unit,
            $value,
            $status,
            $def.Warn,
            $def.Crit,
            $minValue,
            $maxValue,
            $avgValue,
            $delta,
            [math]::Round($weather.Temp, 2),
            [math]::Round($weather.Hum, 2),
            $weather.Cond,
            [math]::Round($weather.Wind, 2),
            $weather.Note,
            $(if ($weather.Alert) { "true" } else { "false" }),
            $ai.Score,
            $ai.Level,
            $ai.Consensus,
            $ai.Summary,
            $ai.Action,
            $ai.Eta,
            $snsId,
            $sqsId,
            $machine.Latitude,
            $machine.Longitude
        )

        $row = ($rowValues | ForEach-Object { Convert-ToCsvField $_ }) -join ","

        $rowBytes = [System.Text.Encoding]::UTF8.GetByteCount($row) + $newlineBytes
        if (($estimatedBytes + $rowBytes) -gt $targetBytes -and $rowCount -gt 0) {
            break
        }

        $writer.WriteLine($row)
        $estimatedBytes += $rowBytes
        $rowCount++

        if (($rowCount % $ProgressEveryRows) -eq 0) {
            $writer.Flush()
            $currentBytes = [math]::Max($estimatedBytes, (Get-Item $OutputPath).Length)
            $pct = [math]::Round(($currentBytes / $targetBytes) * 100, 2)
            $elapsed = (Get-Date) - $startWall
            Write-Host ("Rows: {0:N0} | Size: {1:N2} MB | Target: {2} MB | Progress: {3}% | Elapsed: {4}" -f $rowCount, ($currentBytes / 1MB), $TargetSizeMB, $pct, $elapsed.ToString("hh\:mm\:ss"))
        }
    }
}
finally {
    $writer.Flush()
    $writer.Dispose()
}

$fileInfo = Get-Item $OutputPath
$manifestPath = "$OutputPath.manifest.json"
$manifest = [pscustomobject]@{
    file_path = $OutputPath
    generated_at_utc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    target_size_mb = $TargetSizeMB
    actual_size_bytes = $fileInfo.Length
    actual_size_mb = [math]::Round($fileInfo.Length / 1MB, 2)
    total_rows = $rowCount
    machines = $machineCatalog.Count
    machine_sensor_combinations = $combos.Count
    events_per_second = $EventsPerSecond
    facility_id = $FacilityId
    scenarios = $scenarioCounter
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestPath -Encoding UTF8

Write-Host ""
Write-Host "Generation complete"
Write-Host ("CSV: {0}" -f $OutputPath)
Write-Host ("Manifest: {0}" -f $manifestPath)
Write-Host ("Rows: {0:N0}" -f $rowCount)
Write-Host ("Size: {0:N2} MB" -f ($fileInfo.Length / 1MB))
