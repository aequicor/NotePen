<#
    PostToolUse hook — when Claude edits source code, surface the RELATED test cases for that
    area so it runs them before finishing (testing/TEST-CASE-STANDARD.md § 7).

    Reads the PostToolUse payload (JSON on stdin), resolves the edited file's repo-relative path,
    looks it up in testing/area-map.md (the committed code→case map), and — on a match — injects a
    standing instruction listing the related RC-*/AV-* cases and how to run them.

    Table-driven on purpose: the mapping lives in the committed catalog, not in this script, so the
    two never drift. Wired from .claude/settings.local.json (survives AI-Kit regeneration of the
    committed settings.json). Fails open: any error → exit 0, no output, never blocks a tool call.
#>
$ErrorActionPreference = 'Stop'
try {
    $raw = [Console]::In.ReadToEnd()
    if (-not $raw) { exit 0 }
    $data = $raw | ConvertFrom-Json

    # --- the edited file path (Edit/Write/MultiEdit put it in tool_input.file_path) ---
    $fp = $null
    if ($data.tool_input) {
        if ($data.tool_input.file_path) { $fp = [string]$data.tool_input.file_path }
        elseif ($data.tool_input.path) { $fp = [string]$data.tool_input.path }
    }
    if (-not $fp -and $data.tool_response.filePath) { $fp = [string]$data.tool_response.filePath }
    if (-not $fp) { exit 0 }

    # only react to Kotlin source — not docs, the catalogs themselves, or test files
    if ($fp -notmatch '\.kt$') { exit 0 }
    if ($fp -match '[\\/](testing|vault)[\\/]') { exit 0 }
    if ($fp -match 'Test\.kt$') { exit 0 }

    # repo root = two levels up from this script (tools/uitest/ -> repo root)
    $root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $mapFile = Join-Path $root 'testing\area-map.md'
    if (-not (Test-Path $mapFile)) { exit 0 }

    # repo-relative, forward-slashed
    $rel = $fp -replace '\\', '/'
    $rootFwd = ($root -replace '\\', '/').TrimEnd('/')
    if ($rel.ToLower().StartsWith($rootFwd.ToLower() + '/')) { $rel = $rel.Substring($rootFwd.Length + 1) }

    # parse the "## Map" table: | Path / glob | Area | Related cases | Run |
    $rows = @()
    $inMap = $false
    foreach ($line in (Get-Content -LiteralPath $mapFile)) {
        if ($line -match '^\s*##\s') { $inMap = ($line -match '(?i)##\s*Map') ; continue }
        if (-not $inMap) { continue }
        if ($line -notmatch '^\s*\|') { continue }
        $cells = ($line -split '\|') | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
        if ($cells.Count -lt 3) { continue }
        if ($cells[0] -match '^[-: ]+$' -or $cells[0] -match '(?i)^path') { continue }  # separator / header
        $glob = $cells[0] -replace '`', ''
        $prefix = ($glob -replace '\*+.*$', '').TrimEnd('/')   # glob -> path prefix
        $rows += [pscustomobject]@{
            prefix = $prefix
            area   = if ($cells.Count -ge 2) { $cells[1] } else { '' }
            cases  = if ($cells.Count -ge 3) { $cells[2] } else { '' }
            run    = if ($cells.Count -ge 4) { $cells[3] } else { '' }
        }
    }
    if ($rows.Count -eq 0) { exit 0 }

    # longest-prefix match against the edited path
    $relLower = $rel.ToLower()
    $match = $rows |
        Where-Object { $_.prefix -ne '' -and $relLower.StartsWith($_.prefix.ToLower()) } |
        Sort-Object { $_.prefix.Length } -Descending |
        Select-Object -First 1
    if (-not $match) { exit 0 }

    $context = @"
You just edited ``$rel`` -- area **$($match.area)**. Per testing/TEST-CASE-STANDARD.md (section 7),
before you finish this task run the RELATED test cases for this area, do not skip them:

- Related cases: $($match.cases)
- Run: $($match.run)

Steps: (1) open testing/regression-cases.md and read the $($match.area) section + the testing/area-map.md
row for the exact RC-*/AV-* ids; (2) run the Tier-1 cases (the gradle command above) and report
pass/fail; (3) if the change is visual/gesture and an AV-* exists, run it via the tools/uitest
harness (or state why Tier-2 was deferred -- it is advisory); (4) if you introduced behavior no case
covers, add a case per the standard and a row to testing/area-map.md. This is a standing instruction,
not a suggestion.
"@

    $payload = @{
        hookSpecificOutput = @{
            hookEventName     = 'PostToolUse'
            additionalContext = $context
        }
    } | ConvertTo-Json -Depth 5 -Compress
    [Console]::Out.Write($payload)
    exit 0
} catch {
    exit 0
}
