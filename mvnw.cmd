@echo off
rem Thin shim: delegates to the Maven installation on PATH (install Maven 3.8+ and run "mvn -version" to check).
mvn %*
