$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk) { $sdk = "C:\Users\Abhinav Kumar\AppData\Local\Android\Sdk" }

$platform = Join-Path $sdk "platforms\android-35\android.jar"
$buildTools = Join-Path $sdk "build-tools\35.0.0"
if (-not (Test-Path $platform)) {
    $platform = Get-ChildItem (Join-Path $sdk "platforms") -Directory | Sort-Object Name -Descending | Select-Object -First 1 | ForEach-Object { Join-Path $_.FullName "android.jar" }
}
if (-not (Test-Path $buildTools)) {
    $buildTools = Get-ChildItem (Join-Path $sdk "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1 | ForEach-Object FullName
}

$aapt2 = Join-Path $buildTools "aapt2.exe"
$d8 = Join-Path $buildTools "d8.bat"
$zipalign = Join-Path $buildTools "zipalign.exe"
$apksigner = Join-Path $buildTools "apksigner.bat"
$manifest = Join-Path $root "AndroidManifest.xml"
$res = Join-Path $root "res"
$javaSrc = Join-Path $root "src"
$out = Join-Path $root "build"
$classes = Join-Path $out "classes"
$dex = Join-Path $out "dex"
$generated = Join-Path $out "generated"
$compiled = Join-Path $out "compiled-res.zip"
$unsigned = Join-Path $out "standby-canvas-unsigned.apk"
$aligned = Join-Path $out "standby-canvas-aligned.apk"
$packed = Join-Path $out "standby-canvas-packed.apk"
$apk = Join-Path $root "build\standby-canvas-debug.apk"
$keystore = Join-Path $out "debug.keystore"

function Run-Checked($exe, [string[]] $arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $exe $($arguments -join ' ')"
    }
}

New-Item -ItemType Directory -Force -Path $out, $classes, $dex, $generated | Out-Null
Remove-Item -Force -ErrorAction SilentlyContinue $compiled, $unsigned, $aligned, $packed, $apk, (Join-Path $out "with-classes.apk")
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $classes "*"), (Join-Path $dex "*"), (Join-Path $generated "*")

$assets = Join-Path $root "assets"
Run-Checked $aapt2 @("compile", "--dir", $res, "-o", $compiled)
Run-Checked $aapt2 @("link", "-o", $unsigned, "-I", $platform, "--manifest", $manifest, "--java", $generated, "-A", $assets, "--min-sdk-version", "23", "--target-sdk-version", "35", $compiled)

$sources = Get-ChildItem $javaSrc -Recurse -Filter *.java | ForEach-Object FullName
$javacArgs = @("-encoding", "UTF-8", "-source", "8", "-target", "8", "-classpath", $platform, "-d", $classes) + $sources
Run-Checked "javac" $javacArgs
$classFiles = Get-ChildItem $classes -Recurse -Filter *.class | ForEach-Object FullName
$d8Args = @("--lib", $platform, "--output", $dex) + $classFiles
Run-Checked $d8 $d8Args
Copy-Item $unsigned $packed -Force

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($packed, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $existing = $archive.GetEntry("classes.dex")
    if ($existing) { $existing.Delete() }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $dex "classes.dex"), "classes.dex") | Out-Null
} finally {
    $archive.Dispose()
}

Run-Checked $zipalign @("-f", "4", $packed, $aligned)

if (-not (Test-Path $keystore)) {
    Run-Checked "keytool" @("-genkeypair", "-v", "-keystore", $keystore, "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Android Debug,O=Android,C=US")
}
Run-Checked $apksigner @("sign", "--ks", $keystore, "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", $apk, $aligned)
Run-Checked $apksigner @("verify", "--verbose", $apk)

Write-Host "Built $apk"
