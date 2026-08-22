[CmdletBinding()]
param(
    [string]$RepoRoot,
    [string]$OutputDir
)

$ErrorActionPreference = 'Stop'
if(-not $RepoRoot){$RepoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path}
Add-Type -AssemblyName System.Drawing

if(-not $OutputDir){$OutputDir=Join-Path $RepoRoot 'paper/v1/figures'}
$outDir = [IO.Path]::GetFullPath($OutputDir)
$dataDir = Join-Path $outDir 'source_data'
New-Item -ItemType Directory -Force -Path $outDir, $dataDir | Out-Null

$navy = [System.Drawing.Color]::FromArgb(28, 49, 78)
$blue = [System.Drawing.Color]::FromArgb(48, 112, 173)
$teal = [System.Drawing.Color]::FromArgb(42, 145, 135)
$orange = [System.Drawing.Color]::FromArgb(230, 126, 34)
$red = [System.Drawing.Color]::FromArgb(185, 60, 55)
$gray = [System.Drawing.Color]::FromArgb(105, 115, 125)
$light = [System.Drawing.Color]::FromArgb(242, 245, 248)
$white = [System.Drawing.Color]::White

function New-Canvas([string]$title, [string]$subtitle) {
    $bmp = [System.Drawing.Bitmap]::new(2400, 1500)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.Clear($white)
    $g.DrawString($title, [System.Drawing.Font]::new('Arial', 34, [System.Drawing.FontStyle]::Bold), [System.Drawing.SolidBrush]::new($navy), 70, 45)
    $g.DrawString($subtitle, [System.Drawing.Font]::new('Arial', 18), [System.Drawing.SolidBrush]::new($gray), 72, 100)
    return @{ Bitmap=$bmp; Graphics=$g }
}

function Add-Panel([System.Drawing.Graphics]$g, [int]$x, [int]$y, [int]$w, [int]$h, [string]$label, [string]$title) {
    $g.FillRectangle([System.Drawing.SolidBrush]::new($light), $x, $y, $w, $h)
    $g.DrawRectangle([System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(210,218,226),2), $x, $y, $w, $h)
    $g.DrawString($label, [System.Drawing.Font]::new('Arial', 22, [System.Drawing.FontStyle]::Bold), [System.Drawing.SolidBrush]::new($navy), $x+18, $y+14)
    $g.DrawString($title, [System.Drawing.Font]::new('Arial', 20, [System.Drawing.FontStyle]::Bold), [System.Drawing.SolidBrush]::new($navy), $x+65, $y+16)
}

function Add-ImageFit([System.Drawing.Graphics]$g, [string]$path, [int]$x, [int]$y, [int]$w, [int]$h) {
    $img = [System.Drawing.Image]::FromFile($path)
    try {
        $scale = [Math]::Min($w/$img.Width, $h/$img.Height)
        $dw=[int]($img.Width*$scale); $dh=[int]($img.Height*$scale)
        $g.DrawImage($img, $x+[int](($w-$dw)/2), $y+[int](($h-$dh)/2), $dw, $dh)
    } finally { $img.Dispose() }
}

function Add-BarChart([System.Drawing.Graphics]$g, $rows, [string]$labelField, [string]$valueField, [int]$x, [int]$y, [int]$w, [int]$h, [string]$unit) {
    $vals = @($rows | ForEach-Object { [double]($_.$valueField) })
    $max = ($vals | Measure-Object -Maximum).Maximum
    if ($max -le 0) { $max = 1 }
    $n = $rows.Count; $gap=18; $bw=[Math]::Max(18,[int](($w-120-$gap*($n-1))/$n))
    $axisY=$y+$h-80; $axisX=$x+90
    $g.DrawLine([System.Drawing.Pen]::new($gray,2),$axisX,$y+20,$axisX,$axisY)
    $g.DrawLine([System.Drawing.Pen]::new($gray,2),$axisX,$axisY,$x+$w-20,$axisY)
    for($i=0;$i -lt $n;$i++) {
        $r=$rows[$i]; $v=[double]$r.$valueField; $bh=[int](($axisY-$y-55)*$v/$max); $bx=$axisX+20+$i*($bw+$gap)
        $g.FillRectangle([System.Drawing.SolidBrush]::new($blue),$bx,$axisY-$bh,$bw,$bh)
        $g.DrawString(('{0:G4}' -f $v),[System.Drawing.Font]::new('Arial',12),[System.Drawing.SolidBrush]::new($navy),$bx-8,$axisY-$bh-28)
        $g.DrawString([string]$r.$labelField,[System.Drawing.Font]::new('Arial',12),[System.Drawing.SolidBrush]::new($navy),$bx,$axisY+10)
    }
    $g.DrawString($unit,[System.Drawing.Font]::new('Arial',13),[System.Drawing.SolidBrush]::new($gray),$x+5,$y+5)
}

