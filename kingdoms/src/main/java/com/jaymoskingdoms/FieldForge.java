package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Smith's field forge. A Journeyman Smith (or better) who sneaks and works a lit campfire with a hammer or a pickaxe
 * in hand (any item whose name has "hammer" in it, or a pickaxe) turns it into a camp forge for {@link #LASTS} ticks:
 * the players, tamed animals and built golems within {@link #RANGE} of it mend slowly and work and strike quicker.
 * The tool wears {@link #WEAR}, and the Smith can raise another forge only after {@link #COOLDOWN}. A forge lives in
 * memory (a restart ends it) and ends when its fire goes out. It uses no new block or item.
 */
public final class FieldForge {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** How long a forge burns (ticks), and how long before its Smith may raise another. */
	static final long LASTS = 1200L, COOLDOWN = 6000L;
	/** How far its warmth reaches (blocks). */
	static final double RANGE = 10.0;
	/** Wear on the tool that works it. */
	static final int WEAR = 5;
	/** How often the forge looks after those near it (ticks), and how long each boon is given for (ticks). */
	static final int EVERY = 20, BOON_TICKS = 60;
	/** Mending and quickening levels (0 is the first). */
	static final int MEND_LEVEL = 0, QUICK_LEVEL = 0;
	/** The glow's colour, and how often its ring is drawn (in passes). */
	static final int GLOW = 0xFF9A3C, RING_EVERY = 3;
	static final float RING_RADIUS = 4.0F;
	/** Smith rank needed. */
	static final int RANK = Careers.JOURNEYMAN;

	/** One forge: where, whose, until when (game time). */
	private record Forge(Identifier dimension, BlockPos pos, UUID smith, long until) {
	}

	private static final List<Forge> FORGES = new ArrayList<>();
	/** Smith → the tick they may raise another. */
	private static final Map<UUID, Long> READY = new HashMap<>();

	private FieldForge() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FORGES.clear();
			READY.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(FieldForge::tick);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)
				|| !sp.isShiftKeyDown() || !worksForge(sp.getMainHandItem())) {
				return InteractionResult.PASS;
			}
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (!(state.getBlock() instanceof CampfireBlock) || !state.getValue(CampfireBlock.LIT) || Careers.heldRank(sp, Careers.Job.SMITH) < RANK) {
				return InteractionResult.PASS;
			}
			raise(level, sp, hit.getBlockPos());
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	/** A hammer, or a pickaxe, is what a forge is worked with. */
	static boolean worksForge(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(ItemTags.PICKAXES) || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("hammer"));
	}

	private static void raise(ServerLevel level, ServerPlayer smith, BlockPos pos) {
		long now = level.getGameTime();
		Long ready = READY.get(smith.getUUID());
		if (ready != null && ready > now) {
			smith.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.forge.cooling", (ready - now) / 20L));
			return;
		}
		READY.put(smith.getUUID(), now + COOLDOWN);
		FORGES.removeIf(f -> f.smith().equals(smith.getUUID()));
		FORGES.add(new Forge(level.dimension().identifier(), pos.immutable(), smith.getUUID(), now + LASTS));
		smith.getMainHandItem().hurtAndBreak(WEAR, smith, InteractionHand.MAIN_HAND);
		smith.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Vec3 at = Vec3.atCenterOf(pos);
		level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
		Fx.blast(level, at.add(0.0, 0.5, 0.0), 1.5F, GLOW);
		smith.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.forge.raised"));
	}

	private static void tick(MinecraftServer server) {
		if (FORGES.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		int pass = server.getTickCount() / EVERY;
		FORGES.removeIf(forge -> !keep(server, forge, pass));
	}

	/** One pass over {@code forge}: false once it is spent. */
	private static boolean keep(MinecraftServer server, Forge forge, int pass) {
		ServerLevel level = null;
		for (ServerLevel l : server.getAllLevels()) {
			if (l.dimension().identifier().equals(forge.dimension())) {
				level = l;
			}
		}
		if (level == null || level.getGameTime() >= forge.until()) {
			end(server, forge, level);
			return false;
		}
		if (!level.isLoaded(forge.pos())) {
			return true;
		}
		BlockState state = level.getBlockState(forge.pos());
		if (!(state.getBlock() instanceof CampfireBlock) || !state.getValue(CampfireBlock.LIT)) {
			end(server, forge, level);
			return false;
		}
		Vec3 at = Vec3.atCenterOf(forge.pos());
		level.sendParticles(Fx.spark(GLOW, 0.9F), at.x, at.y + 0.6, at.z, 6, 0.3, 0.3, 0.3, 0.05);
		if (pass % RING_EVERY == 0) {
			Fx.ring(level, at, RING_RADIUS, GLOW);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(forge.pos()).inflate(RANGE), FieldForge::ally)) {
			e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BOON_TICKS, MEND_LEVEL, true, false, true));
			if (e instanceof ServerPlayer) {
				e.addEffect(new MobEffectInstance(MobEffects.HASTE, BOON_TICKS, QUICK_LEVEL, true, false, true));
			} else {
				e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BOON_TICKS, QUICK_LEVEL, true, false, false));
			}
		}
		return true;
	}

	/** Players, tamed animals and built golems. */
	private static boolean ally(LivingEntity e) {
		if (!e.isAlive()) {
			return false;
		}
		return e instanceof ServerPlayer p ? !p.isSpectator() : e instanceof TamableAnimal t ? t.isTame() : e instanceof IronGolem g && g.isPlayerCreated();
	}

	private static void end(MinecraftServer server, Forge forge, @Nullable ServerLevel level) {
		if (level != null) {
			level.playSound(null, forge.pos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
		}
		ServerPlayer smith = server.getPlayerList().getPlayer(forge.smith());
		if (smith != null) {
			smith.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.forge.spent"));
		}
	}
}
