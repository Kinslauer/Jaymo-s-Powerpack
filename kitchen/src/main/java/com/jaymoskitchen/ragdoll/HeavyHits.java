package com.jaymoskitchen.ragdoll;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

/**
 * Andy: "our ragdolls should activate upon taking a large amount of damage, or being hit with a large enough blade"
 * (after Ragdoll Reactions, which we remake). Which hits knock a body limp, the same for players
 * ({@link LivingRagdolls}) and mobs ({@link MobRagdolls}):
 * <ul>
 * <li>a big blow: at least {@link #BIG_HIT} before armour for a body a player's size, more for a heavier one, less
 * for a smaller one ({@link #mass});</li>
 * <li>or a blow that takes at least {@link #BIG_SHARE} of the body's whole health in one go;</li>
 * <li>or a solid swing ({@link #BLADE_HIT} or more) of a big blade ({@link #bigBlade}: greatswords, longswords,
 * claymores, great axes, glaives and the like) at a body no bigger than {@link #BLADE_MAX_HEIGHT} by
 * {@link #BLADE_MAX_WIDTH}.</li>
 * </ul>
 * Only blows that come from somewhere: fire, drowning, hunger and magic from nowhere never count; a blast always comes
 * from its middle. Falls are judged on their own (LivingRagdolls).
 */
public final class HeavyHits {
	/** A blow this big (damage before armour, half-hearts) knocks a body a player's size limp. */
	public static final float BIG_HIT = 8.0F;
	/** A blow that takes this share of a body's most health at once knocks it limp... */
	public static final float BIG_SHARE = 0.35F;
	/** ...if it did at least this much (half-hearts): a punch doesn't floor a chicken. */
	public static final float SHARE_MIN = 3.0F;
	/** A big blade knocks a body limp with a swing at least this hard (before armour): not a half-charged tap. */
	public static final float BLADE_HIT = 5.0F;
	/** A big blade floors bodies up to this tall and wide (blocks, standing): a person, a wolf, a cow, not a golem. */
	public static final float BLADE_MAX_HEIGHT = 2.6F, BLADE_MAX_WIDTH = 1.4F;
	/** A player's standing box (blocks): the body {@link #BIG_HIT} is for. */
	private static final float PLAYER_WIDTH = 0.6F, PLAYER_HEIGHT = 1.8F;
	/** The big-hit threshold is never less than this share of {@link #BIG_HIT}, nor more than this many times it. */
	private static final float MASS_MIN = 0.5F, MASS_MAX = 10.0F;

	/** Item names that make a big blade (any namespace, anywhere in the item's id). */
	private static final String[] BIG_BLADE_WORDS = {"greatsword", "great_sword", "longsword", "long_sword", "claymore", "broadsword",
		"zweihander", "flamberge", "bastard_sword", "nodachi", "odachi", "greataxe", "great_axe", "war_axe", "battleaxe", "battle_axe",
		"glaive", "halberd", "scythe", "naginata", "bardiche", "poleaxe", "pole_axe"};
	/** ...unless it's a part or a mould for one (a Longsword Blade at the Tool Station, a cast). */
	private static final String[] NOT_A_WEAPON = {"_blade", "_head", "cast", "mold", "mould", "blueprint", "pattern", "template"};

	private HeavyHits() {
	}

	/** Whether this blow (damage {@code base} before armour, {@code taken} after) knocks {@code target} limp. */
	public static boolean heavy(LivingEntity target, DamageSource source, float base, float taken) {
		boolean blast = source.is(DamageTypeTags.IS_EXPLOSION);
		if (source.getSourcePosition() == null && !blast) {
			return false;
		}
		if (base >= BIG_HIT * mass(target)) {
			return true;
		}
		if (taken >= SHARE_MIN && taken >= target.getMaxHealth() * BIG_SHARE) {
			return true;
		}
		return bladeFells(target, source, base);
	}

	/** A big blade swung by hand (not thrown) hard enough, at a body small enough to be floored by one. */
	private static boolean bladeFells(LivingEntity target, DamageSource source, float base) {
		if (base < BLADE_HIT || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) {
			return false;
		}
		EntityDimensions size = target.getDimensions(Pose.STANDING);
		return size.height() <= BLADE_MAX_HEIGHT && size.width() <= BLADE_MAX_WIDTH && bigBlade(attacker.getMainHandItem());
	}

	/** A greatsword, longsword, claymore, great axe, glaive or the like, from any mod, by its name. */
	public static boolean bigBlade(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String not : NOT_A_WEAPON) {
			if (path.contains(not)) {
				return false;
			}
		}
		for (String word : BIG_BLADE_WORDS) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}

	/** How much heavier than a player {@code target} is, by its standing box: what a big hit on it takes. */
	static float mass(LivingEntity target) {
		EntityDimensions size = target.getDimensions(Pose.STANDING);
		float ratio = size.width() * size.width() * size.height() / (PLAYER_WIDTH * PLAYER_WIDTH * PLAYER_HEIGHT);
		return Math.max(MASS_MIN, Math.min(MASS_MAX, ratio));
	}
}
