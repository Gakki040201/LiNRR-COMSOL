[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ComsolBin = 'F:\COMSOL64\Multiphysics\bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'
$CaptureLibrary = Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1'
$CcStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$ChamberStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$Java = Join-Path $ProjectRoot 'src\java\LiNRR_M10A0_3b_RotateTop.java'
$Output = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A0_3b_rotate_top.mph'

$ExpectedCcSha = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
$ExpectedChamberSha = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'

$RunStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot "runs\M10A0_3\$RunStamp`_3b_rotate_top"
$BatchLog = Join-Path $RunDir 'M10A0_3b_rotate_top.log'
$StdoutLog = Join-Path $RunDir 'M10A0_3b_stdout.txt'
$StderrLog = Join-Path $RunDir 'M10A0_3b_stderr.txt'
$ConsoleLog = Join-Path $RunDir 'M10A0_3b_console_merged.txt'
$RuntimeJava = Join-Path $RunDir 'LiNRR_M10A0_3b_RuntimeInputs.java'
$RuntimeClass = Join-Path $RunDir 'LiNRR_M10A0_3b_RuntimeInputs.class'
$ClassFile = Join-Path (Split-Path $Java -Parent) 'LiNRR_M10A0_3b_RotateTop.class'

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
    Write-Host ''
    Write-Host 'M10A0_3B_FAILURE_DIAGNOSTIC_BEGIN'
    Write-Host "FAILURE_REASON=$Reason"
    Write-Host "BATCH_EXIT=$BatchExit"
    Write-Host "CONSOLE_EXISTS=$(Test-Path -LiteralPath $ConsoleLog -PathType Leaf)"
    Write-Host "STDOUT_EXISTS=$(Test-Path -LiteralPath $StdoutLog -PathType Leaf)"
    Write-Host "STDERR_EXISTS=$(Test-Path -LiteralPath $StderrLog -PathType Leaf)"
    Write-Host "BATCH_LOG_EXISTS=$(Test-Path -LiteralPath $BatchLog -PathType Leaf)"
    Write-Host "MPH_EXISTS=$(Test-Path -LiteralPath $Output -PathType Leaf)"
    if (Test-Path -LiteralPath $ConsoleLog -PathType Leaf) {
        Write-Host 'COMSOL_CONSOLE_BEGIN'
        Get-Content -LiteralPath $ConsoleLog | ForEach-Object { Write-Host $_ }
        Write-Host 'COMSOL_CONSOLE_END'
    } else {
        Write-Host 'COMSOL_CONSOLE_MISSING'
    }
    if (Test-Path -LiteralPath $StdoutLog -PathType Leaf) {
        Write-Host 'COMSOL_STDOUT_BEGIN'
        Get-Content -LiteralPath $StdoutLog | ForEach-Object { Write-Host $_ }
        Write-Host 'COMSOL_STDOUT_END'
    }
    if (Test-Path -LiteralPath $StderrLog -PathType Leaf) {
        Write-Host 'COMSOL_STDERR_BEGIN'
        Get-Content -LiteralPath $StderrLog | ForEach-Object { Write-Host $_ }
        Write-Host 'COMSOL_STDERR_END'
    }
    if (Test-Path -LiteralPath $BatchLog -PathType Leaf) {
        Write-Host 'COMSOL_BATCHLOG_BEGIN'
        Get-Content -LiteralPath $BatchLog | ForEach-Object { Write-Host $_ }
        Write-Host 'COMSOL_BATCHLOG_END'
    } else {
        Write-Host 'COMSOL_BATCHLOG_MISSING'
    }
    Write-Host 'M10A0_3B_FAILURE_DIAGNOSTIC_END'
}

Require-File $Compiler 'COMSOL_COMPILER_MISSING'
Require-File $Batch 'COMSOL_BATCH_MISSING'
Require-File $CaptureLibrary 'COMSOL_CAPTURE_LIBRARY_MISSING'
Require-File $Java 'JAVA_SOURCE_MISSING'
Require-File $CcStep 'CURRENT_COLLECTOR_STEP_MISSING'
Require-File $ChamberStep 'CHAMBER_STEP_MISSING'
. $CaptureLibrary

New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

