package com.jaymoskingdoms;

import com.jaymoskingdoms.HeritageVariants.Variant;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Reborn as a monster (Tensura Neo Otherworld's reincarnation, ARR: only the shape of the idea, none of its story or names; our own
 * code). The locked road {@link LifeStory.Start#MONSTERBORN} wakes you as one of the goblin people ({@link Heritage.Kind#GOBLIN}) in one of
 * three forms, by where you wake: a <b>slime-born</b> by water or in a swamp, a <b>kobold</b> in the hills and mountains, a plain
 * <b>goblin</b> anywhere else. The forms use the goblin body and the goblin look of the pack (the Workshop's goblin body, tinted green,
 * scaly red-brown or gel-clear): no new mob model. You grow through four stages ({@link #STAGE_AT}: experience from the monsters
 * you kill ({@link #KILL_XP}) and the remains you eat ({@link Feasting}); each stage is a new name, a little more size and strength, and
 * a gift of the form. The last stage opens the way to a throne ({@link DemonThrone}). Kept on the player ({@link #FORM},
 * {@link #XP}), kept through death. The road is opened by naming a follower ({@link NamedFollowers}).
 */
public final class MonsterBorn {
	/** Experience to reach stages 1, 2 and 3. */
	static final int[] STAGE_AT = {25, 90, 220};
	/** Experience a monster killed gives. */
	static final int KILL_XP = 1;
	/** Each stage: the share of size, health, blow, and (kobold) armour or (goblin) pace or (slime) knockback held it adds. */
	static final double SIZE = 0.06, HEALTH = 3.0, BLOW = 0.06, ARMOUR = 1.0, PACE = 0.03, STEADY = 0.1;
	static final int EVERY = 100;
	public static final String GOBLIN = "goblin", KOBOLD = "kobold", SLIME = "slime";
	/** The id prefix of a form's title in {@link TitleChoice}. */
	public static final String PREFIX = "monster:";

	/** The form you woke in: {@link #GOBLIN}, {@link #KOBOLD} or {@link #SLIME}; none for anyone else. Saved, kept through death. */
	static final AttachmentType<String> FORM = AttachmentRegistry.create(JaymosKingdoms.id("monster_form"), b -> b.persistent(Codec.STRING).copyOnDeath());
	/** Your growth so far. Saved, kept through death. */
	static final AttachmentType<Integer> XP = AttachmentRegistry.create(JaymosKingdoms.id("monster_growth"), b -> b.persistent(Codec.INT).copyOnDeath());

	private MonsterBorn() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
			if (source.getEntity() instanceof ServerPlayer p && dead instanceof Enemy && is(p)) {
				grow(p, KILL_XP);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(MonsterBorn::tick);
	}

	/** Whether {@code player} was reborn a monster. */
	public static boolean is(Player player) {
		return player.getAttached(FORM) != null && Heritage.of(player) == Heritage.Kind.GOBLIN;
	}

	/** The goblin people and the orcs: the monster races, who may feast on the dead ({@link Feasting}). */
	static boolean race(Player player) {
		Heritage.Kind kind = Heritage.of(player);
		return kind == Heritage.Kind.GOBLIN || kind == Heritage.Kind.ORC;
	}

	/** Your stage, 0 to 3 (0 for anyone not reborn). */
	public static int stage(Player player) {
		if (!is(player)) {
			return 0;
		}
		int xp = player.getAttachedOrElse(XP, 0), stage = 0;
		for (int at : STAGE_AT) {
			if (xp >= at) {
				stage++;
			}
		}
		return stage;
	}

	/** The form {@code pos} makes of someone waking there: gel by water or in a swamp, scales in the hills, else a goblin. */
	static String formAt(ServerLevel level, BlockPos pos) {
		var biome = level.getBiome(pos);
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(net.minecraft.world.level.biome.Biomes.SWAMP)
			|| biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP) || level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER)) {
			return SLIME;
		}
		return biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || biome.is(BiomeTags.IS_BADLANDS) ? KOBOLD : GOBLIN;
	}

	/** The road's waking: {@code player} takes the form of where they lie, at stage 0, and is told, obliquely, what they are. */
	static void wake(ServerPlayer player) {
		String form = formAt(player.level(), player.blockPosition());
		Variant look = form.equals(SLIME) ? Variant.SLIMEBORN : form.equals(KOBOLD) ? Variant.KOBOLD : null;
		player.setAttached(FORM, form);
		player.setAttached(XP, 0);
		HeritageVariants.set(player, look);
		Stats.attributes(player);
		player.getInventory().add(new ItemStack(Items.BONE, 2));
		player.getInventory().add(new ItemStack(Items.ROTTEN_FLESH, 3));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.monsterborn.wake." + form).withStyle(net.minecraft.ChatFormatting.GRAY));
	}

	/** {@code player} grows by {@code n}; a stage reached is told with a title. */
	static void grow(ServerPlayer player, int n) {
		if (!is(player)) {
			return;
		}
		int before = stage(player);
		player.setAttached(XP, player.getAttachedOrElse(XP, 0) + n);
		int now = stage(player);
		if (now > before) {
			attributes(player);
			Fanfare.title(player, name(player), Component.translatable("message.jaymoskingdoms.monsterborn.grown"));
		}
	}

	/** What {@code player} is called at their stage: {@code monsterborn.<form>.<stage>}. */
	static Component name(Player player) {
		return Component.translatable("monsterborn." + player.getAttachedOrElse(FORM, GOBLIN) + "." + stage(player));
	}

	/** The titles a reborn player has earned (their stage's name from stage 1), for {@link TitleChoice#earned}. */
	static List<TitleChoice.Entry> earned(Player player) {
		List<TitleChoice.Entry> out = new ArrayList<>();
		for (int s = 1; s <= stage(player); s++) {
			out.add(new TitleChoice.Entry(PREFIX + s, Component.translatable("monsterborn." + player.getAttachedOrElse(FORM, GOBLIN) + "." + s), 0x8CC060));
		}
		return out;
	}

	static @Nullable Component shown(Player player, String id) {
		for (TitleChoice.Entry e : earned(player)) {
			if (e.id().equals(id)) {
				return e.name();
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.getAttached(FORM) != null) {
				attributes(p);
			}
		}
	}

	/** Size, health, blow and the form's gift, for the stage: all taken off if they are no longer a goblin. */
	static void attributes(ServerPlayer player) {
		int s = stage(player);
		String form = player.getAttachedOrElse(FORM, GOBLIN);
		Skills.modifier(player, Attributes.SCALE, "monster_stage", SIZE * s, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.MAX_HEALTH, "monster_stage", HEALTH * s, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, "monster_stage", BLOW * s, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.ARMOR, "monster_stage", form.equals(KOBOLD) ? ARMOUR * s : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, "monster_stage", form.equals(GOBLIN) ? PACE * s : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "monster_stage", form.equals(SLIME) ? STEADY * s : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.SAFE_FALL_DISTANCE, "monster_stage", form.equals(SLIME) ? 2.0 * s : 0.0, AttributeModifier.Operation.ADD_VALUE);
	}
}
