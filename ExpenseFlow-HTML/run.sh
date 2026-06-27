#!/bin/bash
# ExpenseFlow (HTML/CSS edition) - Build & Run script (Linux / macOS)
# Requires: JDK 11+ (javac + java on PATH)

set -e
cd "$(dirname "$0")"

echo "Compiling ExpenseFlow..."
rm -rf build
mkdir -p build
javac -d build -encoding UTF-8 $(find src -name "*.java")

echo "Build successful. Launching ExpenseFlow..."
java -cp build com.expense.App
