[CmdletBinding()]
param(
    [ValidateSet('Strict', 'AutoRepair')]
    [string]$ImportMode = 'Strict'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ComsolBin = 'F:\COMSOL64\Multiphysics\bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'

$CcRelative = 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$ChamberRelative = 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$JavaRelative = 'src\java\LiNRR_M10A0_2_RealCAD_Intake.java'

$CcStep = Join-Path $ProjectRoot $CcRelative
$ChamberStep = Join-Path $ProjectRoot $ChamberRelative
$Java = Join-Path $ProjectRoot $JavaRelative

$ExpectedCcSha = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
$ExpectedChamberSha = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'

$ModeToken = $ImportMode.ToLowerInvariant()
$Output = Join-Path $ProjectRoot "models\generated\LiNRR_M10A0_2_real_cad_$ModeToken.mph"
$RunStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot "runs\M10A0_2\$RunStamp`_$ModeToken"
$Log = Join-Path $RunDir 'M10A0_2_real_cad_import.log'
$ConsoleLog = Join-Path $RunDir 'M10A0_2_comsol_console_output.txt'
$Audit = Join-Path $RunDir 'M10A0_2_comsol_cad_audit.csv'
$StaticAudit = Join-Path $RunDir 'M10A0_2_step_static_audit.txt'
$RuntimeJava = Join-Path $RunDir 'LiNRR_M10A0_2_RuntimeInputs.java'
$RuntimeClass = Join-Path $RunDir 'LiNRR_M10A0_2_RuntimeInputs.class'
$LegacyAutoSave = Join-Path (Split-Path $Java -Parent) 'LiNRR_M10A0_2_RealCAD_Intake_Model.mph'

function Require-File {
    param([Parameter(Mandatory = $true)][string]$PathValue, [string]$Code)
    if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf)) {
        throw "$Code`: $PathValue"
    }
}

