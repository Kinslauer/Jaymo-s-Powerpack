"""Side-by-side renders of the Omen: the old OmenRenderer's code poses (top row of each pair, drawn with POSE on the old
bake) against the GeckoLib animations of tools/gen_omen_geo.py (below). Writes art/the_omen-<pose>-comparison.png.

Run from the project root after gen_omen_geo.py: python3 -I tools/omen_compare.py
Bakes to /tmp/omen_compare (scratch): the old from tools/bbmodels/the-omen-<build>.bbmodel, the new from art/.
"""
import math
import os
import subprocess
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRATCH = "/tmp/omen_compare"
OLD, NEW = os.path.join(SCRATCH, "old"), os.path.join(SCRATCH, "new")
S = math.degrees(0.75)

# the old code's poses (degrees, added to rest), and the new animation and time to set beside each
CASES = {
	"rest": ("", "idle", 0.0),
	"walk": ("left_thigh_joint=%f:0:0,right_thigh_joint=%f:0:0,left_calf_joint=%f:0:0,left_arm_joint=%f:0:0,right_arm_joint=%f:0:0"
			 % (-S, S, S * 0.9, S * 0.5, -S * 0.5), "walk", 0.0),
	# aim: the new code sets the same shoulder angles in the renderer, drawn here on the idle animation
	"aim": ("left_arm_joint=-77.35:0:0,right_arm_joint=-83.08:0:0", "idle", 0.0),
	"smash": ("left_arm_joint=%f:0:0,right_arm_joint=%f:0:0" % (math.degrees(-2.5), math.degrees(-2.5)), "smash", 0.3),
	"smash_end": ("left_arm_joint=%f:0:0,right_arm_joint=%f:0:0" % ((math.degrees(-2.5 * math.sin(math.pi * 0.85)),) * 2), "smash", 0.42),
}


def run(*args, env=None):
	subprocess.run([sys.executable, "-I"] + list(args), check=True, cwd=ROOT, env=env, stdout=subprocess.DEVNULL)


def main():
	os.makedirs(SCRATCH, exist_ok=True)
	for build in ("red", "blue"):
		name = "omen_" + build
		old_src = os.path.join(ROOT, "tools", "bbmodels", "the-omen-%s.bbmodel" % build)
		run("tools/bbmodel_bake.py", old_src, OLD, name)
		run("tools/bbmodel_bake.py", os.path.join(ROOT, "art", "the_omen_%s.bbmodel" % build), NEW, name)
		cases = dict(CASES)
		cases["death"] = ("", "death_" + build, 0.6)
		for case, (pose, anim, t) in cases.items():
			if build == "blue" and case not in ("rest", "death"):
				continue
			old_png, new_png = os.path.join(SCRATCH, "o.png"), os.path.join(SCRATCH, "n.png")
			env = dict(os.environ, POSE=pose)
			old_anim = "pose_1" if case == "death" else "-"
			run("tools/bbmodel_preview.py", OLD, name, old_png, old_anim, "0", env=env)
			new_env = dict(os.environ, POSE=pose if case == "aim" else "")
			run("tools/bbmodel_preview.py", NEW, name, new_png, anim, str(t), env=new_env)
			a, b = Image.open(old_png), Image.open(new_png)
			out = Image.new("RGB", (a.width, a.height + b.height))
			out.paste(a, (0, 0))
			out.paste(b, (0, a.height))
			d = ImageDraw.Draw(out)
			d.text((6, 6), "old: OmenRenderer code pose (%s)" % case, fill=(0, 0, 0))
			d.text((6, a.height + 6), "new: GeckoLib %s @ %.2fs" % (anim, t), fill=(0, 0, 0))
			path = os.path.join(ROOT, "art", "the_omen_%s-%s-comparison.png" % (build, case))
			out.save(path)
			print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
	main()
