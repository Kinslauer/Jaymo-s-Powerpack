package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.Container;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * <b>The night burglar</b>: now and then at night, near a player's chests, a quiet young woman turns up, goes to the
 * nearest chest or barrel that holds a gem (a diamond, an emerald, an amethyst shard) and lifts one, then runs the
 * rooftops with it: she scrambles up two blocks at a step, jumps high and keeps to the high ground. Hit her
 * {@link #CATCH_HITS} times (or down her) before she is clear and she gives it back and slips away. Catch her three times
 * (one for each night she came) and the third time she stops running and asks for work: she can be hired at once, as any
 * drifter is, with a coin in hand. A player she has taken on isn't visited again. Locked chests are safe from her.
 */
final class NightBurglars {
	/** Ticks between looks (any player), the chance each look finds a night burglar, and the nights between visits to one player. */
	static final int LOOK_EVERY = 400;
	static final float CHANCE = 0.08F;
	static final long GAP_DAYS = 2L;
	/** How far (blocks) from the player she looks for a chest, across and up and down; how far she starts from it. */
	static final int REACH_XZ = 14, REACH_Y = 5, SPAWN_FROM = 18;
	/** Ticks she's given to get to the chest, and to get clear after. */
	static final long APPROACH_TICKS = 700L, ESCAPE_TICKS = 1800L;
	/** Blows that catch her, and her health share that does too. */
	static final int CATCH_HITS = 3;
	static final float CAUGHT_AT = 0.3F;
	/** Catches that make her ask for work. */
	static final int CATCHES_FOR_WORK = 3;
	/** Her step up (blocks, added), her speed up (a share), how far (blocks) she runs to a roof each time. */
	static final double STEP_UP = 1.0, SPEED_UP = 0.25, RUN_FAR = 30.0;
	static final int LINES = 3;
	static final String TAG = "jaymoskingdoms_night_burglar";
	static final Identifier STEP_ID = JaymosKingdoms.id("burglar_step"), SPEED_ID = JaymosKingdoms.id("burglar_speed");

	/** Per player: how many times they've caught a night burglar. Saved, kept through death. */
	static final AttachmentType<Integer> CAUGHT = AttachmentRegistry.create(JaymosKingdoms.id("burglars_caught"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** Per player: the day a burglar last came to them. Saved. */
	static final AttachmentType<Long> LAST_DAY = AttachmentRegistry.create(JaymosKingdoms.id("burglar_last_day"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	private enum Phase {
		APPROACH, RUN, LEAVING
	}

	/** One burglary: who, whose chest, what she took, how far on she is. */
	private static final class Burglary {
		final UUID burglar;
		final UUID player;
		final BlockPos chest;
		ItemStack gem = ItemStack.EMPTY;
		Phase phase = Phase.APPROACH;
		long until;
		int hits;
		BlockPos roof;

		Burglary(UUID burglar, UUID player, BlockPos chest, long until) {
			this.burglar = burglar;
			this.player = player;
			this.chest = chest;
			this.until = until;
		}
	}

	private static final Map<UUID, Burglary> ACTIVE = new HashMap<>();

	private NightBurglars() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NightBurglars::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
		// (one left in the world from before is gone)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && !ACTIVE.containsKey(entity.getUUID())) {
				entity.discard();
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Burglary b = ACTIVE.get(entity.getUUID());
			if (b != null && taken > 0.0F && entity instanceof Villager v && v.level() instanceof ServerLevel level) {
				struck(level, v, b, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Burglary b = ACTIVE.remove(entity.getUUID());
			if (b != null && entity.level() instanceof ServerLevel level) {
				give(level, entity, b);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (server.getTickCount() % LOOK_EVERY == 0 && NightTrouble.night(level)) {
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && level.getRandom().nextFloat() < CHANCE) {
					visit(level, player, now);
				}
			}
		}
		if (ACTIVE.isEmpty() || now % 10 != 0) {
			return;
		}
		for (Iterator<Burglary> it = ACTIVE.values().iterator(); it.hasNext();) {
			Burglary b = it.next();
			if (!(level.getEntity(b.burglar) instanceof Villager v) || !v.isAlive()) {
				it.remove();
				continue;
			}
			if (!step(level, v, b, now)) {
				it.remove();
			}
		}
	}

	// ---- she comes ---------------------------------------------------------------------------------------------------

	private static void visit(ServerLevel level, ServerPlayer player, long now) {
		long day = now / 24000L;
		Long last = player.getAttached(LAST_DAY);
		if (player.getAttachedOrElse(CAUGHT, 0) >= CATCHES_FOR_WORK || last != null && day - last < GAP_DAYS) {
			return;
		}
		Faction f = Factions.around(level.getServer(), level, player.blockPosition(), Villages.VILLAGE_RADIUS * 2.0);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			return;
		}
		BlockPos chest = gemChest(level, player.blockPosition());
		if (chest == null) {
			return;
		}
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		BlockPos far = chest.offset((int)(Math.cos(angle) * SPAWN_FROM), 0, (int)(Math.sin(angle) * SPAWN_FROM));
		BlockPos at = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far), 4);
		if (at == null || !level.isPositionEntityTicking(at)) {
			return;
		}
		Villager she = McaCompat.woman(level, Vec3.atBottomCenterOf(at));
		if (she == null) {
			return;
		}
		she.addTag(TAG);
		AttributeInstance step = she.getAttribute(Attributes.STEP_HEIGHT), speed = she.getAttribute(Attributes.MOVEMENT_SPEED);
		if (step != null) {
			step.addTransientModifier(new AttributeModifier(STEP_ID, STEP_UP, AttributeModifier.Operation.ADD_VALUE));
		}
		if (speed != null) {
			speed.addTransientModifier(new AttributeModifier(SPEED_ID, SPEED_UP, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		she.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, MobEffectInstance.INFINITE_DURATION, 1, false, false));
		player.setAttached(LAST_DAY, day);
		ACTIVE.put(she.getUUID(), new Burglary(she.getUUID(), player.getUUID(), chest, now + APPROACH_TICKS));
	}

	/** The nearest unlocked chest or barrel near {@code around} with a gem in it, or null. */
	private static @Nullable BlockPos gemChest(ServerLevel level, BlockPos around) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-REACH_XZ, -REACH_Y, -REACH_XZ), around.offset(REACH_XZ, REACH_Y, REACH_XZ))) {
			BlockEntity be = level.getBlockEntity(p);
			if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && Locks.lockOn(level, p) == null && gemSlot((Container)be) >= 0) {
				double d = p.distSqr(around);
				if (d < bestD) {
					bestD = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	private static int gemSlot(Container box) {
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack s = box.getItem(i);
			if (s.is(Items.DIAMOND) || s.is(Items.EMERALD) || s.is(Items.AMETHYST_SHARD)) {
				return i;
			}
		}
		return -1;
	}

	// ---- the job and the run -----------------------------------------------------------------------------------------

	/** One step of her; false once she's done with. */
	private static boolean step(ServerLevel level, Villager v, Burglary b, long now) {
		switch (b.phase) {
			case APPROACH -> {
				if (now > b.until || !(level.getBlockEntity(b.chest) instanceof Container box)) {
					vanish(level, v);
					return false;
				}
				if (v.distanceToSqr(Vec3.atCenterOf(b.chest)) <= 6.25) {
					int slot = gemSlot(box);
					if (slot < 0) {
						vanish(level, v);
						return false;
					}
					b.gem = box.removeItem(slot, 1);
					level.playSound(null, b.chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 1.2F);
					level.playSound(null, b.chest, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6F, 1.4F);
					if (level.getPlayerByUUID(b.player) instanceof ServerPlayer p) {
						p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.burglar.taken"));
					}
					b.phase = Phase.RUN;
					b.until = now + ESCAPE_TICKS;
					roof(level, v, b);
				} else if (now % 20 == 0) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, b.chest, 0.9F, 1);
				}
			}
			case RUN -> {
				if (now > b.until) {
					vanish(level, v);
					return false;
				}
				if (b.roof == null || v.distanceToSqr(Vec3.atCenterOf(b.roof)) < 9.0) {
					roof(level, v, b);
				} else if (now % 20 == 0) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, b.roof, 1.4F, 1);
				}
			}
			case LEAVING -> {
				if (now > b.until) {
					vanish(level, v);
					return false;
				}
			}
		}
		return true;
	}

	/** She makes for the highest roof among some places about {@link #RUN_FAR} blocks off, away from her pursuer. */
	private static void roof(ServerLevel level, Villager v, Burglary b) {
		RandomSource random = level.getRandom();
		ServerPlayer chaser = level.getPlayerByUUID(b.player) instanceof ServerPlayer p ? p : null;
		Vec3 away = chaser == null ? Vec3.ZERO : v.position().subtract(chaser.position()).multiply(1.0, 0.0, 1.0);
		double base = away.lengthSqr() < 1.0E-4 ? random.nextDouble() * Math.PI * 2.0 : Math.atan2(away.z, away.x);
		BlockPos best = null;
		for (int i = 0; i < 6; i++) {
			double a = base + (random.nextDouble() - 0.5) * 2.0;
			BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				BlockPos.containing(v.getX() + Math.cos(a) * RUN_FAR, 0.0, v.getZ() + Math.sin(a) * RUN_FAR));
			if (best == null || p.getY() > best.getY()) {
				best = p;
			}
		}
		b.roof = best;
		BehaviorUtils.setWalkAndLookTargetMemories(v, best, 1.4F, 1);
	}

	/** She was hit: the third blow, or a downing, catches her. */
	private static void struck(ServerLevel level, Villager v, Burglary b, DamageSource source) {
		Entity by = source.getEntity();
		boolean player = by instanceof ServerPlayer || by instanceof OwnableEntity own && own.getOwner() instanceof ServerPlayer;
		if (!player || b.phase != Phase.RUN) {
			return;
		}
		b.hits++;
		if (b.hits < CATCH_HITS && v.getHealth() > v.getMaxHealth() * CAUGHT_AT && !Downed.isDowned(v)) {
			return;
		}
		ServerPlayer catcher = by instanceof ServerPlayer sp ? sp : by instanceof OwnableEntity own && own.getOwner() instanceof ServerPlayer o ? o : null;
		give(level, v, b);
		if (catcher == null) {
			b.phase = Phase.LEAVING;
			b.until = level.getGameTime() + 100L;
			return;
		}
		int caught = catcher.getAttachedOrElse(CAUGHT, 0) + 1;
		catcher.setAttached(CAUGHT, caught);
		if (caught >= CATCHES_FOR_WORK) {
			asksForWork(level, v, catcher);
			ACTIVE.remove(v.getUUID());
			return;
		}
		Speech.tell(level, v, catcher, Component.translatable("speech.jaymoskingdoms.burglar.caught." + level.getRandom().nextInt(LINES)), Speech.WHITE);
		b.phase = Phase.LEAVING;
		b.until = level.getGameTime() + 100L;
		Crooks.flee(level, v, catcher.position());
	}

	/** What she took falls at her feet (once). */
	private static void give(ServerLevel level, Entity v, Burglary b) {
		if (!b.gem.isEmpty()) {
			v.spawnAtLocation(level, b.gem);
			b.gem = ItemStack.EMPTY;
		}
	}

	/** The third time: she stops running, and is for hire like any drifter. */
	private static void asksForWork(ServerLevel level, Villager v, ServerPlayer player) {
		v.removeTag(TAG);
		v.removeEffect(MobEffects.JUMP_BOOST);
		v.getNavigation().stop();
		v.setHealth(v.getMaxHealth());
		v.setPersistenceRequired();
		// (for hire as any drifter is: a coin in hand, {@link Drifters})
		v.setAttached(Drifters.DRIFTER, Long.MAX_VALUE / 2);
		if (McaCompat.LOADED && v instanceof net.conczin.mca.entity.VillagerEntityMCA mca) {
			Drifters.weaken(mca);
		}
		Speech.tell(level, v, player, Component.translatable("speech.jaymoskingdoms.burglar.work." + level.getRandom().nextInt(LINES), Veterans.price(v)), Speech.WHITE);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY(0.8), v.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
	}

	private static void vanish(ServerLevel level, Villager v) {
		level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY(0.5), v.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		v.discard();
	}
}
