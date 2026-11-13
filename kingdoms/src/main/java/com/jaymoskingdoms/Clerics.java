package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Cleric (a priest sworn to a patron god, picked with the class and sworn at once, {@link Patrons}) and the holy symbol both
 * a Cleric and a Paladin carry.
 *
 * <ul>
 * <li><b>Turn the undead:</b> hold the raised symbol ({@link CastingFocus}) and the dead before you in its cone feel it. Each
 * raising spends one of the day's turnings ({@link #channels}: {@link #CHANNEL_BASE} and one a tier of the patron's favour; they
 * come back after a short rest, {@link Rests}). The weak among them crumble (their health no more than
 * {@link #CRUMBLE_BASE} and {@link #CRUMBLE_PER_TIER} a tier, no bosses), the rest are afraid ({@link Fear}, the great ones
 * after their {@link Legendary} resistances) and run from the holder.</li>
 * <li><b>Healing prayers:</b> a tap casts the spell the symbol is turned to (mending first; sanctuary, restoration, and the death
 * patch's revive are learned from scrolls, {@link ClassCasters}).</li>
 * <li><b>Bless the band:</b> the class key with the symbol in hand spends a turning and gives every friend within
 * {@link #BLESS_RANGE} blocks a minute or three of warding: a little absorption, a steadier nerve against fear and charms
 * ({@link #saveBonus}) and a fright lifted.</li>
 * </ul>
 */
public final class Clerics {
	public static Item HOLY_SYMBOL;
	/** Turnings a day before the patron's favour adds to them (a Paladin has {@link #PALADIN_CHANNEL}). */
	static final int CHANNEL_BASE = 2, PALADIN_CHANNEL = 1;
	/** How far the symbol reaches (blocks), the cone it covers (cosine to the look), how long a fright lasts (ticks), the longest a raising holds (ticks). */
	static final double TURN_RANGE = 14.0, CONE_COS = 0.35;
	static final int TURN_FEAR = 160, RAISE_MAX = 200, TURN_EVERY = 5;
	/** The health of the weakest dead that crumble, and what each tier of favour adds. */
	static final float CRUMBLE_BASE = 16.0F, CRUMBLE_PER_TIER = 4.0F;
	/** How far a blessing reaches (blocks), how long it lasts (ticks), the absorption it gives (health) and the nerve (a share added to every save). */
	static final double BLESS_RANGE = 10.0;
	static final int BLESS_TICKS = 20 * 60 * 3;
	static final float BLESS_SAVE = 0.15F;
	/** The dead that are running, how long each runs for, and the blessed. */
	private static final Map<Mob, long[]> FLEEING = new HashMap<>();
	private static final Map<UUID, Long> BLESSED = new HashMap<>();
	private static final Map<UUID, Boolean> RAISING = new HashMap<>();

	private Clerics() {
	}

	static void register() {
		HOLY_SYMBOL = Arcana.item("holy_symbol", p -> new CastingFocus(p.stacksTo(1), true));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(HOLY_SYMBOL));
		ServerTickEvents.END_SERVER_TICK.register(Clerics::tick);
	}

	static void clear() {
		FLEEING.clear();
		BLESSED.clear();
		RAISING.clear();
	}

	/** A new Cleric swears to the patron they picked (their favour a devout beginning's), and the symbol's making is taught by a priest. */
	static void begin(ServerPlayer player, String option) {
		Patron patron = Patron.byKey(option);
		if (patron != null && Patrons.of(player) != patron) {
			Patrons.swear(player, patron, Patrons.FAVOUR_DEVOUT, Patrons.today(player.level().getServer()));
		}
	}

	/** How many turnings a day {@code player} has in all. */
	public static int channels(Player player) {
		int tier = Patrons.state(player).tier();
		return (Classes.is(player, PlayerClass.PALADIN) ? PALADIN_CHANNEL : CHANNEL_BASE) + tier;
	}

	/** The health the dead may have for {@code player}'s symbol to unmake them. */
	static float crumbleHealth(Player player) {
		return CRUMBLE_BASE + CRUMBLE_PER_TIER * Patrons.state(player).tier();
	}

	private static boolean holds(ServerPlayer player) {
		return player.getMainHandItem().is(HOLY_SYMBOL) || player.getOffhandItem().is(HOLY_SYMBOL);
	}

	/** The symbol is raised and has been held {@code held} ticks. */
	static void raised(ServerPlayer player, ServerLevel level, int held) {
		if (!Classes.is(player, PlayerClass.CLERIC) && !Classes.is(player, PlayerClass.PALADIN)) {
			player.stopUsingItem();
			return;
		}
		if (held == CastingFocus.RAISE_TICKS) {
			if (!Rests.spend(player, Rests.CHANNEL, 1, channels(player))) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cleric.silent"));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.4F, 0.6F);
				player.stopUsingItem();
				return;
			}
			RAISING.put(player.getUUID(), true);
			colour(player);
			Fx.ring(level, player.position().add(0.0, 0.1, 0.0), (float) TURN_RANGE * 0.6F, colour(player));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
		}
		if (held < CastingFocus.RAISE_TICKS || !RAISING.containsKey(player.getUUID())) {
			return;
		}
		if (held > RAISE_MAX) {
			player.stopUsingItem();
			return;
		}
		if (held % TURN_EVERY == 0) {
			turn(player, level);
		}
	}

	/** The symbol comes down. */
	static void lowered(ServerPlayer player) {
		RAISING.remove(player.getUUID());
	}

	private static int colour(ServerPlayer player) {
		Patron patron = Patrons.of(player);
		return patron == null ? 0xFFE9A0 : patron.colour & 0xFFFFFF;
	}

	/** One pulse of the raised symbol over the dead before the holder. */
	private static void turn(ServerPlayer player, ServerLevel level) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4) {
			look = player.getLookAngle();
		}
		look = look.normalize();
		Vec3 eye = player.getEyePosition();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(TURN_RANGE), m -> m.isAlive() && Restoration.undead(m))) {
			Vec3 to = mob.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > TURN_RANGE * TURN_RANGE || to.lengthSqr() < 1.0E-4 || look.dot(to.normalize()) < CONE_COS || !player.hasLineOfSight(mob)) {
				continue;
			}
			Fx.beam(level, eye.add(0.0, -0.3, 0.0), mob.getBoundingBox().getCenter(), 0.05F, colour(player), 6);
			if (!Legendary.boss(mob) && mob.getMaxHealth() <= crumbleHealth(player)) {
				// the weak dead are unmade
				mob.hurtServer(level, level.damageSources().indirectMagic(player, player), mob.getMaxHealth() * 2.0F);
				if (mob.isAlive()) {
					mob.igniteForSeconds(6.0F);
				}
				level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY(0.5), mob.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
				continue;
			}
			// the rest are afraid, and run from the one who holds the symbol
			if (Legendary.boss(mob) ? Fear.expose(mob, player, TURN_FEAR) : !Saves.resist(mob, Saves.Kind.FEAR)) {
				Fear.frighten(mob, player, TURN_FEAR);
			}
			if (Fear.frightened(mob)) {
				FLEEING.put(mob, new long[] {level.getGameTime() + TURN_FEAR, player.getId()});
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (!FLEEING.isEmpty()) {
			Iterator<Map.Entry<Mob, long[]>> it = FLEEING.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Mob, long[]> e = it.next();
				Mob mob = e.getKey();
				if (!mob.isAlive() || mob.isRemoved() || mob.level().getGameTime() >= e.getValue()[0] || !Fear.frightened(mob)) {
					it.remove();
					continue;
				}
				if (mob.tickCount % 10 == 0 && mob.level().getEntity((int) e.getValue()[1]) instanceof Player from) {
					Vec3 away = mob.position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
					if (away.lengthSqr() > 1.0E-4) {
						Vec3 goal = mob.position().add(away.normalize().scale(10.0));
						mob.setTarget(null);
						mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.25);
					}
				}
			}
		}
		if (!BLESSED.isEmpty() && server.getTickCount() % 40 == 0) {
			long now = server.overworld().getGameTime();
			BLESSED.values().removeIf(until -> until <= now);
		}
	}

	/** The class key: with the symbol in hand, bless the band (spends a turning). */
	static void key(ServerPlayer player, boolean sneak) {
		if (!(player.level() instanceof ServerLevel level) || sneak) {
			return;
		}
		if (!holds(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cleric.symbol"));
			return;
		}
		if (!Rests.spend(player, Rests.CHANNEL, 1, channels(player))) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cleric.silent"));
			return;
		}
		bless(level, player);
	}

	/** Every friend near {@code player} is blessed: warded, steadied, a fright lifted. */
	static void bless(ServerLevel level, ServerPlayer player) {
		long until = level.getGameTime() + BLESS_TICKS;
		int tier = Patrons.state(player).tier();
		List<LivingEntity> band = new ArrayList<>();
		band.add(player);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BLESS_RANGE), e -> e != player && e.isAlive())) {
			if (e instanceof ServerPlayer || e instanceof TamableAnimal pet && pet.isOwnedBy(player)) {
				band.add(e);
			}
		}
		for (LivingEntity who : band) {
			who.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BLESS_TICKS, tier >= 3 ? 1 : 0, true, true, true), player);
			BLESSED.put(who.getUUID(), until);
			Fear.calm(who);
			level.sendParticles(ParticleTypes.END_ROD, who.getX(), who.getY(1.0), who.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			Fx.ring(level, who.position().add(0.0, 0.1, 0.0), 1.4F, colour(player));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** The share a blessing adds to {@code who}'s every save ({@link Saves}). */
	public static float saveBonus(LivingEntity who) {
		Long until = BLESSED.get(who.getUUID());
		return until != null && until > who.level().getGameTime() ? BLESS_SAVE : 0.0F;
	}

	/** A short rest: the turnings come back (a soft bell). */
	static void rested(ServerPlayer player) {
		if (Classes.is(player, PlayerClass.CLERIC) || Classes.is(player, PlayerClass.PALADIN)) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
	}

	/** Whether {@code stack} is a holy symbol. */
	public static boolean isSymbol(ItemStack stack) {
		return stack.is(HOLY_SYMBOL);
	}
}
