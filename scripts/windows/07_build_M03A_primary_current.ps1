$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ComsolRoot = 'F:\COMSOL64\Multiphysics'
$ComsolBin = Join-Path $ComsolRoot 'bin\win64'
$Compiler = Join-Path $ComsolBin 'comsolcompile.exe'
$Batch = Join-Path $ComsolBin 'comsolbatch.exe'
$RuntimeJava = Join-Path $ComsolRoot 'java\win64\jre\bin\java.exe'
$Ecj = Join-Path $ComsolRoot 'plugins\org.eclipse.jdt.core.compiler.batch_3.42.0.v20250526-2018.jar'
$ApiJar = Join-Path $ComsolRoot 'plugins\com.comsol.api_1.0.0.jar'
$ModelJar = Join-Path $ComsolRoot 'plugins\com.comsol.model_1.0.0.jar'

$Java = Join-Path $ProjectRoot 'src\java\LiNRR_M03A_PrimaryCurrent.java'
$LoadJava = Join-Path $ProjectRoot 'tests\java\LiNRR_M03A_LoadCheck.java'
$Output = Join-Path $ProjectRoot 'models\generated\LiNRR_M03A_primary_current.mph'
$SummaryCsv = Join-Path $ProjectRoot 'results\tables\M03A_current_summary.csv'
$ScanCsv = Join-Path $ProjectRoot 'results\tables\M03A_parameter_scan.csv'
$MeshCsv = Join-Path $ProjectRoot 'results\tables\M03A_mesh_audit.csv'
$PotentialPng = Join-Path $ProjectRoot 'results\figures\M03A_electrolyte_potential.png'
$CurrentPng = Join-Path $ProjectRoot 'results\figures\M03A_current_density.png'
$ScalingPng = Join-Path $ProjectRoot 'results\figures\M03A_voltage_scaling.png'
$LatestDir = Join-Path $ProjectRoot 'runs\latest'
$LatestLog = Join-Path $LatestDir 'M03A_build.log'
$LatestReport = Join-Path $LatestDir 'M03A_report.md'

$RunStamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$RunDir = Join-Path $ProjectRoot ("runs\{0}_M03A" -f $RunStamp)
$CompileLog = Join-Path $RunDir 'M03A_comsolcompile.log'
$EcjLog = Join-Path $RunDir 'M03A_ecj_compile.log'
$LoadCompileLog = Join-Path $RunDir 'M03A_loadcheck_compile.log'
$RunLog = Join-Path $RunDir 'M03A_build.log'
$Stdout = Join-Path $RunDir 'M03A_stdout.log'
$Stderr = Join-Path $RunDir 'M03A_stderr.log'
$ReloadLog = Join-Path $RunDir 'M03A_reload.log'
$ReloadStdout = Join-Path $RunDir 'M03A_reload_stdout.log'
$ReloadStderr = Join-Path $RunDir 'M03A_reload_stderr.log'
$RunReport = Join-Path $RunDir 'M03A_report.md'
$BuildStarted = Get-Date

@((Split-Path $Output), (Split-Path $SummaryCsv), (Split-Path $PotentialPng),
  $LatestDir, $RunDir) | ForEach-Object {
    New-Item -ItemType Directory -Force -Path $_ | Out-Null
}
foreach ($Required in @($Compiler,$Batch,$RuntimeJava,$Ecj,$ApiJar,$ModelJar,$Java,$LoadJava)) {
    if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) {
        throw "Missing required M03A build input: $Required"
    }
}

# Remove only M03A-derived artifacts.  M00 through M02.1 remain untouched.
Get-ChildItem -LiteralPath (Split-Path $Java) -Filter 'LiNRR_M03A_PrimaryCurrent*.class' -File |
    Remove-Item -Force
Get-ChildItem -LiteralPath (Split-Path $LoadJava) -Filter 'LiNRR_M03A_LoadCheck*.class' -File |
    Remove-Item -Force
