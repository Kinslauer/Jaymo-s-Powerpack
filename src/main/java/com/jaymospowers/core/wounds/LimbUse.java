package com.jaymospowers.core.wounds;

import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.136) Andy (2026-10-23): "breaking a limb should hinder all activities associated with it, for example a broken
 * left arm hurts that arms ability to shoot webs accurately or glide, or flight becomes more difficult for those who can,
 * and completely disable the applicable abilities if dismembered." Every ability that's done with a part of the body
 * says which ({@link #need}); the rest (a thought, a voice, the breath, an aura, a rift) never care about limbs.
 * <ul>
 * <li><b>A broken limb</b> works at {@link #BROKEN} of its strength ({@link #SPLINTED} in a splint, {@link #NUMBED} with
 * the pain dulled). What it does goes wide: aimed abilities are thrown off by up to {@link #AIM_SPREAD} degrees
 * ({@link #aimed}), a held stream sways ({@link #sway}).</li>
 * <li><b>A severed limb</b> ({@link com.jaymospowers.core.Dismember}) is 0: the ability won't start at all (the key says why, {@link #refuse}).</li>
 * <li><b>Gliding</b> (web wings, an aercloud glider) is both arms ({@link #BOTH_ARMS}): broken, it sinks and pulls to the
 * weak side; one gone, it won't open.</li>
 * <li><b>Flying</b> (Wind, ki, vapour, the Rig, an elytra) is the whole body ({@link #flight}): every broken or missing
 * limb makes it wobblier, slower and lower, but never grounds you (the power carries you, not your limbs).</li>
 * </ul>
 * Both sides: the breaks and the severed parts are synced to everyone, the dulled pain to its owner.
 */
public final class LimbUse {
	/** What an ability is done with (bits, combined: a zip and a kick is {@code MAIN_ARM | LEGS}). */
	public static final int NONE = 0, MAIN_ARM = 1, OFF_ARM = 2, LEFT_ARM = 4, RIGHT_ARM = 8, ANY_ARM = 16, BOTH_ARMS = 32, LEGS = 64;

	/** A limb's strength: sound, broken, broken but splinted, broken with the pain dulled; severed is 0. */
	public static final float SOUND = 1.0F, BROKEN = 0.4F, SPLINTED = 0.85F, NUMBED = 0.7F;
	/** A sprained foot, for what's done with the legs. */
	public static final float SPRAINED = 0.7F;
	/** A severed limb's weight in {@link #flight}: worse than broken, but the power still carries you. */
	public static final float FLIGHT_MISSING = 0.25F;
	/** The most an aimed ability is thrown off (degrees), at no strength left; a broken arm is {@code (1 - BROKEN)} of it. */
	public static final float AIM_SPREAD = 20.0F;
	/** A held stream's sway (degrees, at no strength left) and how fast it sways (radians a tick). */
	public static final float SWAY = 14.0F, SWAY_RATE = 0.23F;

	private LimbUse() {
	}

	// ---- which limbs each ability uses ------------------------------------------------------------------------------------

	/** The slot names, as the powers screen and {@link Power#slotUnlocked} name them. */
	public static String slot(PowerKey key) {
		return key.id();
	}

	/**
	 * What {@code power}'s {@code slot} ({@code ability_1} to {@code ability_4}, {@code mode}, {@code ultimate},
	 * {@code special}) is done with. Starters share ability 1's key and its limbs. The one table for every power: change
	 * a mapping here.
	 */
	public static int need(@Nullable Power power, Player player, String slot) {
		if (power == null) {
			return NONE;
		}
		return switch (power.id().getPath()) {
			// reaching into a rift for a weapon (or handing one back) takes a hand; the rifts themselves are the power's
			case "arsenal" -> switch (slot) {
				case "ability_1", "ability_2" -> ANY_ARM;
				default -> NONE;
			};
			// Overclock is running; the Chrono Punch is fists
			case "chrono" -> switch (slot) {
				case "ability_3" -> LEGS;
				case "ultimate" -> ANY_ARM;
				default -> NONE;
			};
			// the Ice Slide skates; the shards are thrown from the hand; breath, the freeze, the wall and the storm are the cold's
			case "cryo" -> switch (slot) {
				case "ability_1" -> LEGS;
				case "ability_3" -> MAIN_ARM;
				default -> NONE;
			};
			// every cut and strike is the blade (or fist) in the main hand; Undertow and Black Tide are the water's
			case "darktide" -> switch (slot) {
				case "ability_1", "ability_2", "special", "ultimate" -> MAIN_ARM;
				default -> NONE;
			};
			// Lift is the pointing hand; the Well, the board, the Wave, the Field and the Meteor are the pull itself
			case "gravity" -> "ability_1".equals(slot) ? MAIN_ARM : NONE;
			// the Smoke Bomb is thrown; the rest is smoke (and Vapour Form needs no legs: they're smoke)
			case "smoke" -> "ability_3".equals(slot) ? MAIN_ARM : NONE;
			// the webs come off the wrists: the main hand's, the off hand's (G); Webbed Fists any fist; Web Strike zips and kicks
			case "web" -> switch (slot) {
				case "ability_1", "ability_3", "ability_4", "mode" -> MAIN_ARM;
				case "ability_2" -> OFF_ARM;
				case "special" -> ANY_ARM;
				case "ultimate" -> MAIN_ARM | LEGS;
				default -> NONE;
			};
			// the band is on the left wrist: dialling and scanning need it (an alien's body is its own)
			case "xenoform" -> !com.jaymospowers.power.xenoform.XenoformState.transformed(player) && ("mode".equals(slot) || "special".equals(slot))
				? LEFT_ARM : NONE;
			// ki blasts and techniques leave the hands; charging, Flash Step, flight and forms are ki
			case "zfighter" -> switch (slot) {
				case "ability_2", "ability_3" -> ANY_ARM;
				default -> NONE;
			};
			// resonance (sound) and wind (the air) need no limbs; Wind's Tempest Kick asks for legs itself (WindKick)
			default -> NONE;
		};
	}

	// ---- strengths --------------------------------------------------------------------------------------------------------

	/** One part's strength: 0 severed, {@link #BROKEN} (or splinted, or numbed) broken, else {@link #SOUND}. */
	public static float strength(LivingEntity entity, BodyPart part) {
		if (Breaks.missing(entity, part)) {
			// (0.18.82) a wooden or iron limb stands in for it, weaker (0 when there's none)
			return com.jaymospowers.core.Prosthetics.strength(entity, Breaks.limb(part));
		}
		if (!Breaks.isBroken(entity, part)) {
			return SOUND;
		}
		if ((Breaks.splinted(entity) & Breaks.bit(part)) != 0) {
			return SPLINTED;
		}
		if (part == BodyPart.LEFT_FOOT || part == BodyPart.RIGHT_FOOT) {
			return SPRAINED;
		}
		return numb(entity) ? NUMBED : BROKEN;
	}

	/** The arm on the main hand's side (or the off hand's). */
	public static BodyPart arm(LivingEntity entity, boolean main) {
		boolean right = (entity.getMainArm() == HumanoidArm.RIGHT) == main;
		return right ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
	}

	/** How well {@code entity} can do something needing {@code need}: the weakest part it needs (1 for nothing). */
	public static float strength(LivingEntity entity, int need) {
		if (need == NONE) {
			return SOUND;
		}
		float left = strength(entity, BodyPart.LEFT_ARM), right = strength(entity, BodyPart.RIGHT_ARM);
		boolean mainRight = entity.getMainArm() == HumanoidArm.RIGHT;
		float s = SOUND;
		if ((need & MAIN_ARM) != 0) {
			s = Math.min(s, mainRight ? right : left);
		}
		if ((need & OFF_ARM) != 0) {
			s = Math.min(s, mainRight ? left : right);
		}
		if ((need & LEFT_ARM) != 0) {
			s = Math.min(s, left);
		}
		if ((need & RIGHT_ARM) != 0) {
			s = Math.min(s, right);
		}
		if ((need & ANY_ARM) != 0) {
			s = Math.min(s, Math.max(left, right));
		}
		if ((need & BOTH_ARMS) != 0) {
			s = Math.min(s, Math.min(left, right));
		}
		if ((need & LEGS) != 0) {
			s = Math.min(s, Math.min(legs(entity, true), legs(entity, false)));
		}
		return s;
	}

	/** A leg's strength with its foot's. */
	private static float legs(LivingEntity entity, boolean left) {
		float leg = strength(entity, left ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG);
		return leg == 0.0F ? 0.0F : Math.min(leg, strength(entity, left ? BodyPart.LEFT_FOOT : BodyPart.RIGHT_FOOT));
	}

	/** Whether something needing {@code need} can be done at all (nothing it needs is severed). */
	public static boolean usable(LivingEntity entity, int need) {
		return strength(entity, need) > 0.0F;
	}

	/** Whether it can be done, but worse (something it needs is broken or sprained). */
	public static boolean hindered(LivingEntity entity, int need) {
		float s = strength(entity, need);
		return s > 0.0F && s < SOUND;
	}

	/**
	 * Flying's strength, the whole body's: each limb counts a quarter, a severed one {@link #FLIGHT_MISSING}.
	 * {@code legs}: false where the legs aren't flying (Smoke's Vapour Form, where they're smoke).
	 */
	public static float flight(LivingEntity entity, boolean legs) {
		float sum = flightLimb(entity, BodyPart.LEFT_ARM) + flightLimb(entity, BodyPart.RIGHT_ARM);
		if (!legs) {
			return sum / 2.0F;
		}
		return (sum + flightLimb(entity, BodyPart.LEFT_LEG) + flightLimb(entity, BodyPart.RIGHT_LEG)) / 4.0F;
	}

	private static float flightLimb(LivingEntity entity, BodyPart part) {
		float s = strength(entity, part);
		return s == 0.0F ? FLIGHT_MISSING : s;
	}

	/** Whether the pain is dulled: the server's own reckoning, or (on the owner's client) its synced flag. */
	static boolean numb(LivingEntity entity) {
		if (entity.level().isClientSide()) {
			return Boolean.TRUE.equals(entity.getAttached(Pain.DULLED));
		}
		return Pain.numb(entity);
	}

	// ---- refusing, and going wide -----------------------------------------------------------------------------------------

	/** The part that keeps {@code need} from being done (the first severed one it needs), or null if it can be. */
	public static @Nullable BodyPart gone(LivingEntity entity, int need) {
		if (usable(entity, need)) {
			return null;
		}
		BodyPart main = arm(entity, true), off = arm(entity, false);
		BodyPart[] order = {main, off, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG};
		for (BodyPart part : order) {
			int alone = part == main ? MAIN_ARM : part == off ? OFF_ARM : LEGS;
			if ((need & alone) != 0 && Breaks.missing(entity, part)) {
				return part;
			}
		}
		if ((need & LEFT_ARM) != 0 && Breaks.missing(entity, BodyPart.LEFT_ARM)) {
			return BodyPart.LEFT_ARM;
		}
		if ((need & RIGHT_ARM) != 0 && Breaks.missing(entity, BodyPart.RIGHT_ARM)) {
			return BodyPart.RIGHT_ARM;
		}
		if ((need & BOTH_ARMS) != 0) {
			return Breaks.missing(entity, main) ? main : off;
		}
		// (ANY_ARM: both are gone; LEGS: a leg is)
		if ((need & LEGS) != 0) {
			return Breaks.missing(entity, BodyPart.LEFT_LEG) ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
		}
		return main;
	}

	/** Says on the action bar why {@code need} can't be done (a severed part). */
	public static void refuse(Player player, int need) {
		BodyPart part = gone(player, need);
		if (part == null) {
			return;
		}
		boolean bothArms = (need & ANY_ARM) != 0 && Breaks.missing(player, BodyPart.LEFT_ARM) && Breaks.missing(player, BodyPart.RIGHT_ARM);
		player.sendOverlayMessage((bothArms ? Component.translatable("message.jaymospowers.limbs.no_arms")
			: Component.translatable("message.jaymospowers.limbs.gone", part.displayName())).withStyle(ChatFormatting.RED));
	}

	/** How far an aimed ability needing {@code need} may be thrown off (degrees; 0 when everything it needs is sound). */
	public static float spread(LivingEntity entity, int need) {
		float s = strength(entity, need);
		return s <= 0.0F || s >= SOUND ? 0.0F : AIM_SPREAD * (1.0F - s);
	}

	/**
	 * Runs {@code action} (an ability going off) with {@code player}'s aim thrown off by the limbs it uses, then puts the
	 * aim back: whatever the ability reads of the look (rays, shots, swings, zips) goes wide, and the view never moves.
	 */
	public static void aimed(Player player, int need, Runnable action) {
		float spread = spread(player, need);
		if (spread <= 0.0F) {
			action.run();
			return;
		}
		float yRot = player.getYRot(), xRot = player.getXRot(), head = player.getYHeadRot();
		float dy = (player.getRandom().nextFloat() * 2.0F - 1.0F) * spread;
		float dx = (player.getRandom().nextFloat() * 2.0F - 1.0F) * spread;
		try {
			player.setYRot(yRot + dy);
			player.setYHeadRot(head + dy);
			player.setXRot(Mth.clamp(xRot + dx, -90.0F, 90.0F));
			action.run();
		} finally {
			player.setYRot(yRot);
			player.setYHeadRot(head);
			player.setXRot(xRot);
		}
	}

	/** A held stream's direction, swaying with the limbs it uses (smoothly, with the game time; unchanged when sound). */
	public static Vec3 sway(LivingEntity entity, int need, Vec3 dir) {
		float s = strength(entity, need);
		if (s <= 0.0F || s >= SOUND) {
			return dir;
		}
		float t = entity.level().getGameTime() + entity.getId() * 7;
		float amount = SWAY * (1.0F - s) * Mth.DEG_TO_RAD;
		float yaw = amount * (Mth.sin(t * SWAY_RATE) + 0.5F * Mth.sin(t * SWAY_RATE * 2.7F + 1.1F)) / 1.5F;
		float pitch = amount * 0.6F * Mth.sin(t * SWAY_RATE * 1.6F + 0.4F);
		return dir.yRot(yaw).xRot(pitch);
	}
}
