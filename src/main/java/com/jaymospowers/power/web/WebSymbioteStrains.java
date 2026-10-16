package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.mojang.serialization.Codec;
import java.util.Comparator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The symbiote's strains ({@link WebSymbioteStrain}): what makes the white one and the red one different from the black.
 * <ul>
 * <li><b>Purity:</b> a black symbiote that has taken over a web slinger, healed by a Wind user's Zephyr's Breath
 * ({@link Half#WIND}, +{@link #PURITY_ZEPHYR}) and a Chrono user's Time Bloom ({@link Half#TIME}, +{@link #PURITY_BLOOM}
 * each heal tick), fills two halves of purity, each up to {@link #PURITY_HALF}; both full, the takeover ends, the black
 * boils off in white flakes and it is the white strain from then on. Purity fades {@link #PURITY_DECAY} every
 * {@link #PURITY_DECAY_TICKS}. Healing outside a takeover does nothing; healing the red one mid-takeover only angers it.</li>
 * <li><b>White:</b> Mending Shot ({@link #mendShot}, {@link #mend}), Recoil ({@link #recoil}), mending takeovers
 * ({@link #mostHurt}); every heart it heals costs it {@link #HEAL_SATIETY_COST} satiety, and at none it sleeps
 * ({@link #sleep}) for {@link #DORMANT_TICKS} unless a corpse wakes it.</li>
 * <li><b>Red:</b> blades (see {@link WebMorph}), a faster barrage, a bleeding blade burst, bloodlust takeovers, and its
 * own craters and goo ({@link WebRedCraterFeature}, {@code WebPower.RED_GOO}).</li>
 * <li><b>Rivals:</b> a red symbiote (bonded, roaming or in a host) and a black or white one within {@link #RIVAL_RANGE}
 * go for each other ({@link #rival}, {@link #rivals}).</li>
 * </ul>
 * A player's strain lives in {@link WebSymbioteState#strain()}; a roaming blob's or a host mob's in {@link #KIND}.
 */
public final class WebSymbioteStrains {
	// ---- tuning: purity ----
	/** Purity added to the wind half by a Zephyr's Breath, and to the time half by each Time Bloom heal tick; each half's most. */
	public static final int PURITY_ZEPHYR = 25, PURITY_BLOOM = 5, PURITY_HALF = 50;
	/** Purity fades this much from each half every this many ticks (10 s). */
	public static final int PURITY_DECAY = 1, PURITY_DECAY_TICKS = 200;
	/** It tells you it feels the healing at most this often (ticks); the red one rages at it this often. */
	static final int PURITY_STIR_GAP = 100, RED_ANGER_GAP = 60;
	/** The red one, healed mid-takeover: this much stress. */
	static final int RED_ANGER_STRESS = 4;

	// ---- tuning: white ----
	/** A Mending Shot heals this much (2 hearts), and clears Poison and Wither. */
	public static final float MEND_SHOT_HEAL = 4.0F;
	/** Each heart it heals costs this much satiety. */
	public static final float HEAL_SATIETY_COST = 1.5F;
	/** Out of satiety from healing, it sleeps this long (ticks, 5 minutes). */
	public static final int DORMANT_TICKS = 6000;
	/** A mending takeover: the most hurt within this range (under {@code MEND_BELOW} of its health), this chance a check. */
	public static final double MEND_RANGE = 16.0;
	static final float MEND_BELOW = 0.8F, MEND_CHANCE = 0.3F;
	/** Its Mending Shots in a takeover: the first after this many ticks, then every this many, from no farther than this. */
	static final int MEND_FIRST_SHOT = 20, MEND_SHOT_EVERY = 30;
	static final double MEND_SHOT_RANGE = 10.0;
	static final float MEND_SHOT_SPEED = 1.6F;
	/** Recoil: this share of Spike Burst's damage, and players in range healed this much instead. */
	public static final float RECOIL_SHARE = 0.5F, RECOIL_HEAL = 4.0F;

	// ---- tuning: red ----
	/** Its blades: damage x this, severing chances x this. */
	public static final float RED_BLADE_DAMAGE = 1.25F, RED_SEVER = 1.5F;
	/** Barrage stabs every this many ticks (the black one's is {@link WebTendrilCombos#BARRAGE_EVERY}). */
	public static final int RED_BARRAGE_EVERY = 6;
	/** The blade burst leaves everything it hits bleeding this many drops (one every {@code WebMorph.BLEED_EVERY}: 4 s). */
	public static final int RED_BURST_BLEED_HITS = 8;
	/** Takeover checks come this many times as often. */
	public static final int RED_CHECK_DIVISOR = 2;
	/** Each check, with something living within the takeover range, this chance it goes for it, fed or not. */
	public static final float RED_HUNT_CHANCE = 0.1F;
	/** After a kill, this chance it stays in for the next one. */
	public static final float RED_CHAIN = 0.5F;
	/** It rampages from neglect at any bond under this. */
	public static final int RED_NEGLECT_BOND = 40;
	/** Red craters: one in this many chunks (the number lives in the placed feature's JSON; see tools/gen_web_strains.py). */
	public static final int RED_CRATER_RARITY = 1500;

	// ---- tuning: rivals ----
	public static final double RIVAL_RANGE = 16.0;

	// ---- colours (particles; the client's are in WebStrainLook) ----
	public static final int WHITE = 0xEDEBE4, WHITE_SHEEN = 0xE6D49A, RED = 0xA3121B, RED_SHEEN = 0x140608;

	/** A roaming symbiote's or a host mob's strain (absent: black). Saved, synced to everyone (they see its colours). */
	public static final AttachmentType<Integer> KIND = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_kind"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** The white one is asleep from healing (its dormancy is {@link WebSymbioteBond#DORMANT}). Saved; synced to the owner (the menu). */
	public static final AttachmentType<Boolean> ASLEEP = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_asleep"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));
	/** Server-only bookkeeping (not saved: purity fades anyway). */
	private static final AttachmentType<Live> LIVE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_strain_live"));

	private static final class Live {
		/** The two halves of purity, and the game time their fading is counted from. */
		float wind, time;
		long decayFrom;
		/** Who last breathed and bloomed on it (entity ids, -1: nobody), told when it's purified. */
		int windHealer = -1, timeHealer = -1;
		long stirAt, angerAt;
		/** Satiety owed for healing, below a whole point. */
		float debt;
	}

	/** Which heal: a Wind user's Zephyr's Breath, or a Chrono user's Time Bloom. */
	public enum Half {
		WIND,
		TIME
	}

	private WebSymbioteStrains() {
	}

	private static Live live(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null) {
			live = new Live();
			player.setAttached(LIVE, live);
		}
		return live;
	}

	// ---- shared queries (both sides) --------------------------------------------------------------------------------

	/** The strain of a bonded player (theirs), or of a roaming symbiote or a host mob ({@link #KIND}); black otherwise. */
	public static WebSymbioteStrain of(@Nullable Entity entity) {
		if (entity == null) {
			return WebSymbioteStrain.BLACK;
		}
		if (entity instanceof Player player) {
			WebSymbioteState state = WebSymbiote.state(player);
			return state.bonded() ? state.kind() : WebSymbioteStrain.BLACK;
		}
		Integer kind = entity.getAttached(KIND);
		return kind == null ? WebSymbioteStrain.BLACK : WebSymbioteStrain.of(kind);
	}

	/** The white one is asleep from healing (either side; synced to the owner). */
	public static boolean asleep(Player player) {
		return Boolean.TRUE.equals(player.getAttached(ASLEEP)) && WebSymbioteBond.dormant(player);
	}

	/** It fires Mending Shots instead of Web Shots: bonded to the white one, awake. */
	public static boolean mends(Player player) {
		return WebSymbiote.isBonded(player) && of(player) == WebSymbioteStrain.WHITE && !asleep(player);
	}

	/** A symbiote of any kind: a bonded web slinger, a roaming one, or a mob it has taken. */
	public static boolean symbiotic(Entity entity) {
		if (entity instanceof Player player) {
			return WebSymbiote.isBonded(player);
		}
		return entity instanceof WebSymbioteBlob || WebInhabit.isHost(entity) || WebBehemoth.isHost(entity);
	}

	/** {@code a} and {@code b} are rival symbiotes: one red, the other black or white. */
	public static boolean rivals(@Nullable Entity a, @Nullable Entity b) {
		return a != null && b != null && a != b && symbiotic(a) && symbiotic(b)
			&& (of(a) == WebSymbioteStrain.RED) != (of(b) == WebSymbioteStrain.RED);
	}

	/** The colour its whispers are written in. */
	public static ChatFormatting voice(Player player) {
		return switch (of(player)) {
			case WHITE -> ChatFormatting.WHITE;
			case RED -> ChatFormatting.DARK_RED;
			default -> ChatFormatting.DARK_PURPLE;
		};
	}

	/** Its body colour and its sheen, for particles. */
	public static int color(WebSymbioteStrain strain) {
		return switch (strain) {
			case WHITE -> WHITE;
			case RED -> RED;
			default -> WebSymbiote.BLACK;
		};
	}

	public static int sheen(WebSymbioteStrain strain) {
		return switch (strain) {
			case WHITE -> WHITE_SHEEN;
			case RED -> RED_SHEEN;
			default -> WebSymbiote.SHEEN;
		};
	}

	/** A fleck of {@code entity}'s symbiote, in its strain's colour. */
	public static DustParticleOptions dust(@Nullable Entity entity, float scale) {
		return new DustParticleOptions(color(of(entity)), scale);
	}

	// ---- strain bookkeeping -----------------------------------------------------------------------------------------

	/** Marks a roaming symbiote or a host with {@code strain} (black is the default, left unmarked). Returns it. */
	public static <T extends Entity> T mark(T entity, WebSymbioteStrain strain) {
		if (strain == WebSymbioteStrain.BLACK) {
			entity.removeAttached(KIND);
		} else {
			entity.setAttached(KIND, strain.id());
		}
		return entity;
	}

	/** A new bond: no sleep, no purity carried over. */
	static void onBond(ServerPlayer player) {
		player.removeAttached(ASLEEP);
		player.removeAttached(LIVE);
	}

	/** The bond is gone (rejected, died, the power lost): the same. */
	static void onUnbond(ServerPlayer player) {
		player.removeAttached(ASLEEP);
		player.removeAttached(LIVE);
	}

	// ---- purity ------------------------------------------------------------------------------------------------------

	/**
	 * {@code healer} healed {@code target} (Zephyr's Breath, a Time Bloom heal tick): if it's a web slinger whose black
	 * symbiote has the wheel, that half of its purity fills; both full, it's purified. The red one only gets angrier.
	 */
	public static void purifyHeal(ServerPlayer healer, LivingEntity target, Half half) {
		if (!(target instanceof ServerPlayer player) || !WebSymbiote.isBonded(player) || !WebSymbiote.driving(player)) {
			return;
		}
		WebSymbioteStrain strain = of(player);
		if (strain == WebSymbioteStrain.WHITE) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Live live = live(player);
		if (strain == WebSymbioteStrain.RED) {
			// it wants none of it
			if (now >= live.angerAt) {
				live.angerAt = now + RED_ANGER_GAP;
				WebSymbiote.addStress(player, RED_ANGER_STRESS);
				WebSymbiote.whisper(player, "red.purify." + (1 + player.getRandom().nextInt(WebSymbiote.SAY_LINES)));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ANGRY, SoundSource.PLAYERS, 1.0F, 1.2F);
				level.sendParticles(dust(player, 1.4F), player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.7, 0.4, 0.0);
				Shakes.send(player, 0.6F);
			}
			return;
		}
		decay(live, now);
		if (half == Half.WIND) {
			live.wind = Math.min(PURITY_HALF, live.wind + PURITY_ZEPHYR);
			live.windHealer = healer.getId();
		} else {
			live.time = Math.min(PURITY_HALF, live.time + PURITY_BLOOM);
			live.timeHealer = healer.getId();
		}
		// a few white flecks in the black where it was touched
		level.sendParticles(new DustParticleOptions(WHITE, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.35, 0.7, 0.35, 0.0);
		if (live.wind >= PURITY_HALF && live.time >= PURITY_HALF) {
			purify(player, live);
		} else if (now >= live.stirAt) {
			live.stirAt = now + PURITY_STIR_GAP;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 0.7F);
			WebSymbiote.whisper(player, "purity_stir." + (1 + player.getRandom().nextInt(WebSymbiote.SAY_LINES)));
		}
	}

	/** A Time Bloom heal tick around {@code healer}: every taken-over web slinger within {@code radius}. */
	public static void purifyAround(ServerPlayer healer, double radius, Half half) {
		for (ServerPlayer player : healer.level().getEntitiesOfClass(ServerPlayer.class, healer.getBoundingBox().inflate(radius),
			p -> p != healer && p.isAlive() && !p.isSpectator() && p.distanceToSqr(healer) <= radius * radius)) {
			purifyHeal(healer, player, half);
		}
	}

	/** Purity fades {@link #PURITY_DECAY} from each half every {@link #PURITY_DECAY_TICKS} since it was last counted. */
	private static void decay(Live live, long now) {
		if (live.wind <= 0.0F && live.time <= 0.0F) {
			live.decayFrom = now;
			return;
		}
		long steps = (now - live.decayFrom) / PURITY_DECAY_TICKS;
		if (steps > 0L) {
			live.wind = Math.max(0.0F, live.wind - steps * PURITY_DECAY);
			live.time = Math.max(0.0F, live.time - steps * PURITY_DECAY);
			live.decayFrom += steps * PURITY_DECAY_TICKS;
		}
	}

	/** Both halves full: the takeover ends, the black boils off in white flakes with a chime like a choir, and it's white. */
	private static void purify(ServerPlayer player, Live live) {
		WebSymbiote.purified(player);
		ServerLevel level = player.level();
		double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
		level.sendParticles(WebSymbiote.dust(1.4F), x, y, z, 40, 0.45, 0.9, 0.45, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.4, 0.8, 0.4, 0.04);
		level.sendParticles(new DustParticleOptions(WHITE, 1.6F), x, y, z, 80, 0.5, 1.0, 0.5, 0.05);
		level.sendParticles(new DustParticleOptions(WHITE_SHEEN, 1.2F), x, y, z, 30, 0.5, 1.0, 0.5, 0.03);
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 30, 0.5, 0.9, 0.5, 0.06);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4F, 0.6F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4F, 1.2F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.8F);
		level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		Shakes.send(player, 0.8F);
		WebSymbiote.whisper(player, "white.purified");
		// whoever helped hears it go quiet
		for (int id : new int[] {live.windHealer, live.timeHealer}) {
			if (id >= 0 && level.getEntity(id) instanceof ServerPlayer healer && healer != player) {
				healer.sendOverlayMessage(Component.translatable("message.jaymospowers.web_symbiote.purified_healer")
					.withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
			}
		}
		live.wind = 0.0F;
		live.time = 0.0F;
		live.windHealer = -1;
		live.timeHealer = -1;
	}

	// ---- white: mending ---------------------------------------------------------------------------------------------

	/** The most hurt creature or player near (under {@link #MEND_BELOW} of its health), for a mending takeover; null if none. */
	static @Nullable LivingEntity mostHurt(ServerPlayer player) {
		return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MEND_RANGE),
				e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !(e instanceof WebSymbioteBlob)
					&& e.getHealth() < e.getMaxHealth() * MEND_BELOW && e.distanceToSqr(player) <= MEND_RANGE * MEND_RANGE)
			.stream().min(Comparator.comparingDouble(e -> e.getHealth() / Math.max(1.0F, e.getMaxHealth()))).orElse(null);
	}

	/** A Mending Shot from {@code shooter} at {@code target} (a mending takeover's: aimed for you). */
	static void mendShot(ServerPlayer shooter, LivingEntity target) {
		ServerLevel level = shooter.level();
		Vec3 eye = shooter.getEyePosition();
		Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
		WebBall ball = new WebBall(level, shooter, new ItemStack(WebPower.WEB_BALL_ITEM)).mending();
		ball.setPos(eye.x, eye.y - 0.1, eye.z);
		ball.shoot(to.x, to.y + to.horizontalDistance() * 0.02, to.z, MEND_SHOT_SPEED, 0.5F);
		level.addFreshEntity(ball);
		level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8F, 1.5F);
		level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.6F);
	}

	/**
	 * Mends {@code target}: {@code amount} health back, Poison and Wither gone (it heals anything, monsters too). If the
	 * mending came from a web slinger's white symbiote, it pays for it in satiety. Returns how much was healed.
	 */
	public static float mend(ServerLevel level, @Nullable Entity from, LivingEntity target, float amount) {
		float before = target.getHealth();
		target.heal(amount);
		target.removeEffect(MobEffects.POISON);
		target.removeEffect(MobEffects.WITHER);
		float healed = Math.max(0.0F, target.getHealth() - before);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 8, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.0);
		level.sendParticles(new DustParticleOptions(WHITE, 1.2F), at.x, at.y, at.z, 12, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3,
			target.getBbWidth() * 0.4, 0.0);
		level.sendParticles(new DustParticleOptions(WHITE_SHEEN, 0.8F), at.x, at.y, at.z, 6, 0.2, 0.3, 0.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.5F);
		if (from instanceof ServerPlayer owner) {
			paid(owner, healed);
		}
		return healed;
	}

	/**
	 * Healing costs the white one {@link #HEAL_SATIETY_COST} satiety a heart; out of it, it sleeps. (Creative heals for
	 * free; only the white one pays.)
	 */
	static void paid(ServerPlayer owner, float healed) {
		if (healed <= 0.0F || owner.isCreative() || !WebSymbiote.isBonded(owner) || of(owner) != WebSymbioteStrain.WHITE || asleep(owner)) {
			return;
		}
		Live live = live(owner);
		live.debt += healed / 2.0F * HEAL_SATIETY_COST;
		int whole = (int)live.debt;
		if (whole <= 0) {
			return;
		}
		live.debt -= whole;
		if (WebSymbiote.spendSatiety(owner, whole) <= 0) {
			sleep(owner);
		}
	}

	/** Healed itself out: it lets go, the suit withdraws, and it sleeps for {@link #DORMANT_TICKS} (a corpse wakes it). */
	static void sleep(ServerPlayer player) {
		WebSymbiote.letGoOfWheel(player);
		WebSymbiote.withdrawForSleep(player);
		WebSymbioteBond.setDormant(player, DORMANT_TICKS);
		player.setAttached(ASLEEP, true);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.8F, 0.5F);
		level.sendParticles(new DustParticleOptions(WHITE, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.35, 0.7, 0.35, 0.0);
		Shakes.send(player, 0.4F);
		WebSymbiote.whisper(player, "white.sleep");
	}

	/** Fed while asleep: it wakes early. (Nothing happens if it isn't asleep.) */
	static void wake(ServerPlayer player) {
		if (!asleep(player)) {
			return;
		}
		WebSymbioteBond.setDormant(player, 0);
		player.removeAttached(ASLEEP);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(new DustParticleOptions(WHITE, 1.4F), player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.35, 0.8, 0.35, 0.0);
		Shakes.send(player, 0.4F);
		WebSymbiote.whisper(player, "white.wake");
	}

	/**
	 * Recoil, the white one's Spike Burst: the same pose and push, rounded nubs instead of spikes (the client draws them),
	 * {@link #RECOIL_SHARE} of the harm, and any player in range healed {@link #RECOIL_HEAL} instead of hurt.
	 */
	static void recoil(ServerPlayer player, Vec3 center, double radius) {
		ServerLevel level = player.level();
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(center);
			if (to.length() > radius + living.getBbWidth() * 0.5) {
				continue;
			}
			if (living instanceof Player) {
				mend(level, player, living, RECOIL_HEAL);
				continue;
			}
			living.hurtServer(level, level.damageSources().playerAttack(player), WebSymbiote.SPIKE_DAMAGE * RECOIL_SHARE);
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
			Vec3 push = flat.scale(WebSymbiote.SPIKE_KNOCKBACK).add(0.0, 0.45, 0.0);
			living.setDeltaMovement(living.getDeltaMovement().add(push));
			living.needsSync = true;
			living.syncVelocity = true;
			level.sendParticles(ParticleTypes.CLOUD, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
		}
		// a soft white ring rolling out, a glint of gold: no fire, no black
		for (int ring = 1; ring <= 3; ring++) {
			double r = ring * radius / 3.0;
			int count = 12 + ring * 6;
			for (int k = 0; k < count; k++) {
				double a = k * Math.PI * 2.0 / count + ring;
				level.sendParticles(new DustParticleOptions(ring == 2 ? WHITE_SHEEN : WHITE, 1.6F - ring * 0.2F), center.x + Math.cos(a) * r,
					center.y - 0.6, center.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 16, 0.6, 0.5, 0.6, 0.12);
		level.sendParticles(ParticleTypes.CLOUD, center.x, center.y - 0.5, center.z, 16, 0.8, 0.2, 0.8, 0.08);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		// it bursts out of you all the same: your screen kicks
		Shakes.send(player, WebSymbiote.SPIKE_SHAKE * 0.7F, null);
	}

	// ---- rivals ------------------------------------------------------------------------------------------------------

	/** The nearest rival symbiote within {@link #RIVAL_RANGE} of {@code player}'s, or null. */
	static @Nullable LivingEntity rival(ServerPlayer player) {
		if (!WebSymbiote.isBonded(player)) {
			return null;
		}
		return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RIVAL_RANGE),
				e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && p.isCreative())
					&& e.distanceToSqr(player) <= RIVAL_RANGE * RIVAL_RANGE && rivals(player, e))
			.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
	}

	/** A push sent to a player's client too (the blob's lash at a rival web slinger). */
	static void nudge(Entity entity, Vec3 push) {
		entity.setDeltaMovement(entity.getDeltaMovement().add(push));
		entity.needsSync = true;
		entity.syncVelocity = true;
		if (entity instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, new WebNet.Nudge(push));
		}
	}
}
