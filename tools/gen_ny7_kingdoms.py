#!/usr/bin/env python3
"""Night's Blood patch 7 (Kingdoms side): the garlic wreath, the hunter's coat, the garlic draught and their data and art.
Run from the repo root: python3 tools/gen_ny7_kingdoms.py. Safe to run again (it rewrites the same files)."""
import json, math, os, random
from PIL import Image

K = 'kingdoms/src/main/resources'
KA = f'{K}/assets/jaymoskingdoms'
KD = f'{K}/data/jaymoskingdoms'
KM = f'{K}/data/minecraft'
KP = f'{K}/data/jaymospowers'


def write(path, data):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, 'w', encoding='utf-8') as f:
		json.dump(data, f, indent=2)
		f.write('\n')


def wreath_png(path, leaf, leaf_dark, bulb, seed):
	"""A ring of leaves 14 across with four bulbs of garlic on it."""
	rnd = random.Random(seed)
	im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
	for y in range(16):
		for x in range(16):
			d = math.hypot(x - 7.5, y - 7.5)
			if 4.6 <= d <= 7.6:
				im.putpixel((x, y), (leaf if rnd.random() < 0.6 else leaf_dark) + (255,))
	for ang in (45, 135, 225, 315):
		cx = 7.5 + 6.0 * math.cos(math.radians(ang))
		cy = 7.5 + 6.0 * math.sin(math.radians(ang))
		for dx in (-1, 0, 1):
			for dy in (-1, 0, 1):
				if abs(dx) + abs(dy) <= 1:
					im.putpixel((int(round(cx)) + dx, int(round(cy)) + dy), bulb + (255,))
	os.makedirs(os.path.dirname(path), exist_ok=True)
	im.save(path)


def brown(src, dst, light, dark):
	"""Recolours an armour or item picture to a leather brown, keeping its shading."""
	im = Image.open(src).convert('RGBA')
	out = Image.new('RGBA', im.size)
	for y in range(im.size[1]):
		for x in range(im.size[0]):
			r, g, b, a = im.getpixel((x, y))
			if a == 0:
				continue
			v = (r * 0.3 + g * 0.59 + b * 0.11) / 255.0
			out.putpixel((x, y), tuple(int(dark[i] + (light[i] - dark[i]) * v) for i in range(3)) + (a,))
	os.makedirs(os.path.dirname(dst), exist_ok=True)
	out.save(dst)


def effect_icon(path):
	im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
	for y in range(18):
		for x in range(18):
			d = (x - 8.5) ** 2 + (y - 8.5) ** 2
			if d <= 56:
				im.putpixel((x, y), ((90, 110, 60) if d > 42 else (140, 170, 90)) + (255,))
	# a head of garlic
	for (x, y) in [(8, 4), (8, 5), (7, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8),
		(5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (11, 9), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10), (7, 11), (8, 11), (9, 11)]:
		im.putpixel((x, y), (245, 245, 235, 255))
	os.makedirs(os.path.dirname(path), exist_ok=True)
	im.save(path)


def brewing(name, input_item, input_potion, output_item, output_potion, reagent):
	write(f'{KD}/recipe/brewing/{name}.json', {
		'type': 'minecraft:brewing',
		'input': {'item': input_item, 'potion_contents': {'potions': input_potion}},
		'output': {'components': {'minecraft:potion_contents': {'potion': output_potion}}, 'id': output_item},
		'reagent': {'item': reagent}})


