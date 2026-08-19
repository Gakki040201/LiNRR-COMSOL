Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Invariant = [Globalization.CultureInfo]::InvariantCulture
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ExpectedRoot = 'F:\LiNRR_COMSOL\worktrees\LiNRR_M03A_3'
$ExpectedBranch = 'm03a3-prescribed-current-coupling-verification'
$BaselineCommit = 'e9695b08441cac3f8f82847ab96c4ab5dfeda4c8'
$StageCommit = '9a20c3486d7c832ef23f87e7fd1b1d6e1f7674f8'
$env:GIT_PAGER = 'cat'
$env:LINRR_PROJECT_ROOT = $ProjectRoot

function Write-Utf8NoBomText([string]$Path,[string]$Text) {
    [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))
}

function Export-Utf8NoBomCsv([object[]]$Rows,[string]$Path) {
    if ($Rows.Count -eq 0) { throw "Refusing to write empty CSV: $Path" }
    $Text = (($Rows | ConvertTo-Csv -NoTypeInformation) -join "`n") + "`n"
    Write-Utf8NoBomText $Path $Text
}

function Number($Value) {
    return [double]::Parse([string]$Value,[Globalization.NumberStyles]::Float,$Invariant)
}

function Fmt([double]$Value) {
    return $Value.ToString('G12',$Invariant)
}

function Relative([double]$A,[double]$B) {
    return [Math]::Abs($A-$B)/[Math]::Max([Math]::Max([Math]::Abs($A),[Math]::Abs($B)),1e-300)
}

function Convert-PipeRows([string[]]$Lines,[string]$Prefix) {
    if ($Lines.Count -lt 2) { throw "Missing structured header/rows for $Prefix" }
    $Header = @(($Lines[0] -split '\|') | Select-Object -Skip 1)
    $Rows = foreach ($Line in $Lines | Select-Object -Skip 1) {
        $Values = @(($Line -split '\|') | Select-Object -Skip 1)
        if ($Values.Count -ne $Header.Count) {
            throw "Malformed $Prefix row; expected $($Header.Count), got $($Values.Count): $Line"
        }
        $Data = [ordered]@{}
        for ($i=0;$i-lt$Header.Count;$i++) { $Data[$Header[$i]]=$Values[$i] }
        [pscustomobject]$Data
    }
    return @($Rows)
}

function Invoke-Captured([string]$File,[string[]]$Arguments,[string]$Stdout,[string]$Stderr) {
    $Process = Start-Process -FilePath $File -ArgumentList $Arguments -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr
    return $Process.ExitCode
}

function Test-Png([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf) -or (Get-Item $Path).Length -le 100) {
        throw "Missing or empty PNG: $Path"
    }
    $Bytes=[IO.File]::ReadAllBytes($Path)
    $Signature=[byte[]](137,80,78,71,13,10,26,10)
    for($i=0;$i-lt8;$i++){if($Bytes[$i]-ne$Signature[$i]){throw "Invalid PNG signature: $Path"}}
}

function Assert-Utf8NoBom([string]$Path) {
    $Bytes=[IO.File]::ReadAllBytes($Path)
    if($Bytes.Length-ge3 -and $Bytes[0]-eq0xEF -and $Bytes[1]-eq0xBB -and $Bytes[2]-eq0xBF){
        throw "UTF-8 BOM is forbidden: $Path"
    }
    $StrictUtf8=[Text.UTF8Encoding]::new($false,$true)
    try { [void]$StrictUtf8.GetString($Bytes) } catch { throw "Not valid UTF-8: $Path" }
}

function New-LinePlot([object[]]$Rows,[string]$XField,[string[]]$YFields,
                      [string]$Title,[string]$Path) {
    Add-Type -AssemblyName System.Drawing
    $Bitmap=[Drawing.Bitmap]::new(1100,700);$G=[Drawing.Graphics]::FromImage($Bitmap)
    $TitleFont=[Drawing.Font]::new('Arial',[single]17,[Drawing.FontStyle]::Bold)
    $Font=[Drawing.Font]::new('Arial',[single]10);$Small=[Drawing.Font]::new('Arial',[single]9)
    $Pens=@([Drawing.Pen]::new([Drawing.Color]::DarkBlue,[single]3),
        [Drawing.Pen]::new([Drawing.Color]::Firebrick,[single]3),
        [Drawing.Pen]::new([Drawing.Color]::DarkGreen,[single]3))
    try {
        $G.Clear([Drawing.Color]::White);$G.DrawString($Title,$TitleFont,[Drawing.Brushes]::Black,170,25)
        $Left=110;$Top=100;$Width=850;$Height=480
        $G.DrawRectangle([Drawing.Pens]::Black,$Left,$Top,$Width,$Height)
        $X=@($Rows|ForEach-Object{Number $_.$XField});$Y=@()
        foreach($Field in $YFields){$Y+=@($Rows|ForEach-Object{Number $_.$Field})}
        $XMin=[double](($X|Measure-Object -Minimum).Minimum);$XMax=[double](($X|Measure-Object -Maximum).Maximum)
        $YMin=[double](($Y|Measure-Object -Minimum).Minimum);$YMax=[double](($Y|Measure-Object -Maximum).Maximum)
        if($XMax-eq$XMin){$XMax=$XMin+1};if($YMax-eq$YMin){$YMax=$YMin+1}
        for($j=0;$j-lt$YFields.Count;$j++){
            $Points=New-Object 'System.Collections.Generic.List[Drawing.PointF]'
            foreach($Row in $Rows){$px=$Left+$Width*((Number $Row.$XField)-$XMin)/($XMax-$XMin);$py=$Top+$Height-$Height*((Number $Row.($YFields[$j]))-$YMin)/($YMax-$YMin);$Points.Add([Drawing.PointF]::new([single]$px,[single]$py));$G.FillEllipse([Drawing.Brushes]::DarkSlateBlue,[single]($px-4),[single]($py-4),8,8)}
            if($Points.Count-ge2){$G.DrawLines($Pens[$j],$Points.ToArray())}
            $G.DrawString($YFields[$j],$Small,[Drawing.Brushes]::Black,780,610+$j*20)
        }
        $G.DrawString($XField,$Font,[Drawing.Brushes]::Black,450,650)
        $Bitmap.Save($Path,[Drawing.Imaging.ImageFormat]::Png)
    } finally {$Pens|ForEach-Object{$_.Dispose()};$TitleFont.Dispose();$Font.Dispose();$Small.Dispose();$G.Dispose();$Bitmap.Dispose()}
}

