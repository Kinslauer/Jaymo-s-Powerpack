package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.Armory;
import com.jaymospowers.boxing.BoxingGear;
import com.jaymospowers.core.TwoHanded;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Being armed: what both sides agree a held weapon is, for the body's side of swordplay (the client's {@code ArmedPose}
 * and {@code ParryPose}, the mid-air flip in {@link com.jaymospowers.stamina.Dodge}).
 * <p>
 * Andy picked these from Star Wars: Galaxy in a Cube (All Rights Reserved, 1.20.1 only), which changes the whole body
 * the moment a blade is in the hand. Nothing of it is copied: the idea is its, the poses, names and code are ours.
 * <ul>
 * <li>{@link #kind}: a weapon in the hand is a one-handed blade, a two-handed one, or a polearm. Tools you dig with
 * (picks, shovels, hoes) and gear worn on the fists aren't weapons here.</li>
 * <li>{@link #MET}: the last blow a body's weapon guard met, and on which line (high, low, hanging), for everyone to
 * see the guard move to it. Set from {@link ClashFx#met}, so players' guards, parries and fighters' parries all show.</li>
 * </ul>
 */
public final class Armed {
	// ===== the knobs ================================================================================================
	/** A blow from above this share of the defender's height is met high; from below this share, low. */
	static final double HIGH_ABOVE = 1.15, LOW_BELOW = 0.5;
	/** Words in an item's id that make it a polearm. */
	static final Set<String> POLE_WORDS = Set.of("spear", "halberd", "glaive", "pike", "lance", "staff", "quarterstaff", "javelin",
		"trident", "naginata", "scythe", "poleaxe", "partisan", "bardiche");
	// ================================================================================================================

	/** How a weapon is carried. */
	public enum Kind {
		ONE_HAND, TWO_HAND, POLE
	}

	/** The lines a guard meets a blow on. */
	public static final int HIGH = 0, LOW = 1, HANGING = 2;

	/** The last blow met: when (game time), on which line, and whether it was parried (not only blocked). */
	public record Met(long time, int line, boolean parried) {
		public static final StreamCodec<ByteBuf, Met> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Met::time,
			ByteBufCodecs.VAR_INT, Met::line,
			ByteBufCodecs.BOOL, Met::parried,
			Met::new);
	}

	/** On a body whose weapon guard has met a blow; synced to everyone. Not saved. */
	public static final AttachmentType<Met> MET = AttachmentRegistry.create(JaymosPowers.id("guard_met"),
		builder -> builder.syncWith(Met.CODEC, AttachmentSyncPredicate.all()));

	private static final boolean BETTER_COMBAT = FabricLoader.getInstance().isModLoaded("bettercombat");

	private Armed() {
	}

	/** How {@code stack} is carried as a weapon, or null if it isn't one. The same answer on both sides. */
	public static @Nullable Kind kind(ItemStack stack) {
		if (stack.isEmpty() || !stack.has(DataComponents.WEAPON) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
			return null;
		}
		if (Armory.FANG_GAUNTLETS != null && stack.is(Armory.FANG_GAUNTLETS) || BoxingGear.wornIndex(stack) >= 0) {
			return null;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
		for (String word : path.split("[_./]")) {
			if (POLE_WORDS.contains(word)) {
				return Kind.POLE;
			}
		}
		return BETTER_COMBAT && TwoHanded.is(stack) ? Kind.TWO_HAND : Kind.ONE_HAND;
	}

	/**
	 * {@code defender}'s guard, held in {@code guard}, met a blow from {@code attacker} (null: an arrow) come from
	 * {@code from}. Only a weapon's guard takes a line; a shield is simply held.
	 */
	static void met(LivingEntity defender, ItemStack guard, @Nullable LivingEntity attacker, @Nullable Vec3 from, boolean parried) {
		if (!guard.has(DataComponents.WEAPON)) {
			return;
		}
		// (a melee blow's source position is the attacker's feet: judge by where their weapon comes from)
		double y = attacker != null ? attacker.getEyeY() : from != null ? from.y : defender.getEyeY();
		double rel = y - defender.getY();
		double height = defender.getBbHeight();
		Met last = defender.getAttached(MET);
		int line = rel > height * HIGH_ABOVE ? HIGH : rel < height * LOW_BELOW ? LOW : last != null && last.line() == HIGH ? HANGING : HIGH;
		defender.setAttached(MET, new Met(defender.level().getGameTime(), line, parried));
	}
}
