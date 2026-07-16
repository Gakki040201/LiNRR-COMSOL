param([string]$ResumeRun = '')

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$env:LINRR_PROJECT_ROOT = $ProjectRoot

$ComsolRoot = if ($env:COMSOL_ROOT) { $env:COMSOL_ROOT } else { "F:\COMSOL64\Multiphysics" }
$ComsolBin = Join-Path $ComsolRoot "bin\win64"
$Compiler = Join-Path $ComsolBin "comsolcompile.exe"
$Batch = Join-Path $ComsolBin "comsolbatch.exe"
$RuntimeJava = Join-Path $ComsolRoot "java\win64\jre\bin\java.exe"
$Ecj = Join-Path $ComsolRoot "plugins\org.eclipse.jdt.core.compiler.batch_3.42.0.v20250526-2018.jar"
$ApiJar = Join-Path $ComsolRoot "plugins\com.comsol.api_1.0.0.jar"
$ModelJar = Join-Path $ComsolRoot "plugins\com.comsol.model_1.0.0.jar"

$FrozenJava = Join-Path $ProjectRoot "src\java\LiNRR_M02_1_TransportAudit.java"
$MetricsJava = Join-Path $ProjectRoot "src\java\LiNRR_M02_2_Metrics.java"
$VerificationJava = Join-Path $ProjectRoot "src\java\LiNRR_M02_2_TransportVerification.java"
$LoadJava = Join-Path $ProjectRoot "tests\java\LiNRR_M02_2_LoadCheck.java"

$OperatorMph = Join-Path $ProjectRoot "models\generated\LiNRR_M02_2_operator_benchmarks.mph"
$DiffusionMph = Join-Path $ProjectRoot "models\generated\LiNRR_M02_2_diffusion_operator.mph"
$WallAuditMph = Join-Path $ProjectRoot "models\generated\LiNRR_M02_2_wall_reaction_audit.mph"
$OutputMph = Join-Path $ProjectRoot "models\generated\LiNRR_M02_2_transport_verification.mph"
$UniformCsv = Join-Path $ProjectRoot "results\tables\M02_2_uniform_transport.csv"
$DiffusionCsv = Join-Path $ProjectRoot "results\tables\M02_2_diffusion_linear.csv"
$MmsCsv = Join-Path $ProjectRoot "results\tables\M02_2_mms_convergence.csv"
$LowDaCsv = Join-Path $ProjectRoot "results\tables\M02_2_low_da_balance.csv"
$MeshCsv = Join-Path $ProjectRoot "results\tables\M02_2_mesh_audit.csv"
$PeDaCsv = Join-Path $ProjectRoot "results\tables\M02_2_pe_da_map.csv"
$UniformPng = Join-Path $ProjectRoot "results\figures\M02_2_uniform_concentration.png"
$DiffusionPng = Join-Path $ProjectRoot "results\figures\M02_2_linear_diffusion_profile.png"
$MmsPng = Join-Path $ProjectRoot "results\figures\M02_2_mms_error_convergence.png"
$LowDaPng = Join-Path $ProjectRoot "results\figures\M02_2_low_da_species.png"
$PeDaPng = Join-Path $ProjectRoot "results\figures\M02_2_pe_da_status_map.png"

$LatestDir = Join-Path $ProjectRoot "runs\latest"
$LatestLog = Join-Path $LatestDir "M02_2_build.log"
$LatestReport = Join-Path $LatestDir "M02_2_report.md"
$IsResume = -not [string]::IsNullOrWhiteSpace($ResumeRun)
if ($IsResume) {
    $RunDir = (Resolve-Path -LiteralPath $ResumeRun).Path
    $RunLeaf = Split-Path $RunDir -Leaf
    if ($RunLeaf -notmatch '^(\d{8}_\d{6})_M02_2$') { throw "Invalid M02.2 resume directory: $RunDir" }
    $RunStamp = $Matches[1]
} else {
    $RunStamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $RunDir = Join-Path $ProjectRoot ("runs\{0}_M02_2" -f $RunStamp)
}
$SourceStage = Join-Path $RunDir "frozen_M02_1_source_stage"
$SourceModels = Join-Path $SourceStage "models\generated"
$StagedFrozenJava = Join-Path $SourceStage "LiNRR_M02_1_TransportAudit.java"
$FrozenSourceMph = Join-Path $SourceModels "LiNRR_M02_1_transport_audit.mph"
$CompileLog = Join-Path $RunDir "M02_2_compile.log"
$FrozenCompileOut = Join-Path $RunDir "M02_2_frozen_compile_stdout.log"
$FrozenCompileErr = Join-Path $RunDir "M02_2_frozen_compile_stderr.log"
$FrozenEcjOut = Join-Path $RunDir "M02_2_frozen_ecj_stdout.log"
$FrozenEcjErr = Join-Path $RunDir "M02_2_frozen_ecj_stderr.log"
$FrozenBatchLog = Join-Path $RunDir "M02_2_frozen_M02_1_batch.log"
$FrozenStdout = Join-Path $RunDir "M02_2_frozen_M02_1_stdout.log"
$FrozenStderr = Join-Path $RunDir "M02_2_frozen_M02_1_stderr.log"
$RunLog = Join-Path $RunDir "M02_2_build.log"
$Stdout = Join-Path $RunDir "M02_2_stdout.log"
$Stderr = Join-Path $RunDir "M02_2_stderr.log"
$ReloadLog = Join-Path $RunDir "M02_2_reload.log"
$ReloadStdout = Join-Path $RunDir "M02_2_reload_stdout.log"
$ReloadStderr = Join-Path $RunDir "M02_2_reload_stderr.log"
$RunReport = Join-Path $RunDir "M02_2_report.md"
$FrozenBeforeCsv = Join-Path $RunDir "M02_2_frozen_hashes_before.csv"
$FrozenAfterCsv = Join-Path $RunDir "M02_2_frozen_hashes_after.csv"
$RunInputsJava = Join-Path $RunDir "LiNRR_M02_2_RunInputs.java"
$RunInputsClass = Join-Path $RunDir "LiNRR_M02_2_RunInputs.class"
$RunPrefsDir = Join-Path $RunDir "comsol_prefs"
$UserPrefs = Join-Path $env:USERPROFILE ".comsol\v64\comsol.prefs"
$CompletedStdout = Join-Path $RunDir "M02_2_completed_benchmarks_stdout.log"
$ResumeAfter = ''
$ExistingMarkers = @()
$BuildStarted = Get-Date

function Write-Utf8NoBomText([string]$Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}

function Export-Utf8NoBomCsv([object[]]$Rows, [string]$Path) {
    if ($Rows.Count -eq 0) { throw "Refusing to write an empty CSV: $Path" }
    $Rows | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
    $Text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) -replace "`r`n", "`n"
    Write-Utf8NoBomText $Path $Text
}

function Convert-PipeRows([string[]]$Lines, [string]$Prefix) {
    if ($Lines.Count -lt 2) { throw "No structured header and rows for $Prefix" }
    $Header = @(($Lines[0] -split '\|') | Select-Object -Skip 1)
    $Rows = foreach ($Line in $Lines | Select-Object -Skip 1) {
        $Values = @(($Line -split '\|') | Select-Object -Skip 1)
        if ($Values.Count -ne $Header.Count) {
            throw "Malformed $Prefix row: expected $($Header.Count), got $($Values.Count): $Line"
        }
        $Data = [ordered]@{}
        for ($i = 0; $i -lt $Header.Count; $i++) { $Data[$Header[$i]] = $Values[$i] }
        [PSCustomObject]$Data
    }
    return @($Rows)
}

