$url = "https://repo.gradle.org/gradle/wrapper-dist/gradle-wrapper.jar"
$output = "gradle\wrapper\gradle-wrapper.jar"

# Create directory if not exists
if (!(Test-Path -Path "gradle\wrapper")) {
    New-Item -ItemType Directory -Path "gradle\wrapper" -Force
}

# Download file
Write-Host "Downloading gradle-wrapper.jar..."
Invoke-WebRequest -Uri $url -OutFile $output
Write-Host "Download completed!" 