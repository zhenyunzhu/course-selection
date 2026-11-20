# 启动 Kafka（KRaft 单机模式，无需 ZooKeeper）
# 用法：.\kafka-start.ps1
# 数据目录：C:\Users\zzy13\Apps\kafka-data（独立于安装目录，避免权限问题）

$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Users\zzy13\.jdks\ms-17.0.20.1"
$kafkaHome = "C:\Users\zzy13\Apps\kafka_2.13-4.1.2"
$dataDir = "C:\Users\zzy13\Apps\kafka-data"

if (-not (Test-Path $kafkaHome)) {
    Write-Host "找不到 Kafka：$kafkaHome" -ForegroundColor Red
    Write-Host "去 https://downloads.apache.org/kafka/ 下载 kafka_2.13-4.x.tgz 解压到 C:\Users\zzy13\Apps" -ForegroundColor Yellow
    exit 1
}

# 4.x 的 KRaft 配置就是 config/server.properties；4.0 之前是 config/kraft/server.properties
$conf = Join-Path $kafkaHome "config\server.properties"
if (-not (Test-Path $conf)) { $conf = Join-Path $kafkaHome "config\kraft\server.properties" }

# 数据目录指到用户目录，避免默认 /tmp 在 Windows 上的路径问题
$content = Get-Content $conf -Raw -Encoding UTF8
if ($content -match "(?m)^log\.dirs=") {
    $content = $content -replace "(?m)^log\.dirs=.*$", ("log.dirs=" + ($dataDir -replace '\\','/'))
} else {
    $content += "`nlog.dirs=" + ($dataDir -replace '\\','/')
}
Set-Content -LiteralPath $conf -Value $content -Encoding UTF8 -NoNewline

if (-not (Test-Path (Join-Path $dataDir "meta.properties"))) {
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    $uuid = [guid]::NewGuid().ToString()
    Write-Host "首次启动，格式化存储目录（cluster id: $uuid）"
    & (Join-Path $kafkaHome "bin\windows\kafka-storage.bat") format -t $uuid -c $conf
}

Write-Host "启动 Kafka（后台，直接跑 JVM，绕开 .bat 的命令行长度限制）..."
$java = Join-Path $env:JAVA_HOME "bin\java.exe"
Start-Process -FilePath $java `
    -ArgumentList @("-Xms512m", "-Xmx512m", "-cp", "`"libs\*`"", "kafka.Kafka", "`"$conf`"") `
    -WorkingDirectory $kafkaHome -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $kafkaHome "kafka.out.log") `
    -RedirectStandardError  (Join-Path $kafkaHome "kafka.err.log")

Start-Sleep -Seconds 15
$ok = Test-NetConnection -ComputerName 127.0.0.1 -Port 9092 -InformationLevel Quiet -WarningAction SilentlyContinue
if ($ok) {
    Write-Host "Kafka 已启动：127.0.0.1:9092" -ForegroundColor Green
} else {
    Write-Host "9092 未就绪，看日志：$kafkaHome\kafka.out.log" -ForegroundColor Red
}