foreach ($Stale in @($Output,$SummaryCsv,$ScanCsv,$MeshCsv,$PotentialPng,$CurrentPng,
    $ScalingPng,$LatestLog,$LatestReport,
    (Join-Path (Split-Path $Java) 'LiNRR_M03A_PrimaryCurrent.class.status'),
    (Join-Path (Split-Path $Java) 'LiNRR_M03A_PrimaryCurrent_Model.mph'),
    (Join-Path (Split-Path $Java) 'LiNRR_M03A_ApiProbe_Probe.mph'),
    (Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_LoadCheck.class.status'),
    (Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_LoadCheck_M03ALoadCheck.mph'))) {
    Remove-Item -LiteralPath $Stale -Force -ErrorAction SilentlyContinue
}
Get-ChildItem -LiteralPath $LatestDir -Filter 'M03A_dev_*' -File -ErrorAction SilentlyContinue |
    Remove-Item -Force
Get-ChildItem -LiteralPath $LatestDir -Filter 'M03A_api_probe*' -File -ErrorAction SilentlyContinue |
    Remove-Item -Force

function Parse-StructuredRows([string]$Prefix, [string[]]$Lines) {
    $Matched = @($Lines | Where-Object { $_ -like "$Prefix|*" })
    if ($Matched.Count -lt 2) { throw "No structured rows for $Prefix" }
    $Headers = @($Matched[0] -split '\|' | Select-Object -Skip 1)
    $Rows = foreach ($Line in $Matched | Select-Object -Skip 1) {
        $Fields = @($Line -split '\|' | Select-Object -Skip 1)
        if ($Fields.Count -ne $Headers.Count) {
            throw "Malformed $Prefix row: expected $($Headers.Count), got $($Fields.Count): $Line"
        }
        $Object = [ordered]@{}
        for ($i=0; $i -lt $Headers.Count; $i++) { $Object[$Headers[$i]] = $Fields[$i] }
        [PSCustomObject]$Object
    }
    return @($Rows)
}

function Write-Utf8NoBomCsv([object[]]$Rows, [string]$Path) {
    $Rows | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
    $Text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) -replace "`r`n", "`n"
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}

function Number($Value) {
    return [double]::Parse([string]$Value, [Globalization.CultureInfo]::InvariantCulture)
}

function Draw-VoltageScaling([object[]]$Rows, [string]$Path) {
    $Bitmap=$null; $G=$null; $TitleFont=$null; $Font=$null; $Small=$null
    $AxisPen=$null; $GridPen=$null
    Add-Type -AssemblyName System.Drawing
    $Width=1000; $Height=700; $Left=100; $Right=40; $Top=70; $Bottom=90
    $PlotW=$Width-$Left-$Right; $PlotH=$Height-$Top-$Bottom
    $Xmax=500.0
    $Ymax=(($Rows | ForEach-Object {[Math]::Max((Number $_.simulated_voltage_V),(Number $_.analytic_voltage_V))}) |
        Measure-Object -Maximum).Maximum * 1.08
    $Bitmap=[Drawing.Bitmap]::new($Width,$Height)
    $G=[Drawing.Graphics]::FromImage($Bitmap)
    try {
        $G.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $G.Clear([Drawing.Color]::White)
        $TitleFont=[Drawing.Font]::new('Arial',[single]18,[Drawing.FontStyle]::Bold,[Drawing.GraphicsUnit]::Point)
        $Font=[Drawing.Font]::new('Arial',[single]10,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Point)
        $Small=[Drawing.Font]::new('Arial',[single]9,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Point)
        $AxisPen=[Drawing.Pen]::new([Drawing.Color]::Black,[single]2)
        $GridPen=[Drawing.Pen]::new([Drawing.Color]::LightGray,[single]1)
        $Brush=[Drawing.Brushes]::Black
        $G.DrawString('M03A voltage scaling: numerical points and analytical lines',$TitleFont,$Brush,155,25)
        for($i=0;$i -le 5;$i++){
            $x=$Left+[int][Math]::Round($PlotW*$i/5.0)
            $y=$Top+$PlotH-[int][Math]::Round($PlotH*$i/5.0)
            $G.DrawLine($GridPen,$x,$Top,$x,$Top+$PlotH)
            $G.DrawLine($GridPen,$Left,$y,$Left+$PlotW,$y)
            $G.DrawString(("{0:N0}" -f ($Xmax*$i/5.0)),$Font,$Brush,$x-12,$Top+$PlotH+10)
            $G.DrawString(("{0:N2}" -f ($Ymax*$i/5.0)),$Font,$Brush,45,$y-7)
        }
        $G.DrawLine($AxisPen,$Left,$Top,$Left,$Top+$PlotH)
        $G.DrawLine($AxisPen,$Left,$Top+$PlotH,$Left+$PlotW,$Top+$PlotH)
        $G.DrawString('Icell (mA)',$Font,$Brush,$Left+$PlotW/2-30,$Height-50)
        $G.DrawString('Voltage drop (V)',$Font,$Brush,8,$Top-30)
        $Colors=@([Drawing.Color]::Firebrick,[Drawing.Color]::DarkOrange,
            [Drawing.Color]::ForestGreen,[Drawing.Color]::RoyalBlue,[Drawing.Color]::DarkViolet)
        $Kappas=@(0.1,0.2,0.5,1.0,2.0)
        for($k=0;$k -lt $Kappas.Count;$k++){
            $Group=@($Rows | Where-Object {[Math]::Abs((Number $_.kappa_el_S_m)-$Kappas[$k])-lt 1e-12} |
                Sort-Object {[double]$_.Icell_mA})
            $Pen=[Drawing.Pen]::new($Colors[$k],[single]2)
            $PointBrush=[Drawing.SolidBrush]::new($Colors[$k])
            try {
                $Last=$null
                foreach($Row in $Group){
                    $x=$Left+[int][Math]::Round($PlotW*(Number $Row.Icell_mA)/$Xmax)
                    $ya=$Top+$PlotH-[int][Math]::Round($PlotH*(Number $Row.analytic_voltage_V)/$Ymax)
                    if($null -ne $Last){$G.DrawLine($Pen,$Last[0],$Last[1],$x,$ya)}
                    $Last=@($x,$ya)
                    $yn=$Top+$PlotH-[int][Math]::Round($PlotH*(Number $Row.simulated_voltage_V)/$Ymax)
                    $G.FillEllipse($PointBrush,$x-4,$yn-4,8,8)
                }
                $ly=65+22*$k; $lx=720
                $G.DrawLine($Pen,$lx,$ly,$lx+30,$ly)
                $G.FillEllipse($PointBrush,$lx+11,$ly-4,8,8)
                $G.DrawString(("kappa = {0:N1} S/m" -f $Kappas[$k]),$Font,$Brush,$lx+38,$ly-8)
            } finally {$Pen.Dispose();$PointBrush.Dispose()}
        }
        $G.DrawString('Circles: COMSOL numerical values; straight segments: analytical values; no fitted smoothing.',
            $Small,[Drawing.Brushes]::DimGray,220,$Height-24)
        $Bitmap.Save($Path,[Drawing.Imaging.ImageFormat]::Png)
    } finally {
        if($null -ne $G){$G.Dispose()}; if($null -ne $Bitmap){$Bitmap.Dispose()}
        if($null -ne $TitleFont){$TitleFont.Dispose()}; if($null -ne $Font){$Font.Dispose()}
        if($null -ne $Small){$Small.Dispose()}; if($null -ne $AxisPen){$AxisPen.Dispose()}
        if($null -ne $GridPen){$GridPen.Dispose()}
    }
}

function Annotate-FieldFigure([string]$Path,[string]$Title,[string]$Caption,[bool]$DarkBackground) {
    Add-Type -AssemblyName System.Drawing
    $Source=[Drawing.Image]::FromFile($Path)
    $Bitmap=[Drawing.Bitmap]::new($Source.Width,$Source.Height)
    $G=[Drawing.Graphics]::FromImage($Bitmap)
    $TitleFont=$null; $CaptionFont=$null
    try {
        $G.Clear([Drawing.Color]::White)
        $G.DrawImage($Source,0,0,$Source.Width,$Source.Height)
        $TitleFont=[Drawing.Font]::new('Arial',[single]17,[Drawing.FontStyle]::Bold,[Drawing.GraphicsUnit]::Point)
        $CaptionFont=[Drawing.Font]::new('Arial',[single]10,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Point)
        $Brush=[Drawing.Brushes]::Black
        $TitleSize=$G.MeasureString($Title,$TitleFont)
        $CaptionSize=$G.MeasureString($Caption,$CaptionFont)
        $G.DrawString($Title,$TitleFont,$Brush,($Source.Width-$TitleSize.Width)/2,22)
        $G.DrawString($Caption,$CaptionFont,$Brush,($Source.Width-$CaptionSize.Width)/2,$Source.Height-40)
    } finally {
        if($null -ne $TitleFont){$TitleFont.Dispose()}
        if($null -ne $CaptionFont){$CaptionFont.Dispose()}
        $G.Dispose(); $Source.Dispose()
    }
    $Temporary=$Path+'.tmp.png'
    try {$Bitmap.Save($Temporary,[Drawing.Imaging.ImageFormat]::Png)} finally {$Bitmap.Dispose()}
    Move-Item -LiteralPath $Temporary -Destination $Path -Force
}

Push-Location $ProjectRoot
try {
    & $Compiler $Java 2>&1 | Tee-Object -FilePath $CompileLog
    if ($LASTEXITCODE -ne 0) { throw "M03A comsolcompile failed: $CompileLog" }

    # comsolcompile validates the source.  COMSOL's bundled ECJ emits the
    # private Metrics helper bytecode from that same source.
    $ClassPath = "$ApiJar;$ModelJar"
    & $RuntimeJava -jar $Ecj -source 11 -target 11 -warn:none -cp $ClassPath `
        -d (Split-Path $Java) $Java 2>&1 | Tee-Object -FilePath $EcjLog
    if ($LASTEXITCODE -ne 0) { throw "M03A ECJ compilation failed: $EcjLog" }

    & $Compiler $LoadJava 2>&1 | Tee-Object -FilePath $LoadCompileLog
    if ($LASTEXITCODE -ne 0) { throw "M03A load-check compilation failed: $LoadCompileLog" }

    $Class = Join-Path (Split-Path $Java) 'LiNRR_M03A_PrimaryCurrent.class'
    $Nested = Join-Path (Split-Path $Java) 'LiNRR_M03A_PrimaryCurrent$Metrics.class'
    $LoadClass = Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_LoadCheck.class'
    foreach($Bytecode in @($Class,$Nested,$LoadClass)){
        if(-not(Test-Path -LiteralPath $Bytecode -PathType Leaf)){throw "Missing M03A bytecode: $Bytecode"}
        if((Get-Item -LiteralPath $Bytecode).LastWriteTime -lt $BuildStarted.AddSeconds(-2)){
            throw "Stale M03A bytecode: $Bytecode"
        }
    }

    $Process=Start-Process -FilePath $Batch -ArgumentList @(
        '-inputfile',$Class,'-batchlog',$RunLog
    ) -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr `
      -WindowStyle Hidden -Wait -PassThru
    if($Process.ExitCode -ne 0){throw "M03A COMSOL batch exit code $($Process.ExitCode)."}
    if(-not(Test-Path -LiteralPath $RunLog -PathType Leaf)){throw 'M03A batch log missing.'}

    $FatalPattern='Exception|ERROR|Failed|Undefined|Singular matrix|Out of memory|错误|出错'
    $FatalHits=@(Select-String -LiteralPath $RunLog,$Stderr -Pattern $FatalPattern -CaseSensitive:$false)
    if($FatalHits.Count -gt 0){
        $FatalHits | ForEach-Object {Write-Host ('FATAL LOG MATCH: '+$_.Line)}
        throw 'Fatal pattern found in M03A solve log.'
    }

    $Lines=@(Get-Content -LiteralPath $Stdout -Encoding UTF8)
    $Summary=@(Parse-StructuredRows 'M03A_SUMMARY' $Lines)
    $Mesh=@(Parse-StructuredRows 'M03A_MESH' $Lines)
    $Scan=@(Parse-StructuredRows 'M03A_SCAN' $Lines)
    $SelectedLines=@($Lines | Where-Object {$_ -like 'M03A_SELECTED|*'})
    if($Summary.Count -ne 16){throw "Expected 16 M03A summary rows; got $($Summary.Count)."}
    if($Mesh.Count -ne 3){throw "Expected 3 M03A mesh rows; got $($Mesh.Count)."}
    if($Scan.Count -ne 25){throw "Expected 25 M03A scan rows; got $($Scan.Count)."}
    if($SelectedLines.Count -ne 1){throw 'Expected exactly one M03A selected-mesh line.'}
    $SelectedFields=@($SelectedLines[0] -split '\|')
    $SelectedName=$SelectedFields[1]; $SelectedNx=$SelectedFields[2]; $SelectedNy=$SelectedFields[3]

    Write-Utf8NoBomCsv $Summary $SummaryCsv
    Write-Utf8NoBomCsv $Mesh $MeshCsv
    Write-Utf8NoBomCsv $Scan $ScanCsv
    $Metric=@{}
    foreach($Row in $Summary){$Metric[$Row.metric]=$Row}
    Annotate-FieldFigure $PotentialPng 'M03A electrolyte potential (V)' `
        ("Range: {0} to {1} V; upper anode to grounded lower cathode." -f `
        '0',$Metric['simulated_voltage_drop'].value) $false
    Annotate-FieldFigure $CurrentPng 'M03A current density magnitude (A/m^2)' `
        ("Range: {0} to {1} A/m^2; arrows point anode to cathode." -f `
        $Metric['minimum_current_density'].value,$Metric['maximum_current_density'].value) $true
    Draw-VoltageScaling $Scan $ScalingPng

    $LoadProcess=Start-Process -FilePath $Batch -ArgumentList @(
        '-inputfile',$LoadClass,'-batchlog',$ReloadLog
    ) -RedirectStandardOutput $ReloadStdout -RedirectStandardError $ReloadStderr `
      -WindowStyle Hidden -Wait -PassThru
    if($LoadProcess.ExitCode -ne 0){throw "M03A reload batch exit code $($LoadProcess.ExitCode)."}
    $ReloadFatal=@(Select-String -LiteralPath $ReloadLog,$ReloadStderr -Pattern $FatalPattern -CaseSensitive:$false)
    if($ReloadFatal.Count -gt 0){
        $ReloadFatal | ForEach-Object {Write-Host ('RELOAD FATAL MATCH: '+$_.Line)}
        throw 'Fatal pattern found in M03A reload log.'
    }
    $LoadLines=@(Get-Content -LiteralPath $ReloadStdout -Encoding UTF8 |
        Where-Object {$_ -like 'M03A_LOAD|PASS|PrimaryCurrentDistribution|*'})
    if($LoadLines.Count -ne 1){throw 'M03A independent MPH reload check did not emit PASS.'}

    $Expected=@($Output,$SummaryCsv,$ScanCsv,$MeshCsv,$PotentialPng,$CurrentPng,$ScalingPng)
    foreach($File in $Expected){
        if(-not(Test-Path -LiteralPath $File -PathType Leaf)){throw "Missing M03A output: $File"}
        $Item=Get-Item -LiteralPath $File
        if($Item.Length -le 0 -or $Item.LastWriteTime -lt $BuildStarted.AddSeconds(-2)){
            throw "Empty or stale M03A output: $File"
        }
    }

    if((Number $Metric['current_balance_relative_error'].value)-gt 1e-6){throw 'Base current balance exceeds 1e-6.'}
    if((Number $Metric['voltage_analytic_relative_error'].value)-gt 1e-4){throw 'Base analytical voltage error exceeds 1e-4.'}
    if((Number $Metric['side_leakage_relative_error'].value)-gt 1e-8){throw 'Base side leakage exceeds 1e-8.'}
    if((Number $Metric['anode_total_current'].value)-ge 0 -or (Number $Metric['cathode_total_current'].value)-le 0){
        throw 'Base outward-normal current signs are incorrect.'
    }

    $FailedMesh=@($Mesh | Where-Object {$_.status -ne 'PASS'})
    $FailedScan=@($Scan | Where-Object {$_.status -ne 'PASS'})

    $CurrentScalingErrors=@()
    foreach($K in @(0.1,0.2,0.5,1.0,2.0)){
        $Group=@($Scan | Where-Object {[Math]::Abs((Number $_.kappa_el_S_m)-$K)-lt 1e-12})
        $Ref=(Number $Group[0].simulated_voltage_V)/(Number $Group[0].Icell_mA)
        foreach($Row in $Group){
            $Ratio=(Number $Row.simulated_voltage_V)/(Number $Row.Icell_mA)
            $CurrentScalingErrors += [Math]::Abs($Ratio/$Ref-1.0)
        }
    }
    $InverseKappaErrors=@()
    foreach($I in @(10.0,50.0,100.0,250.0,500.0)){
        $Group=@($Scan | Where-Object {[Math]::Abs((Number $_.Icell_mA)-$I)-lt 1e-12})
        $Ref=(Number $Group[0].simulated_voltage_V)*(Number $Group[0].kappa_el_S_m)
        foreach($Row in $Group){
            $Product=(Number $Row.simulated_voltage_V)*(Number $Row.kappa_el_S_m)
            $InverseKappaErrors += [Math]::Abs($Product/$Ref-1.0)
        }
    }
    $MaxCurrentScaling=($CurrentScalingErrors | Measure-Object -Maximum).Maximum
    $MaxInverseKappa=($InverseKappaErrors | Measure-Object -Maximum).Maximum
    if($MaxCurrentScaling -gt 1e-4 -or $MaxInverseKappa -gt 1e-4){
        throw 'M03A voltage scaling identity exceeds 1e-4.'
    }

    $WarningHits=@(Select-String -LiteralPath $RunLog,$ReloadLog -Pattern 'warning|警告' -CaseSensitive:$false)
    $MeshTable=($Mesh | ForEach-Object {
        "| $($_.mesh) | $($_.nx)x$($_.ny) | $($_.dof) | $($_.solve_s) | $($_.anode_current_A) | $($_.cathode_current_A) | $($_.current_balance_relative_error) | $($_.simulated_voltage_V) | $($_.voltage_relative_error) | $($_.bulk_mean_current_density_A_m2) | $($_.bulk_current_density_cv) | $($_.status) |"
    }) -join [Environment]::NewLine
    $ScanTable=($Scan | ForEach-Object {
        "| $($_.kappa_el_S_m) | $($_.Icell_mA) | $($_.j_app_A_m2) | $($_.simulated_voltage_V) | $($_.analytic_voltage_V) | $($_.voltage_relative_error) | $($_.effective_resistance_ohm) | $($_.current_balance_relative_error) | $($_.side_leakage_relative_error) | $($_.status) |"
    }) -join [Environment]::NewLine
    $Warnings=if($WarningHits.Count -eq 0){'- None.'}else{($WarningHits | ForEach-Object {"- ``$($_.Line)``"}) -join [Environment]::NewLine}

    $Report=@"
# M03A Decoupled Primary Current Distribution Report

- Run: ``$RunStamp``
- Status: **PASS — numerical smoke-test acceptance only; not experimentally validated**
- Physics interface: COMSOL 6.4 Electrochemistry Module ``PrimaryCurrentDistribution`` (tag ``cd``).
- Selected mesh: ``$SelectedName`` (``$SelectedNx x $SelectedNy``), the lowest-cost audited mesh meeting every threshold.
- Editable MPH reload: PASS.
- M00, M01, M02, and M02.1 baselines were not modified or overwritten.

## Scope and equation

M03A solves only uniform-electrolyte pure ohmic conduction:

``div(i_l)=0``, with ``i_l=-kappa_el grad(phi_l)``.

The installed COMSOL 6.4 ``wire_electrode.mph`` example was loaded read-only and reported the interface type ``PrimaryCurrentDistribution``. A separate actual API construction probe then confirmed the no-kinetics boundary feature types ``ElectrolyteCurrent`` (properties ``IonicCurrentType``, ``Itl``, ``Ial``) and ``ElectrolytePotential`` (property ``philbnd``). M03A uses these direct electrolyte boundary conditions and contains no ``ElectrodeReaction`` feature.

M03A只验证均匀电解液中的纯欧姆传导。

M03A不包含Li沉积、HOR、活化极化、浓差极化、SEI、N2反应或FE。

M03A不是Li-NRR机理模型，也不是实验验证。

## Geometry and editable parameters

- Two-dimensional rectangle: ``Lcell=55 mm`` by ``Hcell=4 mm``; out-of-plane active width ``Wcell=55 mm``; ``T0=298.15 K``.
- Stable coordinate selections: ``sel_electrolyte``, ``sel_inlet``, ``sel_outlet``, ``sel_anode_wall``, and ``sel_cathode_wall``. No durable source code uses raw entity numbers.
- Global parameters retained in the GUI: ``Lcell``, ``Hcell``, ``Wcell``, ``T0``, ``kappa_el``, ``Icell``, ``Aelec``, ``j_app``, ``R_analytic``, and ``V_analytic``.
- Geometry and temperature are specified inputs. ``Aelec``, ``j_app``, ``R_analytic``, and ``V_analytic`` are derived.
- ``kappa_el=0.5 S/m`` and ``Icell=100 mA`` are **PROVISIONAL — numerical smoke test only**. They are not claimed to be the real conductivity or formal working current of a LiBF4/Diglyme/EtOH system.

## Boundary conditions and current sign

- Upper ``sel_anode_wall``: uniform electrolyte current density ``j_app=Icell/Aelec`` injected into the liquid. This is mathematically identical to a total current ``Icell`` over the full planar electrode.
- Lower ``sel_cathode_wall``: ``phi_l=0 V`` through ``ElectrolytePotential``.
- Left ``sel_inlet`` and right ``sel_outlet``: the default ``Insulation`` condition, ``n dot i_l=0``.
- Every reported boundary current is the signed integral of ``i_l dot n`` with ``n`` outward from the electrolyte. The accepted base gives anode ``$($Metric['anode_total_current'].value) A`` (negative, current enters) and cathode ``$($Metric['cathode_total_current'].value) A`` (positive, current exits). Absolute values were not taken before this sign check.

## Analytical benchmark

For full, parallel electrodes, ``Aelec=Lcell*Wcell``, ``R=Hcell/(kappa_el*Aelec)``, ``Delta_phi=Icell*R``, and ``j=Icell/Aelec``.

- Simulated / analytical voltage: ``$($Metric['simulated_voltage_drop'].value)`` / ``$($Metric['analytic_voltage_drop'].value) V``.
- Voltage relative error: ``$($Metric['voltage_analytic_relative_error'].value)`` (limit ``1e-4``).
- Effective / analytical resistance: ``$($Metric['effective_resistance'].value)`` / ``$($Metric['analytic_resistance'].value) ohm``.
- Bulk mean / maximum / minimum current density: ``$($Metric['bulk_mean_current_density'].value)`` / ``$($Metric['maximum_current_density'].value)`` / ``$($Metric['minimum_current_density'].value) A/m^2``.
- Bulk current-density coefficient of variation: ``$($Metric['bulk_current_density_cv'].value)``. The bulk line spans ``x/Lcell=0.02..0.98`` at ``y/Hcell=0.5`` and excludes corners.

## Current conservation and leakage

- Current-balance relative error: ``$($Metric['current_balance_relative_error'].value)`` (limit ``1e-6``).
- Left/right signed insulation current: ``$($Metric['left_insulation_current'].value)`` / ``$($Metric['right_insulation_current'].value) A``.
- Side leakage magnitude and relative error: ``$($Metric['side_leakage_current'].value) A`` / ``$($Metric['side_leakage_relative_error'].value)`` (limit ``1e-8``).

## Independent mesh audit

The M02.1 350x200 transport mesh was not inherited. M03A independently compares the required mapped meshes at the base condition.

| mesh | elements | DOF | solve s | anode A | cathode A | balance error | simulated V | voltage error | bulk mean j | bulk CV | status |
|:--|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|:--|
$MeshTable

Because the exact solution is linear in ``y`` and the mapped linear finite-element space represents it, coarse is already accepted and is retained as the lowest-cost mesh. This numerical result does not imply that coarse meshes will be sufficient after geometry, conductivity, or coupled physics become nonuniform.

## 25-point parameter scan

All requested combinations are retained. Fixed-conductivity voltage/current proportionality has maximum relative identity error ``$MaxCurrentScaling``; fixed-current voltage/inverse-conductivity proportionality has maximum relative identity error ``$MaxInverseKappa``.

| kappa S/m | Icell mA | j_app A/m2 | simulated V | analytical V | voltage error | effective R ohm | balance error | leakage error | status |
|--:|--:|--:|--:|--:|--:|--:|--:|--:|:--|
$ScanTable

Failed combinations: $($FailedScan.Count). No row was silently deleted.

## Figures and GUI editability

- ``M03A_electrolyte_potential.png``: COMSOL electrolyte potential in V across the electrode gap.
- ``M03A_current_density.png``: COMSOL current-density magnitude in A/m2 plus direction arrows.
- ``M03A_voltage_scaling.png``: numerical points and analytical straight segments for each conductivity; no fitted smoothing.
- The MPH retains stable component, geometry, mesh, physics, study, plot, export, selection, and parameter tags. The independent load-only batch check uses the explicit audited MPH path and does not read ``user.dir``.

## Log review and commands

- Fatal pattern ``Exception|ERROR|Failed|Undefined|Singular matrix|Out of memory|错误|出错``: 0 solve/reload log matches.
- Warning matches: $($WarningHits.Count). Full warnings, if any, remain in the timestamped logs.
$Warnings
- Compile: ``comsolcompile src/java/LiNRR_M03A_PrimaryCurrent.java`` and ``comsolcompile tests/java/LiNRR_M03A_LoadCheck.java``.
- Solve and reload: separate ``comsolbatch`` runs; timestamped evidence: ``runs/$($RunStamp)_M03A``.

## Scientific limitations

- The conductivity and current are provisional placeholders with no measured or literature provenance assigned.
- Uniform scalar conductivity, full parallel electrodes, and a two-dimensional rectangle deliberately remove current-crowding and material heterogeneity.
- There is no reaction equilibrium, charge-transfer law, activation loss, species migration, concentration dependence, porous electrode, contact resistance, SEI, Li deposition, HOR, N2 reaction, ammonia production, or FE.
- Passing the analytical, mesh, and conservation checks establishes only a numerical primary-current baseline. It is not calibration, mechanism evidence, or experimental validation.

## Minimum data gate before M03B Secondary Current Distribution

- Measured or reliably sourced electrolyte conductivity at the actual composition;
- temperature;
- accurate effective electrode area;
- electrode spacing;
- EIS high-frequency resistance;
- full-cell voltage;
- WE and CE potentials;
- applied current and actual current density;
- at least one calibrated effective cathode kinetic law;
- HOR anode kinetics or evidence demonstrating that HOR is non-limiting.
"@
    Set-Content -LiteralPath $LatestReport -Value $Report -Encoding UTF8
    Copy-Item -LiteralPath $LatestReport -Destination $RunReport -Force
    Copy-Item -LiteralPath $RunLog -Destination $LatestLog -Force

    foreach($Artifact in $Expected){Copy-Item -LiteralPath $Artifact -Destination $RunDir -Force}

    if($FailedMesh.Count -gt 0){throw "$($FailedMesh.Count) mesh audit row(s) failed."}
    if($FailedScan.Count -gt 0){throw "$($FailedScan.Count) parameter scan row(s) failed."}
    Write-Host "[SUCCESS] M03A accepted: mesh=$SelectedName $SelectedNx x $SelectedNy; 25/25 scan rows PASS."
}
finally {
    Get-ChildItem -LiteralPath (Split-Path $Java) -Filter 'LiNRR_M03A_PrimaryCurrent*.class' -File |
        Remove-Item -Force -ErrorAction SilentlyContinue
    Get-ChildItem -LiteralPath (Split-Path $LoadJava) -Filter 'LiNRR_M03A_LoadCheck*.class' -File |
        Remove-Item -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path (Split-Path $Java) 'LiNRR_M03A_PrimaryCurrent.class.status'),
        (Join-Path (Split-Path $Java) 'LiNRR_M03A_PrimaryCurrent_Model.mph'),
        (Join-Path (Split-Path $Java) 'LiNRR_M03A_ApiProbe_Probe.mph'),
        (Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_LoadCheck.class.status'),
        (Join-Path (Split-Path $LoadJava) 'LiNRR_M03A_LoadCheck_M03ALoadCheck.mph') `
        -Force -ErrorAction SilentlyContinue
    Pop-Location
}
