@echo off
set JAVA_HOME=D:\Serve\JDK17
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\Project\ChronoVault\backend
call mvnw.cmd %*