function Add-HBarChart([System.Drawing.Graphics]$g, $rows, [string]$labelField, [string]$valueField, [int]$x, [int]$y, [int]$w, [int]$h, [string]$unit) {
    $vals=@($rows|ForEach-Object{[double]($_.$valueField)}); $max=($vals|Measure-Object -Maximum).Maximum; if($max-le 0){$max=1}
    $n=$rows.Count; $bh=[Math]::Max(20,[int](($h-80)/$n)-12); $labelW=[int]($w*0.42); $barW=$w-$labelW-100
    $g.DrawString($unit,[System.Drawing.Font]::new('Arial',13),[System.Drawing.SolidBrush]::new($gray),$x+$labelW,$y)
    for($i=0;$i-lt$n;$i++){ $r=$rows[$i];$v=[double]$r.$valueField;$yy=$y+45+$i*($bh+12);$len=[int]($barW*$v/$max)
      $g.DrawString([string]$r.$labelField,[System.Drawing.Font]::new('Arial',12),[System.Drawing.SolidBrush]::new($navy),$x,$yy+3)
      $g.FillRectangle([System.Drawing.SolidBrush]::new($blue),$x+$labelW,$yy,$len,$bh)
      $g.DrawString(('{0:G4}' -f $v),[System.Drawing.Font]::new('Arial',12),[System.Drawing.SolidBrush]::new($navy),$x+$labelW+$len+8,$yy+3)
    }
}

function Save-Figure($canvas, [string]$name) {
    $path = Join-Path $outDir $name
    $canvas.Bitmap.SetResolution(300,300)
    $canvas.Bitmap.Save($path,[System.Drawing.Imaging.ImageFormat]::Png)
    $canvas.Graphics.Dispose(); $canvas.Bitmap.Dispose()
}

# Figure 1: verification architecture, with all numerical labels recovered from CSV.
$m01 = Import-Csv (Join-Path $RepoRoot 'results/tables/M01_2_flow_analytic.csv') | Where-Object case -eq 'baseline_fine_fully_developed_outlet'
$m02 = Import-Csv (Join-Path $RepoRoot 'results/tables/M02_2_mms_convergence.csv') | Where-Object mesh -eq 'fine'
$m03 = Import-Csv (Join-Path $RepoRoot 'results/tables/M03A_3_current_conservation.csv') | Where-Object mesh -eq 'fine'
$a4 = Import-Csv (Join-Path $RepoRoot 'results/tables/M10A4_charge_conservation.csv') | Where-Object { $_.quantity -eq 'current_conservation_relative' -or $_.metric -eq 'current_conservation_relative' } | Select-Object -First 1
$areaAudit=Import-Csv (Join-Path $RepoRoot 'results/tables/M10A4_electrochemical_area_audit.csv')
$cathArea=$areaAudit|Where-Object {$_.selection_tag -eq 'sel_bnd_electrolyte_gde_top'}|Select-Object -First 1
$cathAreaLabel=('{0:G6} {1}' -f [double]$cathArea.area_mm2,'mm2')
@(
 [pscustomobject]@{stage='M01';metric='mass balance';value=$m01.mass_balance_error},
 [pscustomobject]@{stage='M02';metric='MMS L2 relative';value=$m02.L2_relative_error},
 [pscustomobject]@{stage='M03';metric='current closure';value=$m03.current_conservation_relative_error},
 [pscustomobject]@{stage='M10A4';metric='current closure';value=if($a4.value){$a4.value}else{$a4.relative_residual}}
) | Export-Csv (Join-Path $dataDir 'figure1_verification_metrics.csv') -NoTypeInformation
$c=New-Canvas 'Figure 1 | Verification-first architecture' 'Analytical/operator gates precede transfer to the real reactor geometry; this is numerical verification, not experimental validation.'
$g=$c.Graphics; $stages=@(
 @{n='M01';t='Flow operator';v=("mass residual`n{0:E2}" -f [double]$m01.mass_balance_error)},
 @{n='M02';t='Transport operator';v=("MMS L2 error`n{0:E2}" -f [double]$m02.L2_relative_error)},
 @{n='M03';t='Current/Faraday';v=("current residual`n{0:E2}" -f [double]$m03.current_conservation_relative_error)},
 @{n='M10';t='Real CAD transfer';v=("named selections`n{0} plane" -f $cathAreaLabel)},
 @{n='M10A3';t='Neutral transport';v="real flow + N2`nGENERIC DONOR"},
 @{n='M10A4';t='Ionic/current';v="conserved fields`nspatial diagnostics"} )