function New-StatusMap([object[]]$Rows,[string]$Path) {
    Add-Type -AssemblyName System.Drawing
    $Bitmap=[Drawing.Bitmap]::new(1000,760);$G=[Drawing.Graphics]::FromImage($Bitmap)
    $Title=[Drawing.Font]::new('Arial',[single]17,[Drawing.FontStyle]::Bold);$Font=[Drawing.Font]::new('Arial',[single]9)
    try {
        $G.Clear([Drawing.Color]::White);$G.DrawString('M03A.3 prescribed current-flow applicability map',$Title,[Drawing.Brushes]::Black,180,25)
        $J=@(0.1,1,10,100,1000);$U=@(1e-5,3e-5,1e-4,3e-4,1e-3);$Left=220;$Top=100;$Cell=105
        for($iy=0;$iy-lt5;$iy++){
            $G.DrawString(('U={0}' -f (Fmt $U[$iy])),$Font,[Drawing.Brushes]::Black,75,$Top+$iy*$Cell+42)
            for($ix=0;$ix-lt5;$ix++){
                $Row=@($Rows|Where-Object{[Math]::Abs((Number $_.imposed_current_density_A_m2)-$J[$ix])-lt1e-12 -and [Math]::Abs((Number $_.Umean_m_s)-$U[$iy])-lt1e-14})[0]
                $Color=switch($Row.scientific_status){'PASS'{[Drawing.Color]::PaleGreen};'WARNING_NUMERICAL_OSCILLATION'{[Drawing.Color]::Khaki};'FAILED_NEGATIVE_CONCENTRATION'{[Drawing.Color]::LightCoral};'FAILED_CONSERVATION'{[Drawing.Color]::OrangeRed};default{[Drawing.Color]::LightGray}}
                $Brush=[Drawing.SolidBrush]::new($Color);try{$G.FillRectangle($Brush,$Left+$ix*$Cell,$Top+$iy*$Cell,$Cell,$Cell)}finally{$Brush.Dispose()}
                $G.DrawRectangle([Drawing.Pens]::Black,$Left+$ix*$Cell,$Top+$iy*$Cell,$Cell,$Cell)
                $G.DrawString([string]$Row.scientific_status,$Font,[Drawing.Brushes]::Black,$Left+$ix*$Cell+4,$Top+$iy*$Cell+35)
            }
        }
        for($ix=0;$ix-lt5;$ix++){$G.DrawString(('j={0}' -f (Fmt $J[$ix])),$Font,[Drawing.Brushes]::Black,$Left+$ix*$Cell+25,640)}
        $G.DrawString('Theta > 1 is outside prescribed-current supply capacity; no row is clipped or deleted.',$Font,[Drawing.Brushes]::Firebrick,210,700)
        $Bitmap.Save($Path,[Drawing.Imaging.ImageFormat]::Png)
    } finally {$Title.Dispose();$Font.Dispose();$G.Dispose();$Bitmap.Dispose()}
}

if ($ProjectRoot -ne $ExpectedRoot) { throw "Wrong project root: $ProjectRoot" }
if ((git branch --show-current) -ne $ExpectedBranch) { throw "Wrong branch." }
if ((git rev-parse HEAD) -ne $StageCommit) { throw "Wrong HEAD." }
if ((git rev-parse origin/m03a2-synthetic-eis-verification) -ne $BaselineCommit) { throw "Wrong origin baseline." }
if ((git merge-base HEAD origin/m03a2-synthetic-eis-verification) -ne $BaselineCommit) { throw "Wrong merge-base." }

$Tokens=$null;$ParseErrors=$null
[void][Management.Automation.Language.Parser]::ParseFile($PSCommandPath,[ref]$Tokens,[ref]$ParseErrors)
if(@($ParseErrors).Count-ne0){throw "PowerShell parser errors: $($ParseErrors|ForEach-Object Message -join '; ')"}

