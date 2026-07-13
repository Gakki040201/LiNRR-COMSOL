param(
    [Parameter(Mandatory = $true)]
    [string]$InputMph,

    [Parameter(Mandatory = $true)]
    [string]$OutputMph,

    [string]$StudyTag = "std1"
)

$ErrorActionPreference = "Stop"
$ComsolBatch = "F:\COMSOL64\Multiphysics\bin\win64\comsolbatch.exe"
$Log = [System.IO.Path]::ChangeExtension($OutputMph, ".log")

& $ComsolBatch `
    -inputfile $InputMph `
    -outputfile $OutputMph `
    -study $StudyTag `
    -batchlog $Log

if ($LASTEXITCODE -ne 0) {
    throw "COMSOL batch failed. Read: $Log"
}

Write-Host "Completed: $OutputMph"
Write-Host "Log: $Log"
