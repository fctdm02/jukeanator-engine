# Builds the WAR and deploys it to the production master (see "Updating the application" in
# MasterConfig.md). Uses SSH key access only; no passwords or secrets are involved.
#
#   powershell -ExecutionPolicy Bypass -File deploy\deploy-prod.ps1
#   powershell -ExecutionPolicy Bypass -File deploy\deploy-prod.ps1 -SkipBuild
#
# -SshHost (or the JUKEANATOR_DEPLOY_HOST environment variable) names the server's entry in
# %USERPROFILE%\.ssh\config; the default is jukeanator-prod. -SkipBuild deploys the WAR already in
# target\. The server-side steps (checksum, database backup, switch, restart, automatic rollback)
# are in deploy\jukeanator-deploy.sh, which is uploaded with the WAR on every run.
param(
  [string]$SshHost = $(if ($env:JUKEANATOR_DEPLOY_HOST) { $env:JUKEANATOR_DEPLOY_HOST } else { 'jukeanator-prod' }),
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
Set-Location $projectDir

function Invoke-Native([string]$what, [scriptblock]$command) {
  & $command
  if ($LASTEXITCODE -ne 0) { throw "$what failed (exit code $LASTEXITCODE)" }
}

if (-not $SkipBuild) {
  Write-Host 'Building the WAR (tests skipped)...'
  Invoke-Native 'Build' { & .\mvnw.cmd -q -DskipTests package }
}

$war = Get-ChildItem -Path target -Filter *.war | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $war) { throw 'No WAR found in target\; build first or drop -SkipBuild' }
$hash = (Get-FileHash -Path $war.FullName -Algorithm SHA256).Hash.ToLower()
Write-Host ("Deploying {0} ({1:N1} MB, built {2}) to {3}" -f $war.Name, ($war.Length / 1MB), $war.LastWriteTime, $SshHost)

Write-Host 'Uploading...'
Invoke-Native 'Upload' { scp -q -o BatchMode=yes $war.FullName "${SshHost}:/opt/jukeanator/jukeanator-engine.war.new" }
Invoke-Native 'Upload of the deploy script' { scp -q -o BatchMode=yes deploy\jukeanator-deploy.sh "${SshHost}:/opt/jukeanator/jukeanator-deploy.sh" }

# The script may have Windows line endings after a git checkout; strip them before running it.
Invoke-Native 'Deploy' { ssh -o BatchMode=yes $SshHost "sed -i 's/\r`$//' /opt/jukeanator/jukeanator-deploy.sh && bash /opt/jukeanator/jukeanator-deploy.sh $hash" }
Write-Host 'Done.'
