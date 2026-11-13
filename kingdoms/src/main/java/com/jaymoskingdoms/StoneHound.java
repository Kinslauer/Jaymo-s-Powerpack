package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The stone hound: a wolf of grey stone no longer than a hand. Set it down and it wakes as a great tame hound that fights at
 * your side for ten minutes (a hound bred of no mother and tired of no work), then turns to stone again where it stands.
 * Once a day; a hound that falls leaves the stone cracked until the next dawn.
 */
public final class StoneHound {
	/** How long it runs (ticks), its health and bite added to a wolf's, and the tag it wears. */
	static final int LASTS = 12000;
	static final double HEALTH = 20.0, BITE = 4.0;
	static final String TAG = "jaymos_stone_hound", DAY = "jaymos_hound_day";
	public static Item ITEM;

	private static final Map<UUID, Long> ENDS = new HashMap<>();

	private StoneHound() {
	}

	static void register() {
		ITEM = Gadgets.item("stone_hound", Rarity.RARE, Figurine::new);
		ServerTickEvents.END_SERVER_TICK.register(StoneHound::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ENDS.clear());
		// a hound still about when the world was saved runs a little longer, then rests
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Wolf wolf && wolf.entityTags().contains(TAG) && !ENDS.containsKey(wolf.getUUID())) {
				ENDS.put(wolf.getUUID(), level.getServer().getTickCount() + 600L);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		if (ENDS.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		long now = server.getTickCount();
		ENDS.entrySet().removeIf(e -> {
			if (e.getValue() > now) {
				for (ServerLevel level : server.getAllLevels()) {
					if (level.getEntity(e.getKey()) instanceof Wolf w && w.isAlive()) {
						return false;
					}
				}
				return true;
			}
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(e.getKey()) instanceof Wolf w) {
					Fx.burst(level, w.getX(), w.getY() + 0.5, w.getZ(), 0xA8A8B0, 0.9F, 18, 0.4, 0.3, 0.4);
					level.playSound(null, w.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 1.0F, 0.7F);
					w.discard();
				}
			}
			return true;
		});
	}

	static final class Figurine extends Item {
		Figurine(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!ItemBonds.works(sp, stack)) {
				return InteractionResult.PASS;
			}
			long today = ChargedWand.today(server);
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data != null && data.copyTag().getLongOr(DAY, -1L) >= today) {
				sp.sendSystemMessage(Component.translatable("gadget.jaymoskingdoms.hound.rests"), true);
				return InteractionResult.FAIL;
			}
			Vec3 at = player.position().add(player.getLookAngle().multiply(1.5, 0, 1.5));
			Wolf wolf = EntityTypes.WOLF.create(server, EntitySpawnReason.MOB_SUMMONED);
			if (wolf == null) {
				return InteractionResult.FAIL;
			}
			wolf.snapTo(at.x, player.getY(), at.z, player.getYRot(), 0.0F);
			wolf.finalizeSpawn(server, server.getCurrentDifficultyAt(wolf.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
			wolf.tame(sp);
			wolf.addTag(TAG);
			wolf.setCustomName(Component.translatable("gadget.jaymoskingdoms.hound.name"));
			boost(wolf, Attributes.MAX_HEALTH, "hound_health", HEALTH);
			boost(wolf, Attributes.ATTACK_DAMAGE, "hound_bite", BITE);
			wolf.setHealth(wolf.getMaxHealth());
			server.addFreshEntity(wolf);
			ENDS.put(wolf.getUUID(), (long)server.getServer().getTickCount() + LASTS);
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(DAY, today));
			Fx.burst(server, at.x, player.getY() + 0.5, at.z, 0xA8A8B0, 0.9F, 18, 0.4, 0.3, 0.4);
			server.playSound(null, wolf.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);
			return InteractionResult.SUCCESS;
		}

		private static void boost(Wolf wolf, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String id, double by) {
			AttributeInstance instance = wolf.getAttribute(attribute);
			if (instance != null) {
				instance.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id(id), by, AttributeModifier.Operation.ADD_VALUE));
			}
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.stone_hound").withStyle(ChatFormatting.GRAY));
		}
	}
}
