package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import com.jaymoskingdoms.money.Money;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A nemesis: the Deck's card of an enemy. Someone sends a hunter after the one who drew it, about once a day, that finds them
 * wherever they are. It is a vindicator picked out in a grey coat, and cannot be bargained with. Three of them must fall to
 * lift the card; each hunter that falls drops a coin purse (they were paid in advance).
 */
public final class Nemesis {
	/** Hunters still to fall (0 = none). */
	public static final AttachmentType<Integer> HUNTERS = AttachmentRegistry.create(JaymosKingdoms.id("nemesis"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	static final String TAG = "jaymos_nemesis";
	/** How many hunters must fall, how often one is sent (ticks, checked every minute), how far off it comes (blocks), its added health, and the purse it carries (coins). */
	static final int MUST_FALL = 3, CHECK = 1200, FAR = 36, PURSE = 8;
	static final double HEALTH = 24.0;
	/** The chance each minute that one is sent when none is about (about one a day). */
	static final double SENT = 1.0 / 20.0;

	private Nemesis() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Nemesis::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.entityTags().contains(TAG) && entity.level() instanceof ServerLevel level) {
				if (source.getEntity() instanceof ServerPlayer player && player.getAttachedOrElse(HUNTERS, 0) > 0) {
					int left = player.getAttachedOrElse(HUNTERS, 0) - 1;
					player.setAttached(HUNTERS, Math.max(0, left));
					Money.pay(player, Money.worth(PURSE));
					player.sendSystemMessage(Component.translatable(left > 0 ? "deck.jaymoskingdoms.nemesis.fell" : "deck.jaymoskingdoms.nemesis.over", left), true);
				}
			}
		});
	}

	/** Marks {@code player}: the card is drawn. */
	static void mark(ServerPlayer player) {
		player.setAttached(HUNTERS, MUST_FALL);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getAttachedOrElse(HUNTERS, 0) <= 0 || player.isSpectator() || player.getRandom().nextDouble() >= SENT) {
				continue;
			}
			ServerLevel level = player.level();
			if (!level.getEntitiesOfClass(Vindicator.class, player.getBoundingBox().inflate(96), v -> v.entityTags().contains(TAG)).isEmpty()) {
				continue;
			}
			send(level, player);
		}
	}

	private static void send(ServerLevel level, ServerPlayer player) {
		double angle = player.getRandom().nextDouble() * Math.PI * 2;
		int x = (int)(player.getX() + Math.cos(angle) * FAR), z = (int)(player.getZ() + Math.sin(angle) * FAR);
		BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		Vindicator hunter = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
		if (hunter == null) {
			return;
		}
		hunter.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		hunter.addTag(TAG);
		hunter.setCustomName(Component.translatable("deck.jaymoskingdoms.nemesis.name"));
		hunter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		hunter.setPersistenceRequired();
		var health = hunter.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("nemesis_health"), HEALTH, AttributeModifier.Operation.ADD_VALUE));
		}
		hunter.setHealth(hunter.getMaxHealth());
		hunter.setTarget(player);
		level.addFreshEntity(hunter);
		player.sendSystemMessage(Component.translatable("deck.jaymoskingdoms.nemesis.sent"), true);
	}
}