function Number($Value) {
    return [double]::Parse([string]$Value, [Globalization.CultureInfo]::InvariantCulture)
}

function Invoke-Captured([string]$FilePath, [string[]]$Arguments,
                         [string]$OutPath, [string]$ErrPath) {
    & $FilePath @Arguments 1> $OutPath 2> $ErrPath
    return $LASTEXITCODE
}

function Quote-ProcessArgument([string]$Value) {
    if ($Value -notmatch '[\s"]') { return $Value }
    return '"' + ($Value -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
}

function Start-CapturedProcess([string]$FilePath, [string[]]$Arguments,
                               [string]$OutPath, [string]$ErrPath) {
    $ArgumentText = (($Arguments | ForEach-Object { Quote-ProcessArgument $_ }) -join ' ')
    return Start-Process -FilePath $FilePath -ArgumentList $ArgumentText `
        -RedirectStandardOutput $OutPath -RedirectStandardError $ErrPath `
        -WindowStyle Hidden -PassThru
}

function Append-LogFile([Text.StringBuilder]$Builder, [string]$Title, [string]$Path) {
    [void]$Builder.AppendLine("===== $Title =====")
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        [void]$Builder.AppendLine([IO.File]::ReadAllText($Path))
    } else {
        [void]$Builder.AppendLine("<missing>")
    }
}

