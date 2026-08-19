Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot 'Invoke-ComsolCaptured.ps1')

function ConvertTo-JavaLiteralText {
    param([string]$Value)
    return $Value.Replace('\', '\\').Replace('"', '\"').Replace("`r", ' ').Replace("`n", ' ')
}

function Invoke-M10A0CadStage {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string]$StageToken,
        [Parameter(Mandatory = $true)][string]$RunSuffix,
        [Parameter(Mandatory = $true)][string]$JavaRelative,
        [Parameter(Mandatory = $true)][string]$JavaClassName,
        [Parameter(Mandatory = $true)][string]$RuntimeClassName,
        [Parameter(Mandatory = $true)][string]$InputMphRelative,
        [Parameter(Mandatory = $true)][string]$OutputMphRelative,
        [Parameter(Mandatory = $true)][string]$PassMarker,
        [string[]]$RequiredMarkers = @()
    )

    $ComsolBin = 'F:\COMSOL64\Multiphysics\bin\win64'
    $Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
    $Batch = Join-Path $ComsolBin 'comsolbatch.exe'
    $CcStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
    $ChamberStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
    $Java = Join-Path $ProjectRoot $JavaRelative
    $InputMph = Join-Path $ProjectRoot $InputMphRelative
    $OutputMph = Join-Path $ProjectRoot $OutputMphRelative
    $ClassFile = Join-Path (Split-Path $Java -Parent) ($JavaClassName + '.class')

    $ExpectedCcSha = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
    $ExpectedChamberSha = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'

    foreach ($Required in @($Compiler, $Batch, $CcStep, $ChamberStep, $Java, $InputMph)) {
        if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) {
            throw "REQUIRED_STAGE_INPUT_MISSING: $Required"
        }
    }
    $CcSha = (Get-FileHash -LiteralPath $CcStep -Algorithm SHA256).Hash.ToUpperInvariant()
    $ChamberSha = (Get-FileHash -LiteralPath $ChamberStep -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($CcSha -cne $ExpectedCcSha -or $ChamberSha -cne $ExpectedChamberSha) {
        throw "SOURCE_STEP_HASH_MISMATCH: cc=$CcSha chamber=$ChamberSha"
    }

    $RunStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $RunDir = Join-Path $ProjectRoot "runs\M10A0_3\$RunStamp`_$RunSuffix"
    [System.IO.Directory]::CreateDirectory($RunDir) | Out-Null
    [System.IO.Directory]::CreateDirectory((Split-Path $OutputMph -Parent)) | Out-Null
    $BatchLog = Join-Path $RunDir ($StageToken + '_batch.log')
    $StdoutLog = Join-Path $RunDir ($StageToken + '_stdout.txt')
    $StderrLog = Join-Path $RunDir ($StageToken + '_stderr.txt')
    $MergedLog = Join-Path $RunDir ($StageToken + '_console_merged.txt')
    $RuntimeJava = Join-Path $RunDir ($RuntimeClassName + '.java')
    $RuntimeClass = Join-Path $RunDir ($RuntimeClassName + '.class')

    if (Test-Path -LiteralPath $OutputMph -PathType Leaf) {
        Move-Item -LiteralPath $OutputMph -Destination (
            Join-Path $RunDir ('preexisting_' + (Split-Path $OutputMph -Leaf)))
    }

    $RuntimeSource = @"
public final class $RuntimeClassName {
    private $RuntimeClassName() {}
    public static final String INPUT_MPH = "$(ConvertTo-JavaLiteralText $InputMph)";
    public static final String OUTPUT_MPH = "$(ConvertTo-JavaLiteralText $OutputMph)";
    public static final String CC_STEP = "$(ConvertTo-JavaLiteralText $CcStep)";
    public static final String CHAMBER_STEP = "$(ConvertTo-JavaLiteralText $ChamberStep)";
    public static final String CC_SHA256 = "$CcSha";
    public static final String CHAMBER_SHA256 = "$ChamberSha";
}
"@
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($RuntimeJava, $RuntimeSource, $Utf8NoBom)

    Push-Location $ProjectRoot
    try {
        Write-Host "========== $StageToken =========="
        Write-Host "INPUT_MPH=$InputMph"
        Write-Host "OUTPUT_MPH=$OutputMph"
        & $Compiler $RuntimeJava
        $RuntimeCompileExit = $LASTEXITCODE
        if ($RuntimeCompileExit -ne 0 -or -not (Test-Path -LiteralPath $RuntimeClass)) {
            throw "RUNTIME_COMPILE_FAILED: exit=$RuntimeCompileExit"
        }
        & $Compiler $Java
        $JavaCompileExit = $LASTEXITCODE
        if ($JavaCompileExit -ne 0 -or -not (Test-Path -LiteralPath $ClassFile)) {
            throw "JAVA_COMPILE_FAILED: exit=$JavaCompileExit"
        }

        $Capture = Invoke-ComsolCaptured -Executable $Batch -ArgumentList @(
            '-classpathadd', $RunDir,
            '-inputfile', $ClassFile,
            '-outputfile', $OutputMph,
            '-batchlog', $BatchLog
        ) -StdoutPath $StdoutLog -StderrPath $StderrLog `
          -MergedConsolePath $MergedLog
        $BatchExit = $Capture.ExitCode

        Write-Host 'CAPTURED_STDOUT_BEGIN'
        Get-Content -LiteralPath $StdoutLog | ForEach-Object { Write-Host $_ }
        Write-Host 'CAPTURED_STDOUT_END'
        Write-Host 'CAPTURED_STDERR_BEGIN'
        Get-Content -LiteralPath $StderrLog | ForEach-Object { Write-Host $_ }
        Write-Host 'CAPTURED_STDERR_END'

        if ($BatchExit -ne 0) {
            throw "COMSOLBATCH_NONZERO: exit=$BatchExit"
        }
        foreach ($Evidence in @($StdoutLog, $MergedLog, $BatchLog, $OutputMph)) {
            if (-not (Test-Path -LiteralPath $Evidence -PathType Leaf) -or
                    (Get-Item -LiteralPath $Evidence).Length -eq 0) {
                throw "EVIDENCE_MISSING_OR_EMPTY: $Evidence"
            }
        }
        foreach ($Marker in @($PassMarker) + $RequiredMarkers) {
            if (-not (Select-String -LiteralPath $StdoutLog -SimpleMatch $Marker -Quiet)) {
                throw "REQUIRED_MARKER_MISSING: $Marker"
            }
        }

        $Fatal = Select-String -Path @($BatchLog, $StdoutLog, $StderrLog) `
            -Pattern @('\berrors?\b','\bfailed\b','\bundefined\b','\bsingular\b',
                '\bout of memory\b','\bexception\b','\blicense error\b',
                'FIRST_FAILED_API','FAILURE_CONTEXT_BEGIN') `
            -CaseSensitive:$false -ErrorAction SilentlyContinue
        if ($Fatal) {
            $Fatal | ForEach-Object { Write-Host "FATAL|$($_.Path)|$($_.LineNumber)|$($_.Line)" }
            throw 'FATAL_EVIDENCE_FOUND'
        }
        $Warnings = Select-String -Path @($BatchLog, $StdoutLog, $StderrLog) `
            -Pattern @('warning','warn:','警告') -CaseSensitive:$false `
            -ErrorAction SilentlyContinue

        $CcAfter = (Get-FileHash -LiteralPath $CcStep -Algorithm SHA256).Hash.ToUpperInvariant()
        $ChamberAfter = (Get-FileHash -LiteralPath $ChamberStep -Algorithm SHA256).Hash.ToUpperInvariant()
        if ($CcAfter -cne $ExpectedCcSha -or $ChamberAfter -cne $ExpectedChamberSha) {
            throw "SOURCE_STEP_HASH_CHANGED: cc=$CcAfter chamber=$ChamberAfter"
        }
        $MphItem = Get-Item -LiteralPath $OutputMph
        Write-Host "$PassMarker"
        Write-Host "RUNTIME_COMPILE_EXIT=$RuntimeCompileExit"
        Write-Host "JAVA_COMPILE_EXIT=$JavaCompileExit"
        Write-Host "COMSOLBATCH_EXIT=$BatchExit"
        Write-Host "WARNING_HITS=$(@($Warnings).Count)"
        Write-Host "MPH_PATH=$($MphItem.FullName)"
        Write-Host "MPH_BYTES=$($MphItem.Length)"
        Write-Host "MPH_SHA256=$((Get-FileHash -LiteralPath $OutputMph -Algorithm SHA256).Hash.ToUpperInvariant())"
        Write-Host "STDOUT_PATH=$StdoutLog"
        Write-Host "STDERR_PATH=$StderrLog"
        Write-Host "MERGED_CONSOLE_PATH=$MergedLog"
        Write-Host "BATCH_LOG_PATH=$BatchLog"
        Write-Host "RUN_DIR=$RunDir"
        return [pscustomobject]@{
            Stage = $StageToken
            RunDir = $RunDir
            OutputMph = $OutputMph
            MphBytes = $MphItem.Length
            MphSha256 = (Get-FileHash -LiteralPath $OutputMph -Algorithm SHA256).Hash.ToUpperInvariant()
            WarningCount = @($Warnings).Count
        }
    }
    finally {
        Pop-Location
    }
}
