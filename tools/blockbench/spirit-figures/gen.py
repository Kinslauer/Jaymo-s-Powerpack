# Writes SpiritFigure.java from figures.py and the template (run after changing a figure, then bbexport.py and render.py).
from figures import FIG, java, fmt, ARM_INNER
import os
HERE = os.path.dirname(os.path.abspath(__file__))
KINDS = ["GRIMSBY", "VARRO", "AMBROSE", "THESSALY", "QUIET", "GRUE"]
def cases(group, ind="\t\t\t"):
    out = []
    for k, name in enumerate(KINDS):
        label = "default" if name == "GRUE" else "case Spirits." + name
        out.append(ind + label + " -> {")
        for l in java(k, group, mirror=(group in ("arm", "fore", "wing"))):
            out.append(ind + "\t" + l)
        out.append(ind + "}")
    return "\n".join(out)
def arr(key, fmtf=lambda v: ("%.2f" % v).rstrip("0").rstrip(".") + ("" if "." in ("%.2f" % v).rstrip("0").rstrip(".") else ".0") + "F"):
    return ", ".join(fmtf(FIG[k][key]) for k in range(6))
def arr2(d):
    return ", ".join(("%.1fF" % d[k]) for k in range(6))
T = open(os.path.join(HERE, "SpiritFigure.java.in")).read()
T = T.replace("@SCALE@", arr("scale")).replace("@HALF@", arr("half")).replace("@FRONT@", arr("front")).replace("@TAIL@", arr("tail_y"))
T = T.replace("@INNER@", arr2(ARM_INNER))
W = lambda i: ", ".join(fmt(FIG[k].get("wing_at", (0.0, 0.0, 0.0))[i]) for k in range(6))
T = T.replace("@WINGX@", W(0)).replace("@WINGY@", W(1)).replace("@WINGZ@", W(2)).replace("@WINGS@", cases("wing"))
T = T.replace("@FOOT@", arr("foot")).replace("@STAGE@", arr("stage"))
T = T.replace("@ARMX@", ", ".join(fmt(FIG[k]["arm_at"][0]) for k in range(6))).replace("@ARMY@", ", ".join(fmt(FIG[k]["arm_at"][1]) for k in range(6)))
T = T.replace("@TORSO@", cases("torso")).replace("@SKULL@", cases("skull")).replace("@TAILS@", cases("tail")).replace("@ARMS@", cases("arm")).replace("@FORES@", cases("fore")).replace("@ELBOW@", arr("elbow"))
open(os.path.join(HERE, "../../../kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/SpiritFigure.java"), "w").write(T)
