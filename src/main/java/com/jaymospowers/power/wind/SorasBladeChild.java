package com.jaymospowers.power.wind;

import com.jaymospowers.core.Powers;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.WeaponParts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * (0.17.110) Andy: "the stone sora's dagger blade should be carried by a child, you have a high chance of running into
 * the child in any village as a wind user, and you can interact with the child to get it from him, you will have to
 * tell him not to play with things like that, and you take it instead."
 * <p>
 * The stone Sora's Dagger Blade isn't shaped at the Part Builder ({@link WeaponParts#FOUND}); it's found. When a Wind
 * user walks into a village they roll once ({@link #CHANCE}): a village child nearby is given the blade to play with and
 * comes running up. Right-click the child (anyone can) to tell him off and take it. Its gold cast, the iron blade and the
 * dagger then go the usual Tinkering way.
 */
public final class SorasBladeChild {
	static final String TAG = "jaymospowers_soras_blade_child";
	/** How often Wind users are checked (ticks), and the odds of the child when they come to a new village. */
	static final int CHECK_EVERY = 100;
	static final float CHANCE = 0.6F;
	/** A village counts as new once you're this far (blocks) from where you last rolled, or after a day. */
	static final double NEW_VILLAGE_DISTANCE = 256.0;
	static final long REROLL_AFTER = 24000L;
	/** How far (blocks) a child can be to be picked, and how fast he runs over to you. */
	static final double CHILD_RADIUS = 48.0;
	static final double RUN_SPEED = 0.7;

	private record Roll(BlockPos at, long time) {
	}

	/** Where and when each Wind user last rolled for the child. */
	private static final Map<UUID, Roll> ROLLS = new HashMap<>();

	private SorasBladeChild() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SorasBladeChild::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager child) || !entity.entityTags().contains(TAG)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				takeBlade(server, child);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Powers.has(player, WindPower.INSTANCE) || player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			BlockPos pos = player.blockPosition();
			if (!level.isVillage(pos)) {
				continue;
			}
			long now = level.getGameTime();
			Roll last = ROLLS.get(player.getUUID());
			if (last != null && now - last.time() < REROLL_AFTER && last.at().closerThan(pos, NEW_VILLAGE_DISTANCE)) {
				continue;
			}
			ROLLS.put(player.getUUID(), new Roll(pos, now));
			if (hasBlade(player) || level.getRandom().nextFloat() >= CHANCE) {
				continue;
			}
			List<Villager> children = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(CHILD_RADIUS),
				v -> v.isBaby() && v.isAlive() && v.getMainHandItem().isEmpty());
			if (children.isEmpty()) {
				continue;
			}
			Villager child = children.get(level.getRandom().nextInt(children.size()));
			child.setItemSlot(EquipmentSlot.MAINHAND, blade());
			child.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
			child.addTag(TAG);
			child.getNavigation().moveTo(player, RUN_SPEED);
		}
	}

	private static void takeBlade(ServerPlayer player, Villager child) {
		ItemStack held = child.getMainHandItem();
		ItemStack blade = held.is(kind().item()) ? held.copy() : blade();
		child.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		child.removeTag(TAG);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.soras_blade_child.scold", player.getDisplayName())
			.withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.soras_blade_child.reply", child.getDisplayName())
			.withStyle(ChatFormatting.GRAY));
		child.level().playSound(null, child.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.6F);
		if (!player.getInventory().add(blade)) {
			player.drop(blade, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/** Whether you already carry the blade (any metal) or a Sora's Dagger. */
	private static boolean hasBlade(Player player) {
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(kind().item()) || stack.getItem() instanceof SorasDagger) {
				return true;
			}
		}
		return false;
	}

	private static WeaponParts.Kind kind() {
		return WeaponParts.KINDS.get(WeaponParts.SORAS_DAGGER_BLADE);
	}

	private static ItemStack blade() {
		return WeaponPartItem.of(kind(), WeaponParts.Metal.STONE, 1);
	}
}
