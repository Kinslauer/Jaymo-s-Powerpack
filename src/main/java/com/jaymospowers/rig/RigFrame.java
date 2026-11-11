package com.jaymospowers.rig;

import java.util.Locale;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The builds a Rig comes in, picked at the Rig Forge. The frame is stamped on each piece when it is built
 * ({@link Rig#FRAME}); the worn suit takes the Core's. The standard frame is the suit as it always was.
 * <ul>
 * <li><b>Scrap:</b> crude and heavy, cheap, the first one anyone can afford. Slow on the ground and thirsty in the
 * air, its shots a little weak.</li>
 * <li><b>Shade:</b> dark and quiet, thin plates. Mobs notice you late ({@link #detect}); quick, but it takes
 * more to the chest.</li>
 * <li><b>Siege:</b> thick plates and a shoulder gun (more, harder missiles from the launcher). Slow in the air.</li>
 * </ul>
 * Every number is per frame, in the constructor's order: armour points and toughness (whole suit; fewer worn pieces,
 * a share), ground speed (a share of walking speed), air speed (a share of hover and flight speed), hover drain,
 * shot damage, extra launcher missiles, the share of normal mob sight range, the Forge price in salvage and in iron.
 */
public enum RigFrame {
	STANDARD(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0, 1.0, 1.0, 1.0),
	SCRAP(1.0, 0.0, -0.06, 0.85, 1.4, 0.85, 0, 1.0, 0.5, 0.5),
	SHADE(-2.0, 0.0, 0.04, 1.1, 0.9, 1.0, 0, 0.45, 1.25, 1.0),
	SIEGE(4.0, 2.0, -0.05, 0.7, 1.2, 1.0, 2, 1.0, 1.5, 1.75);

	/** A Shade suit sneaking is harder to see still (a share of {@link #detect}). */
	public static final double SNEAK_SHARE = 0.6;
	/** The Siege shoulder gun's missiles burst this much bigger. */
	public static final float GUN_BURST = 0.5F;
	/** The least a build can cost in salvage points. */
	public static final int MIN_POINTS = 2;

	public final double armor, toughness, speed;
	public final float airSpeed, hoverDrain, shotDamage;
	public final int extraMissiles;
	public final double detect;
	private final float pointsShare, ironShare;

	RigFrame(double armor, double toughness, double speed, double airSpeed, double hoverDrain, double shotDamage, int extraMissiles, double detect,
		double pointsShare, double ironShare) {
		this.armor = armor;
		this.toughness = toughness;
		this.speed = speed;
		this.airSpeed = (float)airSpeed;
		this.hoverDrain = (float)hoverDrain;
		this.shotDamage = (float)shotDamage;
		this.extraMissiles = extraMissiles;
		this.detect = detect;
		this.pointsShare = (float)pointsShare;
		this.ironShare = (float)ironShare;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public RigFrame next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public RigFrame previous() {
		return values()[(ordinal() + values().length - 1) % values().length];
	}

	/** What this frame charges in salvage points for a piece that costs {@code base} in the standard one. */
	public int points(int base) {
		return Math.max(MIN_POINTS, Math.round(base * this.pointsShare));
	}

	/** What this frame charges in the plain material (iron) for a piece that costs {@code base} in the standard one. */
	public int iron(int base) {
		return Math.max(1, Math.round(base * this.ironShare));
	}

	public static RigFrame byId(@Nullable String id) {
		if (id != null) {
			for (RigFrame frame : values()) {
				if (frame.id().equals(id)) {
					return frame;
				}
			}
		}
		return STANDARD;
	}

	/** The frame stamped on a piece (standard for none). */
	public static RigFrame of(ItemStack piece) {
		return piece.isEmpty() || Rig.FRAME == null ? STANDARD : byId(piece.get(Rig.FRAME));
	}

	/** The frame of the suit this player wears: the Core's, else the first piece worn with one. */
	public static RigFrame of(Player player) {
		ItemStack core = Rig.core(player);
		if (!core.isEmpty()) {
			return of(core);
		}
		for (Rig.Piece piece : Rig.Piece.values()) {
			if (Rig.wears(player, piece)) {
				return of(player.getItemBySlot(piece.slot));
			}
		}
		return STANDARD;
	}
}
