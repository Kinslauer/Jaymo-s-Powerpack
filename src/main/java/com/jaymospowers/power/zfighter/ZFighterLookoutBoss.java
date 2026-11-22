package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.KingdomsMoney;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Downed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The Keeper's trial (Z Fighter gaps 7, Andy's set-piece boss): the Chamber-Worn, a pupil of the Keeper who stayed in the
 * Time Chamber, comes out of its door onto the Lookout when a fighter with the Keeper's blessing asks for the trial. He is a
 * tyrant ({@link ZFighterRogue}, the player model, no new mob) with a set fight:
 * <ul>
 * <li><b>Phase 1</b>: he fights as a tyrant in Super. <b>Phase 2</b> (half health, his Super 2 with its roar): every
 * {@link #WAVE_EVERY} ticks he stops, rises and gathers a great wave for {@link #GATHER_TICKS} (a told-ahead tell: light
 * pouring up round him, a rising hum, a line on everyone's screen), then fires it at his foe. It is a clashable NPC beam
 * ({@link ZFighterNpcKi#fireBeam}): meet it with your own and it locks into a power clash ({@link com.jaymospowers.core.BeamClash}).</li>
 * <li><b>Weak point</b>: after the wave he is spent for {@link #SPENT_TICKS}: he hangs still, turned one way, a mark of light on
 * his back; blows from behind take {@link #BACK_HIT} times as much, from in front {@link #FRONT_HIT}.</li>
 * <li><b>Shared revives</b>: each fighter on the Lookout can be brought round by friends {@link #REVIVES} times (the pack's
 * downed and revive). Downed once more after that, the Keeper pulls them through the Chamber door, out of the fight.</li>
 * <li><b>The prize</b>: {@link #TRIAL_TP} TP (silver and experience for one with no ki) split by how much each hurt him, the
 * top fighter's share {@link #TOP_BONUS} more, and named to everyone.</li>
 * </ul>
 * With nobody left on the Lookout for {@link #EMPTY_TICKS} he goes back through the door. One trial at a time; a fighter may
 * ask once a day.
 */
public final class ZFighterLookoutBoss {
	/** The tag the Chamber-Worn carries (his tyrant prize is paid here, not by {@link ZFighterRaids}). */
	static final String TAG = "jaymospowers_lookout_boss";
	static final long DAY = 24000L;
	/** His health: this, and this much more for each fighter on the Lookout past the first when he comes out. */
	static final double HEALTH = 900.0, HEALTH_PER_FIGHTER = 300.0;
	/** Phase 2 starts below this share of his health (his Super 2 comes at half). */
	static final float PHASE_2 = 0.5F;
	/** The great wave: ticks between them in phase 2, ticks of gathering (the tell), its damage as times his blow. */
	static final int WAVE_EVERY = 400, GATHER_TICKS = 60;
	static final double WAVE_FISTS = 2.0;
	/** Ticks after the wave leaves his hands before he slumps (the beam is held about this long). */
	static final int WAVE_HOLD = 40;
	/** Spent after the wave: how long, and what blows from behind and in front take. */
	static final int SPENT_TICKS = 100;
	static final float BACK_HIT = 2.5F, FRONT_HIT = 1.25F;
	/** How far behind him a blow must come from to count as his back (dot of his facing and the way to the attacker). */
	static final double BEHIND = -0.3;
	/** Times each fighter can be brought round in the trial. */
	static final int REVIVES = 3;
	/** The prize: TP shared by hurt, the least a share pays, the top fighter's extra; silver and experience for one with no ki. */
	static final int TRIAL_TP = 3000, MIN_TP = 200;
	static final float TOP_BONUS = 0.25F;
	static final int TRIAL_SILVER = 64, MIN_SILVER = 10, TRIAL_XP = 40;
	/** Who counts as on the Lookout: this far (flat) from the pole, at or above this much under the floor. */
	static final double ARENA = ZFighterLookout.RADIUS + 8.0;
	static final int BELOW = 6;
	/** He's put back by the door if he strays this far (flat) or falls this far under the floor. */
	static final double LEASH = ZFighterLookout.RADIUS + 14.0;
	static final int LEASH_BELOW = 20;
	/** Ticks with nobody on the Lookout before he goes back. */
	static final int EMPTY_TICKS = 600;
	static final int COLOR = 0xB8A0FF;

	/** The trial now, or null. */
	private static Trial trial;
	/** When each player last asked for it (game time). */
	private static final Map<UUID, Long> ASKED = new HashMap<>();

	private static final class Trial {
		final UUID boss;
		final Map<UUID, Integer> downs = new HashMap<>();
		final Set<UUID> down = new HashSet<>();
		final Set<UUID> out = new HashSet<>();
		int waveIn = WAVE_EVERY / 2;
		/** Ticks left gathering, then holding the wave, then spent; 0 when none. */
		int gather, hold, spent;
		float spentYaw;
		int empty;

		Trial(UUID boss) {
			this.boss = boss;
		}
	}

	private ZFighterLookoutBoss() {
	}

	/** From {@link ZFighterRaids#init}. */
	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterLookoutBoss::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			trial = null;
			ASKED.clear();
		});
	}

	/** Whether the Keeper will open the door for {@code player} now (blessed today, no trial on, not asked today). */
	static boolean canAsk(ServerPlayer player, boolean blessed) {
		Long last = ASKED.get(player.getUUID());
		return blessed && trial == null && (last == null || player.level().getGameTime() - last >= DAY);
	}

	/** The Keeper opens the Chamber door: the Chamber-Worn steps out. */
	static void begin(ServerPlayer asker) {
		ServerLevel level = asker.level();
		ZFighterSites.Data data = level.getAttached(ZFighterSites.DATA);
		if (trial != null || data == null || data.tower().isEmpty()) {
			return;
		}
		BlockPos tower = data.tower().get();
		ZFighterRogue boss = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
		if (boss == null) {
			return;
		}
		ASKED.put(asker.getUUID(), level.getGameTime());
		Vec3 door = door(tower);
		boss.snapTo(door.x, door.y, door.z, 180.0F, 0.0F);
		boss.become(ZFighterRogue.TYRANT, Component.translatable("entity.jaymospowers.rogue_fighter.chamber_worn"));
		boss.addTag(TAG);
		int fighters = Math.max(1, fighters(level, tower).size());
		boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH + HEALTH_PER_FIGHTER * (fighters - 1));
		boss.setHealth(boss.getMaxHealth());
		level.addFreshEntity(boss);
		trial = new Trial(boss.getUUID());
		level.sendParticles(MagicFx.LIGHT, door.x, door.y + 1.0, door.z, 60, 0.5, 1.0, 0.5, 0.1);
		level.playSound(null, door.x, door.y, door.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.5F, 1.4F);
		asker.sendSystemMessage(Component.translatable("talk.jaymospowers.keeper.trial_open").withStyle(ChatFormatting.AQUA));
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_trial_begin",
			boss.getDisplayName(), REVIVES).withStyle(ChatFormatting.LIGHT_PURPLE), false);
	}

	/** Where he comes out: the Chamber door's step, where those coming back through it arrive. */
	private static Vec3 door(BlockPos tower) {
		return new Vec3(tower.getX() + 0.5, ZFighterSites.LOOKOUT_Y + 2, tower.getZ() - 1.5);
	}

	private static List<ServerPlayer> fighters(ServerLevel level, BlockPos tower) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - tower.getX() - 0.5, dz = player.getZ() - tower.getZ() - 0.5;
			if (!player.isSpectator() && !player.isCreative() && dx * dx + dz * dz <= ARENA * ARENA && player.getY() >= ZFighterSites.LOOKOUT_Y - BELOW) {
				list.add(player);
			}
		}
		return list;
	}

	private static boolean is(Entity boss) {
		return trial != null && boss.getUUID().equals(trial.boss);
	}

	private static void tick(MinecraftServer server) {
		if (trial == null) {
			return;
		}
		ServerLevel level = server.overworld();
		ZFighterSites.Data data = level.getAttached(ZFighterSites.DATA);
		if (!(level.getEntity(trial.boss) instanceof ZFighterRogue boss) || !boss.isAlive() || data == null || data.tower().isEmpty()) {
			// gone (killed, unloaded, or the world changed under him)
			trial = null;
			return;
		}
		BlockPos tower = data.tower().get();
		List<ServerPlayer> fighters = fighters(level, tower);
		fighters.removeIf(p -> trial.out.contains(p.getUUID()));
		if (fighters.isEmpty()) {
			if (++trial.empty >= EMPTY_TICKS) {
				withdraw(level, boss, tower);
				return;
			}
		} else {
			trial.empty = 0;
		}
		revives(level, fighters);
		leash(level, boss, tower);
		phases(level, boss, fighters);
	}

	/** Counts each fighter's falls; past {@link #REVIVES}, the Keeper pulls them through the Chamber door. */
	private static void revives(ServerLevel level, List<ServerPlayer> fighters) {
		for (ServerPlayer player : fighters) {
			UUID id = player.getUUID();
			boolean downed = Downed.isDowned(player);
			if (downed && trial.down.add(id)) {
				int downs = trial.downs.merge(id, 1, Integer::sum);
				if (downs > REVIVES) {
					Downed.getUp(level, player);
					if (!Downed.isDowned(player)) {
						trial.out.add(id);
						trial.down.remove(id);
						player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_trial_carried").withStyle(ChatFormatting.AQUA));
						ZFighterSites.pass(player, ZFighterSites.TIME_CHAMBER);
					}
				} else {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_trial_revives", REVIVES - downs + 1)
						.withStyle(ChatFormatting.LIGHT_PURPLE));
				}
			} else if (!downed) {
				trial.down.remove(id);
			}
		}
	}

	/** Back to the door if he strays off the Lookout or falls from it. */
	private static void leash(ServerLevel level, ZFighterRogue boss, BlockPos tower) {
		double dx = boss.getX() - tower.getX() - 0.5, dz = boss.getZ() - tower.getZ() - 0.5;
		if (dx * dx + dz * dz > LEASH * LEASH || boss.getY() < ZFighterSites.LOOKOUT_Y - LEASH_BELOW) {
			Vec3 door = door(tower);
			boss.teleportTo(door.x, door.y, door.z);
			boss.setDeltaMovement(Vec3.ZERO);
			level.sendParticles(ParticleTypes.CLOUD, door.x, door.y + 1.0, door.z, 12, 0.3, 0.6, 0.3, 0.05);
		}
	}

	/** Phase 2: the gathered wave, then the spent moment with his back open. */
	private static void phases(ServerLevel level, ZFighterRogue boss, List<ServerPlayer> fighters) {
		if (trial.spent > 0) {
			trial.spent--;
			if (boss.tickCount % 3 == 0) {
				// the mark on his back
				double yaw = Math.toRadians(trial.spentYaw);
				Vec3 back = boss.position().add(Math.sin(yaw) * 0.45, 1.3, -Math.cos(yaw) * 0.45);
				level.sendParticles(MagicFx.wisp(COLOR, 1.2F), back.x, back.y, back.z, 2, 0.08, 0.15, 0.08, 0.0);
			}
			return;
		}
		if (trial.hold > 0) {
			if (--trial.hold == 0) {
				trial.spent = SPENT_TICKS;
				trial.spentYaw = boss.getYRot();
				boss.setNoGravity(false);
				tell(fighters, "message.jaymospowers.zfighter_trial_spent", boss);
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.HOSTILE, 2.0F, 0.6F);
			}
			return;
		}
		if (trial.gather > 0) {
			trial.gather--;
			boss.setDeltaMovement(0.0, trial.gather > GATHER_TICKS / 2 ? 0.04 : 0.0, 0.0);
			if (boss.tickCount % 2 == 0) {
				level.sendParticles(MagicFx.wisp(COLOR, 1.6F), boss.getX(), boss.getY() + 0.2, boss.getZ(), 4, 0.6, 0.1, 0.6, 0.08);
			}
			if (boss.tickCount % 10 == 0) {
				float fill = 1.0F - trial.gather / (float)GATHER_TICKS;
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.6F + fill);
			}
			if (trial.gather == 0) {
				LivingEntity foe = foe(boss, fighters);
				if (foe != null) {
					ZFighterNpcKi.fireBeam(level, boss, foe, boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * WAVE_FISTS, COLOR);
				}
				trial.hold = WAVE_HOLD;
			}
			return;
		}
		if (boss.getHealth() < boss.getMaxHealth() * PHASE_2 && --trial.waveIn <= 0 && foe(boss, fighters) != null) {
			trial.waveIn = WAVE_EVERY;
			trial.gather = GATHER_TICKS;
			boss.setNoGravity(true);
			tell(fighters, "message.jaymospowers.zfighter_trial_gather", boss);
			level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 0.5F);
		}
	}

	/** His foe: the one he's after if they're a fighter, else the nearest fighter. */
	private static LivingEntity foe(ZFighterRogue boss, List<ServerPlayer> fighters) {
		LivingEntity target = boss.getTarget();
		if (target instanceof ServerPlayer p && fighters.contains(p) && !Downed.isDowned(p)) {
			return target;
		}
		ServerPlayer nearest = null;
		for (ServerPlayer p : fighters) {
			if (!Downed.isDowned(p) && (nearest == null || p.distanceToSqr(boss) < nearest.distanceToSqr(boss))) {
				nearest = p;
			}
		}
		return nearest;
	}

	private static void tell(List<ServerPlayer> fighters, String key, ZFighterRogue boss) {
		for (ServerPlayer p : fighters) {
			p.sendOverlayMessage(Component.translatable(key, boss.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	/**
	 * (Hook in {@link ZFighterRogue#customServerAiStep}) Whether he is held by the set fight just now (gathering, letting the
	 * wave go, or spent): his own fighting waits, and he stays where he is.
	 */
	static boolean holding(ZFighterRogue boss) {
		if (!is(boss) || trial.gather + trial.hold + trial.spent == 0) {
			return false;
		}
		boss.getNavigation().stop();
		if (trial.spent > 0) {
			boss.setDeltaMovement(0.0, boss.getDeltaMovement().y, 0.0);
			boss.setYRot(trial.spentYaw);
			boss.setYBodyRot(trial.spentYaw);
			boss.setYHeadRot(trial.spentYaw);
		}
		return true;
	}

	/** (Hook in {@link ZFighterRogue#hurtServer}) What a blow on him counts for: more while he is spent, most from behind. */
	static float taken(ZFighterRogue boss, DamageSource source) {
		if (!is(boss) || trial.spent <= 0) {
			return 1.0F;
		}
		Entity attacker = source.getEntity();
		if (attacker == null) {
			return FRONT_HIT;
		}
		double yaw = Math.toRadians(trial.spentYaw);
		Vec3 facing = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 to = attacker.position().subtract(boss.position()).multiply(1.0, 0.0, 1.0);
		return to.lengthSqr() > 1.0E-4 && facing.dot(to.normalize()) < BEHIND ? BACK_HIT : FRONT_HIT;
	}

	/** (Hook in {@link ZFighterRaids#defeated}) He's beaten: the prize, shared by hurt, and the top fighter named. True if it was him. */
	static boolean fell(ServerLevel level, ZFighterRogue boss) {
		if (!boss.entityTags().contains(TAG)) {
			return false;
		}
		trial = null;
		float total = 0.0F;
		UUID top = null;
		for (Map.Entry<UUID, Float> entry : boss.hurtBy.entrySet()) {
			total += entry.getValue();
			if (top == null || entry.getValue() > boss.hurtBy.get(top)) {
				top = entry.getKey();
			}
		}
		Component topName = Component.literal("-");
		for (Map.Entry<UUID, Float> entry : boss.hurtBy.entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player == null || total <= 0.0F) {
				continue;
			}
			float share = entry.getValue() / total * (entry.getKey().equals(top) ? 1.0F + TOP_BONUS : 1.0F);
			if (entry.getKey().equals(top)) {
				topName = player.getDisplayName();
			}
			if (Powers.has(player, ZFighterPower.INSTANCE)) {
				int tp = Math.max(MIN_TP, Math.round(TRIAL_TP * share));
				ZFighterTraining.grant(player, tp);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_raid_share_tp", tp).withStyle(ChatFormatting.GOLD));
			} else {
				int silver = Math.max(MIN_SILVER, Math.round(TRIAL_SILVER * share));
				KingdomsMoney.pay(player, KingdomsMoney.worth(silver));
				player.giveExperienceLevels(Math.max(5, Math.round(TRIAL_XP * share)));
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_raid_share", KingdomsMoney.text(player, KingdomsMoney.worth(silver)))
					.withStyle(ChatFormatting.GOLD));
			}
		}
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_trial_won",
			boss.getDisplayName(), topName).withStyle(ChatFormatting.GOLD), false);
		return true;
	}

	/** Nobody left to fight: back through the door. */
	private static void withdraw(ServerLevel level, ZFighterRogue boss, BlockPos tower) {
		Vec3 door = door(tower);
		level.sendParticles(MagicFx.LIGHT, door.x, door.y + 1.0, door.z, 40, 0.5, 1.0, 0.5, 0.1);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_trial_withdrew",
			boss.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
		boss.discard();
		trial = null;
	}
}
