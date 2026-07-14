param([string]$PostprocessRun = '')

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ComsolRoot = "F:\COMSOL64\Multiphysics"
$ComsolBin = Join-Path $ComsolRoot "bin\win64"
$Compiler = Join-Path $ComsolBin "comsolcompile.exe"
$Batch = Join-Path $ComsolBin "comsolbatch.exe"
$RuntimeJava = Join-Path $ComsolRoot "java\win64\jre\bin\java.exe"
$Ecj = Join-Path $ComsolRoot "plugins\org.eclipse.jdt.core.compiler.batch_3.42.0.v20250526-2018.jar"
$ApiJar = Join-Path $ComsolRoot "plugins\com.comsol.api_1.0.0.jar"
$ModelJar = Join-Path $ComsolRoot "plugins\com.comsol.model_1.0.0.jar"
$Java = Join-Path $ProjectRoot "src\java\LiNRR_M02_1_TransportAudit.java"
$Output = Join-Path $ProjectRoot "models\generated\LiNRR_M02_1_transport_audit.mph"
$FluxCsv = Join-Path $ProjectRoot "results\tables\M02_1_flux_audit.csv"
$StabCsv = Join-Path $ProjectRoot "results\tables\M02_1_stabilization_audit.csv"
$LatestDir = Join-Path $ProjectRoot "runs\latest"
$LatestLog = Join-Path $LatestDir "M02_1_build.log"
$LatestReport = Join-Path $LatestDir "M02_1_report.md"

$RunStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$RunDir = Join-Path $ProjectRoot ("runs\{0}_M02_1" -f $RunStamp)
if($PostprocessRun){
    $RunDir=(Resolve-Path -LiteralPath $PostprocessRun).Path
    $RunStamp=((Split-Path $RunDir -Leaf) -replace '_M02_1$','')
}
$CompileLog = Join-Path $RunDir "M02_1_comsolcompile.log"
$EcjLog = Join-Path $RunDir "M02_1_ecj_compile.log"
$RunLog = Join-Path $RunDir "M02_1_build.log"
$Stdout = Join-Path $RunDir "M02_1_stdout.log"
$Stderr = Join-Path $RunDir "M02_1_stderr.log"
$RunReport = Join-Path $RunDir "M02_1_report.md"
$BuildStarted = Get-Date

@((Split-Path $Output),(Split-Path $FluxCsv),$LatestDir,$RunDir) |
    ForEach-Object { New-Item -ItemType Directory -Force -Path $_ | Out-Null }
foreach ($Required in @($Compiler,$Batch,$RuntimeJava,$Ecj,$ApiJar,$ModelJar,$Java)) {
    if (-not (Test-Path -LiteralPath $Required -PathType Leaf)) {
        throw "Missing required M02.1 build input: $Required"
    }
}

if(-not $PostprocessRun){
    # Remove only M02.1-derived files. M00, M01, and M02 baselines are untouched.
    Get-ChildItem -LiteralPath (Split-Path $Java) -Filter 'LiNRR_M02_1_TransportAudit*.class' -File |
        Remove-Item -Force
    foreach ($Stale in @($Output,$FluxCsv,$StabCsv,$LatestLog,$LatestReport,
        (Join-Path (Split-Path $Java) 'LiNRR_M02_1_TransportAudit.class.status'),
        (Join-Path (Split-Path $Java) 'LiNRR_M02_1_TransportAudit_M021.mph'))) {
        Remove-Item -LiteralPath $Stale -Force -ErrorAction SilentlyContinue
    }
}

function Parse-StructuredRows([string]$Prefix,[string[]]$Lines) {
    $Matched = @($Lines | Where-Object { $_ -like "$Prefix|*" })
    if ($Matched.Count -lt 2) { throw "No structured rows for $Prefix" }
    $Headers = @($Matched[0] -split '\|' | Select-Object -Skip 1)
    $Rows = foreach ($Line in $Matched | Select-Object -Skip 1) {
        $Fields = @($Line -split '\|' | Select-Object -Skip 1)
        if ($Fields.Count -ne $Headers.Count) {
            throw "Malformed $Prefix row: expected $($Headers.Count), got $($Fields.Count): $Line"
        }
        $Object = [ordered]@{}
        for ($i=0; $i -lt $Headers.Count; $i++) { $Object[$Headers[$i]]=$Fields[$i] }
        [PSCustomObject]$Object
    }
    return @($Rows)
}

