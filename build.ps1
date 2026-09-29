# Builds better-audio-clarity-<version>.jar without Gradle/Loom.
#
# Minecraft 26.x ships unobfuscated and Fabric runs it under Mojang's own names, so the mod
# compiles straight against the vanilla client jar plus the libraries the Modrinth App already
# downloaded for Fabric on that version (Fabric Loader, Mixin, LWJGL, Gson, SLF4J...).
# Launch any Fabric <version> instance once so they are there.
#
#   powershell -ExecutionPolicy Bypass -File build.ps1 [-McVersion 26.3] [-Loader 0.19.5]

param(
    [string]$McVersion = "26.3",
    [string]$Loader = "0.19.5",
    [string]$Meta = "$env:APPDATA\ModrinthApp\meta",
    [string]$Jdk = "C:\Program Files\Java\jdk-25.0.3"
)
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
if (Test-Path $build) { [IO.Directory]::Delete($build, $true) }
New-Item -ItemType Directory $classes | Out-Null

$clientJar = "$Meta\versions\$McVersion\$McVersion.jar"
$profile = "$Meta\versions\$McVersion-$Loader\$McVersion-$Loader.json"
foreach ($f in $clientJar, $profile) {
    if (-not (Test-Path $f)) { throw "$f not found - launch a Fabric $McVersion instance once" }
}

# Every non-native library of the Fabric profile, as a path in the launcher's library cache.
$libs = (Get-Content $profile -Raw | ConvertFrom-Json).libraries | ForEach-Object {
    $parts = $_.name -split ':'
    if ($parts.Count -ne 3) { return }   # natives have a classifier
    $group, $artifact, $version = $parts
    $jar = "$Meta\libraries\$($group -replace '\.', '\')\$artifact\$version\$artifact-$version.jar"
    if (Test-Path $jar) { $jar }
}
if (-not ($libs | Where-Object { $_ -match 'fabric-loader' })) { throw "Fabric Loader jar not found in $Meta\libraries" }

$cp = @($clientJar) + $libs
$sources = (Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter '*.java').FullName

# An @argfile: the classpath is long and has spaces, which Windows PowerShell 5.1 mangles.
$quote = { param($s) '"' + ($s -replace '\\', '/') + '"' }
$argLines = @('--release 25', '-encoding UTF-8', '-proc:none', '-Xlint:deprecation', ('-d ' + (& $quote $classes)),
    ('-cp ' + (& $quote ($cp -join ';')))) + ($sources | ForEach-Object { & $quote $_ })
$argFile = Join-Path $build 'javac.args'
[IO.File]::WriteAllLines($argFile, $argLines)
& (Join-Path $Jdk 'bin\javac.exe') "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item -Recurse (Join-Path $root 'src\main\resources\*') $classes
$modVersion = (Get-Content (Join-Path $root 'src\main\resources\fabric.mod.json') -Raw | ConvertFrom-Json).version
$out = Join-Path $root "better-audio-clarity-$modVersion.jar"
if (Test-Path $out) { Remove-Item $out }
& (Join-Path $Jdk 'bin\jar.exe') --create --file $out -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Host "Built $out"