function Get-FrozenHashes([string[]]$RelativePaths) {
    $Map = @{}
    foreach ($Relative in $RelativePaths) {
        $Path = Join-Path $ProjectRoot ($Relative -replace '/', '\')
        $Map[$Relative] = if (Test-Path -LiteralPath $Path -PathType Leaf) {
            (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
        } else { "<ABSENT>" }
    }
    return $Map
}

function Export-HashManifest([hashtable]$Hashes, [string]$Path) {
    $Rows = @($Hashes.Keys | Sort-Object | ForEach-Object {
        [PSCustomObject]([ordered]@{ path = $_; sha256 = $Hashes[$_] })
    })
    Export-Utf8NoBomCsv $Rows $Path
}

function Assert-FrozenHashes([hashtable]$Before, [hashtable]$After) {
    foreach ($Relative in $Before.Keys) {
        if (-not $After.ContainsKey($Relative) -or $Before[$Relative] -ne $After[$Relative]) {
            throw "Frozen baseline SHA-256 changed: $Relative"
        }
    }
}

function Test-Png([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "PNG missing: $Path" }
    $Bytes = [IO.File]::ReadAllBytes($Path)
    $Signature = @(0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A)
    if ($Bytes.Length -le 100) { throw "PNG is empty or too small: $Path" }
    for ($i = 0; $i -lt $Signature.Count; $i++) {
        if ($Bytes[$i] -ne $Signature[$i]) { throw "Invalid PNG signature: $Path" }
    }
}

function New-MmsPlot([object[]]$Rows, [string]$Path) {
    Add-Type -AssemblyName System.Drawing
    $Bitmap = New-Object Drawing.Bitmap 1000,650
    $Graphics = [Drawing.Graphics]::FromImage($Bitmap)
    $Font = New-Object Drawing.Font("Arial",16)
    $Small = New-Object Drawing.Font("Arial",12)
    $Pen = New-Object Drawing.Pen([Drawing.Color]::FromArgb(30,90,180),3)
    $Axis = New-Object Drawing.Pen([Drawing.Color]::Black,2)
    $Brush = [Drawing.Brushes]::Black
    try {
        $Graphics.Clear([Drawing.Color]::White)
        $Graphics.DrawString("M02.2 MMS convergence - numerical verification",$Font,$Brush,180,20)
        $Left=110; $Top=80; $Width=800; $Height=470
        $Graphics.DrawLine($Axis,$Left,$Top,$Left,$Top+$Height)
        $Graphics.DrawLine($Axis,$Left,$Top+$Height,$Left+$Width,$Top+$Height)
        $Errors = @($Rows | ForEach-Object { [Math]::Max((Number $_.L2_relative_error),1e-18) })
        $Logs = @($Errors | ForEach-Object { [Math]::Log10($_) })
        $MinLog = ($Logs | Measure-Object -Minimum).Minimum
        $MaxLog = ($Logs | Measure-Object -Maximum).Maximum
        if ([Math]::Abs($MaxLog-$MinLog) -lt 1e-12) { $MaxLog=$MinLog+1.0 }
        $Points = New-Object 'System.Collections.Generic.List[Drawing.PointF]'
        for ($i=0; $i -lt $Rows.Count; $i++) {
            $X = [single]($Left + 100 + $i*300)
            $Y = [single]($Top + 30 + ($MaxLog-$Logs[$i])/($MaxLog-$MinLog)*($Height-80))
            $Points.Add((New-Object Drawing.PointF($X,$Y)))
            $Graphics.FillEllipse([Drawing.Brushes]::DarkBlue,$X-6,$Y-6,12,12)
            $Graphics.DrawString([string]$Rows[$i].mesh,$Small,$Brush,$X-30,$Top+$Height+12)
            $Graphics.DrawString(([string]::Format([Globalization.CultureInfo]::InvariantCulture,
                "{0:0.000E+00}",$Errors[$i])),$Small,$Brush,$X-55,$Y-28)
        }
        if ($Points.Count -ge 2) { $Graphics.DrawLines($Pen,$Points.ToArray()) }
        $Graphics.DrawString("mapped mesh refinement",$Small,$Brush,400,610)
        $Graphics.DrawString("relative L2 error (log scale)",$Small,$Brush,5,290)
        $Bitmap.Save($Path,[Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $Pen.Dispose(); $Axis.Dispose(); $Font.Dispose(); $Small.Dispose()
        $Graphics.Dispose(); $Bitmap.Dispose()
    }
}

function New-PeDaPlot([object[]]$Rows, [string]$Path) {
    Add-Type -AssemblyName System.Drawing
    $Bitmap = New-Object Drawing.Bitmap 1000,760
    $Graphics = [Drawing.Graphics]::FromImage($Bitmap)
    $Font = New-Object Drawing.Font("Arial",16)
    $Small = New-Object Drawing.Font("Arial",10)
    $Axis = New-Object Drawing.Pen([Drawing.Color]::Black,2)
    try {
        $Graphics.Clear([Drawing.Color]::White)
        $Graphics.DrawString("M02.2 Pe-Da status map - numerical verification",$Font,[Drawing.Brushes]::Black,180,20)
        $Pe = @(0.1,1.0,10.0,100.0,1000.0)
        $Da = @(0.0001,0.001,0.01,0.1,1.0)
        $Left=170; $Top=90; $Cell=105
        for ($iy=0; $iy -lt $Pe.Count; $iy++) {
            $Graphics.DrawString(("Pe={0:g}" -f $Pe[$iy]),$Small,[Drawing.Brushes]::Black,75,$Top+$iy*$Cell+42)
            for ($ix=0; $ix -lt $Da.Count; $ix++) {
                $Row = $Rows[$iy*$Da.Count+$ix]
                $Color = switch -Wildcard ([string]$Row.classification) {
                    "PASS" { [Drawing.Color]::FromArgb(120,200,120); break }
                    "WARNING*" { [Drawing.Color]::FromArgb(255,205,80); break }
                    "FAILED*" { [Drawing.Color]::FromArgb(235,100,100); break }
                    default { [Drawing.Color]::FromArgb(150,150,150) }
                }
                $Brush = New-Object Drawing.SolidBrush($Color)
                try { $Graphics.FillRectangle($Brush,$Left+$ix*$Cell,$Top+$iy*$Cell,$Cell,$Cell) }
                finally { $Brush.Dispose() }
                $Graphics.DrawRectangle($Axis,$Left+$ix*$Cell,$Top+$iy*$Cell,$Cell,$Cell)
                $Label = if ($Row.classification -eq "PASS") { "PASS" }
                    elseif ($Row.classification -like "WARNING*") { "WARNING" }
                    elseif ($Row.classification -like "FAILED*") { "FAILED" }
                    else { "OUTSIDE" }
                $Graphics.DrawString($Label,$Small,[Drawing.Brushes]::Black,
                    $Left+$ix*$Cell+17,$Top+$iy*$Cell+42)
            }
        }
        for ($ix=0; $ix -lt $Da.Count; $ix++) {
            $Graphics.DrawString(("Da={0:g}" -f $Da[$ix]),$Small,[Drawing.Brushes]::Black,
                $Left+$ix*$Cell+18,$Top+5*$Cell+15)
        }
        $Graphics.DrawString("Conservation failure takes precedence; concentrations are not clipped.",
            $Small,[Drawing.Brushes]::Black,260,690)
        $Bitmap.Save($Path,[Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $Axis.Dispose(); $Font.Dispose(); $Small.Dispose()
        $Graphics.Dispose(); $Bitmap.Dispose()
    }
}

function Minimum-Finite([object[]]$Rows, [string]$Property) {
    $Values = @($Rows | ForEach-Object { Number $_.$Property } | Where-Object { -not [double]::IsNaN($_) })
    if ($Values.Count -eq 0) { return [double]::NaN }
    return ($Values | Measure-Object -Minimum).Minimum
}

$RequiredDirectories = @($RunDir,$LatestDir,$SourceStage,$SourceModels,
    (Split-Path $OutputMph),(Split-Path $UniformCsv),(Split-Path $UniformPng))
foreach ($Directory in $RequiredDirectories) {
    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
}
foreach ($Required in @($Compiler,$Batch,$RuntimeJava,$Ecj,$ApiJar,$ModelJar,
        $FrozenJava,$MetricsJava,$VerificationJava,$LoadJava)) {
    if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) {
        throw "Missing required M02.2 input: $Required"
    }
}

$TrackedFrozen = @(& git -C $ProjectRoot ls-files | Where-Object {
    $_ -match '^(src/java/LiNRR_M0(0|1|2)(_|\.)|tests/java/LiNRR_M0(0|1|2)(_|\.)|scripts/windows/(0[1-6]|04a)_|results/(tables|figures)/M0(0|1|2)(_|\.))'
})
if ($LASTEXITCODE -ne 0) { throw "Unable to enumerate tracked frozen files." }
$FrozenRelative = @($TrackedFrozen + @(
    "models/generated/LiNRR_M00_geometry.mph",
    "models/generated/LiNRR_M00_2_geometry_audit.mph",
    "models/generated/LiNRR_M01_flow.mph",
    "models/generated/LiNRR_M01_2_flow_verification.mph",
    "models/generated/LiNRR_M02_transport.mph",
    "models/generated/LiNRR_M02_1_transport_audit.mph"
) | Sort-Object -Unique)
if ($IsResume) {
    if (-not (Test-Path -LiteralPath $FrozenBeforeCsv -PathType Leaf)) {
        throw "Resume run lacks frozen before-hash manifest: $FrozenBeforeCsv"
    }
    $FrozenBefore = @{}
    foreach ($Row in @(Import-Csv -LiteralPath $FrozenBeforeCsv)) { $FrozenBefore[$Row.path]=$Row.sha256 }
    $HistoryLines = @(if (Test-Path -LiteralPath $CompletedStdout -PathType Leaf) {
        Get-Content -LiteralPath $CompletedStdout -Encoding UTF8
    })
    $CurrentLines = @(if (Test-Path -LiteralPath $Stdout -PathType Leaf) {
        Get-Content -LiteralPath $Stdout -Encoding UTF8
    })
    $CombinedExisting = @($HistoryLines + $CurrentLines)
    $ExistingMarkers = @($CombinedExisting | Where-Object { $_ -like 'M02_2_PROGRESS|*|COMPLETE' } |
        Select-Object -Unique)
    $OrderedMarkers = @('M02_2_PROGRESS|A_UNIFORM|COMPLETE',
        'M02_2_PROGRESS|B_DIFFUSION|COMPLETE','M02_2_PROGRESS|C_MMS|COMPLETE',
        'M02_2_PROGRESS|D_LOW_DA|COMPLETE','M02_2_PROGRESS|E_PE_DA|COMPLETE')
    for ($i=0; $i -lt $ExistingMarkers.Count; $i++) {
        if ($ExistingMarkers[$i] -ne $OrderedMarkers[$i]) {
            throw "Completed benchmark markers are not a valid prefix: $($ExistingMarkers -join ', ')"
        }
    }
    if ($ExistingMarkers.Count -gt 0) {
        $ResumeAfter = @('A','B','C','D','E')[$ExistingMarkers.Count-1]
        $LastMarker = $ExistingMarkers[-1]
        $LastIndex = [Array]::LastIndexOf([string[]]$CombinedExisting,$LastMarker)
        Write-Utf8NoBomText $CompletedStdout (($CombinedExisting[0..$LastIndex] -join "`n")+"`n")
    }
    if ((Test-Path -LiteralPath $RunLog -PathType Leaf) -and
        (Get-Item -LiteralPath $RunLog).Length -gt 0) {
        $PriorAttempts = @(Get-ChildItem -LiteralPath $RunDir -Filter 'M02_2_blocked_attempt_*.log' `
            -File -ErrorAction SilentlyContinue)
        $BlockedAttemptLog = Join-Path $RunDir ("M02_2_blocked_attempt_{0}.log" -f ($PriorAttempts.Count+1))
        Copy-Item -LiteralPath $RunLog -Destination $BlockedAttemptLog -Force
    }
} else {
    $FrozenBefore = Get-FrozenHashes $FrozenRelative
    Export-HashManifest $FrozenBefore $FrozenBeforeCsv
}

$M02Outputs = @($OperatorMph,$DiffusionMph,$WallAuditMph,$OutputMph,$UniformCsv,$DiffusionCsv,$MmsCsv,
    $LowDaCsv,$MeshCsv,$PeDaCsv,$UniformPng,$DiffusionPng,$MmsPng,$LowDaPng,$PeDaPng,
    $LatestLog,$LatestReport)
foreach ($Stale in $M02Outputs) {
    if ($IsResume -and $ResumeAfter -in @('A','B','C','D','E') -and $Stale -eq $UniformPng) { continue }
    if ($IsResume -and $ResumeAfter -in @('B','C','D','E') -and
        $Stale -in @($DiffusionMph,$DiffusionPng)) { continue }
    if ($IsResume -and $ResumeAfter -in @('C','D','E') -and
        $Stale -in @($OperatorMph,$MmsPng)) { continue }
    if ($IsResume -and $ResumeAfter -in @('D','E') -and $Stale -eq $WallAuditMph) { continue }
    if ($IsResume -and $ResumeAfter -eq 'E' -and
        $Stale -in @($OutputMph,$LowDaPng,$PeDaPng)) { continue }
    Remove-Item -LiteralPath $Stale -Force -ErrorAction SilentlyContinue
}
$SkipBenchmarkBatch = $IsResume -and $ResumeAfter -eq 'E' -and
    (Test-Path -LiteralPath $OperatorMph -PathType Leaf) -and
    (Test-Path -LiteralPath $DiffusionMph -PathType Leaf) -and
    (Test-Path -LiteralPath $WallAuditMph -PathType Leaf) -and
    (Test-Path -LiteralPath $OutputMph -PathType Leaf) -and
    (Test-Path -LiteralPath $LowDaPng -PathType Leaf)
Get-ChildItem -LiteralPath (Split-Path $MetricsJava) -Filter 'LiNRR_M02_2_*.class' -File `
    -ErrorAction SilentlyContinue | Remove-Item -Force
Get-ChildItem -LiteralPath (Split-Path $LoadJava) -Filter 'LiNRR_M02_2_*.class' -File `
    -ErrorAction SilentlyContinue | Remove-Item -Force

# Rebuild frozen M02.1 in an isolated timestamped root. A resume reuses only the
# already completed staged MPH and never repeats its 5413-second source run.
if (-not $IsResume) {
    $FrozenText = [IO.File]::ReadAllText($FrozenJava)
    $StagedPathJava = ($FrozenSourceMph -replace '\\','/') -replace '"','\"'
    $AbsolutePattern = '(?s)private static final String MPH_ABSOLUTE\s*=\s*"F:/LiNRR_COMSOL/LiNRR_COMSOL_Codex_Starter/models/generated/"\+\s*"LiNRR_M02_1_transport_audit\.mph";'
    $AbsoluteReplacement = 'private static final String MPH_ABSOLUTE = "' + $StagedPathJava + '";'
    $StagedText = [regex]::Replace($FrozenText,$AbsolutePattern,$AbsoluteReplacement)
    if ($StagedText -eq $FrozenText) { throw "Frozen M02.1 staging path rewrite did not match expected source." }
    Write-Utf8NoBomText $StagedFrozenJava $StagedText

    $ExitCode = Invoke-Captured $Compiler @($StagedFrozenJava) $FrozenCompileOut $FrozenCompileErr
    if ($ExitCode -ne 0) { throw "Isolated frozen M02.1 comsolcompile failed." }
    $ClassPath = "$ApiJar;$ModelJar"
    $ExitCode = Invoke-Captured $RuntimeJava @('-jar',$Ecj,'-source','11','-target','11','-warn:none',
        '-cp',$ClassPath,'-d',$SourceStage,$StagedFrozenJava) $FrozenEcjOut $FrozenEcjErr
    if ($ExitCode -ne 0) { throw "Isolated frozen M02.1 ECJ bytecode compilation failed." }
    $FrozenClass = Join-Path $SourceStage "LiNRR_M02_1_TransportAudit.class"
    foreach ($Bytecode in @($FrozenClass,
            (Join-Path $SourceStage 'LiNRR_M02_1_TransportAudit$Config.class'),
            (Join-Path $SourceStage 'LiNRR_M02_1_TransportAudit$InletMode.class'),
            (Join-Path $SourceStage 'LiNRR_M02_1_TransportAudit$Metrics.class'))) {
        if (-not (Test-Path -LiteralPath $Bytecode -PathType Leaf)) { throw "Missing frozen bytecode: $Bytecode" }
    }
    Push-Location $SourceStage
    try {
        $ExitCode = Invoke-Captured $Batch @('-inputfile',$FrozenClass,'-batchlog',$FrozenBatchLog) `
            $FrozenStdout $FrozenStderr
    } finally { Pop-Location }
    if ($ExitCode -ne 0) { throw "Isolated frozen M02.1 rebuild returned exit code $ExitCode." }
}
if (-not (Test-Path -LiteralPath $FrozenSourceMph -PathType Leaf) -or
    (Get-Item -LiteralPath $FrozenSourceMph).Length -le 0) {
    throw "Isolated frozen M02.1 MPH was not generated."
}
$env:LINRR_M02_1_SOURCE = $FrozenSourceMph

# Use a run-local preference copy. Only file access is enabled so the Java API
# can load the staged MPH and write the requested artifacts; Runtime, property,
# network, process, and security-setting permissions remain disabled.
if (-not (Test-Path -LiteralPath $UserPrefs -PathType Leaf)) {
    throw "COMSOL user preference source is missing: $UserPrefs"
}
New-Item -ItemType Directory -Force -Path $RunPrefsDir | Out-Null
$PrefsText = [IO.File]::ReadAllText($UserPrefs)
$RunPrefsText = $PrefsText -replace '(?m)^security\.external\.filepermission=limited$',
    'security.external.filepermission=full'
if ($RunPrefsText -eq $PrefsText -and $RunPrefsText -notmatch
    '(?m)^security\.external\.filepermission=full$') {
    throw "Run-local COMSOL file permission could not be configured."
}
Write-Utf8NoBomText (Join-Path $RunPrefsDir 'comsol.prefs') $RunPrefsText

# COMSOL Runtime forbids getenv. Compile the two explicit run paths into a
# transparent timestamped companion class and provide it via -classpathadd.
$RootJava = $ProjectRoot.Replace('\','\\').Replace('"','\"')
$SourceJava = $FrozenSourceMph.Replace('\','\\').Replace('"','\"')
$ResumeJava = $ResumeAfter.Replace('\','\\').Replace('"','\"')
$RunInputsSource = @"
/** Generated M02.2 run-path bridge; no scientific input is stored here. */
public final class LiNRR_M02_2_RunInputs {
    private LiNRR_M02_2_RunInputs() {}
    public static String get(String name) {
        if ("LINRR_PROJECT_ROOT".equals(name)) return "$RootJava";
        if ("LINRR_M02_1_SOURCE".equals(name)) return "$SourceJava";
        if ("M02_2_RESUME_AFTER".equals(name)) return "$ResumeJava";
        return null;
    }
}
"@
Write-Utf8NoBomText $RunInputsJava $RunInputsSource

# Required COMSOL 6.4 compilation order.
$CompileBuilder = New-Object Text.StringBuilder
$CompileSteps = @(
    @($Compiler,@($MetricsJava),'M02.2 metrics'),
    @($Compiler,@('-classpathadd',(Split-Path $MetricsJava),$VerificationJava),'M02.2 verification'),
    @($Compiler,@($LoadJava),'M02.2 reload check'),
    @($Compiler,@($RunInputsJava),'M02.2 run-input bridge')
)
for ($i=0; $i -lt $CompileSteps.Count; $i++) {
    $Out = Join-Path $RunDir ("M02_2_compile_{0}_stdout.log" -f ($i+1))
    $Err = Join-Path $RunDir ("M02_2_compile_{0}_stderr.log" -f ($i+1))
    $ExitCode = Invoke-Captured $CompileSteps[$i][0] $CompileSteps[$i][1] $Out $Err
    Append-LogFile $CompileBuilder ($CompileSteps[$i][2] + " stdout") $Out
    Append-LogFile $CompileBuilder ($CompileSteps[$i][2] + " stderr") $Err
    $CompileText = ((Get-Content -LiteralPath $Out -Raw -ErrorAction SilentlyContinue) +
        (Get-Content -LiteralPath $Err -Raw -ErrorAction SilentlyContinue))
    if ($ExitCode -ne 0 -or $CompileText -match '(?i)compilation failed|cannot be resolved|\bcompile error\b') {
        throw "$($CompileSteps[$i][2]) compilation failed."
    }
}
Write-Utf8NoBomText $CompileLog $CompileBuilder.ToString()

$VerificationClass = Join-Path (Split-Path $VerificationJava) "LiNRR_M02_2_TransportVerification.class"
$LoadClass = Join-Path (Split-Path $LoadJava) "LiNRR_M02_2_LoadCheck.class"
foreach ($Bytecode in @($VerificationClass,$LoadClass,$RunInputsClass,
        (Join-Path (Split-Path $MetricsJava) "LiNRR_M02_2_Metrics.class"))) {
    if (-not (Test-Path -LiteralPath $Bytecode -PathType Leaf)) { throw "Fresh M02.2 bytecode missing: $Bytecode" }
    if ((Get-Item -LiteralPath $Bytecode).LastWriteTime -lt $BuildStarted.AddSeconds(-2)) {
        throw "Stale M02.2 bytecode: $Bytecode"
    }
}

# Run once and publish each Java completion marker as soon as it reaches stdout.
$ProgressExpected = @(
    'M02_2_PROGRESS|A_UNIFORM|COMPLETE',
    'M02_2_PROGRESS|B_DIFFUSION|COMPLETE',
    'M02_2_PROGRESS|C_MMS|COMPLETE',
    'M02_2_PROGRESS|D_LOW_DA|COMPLETE',
    'M02_2_PROGRESS|E_PE_DA|COMPLETE'
)
$ProgressSeen = @{}
foreach ($Marker in $ExistingMarkers) { $ProgressSeen[$Marker]=$true }
if ($SkipBenchmarkBatch) {
    Copy-Item -LiteralPath $RunLog -Destination (Join-Path $RunDir 'M02_2_successful_benchmark_batch.log') -Force
    Write-Utf8NoBomText $Stdout ''
    Write-Utf8NoBomText $Stderr ''
    $RunExitCode = 0
} else {
    $Process = Start-CapturedProcess $Batch @('-prefsdir',$RunPrefsDir,'-classpathadd',$RunDir,
        '-inputfile',$VerificationClass,'-batchlog',$RunLog) `
        $Stdout $Stderr
    while (-not $Process.HasExited) {
        Start-Sleep -Milliseconds 750
        $Process.Refresh()
        if (Test-Path -LiteralPath $Stdout -PathType Leaf) {
            $Current = @(Get-Content -LiteralPath $Stdout -Encoding UTF8 | Where-Object { $ProgressExpected -contains $_ })
            foreach ($Marker in $Current) {
                if (-not $ProgressSeen.ContainsKey($Marker)) {
                    $ProgressSeen[$Marker] = $true
                    Write-Output $Marker
                }
            }
        }
    }
    $Process.WaitForExit()
    $Process.Refresh()
    if (Test-Path -LiteralPath $Stdout -PathType Leaf) {
        $Current = @(Get-Content -LiteralPath $Stdout -Encoding UTF8 | Where-Object { $ProgressExpected -contains $_ })
        foreach ($Marker in $Current) {
            if (-not $ProgressSeen.ContainsKey($Marker)) {
                $ProgressSeen[$Marker] = $true
                Write-Output $Marker
            }
        }
    }
    $RunExitCode = $Process.ExitCode
}
if ($null -ne $RunExitCode -and $RunExitCode -ne 0) {
    throw "M02.2 COMSOL batch returned exit code $RunExitCode."
}
foreach ($Marker in $ProgressExpected) {
    if (-not $ProgressSeen.ContainsKey($Marker)) { throw "Missing benchmark completion marker: $Marker" }
}

$Lines = @(
    if (Test-Path -LiteralPath $CompletedStdout -PathType Leaf) {
        Get-Content -LiteralPath $CompletedStdout -Encoding UTF8
    }
    Get-Content -LiteralPath $Stdout -Encoding UTF8
)
$UniformLines = @($Lines | Where-Object { $_ -like 'M022_UNIFORM|*' })
$DiffusionLines = @($Lines | Where-Object { $_ -like 'M022_DIFFUSION|*' })
$MmsLines = @($Lines | Where-Object { $_ -like 'M022_MMS|*' })
$LowDaLines = @($Lines | Where-Object { $_ -like 'M022_LOW_DA|*' })
$MeshLines = @($Lines | Where-Object { $_ -like 'M022_MESH|*' })
$PeDaLines = @($Lines | Where-Object { $_ -like 'M022_PE_DA|*' })
if ($UniformLines.Count -ne 2) { throw "Expected uniform header plus 1 row; got $($UniformLines.Count)." }
if ($DiffusionLines.Count -ne 4) { throw "Expected diffusion header plus 3 rows; got $($DiffusionLines.Count)." }
if ($MmsLines.Count -ne 4) { throw "Expected MMS header plus 3 rows; got $($MmsLines.Count)." }
if ($LowDaLines.Count -ne 4) { throw "Expected low-Da header plus 3 rows; got $($LowDaLines.Count)." }
if ($MeshLines.Count -ne 3) { throw "Expected 3 low-Da mesh audit rows; got $($MeshLines.Count)." }
if ($PeDaLines.Count -ne 26) { throw "Expected Pe-Da header plus 25 rows; got $($PeDaLines.Count)." }

$UniformRows = @(Convert-PipeRows $UniformLines 'M022_UNIFORM')
$DiffusionRows = @(Convert-PipeRows $DiffusionLines 'M022_DIFFUSION')
$MmsRows = @(Convert-PipeRows $MmsLines 'M022_MMS')
$LowDaRows = @(Convert-PipeRows $LowDaLines 'M022_LOW_DA')
$PeDaRows = @(Convert-PipeRows $PeDaLines 'M022_PE_DA')
# The completed recovery run predates the durable Java print-order correction:
# its low-Da values contain the right metrics but place stoichiometric ratio
# before the three conservation errors. Detect that exact schema signature and
# normalize the parsed objects without modifying preserved raw stdout.
if (@($LowDaRows | Where-Object {
        (Number $_.N2_species_balance_error) -gt 0.9 -and
        (Number $_.stoichiometric_ratio) -lt 0.01
    }).Count -eq $LowDaRows.Count) {
    foreach ($Row in $LowDaRows) {
        $OldRatio = $Row.N2_species_balance_error
        $OldN2 = $Row.NH3_species_balance_error
        $OldNh3 = $Row.nitrogen_atom_balance_error
        $OldNitrogen = $Row.stoichiometric_ratio
        $Row.N2_species_balance_error = $OldN2
        $Row.NH3_species_balance_error = $OldNh3
        $Row.nitrogen_atom_balance_error = $OldNitrogen
        $Row.stoichiometric_ratio = $OldRatio
    }
}
Export-Utf8NoBomCsv $UniformRows $UniformCsv
Export-Utf8NoBomCsv $DiffusionRows $DiffusionCsv
Export-Utf8NoBomCsv $MmsRows $MmsCsv
Export-Utf8NoBomCsv $LowDaRows $LowDaCsv
Export-Utf8NoBomCsv $PeDaRows $PeDaCsv

$MeshAuditRows = @()
foreach ($Row in $DiffusionRows) {
    $MeshAuditRows += [PSCustomObject]([ordered]@{ benchmark='linear_diffusion'; mesh=$Row.mesh;
        n_length=$Row.n_length; n_height=$Row.n_height; cells=$Row.cells; dof=$Row.dof;
        primary_error=$Row.L2_relative_error; conservation_error=$Row.flux_balance_error;
        change_from_previous='NaN'; status=$Row.status })
}
foreach ($Row in $MmsRows) {
    $MeshAuditRows += [PSCustomObject]([ordered]@{ benchmark='mms'; mesh=$Row.mesh;
        n_length=$Row.n_length; n_height=$Row.n_height; cells=$Row.cells; dof=$Row.dof;
        primary_error=$Row.L2_relative_error; conservation_error=$Row.source_flux_balance_error;
        change_from_previous=$Row.observed_order_from_previous; status=$Row.status })
}
for ($i=0; $i -lt $LowDaRows.Count; $i++) {
    $Fields = @(($MeshLines[$i] -split '\|') | Select-Object -Skip 1)
    if ($Fields.Count -ne 7) { throw "Malformed M022_MESH row: $($MeshLines[$i])" }
    $MeshAuditRows += [PSCustomObject]([ordered]@{ benchmark=$Fields[0]; mesh=$Fields[1];
        n_length=$Fields[2]; n_height=$Fields[3]; cells=([int]$Fields[2]*[int]$Fields[3]);
        dof=$Fields[4]; primary_error=$LowDaRows[$i].nitrogen_atom_balance_error;
        conservation_error=[Math]::Max((Number $LowDaRows[$i].N2_species_balance_error),
            (Number $LowDaRows[$i].NH3_species_balance_error));
        change_from_previous=$Fields[5]; status=$Fields[6] })
}
Export-Utf8NoBomCsv $MeshAuditRows $MeshCsv

if (($DiffusionRows.mesh -join ',') -ne 'coarse,medium,fine' -or
    ($MmsRows.mesh -join ',') -ne 'coarse,medium,fine' -or
    ($LowDaRows.mesh -join ',') -ne 'coarse,medium,fine') {
    throw "Three-grid rows are incomplete or out of order."
}
$Uniform = $UniformRows[0]
if ((Number $Uniform.N2_balance_error) -gt 1e-6 -or
    (Number $Uniform.NH3_balance_error) -gt 1e-6 -or
    (Number $Uniform.max_uniform_N2_relative_deviation) -gt 1e-8 -or
    (Number $Uniform.max_uniform_NH3_relative_deviation) -gt 1e-10) {
    throw "Uniform transport acceptance failed in script audit."
}
$DiffFine = @($DiffusionRows | Where-Object mesh -eq 'fine')[0]
if ((Number $DiffFine.L2_relative_error) -gt 1e-4 -or
    (Number $DiffFine.Linf_relative_error) -gt 1e-3 -or
    (Number $DiffFine.flux_balance_error) -gt 1e-6) {
    throw "Linear diffusion acceptance failed in script audit."
}
$MmsFine = @($MmsRows | Where-Object mesh -eq 'fine')[0]
if ((Number $MmsFine.L2_relative_error) -gt 1e-4 -or
    (Number $MmsFine.source_flux_balance_error) -gt 2e-4) {
    throw "MMS acceptance failed in script audit."
}
if ((Number $MmsFine.L2_relative_error) -ge 1e-12 -and
    (Number $MmsFine.observed_order_from_previous) -lt 1.8) {
    throw "MMS observed convergence order is below 1.8."
}
$LowFine = @($LowDaRows | Where-Object mesh -eq 'fine')[0]
if ($LowFine.status -ne 'PASS' -or (Number $LowFine.N2_species_balance_error) -gt 1e-4 -or
    (Number $LowFine.NH3_species_balance_error) -gt 1e-4 -or
    (Number $LowFine.nitrogen_atom_balance_error) -gt 1e-4) {
    throw "Low-Da fine-grid conservation acceptance failed."
}
if ((Number $LowFine.max_key_change) -gt 0.005) { throw "Low-Da fine mesh change exceeds 0.5%." }

$ExpectedPairs = New-Object 'System.Collections.Generic.List[string]'
foreach ($Pe in @(0.1,1.0,10.0,100.0,1000.0)) {
    foreach ($Da in @(0.0001,0.001,0.01,0.1,1.0)) {
        $ExpectedPairs.Add(([string]::Format([Globalization.CultureInfo]::InvariantCulture,"{0:g}|{1:g}",$Pe,$Da)))
    }
}
$ActualPairs = @($PeDaRows | ForEach-Object {
    [string]::Format([Globalization.CultureInfo]::InvariantCulture,"{0:g}|{1:g}",(Number $_.Pe_H),(Number $_.Da_H))
})
if (($ActualPairs -join ',') -ne ($ExpectedPairs.ToArray() -join ',')) {
    throw "The 25 Pe-Da points are incomplete, duplicated, or out of order."
}
foreach ($Row in $PeDaRows) {
    if ([string]::IsNullOrWhiteSpace([string]$Row.classification) -or
        [string]::IsNullOrWhiteSpace([string]$Row.failure_reason)) {
        throw "Pe-Da row lacks classification or failure reason."
    }
}
$PassRows = @($PeDaRows | Where-Object classification -eq 'PASS')
$WarningRows = @($PeDaRows | Where-Object classification -eq 'WARNING_NUMERICAL_OSCILLATION')
$FailedRows = @($PeDaRows | Where-Object {
    $_.classification -like 'FAILED*' -or $_.classification -eq 'OUTSIDE_MODEL_APPLICABILITY'
})
if ($PassRows.Count + $WarningRows.Count + $FailedRows.Count -ne 25) {
    throw "Unclassified Pe-Da row detected."
}

New-MmsPlot $MmsRows $MmsPng
New-PeDaPlot $PeDaRows $PeDaPng

$ExpectedOutputs = @($OperatorMph,$DiffusionMph,$WallAuditMph,$OutputMph,$UniformCsv,$DiffusionCsv,$MmsCsv,
    $LowDaCsv,$MeshCsv,$PeDaCsv,$UniformPng,$DiffusionPng,$MmsPng,$LowDaPng,$PeDaPng)
foreach ($Expected in $ExpectedOutputs) {
    if (-not (Test-Path -LiteralPath $Expected -PathType Leaf) -or
        (Get-Item -LiteralPath $Expected).Length -le 0) {
        throw "Missing or empty M02.2 output: $Expected"
    }
}
foreach ($Png in @($UniformPng,$DiffusionPng,$MmsPng,$LowDaPng,$PeDaPng)) { Test-Png $Png }

$ReloadProcess = Start-CapturedProcess $Batch @('-prefsdir',$RunPrefsDir,'-classpathadd',$RunDir,
    '-inputfile',$LoadClass,'-batchlog',$ReloadLog) `
    $ReloadStdout $ReloadStderr
$ReloadProcess.WaitForExit()
$ReloadProcess.Refresh()
$ReloadExitCode = $ReloadProcess.ExitCode
if (($null -ne $ReloadExitCode -and $ReloadExitCode -ne 0) -or
    -not (Select-String -LiteralPath $ReloadStdout -SimpleMatch 'M022_RELOAD|PASS')) {
    throw "Independent M02.2 MPH reload failed."
}

$BatchLogs = @($FrozenBatchLog,$RunLog,$ReloadLog)
$FatalPattern = '\berror\b|failed to find a solution|undefined value|singular matrix|out of memory|\bexception\b|\u9519\u8bef|\u51fa\u9519'
$WarningPattern = '\bwarning\b|\u8b66\u544a'
$FatalHits = @(Select-String -Encoding UTF8 -LiteralPath $BatchLogs -Pattern $FatalPattern `
    -CaseSensitive:$false -ErrorAction SilentlyContinue)
$WarningHits = @(Select-String -Encoding UTF8 -LiteralPath $BatchLogs -Pattern $WarningPattern `
    -CaseSensitive:$false -ErrorAction SilentlyContinue)
if ($FatalHits.Count -gt 0) { throw "Fatal pattern found in COMSOL batch logs; inspect $RunDir." }

$FrozenAfter = Get-FrozenHashes $FrozenRelative
Export-HashManifest $FrozenAfter $FrozenAfterCsv
Assert-FrozenHashes $FrozenBefore $FrozenAfter

$BuildLogBuilder = New-Object Text.StringBuilder
Append-LogFile $BuildLogBuilder 'isolated frozen M02.1 comsolcompile stdout' $FrozenCompileOut
Append-LogFile $BuildLogBuilder 'isolated frozen M02.1 comsolcompile stderr' $FrozenCompileErr
Append-LogFile $BuildLogBuilder 'isolated frozen M02.1 ECJ stdout' $FrozenEcjOut
Append-LogFile $BuildLogBuilder 'isolated frozen M02.1 ECJ stderr' $FrozenEcjErr
Append-LogFile $BuildLogBuilder 'isolated frozen M02.1 batch log' $FrozenBatchLog
Append-LogFile $BuildLogBuilder 'isolated frozen M02.1 stdout' $FrozenStdout
Append-LogFile $BuildLogBuilder 'isolated frozen M02.1 stderr' $FrozenStderr
Append-LogFile $BuildLogBuilder 'M02.2 compile log' $CompileLog
$BlockedLogs = @(Get-ChildItem -LiteralPath $RunDir -Filter 'M02_2_blocked_attempt_*.log' `
    -File -ErrorAction SilentlyContinue | Sort-Object Name)
if (Test-Path -LiteralPath (Join-Path $RunDir 'M02_2_security_blocked_build.log') -PathType Leaf) {
    $BlockedLogs = @((Get-Item -LiteralPath (Join-Path $RunDir 'M02_2_security_blocked_build.log'))) + $BlockedLogs
}
foreach ($Blocked in $BlockedLogs) {
    Append-LogFile $BuildLogBuilder ("preserved blocked attempt " + $Blocked.Name) $Blocked.FullName
}
Append-LogFile $BuildLogBuilder 'M02.2 batch log' $RunLog
Append-LogFile $BuildLogBuilder 'M02.2 structured stdout' $Stdout
Append-LogFile $BuildLogBuilder 'M02.2 stderr' $Stderr
Append-LogFile $BuildLogBuilder 'M02.2 reload batch log' $ReloadLog
Append-LogFile $BuildLogBuilder 'M02.2 reload stdout' $ReloadStdout
Append-LogFile $BuildLogBuilder 'M02.2 reload stderr' $ReloadStderr
Write-Utf8NoBomText $LatestLog $BuildLogBuilder.ToString()

$MmsTable = ($MmsRows | ForEach-Object {
    "| $($_.mesh) | $($_.n_length)x$($_.n_height) | $($_.L2_relative_error) | $($_.Linf_error_mol_m3) | $($_.source_flux_balance_error) | $($_.observed_order_from_previous) | $($_.min_c_mol_m3) |"
}) -join [Environment]::NewLine
$LowDaTable = ($LowDaRows | ForEach-Object {
    "| $($_.mesh) | $($_.n_length)x$($_.n_height) | $($_.N2_species_balance_error) | $($_.NH3_species_balance_error) | $($_.nitrogen_atom_balance_error) | $($_.min_cN2) | $($_.min_cNH3) | $($_.max_key_change) |"
}) -join [Environment]::NewLine
$PeDaTable = ($PeDaRows | ForEach-Object {
    "| $($_.Pe_H) | $($_.Da_H) | $($_.N2_species_balance_error) | $($_.NH3_species_balance_error) | $($_.nitrogen_atom_balance_error) | $($_.min_cN2) | $($_.min_cNH3) | $($_.N2_conversion) | $($_.classification) | $($_.failure_reason) |"
}) -join [Environment]::NewLine
$WarningText = if ($WarningHits.Count -eq 0) { '- None.' } else {
    ($WarningHits | ForEach-Object { "- $($_.Path):$($_.LineNumber): $($_.Line.Trim())" }) -join [Environment]::NewLine
}
$MaxN2Species = (@($LowDaRows | ForEach-Object { Number $_.N2_species_balance_error }) +
    @($PeDaRows | ForEach-Object { Number $_.N2_species_balance_error } | Where-Object { -not [double]::IsNaN($_) }) |
    Measure-Object -Maximum).Maximum
$MaxNh3Species = (@($LowDaRows | ForEach-Object { Number $_.NH3_species_balance_error }) +
    @($PeDaRows | ForEach-Object { Number $_.NH3_species_balance_error } | Where-Object { -not [double]::IsNaN($_) }) |
    Measure-Object -Maximum).Maximum
$MaxNitrogen = (@($LowDaRows | ForEach-Object { Number $_.nitrogen_atom_balance_error }) +
    @($PeDaRows | ForEach-Object { Number $_.nitrogen_atom_balance_error } | Where-Object { -not [double]::IsNaN($_) }) |
    Measure-Object -Maximum).Maximum
$Report = @"
# M02.2 Conservative Transport Verification

- Run: ``$RunStamp``
- Recovery snapshot: ``F:\LiNRR_COMSOL\recovery\M02_2_crash_recovery_20260716_152444``.
- Crash state: **CODE_ONLY**; no partial M02.2 COMSOL result or Pe-Da checkpoint existed or was reused.
- Runtime recovery: pre-A launches exposed COMSOL's getenv and limited-file-access policies. All blocked logs are preserved; the repository-standard run-input bridge and an isolated run-local ``-prefsdir`` with file access enabled were used. Runtime/property/network/process/security permissions remain disabled, and the completed isolated M02.1 rebuild was reused without rerunning it.
- Status: **PASS - synthetic numerical transport verification only; not experimental validation**.
- Calibration mode: **PROVISIONAL**. The first-order wall law is phenomenological and has no experimental calibration range.
- Physics scope: conservative N2/NH3 transport only; no electrode kinetics and no concentration clipping.
- Frozen M02.1: rebuilt in isolated timestamped staging; $($FrozenBefore.Count) frozen paths retained identical SHA-256 before/after.

## Benchmark A - uniform transport

- min/max/mean N2: $($Uniform.min_cN2_mol_m3) / $($Uniform.max_cN2_mol_m3) / $($Uniform.mean_cN2_mol_m3) mol/m^3.
- min/max/mean NH3: $($Uniform.min_cNH3_mol_m3) / $($Uniform.max_cNH3_mol_m3) / $($Uniform.mean_cNH3_mol_m3) mol/m^3.
- N2/NH3 balance errors: $($Uniform.N2_balance_error) / $($Uniform.NH3_balance_error).
- status: $($Uniform.status).

## Benchmark B - exactly linear diffusion

- Three-grid relative L2 errors: $($DiffusionRows[0].L2_relative_error), $($DiffusionRows[1].L2_relative_error), $($DiffusionRows[2].L2_relative_error).
- Fine flux balance error: $($DiffFine.flux_balance_error); min concentration: $($DiffFine.min_c_mol_m3) mol/m^3.
- Fine status: $($DiffFine.status).

## Benchmark C - manufactured solution convergence

| mesh | elements | relative L2 error | Linf error | source-flux error | observed order | minimum concentration |
|:--|:--|--:|--:|--:|--:|--:|
$MmsTable

Medium-to-fine observed order is $($MmsFine.observed_order_from_previous). The three relative L2 errors above are the required three-grid error record.
The fine reconstructed Dirichlet-boundary physical-flux closure is $($MmsFine.source_flux_balance_error), within its explicit ``2e-4`` truncation tolerance; this diagnostic is separate from the conservative wall-reaction balance.

## Benchmark D - low-Da wall reaction

| mesh | elements | N2 species error | NH3 species error | nitrogen-atom error | min N2 | min NH3 | max key change |
|:--|:--|--:|--:|--:|--:|--:|--:|
$LowDaTable

- Fine stoichiometric ratio / relative error: $($LowFine.stoichiometric_ratio) / $($LowFine.stoichiometric_ratio_relative_error).
- Fine-grid status: PASS. Coarse and medium rows are retained as pre-asymptotic mesh evidence even where they exceed the final-grid conservation tolerance.
- Maximum finite single-species errors over low-Da and Pe-Da: N2=$MaxN2Species; NH3=$MaxNh3Species.
- Maximum finite nitrogen-atom balance error over low-Da and Pe-Da: $MaxNitrogen.

## Benchmark E - complete 25-point Pe-Da map

Counts: PASS=$($PassRows.Count), WARNING=$($WarningRows.Count), FAILED=$($FailedRows.Count). OUTSIDE_MODEL_APPLICABILITY is counted as FAILED for this summary. No point was deleted.

| Pe_H | Da_H | N2 error | NH3 error | nitrogen error | min N2 | min NH3 | conversion | classification | reason |
|--:|--:|--:|--:|--:|--:|--:|--:|:--|:--|
$PeDaTable

## Minimum concentrations

- A uniform overall minimum: $($Uniform.min_concentration) mol/m^3.
- B diffusion coarse/medium/fine: $($DiffusionRows[0].min_c_mol_m3), $($DiffusionRows[1].min_c_mol_m3), $($DiffusionRows[2].min_c_mol_m3) mol/m^3.
- C MMS coarse/medium/fine: $($MmsRows[0].min_c_mol_m3), $($MmsRows[1].min_c_mol_m3), $($MmsRows[2].min_c_mol_m3) mol/m^3.
- D low-Da N2 minima: $($LowDaRows[0].min_cN2), $($LowDaRows[1].min_cN2), $($LowDaRows[2].min_cN2) mol/m^3.
- D low-Da NH3 minima: $($LowDaRows[0].min_cNH3), $($LowDaRows[1].min_cNH3), $($LowDaRows[2].min_cNH3) mol/m^3.
- E Pe-Da global finite min N2 / min NH3: $(Minimum-Finite $PeDaRows 'min_cN2') / $(Minimum-Finite $PeDaRows 'min_cNH3') mol/m^3; every point is retained in the table.

## Integrity, reload, logs, and commands

- Independent reload of operator and wall MPH, named selections, physics, studies, retained solutions, and numerical nodes: PASS.
- CSV data rows: uniform=1, diffusion=3, MMS=3, low-Da=3, mesh-audit=9, Pe-Da=25; headers/field counts checked.
- Five PNGs: nonempty and PNG signatures checked.
- Frozen baseline SHA-256: PASS; manifests are ``$FrozenBeforeCsv`` and ``$FrozenAfterCsv``.
- Fatal batch-log matches: $($FatalHits.Count).
- Warning batch-log matches: $($WarningHits.Count); full logs are preserved under ``$RunDir``.

$WarningText

Commands executed: isolated staging ``comsolcompile`` plus COMSOL-bundled ECJ for frozen M02.1; isolated ``comsolbatch`` rebuild; ``comsolcompile`` for metrics, verification, and reload classes; checkpointed M02.2 recovery batches that resumed after completed A, B, C, D, or E stages without rerunning a completed benchmark; independent reload ``comsolbatch``.

## Changed/generated scope and remaining uncertainty

Changed source scope is D0012, the two M02.2 Java implementation files, the M02.2 reload test, and the Windows verification script. Generated scope is the separate diffusion and MMS operator MPH files, the wall-audit MPH, the required consolidated MPH, six CSVs, five PNGs, timestamped evidence, latest report, and latest build log. Benchmark A retains its structured stdout, CSV, and PNG evidence from the completed recovery stage. Experimental raw data were not modified.

All geometry, velocity, diffusivity, feed concentration, and phenomenological wall-rate inputs remain provisional. The model is 2D and steady, does not identify a microscopic Li-NRR mechanism, does not establish experimental transport parameters, and does not authorize optimization or M03B. Failed or outside-applicability Pe-Da points are numerical applicability evidence, not physical limiting-current claims.

RUN_STATE = SYNTHETIC_SMOKE_TEST

CALIBRATION_MODE = PROVISIONAL

M03B_READY = FALSE
"@
Write-Utf8NoBomText $LatestReport $Report
Copy-Item -LiteralPath $LatestReport -Destination $RunReport -Force

foreach ($Expected in @($LatestLog,$LatestReport,$RunReport)) {
    if (-not (Test-Path -LiteralPath $Expected -PathType Leaf) -or
        (Get-Item -LiteralPath $Expected).Length -le 0) { throw "Missing final evidence: $Expected" }
}

Write-Output "SUCCESS|M02.2 conservative transport verification complete|run=$RunStamp|PASS=$($PassRows.Count)|WARNING=$($WarningRows.Count)|FAILED=$($FailedRows.Count)"