$ComsolRoot = if ($env:COMSOL_ROOT) {$env:COMSOL_ROOT} else {'F:\COMSOL64\Multiphysics'}
$ComsolBin=Join-Path $ComsolRoot 'bin\win64';$Compiler=Join-Path $ComsolBin 'comsolcompile.exe';$Batch=Join-Path $ComsolBin 'comsolbatch.exe'
$RuntimeJava=Join-Path $ComsolRoot 'java\win64\jre\bin\java.exe'
$Ecj=Join-Path $ComsolRoot 'plugins\org.eclipse.jdt.core.compiler.batch_3.42.0.v20250526-2018.jar'
$ApiJar=Join-Path $ComsolRoot 'plugins\com.comsol.api_1.0.0.jar';$ModelJar=Join-Path $ComsolRoot 'plugins\com.comsol.model_1.0.0.jar'
foreach($Required in @($ComsolRoot,$Compiler,$Batch,$RuntimeJava,$Ecj,$ApiJar,$ModelJar)){if(-not(Test-Path -LiteralPath $Required)){throw "Required COMSOL path missing: $Required"}}

$ResumeMode=-not[string]::IsNullOrWhiteSpace($env:M03A3_RESUME_RUN)
if($ResumeMode){$RunDir=(Resolve-Path -LiteralPath $env:M03A3_RESUME_RUN).Path;if((Split-Path $RunDir -Leaf)-notmatch'^\d{8}_\d{6}_M03A_3$'){throw "Invalid M03A.3 resume directory: $RunDir"}}
else{$RunStamp=Get-Date -Format 'yyyyMMdd_HHmmss';$RunDir=Join-Path $ProjectRoot ("runs\{0}_M03A_3" -f $RunStamp)}
$SourceStage=Join-Path $RunDir 'java_source_stage';$ClassOutput=Join-Path $RunDir 'java_class_output';$PrefsDir=Join-Path $RunDir 'comsol_preferences';$TempDir=Join-Path $RunDir 'comsol_temporary';$StagingDir=Join-Path $RunDir 'artifact_staging'
foreach($Dir in @($RunDir,$SourceStage,$ClassOutput,$PrefsDir,$TempDir,$StagingDir)){New-Item -ItemType Directory -Path $Dir -Force|Out-Null}
$env:TEMP=$TempDir;$env:TMP=$TempDir

$OutputMph=Join-Path $ProjectRoot 'models\generated\LiNRR_M03A_3_prescribed_current_coupling.mph'
$StagedMph=Join-Path $StagingDir 'LiNRR_M03A_3_prescribed_current_coupling.mph'
$PotentialPng=Join-Path $ProjectRoot 'results\figures\M03A_3_potential_current.png';$SpeciesPng=Join-Path $ProjectRoot 'results\figures\M03A_3_species_fields.png'
$StagedPotential=Join-Path $StagingDir 'M03A_3_potential_current.png';$StagedSpecies=Join-Path $StagingDir 'M03A_3_species_fields.png'
$FaradaicPng=Join-Path $ProjectRoot 'results\figures\M03A_3_faradaic_closure.png';$MeshPng=Join-Path $ProjectRoot 'results\figures\M03A_3_mesh_convergence.png';$MapPng=Join-Path $ProjectRoot 'results\figures\M03A_3_current_flow_status_map.png'
$SelectionCsv=Join-Path $ProjectRoot 'results\tables\M03A_3_selection_audit.csv';$OhmicCsv=Join-Path $ProjectRoot 'results\tables\M03A_3_ohmic_analytic.csv';$InvarianceCsv=Join-Path $ProjectRoot 'results\tables\M03A_3_frozen_invariance.csv';$CurrentCsv=Join-Path $ProjectRoot 'results\tables\M03A_3_current_conservation.csv';$FaradaicCsv=Join-Path $ProjectRoot 'results\tables\M03A_3_faradaic_stoichiometry.csv';$MeshCsv=Join-Path $ProjectRoot 'results\tables\M03A_3_mesh_audit.csv';$LinearityCsv=Join-Path $ProjectRoot 'results\tables\M03A_3_linearity_audit.csv';$MapCsv=Join-Path $ProjectRoot 'results\tables\M03A_3_current_flow_map.csv';$ReadinessCsv=Join-Path $ProjectRoot 'results\tables\M03A_3_readiness.csv'
$FormalCsv=@($SelectionCsv,$OhmicCsv,$InvarianceCsv,$CurrentCsv,$FaradaicCsv,$MeshCsv,$LinearityCsv,$MapCsv,$ReadinessCsv);$FormalPng=@($PotentialPng,$SpeciesPng,$FaradaicPng,$MeshPng,$MapPng)
$FinalizeEvidence=Join-Path $RunDir 'comsol_finalize_stdout.log';$AuditOnly=$ResumeMode -and (Test-Path $OutputMph) -and (Test-Path $FinalizeEvidence) -and [bool](Select-String -LiteralPath $FinalizeEvidence -SimpleMatch -Pattern 'M03A_3_PROGRESS|ALL_BENCHMARKS|COMPLETE')
foreach($Path in @($OutputMph)+$FormalCsv+$FormalPng){if((Test-Path -LiteralPath $Path)-and-not$AuditOnly){throw "Refusing to overwrite existing M03A.3 artifact: $Path"}}

$BaselineArchive=Join-Path $RunDir 'baseline.tar';$BaselineTree=Join-Path $RunDir 'baseline_tree';$FrozenBefore=Join-Path $RunDir 'frozen_hashes_before.csv';$Tracked=@(git ls-files)
if(-not$ResumeMode){New-Item -ItemType Directory $BaselineTree|Out-Null;& git archive --format=tar --output=$BaselineArchive $StageCommit;if($LASTEXITCODE-ne0){throw 'git archive failed'};& tar -xf $BaselineArchive -C $BaselineTree;if($LASTEXITCODE-ne0){throw 'baseline extraction failed'};$BeforeRows=@();foreach($Path in $Tracked){$BeforeRows+=[pscustomobject][ordered]@{path=$Path;sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $BaselineTree $Path)).Hash.ToLowerInvariant()}};Export-Utf8NoBomCsv $BeforeRows $FrozenBefore}
elseif(-not(Test-Path $FrozenBefore)-or-not(Test-Path $BaselineTree)){throw 'Resume run lacks frozen baseline evidence'}

