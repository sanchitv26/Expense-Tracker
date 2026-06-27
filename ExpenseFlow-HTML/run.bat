@echo off
REM ExpenseFlow (HTML/CSS edition) - Build & Run script (Windows)
REM Requires: JDK 11+ (javac and java available on PATH)

cd /d "%~dp0"

echo Compiling ExpenseFlow...
if exist build rmdir /s /q build
mkdir build

dir /s /b src\*.java > sources.txt
javac -d build -encoding UTF-8 @sources.txt
del sources.txt

if %ERRORLEVEL% NEQ 0 (
    echo Build failed.
    pause
    exit /b 1
)

echo Build successful. Launching ExpenseFlow...
java -cp build com.expense.App
pause
