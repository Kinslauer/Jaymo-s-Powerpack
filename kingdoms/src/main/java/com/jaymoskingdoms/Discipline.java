package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Warlord's tree, Discipline (class trees, docs/ClassTrees-Plan.md), and its gauge, <b>Composure</b>: built by
 * full-strength blows, blocks and parries, knocked away by a clean hit taken, ebbing out of a fight. Full Composure is what
 * the Warlord's best moves spend, and at full the Warlord hits harder (practice: more with each level).
 * <ul>
 * <li><b>Measured Strikes</b> (3): full-strength blows build more Composure, and a little more damage at full, each rank.</li>
 * <li><b>Riposte</b> (3): a blow within a second of a block or a parry hits half again as hard and staggers.</li>
 * <li><b>Sword and Board</b> or <b>Two-Handed Master</b> (10): shield bashes stagger and blocking builds double Composure; or
 * two-handed swings sweep wider (the Warlord's cleave reaches further and takes one more) and pierce armour.</li>
 * <li><b>Read the Foe</b> (15): at full Composure the parry window is twice as wide (Powers' guard asks, {@link #readTheFoe}).</li>
 * <li><b>Commander</b> or <b>Duelist</b> (20): orders reach twice as far, and your hirelings and companions follow them whatever
 * tongue they speak; or +30% with only one foe near.</li>
 * <li><b>Perfect Form</b> (top): the class key at full Composure spends it on the next blow: it can't be parried, hits twice
 * as hard and staggers anything, a boss's posture broken outright.</li>
 * </ul>
 */
public final class Discipline {
	/** Composure (of 1) for a full-strength blow, a block and a parry; lost to a clean hit taken. */
	static final float FILL_BLOW = 0.1F, FILL_BLOCK = 0.12F, FILL_PARRY = 0.2F, LOSS_HIT = 0.2F;
	/** No blow for this long (ticks) and it ebbs, this much a tick. */
	static final int CALM_AFTER = 20 * 10;
	static final float EBB = 0.005F;
	/** At full Composure: this share harder (grown by practice). */
	static final float FULL_DAMAGE = 0.15F;
	/** Measured Strikes, a rank: this share more Composure a full-strength blow, this share harder at full. */
	static final float MEASURED_FILL = 0.25F, MEASURED_DAMAGE = 0.05F;
	/** Riposte: within this long of a block (ticks), this many times as hard, a stagger this long. */
	static final int RIPOSTE_TICKS = 20, RIPOSTE_STAGGER = 30;
	static final float RIPOSTE = 1.5F;
	/** Sword and Board: a bash staggers this long; a block builds this many times the Composure. */
	static final int BASH_STAGGER = 40;
	static final float BOARD_FILL = 2.0F;
	/** Two-Handed Master: the cleave reaches this many times as far and takes this many more; a point of armour pierced for this share. */
	static final double SWEEP = 1.5;
	static final int SWEEP_MORE = 1;
	static final float PIERCE_PER_ARMOUR = 0.02F, PIERCE_MOST = 0.4F;
	/** Read the Foe: Powers' parry window (12 ticks) doubled. */
	static final int READ_PARRY_TICKS = 24;
	/** Commander: orders reach this many times as far. Duelist: this share harder with only one foe within this reach. */
	static final double COMMAND_REACH = 2.0, DUEL_REACH = 10.0;
	static final float DUEL = 0.3F;
	/** Perfect Form: the blow waits this long (ticks), hits this many times as hard and staggers this long. */
	static final int PERFECT_TICKS = 20 * 5, PERFECT_STAGGER = 60;
	static final float PERFECT = 2.0F;
	static final int COLOUR = 0xE8C46A;

	public static final String MEASURED = "wb_measured", RIPOSTE_KEY = "wb_riposte", BOARD = "wb_board", TWO_HANDED = "wb_two_handed",
		READ = "wb_read", COMMANDER = "wb_commander", DUELIST = "wb_duelist", PERFECT_FORM = "wb_perfect";

	/** The gauge (0-1), shown on its owner's screen. Not kept. */
	public static final AttachmentType<Float> COMPOSURE = AttachmentRegistry.create(JaymosKingdoms.id("warlord_composure"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	private static final Map<UUID, Long> LAST_FIGHT = new HashMap<>(), LAST_GUARD = new HashMap<>(), PERFECT_UNTIL = new HashMap<>();

	private Discipline() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Discipline::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Discipline::damaged);
		PowersCompat.onParry((player, attacker) -> {
			if (warlord(player)) {
				guarded(player, FILL_PARRY);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_FIGHT.clear();
			LAST_GUARD.clear();
			PERFECT_UNTIL.clear();
		});
	}

	static boolean warlord(Player player) {
		return Classes.is(player, PlayerClass.WARLORD);
	}

	/** The gauge (0-1), on either side. */
	public static float composure(Player player) {
		Float f = player.getAttached(COMPOSURE);
		return f == null ? 0.0F : f;
	}

	static boolean full(Player player) {
		return composure(player) >= 1.0F;
	}

	private static void fill(ServerPlayer player, float by) {
		player.setAttached(COMPOSURE, Math.clamp(composure(player) + by, 0.0F, 1.0F));
		LAST_FIGHT.put(player.getUUID(), player.level().getGameTime());
	}

	private static void guarded(ServerPlayer player, float by) {
		LAST_GUARD.put(player.getUUID(), player.level().getGameTime());
		fill(player, by * (ClassTrees.has(player, BOARD) > 0 ? BOARD_FILL : 1.0F));
	}

	/** {@code player}'s shield is up (a block or a bash is a shield's). */
	private static boolean shieldUp(Player player) {
		return player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	private static void damaged(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (victim instanceof ServerPlayer guard && warlord(guard) && source.getEntity() instanceof LivingEntity && source.getEntity() != guard) {
			if (blocked) {
				guarded(guard, FILL_BLOCK);
			} else if (taken > 0.0F) {
				// a clean hit knocks some of it away
				fill(guard, -LOSS_HIT);
			}
		}
		if (source.getEntity() instanceof ServerPlayer player && player != victim && source.getDirectEntity() == player && taken > 0.0F
			&& shieldUp(player) && ClassTrees.has(player, BOARD) > 0 && player.level() instanceof ServerLevel level) {
			// Sword and Board: a shield bash (Powers' bash lands while the shield is up) staggers
			com.jaymoskingdoms.spirits.Staggered.stagger(player, victim, BASH_STAGGER);
			level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.7), victim.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
		}
	}

	/** What Discipline does to {@code player}'s blow on {@code target} ({@link TreePerks#dealt}). */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer player) || player == target || source.getDirectEntity() != player || !warlord(player)
			|| CombatArts.striking() || !(player.level() instanceof ServerLevel level)) {
			return amount;
		}
		long now = level.getGameTime();
		boolean fullStrength = CombatArts.fullStrength(player, now);
		ItemStack held = player.getMainHandItem();
		if (fullStrength) {
			fill(player, FILL_BLOW * (1.0F + MEASURED_FILL * ClassTrees.power(player, MEASURED)));
		}
		if (full(player)) {
			// practice: the Warlord's composure, grown with the level
			amount *= 1.0F + FULL_DAMAGE * ClassTrees.practice(player, PlayerClass.WARLORD) + MEASURED_DAMAGE * ClassTrees.power(player, MEASURED);
		}
		Long guard = LAST_GUARD.get(player.getUUID());
		if (guard != null && now - guard <= RIPOSTE_TICKS && ClassTrees.has(player, RIPOSTE_KEY) > 0) {
			LAST_GUARD.remove(player.getUUID());
			amount *= RIPOSTE;
			com.jaymoskingdoms.spirits.Staggered.stagger(player, target, RIPOSTE_STAGGER);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.9F);
		}
		if (held.is(ClassPerks.TWO_HANDED) && ClassTrees.has(player, TWO_HANDED) > 0) {
			// it pierces armour: the more the foe wears, the more of the blow it gives back
			amount *= 1.0F + Math.min(PIERCE_MOST, PIERCE_PER_ARMOUR * target.getArmorValue());
		}
		if (ClassTrees.has(player, DUELIST) > 0 && CombatArts.foes(level, player, player.getBoundingBox().inflate(DUEL_REACH)).size() <= 1) {
			amount *= 1.0F + DUEL * Math.min(1.0F, ClassTrees.power(player, DUELIST));
		}
		Long perfect = PERFECT_UNTIL.remove(player.getUUID());
		if (perfect != null && perfect >= now) {
			amount *= PERFECT;
			com.jaymoskingdoms.spirits.Staggered.stagger(player, target, PERFECT_STAGGER);
			if (BossPosture.applies(target)) {
				// bosses too: its posture broken at once
				BossPosture.wear(level, target, BossPosture.max(target));
			}
			Fx.ring(level, new Vec3(target.getX(), target.getY(0.5), target.getZ()), 1.2F, COLOUR);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 1.2F);
		}
		return amount;
	}

	/** Perfect Form's blow is waiting: no mob parries it ({@link MobParry}). */
	static boolean perfect(Player player) {
		Long until = PERFECT_UNTIL.get(player.getUUID());
		return until != null && until >= player.level().getGameTime();
	}

	/** Two-Handed Master: how many times as far the Warlord's cleave reaches ({@link ClassPerks}). */
	static double sweep(ServerPlayer player) {
		return ClassTrees.has(player, TWO_HANDED) > 0 ? SWEEP : 1.0;
	}

	/** Two-Handed Master: how many more the cleave takes. */
	static int sweepMore(ServerPlayer player) {
		return ClassTrees.has(player, TWO_HANDED) > 0 ? SWEEP_MORE : 0;
	}

	/** Read the Foe, from Powers' guard: a block that missed the parry window, inside a window twice as wide at full Composure. */
	static boolean readTheFoe(ServerPlayer player) {
		return ClassTrees.has(player, READ) > 0 && full(player) && player.getTicksUsingItem() <= READ_PARRY_TICKS;
	}

	/** Commander: how many times as far {@code player}'s orders reach ({@link Presence#reach}). */
	public static double orderReach(Player player) {
		return player instanceof ServerPlayer sp && ClassTrees.has(sp, COMMANDER) > 0 ? COMMAND_REACH : 1.0;
	}

	/** Commander: one of {@code player}'s hirelings or companions takes the order whatever tongue it speaks. */
	static boolean commands(ServerPlayer player, Entity v) {
		return ClassTrees.has(player, COMMANDER) > 0 && Followers.takesOrders(v, player);
	}

	/** The class key: Perfect Form at full Composure; otherwise how full it is. */
	static void key(ServerPlayer player, boolean sneak) {
		if (ClassTrees.has(player, PERFECT_FORM) > 0 && full(player)) {
			player.setAttached(COMPOSURE, 0.0F);
			PERFECT_UNTIL.put(player.getUUID(), player.level().getGameTime() + PERFECT_TICKS);
			ServerLevel level = player.level();
			Fx.ring(level, new Vec3(player.getX(), player.getY(0.1), player.getZ()), 1.0F, COLOUR);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.composure.perfect"));
			return;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.composure.gauge", Math.round(composure(player) * 100.0F)));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!warlord(player) || !player.isAlive()) {
				if (player.hasAttached(COMPOSURE)) {
					player.removeAttached(COMPOSURE);
				}
				continue;
			}
			float c = composure(player);
			if (c <= 0.0F) {
				continue;
			}
			long now = player.level().getGameTime();
			Long last = LAST_FIGHT.get(player.getUUID());
			if (last == null || now - last > CALM_AFTER) {
				float next = Math.max(0.0F, c - EBB);
				if (next == 0.0F) {
					player.removeAttached(COMPOSURE);
					LAST_FIGHT.remove(player.getUUID());
				} else {
					player.setAttached(COMPOSURE, next);
				}
			}
			if (c >= 1.0F && now % 20 == 0 && player.level() instanceof ServerLevel level) {
				level.sendParticles(Fx.mote(COLOUR, 0.6F), player.getX(), player.getY(1.0), player.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
			}
		}
	}
}
