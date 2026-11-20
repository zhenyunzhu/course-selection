# 停止三个服务（只杀本项目的 java 进程，不影响 IDEA 等其它 java）
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like "*cs-course*" -or $_.CommandLine -like "*cs-enroll*" -or $_.CommandLine -like "*cs-gateway*" } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force
        Write-Host "已停止 PID $($_.ProcessId)"
    }
Write-Host "完成"
