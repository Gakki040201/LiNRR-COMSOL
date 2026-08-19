[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ComsolBin = 'F:\COMSOL64\Multiphysics\bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'
$CcStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$ChamberStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$Java = Join-Path $ProjectRoot 'src\java\LiNRR_M10A0_3a_ImportBaseline.java'
$Output = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A0_3a_import_baseline.mph'

$ExpectedCcSha = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
$ExpectedChamberSha = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'

$RunStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot "runs\M10A0_3\$RunStamp`_3a_import_baseline"
$BatchLog = Join-Path $RunDir 'M10A0_3a_cad_import.log'
$ConsoleLog = Join-Path $RunDir 'M10A0_3a_comsol_console_output.txt'
$RuntimeJava = Join-Path $RunDir 'LiNRR_M10A0_3a_RuntimeInputs.java'
$RuntimeClass = Join-Path $RunDir 'LiNRR_M10A0_3a_RuntimeInputs.class'
$ClassFile = Join-Path (Split-Path $Java -Parent) 'LiNRR_M10A0_3a_ImportBaseline.class'

function Require-File {
    param([string]$PathValue, [string]$Code)
    if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf)) {
        throw "$Code`: $PathValue"
    }
}

function Assert-Sha256 {
    param([string]$PathValue, [string]$Expected, [string]$Role)
    $Actual = (Get-FileHash -LiteralPath $PathValue -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($Actual -cne $Expected.ToUpperInvariant()) {
        throw "CAD_SHA256_MISMATCH[$Role]: expected=$Expected actual=$Actual file=$PathValue"
    }
    return $Actual
}

function Java-Escape {
    param([string]$Value)
    return $Value.Replace('\', '\\').Replace('"', '\"').Replace("`r", ' ').Replace("`n", ' ')
}

function Show-Failure {
    param([int]$BatchExit, [string]$Reason)
    Write-Host 'M10A0_3A_FAILURE_DIAGNOSTIC_BEGIN'
    Write-Host "FAILURE_REASON=$Reason"
    Write-Host "BATCH_EXIT=$BatchExit"
    Write-Host "CONSOLE_EXISTS=$(Test-Path -LiteralPath $ConsoleLog -PathType Leaf)"
    Write-Host "BATCH_LOG_EXISTS=$(Test-Path -LiteralPath $BatchLog -PathType Leaf)"
    Write-Host "MPH_EXISTS=$(Test-Path -LiteralPath $Output -PathType Leaf)"
    if (Test-Path -LiteralPath $ConsoleLog -PathType Leaf) {
        Write-Host 'COMSOL_CONSOLE_BEGIN'
        Get-Content -LiteralPath $ConsoleLog | ForEach-Object { Write-Host $_ }
        Write-Host 'COMSOL_CONSOLE_END'
    }
    if (Test-Path -LiteralPath $BatchLog -PathType Leaf) {
        Write-Host 'COMSOL_BATCHLOG_BEGIN'
        Get-Content -LiteralPath $BatchLog | ForEach-Object { Write-Host $_ }
        Write-Host 'COMSOL_BATCHLOG_END'
    }
    Write-Host 'M10A0_3A_FAILURE_DIAGNOSTIC_END'
}

Require-File $Compiler 'COMSOL_COMPILER_MISSING'
Require-File $Batch 'COMSOL_BATCH_MISSING'
Require-File $Java 'JAVA_SOURCE_MISSING'
Require-File $CcStep 'CURRENT_COLLECTOR_STEP_MISSING'
Require-File $ChamberStep 'CHAMBER_STEP_MISSING'

New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

$CcSha = Assert-Sha256 $CcStep $ExpectedCcSha 'CURRENT_COLLECTOR_FLOW_FIELD'
$ChamberSha = Assert-Sha256 $ChamberStep $ExpectedChamberSha 'ELECTROLYTE_CHAMBER'
$CcBytes = (Get-Item -LiteralPath $CcStep).Length
$ChamberBytes = (Get-Item -LiteralPath $ChamberStep).Length

if (Test-Path -LiteralPath $Output -PathType Leaf) {
    Move-Item -LiteralPath $Output -Destination (
        Join-Path $RunDir ('preexisting_' + (Split-Path $Output -Leaf)))
}

$RuntimeSource = @"
public final class LiNRR_M10A0_3a_RuntimeInputs {
    private LiNRR_M10A0_3a_RuntimeInputs() {}
    public static final String CC_STEP = "$(Java-Escape $CcStep)";
    public static final String CHAMBER_STEP = "$(Java-Escape $ChamberStep)";
    public static final String OUTPUT_MPH = "$(Java-Escape $Output)";
    public static final String CC_SHA256 = "$CcSha";
    public static final String CHAMBER_SHA256 = "$ChamberSha";
    public static final Long CC_BYTES = Long.valueOf(${CcBytes}L);
    public static final Long CHAMBER_BYTES = Long.valueOf(${ChamberBytes}L);
}
"@
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($RuntimeJava, $RuntimeSource, $Utf8NoBom)

Push-Location $ProjectRoot
try {
    Write-Host '========== FROZEN M10A0.3a IMPORT BASELINE =========='
    Write-Host "CURRENT_COLLECTOR_SHA256=$CcSha"
    Write-Host "CHAMBER_SHA256=$ChamberSha"

    & $Compiler $RuntimeJava
    $RuntimeCompileExit = $LASTEXITCODE
    if ($RuntimeCompileExit -ne 0) {
        throw "RUNTIME_INPUT_COMPILE_FAILED: exit=$RuntimeCompileExit"
    }
    Require-File $RuntimeClass 'RUNTIME_INPUT_CLASS_MISSING'

    & $Compiler $Java
    $JavaCompileExit = $LASTEXITCODE
    if ($JavaCompileExit -ne 0) {
        throw "COMSOL_JAVA_COMPILE_FAILED: exit=$JavaCompileExit"
    }
    Require-File $ClassFile 'COMPILED_CLASS_NOT_FOUND'

    $BatchRaw = @(
        & $Batch -classpathadd $RunDir -inputfile $ClassFile `
            -outputfile $Output -batchlog $BatchLog 2>&1
    )
    $BatchExit = $LASTEXITCODE
    $BatchLines = @($BatchRaw | ForEach-Object { [string]$_ })
    $BatchLines | ForEach-Object { Write-Host $_ }
    [System.IO.File]::WriteAllLines($ConsoleLog, [string[]]$BatchLines, $Utf8NoBom)

    if ($BatchExit -ne 0) {
        Show-Failure $BatchExit 'NONZERO_COMSOLBATCH_EXIT'
        throw "COMSOL_BATCH_FAILED: exit=$BatchExit"
    }
    foreach ($Required in @($ConsoleLog, $BatchLog, $Output)) {
        if (-not (Test-Path -LiteralPath $Required -PathType Leaf) -or
                (Get-Item -LiteralPath $Required).Length -eq 0) {
            Show-Failure $BatchExit "MISSING_OR_EMPTY[$Required]"
            throw "REQUIRED_OUTPUT_MISSING_OR_EMPTY: $Required"
        }
    }

    foreach ($Marker in @(
            'M10A0_3_BOOT_START',
            'M10A0_3_RUNTIME_INPUTS_PASS',
            'M10A0_3_CAD_KERNEL_PASS',
            'M10A0_3_MODEL_CREATE_PASS',
            'M10A0_3_RAW_COMPONENT_CREATE_PASS',
            'M10A0_3A_IMPORT_BASELINE_PASS')) {
        if (-not (Select-String -LiteralPath $ConsoleLog -SimpleMatch $Marker -Quiet)) {
            Show-Failure $BatchExit "CONSOLE_MARKER_MISSING[$Marker]"
            throw "REQUIRED_CONSOLE_MARKER_MISSING: $Marker"
        }
    }
    foreach ($StepName in @((Split-Path $CcStep -Leaf), (Split-Path $ChamberStep -Leaf))) {
        if (-not (Select-String -LiteralPath $BatchLog -SimpleMatch $StepName -Quiet)) {
            Show-Failure $BatchExit "STEP_EVIDENCE_MISSING[$StepName]"
            throw "STEP_EVIDENCE_MISSING: $StepName"
        }
    }

    $FatalHits = Select-String -Path @($BatchLog, $ConsoleLog) -Pattern @(
        '\berrors?\b', '\bfailed\b', '\bundefined\b', '\bsingular\b',
        '\bout of memory\b', '\bexception\b', '\blicense error\b',
        'M10A0_3_FIRST_FAILED_API', 'M10A0_3_FAILURE_CONTEXT_BEGIN') `
        -CaseSensitive:$false -ErrorAction SilentlyContinue
    if ($FatalHits) {
        Show-Failure $BatchExit 'FATAL_TEXT_IN_COMSOL_EVIDENCE'
        throw 'FATAL_TEXT_FOUND_IN_COMSOL_EVIDENCE'
    }

    $WarningHits = Select-String -Path @($BatchLog, $ConsoleLog) `
        -Pattern @('warning', 'warn:', '警告') -CaseSensitive:$false `
        -ErrorAction SilentlyContinue
    $MphItem = Get-Item -LiteralPath $Output
    Write-Host 'M10A0_3A_IMPORT_BASELINE=PASS'
    Write-Host "RUNTIME_COMSOLCOMPILE_EXIT=$RuntimeCompileExit"
    Write-Host "JAVA_COMSOLCOMPILE_EXIT=$JavaCompileExit"
    Write-Host "COMSOLBATCH_EXIT=$BatchExit"
    Write-Host "WARNING_HITS=$(@($WarningHits).Count)"
    Write-Host "MPH_PATH=$($MphItem.FullName)"
    Write-Host "MPH_BYTES=$($MphItem.Length)"
    Write-Host "MPH_SHA256=$((Get-FileHash -LiteralPath $Output -Algorithm SHA256).Hash.ToUpperInvariant())"
    Write-Host "CONSOLE_LOG_PATH=$ConsoleLog"
    Write-Host "CONSOLE_LOG_SHA256=$((Get-FileHash -LiteralPath $ConsoleLog -Algorithm SHA256).Hash.ToUpperInvariant())"
    Write-Host "BATCH_LOG_PATH=$BatchLog"
    Write-Host "BATCH_LOG_SHA256=$((Get-FileHash -LiteralPath $BatchLog -Algorithm SHA256).Hash.ToUpperInvariant())"
}
finally {
    Pop-Location
}
