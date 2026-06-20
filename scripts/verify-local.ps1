param(
    [switch]$SkipFrontend,
    [switch]$SkipBackendPackage,
    [switch]$IncludeFrontendBuild
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Frontend = Join-Path $Root "MindCrew-frontend"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

Write-Step "MindCrew backend tests"
Push-Location $Root
try {
    Invoke-Native mvn test

    if (-not $SkipBackendPackage) {
        Write-Step "MindCrew backend package"
        Invoke-Native mvn -DskipTests package
    }
}
finally {
    Pop-Location
}

if (-not $SkipFrontend) {
    Write-Step "MindCrew frontend type check"
    Push-Location $Frontend
    try {
        Invoke-Native npm run type-check
        if ($IncludeFrontendBuild) {
            Write-Step "MindCrew frontend production build"
            Invoke-Native npm run build-only
        }
        else {
            Write-Host "Frontend production build skipped in script. Run 'npm run build' in MindCrew-frontend or use -IncludeFrontendBuild." -ForegroundColor Yellow
        }
    }
    finally {
        Pop-Location
    }
}

Write-Step "Git working tree"
Push-Location $Root
try {
    Invoke-Native git status --short
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "MindCrew local verification finished." -ForegroundColor Green
