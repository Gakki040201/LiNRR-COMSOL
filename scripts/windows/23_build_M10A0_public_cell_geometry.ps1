$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ComsolBin = "F:\COMSOL64\Multiphysics\bin\win64"
$Compiler = Join-Path $ComsolBin "comsolcompile.exe"
$Batch = Join-Path $ComsolBin "comsolbatch.exe"

$Java = Join-Path $ProjectRoot "src\java\LiNRR_M10A0_PublicCell_Geometry.java"
$Output = Join-Path $ProjectRoot "models\generated\LiNRR_M10A0_public_cell_geometry.mph"
$RunStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$RunDir = Join-Path $ProjectRoot "runs\M10A0\$RunStamp"
$Log = Join-Path $RunDir "M10A0_public_cell_geometry.log"

if (-not (Test-Path -LiteralPath $Compiler -PathType Leaf)) {
    throw "COMSOL_COMPILER_MISSING: $Compiler"
}
if (-not (Test-Path -LiteralPath $Batch -PathType Leaf)) {
    throw "COMSOL_BATCH_MISSING: $Batch"
}
if (-not (Test-Path -LiteralPath $Java -PathType Leaf)) {
    throw "JAVA_SOURCE_MISSING: $Java"
}

New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

# Do not allow an old MPH to masquerade as a successful build.
if (Test-Path -LiteralPath $Output -PathType Leaf) {
    $Backup = Join-Path $RunDir "preexisting_LiNRR_M10A0_public_cell_geometry.mph"
    Move-Item -LiteralPath $Output -Destination $Backup
    Write-Host "Moved pre-existing MPH to: $Backup"
}

# Remove stale compile output for this exact class only.
Get-ChildItem -Path $ProjectRoot -Recurse -Filter "LiNRR_M10A0_PublicCell_Geometry.class" `
    -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

Push-Location $ProjectRoot
try {
    Write-Host "========== M10A0 PUBLIC CELL GEOMETRY =========="
    Write-Host "Project root: $ProjectRoot"
    Write-Host "Java source : $Java"
    Write-Host "Output MPH  : $Output"
    Write-Host "Run log     : $Log"
    Write-Host ""

    Write-Host "[1/3] Compiling COMSOL Java source..."
    & $Compiler $Java
    if ($LASTEXITCODE -ne 0) {
        throw "COMSOL_JAVA_COMPILE_FAILED: exit=$LASTEXITCODE"
    }

    $ClassFile = Get-ChildItem -Path $ProjectRoot -Recurse `
        -Filter "LiNRR_M10A0_PublicCell_Geometry.class" -File |
        Select-Object -First 1

    if ($null -eq $ClassFile) {
        throw "COMPILED_CLASS_NOT_FOUND"
    }
    Write-Host "Compiled class: $($ClassFile.FullName)"

    Write-Host "[2/3] Building editable MPH in COMSOL batch..."
    & $Batch -inputfile $ClassFile.FullName -batchlog $Log
    if ($LASTEXITCODE -ne 0) {
        throw "COMSOL_BATCH_FAILED: exit=$LASTEXITCODE log=$Log"
    }

    if (-not (Test-Path -LiteralPath $Output -PathType Leaf)) {
        throw "EXPECTED_MPH_NOT_CREATED: $Output"
    }

    $FatalPatterns = "Exception|ERROR:|Failed to find|Undefined value|Singular matrix|Out of memory|Failed to build geometry"
    $FatalHits = Select-String -Path $Log -Pattern $FatalPatterns -CaseSensitive:$false `
        -ErrorAction SilentlyContinue
    if ($FatalHits) {
        Write-Host "FATAL_LOG_HITS_BEGIN"
        $FatalHits | ForEach-Object { Write-Host $_.Line }
        Write-Host "FATAL_LOG_HITS_END"
        throw "POTENTIAL_FATAL_MESSAGES_IN_COMSOL_LOG"
    }

    Write-Host "[3/3] Verifying output..."
    $Item = Get-Item -LiteralPath $Output
    $Sha = (Get-FileHash -LiteralPath $Output -Algorithm SHA256).Hash

    Write-Host ""
    Write-Host "========== BUILD RESULT =========="
    Write-Host "M10A0_GEOMETRY_BUILD=PASS"
    Write-Host "MPH_PATH=$($Item.FullName)"
    Write-Host "MPH_BYTES=$($Item.Length)"
    Write-Host "MPH_SHA256=$Sha"
    Write-Host "LOG_PATH=$Log"
    Write-Host ""
    Write-Host "Open in COMSOL GUI:"
    Write-Host $Item.FullName
    Write-Host ""
    Write-Host "Expected components:"
    Write-Host "  comp_assembly = full public-reference assembly visualization"
    Write-Host "  comp_active   = physics-ready active-zone geometry"
}
finally {
    Pop-Location
}
