package com.jaymospowers.fishing;

import java.util.Locale;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Track D: catch and release (Reel to Fish's idea, our own code). A fish lands alive ({@link FishCatch#ALIVE_TICKS}, longer
 * in a keepnet): use it on water (a river, the sea, a glass tank) and it swims off as itself, at its weight, and goes on
 * growing there ({@link WildFish}); a legend put back is its water's legend again. Sneak and use to look it over: what it
 * is, its weight and length, who caught it and when, and how long it has left out of the water.
 */
public final class CatchRelease {
	private CatchRelease() {
	}

	static void init() {
		UseItemCallback.EVENT.register(CatchRelease::use);
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		FishCatch.Info info = FishCatch.info(stack);
		if (info == null) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(describe(info, level.getGameTime()));
			}
			return InteractionResult.SUCCESS;
		}
		if (!info.alive(level.getGameTime())) {
			return InteractionResult.PASS;
		}
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
		if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer angler) {
			if (!release(server, hit.getBlockPos(), info)) {
				return InteractionResult.PASS;
			}
			stack.shrink(1);
			Species sp = info.kind();
			if (sp != null) {
				FishLog.released(angler, sp);
			}
			server.playSound(null, hit.getBlockPos(), SoundEvents.FISH_SWIM, SoundSource.NEUTRAL, 0.8F, 1.0F);
			angler.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.released",
				sp == null ? Component.literal(info.species()) : Component.translatable(sp.nameKey())).withStyle(ChatFormatting.AQUA));
		}
		return InteractionResult.SUCCESS;
	}

	/** Put the fish {@code info} back into the water at {@code pos}. False if it can't be (no live kind to make). */
	static boolean release(ServerLevel level, BlockPos pos, FishCatch.Info info) {
		Species sp = info.kind();
		EntityType<?> type = sp == null ? null : typeOf(sp);
		if (type == null) {
			return false;
		}
		Entity e = type.create(level, EntitySpawnReason.BUCKET);
		if (e == null) {
			return false;
		}
		e.snapTo(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		if (e instanceof WildFish fish) {
			if (!info.legend().isEmpty()) {
				fish.makeLegend(info.legend(), info.kg());
			} else {
				fish.setWeight(info.kg());
				fish.markReleased();
				fish.setBornDay(level.getOverworldClockTime() / 24000L);
			}
		} else if (e instanceof Critter critter) {
			critter.setWeight(info.kg());
			critter.setPersistenceRequired();
		} else if (e instanceof Mob mob) {
			mob.setPersistenceRequired();
		}
		level.addFreshEntity(e);
		level.sendParticles(ParticleTypes.BUBBLE, e.getX(), e.getY(), e.getZ(), 10, 0.2, 0.1, 0.2, 0.05);
		if (e instanceof WildFish fish && fish.legend()) {
			FishLegends.released(level, fish);
		}
		return true;
	}

	/** The live entity for {@code sp}: ours, the skipjack, or vanilla's own fish. */
	static @Nullable EntityType<?> typeOf(Species sp) {
		return switch (sp) {
			case COD -> net.minecraft.world.entity.EntityTypes.COD;
			case SALMON -> net.minecraft.world.entity.EntityTypes.SALMON;
			case PUFFERFISH -> net.minecraft.world.entity.EntityTypes.PUFFERFISH;
			case TROPICAL -> net.minecraft.world.entity.EntityTypes.TROPICAL_FISH;
			case TUNA -> Skipjack.SKIPJACK;
			default -> FishSpecies.type(sp);
		};
	}

	/** A landed fish looked over: kind, weight, length, angler, day, and whether it's still alive. */
	public static MutableComponent describe(FishCatch.Info info, long now) {
		Species sp = info.kind();
		MutableComponent name = info.legend().isEmpty() ? Component.translatable(sp == null ? "species.jaymospowers.other" : sp.nameKey())
			: Component.literal(info.legend());
		MutableComponent out = Component.translatable("message.jaymospowers.fishing.catch_stats", name,
			String.format(Locale.ROOT, "%.1f", info.kg()), info.cm(), info.angler().isEmpty() ? "?" : info.angler(), info.day());
		if (info.alive(now)) {
			out.append(Component.translatable("message.jaymospowers.fishing.catch_alive", (info.aliveUntil() - now) / 20L));
		}
		return out.withStyle(ChatFormatting.AQUA);
	}
}
