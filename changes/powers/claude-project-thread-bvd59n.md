# Sunfang held the right way round, hot silver ingot icon
- **Sunfang**: the 3D model was turned a quarter the wrong way, so in the hand its hooked tip bent back toward the wielder and it looked backwards. It now leans away the way the Blockbench file's own hold has it. The inventory and item frame icon still read grip low left, tip up right (`tools/blockbench/sunfang.py`, regenerated `models/item/sunfang.json`).
- **Hot silver ingot**: its model pointed at `item/tinkering/silver_ingot`, which doesn't exist, so it showed as a purple-black box. It now uses `item/silver/silver_ingot` (`items/hot_ingot.json`).
- Checked in a cloud test game on armour stands, held, dropped and in the inventory; not played in the pack.
