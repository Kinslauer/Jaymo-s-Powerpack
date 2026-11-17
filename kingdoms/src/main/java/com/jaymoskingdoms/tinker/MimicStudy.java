package com.jaymoskingdoms.tinker;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.FoeStudy;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Pace;
import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * A Tinkerer's research, done the way a Druid studies a beast ({@code Druids}): an ability is learned once the Tinkerer has
 * either watched it a while, {@link #STUDY_TICKS} of seeing it in use while looking on calmly (standing still, sneaking or walking
 * slowly) within {@link #RANGE} blocks and in plain sight, or fought it: each blow it lands on them teaches {@link #HURT_TICKS},
 * and a creature kind known to one star ({@link FoeStudy}) teaches all of its abilities at once, as a Druid's foe does.
 *
 * <p>What is "in use": a player whose power Jaymo's Powers has just marked ({@code jaymospowers_odd:power/<id>}); a shulker's
 * bullet, a blaze's small fireball, a ghast's fireball, an evoker's fangs, a breeze's wind charge or a witch's thrown potion in
 * the air; a creeper swelling, a guardian's beam locked on, a spider on a wall, an enderman that has just blinked, a warden
 * drawing its sonic boom. What is learned is the drawing: the Tinkerer then builds the gadget ({@link MimicBench}).
 */
public final class MimicStudy {
	/** Seeing it in use (ticks) to learn it, a blow of it taken (ticks), and how near it must be (blocks). */
	public static final int STUDY_TICKS = 20 * 30, HURT_TICKS = STUDY_TICKS / 3;
	static final double RANGE = 16.0, CALM_SPEED = 0.03;
	/** How long after an enderman blinks it still counts as seen blinking (ticks). */
	static final int BLINK_SEEN = 30;
	static final String POWER_TAG = "jaymospowers_odd:power/";

	/** The abilities studied so far: key → ticks. Saved, kept through death. */
	public static final AttachmentType<Map<String, Integer>> STUDIED = AttachmentRegistry.create(JaymosKingdoms.id("tinker_studied"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	/** Endermen that blinked, and when (game time). */
	private static final Map<Entity, Long> BLINKED = new WeakHashMap<>();

	private MimicStudy() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MimicStudy::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F && Classes.is(player, PlayerClass.TINKERER) && player.level() instanceof ServerLevel level) {
				Set<Mimic> felt = felt(source);
				if (!felt.isEmpty()) {
					add(player, level, felt, HURT_TICKS);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BLINKED.clear());
	}

	/** (From the enderman mixin) {@code enderman} has just blinked. */
	public static void blinked(Entity enderman) {
		BLINKED.put(enderman, enderman.level().getGameTime());
	}

	/** How far {@code player} has studied {@code m} (ticks, up to {@link #STUDY_TICKS}). */
	public static int progress(Player player, Mimic m) {
		if (knows(player, m)) {
			return STUDY_TICKS;
		}
		return Math.min(STUDY_TICKS, player.getAttachedOrElse(STUDIED, Map.<String, Integer>of()).getOrDefault(m.key(), 0));
	}

	/** Whether {@code player} has studied {@code m} enough to build it. */
	public static boolean knows(Player player, Mimic m) {
		if (player.isCreative()) {
			return true;
		}
		if (player.getAttachedOrElse(STUDIED, Map.<String, Integer>of()).getOrDefault(m.key(), 0) >= STUDY_TICKS) {
			return true;
		}
		for (String kind : m.mobs) {
			if (FoeStudy.stars(player, kind) >= 1) {
				return true;
			}
		}
		return false;
	}

	/** {@code player} is taught {@code m} outright (the Mad Tinkerer's lesson). */
	static void teach(ServerPlayer player, Mimic m) {
		Map<String, Integer> studied = new HashMap<>(player.getAttachedOrElse(STUDIED, Map.<String, Integer>of()));
		studied.put(m.key(), STUDY_TICKS);
		player.setAttached(STUDIED, studied);
	}

	// ---- seeing ---------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()
				|| (level.getGameTime() + player.getId()) % 20 != 0 || !Classes.is(player, PlayerClass.TINKERER)) {
				continue;
			}
			boolean calm = player.isShiftKeyDown() || player.getDeltaMovement().horizontalDistance() < CALM_SPEED || Pace.slow(player);
			if (!calm) {
				continue;
			}
			EnumSet<Mimic> seen = EnumSet.noneOf(Mimic.class);
			long now = level.getGameTime();
			for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(RANGE), x -> x.isAlive() && !x.isSpectator())) {
				Set<Mimic> shown = showing(e, now);
				if (!shown.isEmpty() && !seen.containsAll(shown) && player.hasLineOfSight(e)) {
					seen.addAll(shown);
				}
			}
			if (!seen.isEmpty()) {
				add(player, level, seen, 20);
			}
		}
	}

	/** The abilities {@code e} is using right now. */
	static Set<Mimic> showing(Entity e, long now) {
		EnumSet<Mimic> out = EnumSet.noneOf(Mimic.class);
		if (e instanceof Player p) {
			for (String tag : p.entityTags()) {
				if (tag.startsWith(POWER_TAG)) {
					Mimic m = Mimic.ofPower(tag.substring(POWER_TAG.length()));
					if (m != null) {
						out.add(m);
					}
				}
			}
			return out;
		}
		Mimic thrown = thrown(e);
		if (thrown != null) {
			out.add(thrown);
		} else if (e instanceof Creeper c && c.getSwelling(1.0F) > 0.0F) {
			out.add(Mimic.BLAST);
		} else if (e instanceof Guardian g && g.hasActiveAttackTarget()) {
			out.add(Mimic.BEAM);
		} else if (e instanceof Spider s && s.isClimbing()) {
			out.add(Mimic.CLIMB);
		} else if (e instanceof Warden w && w.getBrain().hasMemoryValue(MemoryModuleType.SONIC_BOOM_SOUND_DELAY)) {
			out.add(Mimic.SONIC);
		} else {
			Long blinked = BLINKED.get(e);
			if (blinked != null && now - blinked <= BLINK_SEEN) {
				out.add(Mimic.BLINK);
			}
		}
		return out;
	}

	/** The ability a thing in flight shows (a shulker's bullet, a witch's potion...), or null. */
	private static Mimic thrown(Entity e) {
		return switch (FoeStudy.kind(e)) {
			case "shulker_bullet" -> Mimic.LIFT;
			case "small_fireball" -> Mimic.FIREBURST;
			case "fireball" -> Mimic.FIREBALL;
			case "evoker_fangs" -> Mimic.FANGS;
			case "breeze_wind_charge" -> Mimic.GUST;
			case "splash_potion", "lingering_potion" -> e instanceof Projectile p && p.getOwner() != null && FoeStudy.kind(p.getOwner()).equals("witch")
				? Mimic.POTION : null;
			default -> null;
		};
	}

	/** The abilities a blow on a Tinkerer came from. */
	private static Set<Mimic> felt(DamageSource source) {
		EnumSet<Mimic> out = EnumSet.noneOf(Mimic.class);
		Entity direct = source.getDirectEntity(), by = source.getEntity();
		if (by instanceof Player p) {
			out.addAll(showing(p, p.level().getGameTime()));
		}
		if (direct != null && direct != by) {
			Mimic m = thrown(direct);
			if (m != null) {
				out.add(m);
			}
		}
		if (source.is(DamageTypes.SONIC_BOOM) && by instanceof Warden) {
			out.add(Mimic.SONIC);
		}
		if (by instanceof Creeper && source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
			out.add(Mimic.BLAST);
		}
		if (by instanceof Guardian && direct == by) {
			out.add(Mimic.BEAM);
		}
		return out;
	}

	/** {@code player} studies each of {@code mimics} {@code ticks} more. */
	private static void add(ServerPlayer player, ServerLevel level, Set<Mimic> mimics, int ticks) {
		Map<String, Integer> studied = null;
		for (Mimic m : mimics) {
			if (knows(player, m)) {
				continue;
			}
			if (studied == null) {
				studied = new HashMap<>(player.getAttachedOrElse(STUDIED, Map.<String, Integer>of()));
			}
			int before = studied.getOrDefault(m.key(), 0);
			studied.put(m.key(), Math.min(STUDY_TICKS, before + ticks));
			if (before + ticks >= STUDY_TICKS) {
				learned(player, level, m);
			}
		}
		if (studied != null) {
			player.setAttached(STUDIED, studied);
		}
	}

	private static void learned(ServerPlayer player, ServerLevel level, Mimic m) {
		com.jaymoskingdoms.ClassTrees.give(player, PlayerClass.TINKERER, com.jaymoskingdoms.ClassTrees.XP_STUDIED);
		level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY(1.0), player.getZ(), 14, 0.4, 0.5, 0.4, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mimic.learned",
			Component.translatable("mimic.jaymoskingdoms." + m.key())));
	}
}
