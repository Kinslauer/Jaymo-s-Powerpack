package com.jaymospowers.tinkering;

/** A tool part shaped at the Part Builder: what its material counts as on a tool, and what it costs (Tinkers' costs). */
public enum PartType {
	PICK_HEAD("pick_head", Kind.HEAD, 2),
	SMALL_AXE_HEAD("small_axe_head", Kind.HEAD, 2),
	ADZE_HEAD("adze_head", Kind.HEAD, 2),
	SMALL_BLADE("small_blade", Kind.HEAD, 2),
	HAMMER_HEAD("hammer_head", Kind.HEAD, 8),
	BROAD_BLADE("broad_blade", Kind.HEAD, 8),
	/** (0.15.x) The broad axe's head. */
	BROAD_AXE_HEAD("broad_axe_head", Kind.HEAD, 8),
	LARGE_PLATE("large_plate", Kind.HEAD, 4),
	TOOL_HANDLE("tool_handle", Kind.HANDLE, 1),
	TOUGH_HANDLE("tough_handle", Kind.HANDLE, 3),
	TOOL_BINDING("tool_binding", Kind.BINDING, 1),
	/** (0.15.x) The big tools' binding (scythe, broad axe, excavator, vein hammer). */
	TOUGH_BINDING("tough_binding", Kind.BINDING, 3),
	/** (0.11.77) Swords' guards, for weapon heads ({@link WeaponParts}); no Tinkering tool uses it. */
	CROSSGUARD("crossguard", Kind.HANDLE, 1),
	// (0.15.x) the ranged weapons' parts (docs/Tinkering-Weapons.md): limbs and grips of wood, bone and metal, strings, arrow parts
	BOW_LIMB("bow_limb", Kind.LIMB, 3),
	BOW_GRIP("bow_grip", Kind.GRIP, 3),
	BOWSTRING("bowstring", Kind.BOWSTRING, 1, true),
	ARROW_HEAD("arrow_head", Kind.ARROW_HEAD, 1, true),
	ARROW_SHAFT("arrow_shaft", Kind.ARROW_SHAFT, 1, true),
	FLETCHING("fletching", Kind.FLETCHING, 1, true);

	/** Which of a material's stats a part uses. */
	public enum Kind { HEAD, HANDLE, BINDING, LIMB, GRIP, BOWSTRING, ARROW_HEAD, ARROW_SHAFT, FLETCHING }

	public final String id;
	public final Kind kind;
	/** Material units it takes to shape. */
	public final int cost;
	/** (0.15.x) Always shaped at the Part Builder, even of metal (Tinkers: strings and arrow parts have no cast). */
	public final boolean builderOnly;

	PartType(String id, Kind kind, int cost) {
		this(id, kind, cost, false);
	}

	PartType(String id, Kind kind, int cost, boolean builderOnly) {
		this.id = id;
		this.kind = kind;
		this.cost = cost;
		this.builderOnly = builderOnly;
	}
}
