#!/usr/bin/env python3
"""Verify battery rules and production pixel frames; export the same frames for preview."""
from pathlib import Path
import subprocess
from toolchain import java_home, sdk_tool

ROOT=Path(__file__).resolve().parents[1]
JAVA=java_home()
out=ROOT/"build/tests"
out.mkdir(parents=True,exist_ok=True)
src=ROOT/"app/src/com/stepxadvance/backscreentoolbox"
subprocess.run([str(sdk_tool(JAVA/"bin","javac")),"-encoding","UTF-8","-d",str(out),*[str(src/n) for n in ("ChargeState.java","ChargeFrames.java","BypassSignal.java","RotationPolicy.java","RotationCoordinator.java","FrameOrientation.java","LockedDisplayPolicy.java","RotationTransition.java","SetupPolicy.java","SensorHealth.java")],*[str(p) for p in sorted((ROOT/"tests").glob("*.java"))]],check=True)
base=[str(sdk_tool(JAVA/"bin","java")),"-cp",str(out),"com.stepxadvance.backscreentoolbox.FrameTest"]
subprocess.run(base,check=True)
subprocess.run([str(sdk_tool(JAVA/"bin","java")),"-cp",str(out),"com.stepxadvance.backscreentoolbox.RotationTest"],check=True)
subprocess.run([str(sdk_tool(JAVA/"bin","java")),"-cp",str(out),"com.stepxadvance.backscreentoolbox.TransitionTest"],check=True)
subprocess.run([str(sdk_tool(JAVA/"bin","java")),"-cp",str(out),"com.stepxadvance.backscreentoolbox.SetupTest"],check=True)
subprocess.run([str(sdk_tool(JAVA/"bin","java")),"-cp",str(out),"com.stepxadvance.backscreentoolbox.SensorHealthTest"],check=True)
subprocess.run([str(sdk_tool(JAVA/"bin","java")),"-cp",str(out),"com.stepxadvance.backscreentoolbox.LockedRotationTest"],check=True)
with (ROOT/"build/frames.json").open("w") as f:subprocess.run(base+["export"],stdout=f,check=True)
with (ROOT/"build/transition-frames.json").open("w") as f:subprocess.run([str(sdk_tool(JAVA/"bin","java")),"-cp",str(out),"com.stepxadvance.backscreentoolbox.TransitionTest","export"],stdout=f,check=True)
