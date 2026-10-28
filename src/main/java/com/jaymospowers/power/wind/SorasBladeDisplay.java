package com.jaymospowers.power.wind;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.110) Andy: "another way to find it is to stumble across it on display at a blacksmith, who created it, but
 * realized it wouldnt work for him." While no Sora's Dagger line is out ({@link SorasLineage}), anyone walking into a
 * village may find the stone blade in an item frame on the wall by a smith's work block (a weaponsmith's grindstone, a
 * toolsmith's smithing table or an armorer's blast furnace). Right-click the frame to take it; the smith tells you why
 * it's yours for the taking.
 */
public final class SorasBladeDisplay {
	static final String TAG = "jaymospowers_soras_blade_display";
	static final int CHECK_EVERY = 100;
	/** The odds of a smith showing it off when someone comes to a new village. */
	static final float CHANCE = 0.35F;
	/** How far (blocks) the smith's work block can be, how far round it the frame may hang, and how far the smith may be. */
	static final int SMITHY_RADIUS = 48, WALL_REACH = 3;
	static final double SMITH_RADIUS = 16.0;
	/** A display no one took is passed over for a new one after this long (ticks, two days). */
	static final long REPLANT_AFTER = 48000L;

	private static final Map<UUID, SorasBladeChild.Roll> ROLLS = new HashMap<>();

	private SorasBladeDisplay() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SorasBladeDisplay::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof ItemFrame frame) || !entity.entityTags().contains(TAG)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				take(server, frame);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0 || !SorasLineage.open(server)) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			BlockPos pos = player.blockPosition();
			long now = level.getGameTime();
			SorasLineage.State state = SorasLineage.state(server);
			if (!level.isVillage(pos) || state.display().isPresent() && now - state.displayAt() < REPLANT_AFTER
				|| !SorasBladeChild.Roll.fresh(ROLLS, player, pos, now) || level.getRandom().nextFloat() >= CHANCE) {
				continue;
			}
			ItemFrame frame = hang(level, pos, SorasLineage.foundBlade(server));
			if (frame != null) {
				SorasLineage.set(server, new SorasLineage.State(state.serial(), false, state.child(), state.childAt(),
					Optional.of(frame.getUUID()), now));
				return;
			}
		}
	}

	/** Hangs the blade in a frame on a wall by the nearest smith's work block, or returns null if there's no spot. */
	private static @Nullable ItemFrame hang(ServerLevel level, BlockPos near, ItemStack blade) {
		List<BlockPos> smithies = level.getPoiManager().getInRange(
				type -> type.is(PoiTypes.WEAPONSMITH) || type.is(PoiTypes.TOOLSMITH) || type.is(PoiTypes.ARMORER),
				near, SMITHY_RADIUS, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos).sorted(Comparator.comparingDouble(p -> p.distSqr(near))).toList();
		for (BlockPos smithy : smithies) {
			for (BlockPos at : BlockPos.betweenClosed(smithy.offset(-WALL_REACH, 0, -WALL_REACH), smithy.offset(WALL_REACH, 2, WALL_REACH))) {
				if (!level.getBlockState(at).isAir()) {
					continue;
				}
				for (Direction facing : Direction.Plane.HORIZONTAL) {
					ItemFrame frame = new ItemFrame(level, at.immutable(), facing);
					if (frame.survives()) {
						frame.setItem(blade, false);
						frame.addTag(TAG);
						level.addFreshEntity(frame);
						return frame;
					}
				}
			}
		}
		return null;
	}

	private static void take(ServerPlayer player, ItemFrame frame) {
		ItemStack blade = frame.getItem().copy();
		frame.discard();
		if (!SorasLineage.marked(blade)) {
			return;
		}
		Villager smith = smith(player.level(), frame.blockPosition());
		Component name = smith != null ? smith.getDisplayName() : Component.translatable("message.jaymospowers.soras_blade_display.the_smith");
		player.sendSystemMessage(Component.translatable("message.jaymospowers.soras_blade_display.says", name).withStyle(ChatFormatting.GRAY));
		player.level().playSound(null, frame.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
		if (!player.getInventory().add(blade)) {
			player.drop(blade, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		SorasLineage.claim(player.level().getServer());
	}

	/** The nearest smith (weaponsmith, toolsmith, armorer) to the frame, or null. */
	private static @Nullable Villager smith(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(at).inflate(SMITH_RADIUS), v -> {
				var job = v.getVillagerData().profession();
				return job.is(VillagerProfession.WEAPONSMITH) || job.is(VillagerProfession.TOOLSMITH) || job.is(VillagerProfession.ARMORER);
			}).stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5))).orElse(null);
	}
}
