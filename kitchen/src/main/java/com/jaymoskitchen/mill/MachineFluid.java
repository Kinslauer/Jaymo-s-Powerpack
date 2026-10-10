package com.jaymoskitchen.mill;

/** What a press's tank holds. */
public enum MachineFluid {
	NONE, WATER, OIL;

	public static MachineFluid byName(String name) {
		for (MachineFluid fluid : values()) {
			if (fluid.name().equals(name)) {
				return fluid;
			}
		}
		return NONE;
	}
}
