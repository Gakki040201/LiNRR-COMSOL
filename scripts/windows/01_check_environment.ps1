$ErrorActionPreference = "Stop"

$ComsolBin = "F:\COMSOL64\Multiphysics\bin\win64"
$Required = @(
    "comsol.exe",
    "comsolbatch.exe",
    "comsolcompile.exe",
    "comsolmphserver.exe"
)

Write-Host "COMSOL binary directory: $ComsolBin"

if (-not (Test-Path $ComsolBin)) {
    throw "COMSOL directory does not exist: $ComsolBin"
}

$Missing = @()
foreach ($Name in $Required) {
    $Path = Join-Path $ComsolBin $Name
    if (Test-Path $Path) {
        Write-Host "[OK] $Path"
    } else {
        Write-Host "[MISSING] $Path"
        $Missing += $Path
    }
}

Write-Host ""
Write-Host "Codex:"
$Codex = Get-Command codex -ErrorAction SilentlyContinue
if ($null -eq $Codex) {
    Write-Host "[NOT FOUND] codex is not on PATH."
} else {
    Write-Host "[OK] $($Codex.Source)"
    & codex --version
}

Write-Host ""
Write-Host "Git:"
$Git = Get-Command git -ErrorAction SilentlyContinue
if ($null -eq $Git) {
    Write-Host "[NOT FOUND] git is not on PATH."
} else {
    Write-Host "[OK] $($Git.Source)"
    & git --version
}

if ($Missing.Count -gt 0) {
    throw "One or more COMSOL command-line executables are missing."
}

Write-Host ""
Write-Host "Environment check completed."
