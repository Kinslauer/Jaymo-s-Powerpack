"""
(0.7.334) The Heavenly Mantle's items (Armory.HEAVENLY_PIECES), made as gen_reverent.py makes the Reverent Warplate's:
each piece's icon is its part of eanncrod's model drawn small with an outline, plus its item models, its equipment
asset (no flat layer: the client's ReverentLayer draws the model) and names.

Run from the project root:  python -I kingdoms/tools/gen_heavenly.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_reverent as r

r.SOURCE = 'tools/bbmodels/heavenly-mantle.bbmodel'
PIECES = {'helmet': 'armorHead', 'chestplate': 'armorBody,armorRightArm,armorLeftArm', 'leggings': 'armorWaist,armorRightLeg,armorLeftLeg',
		  'boots': 'armorRightBoot,armorLeftBoot'}
NAMES = {'helmet': 'Heavenly Crown', 'chestplate': 'Heavenly Mantle', 'leggings': 'Heavenly Greaves', 'boots': 'Heavenly Sabatons'}


def main():
	for piece, bones in PIECES.items():
		name = 'heavenly_' + piece
		r.icon(bones, r.ASSETS + 'textures/item/' + name + '.png')
		r.write(r.ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
		r.write(r.ASSETS + 'models/item/' + name + '.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})
	r.write(r.ASSETS + 'equipment/heavenly.json', {'layers': {}})
	print('\n'.join('\t"item.jaymoskingdoms.heavenly_%s": "%s",' % (p, n) for p, n in NAMES.items()))


if __name__ == '__main__':
	main()
