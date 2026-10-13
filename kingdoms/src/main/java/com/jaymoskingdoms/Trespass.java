package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.9) Villagers grow suspicious of you in their home. Indoors near someone's bed (their house), with them there to
 * see you, you're watched: they stop what they're doing and stare ("can I help you?"), then come after you asking what
 * you're doing in their house, and word gets round that you're up to something (their guards will want to search you,
 * {@link Approaches}). Stay on and it's trespass, a light crime ({@link Theft}); stay on after that and the guards are
 * called. It builds faster at night and slower if you sneak; it fades once you're out of sight or out of the house.
 * Their friends (MCA hearts {@link #FRIEND_HEARTS}), family and anyone of rank in the realm are welcome.
 */
final class Trespass {
	/** How often it's checked (ticks); how far they see you from, and how near their bed counts as in their house. */
	static final int EVERY = 20;
	static final double SEE_RANGE = 12.0, HOUSE_RANGE = 6.0, HOUSE_HEIGHT = 4.0;
	/** Suspicion (about a point a second seen): they stare, then come over and ask, then call it trespass. */
	static final float NOTICE = 2.0F, WARN = 8.0F, OUT = 16.0F;
	/** How fast it builds at night, sneaking; how fast it fades out of sight. */
	static final float NIGHT_RATE = 1.5F, SNEAK_RATE = 0.5F, FADE = 1.0F;
	/** Hearts it costs with them when they ask what you're doing, and when they call it trespass. */
	static final int WARN_HEARTS = -2, OUT_HEARTS = -5;
	/** Friends: MCA hearts with them this high, or reputation with the realm. */
	static final int FRIEND_HEARTS = 40, FRIEND_REP = 300;
	/** Lines per stage (lang {@code talk.jaymoskingdoms.trespass.<stage>.N}). */
	static final int LINES = 3;
	static final double SPEECH_RANGE = 16.0;

	private static final class Watch {
		final Villager owner;
		float level;
		int stage;
		int calls;

		Watch(Villager owner) {
			this.owner = owner;
		}
	}

	/** Per player: who's watching them in their house. */
	private static final Map<UUID, Watch> WATCHES = new HashMap<>();

	private Trespass() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Trespass::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHES.clear());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WATCHES.remove(handler.player.getUUID()));
	}

	/** {@code mob} is keeping an eye on someone in their house (other work leaves them be). */
	static boolean watching(Entity mob) {
		for (Watch w : WATCHES.values()) {
			if (w.owner == mob && w.stage > 0) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			step(level, player, now);
		}
	}

	private static void step(ServerLevel level, ServerPlayer player, long now) {
		Watch w = WATCHES.get(player.getUUID());
		Faction f = player.isSpectator() || player.isCreative() ? null : Theft.lawAt(level, player.blockPosition());
		Villager seen = f == null || Theft.exempt(player, f) || Reputation.get(player, f.id()) >= FRIEND_REP ? null : owner(level, player, w);
		if (seen == null) {
			if (w != null) {
				w.level -= FADE;
				if (w.level <= 0) {
					if (w.stage >= 2 && w.owner.isAlive() && w.owner.distanceTo(player) < SEE_RANGE * 2) {
						say(level, w.owner, "leave");
					}
					WATCHES.remove(player.getUUID());
				}
			}
			return;
		}
		if (w == null || w.owner != seen) {
			w = new Watch(seen);
			WATCHES.put(player.getUUID(), w);
		}
		float rate = 1.0F;
		if (Homes.bedtime(level)) {
			rate *= NIGHT_RATE;
		}
		if (player.isShiftKeyDown()) {
			rate *= SNEAK_RATE;
		}
		w.level += rate;
		Villager owner = w.owner;
		// eyes on you
		owner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
		if (w.level >= NOTICE && w.stage < 1) {
			w.stage = 1;
			owner.getNavigation().stop();
			owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8F, 0.9F);
			say(level, owner, "notice");
		}
		if (w.level >= WARN && w.stage < 2) {
			w.stage = 2;
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.9F, 1.0F);
			say(level, owner, "warn");
			McaCompat.hearts(owner, player, WARN_HEARTS);
			Approaches.suspect(player, f.id(), now);
		}
		if (w.stage >= 2) {
			// (comes over to see you out)
			if (owner.distanceTo(player) > 2.5) {
				BehaviorUtils.setWalkAndLookTargetMemories(owner, player, 0.6F, 2);
			} else {
				owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
		} else {
			owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (w.level >= OUT) {
			// trespass: the first time a light crime; after that they call the guards
			McaCompat.hearts(owner, player, OUT_HEARTS);
			Theft.commit(player, level, f, Theft.Crime.TRESPASS, w.calls == 0 ? null : Theft.Weight.MEDIUM, false, List.of(owner));
			w.calls++;
			w.level = WARN;
		}
	}

	/** Whose house {@code player} is in, of those who see them there (whoever was watching first); null if nobody's. */
	private static @Nullable Villager owner(ServerLevel level, ServerPlayer player, @Nullable Watch was) {
		BlockPos at = player.blockPosition();
		if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) <= at.getY() + 1) {
			// (out under the sky: not in anyone's house)
			return null;
		}
		Villager best = null;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(at).inflate(SEE_RANGE), Villager::isAlive)) {
			if (v.isBaby() || v.isSleeping() || v.getTarget() != null || Frights.afraid(v.getUUID()) || !v.hasLineOfSight(player)) {
				continue;
			}
			GlobalPos home = McaCompat.home(v).orElse(null);
			if (home == null || home.dimension() != level.dimension()) {
				continue;
			}
			BlockPos bed = home.pos();
			double dx = bed.getX() + 0.5 - player.getX(), dz = bed.getZ() + 0.5 - player.getZ();
			if (dx * dx + dz * dz > HOUSE_RANGE * HOUSE_RANGE || Math.abs(bed.getY() - player.getY()) > HOUSE_HEIGHT) {
				continue;
			}
			if (McaCompat.isMca(v) && McaCompat.heartsOf(v, player) >= FRIEND_HEARTS) {
				// (a friend, a spouse, family: welcome, and so is anyone they're with)
				return null;
			}
			if (was != null && was.owner == v) {
				return v;
			}
			if (best == null) {
				best = v;
			}
		}
		return best;
	}

	private static void say(ServerLevel level, Villager owner, String stage) {
		Speech.say(level, owner, Component.translatable("talk.jaymoskingdoms.trespass." + stage + "." + level.getRandom().nextInt(LINES)),
			stage.equals("notice") ? Speech.WHITE : Speech.YELLOW, SPEECH_RANGE);
	}
}
