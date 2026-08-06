[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ComsolBin = 'F:\COMSOL64\Multiphysics\bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'

$CcRelative = 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$ChamberRelative = 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$JavaRelative = 'src\java\LiNRR_M10A0_3_CAD_Registration.java'

$CcStep = Join-Path $ProjectRoot $CcRelative
$ChamberStep = Join-Path $ProjectRoot $ChamberRelative
$Java = Join-Path $ProjectRoot $JavaRelative

$ExpectedCcSha = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
$ExpectedChamberSha = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'

# This script is intentionally locked to the first diagnostic increment.
$Stage = 'M10A0.3a'
$Output = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A0_3a_import_baseline.mph'
$RunStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot "runs\M10A0_3\$RunStamp`_3a_import_baseline"
$BatchLog = Join-Path $RunDir 'M10A0_3a_cad_import.log'
$ConsoleLog = Join-Path $RunDir 'M10A0_3a_comsol_console_output.txt'
$RuntimeJava = Join-Path $RunDir 'LiNRR_M10A0_3_RuntimeInputs.java'
$RuntimeClass = Join-Path $RunDir 'LiNRR_M10A0_3_RuntimeInputs.class'
$ClassFile = Join-Path (Split-Path $Java -Parent) 'LiNRR_M10A0_3_CAD_Registration.class'

function Require-File {
    param(
        [Parameter(Mandatory = $true)][string]$PathValue,
        [Parameter(Mandatory = $true)][string]$Code
    )
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

function Show-Failure {
    param(
        [int]$BatchExit,
        [string]$Reason
    )

    Write-Host ''
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
    } else {
        Write-Host 'COMSOL_CONSOLE_MISSING'
    }

    if (Test-Path -LiteralPath $BatchLog -PathType Leaf) {
        Write-Host 'COMSOL_BATCHLOG_BEGIN'
        Get-Content -LiteralPath $BatchLog | ForEach-Object { Write-Host $_ }
        Write-Host 'COMSOL_BATCHLOG_END'
    } else {
        Write-Host 'COMSOL_BATCHLOG_MISSING'
    }

    Write-Host 'M10A0_3A_FAILURE_DIAGNOSTIC_END'
}

Require-File -PathValue $Compiler -Code 'COMSOL_COMPILER_MISSING'
Require-File -PathValue $Batch -Code 'COMSOL_BATCH_MISSING'
Require-File -PathValue $Java -Code 'JAVA_SOURCE_MISSING'
Require-File -PathValue $CcStep -Code 'CURRENT_COLLECTOR_STEP_MISSING'
Require-File -PathValue $ChamberStep -Code 'CHAMBER_STEP_MISSING'

New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

$CcSha = Assert-Sha256 -PathValue $CcStep -Expected $ExpectedCcSha -Role 'CURRENT_COLLECTOR_FLOW_FIELD'
$ChamberSha = Assert-Sha256 -PathValue $ChamberStep -Expected $ExpectedChamberSha -Role 'ELECTROLYTE_CHAMBER'
$CcBytes = (Get-Item -LiteralPath $CcStep).Length
$ChamberBytes = (Get-Item -LiteralPath $ChamberStep).Length

if (Test-Path -LiteralPath $Output -PathType Leaf) {
    $Backup = Join-Path $RunDir ('preexisting_' + (Split-Path $Output -Leaf))
    Move-Item -LiteralPath $Output -Destination $Backup
    Write-Host "Moved pre-existing stage output to: $Backup"
}

$RuntimeSource = @"
public final class LiNRR_M10A0_3_RuntimeInputs {
    private LiNRR_M10A0_3_RuntimeInputs() {}
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
    Write-Host '========== M10A0.3a IMPORT BASELINE =========='
    Write-Host "Stage        : $Stage"
    Write-Host "Project root : $ProjectRoot"
    Write-Host "Current coll.: $CcStep"
    Write-Host "Chamber      : $ChamberStep"
    Write-Host "Output MPH   : $Output"
    Write-Host "Batch log    : $BatchLog"
    Write-Host "Console log  : $ConsoleLog"
    Write-Host "CURRENT_COLLECTOR_SHA256=$CcSha"
    Write-Host "CHAMBER_SHA256=$ChamberSha"
    Write-Host ''

    Write-Host '[1/5] Source CAD integrity passed.'

    Write-Host '[2/5] Compiling run-specific runtime input class...'
    & $Compiler $RuntimeJava
    $RuntimeCompileExit = $LASTEXITCODE
    if ($RuntimeCompileExit -ne 0) {
        throw "RUNTIME_INPUT_COMPILE_FAILED: exit=$RuntimeCompileExit"
    }
    Require-File -PathValue $RuntimeClass -Code 'RUNTIME_INPUT_CLASS_MISSING'

    Write-Host '[3/5] Compiling M10A0.3a COMSOL Java source...'
    & $Compiler $Java
    $JavaCompileExit = $LASTEXITCODE
    if ($JavaCompileExit -ne 0) {
        throw "COMSOL_JAVA_COMPILE_FAILED: exit=$JavaCompileExit"
    }
    Require-File -PathValue $ClassFile -Code 'COMPILED_CLASS_NOT_FOUND'

    Write-Host "Compiled class: $ClassFile"
    Write-Host "Runtime class : $RuntimeClass"

    Write-Host '[4/5] Running only the two-STEP strict import baseline...'
    $BatchRaw = @(
        & $Batch `
            -classpathadd $RunDir `
            -inputfile $ClassFile `
            -outputfile $Output `
            -batchlog $BatchLog `
            2>&1
    )
    $BatchExit = $LASTEXITCODE
    $BatchLines = @($BatchRaw | ForEach-Object { [string]$_ })
    $BatchLines | ForEach-Object { Write-Host $_ }
    [System.IO.File]::WriteAllLines($ConsoleLog, [string[]]$BatchLines, $Utf8NoBom)

    # All four independent success signals are mandatory. Exit code alone is
    # never accepted as evidence of a successful COMSOL stage.
    if ($BatchExit -ne 0) {
        Show-Failure -BatchExit $BatchExit -Reason 'NONZERO_COMSOLBATCH_EXIT'
        throw "COMSOL_BATCH_FAILED: exit=$BatchExit"
    }

    if (-not (Test-Path -LiteralPath $ConsoleLog -PathType Leaf) -or
            (Get-Item -LiteralPath $ConsoleLog).Length -eq 0) {
        Show-Failure -BatchExit $BatchExit -Reason 'CONSOLE_OUTPUT_MISSING_OR_EMPTY'
        throw "COMSOL_CONSOLE_OUTPUT_MISSING_OR_EMPTY: $ConsoleLog"
    }

    if (-not (Test-Path -LiteralPath $BatchLog -PathType Leaf) -or
            (Get-Item -LiteralPath $BatchLog).Length -eq 0) {
        Show-Failure -BatchExit $BatchExit -Reason 'BATCH_LOG_MISSING_OR_EMPTY'
        throw "COMSOL_BATCH_LOG_MISSING_OR_EMPTY: $BatchLog"
    }

    if (-not (Test-Path -LiteralPath $Output -PathType Leaf) -or
            (Get-Item -LiteralPath $Output).Length -eq 0) {
        Show-Failure -BatchExit $BatchExit -Reason 'MPH_MISSING_OR_EMPTY'
        throw "EXPECTED_MPH_MISSING_OR_EMPTY: $Output"
    }

    $RequiredConsoleMarkers = @(
        'M10A0_3_BOOT_START',
        'M10A0_3_RUNTIME_INPUTS_PASS',
        'M10A0_3_CAD_KERNEL_PASS',
        'M10A0_3_MODEL_CREATE_PASS',
        'M10A0_3_RAW_COMPONENT_CREATE_PASS',
        'M10A0_3A_IMPORT_BASELINE_PASS'
    )
    foreach ($Marker in $RequiredConsoleMarkers) {
        if (-not (Select-String -LiteralPath $ConsoleLog -SimpleMatch $Marker -Quiet)) {
            Show-Failure -BatchExit $BatchExit -Reason "CONSOLE_MARKER_MISSING[$Marker]"
            throw "REQUIRED_CONSOLE_MARKER_MISSING: $Marker"
        }
    }

    $RequiredBatchEvidence = @(
        (Split-Path $CcStep -Leaf),
        (Split-Path $ChamberStep -Leaf)
    )
    foreach ($Evidence in $RequiredBatchEvidence) {
        if (-not (Select-String -LiteralPath $BatchLog -SimpleMatch $Evidence -Quiet)) {
            Show-Failure -BatchExit $BatchExit -Reason "BATCH_LOG_STEP_EVIDENCE_MISSING[$Evidence]"
            throw "REQUIRED_BATCH_LOG_STEP_EVIDENCE_MISSING: $Evidence"
        }
    }

    $FatalPatterns = @(
        '\berrors?\b',
        '\bfailed\b',
        '\bundefined\b',
        '\bsingular\b',
        '\bout of memory\b',
        '\bexception\b',
        '\blicense error\b',
        'M10A0_3_FIRST_FAILED_API',
        'M10A0_3_FAILURE_CONTEXT_BEGIN'
    )
    $FatalHits = Select-String `
        -Path @($BatchLog, $ConsoleLog) `
        -Pattern $FatalPatterns `
        -CaseSensitive:$false `
        -ErrorAction SilentlyContinue
    if ($FatalHits) {
        Write-Host 'FATAL_LOG_HITS_BEGIN'
        $FatalHits | ForEach-Object { Write-Host "$($_.Path):$($_.LineNumber):$($_.Line)" }
        Write-Host 'FATAL_LOG_HITS_END'
        Show-Failure -BatchExit $BatchExit -Reason 'FATAL_TEXT_IN_COMSOL_EVIDENCE'
        throw 'FATAL_TEXT_FOUND_IN_COMSOL_EVIDENCE'
    }

    $WarningHits = Select-String `
        -Path @($BatchLog, $ConsoleLog) `
        -Pattern @('warning', 'warn:', '警告') `
        -CaseSensitive:$false `
        -ErrorAction SilentlyContinue

    Write-Host '[5/5] All four success signals passed.'

    $MphItem = Get-Item -LiteralPath $Output
    $MphSha = (Get-FileHash -LiteralPath $Output -Algorithm SHA256).Hash.ToUpperInvariant()
    $BatchLogSha = (Get-FileHash -LiteralPath $BatchLog -Algorithm SHA256).Hash.ToUpperInvariant()
    $ConsoleSha = (Get-FileHash -LiteralPath $ConsoleLog -Algorithm SHA256).Hash.ToUpperInvariant()

    Write-Host ''
    Write-Host '========== BUILD RESULT =========='
    Write-Host 'M10A0_3A_IMPORT_BASELINE=PASS'
    Write-Host "COMSOLBATCH_EXIT=$BatchExit"
    Write-Host 'CONSOLE_OUTPUT_CHECK=PASS'
    Write-Host 'BATCH_LOG_CHECK=PASS'
    Write-Host 'MPH_EXISTS_CHECK=PASS'
    Write-Host "WARNING_HITS=$(@($WarningHits).Count)"
    if ($WarningHits) {
        Write-Host 'WARNING_LOG_HITS_BEGIN'
        $WarningHits | ForEach-Object { Write-Host "$($_.Path):$($_.LineNumber):$($_.Line)" }
        Write-Host 'WARNING_LOG_HITS_END'
    }
    Write-Host "MPH_PATH=$($MphItem.FullName)"
    Write-Host "MPH_BYTES=$($MphItem.Length)"
    Write-Host "MPH_SHA256=$MphSha"
    Write-Host "CONSOLE_LOG_PATH=$ConsoleLog"
    Write-Host "CONSOLE_LOG_SHA256=$ConsoleSha"
    Write-Host "BATCH_LOG_PATH=$BatchLog"
    Write-Host "BATCH_LOG_SHA256=$BatchLogSha"
    Write-Host "RUNTIME_INPUT_SOURCE=$RuntimeJava"
    Write-Host "RUNTIME_INPUT_CLASS=$RuntimeClass"
    Write-Host ''
    Write-Host 'No Rotate, Move, Mirror, Assembly, Selection, material, mesh, physics, study, or solver was created.'
}
finally {
    Pop-Location
}