$CcSha = Assert-Sha256 $CcStep $ExpectedCcSha 'CURRENT_COLLECTOR_FLOW_FIELD'
$ChamberSha = Assert-Sha256 $ChamberStep $ExpectedChamberSha 'ELECTROLYTE_CHAMBER'
$CcBytes = (Get-Item -LiteralPath $CcStep).Length
$ChamberBytes = (Get-Item -LiteralPath $ChamberStep).Length

if (Test-Path -LiteralPath $Output -PathType Leaf) {
    $Backup = Join-Path $RunDir ('preexisting_' + (Split-Path $Output -Leaf))
    Move-Item -LiteralPath $Output -Destination $Backup
    Write-Host "Moved pre-existing 3b MPH to: $Backup"
}

$RuntimeSource = @"
public final class LiNRR_M10A0_3b_RuntimeInputs {
    private LiNRR_M10A0_3b_RuntimeInputs() {}
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
    Write-Host '========== M10A0.3b ROTATE TOP =========='
    Write-Host "Project root : $ProjectRoot"
    Write-Host "Current coll.: $CcStep"
    Write-Host "Chamber      : $ChamberStep"
    Write-Host "Output MPH   : $Output"
    Write-Host "Batch log    : $BatchLog"
    Write-Host "Stdout log   : $StdoutLog"
    Write-Host "Stderr log   : $StderrLog"
    Write-Host "Merged log   : $ConsoleLog"
    Write-Host "CURRENT_COLLECTOR_SHA256=$CcSha"
    Write-Host "CHAMBER_SHA256=$ChamberSha"

    Write-Host '[1/5] Source hashes passed.'
    Write-Host '[2/5] Compiling runtime input class...'
    & $Compiler $RuntimeJava
    $RuntimeCompileExit = $LASTEXITCODE
    if ($RuntimeCompileExit -ne 0) {
        throw "RUNTIME_INPUT_COMPILE_FAILED: exit=$RuntimeCompileExit"
    }
    Require-File $RuntimeClass 'RUNTIME_INPUT_CLASS_MISSING'

    Write-Host '[3/5] Compiling M10A0.3b Java class...'
    & $Compiler $Java
    $JavaCompileExit = $LASTEXITCODE
    if ($JavaCompileExit -ne 0) {
        throw "COMSOL_JAVA_COMPILE_FAILED: exit=$JavaCompileExit"
    }
    Require-File $ClassFile 'COMPILED_CLASS_NOT_FOUND'

