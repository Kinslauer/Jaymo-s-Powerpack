package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.monsters.Werewolf;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.0) Lycanthropy, stage 2 of the 0.10.0 drop ({@code docs/Vampires-Plan.md}). The curse lies quietly in some of
 * the world's people (villagers, MCA's too, and realms' folk): by day, and on most nights, nothing shows. Under a full
 * moon each of them changes: they double over and convulse (bones cracking, a scream), then the beast tears out of
 * them: a {@link Werewolf} of the legend's kind, but each its own, its size, its build and its coat theirs for life
 * ({@link Curse}), the muzzle pushing out of the face, the claws and ears and tail growing, the body swelling as it
 * shakes ({@link Werewolf#beastness}, drawn by its model). The beast hunts all night (villagers, players, and any vampire
 * showing itself); at dawn it shrinks back into the person, who wakes on the ground sick and shaking. Kill the beast and
 * the person is dead.
 *
 * <p>A bite from one that someone lives through passes the curse on ({@link #BITE_CHANCE}). Now and then the curse turns
 * up in someone new near a player ({@link #SEED_CHANCE} a day). A vampire can't carry it, nor can one carrying it turn.
 * The person is hidden inside the beast (invisible, frozen, carried along) and can't be hurt there. Ops:
 * {@code /kingdom werewolf curse|change|cure|moon}.
 */
public final class Lycans {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The chance a bite someone survives passes the curse on. */
	static final float BITE_CHANCE = 0.4F;
	/** Each dusk from this day, the chance the curse turns up in someone new near a player (20-64 blocks off). */
	static final long SEED_FROM_DAY = 4;
	static final float SEED_CHANCE = 0.15F;
	static final double SEED_RANGE = 64.0, SEED_UNSEEN = 20.0;
	/** Sizes (times) and builds (lean, middling, heavy: shares). */
	static final float SCALE_MIN = 0.8F, SCALE_MAX = 1.4F;
	static final float LEAN_CHANCE = 0.3F, HEAVY_CHANCE = 0.3F;
	/** One in this many is the fiery coat. */
	static final int FIRE_ODDS = 20;
	/** The person convulsing before the beast tears out (ticks); the changes start up to this long after moonrise. */
	static final int CONVULSE_TICKS = 50, STAGGER_TICKS = 200;
	/** After the change back: sick and shaking this long (ticks). */
	static final int AFTER_TICKS = 1200;

	// ---- what's kept ---------------------------------------------------------------------------------------------------

	/** The curse in someone: their beast's coat, size and build, the last night they changed, and (a player) how many moons they've changed under. */
	record Curse(int coat, float scale, int build, long night, int moons, boolean half) {
		static final Codec<Curse> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("coat").forGetter(Curse::coat),
			Codec.FLOAT.fieldOf("scale").forGetter(Curse::scale),
			Codec.INT.fieldOf("build").forGetter(Curse::build),
			Codec.LONG.fieldOf("night").forGetter(Curse::night),
			Codec.INT.optionalFieldOf("moons", 0).forGetter(Curse::moons),
			// (0.7.66) a wolfblood: half-cursed by a ritual done under any moon but the full one; never changes ({@link NightGifts})
			Codec.BOOL.optionalFieldOf("half", false).forGetter(Curse::half)).apply(i, Curse::new));

		Curse changedOn(long nightNo) {
			return new Curse(this.coat, this.scale, this.build, nightNo, this.night == nightNo ? this.moons : this.moons + 1, this.half);
		}

		Curse halved() {
			return new Curse(this.coat, this.scale, this.build, this.night, this.moons, true);
		}

		Curse withMoons(int moons) {
			return new Curse(this.coat, this.scale, this.build, this.night, moons, false);
		}
	}

	static final AttachmentType<Curse> CURSE = AttachmentRegistry.create(JaymosKingdoms.id("lycanthropy"),
		builder -> builder.persistent(Curse.CODEC).copyOnDeath());
	/** On the Overworld: the night an operator called a change for (-1 for none). */
	static final AttachmentType<Long> FORCED = AttachmentRegistry.create(JaymosKingdoms.id("lycan_moon"), builder -> builder.persistent(Codec.LONG));

	/** The cursed who are loaded; those convulsing (when the beast comes out); the beasts by their person. */
	private static final Map<UUID, PathfinderMob> LOADED = new HashMap<>();
	private static final Map<UUID, Long> CONVULSING = new HashMap<>();
	private static final Map<UUID, Long> DUE = new HashMap<>();
	private static final Map<UUID, Werewolf> BEASTS = new HashMap<>();
	private static final List<Entity> DISCARD = new ArrayList<>();

	private Lycans() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Werewolf w && w.host() != null && !BEASTS.containsKey(w.host()) && !WerewolfPlayers.current(w)) {
				// (a beast whose night was cut short: its person shows again when they load)
				DISCARD.add(w);
				return;
			}
			if (entity instanceof PathfinderMob mob && mob.hasAttached(CURSE)) {
				LOADED.put(mob.getUUID(), mob);
				if (!BEASTS.containsKey(mob.getUUID()) && mob.isInvisible() && mob.isNoAi()) {
					show(mob);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(Lycans::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Lycans::died);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(BEASTS.containsKey(entity.getUUID()) && entity.isInvisible()) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Werewolf w : new ArrayList<>(BEASTS.values())) {
				back(w, false);
			}
			for (UUID id : new ArrayList<>(CONVULSING.keySet())) {
				PathfinderMob mob = LOADED.get(id);
				if (mob != null) {
					mob.setNoAi(false);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			CONVULSING.clear();
			DUE.clear();
			BEASTS.clear();
			DISCARD.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("werewolf").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("curse").executes(c -> curseNearest(c.getSource())))
				.then(Commands.literal("change").executes(c -> changeNearest(c.getSource())))
				.then(Commands.literal("cure").executes(c -> cureNearest(c.getSource())))
				.then(Commands.literal("moon").executes(c -> forceMoon(c.getSource()))))));
	}

	// ---- who's what ----------------------------------------------------------------------------------------------------

	public static boolean cursed(Entity e) {
		return e.hasAttached(CURSE);
	}

	/** Someone hidden inside their beast, or doubled over changing. */
	public static boolean changed(Entity e) {
		return BEASTS.containsKey(e.getUUID()) || CONVULSING.containsKey(e.getUUID());
	}

	/** A full moon tonight (or one an operator called). */
	static boolean fullMoon(ServerLevel overworld, long clock) {
		Long forced = overworld.getAttached(FORCED);
		if (forced != null && forced == Vampires.nightIndex(clock)) {
			return true;
		}
		return overworld.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.FULL_MOON;
	}

	/** The curse laid on {@code mob}: its beast rolled (the coat by the cold or heat of where they live). */
	static void curse(ServerLevel level, PathfinderMob mob) {
		mob.setAttached(CURSE, roll(level, mob));
		mob.removeAttached(Vampires.BITTEN);
		LOADED.put(mob.getUUID(), mob);
	}

	/** (0.7.1) The colours of the clothes a beast's rags were (by the villager's trade; folk and players get a plain cloth). */
	private static final Map<String, Integer> TRADE_CLOTHES = Map.ofEntries(Map.entry("farmer", 0x9C7A3C), Map.entry("fisherman", 0x4E6A7A),
		Map.entry("shepherd", 0xC8C0B0), Map.entry("fletcher", 0x6E7F4A), Map.entry("librarian", 0x8A2E2E), Map.entry("cartographer", 0x3E5A8A),
		Map.entry("cleric", 0x6A3A7A), Map.entry("armorer", 0x5A5A5A), Map.entry("weaponsmith", 0x3A3A3A), Map.entry("toolsmith", 0x5A4A3A),
		Map.entry("butcher", 0xB8B0A0), Map.entry("leatherworker", 0x7A4A2A), Map.entry("mason", 0x8A8A80), Map.entry("nitwit", 0x6A8A4A));
	private static final int[] PLAIN_CLOTHES = {0xB8A27A, 0x7A6A55, 0x5A6A7A, 0x8A5A4A, 0x9A9A8A, 0x4A5A3A};

	/** (0.7.1) How {@code who}'s beast wears its coat and what's left of their clothes: fixed by who they are. */
	static int look(Entity who) {
		long bits = who.getUUID().getLeastSignificantBits() ^ who.getUUID().getMostSignificantBits() >>> 7;
		return 1 + (int) Math.floorMod(bits, (long) Werewolf.WEARS * Werewolf.RAG_SETS);
	}

	/** (0.7.1) The colour of the clothes {@code who} wore when they changed. */
	static int clothes(Entity who) {
		if (who instanceof Villager v) {
			String trade = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
			Integer colour = TRADE_CLOTHES.get(trade);
			if (colour != null) {
				return colour;
			}
		}
		return PLAIN_CLOTHES[Math.floorMod(who.getUUID().hashCode(), PLAIN_CLOTHES.length)];
	}

	/** A beast of their own for {@code e}. */
	static Curse roll(ServerLevel level, LivingEntity mob) {
		RandomSource random = level.getRandom();
		float temperature = level.getBiome(mob.blockPosition()).value().getBaseTemperature();
		int coat;
		if (random.nextInt(FIRE_ODDS) == 0) {
			coat = Werewolf.FIRE_COAT;
		} else if (temperature < 0.3F) {
			// (timber-grey in the cold, brown in the heat, any of the three elsewhere)
			coat = 2;
		} else if (temperature > 1.0F) {
			coat = 1;
		} else {
			coat = random.nextInt(Werewolf.FIRE_COAT);
		}
		float scale = SCALE_MIN + random.nextFloat() * (SCALE_MAX - SCALE_MIN);
		float b = random.nextFloat();
		int build = b < LEAN_CHANCE ? 0 : b < LEAN_CHANCE + HEAVY_CHANCE ? 2 : 1;
		return new Curse(coat, scale, build, -1L, 0, false);
	}

	static void cure(PathfinderMob mob) {
		Werewolf w = BEASTS.get(mob.getUUID());
		if (w != null) {
			back(w, false);
		}
		if (CONVULSING.remove(mob.getUUID()) != null) {
			mob.setNoAi(false);
		}
		mob.removeAttached(CURSE);
		LOADED.remove(mob.getUUID());
	}

	/** (Werewolf) a cursed beast bit {@code target}: if they live through it, the curse may be theirs too. */
	public static void bite(ServerLevel level, Werewolf beast, Entity target) {
		if (target instanceof PathfinderMob mob && mob.isAlive() && !mob.isBaby() && (mob instanceof Villager || mob instanceof Folk)
			&& !cursed(mob) && !Vampires.isVampire(mob) && level.getRandom().nextFloat() < BITE_CHANCE) {
			curse(level, mob);
		}
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!DISCARD.isEmpty()) {
			DISCARD.forEach(Entity::discard);
			DISCARD.clear();
		}
		ServerLevel overworld = server.overworld();
		long clock = overworld.getOverworldClockTime();
		long now = overworld.getGameTime();
		boolean night = Vampires.night(clock);
		long nightNo = Vampires.nightIndex(clock);
		if (Math.floorMod(clock, 24000L) == Vampires.NIGHT_FROM) {
			seed(overworld, clock);
		}
		// the beasts: carrying their people; at dawn, back
		for (Werewolf w : new ArrayList<>(BEASTS.values())) {
			PathfinderMob host = w.host() == null ? null : LOADED.get(w.host());
			if (!w.isAlive() || w.isRemoved()) {
				BEASTS.values().remove(w);
				if (host != null && host.isAlive()) {
					show(host);
				}
				continue;
			}
			if (host != null) {
				host.setPos(w.getX(), w.getY(), w.getZ());
				host.setDeltaMovement(Vec3.ZERO);
				host.fallDistance = 0.0;
			}
			if (!night) {
				if (!(w.level() instanceof ServerLevel l) || !l.isPositionEntityTicking(w.blockPosition())) {
					// (nobody near to see it: they're simply themselves again)
					back(w, true);
					continue;
				}
				w.changeBack();
			}
			int c = w.change();
			if (c > 0 && w.level() instanceof ServerLevel level) {
				changingIn(level, w, c);
			} else if (c < 0) {
				if (c >= -1) {
					back(w, true);
				} else if (w.level() instanceof ServerLevel level && level.getRandom().nextInt(4) == 0) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, w.getX(), w.getY(0.6), w.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
				}
			}
		}
		if (LOADED.isEmpty()) {
			return;
		}
		boolean moon = night && fullMoon(overworld, clock);
		for (PathfinderMob mob : new ArrayList<>(LOADED.values())) {
			if (!mob.isAlive() || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				LOADED.remove(mob.getUUID());
				continue;
			}
			UUID id = mob.getUUID();
			Long started = CONVULSING.get(id);
			if (started != null) {
				convulse(level, mob, now - started);
				if (now - started >= CONVULSE_TICKS) {
					CONVULSING.remove(id);
					tearOut(level, mob, nightNo);
				}
				continue;
			}
			if (!moon || BEASTS.containsKey(id)) {
				DUE.remove(id);
				continue;
			}
			Curse curse = mob.getAttached(CURSE);
			if (curse == null || curse.night() == nightNo) {
				continue;
			}
			long due = DUE.computeIfAbsent(id, k -> now + level.getRandom().nextInt(STAGGER_TICKS));
			if (now >= due) {
				DUE.remove(id);
				begin(level, mob, now);
			}
		}
	}

	/** At dusk, now and then, the curse turns up in someone new near a player. */
	private static void seed(ServerLevel overworld, long clock) {
		if (Math.floorDiv(clock, 24000L) < SEED_FROM_DAY || overworld.getRandom().nextFloat() >= SEED_CHANCE) {
			return;
		}
		var players = overworld.players().stream().filter(p -> !p.isSpectator()).toList();
		if (players.isEmpty()) {
			return;
		}
		var near = players.get(overworld.getRandom().nextInt(players.size()));
		List<PathfinderMob> candidates = overworld.getEntitiesOfClass(PathfinderMob.class, near.getBoundingBox().inflate(SEED_RANGE),
			m -> m.isAlive() && !m.isBaby() && (m instanceof Villager || m instanceof Folk) && !cursed(m) && !Vampires.isVampire(m)
				&& !m.hasAttached(Vampires.BITTEN) && players.stream().noneMatch(p -> p.distanceTo(m) < SEED_UNSEEN));
		if (!candidates.isEmpty()) {
			PathfinderMob chosen = candidates.get(overworld.getRandom().nextInt(candidates.size()));
			curse(overworld, chosen);
			JaymosKingdoms.LOGGER.debug("The wolf's curse is in {}", chosen.getName().getString());
		}
	}

	// ---- the change ----------------------------------------------------------------------------------------------------

	/** The moon's up: they stop where they are and double over. */
	private static void begin(ServerLevel level, PathfinderMob mob, long now) {
		if (mob.isSleeping()) {
			mob.stopSleeping();
		}
		if (mob.isPassenger()) {
			mob.stopRiding();
		}
		mob.getNavigation().stop();
		mob.setNoAi(true);
		CONVULSING.put(mob.getUUID(), now);
		level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.HOSTILE, 1.2F, 0.6F);
	}

	/** Jerking, twisting, bones cracking. */
	static void convulse(ServerLevel level, LivingEntity mob, long t) {
		RandomSource random = level.getRandom();
		float violence = Math.min(1.0F, 0.3F + t / (float) CONVULSE_TICKS);
		if (!(mob instanceof net.minecraft.world.entity.player.Player)) {
			convulseBody(mob, random, violence);
		}
		if (t % 6 == 0) {
			level.playSound(null, mob.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.0F, 0.5F + random.nextFloat() * 0.4F);
		}
		if (t % 14 == 7) {
			level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.HOSTILE, 1.0F, 0.5F + random.nextFloat() * 0.3F);
		}
		if (t % 3 == 0) {
			level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0x5A4632, 1.2F), mob.getX(), mob.getY(0.6), mob.getZ(),
				2, 0.25, 0.4, 0.25, 0.05);
		}
	}

	private static void convulseBody(LivingEntity mob, RandomSource random, float violence) {
		mob.setYRot(mob.getYRot() + (random.nextFloat() - 0.5F) * 70.0F * violence);
		mob.setYBodyRot(mob.getYRot());
		mob.setYHeadRot(mob.getYRot() + (random.nextFloat() - 0.5F) * 90.0F * violence);
		mob.setXRot(Math.max(-30.0F, Math.min(80.0F, 40.0F + (random.nextFloat() - 0.5F) * 80.0F * violence)));
	}

	/** The beast tears out: the person hidden inside it, the beast changing on (its own animation). */
	private static void tearOut(ServerLevel level, PathfinderMob mob, long nightNo) {
		Curse curse = mob.getAttached(CURSE);
		Werewolf w = curse == null ? null : beast(level, mob, curse);
		if (w == null) {
			mob.setNoAi(false);
			return;
		}
		mob.setAttached(CURSE, curse.changedOn(nightNo));
		BEASTS.put(mob.getUUID(), w);
		level.addFreshEntity(w);
		hide(mob);
		tearFx(level, mob);
	}

	/** {@code who}'s beast, changing in, where they stand (not yet in the world). */
	static @Nullable Werewolf beast(ServerLevel level, LivingEntity who, Curse curse) {
		Werewolf w = Monsters.WEREWOLF.create(level, EntitySpawnReason.TRIGGERED);
		if (w == null) {
			return null;
		}
		w.snapTo(who.getX(), who.getY(), who.getZ(), who.getYRot(), 0.0F);
		// (any face: the cursed are never seen as the legend's wanderer)
		w.setVariant(curse.coat());
		w.curse(who.getUUID(), curse.build(), look(who), clothes(who));
		AttributeInstance scale = w.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(curse.scale());
		}
		AttributeInstance health = w.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			// (heavier, bigger beasts take more killing)
			health.setBaseValue(health.getBaseValue() * curse.scale() * (curse.build() == 2 ? 1.2 : curse.build() == 0 ? 0.85 : 1.0));
			w.setHealth(w.getMaxHealth());
		}
		if (who.hasCustomName()) {
			w.setCustomName(who.getCustomName());
			w.setCustomNameVisible(false);
		}
		return w;
	}

	/** The beast's bursting out: smoke, blood, a crack. */
	static void tearFx(ServerLevel level, LivingEntity mob) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY(0.5), mob.getZ(), 20, 0.4, 0.6, 0.4, 0.03);
		level.sendParticles(PowersCompat.blood(0x8A0303, 2.0F), mob.getX(), mob.getY(0.6), mob.getZ(),
			16, 0.3, 0.4, 0.3, 0.1);
		level.playSound(null, mob.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.5F, 0.4F);
	}

	/** The beast changing on: shaking, swelling, growling. */
	static void changingIn(ServerLevel level, Werewolf w, int left) {
		RandomSource random = level.getRandom();
		if (left % 7 == 0) {
			level.playSound(null, w.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.0F, 0.45F + random.nextFloat() * 0.3F);
		}
		if (left == Werewolf.CURSE_IN_TICKS / 2) {
			w.playSound(SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.ANGRY).adultSounds().growlSound().value(), 2.0F, 0.5F);
		}
		if (left == 1) {
			w.playSound(SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.BIG).adultSounds().whineSound().value(), 2.5F, 0.4F);
		}
		if (random.nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, w.getX(), w.getY(0.6), w.getZ(), 2, 0.3, 0.4, 0.3, 0.02);
		}
	}

	/** Dawn (or the night cut short): the beast gone, the person on the ground where it stood. */
	private static void back(Werewolf w, boolean sick) {
		UUID id = w.host();
		BEASTS.values().remove(w);
		if (id != null) {
			BEASTS.remove(id);
		}
		PathfinderMob host = id == null ? null : LOADED.get(id);
		Vec3 at = w.position();
		w.discard();
		if (host == null || !(host.level() instanceof ServerLevel level)) {
			return;
		}
		// (0.7.281) the person back where the beast was, whoever spoke to them last
		Attention.carry(() -> host.teleportTo(at.x, at.y, at.z));
		show(host);
		if (sick) {
			host.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, AFTER_TICKS, 1));
			host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, AFTER_TICKS, 1));
			host.addEffect(new MobEffectInstance(MobEffects.NAUSEA, AFTER_TICKS / 4, 0));
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 16, 0.3, 0.5, 0.3, 0.02);
			level.playSound(null, host.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.0F, 0.7F);
		}
	}

	private static void hide(PathfinderMob mob) {
		mob.setInvisible(true);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.noPhysics = true;
	}

	private static void show(PathfinderMob mob) {
		mob.setInvisible(false);
		mob.setNoAi(false);
		mob.setNoGravity(false);
		mob.noPhysics = false;
		mob.fallDistance = 0.0;
	}

	/** The beast slain: the person inside dies with it. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (entity instanceof Werewolf w && w.host() != null && !WerewolfPlayers.puppet(w)) {
			UUID id = w.host();
			BEASTS.remove(id);
			PathfinderMob host = LOADED.get(id);
			if (host != null && host.isAlive() && host.level() instanceof ServerLevel level) {
				Attention.carry(() -> host.teleportTo(w.getX(), w.getY(), w.getZ()));
				show(host);
				host.getCombatTracker().recordDamage(source, host.getHealth());
				host.setHealth(0.0F);
				host.die(source);
			}
			return;
		}
		CONVULSING.remove(entity.getUUID());
		DUE.remove(entity.getUUID());
		LOADED.remove(entity.getUUID());
	}

	// ---- operators -----------------------------------------------------------------------------------------------------

	private static @Nullable PathfinderMob nearest(CommandSourceStack source, boolean cursed) {
		Vec3 at = source.getPosition();
		PathfinderMob best = null;
		double bestD = Double.MAX_VALUE;
		for (PathfinderMob m : source.getLevel().getEntitiesOfClass(PathfinderMob.class, new net.minecraft.world.phys.AABB(at, at).inflate(16.0),
			m -> m.isAlive() && !m.isBaby() && (m instanceof Villager || m instanceof Folk) && cursed == cursed(m) && (cursed || !Vampires.isVampire(m)))) {
			double d = m.distanceToSqr(at);
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	private static int curseNearest(CommandSourceStack source) {
		PathfinderMob mob = nearest(source, false);
		if (mob == null) {
			source.sendFailure(Component.literal("No grown villager or folk (not a vampire) within 16 blocks"));
			return 0;
		}
		curse(source.getLevel(), mob);
		source.sendSuccess(() -> Component.literal(mob.getName().getString() + " carries the wolf's curse"), true);
		return 1;
	}

	private static int changeNearest(CommandSourceStack source) {
		PathfinderMob mob = nearest(source, true);
		if (mob == null || changed(mob)) {
			source.sendFailure(Component.literal("No cursed villager or folk within 16 blocks, or they've changed already"));
			return 0;
		}
		begin(source.getLevel(), mob, source.getLevel().getGameTime());
		source.sendSuccess(() -> Component.literal(mob.getName().getString() + " changes"), true);
		return 1;
	}

	private static int cureNearest(CommandSourceStack source) {
		PathfinderMob mob = nearest(source, true);
		if (mob == null) {
			source.sendFailure(Component.literal("No cursed villager or folk within 16 blocks"));
			return 0;
		}
		cure(mob);
		source.sendSuccess(() -> Component.literal(mob.getName().getString() + " is free of the curse"), true);
		return 1;
	}

	private static int forceMoon(CommandSourceStack source) {
		ServerLevel overworld = source.getServer().overworld();
		long clock = overworld.getOverworldClockTime();
		long night = Vampires.night(clock) ? Vampires.nightIndex(clock) : Vampires.nightIndex(clock) + 1;
		overworld.setAttached(FORCED, night);
		source.sendSuccess(() -> Component.literal("The cursed change tonight"), true);
		return 1;
	}
}
