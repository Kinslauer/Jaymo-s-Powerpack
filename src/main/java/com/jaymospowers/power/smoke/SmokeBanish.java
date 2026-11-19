package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sneak + B, Banish (Andy: "smoke"): smoke wraps the creature you aim at (within {@link #RANGE} blocks) and it is gone,
 * banished into the smoke for {@link #TICKS}. It is hidden (invisible and silent), it cannot act (no AI, no gravity) and
 * nothing hurts it; it stays where it was, and a knot of smoke lingers on the spot. A blow on the knot (the banished
 * creature's own hitbox) cuts {@link #HIT_CUT} ticks off the wait and sets the smoke churning. When the time is up it comes
 * back where it went, dazed ({@link #DAZE} ticks of slowness). Players, tamed pets and bosses (the Dragon, the Wither, the
 * Warden, anything with {@link #BOSS_HEALTH} health or more) cannot be banished.
 * <p>
 * It always comes back: the end time and what the creature was before are kept on the creature itself (saved with it), so
 * a chunk unload or a server stop only pauses the wait, and a banished creature that loads is put back in the count.
 */
public final class SmokeBanish {
	public static final double RANGE = 20.0;
	/** How long it lasts, what a blow takes off, how long the daze lasts after (ticks). */
	public static final int TICKS = 20 * 10, HIT_CUT = 20 * 3, DAZE = 20 * 4;
	/** Ticks before another, hunger it costs. */
	public static final int COOLDOWN = 20 * 25;
	public static final float EXHAUSTION = 2.0F;
	/** At this much health a creature counts as a boss. */
	public static final float BOSS_HEALTH = 150.0F;
	static final int GREY = 0x6B6B72;

	/** When the banishment ends (game time), on the banished creature. Saved with it. */
	private static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(JaymosPowers.id("smoke_banished_until"),
		builder -> builder.persistent(Codec.LONG));
	/** What it was before (bit 0 invisible, 1 no AI, 2 silent, 3 no gravity), on the banished creature. Saved with it. */
	private static final AttachmentType<Integer> WAS = AttachmentRegistry.create(JaymosPowers.id("smoke_banished_was"),
		builder -> builder.persistent(Codec.INT));

	private static final Map<UUID, Long> BANISHED = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private SmokeBanish() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SmokeBanish::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			Long until = entity.getAttached(UNTIL);
			if (until != null) {
				BANISHED.put(entity.getUUID(), until);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Long until = BANISHED.isEmpty() ? null : entity.getAttached(UNTIL);
			if (until == null) {
				return true;
			}
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			if (source.getDirectEntity() instanceof LivingEntity && entity.level() instanceof ServerLevel level) {
				// a blow on the knot: the smoke tears and lets it go sooner
				long left = until - HIT_CUT;
				entity.setAttached(UNTIL, left);
				BANISHED.put(entity.getUUID(), left);
				level.sendParticles(MagicFx.miasma(GREY, 1.2F), entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.5F, 1.4F);
			}
			return false;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> BANISHED.remove(entity.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READY.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BANISHED.clear();
			READY.clear();
		});
	}

	private static boolean boss(LivingEntity entity) {
		return entity instanceof EnderDragon || entity instanceof WitherBoss || entity instanceof Warden || entity.getMaxHealth() >= BOSS_HEALTH;
	}

	/** Banishes the creature {@code player} aims at (sneak + B). */
	static void cast(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long left = READY.getOrDefault(player.getUUID(), 0L) - now;
		if (left > 0 && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_banish_wait", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		EntityHitResult aimed = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(RANGE)),
			player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0),
			e -> e instanceof Mob mob && mob.isAlive() && !BANISHED.containsKey(e.getUUID()), RANGE * RANGE);
		if (aimed == null || !(aimed.getEntity() instanceof Mob target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_banish_nothing").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (boss(target) || target instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_banish_too_strong").withStyle(ChatFormatting.GRAY));
			return;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		player.causeFoodExhaustion(EXHAUSTION);
		int was = (target.isInvisible() ? 1 : 0) | (target.isNoAi() ? 2 : 0) | (target.isSilent() ? 4 : 0) | (target.isNoGravity() ? 8 : 0);
		target.setAttached(WAS, was);
		target.setAttached(UNTIL, now + TICKS);
		BANISHED.put(target.getUUID(), now + TICKS);
		target.setTarget(null);
		target.getNavigation().stop();
		target.setDeltaMovement(Vec3.ZERO);
		target.setInvisible(true);
		target.setNoAi(true);
		target.setSilent(true);
		target.setNoGravity(true);
		SmokeField.puff(level, target.getEyePosition(), 3.0F, player);
		MagicFx.blast(level, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1.4F, GREY);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
		com.jaymospowers.core.trials.PowerTrials.count(player, "smoke.banish", 1);
	}

	private static void release(Mob mob) {
		int was = mob.getAttachedOrElse(WAS, 0);
		mob.removeAttached(UNTIL);
		mob.removeAttached(WAS);
		BANISHED.remove(mob.getUUID());
		mob.setInvisible((was & 1) != 0);
		mob.setNoAi((was & 2) != 0);
		mob.setSilent((was & 4) != 0);
		mob.setNoGravity((was & 8) != 0);
		if (mob.level() instanceof ServerLevel level) {
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DAZE, 2));
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DAZE, 0));
			SmokeField.puff(level, mob.getEyePosition(), 2.0F, null);
			MagicFx.blast(level, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 1.2F, GREY);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 0.8F);
		}
	}

	private static void tick(MinecraftServer server) {
		if (BANISHED.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, Long>> it = BANISHED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> entry = it.next();
			Entity found = null;
			for (ServerLevel level : server.getAllLevels()) {
				found = level.getEntity(entry.getKey());
				if (found != null) {
					break;
				}
			}
			if (found == null) {
				// unloaded: it keeps its time on itself and is counted again when it loads
				continue;
			}
			if (!(found instanceof Mob mob) || !mob.isAlive() || mob.getAttached(UNTIL) == null) {
				it.remove();
				continue;
			}
			if (now >= entry.getValue()) {
				it.remove();
				release(mob);
				continue;
			}
			if (now % 10 == 0 && mob.level() instanceof ServerLevel level) {
				// the knot: smoke curling where it went
				level.sendParticles(MagicFx.miasma(GREY, 1.0F), mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 4, 0.25, 0.3, 0.25, 0.01);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
			}
		}
	}
}
