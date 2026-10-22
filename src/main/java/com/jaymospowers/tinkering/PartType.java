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
	CROSSGUARD("crossguard", Kind.HANDLE, 1);

	/** Which of a material's stats a part uses. */
	public enum Kind { HEAD, HANDLE, BINDING }

	public final String id;
	public final Kind kind;
	/** Material units it takes to shape. */
	public final int cost;

	PartType(String id, Kind kind, int cost) {
		this.id = id;
		this.kind = kind;
		this.cost = cost;
	}
}