function Assert-Sha256 {
    param(
        [Parameter(Mandatory = $true)][string]$PathValue,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Role
    )
    $Actual = (Get-FileHash -LiteralPath $PathValue -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($Actual -cne $Expected.ToUpperInvariant()) {
        throw "CAD_SHA256_MISMATCH[$Role]: expected=$Expected actual=$Actual file=$PathValue"
    }
    return $Actual
}

function Java-Escape {
    param([Parameter(Mandatory = $true)][string]$Value)
    return $Value.Replace('\', '\\').Replace('"', '\"').Replace("`r", ' ').Replace("`n", ' ')
}

function Show-ComsolFailure {
    param([int]$BatchExit)
    Write-Host ''
    Write-Host 'COMSOL_FAILURE_DIAGNOSTIC_BEGIN'
    Write-Host "BATCH_EXIT=$BatchExit"
    Write-Host "MPH_EXISTS=$(Test-Path -LiteralPath $Output -PathType Leaf)"
    Write-Host "AUDIT_EXISTS=$(Test-Path -LiteralPath $Audit -PathType Leaf)"
    if (Test-Path -LiteralPath $ConsoleLog -PathType Leaf) {
        Write-Host 'COMSOL_CONSOLE_BEGIN'
        Get-Content -LiteralPath $ConsoleLog | ForEach-Object { Write-Host $_ }
        Write-Host 'COMSOL_CONSOLE_END'
    } else {
        Write-Host 'COMSOL_CONSOLE_MISSING'
    }
    if (Test-Path -LiteralPath $Log -PathType Leaf) {
        Write-Host 'COMSOL_BATCHLOG_BEGIN'
        Get-Content -LiteralPath $Log | ForEach-Object { Write-Host $_ }
        Write-Host 'COMSOL_BATCHLOG_END'
    } else {
        Write-Host 'COMSOL_BATCHLOG_MISSING'
    }
    Write-Host 'COMSOL_FAILURE_DIAGNOSTIC_END'
}

function Get-StepStaticAudit {
    param(
        [Parameter(Mandatory = $true)][string]$PathValue,
        [Parameter(Mandatory = $true)][string]$Role
    )

    $Text = Get-Content -LiteralPath $PathValue -Raw
    $Schema = if ($Text -match "FILE_SCHEMA\s*\(\(\s*'([^']+)'\s*\)\)") { $Matches[1] } else { 'UNKNOWN' }
    $SourceApp = if ($Text -match "FILE_NAME\s*\([\s\S]*?'SwSTEP 2\.0',\s*'([^']+)'") { $Matches[1] } else { 'UNKNOWN' }
    $LengthUnit = if ($Text -match "SI_UNIT\s*\(\s*\.MILLI\.\s*,\s*\.METRE\.\s*\)") { 'mm' } else { 'UNRESOLVED' }

    $EntityNames = [regex]::Matches($Text, '(?m)^#\d+\s*=\s*([A-Z0-9_]+)\s*\(') |
        ForEach-Object { $_.Groups[1].Value }
    $Counts = @{}
    foreach ($Name in $EntityNames) {
        if (-not $Counts.ContainsKey($Name)) { $Counts[$Name] = 0 }
        $Counts[$Name]++
    }

    [pscustomobject]@{
        Role = $Role
        File = $PathValue
        Bytes = (Get-Item -LiteralPath $PathValue).Length
        Sha256 = (Get-FileHash -LiteralPath $PathValue -Algorithm SHA256).Hash.ToUpperInvariant()
        Schema = $Schema
        SourceApplication = $SourceApp
        LengthUnit = $LengthUnit
        ManifoldSolidBrep = if ($Counts.ContainsKey('MANIFOLD_SOLID_BREP')) { $Counts['MANIFOLD_SOLID_BREP'] } else { 0 }
        AdvancedFaces = if ($Counts.ContainsKey('ADVANCED_FACE')) { $Counts['ADVANCED_FACE'] } else { 0 }
        EdgeCurves = if ($Counts.ContainsKey('EDGE_CURVE')) { $Counts['EDGE_CURVE'] } else { 0 }
        VertexPoints = if ($Counts.ContainsKey('VERTEX_POINT')) { $Counts['VERTEX_POINT'] } else { 0 }
        ClosedShells = if ($Counts.ContainsKey('CLOSED_SHELL')) { $Counts['CLOSED_SHELL'] } else { 0 }
    }
}

Require-File -PathValue $Compiler -Code 'COMSOL_COMPILER_MISSING'
Require-File -PathValue $Batch -Code 'COMSOL_BATCH_MISSING'
Require-File -PathValue $Java -Code 'JAVA_SOURCE_MISSING'
Require-File -PathValue $CcStep -Code 'CURRENT_COLLECTOR_STEP_MISSING'
Require-File -PathValue $ChamberStep -Code 'CHAMBER_STEP_MISSING'

New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

if (Test-Path -LiteralPath $LegacyAutoSave -PathType Leaf) {
    $LegacyBackup = Join-Path $RunDir 'preexisting_LiNRR_M10A0_2_RealCAD_Intake_Model.mph'
    Move-Item -LiteralPath $LegacyAutoSave -Destination $LegacyBackup -Force
    Write-Host "Moved legacy COMSOL auto-save to: $LegacyBackup"
}

$CcSha = Assert-Sha256 -PathValue $CcStep -Expected $ExpectedCcSha -Role 'CURRENT_COLLECTOR_FLOW_FIELD'
$ChamberSha = Assert-Sha256 -PathValue $ChamberStep -Expected $ExpectedChamberSha -Role 'ELECTROLYTE_CHAMBER'
$CcBytes = (Get-Item -LiteralPath $CcStep).Length
$ChamberBytes = (Get-Item -LiteralPath $ChamberStep).Length

$Static = @(
    Get-StepStaticAudit -PathValue $CcStep -Role 'CURRENT_COLLECTOR_FLOW_FIELD'
    Get-StepStaticAudit -PathValue $ChamberStep -Role 'ELECTROLYTE_CHAMBER'
)
$Static | Format-List * | Out-File -LiteralPath $StaticAudit -Encoding utf8

if (Test-Path -LiteralPath $Output -PathType Leaf) {
    $Backup = Join-Path $RunDir ("preexisting_" + (Split-Path $Output -Leaf))
    Move-Item -LiteralPath $Output -Destination $Backup
    Write-Host "Moved pre-existing MPH to: $Backup"
}

Get-ChildItem -Path $ProjectRoot -Recurse `
    -Filter 'LiNRR_M10A0_2_RealCAD_Intake.class' -File `
    -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue

$RuntimeSource = @"
public final class LiNRR_M10A0_2_RuntimeInputs {
    private LiNRR_M10A0_2_RuntimeInputs() {}
    public static final String CC_STEP = "$(Java-Escape $CcStep)";
    public static final String CHAMBER_STEP = "$(Java-Escape $ChamberStep)";
    public static final String OUTPUT_MPH = "$(Java-Escape $Output)";
    public static final String CC_SHA256 = "$CcSha";
    public static final String CHAMBER_SHA256 = "$ChamberSha";
    public static final Long CC_BYTES = Long.valueOf(${CcBytes}L);
    public static final Long CHAMBER_BYTES = Long.valueOf(${ChamberBytes}L);
    public static final String IMPORT_MODE = "$($ImportMode.ToUpperInvariant())";
}
"@

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($RuntimeJava, $RuntimeSource, $Utf8NoBom)

Push-Location $ProjectRoot
try {
    Write-Host '========== M10A0.2 REAL CAD INTAKE =========='
    Write-Host "Project root : $ProjectRoot"
    Write-Host "Import mode  : $ImportMode"
    Write-Host "Current coll.: $CcStep"
    Write-Host "Chamber      : $ChamberStep"
    Write-Host "Output MPH   : $Output"
    Write-Host "Batch log    : $Log"
    Write-Host "Console log  : $ConsoleLog"
    Write-Host "Audit CSV    : $Audit"
    Write-Host "Runtime Java : $RuntimeJava"
    Write-Host ''
    Write-Host "CURRENT_COLLECTOR_SHA256=$CcSha"
    Write-Host "CHAMBER_SHA256=$ChamberSha"
    Write-Host ''

    Write-Host '[1/5] STEP manifest and static audit passed.'

    Write-Host '[2/5] Compiling run-specific runtime input class...'
    & $Compiler $RuntimeJava
    if ($LASTEXITCODE -ne 0) {
        throw "RUNTIME_INPUT_COMPILE_FAILED: exit=$LASTEXITCODE"
    }
    Require-File -PathValue $RuntimeClass -Code 'RUNTIME_INPUT_CLASS_MISSING'

    Write-Host '[3/5] Compiling durable COMSOL Java source...'
    & $Compiler $Java
    if ($LASTEXITCODE -ne 0) {
        throw "COMSOL_JAVA_COMPILE_FAILED: exit=$LASTEXITCODE"
    }

    $ClassFile = Get-ChildItem -Path $ProjectRoot -Recurse `
        -Filter 'LiNRR_M10A0_2_RealCAD_Intake.class' -File |
        Select-Object -First 1

    if ($null -eq $ClassFile) {
        throw 'COMPILED_CLASS_NOT_FOUND'
    }
    Write-Host "Compiled class: $($ClassFile.FullName)"
    Write-Host "Runtime class : $RuntimeClass"

    Write-Host '[4/5] Importing exact STEP solids with COMSOL CAD kernel...'
    $BatchRaw = @(
        & $Batch `
            -classpathadd $RunDir `
            -inputfile $ClassFile.FullName `
            -outputfile $Output `
            -batchlog $Log `
            2>&1
    )
    $BatchExit = $LASTEXITCODE
    $BatchLines = @($BatchRaw | ForEach-Object { [string]$_ })
    $BatchLines | ForEach-Object { Write-Host $_ }
    [System.IO.File]::WriteAllLines($ConsoleLog, [string[]]$BatchLines, $Utf8NoBom)

    if ($BatchExit -ne 0) {
        Show-ComsolFailure -BatchExit $BatchExit
        throw "COMSOL_BATCH_FAILED: exit=$BatchExit log=$Log console=$ConsoleLog"
    }

    if (-not (Test-Path -LiteralPath $Log -PathType Leaf)) {
        throw "COMSOL_BATCHLOG_MISSING: $Log"
    }
    if (-not (Test-Path -LiteralPath $ConsoleLog -PathType Leaf)) {
        throw "COMSOL_CONSOLE_LOG_MISSING: $ConsoleLog"
    }

    $AuditRecords = @(
        $BatchLines |
        Where-Object { $_.StartsWith('CAD_AUDIT_CSV|') } |
        ForEach-Object { $_.Substring('CAD_AUDIT_CSV|'.Length) }
    )

    if ($AuditRecords.Count -lt 3) {
        Show-ComsolFailure -BatchExit $BatchExit
        throw "AUDIT_RECORDS_MISSING_OR_INCOMPLETE: count=$($AuditRecords.Count)"
    }

    [System.IO.File]::WriteAllLines($Audit, $AuditRecords, $Utf8NoBom)

    if (-not (Test-Path -LiteralPath $Output -PathType Leaf)) {
        Show-ComsolFailure -BatchExit $BatchExit
        throw "EXPECTED_MPH_NOT_CREATED: $Output"
    }
    if (-not (Test-Path -LiteralPath $Audit -PathType Leaf)) {
        Show-ComsolFailure -BatchExit $BatchExit
        throw "EXPECTED_AUDIT_NOT_CREATED: $Audit"
    }

    $FatalPatterns = @(
        'Exception',
        'ERROR:',
        'Failed to find',
        'Undefined value',
        'Out of memory',
        'Failed to build geometry',
        'CAD import failed',
        'License error',
        'RUNTIME_INPUT_CLASS_OR_FIELD_MISSING',
        'getenv\.'
    )
    $FatalHits = Select-String -Path @($Log, $ConsoleLog) -Pattern $FatalPatterns `
        -CaseSensitive:$false -ErrorAction SilentlyContinue
    if ($FatalHits) {
        Write-Host 'FATAL_LOG_HITS_BEGIN'
        $FatalHits | ForEach-Object { Write-Host $_.Line }
        Write-Host 'FATAL_LOG_HITS_END'
        throw 'POTENTIAL_FATAL_MESSAGES_IN_COMSOL_LOG'
    }

    if (Test-Path -LiteralPath $LegacyAutoSave -PathType Leaf) {
        $LegacyBackup = Join-Path $RunDir 'postrun_LiNRR_M10A0_2_RealCAD_Intake_Model.mph'
        Move-Item -LiteralPath $LegacyAutoSave -Destination $LegacyBackup -Force
        Write-Host "Quarantined unexpected legacy auto-save: $LegacyBackup"
    }

    Write-Host '[5/5] Verifying MPH and audit outputs...'
    $MphItem = Get-Item -LiteralPath $Output
    $MphSha = (Get-FileHash -LiteralPath $Output -Algorithm SHA256).Hash.ToUpperInvariant()
    $AuditSha = (Get-FileHash -LiteralPath $Audit -Algorithm SHA256).Hash.ToUpperInvariant()

    Write-Host ''
    Write-Host '========== BUILD RESULT =========='
    Write-Host 'M10A0_2_REAL_CAD_IMPORT=PASS'
    Write-Host "IMPORT_MODE=$ImportMode"
    Write-Host "MPH_PATH=$($MphItem.FullName)"
    Write-Host "MPH_BYTES=$($MphItem.Length)"
    Write-Host "MPH_SHA256=$MphSha"
    Write-Host "AUDIT_PATH=$Audit"
    Write-Host "AUDIT_SHA256=$AuditSha"
    Write-Host "STATIC_AUDIT_PATH=$StaticAudit"
    Write-Host "CONSOLE_LOG_PATH=$ConsoleLog"
    Write-Host "CONSOLE_LOG_SHA256=$((Get-FileHash -LiteralPath $ConsoleLog -Algorithm SHA256).Hash.ToUpperInvariant())"
    Write-Host "RUNTIME_INPUT_SOURCE=$RuntimeJava"
    Write-Host "RUNTIME_INPUT_CLASS=$RuntimeClass"
    Write-Host "LOG_PATH=$Log"
    Write-Host ''
    Write-Host 'Expected components:'
    Write-Host '  comp_cc_raw       = exact current collector / gas-flow-field STEP'
    Write-Host '  comp_chamber_raw  = exact electrolyte chamber STEP'
    Write-Host ''
    Write-Host 'No assembly transform, fluid extraction, mesh, physics, study, or solver was created.'
}
finally {
    Pop-Location
}
