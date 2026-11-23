# Divinity and Retribution no longer stop the server starting
- The From the Shadows enchantments **Divinity** and **Retribution** were written in the old requirement format
  (`"condition"` and a `"type"` entity predicate), which 26.3 can't read, so the server refused to load its datapacks and
  wouldn't start. They now use 26.3's format, as vanilla Smite does (`"type": "minecraft:entity_properties"`,
  `"minecraft:entity_type"`).
- Checked against the 26.3 server jar's `smite.json`; not yet started on a server with the fix.
