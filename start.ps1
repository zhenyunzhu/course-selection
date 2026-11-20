# 启动三个服务（Windows PowerShell）
# 用法：右键「使用 PowerShell 运行」，或在窗口里执行 .\start.ps1

$ErrorActionPreference = "Stop"

# ⚠️ 必须用 JDK 17：本机默认的 java 是 26，Lombok 和 Spring Boot 3.5 在 26 上编译会静默失败
$env:JAVA_HOME = "C:\Users\zzy13\.jdks\ms-17.0.20.1"
$java = Join-Path $env:JAVA_HOME "bin\java.exe"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$logs = Join-Path $root "logs"
New-Item -ItemType Directory -Force -Path $logs | Out-Null

$services = @(
    @{ name = "course";  jar = "cs-course\target\cs-course-1.0.0.jar" },
    @{ name = "enroll";  jar = "cs-enroll\target\cs-enroll-1.0.0.jar" },
    @{ name = "gateway"; jar = "cs-gateway\target\cs-gateway-1.0.0.jar" }
)

foreach ($s in $services) {
    $jar = Join-Path $root $s.jar
    if (-not (Test-Path $jar)) {
        Write-Host "缺少 $($s.jar)，请先执行： mvn -DskipTests package" -ForegroundColor Red
        exit 1
    }
    Start-Process -FilePath $java -ArgumentList @("-jar", "`"$jar`"") -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logs "$($s.name).log") `
        -RedirectStandardError  (Join-Path $logs "$($s.name).err") `
        -WindowStyle Hidden
    Write-Host "已启动 $($s.name)  (日志: logs\$($s.name).log)"
    Start-Sleep -Seconds 2
}

Write-Host "`n等待启动..." -ForegroundColor Yellow
Start-Sleep -Seconds 20

foreach ($p in 8081, 8082, 8088) {
    $ok = Test-NetConnection -ComputerName 127.0.0.1 -Port $p -InformationLevel Quiet -WarningAction SilentlyContinue
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host ("端口 {0} : {1}" -f $p, $(if ($ok) { "OK" } else { "FAILED" })) -ForegroundColor $color
}

Write-Host "`n网关地址: http://localhost:8088" -ForegroundColor Cyan
