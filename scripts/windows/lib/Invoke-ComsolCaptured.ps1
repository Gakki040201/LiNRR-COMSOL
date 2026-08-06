Set-StrictMode -Version Latest

function ConvertTo-NativeQuotedArgument {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    if ($Value -notmatch '[\s"]') {
        return $Value
    }

    # Windows CommandLineToArgvW-compatible quoting. Backslashes preceding a
    # quote, and trailing backslashes inside quotes, must be doubled.
    $Builder = New-Object System.Text.StringBuilder
    [void]$Builder.Append('"')
    $Backslashes = 0
    foreach ($Character in $Value.ToCharArray()) {
        if ($Character -eq '\') {
            $Backslashes++
            continue
        }
        if ($Character -eq '"') {
            [void]$Builder.Append(('\' * (2 * $Backslashes + 1)))
            [void]$Builder.Append('"')
            $Backslashes = 0
            continue
        }
        if ($Backslashes -gt 0) {
            [void]$Builder.Append(('\' * $Backslashes))
            $Backslashes = 0
        }
        [void]$Builder.Append($Character)
    }
    if ($Backslashes -gt 0) {
        [void]$Builder.Append(('\' * (2 * $Backslashes)))
    }
    [void]$Builder.Append('"')
    return $Builder.ToString()
}

function Invoke-ComsolCaptured {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][string]$StdoutPath,
        [Parameter(Mandatory = $true)][string]$StderrPath,
        [Parameter(Mandatory = $true)][string]$MergedConsolePath
    )

    foreach ($PathValue in @($StdoutPath, $StderrPath, $MergedConsolePath)) {
        $Parent = Split-Path $PathValue -Parent
        if ($Parent) {
            [System.IO.Directory]::CreateDirectory($Parent) | Out-Null
        }
    }

    $StartInfo = New-Object System.Diagnostics.ProcessStartInfo
    $StartInfo.FileName = $Executable
    $StartInfo.Arguments = (($ArgumentList | ForEach-Object {
        ConvertTo-NativeQuotedArgument -Value $_
    }) -join ' ')
    $StartInfo.UseShellExecute = $false
    $StartInfo.RedirectStandardOutput = $true
    $StartInfo.RedirectStandardError = $true
    $StartInfo.CreateNoWindow = $true
    $StartInfo.WorkingDirectory = (Get-Location).Path

    $Process = New-Object System.Diagnostics.Process
    $Process.StartInfo = $StartInfo
    $StartedUtc = [DateTime]::UtcNow
    if (-not $Process.Start()) {
        throw "NATIVE_PROCESS_START_FAILED: $Executable"
    }

    # Start both asynchronous drains before waiting. This prevents either pipe
    # buffer from blocking COMSOL even when one stream is very verbose.
    $StdoutTask = $Process.StandardOutput.ReadToEndAsync()
    $StderrTask = $Process.StandardError.ReadToEndAsync()
    $Process.WaitForExit()
    $StdoutText = $StdoutTask.GetAwaiter().GetResult()
    $StderrText = $StderrTask.GetAwaiter().GetResult()
    $ExitCode = $Process.ExitCode
    $ProcessId = $Process.Id
    $FinishedUtc = [DateTime]::UtcNow
    $Process.Dispose()

    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($StdoutPath, $StdoutText, $Utf8NoBom)
    [System.IO.File]::WriteAllText($StderrPath, $StderrText, $Utf8NoBom)

    $Merged = @(
        'COMSOL_CAPTURE_METADATA_BEGIN'
        "EXECUTABLE=$Executable"
        "ARGUMENTS=$($StartInfo.Arguments)"
        "STARTED_UTC=$($StartedUtc.ToString('o'))"
        "FINISHED_UTC=$($FinishedUtc.ToString('o'))"
        "EXIT_CODE=$ExitCode"
        'COMSOL_CAPTURE_METADATA_END'
        'COMSOL_STDOUT_BEGIN'
        $StdoutText
        'COMSOL_STDOUT_END'
        'COMSOL_STDERR_BEGIN'
        $StderrText
        'COMSOL_STDERR_END'
    ) -join [Environment]::NewLine
    [System.IO.File]::WriteAllText($MergedConsolePath, $Merged, $Utf8NoBom)

    [pscustomobject]@{
        ExitCode = $ExitCode
        ProcessId = $ProcessId
        StartedUtc = $StartedUtc
        FinishedUtc = $FinishedUtc
        DurationSeconds = ($FinishedUtc - $StartedUtc).TotalSeconds
        StdoutPath = $StdoutPath
        StderrPath = $StderrPath
        MergedConsolePath = $MergedConsolePath
        StdoutBytes = (Get-Item -LiteralPath $StdoutPath).Length
        StderrBytes = (Get-Item -LiteralPath $StderrPath).Length
    }
}
