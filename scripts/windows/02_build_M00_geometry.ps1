$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ComsolBin = "F:\COMSOL64\Multiphysics\bin\win64"
$Compiler = Join-Path $ComsolBin "comsolcompile.exe"
$Batch = Join-Path $ComsolBin "comsolbatch.exe"

$Java = Join-Path $ProjectRoot "src\java\LiNRR_M00_Geometry.java"
$Output = Join-Path $ProjectRoot "models\generated\LiNRR_M00_geometry.mph"
$Log = Join-Path $ProjectRoot "runs\latest\M00_build.log"

New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $Log) | Out-Null

# Remove stale compile outputs so success cannot be confused with an older build.
Get-ChildItem -Path $ProjectRoot -Recurse -Filter "LiNRR_M00_Geometry.class" `
    -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

Push-Location $ProjectRoot
try {
    Write-Host "Compiling: $Java"
    & $Compiler $Java
    if ($LASTEXITCODE -ne 0) {
        throw "COMSOL Java compilation failed with exit code $LASTEXITCODE."
    }

    $ClassFile = Get-ChildItem -Path $ProjectRoot -Recurse `
        -Filter "LiNRR_M00_Geometry.class" -File |
        Select-Object -First 1

    if ($null -eq $ClassFile) {
        throw "Compilation returned success, but LiNRR_M00_Geometry.class was not found."
    }

    Write-Host "Compiled class: $($ClassFile.FullName)"
    Write-Host "Running COMSOL batch..."
    & $Batch -inputfile $ClassFile.FullName -batchlog $Log

    if ($LASTEXITCODE -ne 0) {
        throw "COMSOL batch failed with exit code $LASTEXITCODE. See $Log"
    }

    if (-not (Test-Path $Output)) {
        throw "Expected MPH output was not created: $Output"
    }

    $FatalPatterns = "Exception|ERROR:|Failed to find a solution|Undefined value|Singular matrix|Out of memory"
    $Hits = Select-String -Path $Log -Pattern $FatalPatterns -CaseSensitive:$false `
        -ErrorAction SilentlyContinue

    if ($Hits) {
        Write-Warning "Potential fatal messages were found in the COMSOL log:"
        $Hits | ForEach-Object { Write-Warning $_.Line }
    }

    Write-Host ""
    Write-Host "[SUCCESS] Generated editable model:"
    Write-Host $Output
    Write-Host "Log:"
    Write-Host $Log
}
finally {
    Pop-Location
}
