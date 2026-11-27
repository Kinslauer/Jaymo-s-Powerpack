#!/usr/bin/env python3
"""Writes the data and art of Hunter's Road patch 7, part A (oils, bombs, oil sash, oil pot, slick, monster fat).
Run from the repo root: python3 tools/gen_hr7a_data.py. Safe to run again (it rewrites the same files)."""
import json, os
from PIL import Image

P = 'src/main/resources'
K = 'kitchen/src/main/resources'
PA = f'{P}/assets/jaymospowers'
PD = f'{P}/data/jaymospowers'
KA = f'{K}/assets/jaymoskitchen'
KD = f'{K}/data/jaymoskitchen'

OILS = [  # id, colour, group
	('dead', 0xB8C4D8), ('beasts', 0xC8883A), ('brutes', 0x7A8A5A), ('people', 0xC85A5A),
	('poison', 0x6FA03A), ('haze', 0x9AA0A8), ('mark', 0xF2D84A), ('toxin', 0x7FB020), ('smoke', 0x585860),
	('acid', 0xB8D030), ('fire', 0xF08030), ('mending', 0x58C8B0), ('fire_ward', 0xE86A30), ('healing', 0xE85A78)]
BOMBS = [('fire_burst', 0xF08030), ('stun_flash', 0xF4F0D0), ('fire_spread', 0xE05818), ('poison_cloud', 0x6FA03A),
	('volatile_gas', 0xB8C060), ('freeze_blast', 0x9AD8F0), ('snuffer', 0x8A7AB0), ('stopper_dust', 0xB09878)]
FATS = [('dead', 0xB8C4D8), ('beast', 0xC8883A), ('brute', 0x7A8A5A), ('folk', 0xC85A5A)]


def write(path, data):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, 'w', encoding='utf-8') as f:
		if isinstance(data, str):
			f.write(data)
		else:
			json.dump(data, f, indent=2)
			f.write('\n')


def rgb(c):
	return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def shade(c, k):
	return tuple(max(0, min(255, int(v * k))) for v in c)


def png(path, rows, pal):
	"""rows: 16 strings; pal maps a character to an (r, g, b) or (r, g, b, a); '.' is clear."""
	im = Image.new('RGBA', (len(rows[0]), len(rows)), (0, 0, 0, 0))
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			if ch != '.':
				v = pal[ch]
				im.putpixel((x, y), v if len(v) == 4 else v + (255,))
	os.makedirs(os.path.dirname(path), exist_ok=True)
	im.save(path)


FLASK = [
	'................',
	'.......kk.......',
	'......kcck......',
	'......kcck......',
	'.......gg.......',
	'......kggk......',
	'.....kgwggk.....',
	'....kgwlllgk....',
	'....kglllllk....',
	'....kllllllk....',
	'....kllLllLk....',
	'....kllllllk....',
	'....kdllllLk....',
	'.....kddddk.....',
	'......kkkk......',
	'................']
BOMB = [
	'................',
	'..........y.....',
	'.........y.o....',
	'........f.......',
	'......kkkk......',
	'.....kssssk.....',
	'....kswssssk....',
	'....ksssssskk...',
	'...kbbbbbbbbk...',
	'...kbllllllbk...',
	'...kbbbbbbbbk...',
	'....ksssssssk...',
	'....kssssssdk...',
	'.....kddddk.....',
	'......kkkk......',
	'................']
BLOB = [  # fat: a lump
	'................',
	'................',
	'................',
	'.....kkkkk......',
	'....kllllwkk....',
	'...klwlllllkk...',
	'..kllllllllllk..',
	'..klllLlllllLk..',
	'..kllllllllllk..',
	'..kdlllLllllLk..',
	'...kddllllLdk...',
	'....kkddddkk....',
	'......kkkk......',
	'................',
	'................',
	'................']
POT = [  # grease: a small dish
	'................',
	'................',
	'................',
	'................',
	'....kkkkkkkk....',
	'...kcccccccck...',
	'..kcwllllllLck..',
	'..kclllLlllllk..',
	'..kcllllllllck..',
	'..kdcllllllcdk..',
	'...kdcccccdk....',
	'....kkkkkkk.....',
	'................',
	'................',
	'................',
	'................']
SASH = [
	'................',
	'.bb.............',
	'.bBb............',
	'..bBb...........',
	'...bBb..........',
	'....bBb.........',
	'.....bBb..pp....',
	'......bBbpPPp...',
	'.......bbPllPp..',
	'.......bBpPPPp..',
	'........bBbpp...',
	'.........bBb....',
	'..........bb....',
	'................',
	'................',
	'................']
