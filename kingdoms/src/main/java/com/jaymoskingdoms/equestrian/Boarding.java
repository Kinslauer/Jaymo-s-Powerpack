package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.dragons.AetherionDragon;
import com.jaymoskingdoms.dragons.BoreasDragon;
import com.jaymoskingdoms.dragons.NotusDragon;
import com.jaymoskingdoms.dragons.WindDragon;
import com.jaymoskingdoms.dragons.ZephyrusDragon;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;

/**
 * How a big mount of ours lets a rider up, asked by Jaymo's Powers' mount climb ({@code MountClimb}, by reflection; without
 * Powers nobody climbs, the rider is simply seated, as before).
 * <ul>
 * <li><b>War elephants and mammoths:</b> stand at its head and it curls its trunk down, takes you up in it and sets you on its
 * back ({@link #STYLE_TRUNK}); anywhere else along its flank, or if you are in heavy armour (it kneels for those anyway,
 * {@link Bonding#KNEEL_ARMOUR}), it kneels and you climb up its folded foreleg ({@link #STYLE_KNEEL}). The kneel is the one the
 * armoured rider's already gets ({@link Bonding#KNEEL}, drawn by (the elephant model in the client) and
 * followed by the saddle). The trunk's curl is drawn from the climb itself, in step with the rider.</li>
 * <li><b>Wind dragons:</b> it lowers its neck to someone who stands at its head ({@link #STYLE_NECK}), and (Notus, Boreas,
 * Zephyrus and Aetherion) its wings to someone at its side or rump ({@link #STYLE_WING}), for them to climb up. The wingless
 * species and the Kintsugi ones are climbed from the side like a horse. One in the air is boarded as before ({@link #STYLE_AIR}).</li>
 * </ul>
 */
public final class Boarding {
	/** What {@link #style} answers. Powers' {@code MountClimb} has the same numbers. */
	public static final int STYLE_NONE = 0, STYLE_KNEEL = 1, STYLE_TRUNK = 2, STYLE_NECK = 3, STYLE_WING = 4, STYLE_AIR = -1;
	/** A rider within this many degrees of straight ahead of the mount's head is "at its head". */
	static final float HEAD_ARC = 50.0F;

	private Boarding() {
	}

	/** (Read by Jaymo's Powers' mount climb) how {@code mount} lets {@code player} up: one of the {@code STYLE_} numbers. */
	public static int style(AbstractHorse mount, Player player) {
		if (mount instanceof WarElephant) {
			if (player.getArmorValue() >= Bonding.KNEEL_ARMOUR || !atHead(mount, player)) {
				// down on its knees for them (the heavy armoured already got it: this refreshes the window)
				Bonding.kneel(mount);
				return STYLE_KNEEL;
			}
			return STYLE_TRUNK;
		}
		if (mount instanceof WindDragon dragon) {
			if (dragon.airborne()) {
				return STYLE_AIR;
			}
			if (atHead(mount, player)) {
				return STYLE_NECK;
			}
			// the species with wings in a model we can bend lower one; the rest (and the Kintsugi kind) are climbed from the side as a horse is
			return dragon instanceof NotusDragon || dragon instanceof BoreasDragon || dragon instanceof ZephyrusDragon
				|| dragon instanceof AetherionDragon ? STYLE_WING : STYLE_NONE;
		}
		return STYLE_NONE;
	}

	/** Whether {@code player} stands in front of {@code mount}, within {@link #HEAD_ARC} of straight ahead of it. */
	static boolean atHead(AbstractHorse mount, Player player) {
		double dx = player.getX() - mount.getX(), dz = player.getZ() - mount.getZ();
		float toPlayer = (float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		return Math.abs(Mth.wrapDegrees(toPlayer - mount.yBodyRot)) < HEAD_ARC;
	}
}
