#!/bin/bash
# ExpenseFlow (HTML/CSS edition) - Package as runnable JAR (Linux / macOS)
# Note: the web/ folder (HTML + CSS) is NOT bundled inside the jar — it stays
# as plain files on disk next to the jar, since the whole point of this build
# is that the UI is editable HTML/CSS, not compiled-in resources.
set -e
cd "$(dirname "$0")"

echo "Compiling..."
rm -rf build
mkdir -p build
javac -d build -encoding UTF-8 $(find src -name "*.java")

echo "Creating manifest..."
mkdir -p build/META-INF
cat > build/META-INF/MANIFEST.MF << EOF
Manifest-Version: 1.0
Main-Class: com.expense.App

EOF

echo "Packaging JAR..."
cd build
jar cfm ../ExpenseFlow.jar META-INF/MANIFEST.MF com
cd ..

echo "Done."
echo "Run it from this folder with:  java -jar ExpenseFlow.jar"
echo "(the web/ folder must stay alongside the jar - it holds the HTML/CSS UI)"
