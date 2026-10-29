"""
(0.7.351) The Kintsugi Armour's and the Primal Visage's items (Armory.KINTSUGI_PIECES, Armory.PRIMAL_PIECES), made as
gen_reverent.py makes the Reverent Warplate's: each set's model baked for the client's ReverentLayer, each piece's icon
its part of eanncrod's model drawn small with an outline, plus its item models, its equipment asset (no flat layer) and
names.

Run from the project root:  python -I kingdoms/tools/gen_kintsugi_primal.py
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_reverent as r

SETS = {
	'kintsugi': ('tools/bbmodels/kintsugi-armor.bbmodel', 'kintsugi_armour',
				 {'helmet': 'Kintsugi Kabuto', 'chestplate': 'Kintsugi Do', 'leggings': 'Kintsugi Haidate', 'boots': 'Kintsugi Suneate'}),
	'primal': ('tools/bbmodels/primal-visage.bbmodel', 'primal_visage',
			   {'helmet': 'Primal Visage', 'chestplate': 'Primal Hauberk', 'leggings': 'Primal Girdle', 'boots': 'Primal Stompers'}),
}
PIECES = {'helmet': 'armorHead', 'chestplate': 'armorBody,armorRightArm,armorLeftArm', 'leggings': 'armorWaist,armorRightLeg,armorLeftLeg',
		  'boots': 'armorRightBoot,armorLeftBoot'}


def main():
	for prefix, (source, model, names) in SETS.items():
		subprocess.run([sys.executable, '-I', 'tools/bbmodel_bake.py', source, r.ASSETS.rstrip('/'), model], check=True)
		r.SOURCE = source
		for piece, bones in PIECES.items():
			name = prefix + '_' + piece
			r.icon(bones, r.ASSETS + 'textures/item/' + name + '.png')
			r.write(r.ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
			r.write(r.ASSETS + 'models/item/' + name + '.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})
		r.write(r.ASSETS + 'equipment/' + prefix + '.json', {'layers': {}})
		print('\n'.join('\t"item.jaymoskingdoms.%s_%s": "%s",' % (prefix, p, n) for p, n in names.items()))


if __name__ == '__main__':
	main()