    Write-Host '[4/5] Running strict imports plus one Rotate...'
    $Capture = Invoke-ComsolCaptured `
        -Executable $Batch `
        -ArgumentList @(
            '-classpathadd', $RunDir,
            '-inputfile', $ClassFile,
            '-outputfile', $Output,
            '-batchlog', $BatchLog
        ) `
        -StdoutPath $StdoutLog `
        -StderrPath $StderrLog `
        -MergedConsolePath $ConsoleLog
    $BatchExit = $Capture.ExitCode
    Write-Host "COMSOL_CAPTURE_PROCESS_ID=$($Capture.ProcessId)"
    Write-Host "COMSOL_CAPTURE_DURATION_SECONDS=$($Capture.DurationSeconds)"
    Write-Host 'COMSOL_CAPTURED_STDOUT_BEGIN'
    Get-Content -LiteralPath $StdoutLog | ForEach-Object { Write-Host $_ }
    Write-Host 'COMSOL_CAPTURED_STDOUT_END'
    Write-Host 'COMSOL_CAPTURED_STDERR_BEGIN'
    Get-Content -LiteralPath $StderrLog | ForEach-Object { Write-Host $_ }
    Write-Host 'COMSOL_CAPTURED_STDERR_END'

    if ($BatchExit -ne 0) {
        Show-Failure $BatchExit 'NONZERO_COMSOLBATCH_EXIT'
        throw "COMSOL_BATCH_FAILED: exit=$BatchExit"
    }
    foreach ($Required in @($StdoutLog, $StderrLog, $ConsoleLog, $BatchLog, $Output)) {
        if (-not (Test-Path -LiteralPath $Required -PathType Leaf) -or
                ($Required -ne $StderrLog -and
                (Get-Item -LiteralPath $Required).Length -eq 0)) {
            Show-Failure $BatchExit "MISSING_OR_EMPTY[$Required]"
            throw "REQUIRED_OUTPUT_MISSING_OR_EMPTY: $Required"
        }
    }

    $RequiredMarkers = @(
        'M10A0_3B_BOOT_START',
        'M10A0_3B_RUNTIME_INPUTS_PASS',
        'M10A0_3B_CAD_KERNEL_PASS',
        'M10A0_3B_RAW_IMPORTS_PASS',
        'M10A0_3B_ROTATE_FEATURE_CREATE_PASS',
        'M10A0_3B_ROTATE_BUILD_PASS',
        'M10A0_3B_MODEL_SAVE_PASS',
        'M10A0_3B_ROTATE_TOP=PASS',
        'M10A0_3B_SOURCE_CC_BBOX_MM=',
        'M10A0_3B_ROTATED_CC_BBOX_MM=',
        'M10A0_3B_ROTATED_ENTITIES=domains:1,boundaries:135,edges:372,vertices:245',
        'M10A0_3B_ROTATED_CAD_REPRESENTATION=true'
    )
    foreach ($Marker in $RequiredMarkers) {
        if (-not (Select-String -LiteralPath $StdoutLog -SimpleMatch $Marker -Quiet)) {
            Show-Failure $BatchExit "CONSOLE_MARKER_MISSING[$Marker]"
            throw "REQUIRED_CONSOLE_MARKER_MISSING: $Marker"
        }
    }

    $CcReadCount = @(
        Select-String -LiteralPath $BatchLog -SimpleMatch (
            "Begin CAD File Read '" + $CcStep.Replace('\', '/'))
    ).Count
    $ChamberReadCount = @(
        Select-String -LiteralPath $BatchLog -SimpleMatch (
            "Begin CAD File Read '" + $ChamberStep.Replace('\', '/'))
    ).Count
    if ($CcReadCount -ne 2 -or $ChamberReadCount -ne 1) {
        Show-Failure $BatchExit 'UNEXPECTED_STEP_READ_COUNTS'
        throw "UNEXPECTED_STEP_READ_COUNTS: cc=$CcReadCount chamber=$ChamberReadCount"
    }

    $FatalPatterns = @(
        '\berrors?\b',
        '\bfailed\b',
        '\bundefined\b',
        '\bsingular\b',
        '\bout of memory\b',
        '\bexception\b',
        '\blicense error\b',
        'M10A0_3B_FIRST_FAILED_API',
        'M10A0_3B_FAILURE_CONTEXT_BEGIN',
        'ROTATED_TOPOLOGY_CHANGED',
        'BBOX_OUTSIDE_TOLERANCE',
        'ROTATED_CAD_REPRESENTATION_FALSE'
    )
    $FatalHits = Select-String -Path @($BatchLog, $StdoutLog, $StderrLog, $ConsoleLog) `
        -Pattern $FatalPatterns -CaseSensitive:$false `
        -ErrorAction SilentlyContinue
    if ($FatalHits) {
        Write-Host 'FATAL_LOG_HITS_BEGIN'
        $FatalHits | ForEach-Object {
            Write-Host "$($_.Path):$($_.LineNumber):$($_.Line)"
        }
        Write-Host 'FATAL_LOG_HITS_END'
        Show-Failure $BatchExit 'FATAL_TEXT_IN_COMSOL_EVIDENCE'
        throw 'FATAL_TEXT_FOUND_IN_COMSOL_EVIDENCE'
    }

    $WarningHits = Select-String -Path @($BatchLog, $StdoutLog, $StderrLog) `
        -Pattern @('warning', 'warn:', '警告') -CaseSensitive:$false `
        -ErrorAction SilentlyContinue

    # Recheck source integrity after COMSOL has closed the files.
    $CcShaAfter = Assert-Sha256 $CcStep $ExpectedCcSha 'CURRENT_COLLECTOR_FLOW_FIELD_POSTRUN'
    $ChamberShaAfter = Assert-Sha256 $ChamberStep $ExpectedChamberSha 'ELECTROLYTE_CHAMBER_POSTRUN'

    Write-Host '[5/5] Evidence and geometry acceptance passed.'
    $MphItem = Get-Item -LiteralPath $Output
    $MphSha = (Get-FileHash -LiteralPath $Output -Algorithm SHA256).Hash.ToUpperInvariant()
    $StdoutSha = (Get-FileHash -LiteralPath $StdoutLog -Algorithm SHA256).Hash.ToUpperInvariant()
    $StderrSha = (Get-FileHash -LiteralPath $StderrLog -Algorithm SHA256).Hash.ToUpperInvariant()
    $ConsoleSha = (Get-FileHash -LiteralPath $ConsoleLog -Algorithm SHA256).Hash.ToUpperInvariant()
    $BatchLogSha = (Get-FileHash -LiteralPath $BatchLog -Algorithm SHA256).Hash.ToUpperInvariant()
    $SourceBox = (Select-String -LiteralPath $StdoutLog `
        -Pattern '^M10A0_3B_SOURCE_CC_BBOX_MM=').Line
    $RotatedBox = (Select-String -LiteralPath $StdoutLog `
        -Pattern '^M10A0_3B_ROTATED_CC_BBOX_MM=').Line
    $Entities = (Select-String -LiteralPath $StdoutLog `
        -Pattern '^M10A0_3B_ROTATED_ENTITIES=').Line

    Write-Host ''
    Write-Host '========== BUILD RESULT =========='
    Write-Host 'M10A0_3B_ROTATE_TOP=PASS'
    Write-Host "RUNTIME_COMSOLCOMPILE_EXIT=$RuntimeCompileExit"
    Write-Host "JAVA_COMSOLCOMPILE_EXIT=$JavaCompileExit"
    Write-Host "COMSOLBATCH_EXIT=$BatchExit"
    Write-Host 'CONSOLE_OUTPUT_CHECK=PASS'
    Write-Host 'BATCH_LOG_CHECK=PASS'
    Write-Host 'ASCII_MARKERS_CHECK=PASS'
    Write-Host 'STEP_READ_COUNTS_CHECK=PASS'
    Write-Host "CC_STEP_READ_COUNT=$CcReadCount"
    Write-Host "CHAMBER_STEP_READ_COUNT=$ChamberReadCount"
    Write-Host 'MPH_EXISTS_CHECK=PASS'
    Write-Host 'FATAL_HITS=0'
    Write-Host "WARNING_HITS=$(@($WarningHits).Count)"
    if ($WarningHits) {
        Write-Host 'WARNING_LOG_HITS_BEGIN'
        $WarningHits | ForEach-Object {
            Write-Host "$($_.Path):$($_.LineNumber):$($_.Line)"
        }
        Write-Host 'WARNING_LOG_HITS_END'
    }
    Write-Host $SourceBox
    Write-Host $RotatedBox
    Write-Host $Entities
    Write-Host "CURRENT_COLLECTOR_SHA256_POSTRUN=$CcShaAfter"
    Write-Host "CHAMBER_SHA256_POSTRUN=$ChamberShaAfter"
    Write-Host "MPH_PATH=$($MphItem.FullName)"
    Write-Host "MPH_BYTES=$($MphItem.Length)"
    Write-Host "MPH_SHA256=$MphSha"
    Write-Host "STDOUT_LOG_PATH=$StdoutLog"
    Write-Host "STDOUT_LOG_SHA256=$StdoutSha"
    Write-Host "STDERR_LOG_PATH=$StderrLog"
    Write-Host "STDERR_LOG_SHA256=$StderrSha"
    Write-Host "CONSOLE_LOG_PATH=$ConsoleLog"
    Write-Host "CONSOLE_LOG_SHA256=$ConsoleSha"
    Write-Host "BATCH_LOG_PATH=$BatchLog"
    Write-Host "BATCH_LOG_SHA256=$BatchLogSha"
    Write-Host "RUNTIME_INPUT_SOURCE=$RuntimeJava"
    Write-Host "RUNTIME_INPUT_CLASS=$RuntimeClass"
    Write-Host ''
    Write-Host 'No Move, Mirror, Assembly, explicit selection feature, pair, material, mesh, physics, study, or solver was created.'
}
finally {
    Pop-Location
}