BANDOLIER = [
	'................',
	'bb..............',
	'bBb.............',
	'.bBb.....pp.....',
	'..bBb...pPPp....',
	'...bBb..pllp.pp.',
	'....bBbbpPPppPPp',
	'.....bBbbbpPllPp',
	'......bBbppPPPp.',
	'.pp...pbBbbpp...',
	'pPPp...bBb.pp...',
	'pllp....bBb.....',
	'pPPp.....bb.....',
	'.pp.............',
	'................',
	'................']


def flask_png(path, col):
	c = rgb(col)
	pal = {'k': (40, 36, 44), 'c': (150, 110, 70), 'g': (200, 220, 230), 'w': (255, 255, 255), 'l': c,
		'L': shade(c, 1.25), 'd': shade(c, 0.7)}
	png(path, FLASK, pal)


def bomb_png(path, col):
	c = rgb(col)
	pal = {'k': (30, 28, 34), 's': (84, 84, 94), 'w': (150, 150, 162), 'd': (54, 54, 62), 'b': (126, 96, 62), 'l': c,
		'y': (150, 110, 70), 'o': (255, 170, 40), 'f': (110, 80, 50)}
	png(path, BOMB, pal)


def lump_png(path, rows, col):
	c = rgb(col)
	pal = {'k': (60, 54, 48), 'w': shade(c, 1.5), 'l': shade(c, 1.1), 'L': shade(c, 1.3), 'd': shade(c, 0.7), 'c': (150, 110, 70)}
	png(path, rows, pal)


def strap_png(path, rows, col):
	pal = {'b': (96, 64, 40), 'B': (126, 88, 56), 'p': (70, 50, 34), 'P': (100, 72, 48), 'l': rgb(col)}
	png(path, rows, pal)


def item_files(ns_assets, ns, path_id, model_dir, texture_dir):
	write(f'{ns_assets}/items/{path_id}.json', {'model': {'type': 'minecraft:model', 'model': f'{ns}:item/{model_dir}{path_id}'}})
	write(f'{ns_assets}/models/item/{model_dir}{path_id}.json',
		{'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{ns}:item/{texture_dir}{path_id}'}})


def effect_icon(path, col, glyph):
	im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
	c = rgb(col)
	for y in range(18):
		for x in range(18):
			d = (x - 8.5) ** 2 + (y - 8.5) ** 2
			if d <= 56:
				im.putpixel((x, y), shade(c, 0.45 if d > 42 else 0.75) + (255,))
	for (x, y) in glyph:
		im.putpixel((x, y), (255, 255, 255, 255))
	os.makedirs(os.path.dirname(path), exist_ok=True)
	im.save(path)


