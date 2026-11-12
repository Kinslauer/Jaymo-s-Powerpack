package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Catching your breath (fifth edition's short rest). Sit quietly for a minute at a lit fire, on a bench or stool, or on a
 * horse standing still, with nothing hunting you and no blow taken lately, and some of your wounds close. Each day there
 * is only so much of this in you, a reserve of health sized by Endurance and a little by class; every breath spends what
 * it healed. A full night's sleep, to the morning, fills the reserve again.
 * <p>
 * It only heals up to your full health. The hearts hunger has taken off the top (the food hearts) are never given back by
 * it: those come back by eating, as they always have.
 */
public final class CatchBreath {
	/** The reserve (health points) with no Endurance, and what the top of Endurance adds. */
	static final float BASE_RESERVE = 8.0F, ENDURANCE_RESERVE = 12.0F;
	/** What a class adds to the reserve: a Survivalist most, a Brute and a Warlord next, then the rest. */
	static final float SURVIVALIST = 4.0F, BRUTE = 3.0F, WARLORD = 2.0F, RIDER = 2.0F, LIGHT_HANDS = 1.0F;
	/** The sitting it takes (ticks): a minute. */
	static final int REST_TICKS = 1200;
	/** What one minute heals at most (health points): the base, and what the top of Endurance adds. */
	static final float DIE = 4.0F, DIE_ENDURANCE = 4.0F;
	/** How near (blocks) a lit fire must be, and how near the hunters that spoil a rest. */
	static final int FIRE = 4;
	static final double HUNTERS = 12.0;
	/** A blow taken this recently (ticks) spoils the rest. */
	static final int HURT_GAP = 200;
	/** A horse moving faster than this (blocks a tick) isn't standing still. */
	static final double STANDING = 0.01;
	/** Sitting is looked at this often (ticks). */
	static final int EVERY = 20;
	/** A rest that has wandered this far (blocks) from where it began starts over. */
	static final double WANDER = 1.5;

	/** Health points the player has already breathed back today. Saved; a night's sleep clears it. */
	static final AttachmentType<Float> SPENT = AttachmentRegistry.create(JaymosKingdoms.id("breath_spent"),
		builder -> builder.persistent(Codec.FLOAT));

	/** One sitting: when it began, where, and whether it has been told to the player. */
	private static final class Sitting {
		int ticks;
		net.minecraft.world.phys.Vec3 at;
		boolean told;
	}

	private static final Map<UUID, Sitting> SITTING = new HashMap<>();
	private static final Map<UUID, Long> HURT = new HashMap<>();
	/** Players who have slept right through the night and are still in bed. */
	private static final Set<UUID> SLEPT = new HashSet<>();

	private CatchBreath() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CatchBreath::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof ServerPlayer p) {
				HURT.put(p.getUUID(), p.level().getGameTime());
				SITTING.remove(p.getUUID());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SITTING.clear();
			HURT.clear();
			SLEPT.clear();
		});
	}

	private static void forget(UUID id) {
		SITTING.remove(id);
		HURT.remove(id);
		SLEPT.remove(id);
	}

	/** The most health {@code player} can breathe back in a day. */
	public static float reserve(ServerPlayer player) {
		float r = BASE_RESERVE + ENDURANCE_RESERVE * Saves.share(player, Stats.Stat.ENDURANCE);
		Classes.PlayerClass cls = Classes.classOf(player);
		if (cls != null) {
			r += switch (cls) {
				case SURVIVALIST -> SURVIVALIST;
				case BRUTE -> BRUTE;
				case WARLORD -> WARLORD;
				case RIDER -> RIDER;
				case ARCHER, MARKSMAN, THIEF -> LIGHT_HANDS;
				default -> 0.0F;
			};
		}
		return r;
	}

	/** How much of today's reserve {@code player} has left. */
	public static float left(ServerPlayer player) {
		return Math.max(0.0F, reserve(player) - player.getAttachedOrElse(SPENT, 0.0F));
	}

	private static void tick(MinecraftServer server) {
		boolean now = server.getTickCount() % EVERY == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sleep(player);
			if (now) {
				sit(player);
			}
		}
	}

	/** A night slept through to the morning fills the reserve. */
	private static void sleep(ServerPlayer player) {
		UUID id = player.getUUID();
		if (player.isSleeping()) {
			if (player.isSleepingLongEnough() && !BedRest.resting(player)) {
				SLEPT.add(id);
			}
			return;
		}
		if (SLEPT.remove(id) && player.level().isBrightOutside() && player.getAttachedOrElse(SPENT, 0.0F) > 0.0F) {
			player.removeAttached(SPENT);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.breath.slept"));
		}
	}

	private static void sit(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level) || !resting(level, player)) {
			SITTING.remove(id);
			return;
		}
		Sitting s = SITTING.computeIfAbsent(id, k -> new Sitting());
		if (s.at == null || s.at.distanceTo(player.position()) > WANDER) {
			s.at = player.position();
			s.ticks = 0;
			s.told = false;
		}
		if (!calm(level, player)) {
			s.ticks = 0;
			return;
		}
		boolean hurt = player.getHealth() < player.getMaxHealth();
		float left = left(player);
		if (!hurt || left <= 0.0F) {
			s.ticks = 0;
			if (hurt && !s.told) {
				s.told = true;
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.breath.spent"));
			}
			return;
		}
		if (s.ticks == 0 && !s.told) {
			s.told = true;
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.breath.settle"));
		}
		s.ticks += EVERY;
		if (s.ticks % 200 == 0) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
		}
		if (s.ticks < REST_TICKS) {
			return;
		}
		s.ticks = 0;
		s.told = false;
		float die = DIE + DIE_ENDURANCE * Saves.share(player, Stats.Stat.ENDURANCE);
		float want = Math.min(Math.min(die, left), player.getMaxHealth() - player.getHealth());
		float healed = PowersCompat.mend(player, want);
		if (healed <= 0.0F) {
			return;
		}
		player.setAttached(SPENT, player.getAttachedOrElse(SPENT, 0.0F) + healed);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 1.0F);
		level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2, player.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.breath.caught", Math.round(left(player) / 2.0F)));
	}

	/** Whether {@code player} is sat the way a rest wants: at a lit fire, on a bench or stool, or on a horse standing still. */
	static boolean resting(ServerLevel level, ServerPlayer player) {
		if (player.isSleeping() || player.isSprinting() || player.isSwimming() || PowersCompat.helpless(player)) {
			return false;
		}
		if (player.getVehicle() instanceof Cushion) {
			return true;
		}
		if (player.getVehicle() instanceof AbstractHorse horse) {
			return horse.getDeltaMovement().horizontalDistance() < STANDING && horse.isAlive();
		}
		return player.getVehicle() == null && PowersCompat.meditating(player) && litFire(level, player.blockPosition());
	}

	private static boolean litFire(ServerLevel level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-FIRE, -2, -FIRE), at.offset(FIRE, 2, FIRE))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.CAMPFIRES) && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)
				|| state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
				return true;
			}
		}
		return false;
	}

	/** Nothing hunting them near, and no blow lately. */
	private static boolean calm(ServerLevel level, ServerPlayer player) {
		@Nullable Long hurt = HURT.get(player.getUUID());
		if (hurt != null && level.getGameTime() - hurt < HURT_GAP) {
			return false;
		}
		AABB box = player.getBoundingBox().inflate(HUNTERS);
		return level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.getTarget() == player).isEmpty();
	}
}
