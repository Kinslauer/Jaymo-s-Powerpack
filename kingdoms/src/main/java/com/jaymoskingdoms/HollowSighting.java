package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import com.mojang.serialization.Codec;

/**
 * Andy (2026-11-14), after rising a Hollow and being carried into Caelreach: "would those villagers react after noticing you
 * are now undead? they should". A player who died and rose one of the Hollow ({@link Hollows#turn}) is met with it by every
 * townsperson the first time they see them: one who knew them (MCA hearts) cries out their name and grieves, a stranger
 * screams at the dead walking, a child shrieks. Those who mind the Hollow ({@link Prejudice#minds}) back away and run;
 * each loses hearts and remembers it ({@link Recollections.Kind#SAW_TURNED}), so they bring it up later and tell others.
 * A full mask hides it, as it hides your blood. Born Hollow aren't met this way: only those folk saw die and get up.
 */
public final class HollowSighting {
	/** How often risen players are looked at (ticks), how far townsfolk notice, and at most this many react at once. */
	static final int TICK = 20, AT_ONCE = 3;
	static final double NOTICE_RANGE = 12.0, SPEECH_RANGE = 16.0;
	/** One with at least this many hearts with the player knew them; hearts lost by one who knew them, and by a stranger. */
	static final int KNEW_HEARTS = 10, KNEW_LOST = 15, STRANGER_LOST = 5;
	/** How far (blocks) and how fast one who minds runs from them. */
	static final double FLEE_DISTANCE = 10.0;
	static final float FLEE_SPEED = 0.8F;
	/** Lines: one who knew them, a stranger, a child ({@code hollow_sighting.jaymoskingdoms.<kind>.<n>}). */
	static final int KNEW_LINES = 5, STRANGER_LINES = 5, CHILD_LINES = 3;

	/** Whether a player died and rose one of the Hollow (not born one). Saved; ends when they're no longer Hollow. */
	public static final AttachmentType<Boolean> RISEN = AttachmentRegistry.create(JaymosKingdoms.id("hollow_risen"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private HollowSighting() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HollowSighting::tick);
	}

	/** {@code player} just rose one of the Hollow. */
	static void rose(ServerPlayer player) {
		player.setAttached(RISEN, true);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICK != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.hasAttached(RISEN)) {
				continue;
			}
			if (!Heritage.is(p, Heritage.Kind.HOLLOW)) {
				p.removeAttached(RISEN);
				continue;
			}
			if (Prejudice.masked(p) || p.isSpectator() || p.isCreative() || !p.isAlive()) {
				continue;
			}
			ServerLevel level = p.level();
			List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(NOTICE_RANGE),
				x -> x.isAlive() && !x.isSleeping() && Recollections.rememberer(x) && x.hasLineOfSight(p) && !saw(x, p));
			int reacted = 0;
			for (LivingEntity by : near) {
				if (reacted++ >= AT_ONCE) {
					break;
				}
				react(level, by, p);
			}
		}
	}

	private static boolean saw(LivingEntity who, ServerPlayer player) {
		for (Recollections.Recollection r : Recollections.of(who, player)) {
			if (r.type() == Recollections.Kind.SAW_TURNED) {
				return true;
			}
		}
		return false;
	}

	private static void react(ServerLevel level, LivingEntity by, ServerPlayer p) {
		RandomSource random = level.getRandom();
		boolean knew = by instanceof Villager v && McaCompat.heartsOf(v, p) >= KNEW_HEARTS;
		Recollections.remember(by, p, Recollections.Kind.SAW_TURNED, Component.empty());
		McaCompat.hearts(by, p, -(knew ? KNEW_LOST : STRANGER_LOST));
		if (by instanceof Mob m) {
			m.getLookControl().setLookAt(p, 30.0F, 30.0F);
		}
		String key = knew ? "knew." + random.nextInt(KNEW_LINES) : by.isBaby() ? "child." + random.nextInt(CHILD_LINES)
			: "stranger." + random.nextInt(STRANGER_LINES);
		Speech.say(level, by, Component.translatable("hollow_sighting.jaymoskingdoms." + key, Recollections.nameTo(p, by)),
			knew ? Speech.WHITE : Speech.RED, SPEECH_RANGE);
		level.playSound(null, by.blockPosition(), knew ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.8F,
			by.isBaby() ? 1.5F : 1.0F);
		if (by.isBaby() || Prejudice.minds(by, p) > 0.0F) {
			flee(by, p);
		}
		JaymosKingdoms.LOGGER.info("{} saw {} risen Hollow ({})", by.getName().getString(), p.getScoreboardName(), knew ? "knew them" : "a stranger");
	}

	/** {@code who} runs from {@code from}. */
	private static void flee(LivingEntity who, ServerPlayer from) {
		if (!(who instanceof net.minecraft.world.entity.PathfinderMob mob)) {
			return;
		}
		Vec3 to = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(mob, (int) FLEE_DISTANCE, 4, from.position());
		if (to == null) {
			Vec3 away = who.position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 1.0E-4) {
				return;
			}
			to = who.position().add(away.normalize().scale(FLEE_DISTANCE));
		}
		if (who instanceof Villager v) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, FLEE_SPEED, 1));
		} else {
			mob.getNavigation().moveTo(to.x, to.y, to.z, 1.2);
		}
	}
}
