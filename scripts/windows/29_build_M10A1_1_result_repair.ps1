[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'lib\Invoke-ComsolCaptured.ps1')

$ComsolBin = 'F:\COMSOL64\Multiphysics\bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'
$ProbeJava = Join-Path $ProjectRoot 'src\java\LiNRR_M10A1_1_VariableProbe.java'
$RepairJava = Join-Path $ProjectRoot 'src\java\LiNRR_M10A1_1_ResultRepair.java'
$InputMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A1_real_cad_flow_solved.mph'
$OutputMph = Join-Path $ProjectRoot 'models\generated\LiNRR_M10A1_1_real_cad_flow_repaired.mph'
$ExpectedInputSha = '535F90E93B42B6D69A31A635C429B01B0B36D3EA8FE39B5645352FCBDF3D43C5'

foreach ($PathValue in @($Compiler,$Batch,$ProbeJava,$RepairJava,$InputMph)) {
    if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf)) { throw "M10A1_1_REQUIRED_INPUT_MISSING: $PathValue" }
}
$InputBefore = Get-Item -LiteralPath $InputMph
$InputShaBefore = (Get-FileHash -LiteralPath $InputMph -Algorithm SHA256).Hash.ToUpperInvariant()
if ($InputShaBefore -cne $ExpectedInputSha) { throw "M10A1_1_BASELINE_HASH_MISMATCH: $InputShaBefore" }

$Stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot "runs\M10A1_1\$($Stamp)_result_repair"
[System.IO.Directory]::CreateDirectory($RunDir) | Out-Null
[System.IO.Directory]::CreateDirectory((Split-Path $OutputMph -Parent)) | Out-Null
$RuntimeJava = Join-Path $RunDir 'LiNRR_M10A1_1_RuntimeInputs.java'
$RuntimeClass = Join-Path $RunDir 'LiNRR_M10A1_1_RuntimeInputs.class'
function ConvertTo-JavaLiteral([string]$Value) { $Value.Replace('\','\\').Replace('"','\"') }
$Runtime = @"
public final class LiNRR_M10A1_1_RuntimeInputs {
 public static final String INPUT_MPH="$(ConvertTo-JavaLiteral $InputMph)";
 public static final String OUTPUT_MPH="$(ConvertTo-JavaLiteral $OutputMph)";
 private LiNRR_M10A1_1_RuntimeInputs() {}
}
"@
[System.IO.File]::WriteAllText($RuntimeJava,$Runtime,(New-Object System.Text.UTF8Encoding($false)))

function Invoke-Compile([string]$JavaPath,[string]$Stem) {
    $Result = Invoke-ComsolCaptured -Executable $Compiler -ArgumentList @($JavaPath) `
        -StdoutPath (Join-Path $RunDir "$($Stem)_compile_stdout.txt") `
        -StderrPath (Join-Path $RunDir "$($Stem)_compile_stderr.txt") `
        -MergedConsolePath (Join-Path $RunDir "$($Stem)_compile_merged.txt")
    if ($Result.ExitCode -ne 0) { throw "M10A1_1_COMPILE_FAILED: stem=$Stem exit=$($Result.ExitCode)" }
}
Invoke-Compile $RuntimeJava 'runtime'
Invoke-Compile $ProbeJava 'probe'
Invoke-Compile $RepairJava 'repair'
if (-not (Test-Path -LiteralPath $RuntimeClass -PathType Leaf)) { throw 'M10A1_1_RUNTIME_CLASS_MISSING' }

$ProbeClass = [System.IO.Path]::ChangeExtension($ProbeJava,'.class')
$RepairClass = [System.IO.Path]::ChangeExtension($RepairJava,'.class')
$ProbeLog = Join-Path $RunDir 'M10A1_1_probe.log'
$ProbeOut = Join-Path $RunDir 'M10A1_1_probe_stdout.txt'
$ProbeErr = Join-Path $RunDir 'M10A1_1_probe_stderr.txt'
$ProbeCapture = Invoke-ComsolCaptured -Executable $Batch -ArgumentList @('-classpathadd',$RunDir,'-inputfile',$ProbeClass,'-batchlog',$ProbeLog) `
    -StdoutPath $ProbeOut -StderrPath $ProbeErr -MergedConsolePath (Join-Path $RunDir 'M10A1_1_probe_merged.txt')
if ($ProbeCapture.ExitCode -ne 0 -or -not (Select-String -LiteralPath $ProbeOut -SimpleMatch 'M10A1_1_READ_ONLY_VARIABLE_PROBE=PASS' -Quiet)) {
    throw "M10A1_1_VARIABLE_PROBE_FAILED: exit=$($ProbeCapture.ExitCode)"
}

if (Test-Path -LiteralPath $OutputMph -PathType Leaf) {
    Move-Item -LiteralPath $OutputMph -Destination (Join-Path $RunDir ('preexisting_' + (Split-Path $OutputMph -Leaf)))
}
$RepairLog = Join-Path $RunDir 'M10A1_1_repair.log'
$RepairOut = Join-Path $RunDir 'M10A1_1_repair_stdout.txt'
$RepairErr = Join-Path $RunDir 'M10A1_1_repair_stderr.txt'
$RepairCapture = Invoke-ComsolCaptured -Executable $Batch -ArgumentList @('-classpathadd',$RunDir,'-inputfile',$RepairClass,'-outputfile',$OutputMph,'-batchlog',$RepairLog) `
    -StdoutPath $RepairOut -StderrPath $RepairErr -MergedConsolePath (Join-Path $RunDir 'M10A1_1_repair_merged.txt')
if ($RepairCapture.ExitCode -ne 0) { throw "M10A1_1_REPAIR_BATCH_FAILED: exit=$($RepairCapture.ExitCode)" }
if (-not (Test-Path -LiteralPath $OutputMph -PathType Leaf) -or (Get-Item -LiteralPath $OutputMph).Length -le 0) { throw 'M10A1_1_OUTPUT_MPH_MISSING' }
if (-not (Select-String -LiteralPath $RepairOut -SimpleMatch 'M10A1_1_RESULT_REPAIR=PASS' -Quiet)) { throw 'M10A1_1_PASS_MARKER_MISSING' }

$Fatal = Select-String -Path @($ProbeLog,$ProbeOut,$ProbeErr,$RepairLog,$RepairOut,$RepairErr) `
    -Pattern @('\berrors?\b','\bfailed\b','\bundefined\b','\bsingular\b','\bout of memory\b','\bexception\b') `
    -CaseSensitive:$false -ErrorAction SilentlyContinue
if ($Fatal) {
    $Fatal | ForEach-Object { Write-Host "FATAL|$($_.Path)|$($_.LineNumber)|$($_.Line)" }
    throw 'M10A1_1_FATAL_LOG_MATCH'
}
$Warnings = Select-String -Path @($ProbeLog,$ProbeOut,$ProbeErr,$RepairLog,$RepairOut,$RepairErr) `
    -Pattern @('\bwarning\b','\bwarn:') -CaseSensitive:$false -ErrorAction SilentlyContinue

$InputAfter = Get-Item -LiteralPath $InputMph
$InputShaAfter = (Get-FileHash -LiteralPath $InputMph -Algorithm SHA256).Hash.ToUpperInvariant()
if ($InputAfter.Length -ne $InputBefore.Length -or $InputShaAfter -cne $InputShaBefore) { throw 'M10A1_1_BASELINE_CHANGED' }
$OutputItem = Get-Item -LiteralPath $OutputMph
Write-Host 'M10A1_1_RESULT_REPAIR=PASS'
Write-Host "M10A1_BASELINE_BYTES_BEFORE=$($InputBefore.Length)"
Write-Host "M10A1_BASELINE_SHA256_BEFORE=$InputShaBefore"
Write-Host "M10A1_BASELINE_BYTES_AFTER=$($InputAfter.Length)"
Write-Host "M10A1_BASELINE_SHA256_AFTER=$InputShaAfter"
Write-Host "OUTPUT_MPH=$($OutputItem.FullName)"
Write-Host "OUTPUT_MPH_BYTES=$($OutputItem.Length)"
Write-Host "OUTPUT_MPH_SHA256=$((Get-FileHash -LiteralPath $OutputMph -Algorithm SHA256).Hash.ToUpperInvariant())"
Write-Host "WARNING_HITS=$(@($Warnings).Count)"
Write-Host "RUN_DIR=$RunDir"
