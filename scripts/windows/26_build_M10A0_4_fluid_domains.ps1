[CmdletBinding()]param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')

$ComsolBin = 'F:\COMSOL64\Multiphysics\bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'
$Java = Join-Path $ProjectRoot 'src\java\LiNRR_M10A0_4_FluidDomains.java'
$ClassFile = Join-Path $ProjectRoot 'src\java\LiNRR_M10A0_4_FluidDomains.class'
$InputMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A0_3_real_cad_registered.mph'
$OutputMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A0_4_real_cad_fluid_domains.mph'
$CcStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\block_currentCollector_flowField_v03.2.1_20210525_STEP-AP203.STEP'
$ChamberStep = Join-Path $ProjectRoot 'cad\raw\M10A0_2\electrolyteChamber_rectangularMask_v02.2.2_20210521_STEP-AP203.STEP'
$ExpectedCcSha = '0091777D10ECA620E08780381438F3522B3BE31FEE307F9DDCF074A1FACDDFE9'
$ExpectedChamberSha = 'AE350E006228A10EEC4476F79CAFCE6D63F18F10F58F0A8B3559EED0C37FF12C'

foreach ($Required in @($Compiler,$Batch,$Java,$InputMph,$CcStep,$ChamberStep)) {
    if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) { throw "M10A0_4_REQUIRED_INPUT_MISSING: $Required" }
}
$CcSha = (Get-FileHash -LiteralPath $CcStep -Algorithm SHA256).Hash.ToUpperInvariant()
$ChamberSha = (Get-FileHash -LiteralPath $ChamberStep -Algorithm SHA256).Hash.ToUpperInvariant()
if ($CcSha -cne $ExpectedCcSha -or $ChamberSha -cne $ExpectedChamberSha) {
    throw "M10A0_4_SOURCE_STEP_HASH_MISMATCH: cc=$CcSha chamber=$ChamberSha"
}

$RunStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot "runs\M10A0_4\$RunStamp`_fluid_domains"
[System.IO.Directory]::CreateDirectory($RunDir) | Out-Null
[System.IO.Directory]::CreateDirectory((Split-Path $OutputMph -Parent)) | Out-Null
$RuntimeJava = Join-Path $RunDir 'LiNRR_M10A0_4_RuntimeInputs.java'
$RuntimeClass = Join-Path $RunDir 'LiNRR_M10A0_4_RuntimeInputs.class'
$CompileStdout = Join-Path $RunDir 'M10A0_4_compile_stdout.txt'
$CompileStderr = Join-Path $RunDir 'M10A0_4_compile_stderr.txt'
$CompileMerged = Join-Path $RunDir 'M10A0_4_compile_console_merged.txt'
$Stdout = Join-Path $RunDir 'M10A0_4_stdout.txt'
$Stderr = Join-Path $RunDir 'M10A0_4_stderr.txt'
$Merged = Join-Path $RunDir 'M10A0_4_console_merged.txt'
$BatchLog = Join-Path $RunDir 'M10A0_4_fluid_domains.log'
$AuditCsv = Join-Path $RunDir 'M10A0_4_fluid_domain_audit.csv'

