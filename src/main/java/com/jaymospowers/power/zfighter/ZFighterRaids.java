package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Raids (needs Jaymo's Kingdoms): every {@link #RAID_DAYS} days or so, by night, a tyrant ({@link ZFighterRogue}) comes
 * down in lightning beside a kingdom someone is near, and fights its people, its golems and everyone who comes. Whoever
 * hurt him shares {@link #RAID_TP} TP by how much (a Z Fighter; anyone else, emeralds and experience).
 */
public final class ZFighterRaids {
	/** (0.13.16) Much rarer: every 20 to 30 days (was 5 to 7). */
	static final long RAID_DAYS = 20L;
	static final long RAID_SPREAD = 240000L;
	static final double NEAR = 160.0;
	/** (0.15.61, Andy) A tyrant only comes to a kingdom with a player near it in this world tier or higher (1: Borderlands, the second). */
	static final int MIN_TIER = 1;
	static final int RAID_TP = 1500;
	static final int MIN_TP = 125;
	static final int RAID_EMERALDS = 48;
	static final int RAID_XP = 30;
	static final double LAND_AT = 24.0;
	static final long DAY = 24000L;
	static final String[] TYRANTS = {"garrok", "vexa", "kural", "morrow", "saiza", "dren"};

	public static EntityType<ZFighterRogue> ROGUE;

	/** When the next tyrant may come (overworld game time). */
	public static final AttachmentType<Long> NEXT = AttachmentRegistry.create(JaymosPowers.id("zfighter_next_raid"),
		builder -> builder.persistent(Codec.LONG));

	private ZFighterRaids() {
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("rogue_fighter"));
		ROGUE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<ZFighterRogue>of(ZFighterRogue::new, MobCategory.MONSTER)
				.sized(0.6F, 1.8F)
				.eyeHeight(1.62F)
				.clientTrackingRange(10)
				.noLootTable()
				.build(key));
		FabricDefaultAttributeRegistry.register(ROGUE, ZFighterRogue.createAttributes());
		ServerTickEvents.END_SERVER_TICK.register(ZFighterRaids::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (now % 600 != 0 || !ZFighterKingdoms.present()) {
			return;
		}
		Long next = level.getAttached(NEXT);
		if (next == null) {
			level.setAttached(NEXT, now + RAID_DAYS * DAY + level.getRandom().nextInt((int)RAID_SPREAD));
			return;
		}
		if (now < next || !level.isDarkOutside()) {
			return;
		}
		List<ZFighterKingdoms.Settlement> near = new ArrayList<>();
		for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(server)) {
			if ("KINGDOM".equals(s.type()) && level.getNearestPlayer(s.seat().getX(), s.seat().getY(), s.seat().getZ(), NEAR,
				p -> !p.isSpectator() && ZFighterKingdoms.worldTier((net.minecraft.world.entity.player.Player)p) >= MIN_TIER) != null) {
				near.add(s);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		ZFighterKingdoms.Settlement s = near.get(random.nextInt(near.size()));
		if (land(level, s.seat(), s.name())) {
			level.setAttached(NEXT, now + RAID_DAYS * DAY + random.nextInt((int)RAID_SPREAD));
		}
	}

	/** A tyrant comes down beside this kingdom. */
	static boolean land(ServerLevel level, BlockPos seat, String kingdom) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, seat.offset((int)(Math.cos(angle) * LAND_AT), 0,
			(int)(Math.sin(angle) * LAND_AT)));
		ZFighterRogue tyrant = ROGUE.create(level, EntitySpawnReason.EVENT);
		if (tyrant == null) {
			return false;
		}
		String who = TYRANTS[random.nextInt(TYRANTS.length)];
		tyrant.snapTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		tyrant.become(ZFighterRogue.TYRANT, Component.translatable("entity.jaymospowers.rogue_fighter.tyrant." + who));
		tyrant.kingdom = kingdom;
		level.addFreshEntity(tyrant);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0F, 1.2F);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_raid",
			tyrant.getDisplayName(), kingdom).withStyle(ChatFormatting.RED), false);
		return true;
	}

	/** He's beaten: everyone who hurt him shares the prize, by how much they hurt him. */
	static void defeated(ServerLevel level, ZFighterRogue tyrant) {
		float total = 0.0F;
		for (float amount : tyrant.hurtBy.values()) {
			total += amount;
		}
		int shared = 0;
		for (Map.Entry<UUID, Float> entry : tyrant.hurtBy.entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player == null || total <= 0.0F) {
				continue;
			}
			float share = entry.getValue() / total;
			shared++;
			if (Powers.has(player, ZFighterPower.INSTANCE)) {
				int tp = Math.max(MIN_TP, Math.round(RAID_TP * share));
				ZFighterTraining.grant(player, tp);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_raid_share_tp", tp).withStyle(ChatFormatting.GOLD));
			} else {
				int emeralds = Math.max(8, Math.round(RAID_EMERALDS * share));
				if (!player.getInventory().add(new ItemStack(Items.EMERALD, emeralds))) {
					player.drop(new ItemStack(Items.EMERALD, emeralds), false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				player.giveExperienceLevels(Math.max(5, Math.round(RAID_XP * share)));
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_raid_share", emeralds).withStyle(ChatFormatting.GOLD));
			}
		}
		// (0.17.53) a rare pair of bonding earrings
		ZFighterFusionItems.tyrantDrop(level, tyrant);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_raid_won",
			tyrant.getDisplayName(), tyrant.kingdom, shared).withStyle(ChatFormatting.GOLD), false);
	}
}
