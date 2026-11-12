#!/usr/bin/env python3
"""Data mistakes that stop the server loading at all (26.3 refuses the whole datapack load for one bad value):
- a crafting/cooking recipe with a category the recipe book doesn't have ("tools" did, 2026-11-11);
- a recipe, loot table or advancement naming one of our items that doesn't exist (jaymospowers:zanite_sword did);
- a loot table or predicate using a condition or function name 26.3 doesn't have (block_state_property, now match_block,
  did). The lists below are 26.3's own (LootItemConditionTypes, LootPredicates, LootItemFunctions, LootPoolEntries).
An item exists if it has assets/<mod>/items/<id>.json or blockstates/<id>.json, or its id appears as a string in the
mod's Java (items registered without a model file). Run by the PR checks workflow; exits 1 on any finding."""
import glob, json, os, re, sys

MODS = {'jaymospowers': 'src', 'jaymoskingdoms': 'kingdoms/src', 'jaymoskitchen': 'kitchen/src'}
CRAFT = {'building', 'redstone', 'equipment', 'misc'}
COOK = {'food', 'blocks', 'misc'}
LOOT_NAMES = set("""all_of any_of block/fast_cooking damage_source_properties enchantment_active_check entity_properties entity_scores environment_attribute_check float_value_check int_value_check inverted killed_by_player location_check match_block match_tool random_chance random_chance_with_enchanted_bonus survives_explosion table_bonus time_check tool/can_shear tool/can_silk_touch weather_check
apply_bonus copy_components copy_custom_data copy_name copy_state discard enchant_randomly enchant_with_levels enchanted_count_increase exploration_map explosion_decay fill_player_head filtered furnace_smelt limit_count modify_contents sequence set_attributes set_banner_pattern set_book_cover set_components set_contents set_count set_custom_data set_custom_model_data set_damage set_enchantments set_firework_explosion set_fireworks set_instrument set_item set_loot_table set_lore set_name set_ominous_bottle_amplifier set_potion set_random_dyes set_random_potion set_stew_effect set_writable_book_pages set_written_book_pages toggle_tooltips
alternatives dynamic empty group item loot_table sequence slots tag
block chest entity fishing gift archaeology equipment shearing vault barter advancement_reward advancement_entity
advancement_location command selector empty generic block_use entity_interact uniform constant binomial score storage
enchantment_level sum list lookup linear levels_squared clamped fraction exponent""".split())
REF = re.compile(r'"(?:item|id|name|result|ingredient|base|addition|template)"\s*:\s*"(jaymos(?:powers|kingdoms|kitchen)):([a-z0-9_/]+)"')


def stem(path):
	return os.path.splitext(os.path.basename(path))[0]


known = {}
for ns, root in MODS.items():
	ids = {stem(p) for p in glob.glob(f'{root}/**/assets/{ns}/items/*.json', recursive=True)}
	ids |= {stem(p) for p in glob.glob(f'{root}/**/assets/{ns}/blockstates/*.json', recursive=True)}
	for p in glob.glob(f'{root}/**/*.java', recursive=True):
		ids |= set(re.findall(r'"([a-z0-9_]+)"', open(p, encoding='utf-8', errors='ignore').read()))
	known[ns] = ids

problems = []


def loot_names(node, f):
	if isinstance(node, dict):
		for k, v in node.items():
			if k in ('condition', 'function', 'type') and isinstance(v, str) and v.startswith('minecraft:') and v[10:] not in LOOT_NAMES:
				problems.append(f'{f}: "{k}": "{v}" isn\'t a 26.3 loot name')
			loot_names(v, f)
	elif isinstance(node, list):
		for x in node:
			loot_names(x, f)

for ns, root in MODS.items():
	for f in glob.glob(f'{root}/main/resources/data/**/*.json', recursive=True):
		if '/tags/' in f:
			continue
		text = open(f, encoding='utf-8').read()
		if any(f'/{sub}/' in f for sub in ('loot_table', 'predicate', 'item_modifier')):
			try:
				loot_names(json.loads(text), f)
			except ValueError as e:
				problems.append(f'{f}: not valid JSON ({e})')
		if '/recipe/' in f:
			try:
				d = json.loads(text)
			except ValueError as e:
				problems.append(f'{f}: not valid JSON ({e})')
				continue
			kind, cat = d.get('type', ''), d.get('category')
			allowed = COOK if any(k in kind for k in ('smelting', 'blasting', 'smoking', 'campfire')) else CRAFT if kind.startswith('minecraft:crafting') else None
			if cat is not None and allowed is not None and cat not in allowed:
				problems.append(f'{f}: category "{cat}" isn\'t one of {sorted(allowed)}')
		for m in REF.finditer(text):
			if m.group(2) not in known[m.group(1)]:
				problems.append(f'{f}: {m.group(1)}:{m.group(2)} isn\'t an item or block of ours')

for p in problems:
	print(p)
print(f'Data references: {len(problems)} problem(s).')
sys.exit(1 if problems else 0)
