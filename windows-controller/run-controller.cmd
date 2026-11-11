@echo off
setlocal
set ROOT=%~dp0
dotnet run --project "%ROOT%LaserController.Windows.csproj" -- --simulate %*
