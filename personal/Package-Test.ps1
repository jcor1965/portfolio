param(
    [Parameter(Mandatory=$true)][string]$OfficialZip,
    [Parameter(Mandatory=$true)][string]$OfficialSha256,
    [string]$OutputDir
)
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path -Parent $PSScriptRoot
$taskWorkspace = Split-Path -Parent $taskRepo
if (-not $OutputDir) {
    $OutputDir = Join-Path $taskWorkspace ('test-builds\PP-Pessoal-FIFO-0.89.0-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
if (Test-Path -LiteralPath $OutputDir) { throw 'A pasta de destino já existe. Escolha uma pasta nova.' }
if ((Get-FileHash -LiteralPath $OfficialZip -Algorithm SHA256).Hash -ine $OfficialSha256) {
    throw 'O ZIP oficial não corresponde ao SHA-256 esperado.'
}
$taskBundles = @('name.abuchen.portfolio', 'name.abuchen.portfolio.ui', 'name.abuchen.portfolio.bootstrap')
foreach ($taskBundle in $taskBundles) {
    if (-not (Test-Path -LiteralPath (Join-Path $taskRepo "$taskBundle\target\$taskBundle-0.89.0.jar"))) {
        throw "Módulo compilado em falta: $taskBundle. Compile primeiro a versão pessoal."
    }
}
Expand-Archive -LiteralPath $OfficialZip -DestinationPath $OutputDir
$taskProduct = Join-Path $OutputDir 'portfolio'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskOAuthName = 'name/abuchen/portfolio/oauth/impl/config.json'
$taskOfficialCore = [IO.Compression.ZipFile]::OpenRead((Join-Path $taskProduct 'plugins\name.abuchen.portfolio_0.89.0.jar'))
try {
    $taskOAuthEntry = $taskOfficialCore.GetEntry($taskOAuthName)
    if ($null -eq $taskOAuthEntry) { throw 'Configuração OAuth pública em falta no pacote oficial.' }
    $taskMemory = [IO.MemoryStream]::new()
    $taskInput = $taskOAuthEntry.Open()
    try { $taskInput.CopyTo($taskMemory); $taskOAuthBytes = $taskMemory.ToArray() }
    finally { $taskInput.Dispose(); $taskMemory.Dispose() }
} finally { $taskOfficialCore.Dispose() }
foreach ($taskBundle in $taskBundles) {
    $taskDestination = Join-Path $taskProduct "plugins\${taskBundle}_0.89.0.jar"
    if (-not (Test-Path -LiteralPath $taskDestination)) { throw "Módulo oficial em falta: $taskBundle" }
    Copy-Item -LiteralPath (Join-Path $taskRepo "$taskBundle\target\$taskBundle-0.89.0.jar") -Destination $taskDestination
}
# Preserve the public client configuration already distributed in the verified
# official ZIP. It is not a user token and is never copied into the Git checkout.
$taskPersonalCore = [IO.Compression.ZipFile]::Open((Join-Path $taskProduct 'plugins\name.abuchen.portfolio_0.89.0.jar'), [IO.Compression.ZipArchiveMode]::Update)
try {
    $taskExisting = $taskPersonalCore.GetEntry($taskOAuthName)
    if ($null -ne $taskExisting) { $taskExisting.Delete() }
    $taskOutput = $taskPersonalCore.CreateEntry($taskOAuthName).Open()
    try { $taskOutput.Write($taskOAuthBytes, 0, $taskOAuthBytes.Length) }
    finally { $taskOutput.Dispose() }
} finally { $taskPersonalCore.Dispose() }
Copy-Item -LiteralPath (Join-Path $taskWorkspace 'runtime') -Destination (Join-Path $taskProduct 'runtime') -Recurse
$taskIni = Join-Path $taskProduct 'PortfolioPerformance.ini'
[IO.File]::AppendAllText($taskIni, "`r`n-Dname.abuchen.portfolio.in-app-update=disable`r`n", [Text.UTF8Encoding]::new($false))
$taskLauncher = @'
@echo off
setlocal
set "PP_PERSONAL_ROOT=%~dp0"
if not exist "%PP_PERSONAL_ROOT%test-state\home" mkdir "%PP_PERSONAL_ROOT%test-state\home"
start "" /D "%PP_PERSONAL_ROOT%" "%PP_PERSONAL_ROOT%PortfolioPerformance.exe" -clean -vm "%PP_PERSONAL_ROOT%runtime\bin\javaw.exe" -data "%PP_PERSONAL_ROOT%test-state\workspace" -user "%PP_PERSONAL_ROOT%test-state\user" -vmargs -XX:+IgnoreUnrecognizedVMOptions -Dosgi.requiredJavaVersion=21 -Djdk.xml.elementAttributeLimit=0 --add-modules=ALL-SYSTEM -XX:+UseG1GC -XX:+UseStringDeduplication -Xms512m -Dname.abuchen.portfolio.in-app-update=disable -Duser.home="%PP_PERSONAL_ROOT%test-state\home"
endlocal
'@
[IO.File]::WriteAllText((Join-Path $taskProduct 'Abrir-PP-Pessoal.cmd'), $taskLauncher, [Text.Encoding]::ASCII)
$taskReadme = @'
PORTFOLIO PERFORMANCE PESSOAL — TESTE FIFO

Abrir pelo ficheiro Abrir-PP-Pessoal.cmd.
Esta distribuição é uma versão pessoal de teste, sem assinatura dos módulos adaptados.
Preferências e workspace ficam separados em test-state.

1. Abrir uma CÓPIA da carteira. O isolamento não torna as carteiras só de leitura.
2. Relatórios > Performance > Negociações > ícone Exportar.
3. Escolher Exportar relatório FIFO (Excel), conta Trading 212 e ano.
4. Escolher o destino do ficheiro XLSX.

Ficheiro > Importar > Trading 212 (CSV) preserva o importador do complemento.
Mantém os limites: EUR, sem câmbio/encargos adicionais não nulos;
deduplicação do extrator apenas dentro do mesmo lote de importação.
Rever os avisos do assistente antes de aceitar movimentos.

O relatório é por conta de títulos e requer o histórico completo da instituição.
Inclui resumo por venda, detalhe FIFO e validação interna. Não calcula imposto.
País da fonte é lido dos atributos personalizados da carteira.
Transferências, entregas, encargos e câmbio são rejeitados nesta primeira versão.

Atualizações oficiais automáticas estão desativadas nesta distribuição.
As novas versões devem ser integradas no fork, compiladas e novamente testadas.
Nenhuma carteira ou relatório pessoal está incluído neste pacote.
'@
[IO.File]::WriteAllText((Join-Path $taskProduct 'LEIA-ME-FIFO.txt'), $taskReadme, [Text.UTF8Encoding]::new($false))
$taskCommit = (& git -C $taskRepo rev-parse HEAD).Trim()
$taskManifest = @("Base oficial: 0.89.0", "Commit base do checkout: $taskCommit", 'Adaptações locais presentes no momento do empacotamento:', (& git -C $taskRepo status --short))
foreach ($taskBundle in $taskBundles) {
    $taskFile = Join-Path $taskProduct "plugins\${taskBundle}_0.89.0.jar"
    $taskManifest += (Get-FileHash -LiteralPath $taskFile -Algorithm SHA256).Hash + "  plugins/${taskBundle}_0.89.0.jar"
}
[IO.File]::WriteAllLines((Join-Path $taskProduct 'BUILD-FIFO.txt'), [string[]]$taskManifest, [Text.UTF8Encoding]::new($false))
Write-Output $taskProduct
