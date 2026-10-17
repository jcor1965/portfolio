param(
    [ValidateSet('Validate', 'CoreTests', 'Compile', 'Full')][string]$Mode = 'CoreTests',
    [string]$JavaHome,
    [string]$MavenHome,
    [string]$LocalRepository
)
$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path -Parent $PSScriptRoot
$taskWorkspace = Split-Path -Parent $taskRepo
if (-not $JavaHome) { $JavaHome = Join-Path $taskWorkspace 'runtime' }
if (-not $MavenHome) { $MavenHome = Join-Path $taskWorkspace '.build-tools\apache-maven-3.9.11' }
if (-not $LocalRepository) { $LocalRepository = Join-Path $taskWorkspace '.build-tools\m2' }
$taskJava = Join-Path $JavaHome 'bin\java.exe'
$taskMaven = Join-Path $MavenHome 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $taskJava)) { throw 'JDK em falta. Indique -JavaHome com um JDK 21.' }
if (-not (Test-Path -LiteralPath $taskMaven)) { throw 'Maven em falta. Indique -MavenHome.' }
$taskOriginalJava = $env:JAVA_HOME
$taskOriginalMavenOptions = $env:MAVEN_OPTS
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $env:MAVEN_OPTS = ($taskOriginalMavenOptions + ' -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE').Trim()
    $taskArguments = @('--batch-mode', '--no-transfer-progress', '-f', (Join-Path $taskRepo 'portfolio-app\pom.xml'), "-Dmaven.repo.local=$LocalRepository")
    if ($Mode -eq 'CoreTests') {
        $taskArguments += @('-Plocal-dev', '-pl', ':portfolio-target-definition,:name.abuchen.portfolio.pdfbox1,:name.abuchen.portfolio.pdfbox3,:name.abuchen.portfolio,:name.abuchen.portfolio.junit,:name.abuchen.portfolio.tests', '-am', 'verify')
    } elseif ($Mode -eq 'Compile') {
        $taskArguments += @('-Plocal-dev', '-DskipTests', 'package')
    } elseif ($Mode -eq 'Validate') {
        $taskArguments += @('validate')
    } else {
        $taskArguments += @('clean', 'verify')
    }
    & $taskMaven @taskArguments
    if ($LASTEXITCODE -ne 0) { throw "Compilação falhou com código $LASTEXITCODE." }
} finally {
    $env:JAVA_HOME = $taskOriginalJava
    $env:MAVEN_OPTS = $taskOriginalMavenOptions
}
