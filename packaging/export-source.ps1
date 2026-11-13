param([string]$ArchivePath=(Join-Path $PSScriptRoot '..\dist\nine-feelings-0.4.1-source.zip'))
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$files=@(& git -C $root ls-files)
if($LASTEXITCODE -ne 0 -or $files.Count -eq 0){throw 'Initialize and stage the public source files first.'}
foreach($file in $files){if($file -match '(^|/)(build|dist|\.tools|\.gradle)/|\.(jks|keystore|apk|aab|idsig|hprof|log)$'){throw "Unexpected private/build artifact in source index: $file"}}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archiveFull=[IO.Path]::GetFullPath($ArchivePath)
New-Item -ItemType Directory -Force (Split-Path $archiveFull -Parent) | Out-Null
$stream=[IO.File]::Open($archiveFull,[IO.FileMode]::Create,[IO.FileAccess]::Write)
$archive=New-Object IO.Compression.ZipArchive($stream,[IO.Compression.ZipArchiveMode]::Create)
try{foreach($file in $files){[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $root $file),$file,[IO.Compression.CompressionLevel]::Optimal)|Out-Null}}finally{$archive.Dispose();$stream.Dispose()}
Write-Output "Public source archive: $($files.Count) files"
