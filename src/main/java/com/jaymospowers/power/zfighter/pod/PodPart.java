package com.jaymospowers.power.zfighter.pod;

import java.util.List;

/**
 * (0.18.12, Andy) The parts a space pod is built from, in the order they go on (and come off in reverse): the keel is
 * the found piece everything hangs on, then the fins, the thruster bell, the fuel tank, the four hull plates, the
 * canopy and the beacon mast. Each part has {@link #count} slots; slot {@code i} of a part is bit {@link #bit(int)} in
 * the pod's masks (see {@code PodEntity}). Mockup: project files art-overhaul/space-travel/pod-mockup.png.
 */
public enum PodPart {
	/** Forged iron frame: never made, only found (raiders, the Maw, the Patrol). Standing alone it is a bare pod. */
	KEEL("keel", 1),
	/** Steel plate landing fins: left, right, back. */
	FIN("fin", 3),
	/** Bronze thruster bell, fired in the kiln. */
	BELL("bell", 1),
	/** Copper fuel tank with the red band; holds the pod's fuel. */
	TANK("tank", 1),
	/** Bone-white riveted hull plates: front (porthole), right (hatch), back, left. */
	PLATE("plate", 4),
	/** The glass canopy dome, an Alchemy infusion. */
	CANOPY("canopy", 1),
	/** The beacon mast on top; the Patrol reads it. */
	MAST("mast", 1);

	public static final List<PodPart> ORDER = List.of(values());
	/** Every slot of every part, as bits. */
	public static final int ALL_SLOTS;

	static {
		int all = 0;
		for (PodPart part : values()) {
			for (int i = 0; i < part.count; i++) {
				all |= part.bit(i);
			}
		}
		ALL_SLOTS = all;
	}

	public final String key;
	public final int count;
	private final int firstBit;

	PodPart(String key, int count) {
		this.key = key;
		this.count = count;
		this.firstBit = Holder.next;
		Holder.next += count;
	}

	private static final class Holder {
		static int next = 0;
	}

	/** The mask bit of this part's slot {@code index} (0 to {@code count - 1}). */
	public int bit(int index) {
		return 1 << (firstBit + index);
	}

	/** Every slot of this part, as bits. */
	public int bits() {
		int bits = 0;
		for (int i = 0; i < count; i++) {
			bits |= bit(i);
		}
		return bits;
	}

	/** The part and slot a mask bit belongs to, or null. */
	public static PodPart ofBit(int bit) {
		for (PodPart part : values()) {
			if ((part.bits() & bit) != 0) {
				return part;
			}
		}
		return null;
	}

	/** The block id's path of this part ({@code pod_keel}, {@code pod_fin}...). */
	public String blockPath() {
		return "pod_" + key;
	}
}
