#!/bin/sh
#

set -xe

java -Dsun.java2d.opengl=True -Dsun.java2d.ddforcevram=true  -Dsun.java2d.ddscale=true -Dsun.java2d.translaccel=true  -cp ./bin:. Wendigo
