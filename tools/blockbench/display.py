import math

def display(tp, fp, gui, center_y):
    """Held/GUI transforms for an upright blade model whose grip sits near y=8 and whose blade is wide along x."""
    off = (center_y - 8) * gui
    d = round(off * math.sin(math.radians(45)), 2)
    g = round(gui, 3)
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 1.5, 0.5], "scale": [tp] * 3},
        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 1.5, 0.5], "scale": [tp] * 3},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [fp] * 3},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [fp] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [round(tp * 0.6, 3)] * 3},
        "gui": {"rotation": [0, 0, -45], "translation": [-d, -d, 0], "scale": [g] * 3},
        "fixed": {"rotation": [0, 0, -45], "translation": [-d, -d, 0], "scale": [g] * 3},
    }
