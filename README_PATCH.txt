LiNRR M00 patch
===============

Replace these two files in the existing project:

1. src\java\LiNRR_M00_Geometry.java
2. scripts\windows\02_build_M00_geometry.ps1

Then run from the project root:

powershell -ExecutionPolicy Bypass -File .\scripts\windows\02_build_M00_geometry.ps1

The Java fix declares java.io.IOException for Model.save().
The PowerShell fix locates the compiled .class file robustly.