def main():
	# ---- the wreath ----
	stages = [((70, 130, 50), (45, 95, 40), (250, 248, 235)), ((140, 140, 60), (110, 100, 45), (225, 215, 185)),
		((120, 90, 50), (85, 62, 38), (190, 170, 140))]
	for i, (leaf, dark, bulb) in enumerate(stages):
		wreath_png(f'{KA}/textures/block/garlic_wreath_{i}.png', leaf, dark, bulb, 7 + i)
		write(f'{KA}/models/block/garlic_wreath_{i}.json', {
			'textures': {'particle': f'jaymoskingdoms:block/garlic_wreath_{i}', 'w': f'jaymoskingdoms:block/garlic_wreath_{i}'},
			'elements': [{'from': [1, 1, 14], 'to': [15, 15, 16], 'faces': {
				'north': {'uv': [0, 0, 16, 16], 'texture': '#w'}, 'south': {'uv': [0, 0, 16, 16], 'texture': '#w'},
				'east': {'uv': [0, 0, 2, 16], 'texture': '#w'}, 'west': {'uv': [0, 0, 2, 16], 'texture': '#w'},
				'up': {'uv': [0, 0, 16, 2], 'texture': '#w'}, 'down': {'uv': [0, 0, 16, 2], 'texture': '#w'}}}]})
	variants = {}
	for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
		for age in range(3):
			v = {'model': f'jaymoskingdoms:block/garlic_wreath_{age}'}
			if y:
				v['y'] = y
			variants[f'age={age},facing={facing}'] = v
	write(f'{KA}/blockstates/garlic_wreath.json', {'variants': variants})
	wreath_png(f'{KA}/textures/item/garlic_wreath.png', *stages[0], 7)
	write(f'{KA}/items/garlic_wreath.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/garlic_wreath'}})
	write(f'{KA}/models/item/garlic_wreath.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/garlic_wreath'}})
	# the wreath's drop is as worn as the block was
	pools = []
	for age, wear in enumerate((0.0, 1.0 / 3.0, 2.0 / 3.0)):
		pools.append({'rolls': 1, 'bonus_rolls': 0,
			'condition': {'type': 'minecraft:match_block', 'blocks': 'jaymoskingdoms:garlic_wreath', 'state': {'age': str(age)}},
			'entries': [{'type': 'minecraft:item', 'name': 'jaymoskingdoms:garlic_wreath',
				'modifier': [{'type': 'minecraft:set_damage', 'damage': round(wear, 3)}]}]})
	write(f'{KD}/loot_table/blocks/garlic_wreath.json', {'type': 'minecraft:block', 'pools': pools, 'random_sequence': 'jaymoskingdoms:blocks/garlic_wreath'})
	write(f'{KD}/recipe/garlic_wreath.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment',
		'key': {'G': '#jaymoskingdoms:garlic', 'V': 'minecraft:vine'}, 'pattern': ['GVG', 'V V', 'GVG'],
		'result': {'id': 'jaymoskingdoms:garlic_wreath'}})
	write(f'{KD}/tags/item/garlic.json', {'replace': False, 'values': ['jaymoskingdoms:garlic', {'id': 'jaymospowers:wild_garlic', 'required': False}]})
	# worn at the neck or on the head
	write(f'{KP}/tags/item/baubles/amulet.json', {'replace': False, 'values': ['jaymoskingdoms:necklace_of_tusks', 'jaymoskingdoms:garlic_wreath']})
	write(f'{KP}/tags/item/baubles/head.json', {'replace': False, 'values': ['jaymoskingdoms:garlic_wreath']})

	# ---- the coat ----
	brown(f'{KA}/textures/item/wool_shirt.png', f'{KA}/textures/item/hunters_coat.png', (150, 112, 74), (52, 36, 24))
	write(f'{KA}/items/hunters_coat.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/hunters_coat'}})
	write(f'{KA}/models/item/hunters_coat.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/hunters_coat'}})
	for layer in ('humanoid', 'humanoid_leggings'):
		src = f'{KA}/textures/entity/equipment/{layer}/wool.png'
		if os.path.exists(src):
			brown(src, f'{KA}/textures/entity/equipment/{layer}/hunters_coat.png', (150, 112, 74), (52, 36, 24))
	write(f'{KA}/equipment/hunters_coat.json', {'layers': {'humanoid': [{'texture': 'jaymoskingdoms:hunters_coat'}]}})
	write(f'{KD}/recipe/hunters_coat.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment',
		'key': {'L': 'minecraft:leather', 'S': 'minecraft:string'}, 'pattern': ['L L', 'LSL', 'LLL'],
		'result': {'id': 'jaymoskingdoms:hunters_coat'}})
	write(f'{KD}/villager_trade/shepherd/hunters_coat.json', {
		'gives': {'id': 'jaymoskingdoms:hunters_coat'}, 'max_uses': 6, 'reputation_discount': 0.2,
		'wants': {'count': 10, 'id': 'minecraft:emerald'}, 'xp': 12})
	write(f'{KM}/tags/villager_trade/shepherd/level_3.json', {'replace': False, 'values': [
		'jaymoskingdoms:shepherd/wool_shirt', 'jaymoskingdoms:shepherd/wool_pants', 'jaymoskingdoms:shepherd/hunters_coat']})
	# (the coat is added to minecraft:chest_armor and jaymospowers:cloth_armor by hand in those files)

	# ---- the garlic draught ----
	effect_icon(f'{KA}/textures/mob_effect/sour_blood.png')
	brewing('potion_awkward_garlic', 'minecraft:potion', 'minecraft:awkward', 'minecraft:potion', 'jaymoskingdoms:garlic_draught', '#jaymoskingdoms:garlic')
	brewing('potion_garlic_draught_redstone', 'minecraft:potion', 'jaymoskingdoms:garlic_draught', 'minecraft:potion', 'jaymoskingdoms:long_garlic_draught', 'minecraft:redstone')
	brewing('potion_garlic_draught_gunpowder', 'minecraft:potion', 'jaymoskingdoms:garlic_draught', 'minecraft:splash_potion', 'jaymoskingdoms:garlic_draught', 'minecraft:gunpowder')
	brewing('potion_long_garlic_draught_gunpowder', 'minecraft:potion', 'jaymoskingdoms:long_garlic_draught', 'minecraft:splash_potion', 'jaymoskingdoms:long_garlic_draught', 'minecraft:gunpowder')
	brewing('splash_potion_garlic_draught_redstone', 'minecraft:splash_potion', 'jaymoskingdoms:garlic_draught', 'minecraft:splash_potion', 'jaymoskingdoms:long_garlic_draught', 'minecraft:redstone')


main()