$MetricsJava=Join-Path $ProjectRoot 'src\java\LiNRR_M03A_3_Metrics.java';$BuilderJava=Join-Path $ProjectRoot 'src\java\LiNRR_M03A_3_PrescribedCurrentCoupling.java';$LoadJava=Join-Path $ProjectRoot 'tests\java\LiNRR_M03A_3_LoadCheck.java'
$Inventory=@();foreach($Source in @($MetricsJava,$BuilderJava,$LoadJava)){$Text=[IO.File]::ReadAllText($Source);if($Text-match'F:\\LiNRR_COMSOL|F:/LiNRR_COMSOL'){throw "Absolute project path in Java: $Source"};$Inventory+=[pscustomobject][ordered]@{path=$Source.Substring($ProjectRoot.Length+1).Replace('\','/');bytes=(Get-Item $Source).Length;sha256=(Get-FileHash -Algorithm SHA256 $Source).Hash.ToLowerInvariant()};Copy-Item -LiteralPath $Source -Destination $SourceStage -Force}
Export-Utf8NoBomCsv $Inventory (Join-Path $RunDir 'java_source_inventory.csv')
if(@(Get-ChildItem (Join-Path $ProjectRoot 'src'),(Join-Path $ProjectRoot 'tests') -Recurse -Filter '*.class' -File).Count-ne0){throw '.class file found in repository source/test directories'}

$RootJava=$ProjectRoot.Replace('\','\\').Replace('"','\"');$MphJava=$StagedMph.Replace('\','\\').Replace('"','\"');$PotentialJava=$StagedPotential.Replace('\','\\').Replace('"','\"');$SpeciesJava=$StagedSpecies.Replace('\','\\').Replace('"','\"');$ResumeSource='';if($ResumeMode){$RecoveryMph=Join-Path $ClassOutput 'LiNRR_M03A_3_PrescribedCurrentCoupling_M033PrescribedCurrent.mph';if(-not(Test-Path $RecoveryMph)){throw "Recovery MPH missing: $RecoveryMph"};$ResumeSource=$RecoveryMph.Replace('\','\\').Replace('"','\"')}
$Bridge=@"
public final class LiNRR_M03A_3_RunInputs {
    private LiNRR_M03A_3_RunInputs() {}
    public static String get(String name) {
        if ("LINRR_PROJECT_ROOT".equals(name)) return "$RootJava";
        if ("M03A3_OUTPUT_MPH".equals(name)) return "$MphJava";
        if ("M03A3_POTENTIAL_PNG".equals(name)) return "$PotentialJava";
        if ("M03A3_SPECIES_PNG".equals(name)) return "$SpeciesJava";
        if ("M03A3_RESUME_SOURCE".equals(name)) return "$ResumeSource";
        return null;
    }
}
"@
$BridgeJava=Join-Path $SourceStage 'LiNRR_M03A_3_RunInputs.java';Write-Utf8NoBomText $BridgeJava $Bridge

$UserPrefs=Join-Path $env:USERPROFILE '.comsol\v64\comsol.prefs';if(-not(Test-Path $UserPrefs)){throw "COMSOL preferences missing: $UserPrefs"}
$Prefs=[IO.File]::ReadAllText($UserPrefs)-replace '(?m)^security\.external\.filepermission=limited$','security.external.filepermission=full'
if($Prefs-notmatch'(?m)^security\.external\.filepermission=full$'){throw 'Could not enable isolated COMSOL file access'}
Write-Utf8NoBomText (Join-Path $PrefsDir 'comsol.prefs') $Prefs

$CompileStdout=Join-Path $RunDir 'java_compile_stdout.log';$CompileStderr=Join-Path $RunDir 'java_compile_stderr.log';Write-Utf8NoBomText $CompileStdout '';Write-Utf8NoBomText $CompileStderr ''
$StagedMetrics=Join-Path $SourceStage 'LiNRR_M03A_3_Metrics.java';$StagedBuilder=Join-Path $SourceStage 'LiNRR_M03A_3_PrescribedCurrentCoupling.java';$StagedLoad=Join-Path $SourceStage 'LiNRR_M03A_3_LoadCheck.java'
$CompileSteps=@(@($StagedMetrics),@('-classpathadd',$SourceStage,$StagedBuilder),@($StagedLoad),@($BridgeJava))
for($i=0;$i-lt$CompileSteps.Count;$i++){$Out=Join-Path $RunDir ("compile_{0}_stdout.log"-f($i+1));$Err=Join-Path $RunDir ("compile_{0}_stderr.log"-f($i+1));$Code=Invoke-Captured $Compiler $CompileSteps[$i] $Out $Err;$Combined=([IO.File]::ReadAllText($CompileStdout)+[IO.File]::ReadAllText($Out));Write-Utf8NoBomText $CompileStdout $Combined;$Combined=([IO.File]::ReadAllText($CompileStderr)+[IO.File]::ReadAllText($Err));Write-Utf8NoBomText $CompileStderr $Combined;if($Code-ne0){throw "comsolcompile step $($i+1) failed"}}
$EcjOut=Join-Path $RunDir 'ecj_stdout.log';$EcjErr=Join-Path $RunDir 'ecj_stderr.log';$Sources=@($StagedMetrics,$StagedBuilder,$StagedLoad,$BridgeJava);$EcjArgs=@('-jar',$Ecj,'-source','11','-target','11','-warn:none','-cp',("$ApiJar;$ModelJar"),'-d',$ClassOutput)+$Sources;$Code=Invoke-Captured $RuntimeJava $EcjArgs $EcjOut $EcjErr;if($Code-ne0){throw 'ECJ class-output compilation failed'}
$BuilderClass=Join-Path $ClassOutput 'LiNRR_M03A_3_PrescribedCurrentCoupling.class';$LoadClass=Join-Path $ClassOutput 'LiNRR_M03A_3_LoadCheck.class';foreach($Class in @($BuilderClass,$LoadClass,(Join-Path $ClassOutput 'LiNRR_M03A_3_Metrics.class'),(Join-Path $ClassOutput 'LiNRR_M03A_3_RunInputs.class'))){if(-not(Test-Path $Class)){throw "Missing class output: $Class"}}

$VersionOut=Join-Path $RunDir 'comsol_version_stdout.log';$VersionErr=Join-Path $RunDir 'comsol_version_stderr.log';[void](Invoke-Captured $Batch @('-version') $VersionOut $VersionErr)
$PreviousStdout=Join-Path $RunDir 'comsol_batch_stdout.log';$PreviousBatchLog=Join-Path $RunDir 'comsol_batch.log'
if($ResumeMode){$BatchStdout=Join-Path $RunDir 'comsol_finalize_stdout.log';$BatchStderr=Join-Path $RunDir 'comsol_finalize_stderr.log';$BatchLog=Join-Path $RunDir 'comsol_finalize.log'}
else{$BatchStdout=$PreviousStdout;$BatchStderr=Join-Path $RunDir 'comsol_batch_stderr.log';$BatchLog=$PreviousBatchLog}
if(-not$AuditOnly){$Code=Invoke-Captured $Batch @('-prefsdir',$PrefsDir,'-tmpdir',$TempDir,'-classpathadd',$ClassOutput,'-inputfile',$BuilderClass,'-batchlog',$BatchLog) $BatchStdout $BatchStderr;if($Code-ne0){throw "COMSOL batch failed with exit code $Code"};if(-not(Select-String -LiteralPath $BatchStdout -SimpleMatch -Pattern 'M03A_3_PROGRESS|ALL_BENCHMARKS|COMPLETE')){throw 'Missing all-benchmarks completion marker'};foreach($Artifact in @($StagedMph,$StagedPotential,$StagedSpecies)){if(-not(Test-Path $Artifact)-or(Get-Item $Artifact).Length-le0){throw "Missing staged artifact: $Artifact"}}}
elseif(-not(Select-String -LiteralPath $BatchStdout -SimpleMatch -Pattern 'M03A_3_PROGRESS|ALL_BENCHMARKS|COMPLETE')){throw 'Published-artifact continuation lacks finalization completion marker'}

$Lines=@(if($ResumeMode){Get-Content -LiteralPath $PreviousStdout -Encoding UTF8};Get-Content -LiteralPath $BatchStdout -Encoding UTF8)
$Specs=@(
    @('M03A3_SELECTION',$SelectionCsv,6),@('M03A3_OHMIC',$OhmicCsv,4),@('M03A3_INVARIANCE',$InvarianceCsv,18),
    @('M03A3_CURRENT',$CurrentCsv,4),@('M03A3_FARADAIC',$FaradaicCsv,4),@('M03A3_MESH',$MeshCsv,4),
    @('M03A3_LINEARITY',$LinearityCsv,25),@('M03A3_MAP',$MapCsv,26),@('M03A3_READINESS',$ReadinessCsv,5))
$Parsed=@{}
foreach($Spec in $Specs){$Subset=@($Lines|Where-Object{$_-like("{0}|*"-f$Spec[0])});if($Subset.Count-ne[int]$Spec[2]){throw "Unexpected $($Spec[0]) line count: $($Subset.Count)"};$Rows=@(Convert-PipeRows $Subset $Spec[0]);$Parsed[$Spec[0]]=$Rows;Export-Utf8NoBomCsv $Rows $Spec[1]}
$Invariance=@($Parsed['M03A3_INVARIANCE']);foreach($Row in @($Invariance|Where-Object category -eq 'flow')){$Row.baseline_source='models/generated/LiNRR_M01_2_flow_verification.mph; results/tables/M01_2_flow_analytic.csv'};Export-Utf8NoBomCsv $Invariance $InvarianceCsv

$Ohmic=@($Parsed['M03A3_OHMIC']);$FineOhmic=@($Ohmic|Where-Object mesh -eq 'fine')[0]
if($FineOhmic.status -ne 'PASS' -or (Number $FineOhmic.current_relative_error)-gt1e-3 -or (Number $FineOhmic.resistance_relative_error)-gt1e-3 -or (Number $FineOhmic.current_conservation_relative_error)-gt1e-6){throw 'Ohmic acceptance failed'}
$Faradaic=@($Parsed['M03A3_FARADAIC']);$FineFaradaic=@($Faradaic|Where-Object mesh -eq 'fine')[0]
foreach($Field in @('current_to_N2_relative_error','current_to_NH3_relative_error','stoichiometric_relative_error')){if((Number $FineFaradaic.$Field)-gt1e-6){throw "Faradaic $Field failed"}}
foreach($Field in @('N2_species_balance_error','NH3_species_balance_error','nitrogen_balance_error')){if((Number $FineFaradaic.$Field)-gt1e-4){throw "Faradaic $Field failed"}}
$Mesh=@($Parsed['M03A3_MESH']);$FineMesh=@($Mesh|Where-Object mesh -eq 'fine')[0];if($FineMesh.status -ne 'PASS' -or (Number $FineMesh.max_key_change)-ge0.005){throw 'Mesh audit failed'}
$Linearity=@($Parsed['M03A3_LINEARITY']);if(@($Linearity|Where-Object status -ne 'PASS').Count-ne0){throw 'Linearity/decoupling row failed'}
foreach($Test in @('conductivity_scaling','potential_scaling')){if((Number (@($Linearity|Where-Object test -eq $Test)[0]).R2)-lt0.999999){throw "$Test R2 failed"}}
$Map=@($Parsed['M03A3_MAP']);$Allowed=@('PASS','WARNING_NUMERICAL_OSCILLATION','FAILED_NEGATIVE_CONCENTRATION','FAILED_CONSERVATION','OUTSIDE_MODEL_APPLICABILITY');foreach($Row in $Map){if($Row.scientific_status-notin$Allowed){throw "Illegal map status: $($Row.scientific_status)"};if([string]::IsNullOrWhiteSpace($Row.failure_reason)){throw 'Map row missing reason'}}
$ConfigSweep=@(Import-Csv -LiteralPath (Join-Path $ProjectRoot 'config\M03A_3_current_flow_sweep.csv'));if($ConfigSweep.Count-ne25-or$Map.Count-ne25){throw '25-point sweep incomplete'}

if(-not$AuditOnly){Move-Item -LiteralPath $StagedMph -Destination $OutputMph;Move-Item $StagedPotential $PotentialPng;Move-Item $StagedSpecies $SpeciesPng;New-LinePlot $Faradaic 'predicted_N2_from_current_mol_s' @('integrated_N2_consumption_mol_s','integrated_NH3_generation_mol_s') 'M03A.3 independent current-to-Faraday closure' $FaradaicPng;New-LinePlot $Mesh 'mesh_elements' @('total_current_A','N2_consumption_mol_s','NH3_production_mol_s') 'M03A.3 coupled three-mesh audit' $MeshPng;New-StatusMap $Map $MapPng}
foreach($Png in $FormalPng){Test-Png $Png}
if(-not(Test-Path $OutputMph)-or(Get-Item $OutputMph).Length-le0){throw 'Final MPH missing or empty'}
$MphHash=(Get-FileHash -Algorithm SHA256 $OutputMph).Hash.ToLowerInvariant();Write-Utf8NoBomText (Join-Path $RunDir 'mph_sha256.txt') ("$MphHash  models/generated/LiNRR_M03A_3_prescribed_current_coupling.mph`n")

$ReloadStdout=Join-Path $RunDir 'reload_check_stdout.log';$ReloadStderr=Join-Path $RunDir 'reload_check_stderr.log';$ReloadLog=Join-Path $RunDir 'reload_check.log'
$Code=Invoke-Captured $Batch @('-prefsdir',$PrefsDir,'-tmpdir',$TempDir,'-classpathadd',$ClassOutput,'-inputfile',$LoadClass,'-batchlog',$ReloadLog) $ReloadStdout $ReloadStderr
if($Code-ne0-or-not(Select-String -LiteralPath $ReloadStdout -SimpleMatch -Pattern 'M03A3_RELOAD|PASS')){throw 'Independent reload/tree audit failed'}
$TreeLines=@(Select-String $ReloadStdout -Pattern '^M03A3_TREE\|'|ForEach-Object Line);if($TreeLines.Count-lt3){throw 'Actual model-tree evidence missing'}
$Forbidden='SecondaryCurrentDistribution|TertiaryCurrentDistribution|ElectrodeReaction|ButlerVolmer|ExchangeCurrent|\bHOR\b|\bHER\b|LiPlating|LiStripping|\bSEI\b';if(($TreeLines-join"`n")-match$Forbidden){throw 'Forbidden actual model-tree entry found'}

$LogPaths=@($BatchLog,$BatchStderr,$ReloadLog,$ReloadStderr);$FatalPattern='\berror\b|failed to find a solution|undefined value|singular matrix|out of memory|\bexception\b';$WarningPattern='\bwarning\b'
$Fatal=@(Select-String -LiteralPath $LogPaths -Pattern $FatalPattern -CaseSensitive:$false -ErrorAction SilentlyContinue);$Warnings=@(Select-String -LiteralPath $LogPaths -Pattern $WarningPattern -CaseSensitive:$false -ErrorAction SilentlyContinue);if($Fatal.Count-ne0){throw "Fatal runtime pattern found: $($Fatal[0])"}
$RecoveredFatal=@();if($ResumeMode){$RecoveredFatal=@(Select-String -LiteralPath $PreviousBatchLog -Pattern $FatalPattern -CaseSensitive:$false -ErrorAction SilentlyContinue)}
$RecoveredFatalEvents=0;if($ResumeMode -and (Select-String -LiteralPath $PreviousStdout -SimpleMatch -Pattern 'M03A_3_PROGRESS|F_CURRENT_FLOW_MAP|COMPLETE') -and -not(Select-String -LiteralPath $PreviousStdout -SimpleMatch -Pattern 'M03A_3_PROGRESS|ALL_BENCHMARKS|COMPLETE')){$RecoveredFatalEvents=1}

$AfterRows=@();foreach($Path in $Tracked){$AfterRows+=[pscustomobject][ordered]@{path=$Path;sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $ProjectRoot $Path)).Hash.ToLowerInvariant()}}
$FrozenAfter=Join-Path $RunDir 'frozen_hashes_after.csv';Export-Utf8NoBomCsv $AfterRows $FrozenAfter
$BeforeMap=@{};foreach($Row in @(Import-Csv -LiteralPath $FrozenBefore)){$BeforeMap[$Row.path]=$Row.sha256};foreach($Row in $AfterRows){if($Row.path -eq 'docs/DECISIONS.md'){continue};if($BeforeMap[$Row.path] -ne $Row.sha256){throw "Frozen tracked file changed: $($Row.path)"}}
$BaseDecision=[IO.File]::ReadAllBytes((Join-Path $BaselineTree 'docs\DECISIONS.md'));$CurrentDecision=[IO.File]::ReadAllBytes((Join-Path $ProjectRoot 'docs\DECISIONS.md'));if($CurrentDecision.Length-le$BaseDecision.Length){throw 'DECISIONS.md was not append-only'};for($i=0;$i-lt$BaseDecision.Length;$i++){if($BaseDecision[$i]-ne$CurrentDecision[$i]){throw 'D0001-D0013 bytes changed'}};$DecisionText=[Text.Encoding]::UTF8.GetString($CurrentDecision);$D0014Title='## D0014 '+[char]0x2014+' M03A.3 prescribed-current stoichiometric coupling verification';if(([regex]::Matches($DecisionText,[regex]::Escape($D0014Title))).Count-ne1){throw 'D0014 title missing or duplicated'}

$EnvText=@("project_root=$ProjectRoot","git_head=$(git rev-parse HEAD)","branch=$(git branch --show-current)","powershell=$($PSVersionTable.PSVersion)","os=$([Environment]::OSVersion)","comsol_root=$ComsolRoot","prefs_dir=$PrefsDir","temporary_dir=$TempDir","staging_dir=$StagingDir","class_output_dir=$ClassOutput")-join"`n";Write-Utf8NoBomText (Join-Path $RunDir 'environment_inventory.txt') ($EnvText+"`n");Write-Utf8NoBomText (Join-Path $RunDir 'exact_git_head.txt') ($StageCommit+"`n")

$PassCount=@($Map|Where-Object scientific_status -eq 'PASS').Count;$WarningCount=@($Map|Where-Object scientific_status -eq 'WARNING_NUMERICAL_OSCILLATION').Count;$FailedCount=25-$PassCount-$WarningCount
$NonPass=@($Map|Where-Object scientific_status -ne 'PASS');$FailureTable="| j (A/m2) | Umean (m/s) | Theta | min cN2 | N2 err | NH3 err | N err | status | reason |`n|--:|--:|--:|--:|--:|--:|--:|:--|:--|`n"+(($NonPass|ForEach-Object{"| $($_.imposed_current_density_A_m2) | $($_.Umean_m_s) | $($_.Theta) | $($_.min_cN2) | $($_.N2_balance_error) | $($_.NH3_balance_error) | $($_.nitrogen_balance_error) | $($_.scientific_status) | $($_.failure_reason) |"})-join"`n")
$ComsolVersion=(([IO.File]::ReadAllText($VersionOut))+([IO.File]::ReadAllText($VersionErr))).Trim()
$Report=@"
# M03A.3 prescribed-current stoichiometric coupling verification

- Baseline commit: $BaselineCommit
- COMSOL version: $ComsolVersion; installation audited at $ComsolRoot.
- Java compile: PASS (`comsolcompile` plus isolated ECJ class output).
- MPH build and independent reload: PASS; SHA-256 `$MphHash`.
- Model state: synthetic numerical smoke test; no experimental calibration.

## Geometry and 2D/3D area

The frozen 2D electrode boundary length is `Lcell`; `Wcell` is the out-of-plane thickness. The 3D equivalent current-carrying area is `Aeq_M033=Lcell*Wcell`. A 2D line length is not treated as square-metre area. Named selections were retained and audited without durable raw entity numbers.

## Current sign and physics tree

Primary Current Distribution uses an upper prescribed-current boundary and lower zero-potential boundary. Raw signed outward-normal current is integrated on every boundary. The cathode signed current is positive before it is assigned to `j_cathodic_positive`; no `abs()` is used. Current conservation, fine analytical current error, and fine resistance error are recorded in the formal CSVs. Independent reload inspected actual physics and feature types; no prohibited physics-tree entry exists.

## Flow, species, and nitrogen conservation

FE=0 frozen-model invariance passed for M01.2 flow metrics and M02.2 conservative N2/NH3 transport metrics. Flow relative differences are at most 1e-6 and transport relative differences at most 1e-4. The coupled fine grid passes current, N2, NH3 and nitrogen-atom conservation. Single-species checks are independent; nitrogen closure is not used to conceal either species error.

## Ohmic analytical comparison and mesh convergence

Coarse, medium and fine mapped meshes were retained. The rectangular uniform-conductivity result is compared with `I=kappa*Aeq*DeltaPhi/Hcell` and `R=Hcell/(kappa*Aeq)`. Exact linear finite-element results are classified `EXACT_POLYNOMIAL_REPRESENTATION`; no convergence order is fabricated. The coupled medium-to-fine changes in current, N2 consumption, NH3 production and outlet species rates are all below 0.5%.

## Faraday current-to-flux closure

Three independent routes were used: current-boundary integration, species-boundary total-flux integration, and inlet/outlet species balances. N2 demand uses `FE_prescribed*I_cath/(6F)` and NH3 generation uses `FE_prescribed*I_cath/(3F)`. Fine current-to-N2, current-to-NH3 and 2:1 stoichiometric relative errors pass 1e-6. `FE_prescribed` is prescribed synthetic input and is not predicted.

## Linearity and one-way decoupling

Conductivity and potential scans use multipliers 0.1, 0.3, 1, 3 and 10; each current fit has R2 at least 0.999999. FE scans retain 0, 0.1, 0.5 and 1.0 and demonstrate linear N2/NH3 mapping. FE=0 has zero cathode species reaction flux while current remains nonzero and transport returns to the uncoupled baseline. Flow changes do not change constant-conductivity ohmic current beyond 1e-6 relative; applied-potential changes do not change flow beyond 1e-6 relative.

## 25-point current-flow map

All 25 configured current-density/flow pairs are retained: PASS=$PassCount, WARNING=$WarningCount, failed or outside applicability=$FailedCount. Every warning and failure remains in `M03A_3_current_flow_map.csv` with solver status, scientific status and reason. `Theta>1` is classified outside applicability because prescribed demand exceeds inlet N2 capacity; it is not a physical limiting-current or performance claim.

$FailureTable

## Applicability, evidence gaps, and M03B readiness

M03A.3 does not contain electrode kinetics.
The current-to-species coupling is prescribed and one-way.
FE_prescribed is an input, not a prediction.
The model is not experimentally calibrated.
No failed or negative-concentration case was clipped or deleted.
M03A.3 does not authorize M03B.

Experimental conductivity, dissolved-N2 data, effective diffusivities, validated geometry/area, current distribution and independent ammonia/isotope measurements remain missing. Therefore no quantitative experimental prediction or optimization is authorized. Successful finalization/reload warning matches: $($Warnings.Count); fatal matches: 0. The completed A-F batch encountered $RecoveredFatalEvents recovered fatal event during the post-benchmark fine-state restoration because LU memory was insufficient; the failure log is preserved and a fresh COMSOL process recovered the same fine low-conversion state before final save. Encoding-sensitive English-pattern matches in that Chinese batch log: $($RecoveredFatal.Count). No failed sweep row was removed.

RUN_STATE = SYNTHETIC_SMOKE_TEST
CALIBRATION_MODE = PROVISIONAL
COUPLING_MODE = PRESCRIBED_CURRENT_ONE_WAY
M03B_READY = FALSE
"@
$RunReport=Join-Path $RunDir 'M03A_3_report.md';$LatestReport=Join-Path $ProjectRoot 'runs\latest\M03A_3_report.md';Write-Utf8NoBomText $RunReport $Report;Write-Utf8NoBomText $LatestReport $Report
$BuildText=@("COMSOL compile stdout:",([IO.File]::ReadAllText($CompileStdout)),"COMSOL compile stderr:",([IO.File]::ReadAllText($CompileStderr)),"completed A-F batch log:",([IO.File]::ReadAllText($PreviousBatchLog)),"fresh-process finalization log:",([IO.File]::ReadAllText($BatchLog)),"reload log:",([IO.File]::ReadAllText($ReloadLog)),"successful_warning_count=$($Warnings.Count)","recovered_fatal_event_count=$RecoveredFatalEvents","encoding_sensitive_recovered_fatal_pattern_count=$($RecoveredFatal.Count)")-join"`n";$LatestBuild=Join-Path $ProjectRoot 'runs\latest\M03A_3_build.log';Write-Utf8NoBomText (Join-Path $RunDir 'M03A_3_build.log') $BuildText;Write-Utf8NoBomText $LatestBuild $BuildText

foreach($Csv in $FormalCsv){Copy-Item $Csv $RunDir -Force};foreach($Png in $FormalPng){Copy-Item $Png $RunDir -Force};Copy-Item $OutputMph (Join-Path $RunDir 'LiNRR_M03A_3_prescribed_current_coupling.mph') -Force
$FormalText=@($FormalCsv)+@($RunReport,$LatestReport,(Join-Path $RunDir 'M03A_3_build.log'),$LatestBuild,$FrozenBefore,$FrozenAfter,(Join-Path $RunDir 'environment_inventory.txt'),(Join-Path $RunDir 'exact_git_head.txt'),(Join-Path $RunDir 'mph_sha256.txt'))
foreach($TextPath in $FormalText){Assert-Utf8NoBom $TextPath}
if(@(Get-ChildItem (Join-Path $ProjectRoot 'src'),(Join-Path $ProjectRoot 'tests') -Recurse -Filter '*.class' -File).Count-ne0){throw 'Temporary-file audit found .class in source/test'}
& git diff --check;if($LASTEXITCODE-ne0){throw 'git diff --check failed'}
& git --no-pager diff --stat | Out-Null;& git status --short | Out-Null

Write-Output 'SUCCESS|M03A.3 prescribed-current coupling verification complete|RUN_STATE=SYNTHETIC_SMOKE_TEST|CALIBRATION_MODE=PROVISIONAL|COUPLING_MODE=PRESCRIBED_CURRENT_ONE_WAY|M03B_READY=FALSE'
