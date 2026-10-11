package com.jaymospowers.power.resonance;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.PowerInput;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * G (hold), Shatter: hum at what you look at for up to {@code MAX_HUM} ticks.
 * <ul>
 * <li>A block: its pitch is found and it breaks. Glass and ice at once; anything else takes {@code STONE_TICKS} per
 * {@code STONE_HARDNESS} of hardness (stone 1 s), so a hum breaks nothing harder than deepslate (tuned low, twice as
 * hard). Obsidian and anything as hard never break; blocks harder than iron ore break without drops.</li>
 * <li>A creature: each piece of its armour loses 10% of its durability every {@code STRIP_EVERY} ticks held (tuned high,
 * faster), and the hum ends in {@code END_DAMAGE}.</li>
 * </ul>
 * The cooldown follows only a hum that did something.
 */
final class ResonanceShatter {
	static final double RANGE = 12.0;
	static final int MAX_HUM = 40;
	static final int STONE_TICKS = 20;
	static final float STONE_HARDNESS = 1.5F;
	/** Never: obsidian's hardness and above (and unbreakable blocks). */
	static final float NEVER_HARDNESS = 50.0F;
	/** Harder than iron ore: broken, but nothing drops. */
	static final float IRON_ORE_HARDNESS = 3.0F;
	/** Tuned low: stone breaks this much faster per step (half the time at the bottom). */
	static final double LOW_SPEEDUP_PER_STEP = 0.25;
	static final int STRIP_EVERY = 10;
	/** Tuned high: armour strips this many ticks sooner per step. */
	static final int HIGH_STRIP_FASTER = 2;
	static final float STRIP_SHARE = 0.10F;
	static final float END_DAMAGE = 2.0F;

	private static final class Hum {
		final long from;
		@Nullable BlockPos block;
		int blockTicks;
		boolean warned;
		int entityId = -1;
		int entityTicks;
		boolean did;

		Hum(long from) {
			this.from = from;
		}
	}

	private static final Map<UUID, Hum> HUMS = new HashMap<>();

	private ResonanceShatter() {
	}

	static void start(ServerPlayer player) {
		if (HUMS.containsKey(player.getUUID())) {
			return;
		}
		long now = player.level().getGameTime();
		HUMS.put(player.getUUID(), new Hum(now));
		ResonancePower.set(player, ResonancePower.state(player).withHum(now));
	}

