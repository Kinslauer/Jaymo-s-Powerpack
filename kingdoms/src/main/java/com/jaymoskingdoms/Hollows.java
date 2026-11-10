package com.jaymoskingdoms;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.525) Turned by what killed you. Andy (2026-11-04): "whenever they die, there's a chance that the death method that killed
 * them turns them into one of those specific hollows, like enthralled by a vampire, or maybe zombified by a witch."
 * <p>
 * Jaymo's Powers' death by cause asks first ({@code Mortality.TURNINGS}, through {@link PowersCompat#tellTurnings}): killed by
 * one of these, a player may rise where they fell as a Hollow of the matching look instead of the roll for their life. The
 * turned death is never final and leaves no new injury; the Hollow is unlocked for their children too.
 * <ul>
 * <li><b>Zombie:</b> zombies of every kind (husks, the drowned, zombie villagers, zombified piglins), and witches.</li>
 * <li><b>Skeleton:</b> only the Skeleton Lord's curse ({@link SkeletonCurse}); skeletons themselves turn nobody.</li>
 * <li><b>Wraith:</b> enthralled by a vampire (any, players too), or taken by a phantom or a vex.</li>
 * <li><b>Reaper:</b> a necromancer's work, or the Wither's.</li>
 * <li>Any other undead: the Pallid, the Hollow's first look.</li>
 * </ul>
 * One who is Hollow already, or an android, isn't turned again.
 */
public final class Hollows {
	/** The chance a kill turns one, by who killed them. */
	static final float UNDEAD_CHANCE = 0.25F, WITCH_CHANCE = 0.3F, VAMPIRE_CHANCE = 0.4F, REAPER_CHANCE = 0.35F, OTHER_UNDEAD_CHANCE = 0.2F;

	private Hollows() {
	}

	static void register() {
		PowersCompat.tellTurnings(Hollows::turning);
	}

	/** What one killed by {@code by} would rise as, and how likely: null if {@code by} turns nobody. */
	record Turn(HeritageVariants.@Nullable Variant look, float chance, String line) {
	}

	static @Nullable Turn turnOf(Entity by) {
		if (Vampires.isVampire(by)) {
			return new Turn(HeritageVariants.Variant.WRAITH, VAMPIRE_CHANCE, "enthralled");
		}
		int role = Rulers.role(by);
		if (role == Sorcery.NECROMANCER || by instanceof WitherBoss) {
			return new Turn(HeritageVariants.Variant.REAPER, REAPER_CHANCE, "reaped");
		}
		if (by instanceof Witch || role == Sorcery.BLOOD_WITCH) {
			return new Turn(HeritageVariants.Variant.ZOMBIE, WITCH_CHANCE, "hexed");
		}
		if (by instanceof Zombie) {
			return new Turn(HeritageVariants.Variant.ZOMBIE, UNDEAD_CHANCE, "zombie");
		}
		if (by instanceof AbstractSkeleton || by instanceof SkeletonLord) {
			// (Andy: "you shouldn't be able to become a skeleton unless you are cursed by the skeleton lord": skeletons turn
			// nobody, and leave no blight; the Lord's curse is its own, SkeletonCurse)
			return null;
		}
		if (by instanceof Phantom || by instanceof Vex) {
			return new Turn(HeritageVariants.Variant.WRAITH, UNDEAD_CHANCE, "wraith");
		}
		if (by instanceof LivingEntity living && living.is(EntityTypeTags.UNDEAD)) {
			return new Turn(null, OTHER_UNDEAD_CHANCE, "pallid");
		}
		return null;
	}

	/** Rolls whether {@code by}'s kill turns {@code player}; if it does, what's done to them as they rise. */
	static @Nullable Runnable turning(ServerPlayer player, Entity by) {
		if (Heritage.of(player) == Heritage.Kind.HOLLOW || PowersCompat.android(player)) {
			return null;
		}
		Turn turn = turnOf(by);
		if (turn == null || player.getRandom().nextFloat() >= turn.chance()) {
			return null;
		}
		return () -> turn(player, turn);
	}

	/** {@code player} rises a Hollow, of {@code turn}'s look. */
	static void turn(ServerPlayer player, Turn turn) {
		if (!player.isAlive()) {
			return;
		}
		Heritage.unlock(player, Heritage.Kind.HOLLOW);
		Heritage.RISEN.remove(player.getUUID());
		Heritage.set(player, Heritage.Kind.HOLLOW, true);
		HeritageVariants.set(player, turn.look());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hollow.turned." + turn.line()).withStyle(ChatFormatting.DARK_GRAY,
			ChatFormatting.ITALIC));
		JaymosKingdoms.LOGGER.info("{} rose a Hollow ({})", player.getScoreboardName(), turn.look() == null ? "pallid" : turn.look().key());
	}
}