for($i=0;$i -lt $stages.Count;$i++){ $x=85+$i*382; $g.FillRectangle([System.Drawing.SolidBrush]::new($(if($i -lt 3){$blue}else{$teal})),$x,330,300,320); $s=$stages[$i]; $g.DrawString($s.n,[System.Drawing.Font]::new('Arial',28,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($white),$x+20,355); $g.DrawString($s.t,[System.Drawing.Font]::new('Arial',16,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($white),$x+20,420); $g.DrawString($s.v,[System.Drawing.Font]::new('Arial',14),[System.Drawing.SolidBrush]::new($white),$x+20,500); if($i -lt 5){$g.DrawLine([System.Drawing.Pen]::new($orange,8),$x+305,490,$x+375,490)} }
$g.DrawString('VERIFIED OPERATORS',[System.Drawing.Font]::new('Arial',24,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($blue),150,760)
$g.DrawString('TRANSFERRED WITHOUT TRANSFERRING OLD GEOMETRY OR PROVISIONAL VALUES',[System.Drawing.Font]::new('Arial',21,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($orange),650,760)
$g.DrawString('Scientific boundary: conservation and benchmark agreement establish numerical correctness; real-cell fields remain model predictions with stated calibration gaps.',[System.Drawing.Font]::new('Arial',20),[System.Drawing.SolidBrush]::new($navy),120,940)
Save-Figure $c 'Figure1_verification_first_architecture.png'

# Figure 2: immutable real-cell rendering plus area distinction.
$area=$areaAudit
$area | Export-Csv (Join-Path $dataDir 'figure2_area_audit.csv') -NoTypeInformation
$ssc=$area|Where-Object {$_.surface -eq 'SSC physical cut'}|Select-Object -First 1
if(-not $cathArea-or-not$ssc){throw 'Authoritative cathode/SSC area rows missing'}
$interfaceSide=[Math]::Sqrt([double]$cathArea.area_mm2)
if($ssc.physical_definition-notmatch'(?<w>[0-9.]+)x(?<h>[0-9.]+) mm') {throw 'SSC dimensions unavailable in accepted area audit'}
$sscW=[double]$Matches.w;$sscH=[double]$Matches.h;$sscComputed=$sscW*$sscH
$c=New-Canvas 'Figure 2 | Real-cell geometry and flow routes' 'Frozen CAD ancestry and named interfaces; no geometry modification or new solve.'; $g=$c.Graphics
Add-Panel $g 60 160 1460 1240 'a' 'Accepted real-cell geometry'
Add-ImageFit $g (Join-Path $RepoRoot 'evidence/M10A4/physical_cell.png') 100 235 1380 850
$g.DrawString('N2 route  |  H2 route  |  electrolyte route',[System.Drawing.Font]::new('Arial',20,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($teal),180,1120)
$g.DrawString('Source: accepted M10A4 result rendering',[System.Drawing.Font]::new('Arial',16),[System.Drawing.SolidBrush]::new($gray),180,1180)
Add-Panel $g 1560 160 780 1240 'b' 'Area authority'
$g.FillRectangle([System.Drawing.SolidBrush]::new($blue),1680,360,520,520)
$g.DrawString(('{0:G6} x {0:G6} mm' -f $interfaceSide),[System.Drawing.Font]::new('Arial',25,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($white),1830,560)
$g.DrawString($cathAreaLabel,[System.Drawing.Font]::new('Arial',32,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($white),1815,625)
$g.DrawRectangle([System.Drawing.Pen]::new($orange,10),1730,410,420,420)
$g.DrawString('Authoritative electrochemical interface',[System.Drawing.Font]::new('Arial',18,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($navy),1630,930)
$g.DrawString(('Distinct from {0:G6} x {1:G6} mm SSC cut ({2:G6} mm2)' -f $sscW,$sscH,$sscComputed),[System.Drawing.Font]::new('Arial',17),[System.Drawing.SolidBrush]::new($red),1630,1000)
$g.DrawString(("Named selection:`n"+$cathArea.selection_tag),[System.Drawing.Font]::new('Arial',16),[System.Drawing.SolidBrush]::new($gray),1630,1080)
Save-Figure $c 'Figure2_real_cell_geometry_flow.png'

# Figure 3: existing frozen concentration renderings only.
$c=New-Canvas 'Figure 3 | Neutral and effective ionic transport' 'Frozen fields. Li+/BF4- transport is provisional and calibration-required; donor is GENERIC DONOR.'; $g=$c.Graphics
$panels=@(
 @{l='a';t='Dissolved N2 availability';p='evidence/M10A3/n2_dissolved_real.png';n='M10A3 accepted field'},
 @{l='b';t='Li+ effective concentration';p='evidence/M10A4/li_concentration.png';n='M10A4B reduced electroneutral transport'},
 @{l='c';t='BF4- effective concentration';p='evidence/M10A4/li_concentration.png';n='cBF4 = cLi by accepted electroneutral reduction'},
 @{l='d';t='GENERIC DONOR availability';p='evidence/M10A3/proton_donor_real.png';n='Not a validated local ethanol field'} )
$panels|ForEach-Object{[pscustomobject]@{panel=$_.l;title=$_.t;source_path=$_.p;interpretation=$_.n}}|Export-Csv (Join-Path $dataDir 'figure3_transport_sources.csv') -NoTypeInformation
for($i=0;$i -lt 4;$i++){ $x=60+($i%2)*1170; $y=155+[Math]::Floor($i/2)*660; Add-Panel $g $x $y 1110 610 $panels[$i].l $panels[$i].t; Add-ImageFit $g (Join-Path $RepoRoot $panels[$i].p) ($x+40) ($y+80) 1030 420; $g.DrawString($panels[$i].n,[System.Drawing.Font]::new('Arial',15),[System.Drawing.SolidBrush]::new($gray),$x+55,$y+535) }
Save-Figure $c 'Figure3_neutral_ionic_transport.png'

# Figure 4: accepted maps and programmatic statistics.
$stats=Import-Csv (Join-Path $RepoRoot 'results/tables/M10A4_current_distribution_statistics.csv')
$wanted=@('j_surface_magnitude_min','j_surface_magnitude_P10','j_surface_magnitude_P50','j_surface_magnitude_mean','j_surface_magnitude_P90','j_surface_magnitude_max')
$chart=@($wanted | ForEach-Object { $q=$_; $r=$stats|Where-Object quantity -eq $q; [pscustomobject]@{label=($q -replace 'j_surface_magnitude_','');value=$r.value;unit=$r.unit} })
$chart | Export-Csv (Join-Path $dataDir 'figure4_current_statistics.csv') -NoTypeInformation
$c=New-Canvas 'Figure 4 | Current distribution and crowding' 'Area-weighted statistics on the authoritative cathode reaction plane.'; $g=$c.Graphics
Add-Panel $g 60 155 740 1220 'a' 'Electrolyte potential'; Add-ImageFit $g (Join-Path $RepoRoot 'evidence/M10A4/electrolyte_potential.png') 90 245 680 850
Add-Panel $g 830 155 740 1220 'b' 'Cathode current magnitude'; Add-ImageFit $g (Join-Path $RepoRoot 'evidence/M10A4/cathode_current_density.png') 860 245 680 850
Add-Panel $g 1600 155 740 1220 'c' 'Distribution statistics'; Add-BarChart $g $chart 'label' 'value' 1620 270 700 720 'A/m2'
$cv=($stats|Where-Object quantity -eq 'j_surface_magnitude_CV').value; $sd=($stats|Where-Object quantity -eq 'j_surface_magnitude_std').value
$g.DrawString(('CV = {0:G15}' -f [double]$cv),[System.Drawing.Font]::new('Arial',23,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($orange),1680,1080)
$g.DrawString(('SD = {0:G8} A/m2' -f [double]$sd),[System.Drawing.Font]::new('Arial',18),[System.Drawing.SolidBrush]::new($navy),1680,1145)
Save-Figure $c 'Figure4_current_distribution.png'

# Figure 5: Faraday-equivalent charge program.
$far=Import-Csv (Join-Path $RepoRoot 'results/tables/M10A4_li_faraday_ledger.csv')
$upperFraction=($far|Where-Object classification -eq 'NUMERICAL_UPPER_BOUND'|ForEach-Object{[double]$_.f_Li_current}|Select-Object -Unique)
if(@($upperFraction).Count-ne1){throw 'Accepted numerical-upper-bound fraction is ambiguous'}
$sensitivityFractions=@($far|Where-Object classification -eq 'CURRENT_PARTITION_SENSITIVITY'|ForEach-Object{[double]$_.f_Li_current}|Sort-Object -Unique)
$f1=@($far|Where-Object {[Math]::Abs([double]$_.f_Li_current-$upperFraction)-lt 1e-12}|Sort-Object {[double]$_.Q_C}|ForEach-Object{[pscustomobject]@{Q_label=('{0:G0}'-f[double]$_.Q_C);Q_C=$_.Q_C;mean_thickness_m=$_.mean_thickness_m;m_Li_equiv_kg=$_.m_Li_equiv_kg;CV_thickness=$_.CV_thickness;classification=$_.classification}})
$f1 | Export-Csv (Join-Path $dataDir 'figure5_li_equivalent_f1.csv') -NoTypeInformation
$sensLabel=($sensitivityFractions|ForEach-Object{'{0:F2}'-f$_})-join'/'
$maxQ=($f1|ForEach-Object{[double]$_.Q_C}|Measure-Object -Maximum).Maximum
$c=New-Canvas 'Figure 5 | Li-equivalent Faradaic charge program' (('LI-EQUIVALENT | fLi = {0:F2} is a NUMERICAL UPPER BOUND; {1} are current-partition sensitivities.' -f $upperFraction,$sensLabel)); $g=$c.Graphics
Add-Panel $g 60 155 1080 1220 'a' (('{0:G0} C spatial upper bound' -f $maxQ)); Add-ImageFit $g (Join-Path $RepoRoot 'evidence/M10A4/li_equivalent_thickness_297C_f1_upper_bound.png') 100 245 1000 860
Add-Panel $g 1180 155 1160 1220 'b' 'Charge-indexed mean thickness'; Add-BarChart $g $f1 'Q_label' 'mean_thickness_m' 1220 285 1070 700 'm'
$g.DrawString('Ledger closes against fLi x Q',[System.Drawing.Font]::new('Arial',21,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($teal),1340,1080)
$g.DrawString('Not retained metallic-Li thickness; no plating kinetics.',[System.Drawing.Font]::new('Arial',19),[System.Drawing.SolidBrush]::new($red),1280,1150)
Save-Figure $c 'Figure5_li_equivalent_charge_program.png'

# Figure 6: accepted co-limitation rendering and threshold sensitivity.
$col=Import-Csv (Join-Path $RepoRoot 'results/tables/M10A4_spatial_colimitation.csv')
$cats=@($col|Where-Object {$_.diagnostic -eq 'N2_LI_DONOR_CURRENT_COLIMITATION'})
$cats | Export-Csv (Join-Path $dataDir 'figure6_colimitation_thresholds.csv') -NoTypeInformation
$thresholds=@($cats|ForEach-Object{[double]$_.threshold_quantile}|Sort-Object -Unique)
$primaryQ=@($cats|Where-Object robustness_status -eq 'PRIMARY'|ForEach-Object{[double]$_.threshold_quantile}|Select-Object -Unique)
if($primaryQ.Count-ne1){throw 'Accepted primary co-limitation threshold is ambiguous'}
$primary=@($cats|Where-Object {[Math]::Abs([double]$_.threshold_quantile-$primaryQ[0])-lt 1e-12}|ForEach-Object{$short=@{CURRENT_RICH_N2_POOR='CURR-RICH / N2-POOR';N2_RICH_CURRENT_POOR='N2-RICH / CURR-POOR';LI_LIMITED='LI-LIMITED';DONOR_LIMITED='DONOR-LIMITED';BALANCED='BALANCED';MULTI_LIMITED='MULTI-LIMITED';MIXED_UNCLASSIFIED='MIXED / UNCLASSIFIED'}[$_.category];[pscustomobject]@{display=$short;category=$_.category;area_fraction=$_.area_fraction}})
$thresholdLabel=($thresholds|ForEach-Object{'{0:F2}'-f$_})-join'/'
$c=New-Canvas 'Figure 6 | Spatial co-limitation diagnostic' (('Frozen N2 x Li+ x GENERIC DONOR x current fields; thresholds {0}. Not an NH3-rate or FE map.' -f $thresholdLabel)); $g=$c.Graphics
Add-Panel $g 60 155 1320 1220 'a' 'Accepted regime map'; Add-ImageFit $g (Join-Path $RepoRoot 'evidence/M10A4/spatial_colimitation.png') 100 245 1240 850
Add-Panel $g 1420 155 920 1220 'b' (('Primary q = {0:F2} area fractions' -f $primaryQ[0])); Add-HBarChart $g $primary 'display' 'area_fraction' 1460 280 830 700 'area fraction'
$g.DrawString('Category fractions close to unity at each threshold.',[System.Drawing.Font]::new('Arial',18),[System.Drawing.SolidBrush]::new($teal),1510,1080)
$g.DrawString('Diagnostic only | no mechanistic proof',[System.Drawing.Font]::new('Arial',19,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($red),1540,1140)
Save-Figure $c 'Figure6_spatial_colimitation.png'

# Figure 7: existing mesh evidence and identifiability gaps.
$mesh=Import-Csv (Join-Path $RepoRoot 'results/tables/M10A4_mesh_convergence.csv')
$meshSummary=$mesh|Where-Object {$_.mesh -eq 'comparison' -and $_.metric -eq 'MESH_MAX_KEY_DIFFERENCE'}|Select-Object -First 1
if(-not$meshSummary-or$meshSummary.notes-notmatch'limiting_metric=(?<metric>[^;]+)'){throw 'Accepted mesh summary/limiting metric unavailable'}
$meshLimitMetric=$Matches.metric
$keys=@($mesh|Where-Object {$_.mesh -eq 'coarse' -and $_.relative_difference -and $_.metric -ne 'MESH_MAX_KEY_DIFFERENCE'}|Sort-Object {[double]$_.relative_difference} -Descending|Select-Object -First 10|ForEach-Object{[pscustomobject]@{display=if($_.metric.Length-gt28){$_.metric.Substring(0,28)}else{$_.metric};metric=$_.metric;relative_difference=$_.relative_difference;status=$_.status}})
$keys | Export-Csv (Join-Path $dataDir 'figure7_mesh_key_differences.csv') -NoTypeInformation
$c=New-Canvas 'Figure 7 | Robustness and identifiability' 'Existing evidence only. Medium mesh remains authoritative; coarse comparison is PASS_WITH_LIMITATION.'; $g=$c.Graphics
Add-Panel $g 60 155 1420 1220 'a' 'Largest coarse-to-medium metric differences'; Add-HBarChart $g $keys 'display' 'relative_difference' 100 250 1340 750 'relative difference'
$g.DrawString(('MESH_MAX_KEY_DIFFERENCE = {0:G15}' -f [double]$meshSummary.value),[System.Drawing.Font]::new('Arial',21,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($orange),180,1100)
$g.DrawString(('limiting metric: '+$meshLimitMetric),[System.Drawing.Font]::new('Arial',19),[System.Drawing.SolidBrush]::new($navy),180,1160)
Add-Panel $g 1520 155 820 1220 'b' 'Calibration boundary'
$paperProv=Import-Csv (Join-Path $RepoRoot 'paper/v1/PARAMETER_PROVENANCE.csv')
$cal=Import-Csv (Join-Path $RepoRoot 'results/tables/M10A4_calibration_required.csv')
$wantedSymbols=@('kappa_M10A4_nominal','D_salt_a4b_eff','t_plus_a4b','j_app_sensitivity','D_donor','f_Li_current')
$items=@($wantedSymbols|ForEach-Object{$s=$_;$r=$paperProv|Where-Object symbol -eq $s|Select-Object -First 1;if($r){('{0} - {1}'-f$r.symbol,$r.calibration_status)}})
if($items.Count-lt4){throw 'Calibration provenance is incomplete'}
for($i=0;$i -lt $items.Count;$i++){ $yy=300+$i*145; $g.FillEllipse([System.Drawing.SolidBrush]::new($(if($i -eq 0 -or $i -eq 3){$orange}else{$red})),1590,$yy,32,32); $g.DrawString($items[$i],[System.Drawing.Font]::new('Arial',18,[System.Drawing.FontStyle]::Bold),[System.Drawing.SolidBrush]::new($navy),1650,$yy-2) }
Save-Figure $c 'Figure7_robustness_identifiability.png'

'PAPER_V1_FIGURES_GENERATED=7'