	static void tick(ServerPlayer player) {
		Hum hum = HUMS.get(player.getUUID());
		if (hum == null) {
			return;
		}
		ServerLevel level = player.level();
		long held = level.getGameTime() - hum.from;
		if (!player.isAlive() || player.isSpectator() || !PowerInput.isDown(player, PowerKey.ABILITY_2)) {
			stop(player, true);
			return;
		}
		int tune = ResonancePower.tune(player);
		if (held % 4 == 0) {
			float pitch = 0.5F + 1.5F * held / MAX_HUM;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F,
				pitch * ResonancePower.pitch(tune) * 0.7F);
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), eye.distanceToSqr(end));
		if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
			clearCrack(player, level, hum);
			humCreature(player, level, hum, living, tune);
		} else if (blockHit.getType() == HitResult.Type.BLOCK) {
			if (humBlock(player, level, hum, blockHit.getBlockPos(), tune)) {
				stop(player, true);
				return;
			}
		} else {
			clearCrack(player, level, hum);
		}
		if (held >= MAX_HUM) {
			stop(player, true);
		}
	}

	private static void humCreature(ServerPlayer player, ServerLevel level, Hum hum, LivingEntity living, int tune) {
		if (living.getId() != hum.entityId) {
			hum.entityId = living.getId();
			hum.entityTicks = 0;
		}
		hum.entityTicks++;
		int every = Math.max(4, STRIP_EVERY - HIGH_STRIP_FASTER * Math.max(0, tune));
		if (hum.entityTicks % every == 0) {
			strip(living);
			hum.did = true;
			level.sendParticles(ParticleTypes.NOTE, living.getX(), living.getY() + living.getBbHeight() + 0.3, living.getZ(), 2, 0.3, 0.1, 0.3,
				(tune + 2) / 4.0);
		}
		if (hum.entityTicks % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 2,
				living.getBbWidth() * 0.4, living.getBbHeight() * 0.3, living.getBbWidth() * 0.4, 0.02);
		}
	}

	/** Every worn piece of armour loses {@code STRIP_SHARE} of its full durability. */
	private static void strip(LivingEntity living) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!slot.isArmor()) {
				continue;
			}
			ItemStack stack = living.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				stack.hurtAndBreak(Math.max(1, (int)Math.ceil(stack.getMaxDamage() * STRIP_SHARE)), living, slot);
			}
		}
	}

	/** Returns true once the block broke (the hum is spent). */
	private static boolean humBlock(ServerPlayer player, ServerLevel level, Hum hum, BlockPos pos, int tune) {
		if (!pos.equals(hum.block)) {
			clearCrack(player, level, hum);
			hum.block = pos.immutable();
			hum.blockTicks = 0;
			hum.warned = false;
		}
		BlockState state = level.getBlockState(pos);
		int need = ticksFor(level, pos, state, tune);
		if (need < 0 || need > MAX_HUM) {
			if (!hum.warned) {
				hum.warned = true;
				player.sendOverlayMessage(Component.translatable(need < 0 ? "message.jaymospowers.resonance_no_pitch"
					: "message.jaymospowers.resonance_too_deep").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			return false;
		}
		hum.blockTicks++;
		level.destroyBlockProgress(crackId(player), pos, Math.min(9, hum.blockTicks * 10 / Math.max(1, need)));
		if (hum.blockTicks % 3 == 0) {
			level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.2, 0.0, 0.2, (tune + 2) / 4.0);
		}
		if (hum.blockTicks < need) {
			return false;
		}
		level.destroyBlockProgress(crackId(player), pos, -1);
		hum.block = null;
		if (!ResonanceDamage.mayBreak(player, level, pos, state)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.resonance_protected").withStyle(ChatFormatting.GRAY));
			return true;
		}
		float hardness = state.getDestroySpeed(level, pos);
		level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
		ResonanceDamage.breakBlock(player, level, pos, state, hardness <= IRON_ORE_HARDNESS);
		hum.did = true;
		return true;
	}

	/** How long a hum takes to find this block's pitch (ticks), or -1 for never. */
	static int ticksFor(ServerLevel level, BlockPos pos, BlockState state, int tune) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return -1;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || hardness >= NEVER_HARDNESS) {
			return -1;
		}
		if (state.getSoundType() == SoundType.GLASS || state.is(BlockTags.ICE)) {
			return 1;
		}
		double ticks = hardness * STONE_TICKS / STONE_HARDNESS;
		if (tune < 0) {
			ticks *= 1.0 - LOW_SPEEDUP_PER_STEP * -tune;
		}
		return Math.max(1, (int)Math.ceil(ticks));
	}

	/** G let go (or the hum ran out): the creature hummed at takes the last note; the cooldown if anything happened. */
	static void stop(ServerPlayer player, boolean finish) {
		Hum hum = HUMS.remove(player.getUUID());
		if (hum == null) {
			return;
		}
		ServerLevel level = player.level();
		clearCrack(player, level, hum);
		if (finish && hum.entityId >= 0 && hum.entityTicks > 0 && level.getEntity(hum.entityId) instanceof LivingEntity living && living.isAlive()
			&& living.distanceTo(player) <= RANGE + 2.0) {
			living.hurtServer(level, ResonanceDamage.source(player, ResonanceDamage.PULSE), END_DAMAGE);
			hum.did = true;
		}
		ResonanceState state = ResonancePower.state(player).withHum(-1L);
		if (finish && hum.did) {
			state = state.withCooldown(ResonanceState.SHATTER, level.getGameTime(), ResonancePower.SHATTER_COOLDOWN);
		}
		if (com.jaymospowers.core.Powers.has(player, ResonancePower.INSTANCE)) {
			ResonancePower.set(player, state);
		}
	}

	private static void clearCrack(ServerPlayer player, ServerLevel level, Hum hum) {
		if (hum.block != null) {
			level.destroyBlockProgress(crackId(player), hum.block, -1);
			hum.block = null;
			hum.blockTicks = 0;
		}
	}

	private static int crackId(ServerPlayer player) {
		return 0x5E50_0000 ^ player.getId();
	}

	static void clear() {
		HUMS.clear();
	}
}
