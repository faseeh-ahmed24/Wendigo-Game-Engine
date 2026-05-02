@echo off

echo Running Program.

@REM -Dsun.java2d.d3d=True -Dsun.java2d.accthreshold=0 -Dsun.java2d.d3dtexbpp=16 -Dsun.java2d.ddforcevram=true  -Dsun.java2d.ddscale=true -Dsun.java2d.translaccel=true
java.exe -Dsun.java2d.d3d=True -Dsun.java2d.accthreshold=0 -Dsun.java2d.d3dtexbpp=16 -Dsun.java2d.ddforcevram=true  -Dsun.java2d.ddscale=true -Dsun.java2d.translaccel=true -cp bin/ Wendigo

echo Execution Finished.
