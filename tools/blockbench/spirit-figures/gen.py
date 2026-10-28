# Writes SpiritFigure.java from figures.py and the template (run after changing a figure, then bbexport.py and render.py).
from figures import FIG, java, ARM_INNER
import os
HERE = os.path.dirname(os.path.abspath(__file__))
KINDS = ["GRIMSBY", "VARRO", "AMBROSE", "THESSALY", "QUIET"]
def cases(group, ind="\t\t\t"):
    out = []
    for k, name in enumerate(KINDS):
        label = "default" if name == "QUIET" else "case Spirits." + name
        out.append(ind + label + " -> {")
        for l in java(k, group):
            out.append(ind + "\t" + l)
        out.append(ind + "}")
    return "\n".join(out)
def arr(key, fmtf=lambda v: ("%.2f" % v).rstrip("0").rstrip(".") + ("" if "." in ("%.2f" % v).rstrip("0").rstrip(".") else ".0") + "F"):
    return ", ".join(fmtf(FIG[k][key]) for k in range(5))
def arr2(d):
    return ", ".join(("%.1fF" % d[k]) for k in range(5))
T = open(os.path.join(HERE, "SpiritFigure.java.in")).read()
T = T.replace("@SCALE@", arr("scale")).replace("@HALF@", arr("half")).replace("@FRONT@", arr("front")).replace("@TAIL@", arr("tail_y"))
T = T.replace("@INNER@", arr2(ARM_INNER))
T = T.replace("@TORSO@", cases("torso")).replace("@SKULL@", cases("skull")).replace("@TAILS@", cases("tail"))
open(os.path.join(HERE, "../../../kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/SpiritFigure.java"), "w").write(T)