def main():
	# ---- items: oil flasks, bombs, sashes ----
	for oid, col in OILS:
		item_files(PA, 'jaymospowers', f'oil_{oid}', 'alchemy/', 'alchemy/')
		flask_png(f'{PA}/textures/item/alchemy/oil_{oid}.png', col)
	for bid, col in BOMBS:
		item_files(PA, 'jaymospowers', f'bomb_{bid}', 'alchemy/', 'alchemy/')
		bomb_png(f'{PA}/textures/item/alchemy/bomb_{bid}.png', col)
	item_files(PA, 'jaymospowers', 'oil_sash', 'alchemy/', 'alchemy/')
	strap_png(f'{PA}/textures/item/alchemy/oil_sash.png', SASH, 0xC8883A)
	item_files(PA, 'jaymospowers', 'oil_bandolier', 'alchemy/', 'alchemy/')
	strap_png(f'{PA}/textures/item/alchemy/oil_bandolier.png', BANDOLIER, 0xC8883A)

	# ---- the oil pot block ----
	write(f'{PA}/items/oil_pot.json', {'model': {'type': 'minecraft:model', 'model': 'jaymospowers:block/alchemy/oil_pot'}})
	tex = {'particle': 'minecraft:block/terracotta', 'p': 'minecraft:block/terracotta', 'r': 'minecraft:block/orange_terracotta'}

	def box(a, b, tx='#p'):
		return {'from': a, 'to': b, 'faces': {d: {'uv': [0, 0, 16, 16], 'texture': tx} for d in ('down', 'up', 'north', 'south', 'east', 'west')}}
	write(f'{PA}/models/block/alchemy/oil_pot.json', {'textures': tex, 'elements': [
		box([3, 0, 3], [13, 1, 13]),
		box([3, 1, 3], [13, 9, 4]), box([3, 1, 12], [13, 9, 13]),
		box([3, 1, 4], [4, 9, 12]), box([12, 1, 4], [13, 9, 12]),
		box([2, 8, 2], [14, 10, 3], '#r'), box([2, 8, 13], [14, 10, 14], '#r'),
		box([2, 8, 3], [3, 10, 13], '#r'), box([13, 8, 3], [14, 10, 13], '#r')]})
	multipart = [{'apply': {'model': 'jaymospowers:block/alchemy/oil_pot'}}]
	for oid, col in OILS:
		c = shade(rgb(col), 1.0)
		png(f'{PA}/textures/block/alchemy/oil_liquid_{oid}.png',
			['L' * 16 if y % 5 else 'M' * 16 for y in range(16)], {'L': c, 'M': shade(c, 1.2)})
		for fill in range(1, 5):
			write(f'{PA}/models/block/alchemy/oil_pot_{oid}_{fill}.json', {
				'textures': {'particle': f'jaymospowers:block/alchemy/oil_liquid_{oid}', 'o': f'jaymospowers:block/alchemy/oil_liquid_{oid}'},
				'elements': [{'from': [4, 1, 4], 'to': [12, 1 + 2 * fill, 12], 'faces': {
					d: {'uv': [0, 0, 16, 16], 'texture': '#o'} for d in ('down', 'up', 'north', 'south', 'east', 'west')}}]})
			multipart.append({'when': {'oil': oid, 'fill': str(fill)},
				'apply': {'model': f'jaymospowers:block/alchemy/oil_pot_{oid}_{fill}'}})
	write(f'{PA}/blockstates/oil_pot.json', {'multipart': multipart})
	write(f'{PD}/loot_table/blocks/oil_pot.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
		{'type': 'minecraft:item', 'name': 'jaymospowers:oil_pot'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
	write(f'{PD}/recipe/hunter/oil_pot.json', {'type': 'minecraft:crafting_shaped', 'category': 'building',
		'pattern': ['T T', 'TBT', 'TTT'], 'key': {'T': 'minecraft:terracotta', 'B': 'minecraft:glass_bottle'},
		'result': {'id': 'jaymospowers:oil_pot', 'count': 1}})
	write(f'{PD}/recipe/hunter/oil_sash.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment',
		'pattern': ['L  ', ' LB', '  L'], 'key': {'L': 'minecraft:leather', 'B': 'minecraft:glass_bottle'},
		'result': {'id': 'jaymospowers:oil_sash', 'count': 1}})
	write(f'{PD}/recipe/hunter/oil_bandolier.json', {'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
		'ingredients': ['jaymospowers:oil_sash', 'minecraft:leather', 'minecraft:leather', 'minecraft:glass_bottle', 'minecraft:glass_bottle'],
		'result': {'id': 'jaymospowers:oil_bandolier', 'count': 1}})

	# ---- the slick ----
	write(f'{PA}/blockstates/oil_slick.json', {'variants': {'': {'model': 'jaymospowers:block/alchemy/oil_slick'}}})
	write(f'{PA}/models/block/alchemy/oil_slick.json', {
		'textures': {'particle': 'jaymospowers:block/alchemy/oil_slick', 's': 'jaymospowers:block/alchemy/oil_slick'},
		'elements': [{'from': [0, 0, 0], 'to': [16, 0.5, 16], 'faces': {
			'up': {'uv': [0, 0, 16, 16], 'texture': '#s'}, 'down': {'uv': [0, 0, 16, 16], 'texture': '#s'}}}]})
	slick = [
		'................', '....kkkkkk......', '..kkdddddkkk....', '.kddddLdddddkk..', '.kdddddddddddk..', 'kdddLdddddddddk.',
		'kddddddddLddddk.', 'kdddddddddddddk.', '.kddddddddLdddk.', '.kdddLddddddddk.', '..kkddddddddkk..', '...kkdddLdddk...',
		'....kkkddddk....', '......kkkkk.....', '................', '................']
	png(f'{PA}/textures/block/alchemy/oil_slick.png', slick, {'k': (20, 18, 24), 'd': (34, 30, 40), 'L': (70, 64, 84)})

	# ---- effect icons ----
	effect_icon(f'{PA}/textures/mob_effect/snuffed.png', 0x8A7AB0, [(8, 4), (8, 5), (8, 6), (8, 7), (8, 8), (8, 9), (8, 10), (8, 13), (5, 12), (11, 12), (6, 11), (10, 11)])
	effect_icon(f'{PA}/textures/mob_effect/unhealing.png', 0xB09878, [(8, 4), (8, 5), (8, 6), (8, 7), (8, 8), (8, 9), (8, 10), (8, 11), (8, 12), (4, 8), (5, 8), (6, 8), (7, 8), (9, 8), (10, 8), (11, 8), (12, 8), (5, 5), (6, 6), (11, 11), (12, 12), (11, 5), (12, 4), (5, 11), (4, 12)])

	# ---- crucible recipes ----
	grade_names = ['plain', 'fine', 'expert']
	mult = [1.0, 1.5, 2.2]
	base = {
		'dead': ('jaymoskitchen:dead_grease', {'death': 6, 'shadow': 3, 'metal': 2}),
		'beasts': ('jaymoskitchen:beast_grease', {'beast': 6, 'life': 3, 'metal': 2}),
		'brutes': ('jaymoskitchen:brute_grease', {'war': 6, 'earth': 3, 'metal': 2}),
		'people': ('jaymoskitchen:folk_grease', {'life': 5, 'mind': 3, 'metal': 2}),
		'poison': ('jaymoskitchen:beast_grease', {'venom': 6, 'life': 2, 'salt': 2}),
		'haze': ('jaymoskitchen:dead_grease', {'mind': 5, 'air': 4, 'shadow': 2}),
		'mark': ('jaymoskitchen:folk_grease', {'light': 5, 'mind': 4, 'spark': 2}),
		'toxin': ('jaymoskitchen:brute_grease', {'venom': 6, 'rot': 3, 'sulphur': 2}),
		'smoke': ('jaymoskitchen:dead_grease', {'air': 5, 'shadow': 4, 'fire': 2}),
		'acid': ('jaymoskitchen:brute_grease', {'venom': 5, 'sulphur': 4, 'salt': 3}),
		'fire': ('jaymoskitchen:beast_grease', {'fire': 6, 'sulphur': 4, 'spark': 2}),
		'mending': ('jaymoskitchen:folk_grease', {'life': 5, 'metal': 4, 'earth': 3}),
		'fire_ward': ('jaymoskitchen:beast_grease', {'frost': 5, 'water': 4, 'life': 2}),
		'healing': ('jaymoskitchen:folk_grease', {'life': 7, 'light': 3, 'water': 3}),
	}
	for oid, (catalyst, ess) in base.items():
		for g in range(3):
			name = f'oil_{oid}' + ('' if g == 0 else '_' + grade_names[g])
			write(f'{PD}/alchemy/crucible/{name}.json', {
				'catalyst': catalyst, 'essences': {k: max(1, round(v * mult[g])) for k, v in ess.items()},
				'result': {'item': f'jaymospowers:oil_{oid}', 'count': 1, 'grade': grade_names[g]},
				'research': 'hunters_oils'})

	# ---- bomb recipes ----
	bomb_in = {
		'fire_burst': ['minecraft:blaze_powder'], 'stun_flash': ['minecraft:glowstone_dust'], 'fire_spread': ['minecraft:magma_cream'],
		'poison_cloud': ['minecraft:spider_eye'], 'volatile_gas': ['jaymoskitchen:gas_bladder'], 'freeze_blast': ['minecraft:packed_ice'],
		'snuffer': ['minecraft:amethyst_shard', 'minecraft:ender_pearl'], 'stopper_dust': ['minecraft:fermented_spider_eye']}
	for bid, extra in bomb_in.items():
		write(f'{PD}/recipe/hunter/bomb_{bid}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
			'ingredients': ['minecraft:glass_bottle', 'minecraft:gunpowder'] + extra,
			'result': {'id': f'jaymospowers:bomb_{bid}', 'count': 1}})

	# ---- tags ----
	write(f'{PD}/tags/block/burst_breaks.json', {'values': ['minecraft:cobweb', {'id': 'jaymoskingdoms:nest', 'required': False}]})
	write(f'{PD}/tags/block/acid_proof.json', {'values': ['minecraft:bedrock', 'minecraft:obsidian', 'minecraft:crying_obsidian',
		'minecraft:reinforced_deepslate', 'minecraft:end_portal_frame', 'minecraft:spawner', 'minecraft:vault', 'minecraft:trial_spawner']})

	# ---- Kitchen: fat, grease ----
	for fid, col in FATS:
		for kind, rows in (('fat', BLOB), ('grease', POT)):
			name = f'{fid}_{kind}'
			item_files(KA, 'jaymoskitchen', name, '', '')
			lump_png(f'{KA}/textures/item/{name}.png', rows, col if kind == 'fat' else col)
		fat = f'jaymoskitchen:{fid}_fat'
		grease = f'jaymoskitchen:{fid}_grease'
		write(f'{KD}/recipe/{fid}_grease_from_campfire_cooking.json', {'type': 'minecraft:campfire_cooking', 'category': 'misc',
			'cookingtime': 600, 'experience': 0.1, 'ingredient': fat, 'result': {'id': grease}})
		write(f'{KD}/recipe/{fid}_grease_from_smelting.json', {'type': 'minecraft:smelting', 'category': 'misc',
			'cookingtime': 200, 'experience': 0.1, 'ingredient': fat, 'result': {'id': grease}})
		write(f'{KD}/recipe/{fid}_grease_from_cooking_pot.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
			'ingredients': ['jaymoskitchen:cooking_pot', fat, fat], 'result': {'id': grease, 'count': 2}})
	write(f'{KD}/tags/entity_type/no_fat.json', {'values': ['minecraft:villager', 'minecraft:wandering_trader', 'minecraft:player',
		{'id': 'mca:male_villager', 'required': False}, {'id': 'mca:female_villager', 'required': False}]})


main()
