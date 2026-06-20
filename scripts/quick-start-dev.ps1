param(
    [switch]$InfraOnly
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot

Write-Host "Starting MindCrew local infrastructure..." -ForegroundColor Cyan
Push-Location $Root
try {
    docker compose -f docker-compose.dev.yml up -d

    if (-not $InfraOnly) {
        Write-Host ""
        Write-Host "Infrastructure is starting. Next commands:" -ForegroundColor Green
        Write-Host "  Backend : mvn spring-boot:run"
        Write-Host "  Frontend: cd MindCrew-frontend; npm run dev"
    }
}
finally {
    Pop-Location
}