if (Test-Path -LiteralPath $OutputMph -PathType Leaf) {
    Move-Item -LiteralPath $OutputMph -Destination (Join-Path $RunDir ('preexisting_' + (Split-Path $OutputMph -Leaf)))
}
function ConvertTo-JavaLiteral([string]$Value) { $Value.Replace('\','\\').Replace('"','\"') }
$RuntimeSource = @"
public final class LiNRR_M10A0_4_RuntimeInputs {
  private LiNRR_M10A0_4_RuntimeInputs() {}
  public static final String INPUT_MPH = "$(ConvertTo-JavaLiteral $InputMph)";
  public static final String OUTPUT_MPH = "$(ConvertTo-JavaLiteral $OutputMph)";
  public static final String CC_STEP = "$(ConvertTo-JavaLiteral $CcStep)";
  public static final String CHAMBER_STEP = "$(ConvertTo-JavaLiteral $ChamberStep)";
  public static final String CC_SHA256 = "$CcSha";
  public static final String CHAMBER_SHA256 = "$ChamberSha";
}
"@
[System.IO.File]::WriteAllText($RuntimeJava,$RuntimeSource,(New-Object System.Text.UTF8Encoding($false)))

$RuntimeCompile = Invoke-ComsolCaptured -Executable $Compiler -ArgumentList @($RuntimeJava) `
    -StdoutPath (Join-Path $RunDir 'M10A0_4_runtime_compile_stdout.txt') `
    -StderrPath (Join-Path $RunDir 'M10A0_4_runtime_compile_stderr.txt') `
    -MergedConsolePath (Join-Path $RunDir 'M10A0_4_runtime_compile_console_merged.txt')
if ($RuntimeCompile.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $RuntimeClass)) { throw "M10A0_4_RUNTIME_COMPILE_FAILED: exit=$($RuntimeCompile.ExitCode)" }
$Compile = Invoke-ComsolCaptured -Executable $Compiler -ArgumentList @($Java) -StdoutPath $CompileStdout -StderrPath $CompileStderr -MergedConsolePath $CompileMerged
if ($Compile.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $ClassFile)) { throw "M10A0_4_JAVA_COMPILE_FAILED: exit=$($Compile.ExitCode)" }

$Capture = Invoke-ComsolCaptured -Executable $Batch -ArgumentList @(
    '-classpathadd',$RunDir,'-inputfile',$ClassFile,'-outputfile',$OutputMph,'-batchlog',$BatchLog
) -StdoutPath $Stdout -StderrPath $Stderr -MergedConsolePath $Merged

Write-Host 'M10A0_4_CAPTURED_STDOUT_BEGIN'
Get-Content -LiteralPath $Stdout | ForEach-Object { Write-Host $_ }
Write-Host 'M10A0_4_CAPTURED_STDOUT_END'
Write-Host 'M10A0_4_CAPTURED_STDERR_BEGIN'
Get-Content -LiteralPath $Stderr | ForEach-Object { Write-Host $_ }
Write-Host 'M10A0_4_CAPTURED_STDERR_END'

if ($Capture.ExitCode -ne 0) { throw "M10A0_4_COMSOLBATCH_NONZERO: exit=$($Capture.ExitCode)" }
foreach ($Evidence in @($Stdout,$Stderr,$Merged,$BatchLog,$OutputMph)) {
    if (-not (Test-Path -LiteralPath $Evidence -PathType Leaf)) { throw "M10A0_4_EVIDENCE_MISSING: $Evidence" }
}
foreach ($Evidence in @($Stdout,$Merged,$BatchLog,$OutputMph)) {
    if ((Get-Item -LiteralPath $Evidence).Length -eq 0) { throw "M10A0_4_EVIDENCE_EMPTY: $Evidence" }
}
$Markers = @('M10A0_4_RUNTIME_INPUTS_PASS','M10A0_4_ELECTROLYTE_GEOMETRY_PASS','M10A0_4_N2_GEOMETRY_PASS',
    'M10A0_4_SELECTIONS_PASS','M10A0_4_CONNECTIVITY_PASS','M10A0_4_MODEL_SAVE_PASS','M10A0_4_REAL_CAD_FLUID_DOMAINS=PASS')
foreach ($Marker in $Markers) { if (-not (Select-String -LiteralPath $Stdout -SimpleMatch $Marker -Quiet)) { throw "M10A0_4_REQUIRED_MARKER_MISSING: $Marker" } }
foreach ($StepName in @((Split-Path $CcStep -Leaf),(Split-Path $ChamberStep -Leaf))) {
    if (-not (Select-String -LiteralPath $BatchLog -SimpleMatch $StepName -Quiet)) { throw "M10A0_4_STEP_READ_EVIDENCE_MISSING: $StepName" }
}
$Fatal = Select-String -Path @($BatchLog,$Stdout,$Stderr) -Pattern @('\berrors?\b','\bfailed\b','\bundefined\b','\bsingular\b','\bout of memory\b','\bexception\b','FIRST_FAILED_API','FAILURE_CONTEXT_BEGIN') -CaseSensitive:$false -ErrorAction SilentlyContinue
if ($Fatal) { $Fatal | ForEach-Object { Write-Host "FATAL|$($_.Path)|$($_.LineNumber)|$($_.Line)" }; throw 'M10A0_4_FATAL_EVIDENCE_FOUND' }
$Warnings = Select-String -Path @($BatchLog,$Stdout,$Stderr) -Pattern @('\bwarning\b','\bwarn:') -CaseSensitive:$false -ErrorAction SilentlyContinue

$AuditPrefix = 'M10A0_4_AUDIT_CSV|'
$AuditLines = Get-Content -LiteralPath $Stdout | Where-Object { $_ -like "$AuditPrefix*" } | ForEach-Object { $_.Substring($AuditPrefix.Length) } | Select-Object -Unique
[System.IO.File]::WriteAllLines($AuditCsv,[string[]]$AuditLines,(New-Object System.Text.UTF8Encoding($false)))
if (@($AuditLines).Count -ne 3 -or (Get-Item -LiteralPath $AuditCsv).Length -eq 0) { throw "M10A0_4_AUDIT_CSV_INVALID: records=$(@($AuditLines).Count)" }

$CcAfter = (Get-FileHash -LiteralPath $CcStep -Algorithm SHA256).Hash.ToUpperInvariant()
$ChamberAfter = (Get-FileHash -LiteralPath $ChamberStep -Algorithm SHA256).Hash.ToUpperInvariant()
if ($CcAfter -cne $ExpectedCcSha -or $ChamberAfter -cne $ExpectedChamberSha) { throw "M10A0_4_SOURCE_STEP_HASH_CHANGED: cc=$CcAfter chamber=$ChamberAfter" }
$Mph = Get-Item -LiteralPath $OutputMph
Write-Host 'M10A0_4_REAL_CAD_FLUID_DOMAINS=PASS'
Write-Host "RUNTIME_COMPILE_EXIT=$($RuntimeCompile.ExitCode)"
Write-Host "JAVA_COMPILE_EXIT=$($Compile.ExitCode)"
Write-Host "COMSOLBATCH_EXIT=$($Capture.ExitCode)"
Write-Host "WARNING_HITS=$(@($Warnings).Count)"
Write-Host "MPH_PATH=$($Mph.FullName)"
Write-Host "MPH_BYTES=$($Mph.Length)"
Write-Host "MPH_SHA256=$((Get-FileHash -LiteralPath $OutputMph -Algorithm SHA256).Hash.ToUpperInvariant())"
Write-Host "AUDIT_CSV=$AuditCsv"
Write-Host "STDOUT_PATH=$Stdout"
Write-Host "STDERR_PATH=$Stderr"
Write-Host "MERGED_CONSOLE_PATH=$Merged"
Write-Host "BATCH_LOG_PATH=$BatchLog"
Write-Host "RUN_DIR=$RunDir"