function Write-Utf8NoBomCsv([object[]]$Rows,[string]$Path) {
    $Rows | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
    $Text=[IO.File]::ReadAllText($Path,[Text.Encoding]::UTF8) -replace "`r`n","`n"
    [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))
}

function Number($Value) {
    return [double]::Parse([string]$Value,[Globalization.CultureInfo]::InvariantCulture)
}

Push-Location $ProjectRoot
try {
    if(-not $PostprocessRun){
        & $Compiler $Java 2>&1 | Tee-Object -FilePath $CompileLog
        if ($LASTEXITCODE -ne 0) { throw "M02.1 comsolcompile failed: $CompileLog" }

    # comsolcompile validates the COMSOL API source but emits only the primary
    # class for a single-file source containing private helper classes. Compile
    # the same source with COMSOL's bundled ECJ to emit those helper bytecodes.
        $ClassPath = "$ApiJar;$ModelJar"
        & $RuntimeJava -jar $Ecj -source 11 -target 11 -warn:none -cp $ClassPath `
            -d (Split-Path $Java) $Java 2>&1 | Tee-Object -FilePath $EcjLog
        if ($LASTEXITCODE -ne 0) { throw "M02.1 ECJ bytecode compilation failed: $EcjLog" }

    $Class = Join-Path (Split-Path $Java) 'LiNRR_M02_1_TransportAudit.class'
    $Nested = @(
        (Join-Path (Split-Path $Java) 'LiNRR_M02_1_TransportAudit$Config.class'),
        (Join-Path (Split-Path $Java) 'LiNRR_M02_1_TransportAudit$InletMode.class'),
        (Join-Path (Split-Path $Java) 'LiNRR_M02_1_TransportAudit$Metrics.class')
    )
        foreach ($Bytecode in @($Class)+$Nested) {
            if (-not (Test-Path -LiteralPath $Bytecode -PathType Leaf)) { throw "Missing bytecode: $Bytecode" }
            if ((Get-Item -LiteralPath $Bytecode).LastWriteTime -lt $BuildStarted.AddSeconds(-2)) {
                throw "Stale bytecode: $Bytecode"
            }
        }

        $Process = Start-Process -FilePath $Batch -ArgumentList @(
            '-inputfile',$Class,'-batchlog',$RunLog
        ) -RedirectStandardOutput $Stdout -RedirectStandardError $Stderr `
          -WindowStyle Hidden -Wait -PassThru
        if ($Process.ExitCode -ne 0) { throw "M02.1 COMSOL batch exit code $($Process.ExitCode)." }
    }
    if (-not (Test-Path -LiteralPath $RunLog -PathType Leaf)) { throw "M02.1 batch log missing." }

    $FatalPattern='error|failed|undefined|singular|out of memory|Exception'
    $FatalHits=@(Select-String -LiteralPath $RunLog -Pattern $FatalPattern -CaseSensitive:$false)
    if($FatalHits.Count -gt 0){
        $FatalHits | ForEach-Object { Write-Host ('FATAL LOG MATCH: '+$_.Line) }
        throw "Fatal pattern found in M02.1 batch log."
    }
    $WarningHits=@(Select-String -LiteralPath $RunLog -Pattern 'warning' -CaseSensitive:$false)

    $Lines=@(Get-Content -LiteralPath $Stdout -Encoding UTF8)
    $LoadRecoveryUsed=$false
    $ReloadStdout=Join-Path $RunDir 'M02_1_reload_stdout.log'
    if(-not ($Lines | Where-Object {$_ -like 'M021_LOAD|PASS|*'}) -and
       (Test-Path -LiteralPath $ReloadStdout -PathType Leaf)){
        $Lines += @(Get-Content -LiteralPath $ReloadStdout -Encoding UTF8)
        $LoadRecoveryUsed=$true
    }
    $FluxLines=@($Lines | Where-Object {$_ -like 'M021_FLUX|*'})
    $StabLines=@($Lines | Where-Object {$_ -like 'M021_STAB|*'})
    $ScanLines=@($Lines | Where-Object {$_ -like 'M021_SCAN|*'})
    $SelectedLines=@($Lines | Where-Object {$_ -like 'M021_SELECTED|*'})
    $LoadLines=@($Lines | Where-Object {$_ -like 'M021_LOAD|PASS|*'})
    if($FluxLines.Count -ne 4){throw "Expected flux header + 3 boundary rows; got $($FluxLines.Count)."}
    if($StabLines.Count -ne 11){throw "Expected stabilization header + 10 rows; got $($StabLines.Count)."}
    if($ScanLines.Count -ne 26){throw "Expected scan header + 25 rows; got $($ScanLines.Count)."}
    if($SelectedLines.Count -ne 1){throw "Expected one selected configuration line."}
    if($LoadLines.Count -ne 1){throw "Generated MPH did not pass in-process reload check."}

    $Flux=@(Parse-StructuredRows 'M021_FLUX' $Lines)
    $Stab=@(Parse-StructuredRows 'M021_STAB' $Lines)
    $Scan=@(Parse-StructuredRows 'M021_SCAN' $Lines)
    $Selected=($SelectedLines[0] -split '\|')[1]

    # One flux-audit CSV contains both the three boundary comparisons and the
    # complete selected-configuration scan, distinguished by row_type.
    $Headers=New-Object System.Collections.Generic.List[string]
    foreach($Name in @($Flux[0].PSObject.Properties.Name)+@($Scan[0].PSObject.Properties.Name)){
        if(-not $Headers.Contains($Name)){$Headers.Add($Name)}
    }
    $FluxAuditRows=@()
    foreach($Pair in @(@('boundary',$Flux),@('scan',$Scan))){
        $RowType=$Pair[0]; $Rows=@($Pair[1])
        foreach($Row in $Rows){
            $Out=[ordered]@{row_type=$RowType}
            foreach($Header in $Headers){
                $Property=$Row.PSObject.Properties[$Header]
                $Out[$Header]=if($null -eq $Property){''}else{$Property.Value}
            }
            $FluxAuditRows += [PSCustomObject]$Out
        }
    }
    Write-Utf8NoBomCsv $FluxAuditRows $FluxCsv
    Write-Utf8NoBomCsv $Stab $StabCsv

    foreach($Expected in @($Output,$FluxCsv,$StabCsv)){
        if(-not(Test-Path -LiteralPath $Expected -PathType Leaf)){throw "Missing M02.1 output: $Expected"}
        $Item=Get-Item -LiteralPath $Expected
        if($Item.Length -le 0 -or ((-not $PostprocessRun) -and
            $Item.LastWriteTime -lt $BuildStarted.AddSeconds(-2))){
            throw "Empty or stale M02.1 output: $Expected"
        }
    }

    $Base=@($Scan | Where-Object {
        [math]::Abs((Number $_.Qliq_cm3_min)-1.0)-lt 1e-12 -and
        [math]::Abs((Number $_.kN2_m_s)-1e-5)-lt 1e-15
    })
    if($Base.Count -ne 1){throw "Selected base scan row not unique."}
    $Base=$Base[0]
    foreach($Metric in @('N2_species_error','NH3_species_error','N_boundary_error','N_overall_error')){
        if((Number $Base.$Metric)-gt 1e-4){throw "Base $Metric exceeds 1e-4: $($Base.$Metric)"}
    }
    if((Number $Base.min_cN2)-lt -5e-6 -or (Number $Base.min_cNH3)-lt -5e-6){
        throw "Selected base has FAILED-level negative concentration."
    }
    if($Base.classification -like 'FAILED*'){throw "Selected base classification is $($Base.classification)."}

    $Failed=@($Scan | Where-Object {$_.classification -like 'FAILED*'})
    $Warning=@($Scan | Where-Object {$_.classification -eq 'WARNING_UNDERSHOOT'})
    $Pass=@($Scan | Where-Object {$_.classification -eq 'PASS_CONSERVATIVE'})
    if($Failed.Count+$Warning.Count+$Pass.Count -ne 25){throw "Unclassified scan row detected."}

    $BoundaryTable=($Flux | ForEach-Object {
        "| $($_.config) | $($_.N2_species_error) | $($_.NH3_species_error) | $($_.NH3_in_total) | $($_.NH3_inlet_backdiff_out) | $($_.min_cNH3) |"
    }) -join [Environment]::NewLine
    $StabTable=($Stab | ForEach-Object {
        "| $($_.config) | $($_.convective_form) | $($_.streamline)/$($_.crosswind) | $($_.order) | $($_.nx)x$($_.ny) | $($_.dof) | $($_.solve_s) | $($_.min_cN2) | $($_.min_cNH3) | $($_.N2_species_error) | $($_.NH3_species_error) | $($_.N_boundary_error) | $($_.N_overall_error) | $($_.N2_conversion) | $($_.NH3_generation) | $($_.physical_change_gt_1pct) | $($_.classification) |"
    }) -join [Environment]::NewLine
    $ScanTable=($Scan | ForEach-Object {
        "| $($_.Qliq_cm3_min) | $($_.kN2_m_s) | $($_.N2_species_error) | $($_.NH3_species_error) | $($_.NH3_inlet_backdiff_out) | $($_.N2_conversion) | $($_.NH3_generation) | $($_.min_cathode_cN2) | $($_.min_cNH3) | $($_.reaction_to_limiting_flux) | $($_.classification) | $($_.reason) |"
    }) -join [Environment]::NewLine
    $FailedText=if($Failed.Count -eq 0){'- None.'}else{($Failed | ForEach-Object {
        "- Qliq=$($_.Qliq_cm3_min) cm^3/min, kN2=$($_.kN2_m_s) m/s: $($_.classification); $($_.reason)."
    }) -join [Environment]::NewLine}

    $Report=@"
# M02.1 Transport Conservation and Positivity Audit

- Run: ``$RunStamp``
- Status: **PASS for M02.1 numerical acceptance; PROVISIONAL and not experimentally validated**
- Selected configuration: ``$Selected``.
- Editable MPH reload: PASS.
- M00, M01, and M02 baselines were not overwritten. No electrochemistry was added.

## Root finding and total-flux definition

The original M02 postprocessing already used COMSOL's generated ``tds.ntflux_cN2`` and ``tds.ntflux_cNH3`` variables, confirmed from the model expression tree and COMSOL 6.4 application-library models. These are outward-normal total molar flux variables. The original report's inlet/outlet molar flow expressions were therefore not convection-only, but the original TDS advanced setting used the nonconservative convective form; that is why individual species errors remained about 0.38% and 1.58% even while nitrogen-element cancellation looked good.

The audited physical flux is

``N_i = u*c_i - D_i*grad(c_i)``.

The boundary normal convention is outward from the liquid domain. A positive boundary integral is outward; reported inlet rates are the negative of that integral and are positive into the reactor. The CSV separately retains total, advective, and diffusive contributions. Cathode N2 consumption and NH3 generation are positive source/sink magnitudes. The all-boundary sums include inlet, outlet, cathode, and anode wall, so an omitted boundary cannot silently close the balance.

## Boundary-condition audit

| Combination | N2 species error | NH3 species error | signed NH3 inlet total (mol/s) | NH3 inlet reverse diffusion outward (mol/s) | min NH3 (mol/m^3) |
|:--|--:|--:|--:|--:|--:|
$BoundaryTable

- A is the current fixed ``Concentration`` inlet plus ``Outflow`` and retains the original nonconservative convection form. Fixing ``cNH3=0`` does not remove diffusion: the audit quantifies outward NH3 back-diffusion instead of ignoring it.
- B uses COMSOL's actual ``Inflow`` API with ``BoundaryConditionType=FluxDanckwerts``, conservative convection, and ``Outflow``. It prescribes the incoming feed flux while permitting the boundary concentration and its balancing diffusion to respond to the interior solution.
- C uses COMSOL ``Inflow`` with ``ConcentrationConstraint`` plus ``Outflow`` and conservative convection. It is conservative in equation form but retains a concentration-type inlet and its reverse-diffusion sensitivity.
- Selected for a continuous-flow liquid reactor: Danckwerts inflow plus Outflow with conservative convection. It avoids treating a zero NH3 feed concentration as zero total NH3 inlet flux.

For the accepted base, NH3 reverse diffusion at the inlet is ``$($Base.NH3_inlet_backdiff_out) mol/s`` and the signed total NH3 inlet contribution is ``$($Base.NH3_in_total) mol/s``.

## Three distinct conservation statements

- Element conservation checks total nitrogen atoms and can pass through cancellation between N2 and NH3 equation errors. Base boundary-stoichiometric error: ``$($Base.N_boundary_error)``; base overall nitrogen error: ``$($Base.N_overall_error)``.
- Species conservation closes each equation separately using total flux. Base N2 error: ``$($Base.N2_species_error)``; base NH3 error: ``$($Base.NH3_species_error)``.
- Apparent convection-only conservation omits diffusive inlet/outlet terms. It is not an acceptance metric, especially for NH3 at a nominally zero-concentration feed boundary. The separate convection and diffusion columns remain in ``M02_1_flux_audit.csv``.

## Stabilization, order, and mesh audit

WARNING means a negative value exists but ``abs(cmin)/cN2_in <= 1e-6``. FAILED means that ratio is larger or the negative value distorts reaction direction, flux, or conversion. The absolute base threshold is ``5e-6 mol/m^3`` from ``cN2_in=5 mol/m^3``. This strict threshold is retained; it was not relaxed to match a solver tolerance.

| Configuration | convection | streamline/crosswind | order | mesh | DOF | solve s | min N2 | min NH3 | N2 err | NH3 err | N boundary err | N overall err | conversion | NH3 generation | >1% change | class |
|:--|:--|:--|--:|:--|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|:--|:--|
$StabTable

The no-stabilization conservative solution closes species balances but has large high-Peclet oscillations. Crosswind suppresses the oscillation but its coarse-grid physical-flux audit does not meet 1e-4. The selected second-order 350x200 grid is the first tested configuration that meets both species balances and the FAILED-level positivity threshold. No ``max(c,0)`` clipping, post-solve zeroing, or unreported artificial diffusivity was used. ``kN2`` continuation and direct target solves are both retained in the table.

## Selected base result

- N2 species total-flux error: ``$($Base.N2_species_error)``.
- NH3 species total-flux error: ``$($Base.NH3_species_error)``.
- Nitrogen boundary/overall errors: ``$($Base.N_boundary_error)`` / ``$($Base.N_overall_error)``.
- min N2 / min NH3: ``$($Base.min_cN2)`` / ``$($Base.min_cNH3) mol/m^3``.
- N2 conversion: ``$($Base.N2_conversion)``; NH3 generation: ``$($Base.NH3_generation) mol/s``.
- Cathode average/minimum N2: ``$($Base.avg_cathode_cN2)`` / ``$($Base.min_cathode_cN2) mol/m^3``.
- Operational limiting mass-transfer flux: ``$($Base.limiting_mass_transfer_flux) mol/(m^2*s)``; imposed reaction/limiting ratio: ``$($Base.reaction_to_limiting_flux)``.

The limiting flux is an operational extrapolation ``j_lim=j_rxn*cN2_in/(cN2_in-cN2_cath,avg)``, not an independently validated mass-transfer correlation.

## 25-point Qliq-kN2 scan

Counts: PASS_CONSERVATIVE=$($Pass.Count), WARNING_UNDERSHOOT=$($Warning.Count), failed=$($Failed.Count). No combination was deleted.

| Qliq | kN2 | N2 err | NH3 err | NH3 inlet backdiff | conversion | NH3 generation | min cathode N2 | min NH3 | reaction/limit | class | reason |
|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|:--|:--|
$ScanTable

### Failed combinations and physical-limit interpretation

$FailedText

Negative values are classified first as numerical evidence. A case with near-zero cathode N2 or a high reaction/limiting ratio is labeled near depletion plus numerical oscillation, not declared a real transport limit. Mass-balance failures indicate that mesh/stabilization or boundary recovery remains inadequate. Solver failures indicate that a steady solution was not obtained and may require transient analysis; none is silently interpreted as physical limiting current.

## Acceptance and integrity

- Java ``comsolcompile``: PASS; bundled ECJ emitted helper bytecodes from the same source.
- COMSOL solve batch completed all solves and saved the MPH; MPH reload batch: PASS.
- Base N2/NH3 species balances <=1e-4: PASS.
- Two nitrogen-element balances <=1e-4: PASS.
- NH3 inlet reverse diffusion quantified: PASS.
- Base has no FAILED-level negative concentration: PASS ($($Base.classification)).
- All 25 scan rows classified: PASS.
- No concentration hard clipping: PASS.
- No electrochemical physics: PASS.
- Experimental validation claimed: NO.

## Log review, commands, and uncertainty

- Fatal search (``error|failed|undefined|singular|out of memory|Exception``): 0 batch-log matches.
- Warning matches: $($WarningHits.Count); warnings are preserved in the full log.
- The first in-process reload attempt was blocked by COMSOL's batch security policy when Java requested ``user.dir`` through ``File.getAbsolutePath``. The solve and MPH save had completed. The source now uses the explicit project path, and the separate corrected reload batch passed. Recovery mode used: ``$LoadRecoveryUsed``. The original error remains preserved in the solve log.
- Commands: ``comsolcompile src/java/LiNRR_M02_1_TransportAudit.java``; COMSOL-bundled ECJ compilation of the same source; solve ``comsolbatch``; corrected load-only ``comsolbatch``; postprocessing with ``-PostprocessRun``.
- Full run: ``runs/$($RunStamp)_M02_1``; latest log: ``runs/latest/M02_1_build.log``.
- Changed/generated scope: M02.1 Java, Windows build script, M02.1 MPH, two audit CSVs, this report/log, and the decision log. Experimental raw data were not modified.
- Remaining scientific uncertainty: all density, viscosity, diffusivity, solubility, and kinetic inputs remain provisional; the wall law is phenomenological with no calibration range; the operational limiting-flux metric is not independently validated; steady-state validity at aggressive scan points is unresolved.

M03 current distribution remains blocked until these provisional transport parameters receive traceable calibration and the failed scan region is resolved or explicitly excluded on physical grounds.
"@
    Set-Content -LiteralPath $LatestReport -Value $Report -Encoding UTF8
    Copy-Item -LiteralPath $LatestReport -Destination $RunReport -Force
    Copy-Item -LiteralPath $RunLog -Destination $LatestLog -Force
    Write-Host "[SUCCESS] M02.1 accepted. PASS=$($Pass.Count), WARNING=$($Warning.Count), FAILED=$($Failed.Count)."
}
finally {
    Get-ChildItem -LiteralPath (Split-Path $Java) -Filter 'LiNRR_M02_1_TransportAudit*.class' -File |
        Remove-Item -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path (Split-Path $Java) 'LiNRR_M02_1_TransportAudit.class.status'),
        (Join-Path (Split-Path $Java) 'LiNRR_M02_1_TransportAudit_M021.mph') -Force -ErrorAction SilentlyContinue
    Pop-Location
}
