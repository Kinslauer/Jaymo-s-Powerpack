package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.LockOnRegions;
import com.jaymospowers.core.LockOnRegions.Region;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.bettercombat.api.WeaponAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-14): "tools like axes, should only do the better combat swinging when locked on, and with weapon forms
 * should be animated more accurately across the board for example, doing thrusts and slashes to the body with swords,
 * but horizontal cuts and stabs to the head, vertical and horizontal slashes to the arms, never a vertical slash to the
 * torso, as that is less likely to hit its target."
 *
 * <p><b>Tools</b> ({@link #tool}): an axe, pick, shovel or hoe made for work, not war, swings Better Combat's way only
 * while you're locked on; otherwise it's the plain swing (the client's {@code ToolSwingGateMixin}). Tools made for war
 * (tag {@code jaymospowers:war_tools}: the Armory's war axes and hatchets, Tinkers' broad and minotaur axes, war pick,
 * kama and scythe) are weapons and always swing.
 *
 * <p><b>Region swings</b> ({@link #fit}): while you're locked on, the swings your weapon picks from are the ones that
 * suit the body region the scroll wheel chose. Each of Better Combat's attacks is a {@link Motion} (read from its
 * animation's name); each {@link Family} of weapon has the motions it can make and, per region, the ones that land
 * there ({@link #allowed}). Your weapon's own attacks that fit stay, in its order; a fitting motion it can make but has
 * no attack for is added, drawn from its first attack (same damage, sound and wind-up) with a stock animation. With
 * nothing fitting at all, the weapon swings as it always has. Weapon Forms still trims what's left to the basic two for
 * those who haven't learnt them (Kingdoms' {@code AttackSelectionMixin} runs after this one). Both sides pick the same,
 * from the same lock and region. The chain axe is left out: it has its own overhead chop.
 */
public final class RegionSwings {
	/** Tools made for war: these always swing Better Combat's way. */
	public static final TagKey<Item> WAR_TOOLS = TagKey.create(Registries.ITEM, JaymosPowers.id("war_tools"));
	/** Weapons whose swing is their own (the chain axe's overhead chop): never re-picked by region. */
	static final Set<String> OWN_SWING = Set.of("jaymospowers:chain_axe");

	/** How a swing travels. */
	public enum Motion {
		/** A side cut, left or right (and a dual-wielder's crossing cut). */
		HORIZONTAL,
		/** Up or down: a slam, an overhead chop, an uppercut, a vertical slash. */
		VERTICAL,
		/** A thrust, stab or punch. */
		THRUST,
		/** A flourish that sweeps round wide (our twirls, whirls and spins). */
		SWEEP
	}

	/** Kinds of weapon, by what they can do with an edge, a point or a weight. */
	public enum Family {
		/** Swords, sabres, katanas, longswords, claymores, rapiers, twin blades: cut every way and thrust. */
		BLADE(EnumSet.allOf(Motion.class)),
		/** Daggers, knives, claws, sickles: cut sideways and stab; too short for a real overhead cut. */
		DAGGER(EnumSet.of(Motion.HORIZONTAL, Motion.THRUST, Motion.SWEEP)),
		/** Axes, hatchets, war axes, picks: chop down or across; no point to thrust with. */
		AXE(EnumSet.of(Motion.HORIZONTAL, Motion.VERTICAL, Motion.SWEEP)),
		/** Maces, hammers, clubs, anchors: swing across or smash down. */
		BLUNT(EnumSet.of(Motion.HORIZONTAL, Motion.VERTICAL, Motion.SWEEP)),
		/** Quarterstaffs and battle staves: swing, smash, and jab with an end. */
		STAFF(EnumSet.allOf(Motion.class)),
		/** Spears, lances, tridents: thrust, or sweep the haft across. */
		SPEAR(EnumSet.of(Motion.THRUST, Motion.HORIZONTAL, Motion.SWEEP)),
		/** Glaives, halberds, scythes: a blade on a pole, cutting across or down. */
		POLEARM(EnumSet.of(Motion.HORIZONTAL, Motion.VERTICAL, Motion.SWEEP));

		final Set<Motion> can;

		Family(Set<Motion> can) {
			this.can = can;
		}

		/** The family of Better Combat's weapon {@code category}, or null for anything we don't re-pick (bows, fists, wands). */
		public static @Nullable Family of(@Nullable String category) {
			if (category == null) {
				return null;
			}
			return switch (category.toLowerCase(Locale.ROOT)) {
				case "sword", "cutlass", "katana", "claymore", "rapier", "twin_blade", "coral_blade" -> BLADE;
				case "dagger", "soul_knife", "claw", "sickle" -> DAGGER;
				case "axe", "heavy_axe", "double_axe", "pickaxe" -> AXE;
				case "mace", "hammer", "anchor" -> BLUNT;
				case "battlestaff", "staff" -> STAFF;
				case "spear", "lance", "trident" -> SPEAR;
				case "glaive", "halberd", "scythe" -> POLEARM;
				default -> null;
			};
		}
	}

	/**
	 * The motions that land on {@code region} for {@code family}, most fitting first. Andy's sword rules for every edge:
	 * head = side cuts and thrusts; torso = thrusts, side cuts and sweeps, never a vertical; arms = vertical and side cuts;
	 * legs = side cuts low and thrusts. Our own calls on top: axes and blunt weapons chop or smash down onto a leg, and a
	 * blunt weapon's overhead smash goes to the head as well as its side swing; a spear only thrusts at head and body and
	 * sweeps its haft at arms and legs; the Great Ape's tail is cut across or chopped down on.
	 */
	public static Set<Motion> allowed(Family family, Region region) {
		boolean chops = family == Family.AXE || family == Family.BLUNT;
		return switch (region) {
			case HEAD -> family == Family.SPEAR ? EnumSet.of(Motion.THRUST)
				: family == Family.BLUNT ? EnumSet.of(Motion.HORIZONTAL, Motion.VERTICAL)
				: EnumSet.of(Motion.HORIZONTAL, Motion.THRUST);
			case TORSO -> family == Family.SPEAR ? EnumSet.of(Motion.THRUST) : EnumSet.of(Motion.THRUST, Motion.HORIZONTAL, Motion.SWEEP);
			case ARMS -> family == Family.SPEAR ? EnumSet.of(Motion.THRUST, Motion.HORIZONTAL) : EnumSet.of(Motion.VERTICAL, Motion.HORIZONTAL);
			case LEGS -> chops ? EnumSet.of(Motion.HORIZONTAL, Motion.VERTICAL) : EnumSet.of(Motion.HORIZONTAL, Motion.THRUST);
			case TAIL -> family == Family.SPEAR ? EnumSet.of(Motion.HORIZONTAL, Motion.THRUST) : EnumSet.of(Motion.HORIZONTAL, Motion.VERTICAL);
		};
	}

	/** The motion of a Better Combat animation, read from its name (an unknown one counts as a side cut). */
	public static Motion motion(@Nullable String animation) {
		String name = animation == null ? "" : animation.substring(animation.indexOf(':') + 1).toLowerCase(Locale.ROOT);
		if (name.contains("stab") || name.contains("punch") || name.contains("thrust")) {
			return Motion.THRUST;
		}
		if (name.contains("slam") || name.contains("vertical") || name.contains("uppercut") || name.contains("chop")
			|| name.contains("overhead")) {
			return Motion.VERTICAL;
		}
		if (name.contains("twirl") || name.contains("whirl") || name.contains("spin") || name.contains("flip")) {
			return Motion.SWEEP;
		}
		return Motion.HORIZONTAL;
	}

	/** The stock animation for a missing motion: one- or two-handed, and a two-handed chopper slams rather than slashes. */
	static @Nullable String stock(Motion motion, Family family, boolean twoHanded) {
		return switch (motion) {
			case HORIZONTAL -> twoHanded ? "bettercombat:two_handed_slash_horizontal_right" : "bettercombat:one_handed_slash_horizontal_right";
			case VERTICAL -> !twoHanded ? "bettercombat:one_handed_slam"
				: family == Family.AXE || family == Family.BLUNT ? "bettercombat:two_handed_slam" : "bettercombat:two_handed_slash_vertical_right";
			case THRUST -> twoHanded ? "bettercombat:two_handed_stab_right" : "bettercombat:one_handed_stab";
			case SWEEP -> null; // flourishes are the weapon's own or none
		};
	}

	/** Made-up attacks, per weapon attack they're drawn from, per animation: built once, the same object every pick. */
	private static final Map<WeaponAttributes.Attack, Map<String, WeaponAttributes.Attack>> MADE = Collections.synchronizedMap(new WeakHashMap<>());

	private RegionSwings() {
	}

	/** A tool made for work (in the axes, pickaxes, shovels or hoes tag, not {@link #WAR_TOOLS}). */
	public static boolean tool(ItemStack stack) {
		return !stack.isEmpty() && !stack.is(WAR_TOOLS)
			&& (stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES));
	}

	/** Whether {@code player} is locked onto something, on whichever side this runs. */
	public static boolean lockedOn(Player player) {
		return LockOn.target(player) != null;
	}

	/**
	 * The attacks {@code player} picks from with {@code weapon}'s {@code attacks} (already cut down by their conditions):
	 * the ones suiting the locked region, or {@code attacks} as they were when not locked on or nothing fits.
	 */
	public static WeaponAttributes.Attack[] fit(WeaponAttributes.Attack[] attacks, @Nullable WeaponAttributes weapon, @Nullable Player player,
		boolean offHand) {
		if (player == null || weapon == null || attacks.length == 0) {
			return attacks;
		}
		LivingEntity target = LockOn.target(player);
		if (target == null) {
			return attacks;
		}
		ItemStack held = offHand ? player.getOffhandItem() : player.getMainHandItem();
		if (!held.isEmpty() && OWN_SWING.contains(BuiltInRegistries.ITEM.getKey(held.getItem()).toString())) {
			return attacks;
		}
		Family family = Family.of(weapon.category());
		if (family == null) {
			return attacks;
		}
		Region region = LockOnRegions.fit(target, LockOn.region(player));
		Set<Motion> allowed = allowed(family, region);
		List<WeaponAttributes.Attack> out = new ArrayList<>(attacks.length + 2);
		Set<Motion> have = EnumSet.noneOf(Motion.class);
		for (WeaponAttributes.Attack attack : attacks) {
			Motion motion = motion(attack.animation());
			if (allowed.contains(motion)) {
				out.add(attack);
				have.add(motion);
			}
		}
		for (Motion motion : allowed) {
			if (!have.contains(motion) && family.can.contains(motion)) {
				String animation = stock(motion, family, weapon.isTwoHanded());
				if (animation != null) {
					out.add(made(attacks[0], animation, motion));
				}
			}
		}
		return out.isEmpty() ? attacks : out.toArray(WeaponAttributes.Attack[]::new);
	}

	/** {@code base} swung with {@code animation} instead, its hitbox shaped for the motion. */
	private static WeaponAttributes.Attack made(WeaponAttributes.Attack base, String animation, Motion motion) {
		return MADE.computeIfAbsent(base, b -> Collections.synchronizedMap(new HashMap<>())).computeIfAbsent(animation, name -> {
			WeaponAttributes.HitBoxShape shape = switch (motion) {
				case THRUST -> WeaponAttributes.HitBoxShape.FORWARD_BOX;
				case VERTICAL -> WeaponAttributes.HitBoxShape.VERTICAL_PLANE;
				default -> WeaponAttributes.HitBoxShape.HORIZONTAL_PLANE;
			};
			double angle = motion == Motion.THRUST ? 0.0 : base.angle() > 0.0 ? Math.min(base.angle(), 120.0) : 90.0;
			return new WeaponAttributes.Attack(base.conditions(), shape, base.damageMultiplier(), base.movementSpeedMultiplier(),
				base.rangeMultiplier(), angle, base.upswing(), name, base.swingSound(), base.impactSound(), null);
		});
	}
}
