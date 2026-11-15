package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * GT New Horizons picks, patch 6 (Infernal Mobs' idea, none of its code: our own names, numbers and looks). A monster may
 * come up <b>marked</b> with one to four {@link Mark marks}: where the old sorcerer's mana lies in the ground, a thing of the
 * dark that rises there drinks some of it, and it shows on the creature as a faint aura (a Lore.md placement, for Andy to
 * approve). The harder the world has grown for the players near it (their {@link WorldTiers} tier), the likelier, and the
 * more marks.
 * <ul>
 * <li><b>No words.</b> Nothing names a mark: each shows by its own faint aura (a typed {@link Fx} mote, haze or spark, in
 * its own colour) and its own sound, now and then and when it acts. A monster with {@link #BAR_AT} marks or more has a boss
 * bar ({@link BossBars}: only for those who have struck it), with just its own name on it.</li>
 * <li><b>The marks</b>: Vengeance returns a share of a melee blow to the striker; Regen heals; Sapper drains its victim's
 * food; Webber webs its victim where they stand; Ghastly looses a fireball now and then; Blastoff throws its victim up;
 * Ninja now and then appears behind its foe; Sticky knocks the held weapon out of its victim's hand to the ground near by;
 * Rust wears its victim's armour faster; Storm, rarely, calls lightning down near its foe (only under the open sky).</li>
 * <li><b>Spoils</b>: a little more of its own loot, and experience, by how many marks it carried.</li>
 * <li><b>Captains</b> (Andy: "give these to captains"): the marks are also {@link Captains.Trait traits}, rolled with the
 * others; a captain's are read from his trait tags and act through the same code ({@link #step}, from
 * {@link CaptainWays#step}), so they do exactly what a wild monster's do.</li>
 * </ul>
 * Never on bosses, captains' bodies (they have their own), villagers and folk, thralls or anyone's tamed or owned beast.
 */
public final class MarkedMonsters {
	// ================================================================================================================
	// Tuning (everything starts gentle)
	/** Chance a monster rises marked, by the world tier of the players near it: Hearth, Borderlands, Wildlands, Dread, Legend. */
	static final float[] CHANCE = {0.01F, 0.03F, 0.07F, 0.14F, 0.20F};
	/** Once marked, the chance of each further mark (up to {@link #MOST}), by tier. */
	static final float[] MORE = {0.10F, 0.18F, 0.28F, 0.38F, 0.45F};
	static final int MOST = 4;
	/** How near (blocks) a player's tier counts for a monster rising. */
	static final double TIER_NEAR = 96.0;
	/** A boss bar from this many marks. */
	static final int BAR_AT = 3;
	/** The aura: one faint mote per mark this often (ticks), this big; a mark's sound now and then (ticks, plus up to as much again). */
	static final int AURA_EVERY = 10, SOUND_EVERY = 240;
	static final float AURA_SIZE = 0.55F, SOUND_VOLUME = 0.35F;
	/** Spoils: each drop's chance to come twice, and experience, for every mark. */
	static final float LOOT_PER_MARK = 0.15F;
	static final int XP_PER_MARK = 4;
	/** Vengeance: the share of a melee blow returned to the striker, within this reach (blocks). */
	static final float VENGEANCE_SHARE = 0.25F;
	static final double VENGEANCE_REACH = 5.0;
	/** Regen: health back this often (ticks). */
	static final float REGEN_HEAL = 1.0F;
	static final int REGEN_EVERY = 40;
	/** Sapper: food exhaustion a blow costs its victim (4 is one hunger point). */
	static final float SAPPER_EXHAUSTION = 6.0F;
	/** Webber: chance a blow webs, how long the web holds (ticks). */
	static final float WEB_CHANCE = 0.3F;
	static final int WEB_HOLD = 80;
	/** Ghastly: from this range to this (blocks), at most this often (ticks, plus up to half again). */
	static final double GHAST_MIN = 4.0, GHAST_MAX = 20.0;
	static final int GHAST_EVERY = 140;
	/** Blastoff: chance a blow throws its victim up, and how hard. */
	static final float BLAST_CHANCE = 0.3F;
	static final double BLAST_UP = 0.85;
	/** Ninja: within this range of its foe, at most this often (ticks, plus up to as much again), this far behind it. */
	static final double NINJA_RANGE = 16.0, NINJA_BEHIND = 2.0;
	static final int NINJA_EVERY = 200;
	/** Sticky: chance a blow knocks the weapon out of the hand, how far it lands (blocks), how long before it can be picked up (ticks). */
	static final float STICKY_CHANCE = 0.15F;
	static final double STICKY_THROW = 1.5;
	static final int STICKY_DELAY = 30;
	/** Rust: extra wear on each worn piece of armour a blow. */
	static final int RUST_WEAR = 2;
	/** Storm: within this range of its foe and no nearer than STORM_MIN, at most this often (ticks, plus up to as much again);
	 * the bolt lands this far from the foe (blocks), this long after the warning (ticks). */
	static final double STORM_RANGE = 24.0, STORM_MIN = 6.0, STORM_OFF_MIN = 2.0, STORM_OFF_MAX = 4.0;
	static final int STORM_EVERY = 700, STORM_WARN = 30;
	// ================================================================================================================

	/** The marks, each with its aura's look and its sound. Each is also a captain's trait of the same name. */
	public enum Mark {
		VENGEANCE(Look.MOTE, 0xB02424, SoundEvents.THORNS_HIT),
		REGEN(Look.MOTE, 0xF07AA8, SoundEvents.AMETHYST_BLOCK_CHIME),
		SAPPER(Look.MIASMA, 0x6E7F2A, SoundEvents.PLAYER_BURP),
		WEBBER(Look.MOTE, 0xE6E6E0, SoundEvents.COBWEB_PLACE),
		GHASTLY(Look.MOTE, 0xFF8A30, SoundEvents.GHAST_WARN),
		BLASTOFF(Look.MOTE, 0xA8E4FF, SoundEvents.BREEZE_JUMP),
		NINJA(Look.MIASMA, 0x24242C, SoundEvents.ILLUSIONER_MIRROR_MOVE),
		STICKY(Look.MOTE, 0xE0A030, SoundEvents.HONEY_BLOCK_SLIDE),
		RUST(Look.MOTE, 0xA0522D, SoundEvents.GRINDSTONE_USE),
		STORM(Look.SPARK, 0xFFF7B0, SoundEvents.LIGHTNING_BOLT_THUNDER);

		final Look look;
		final int color;
		final SoundEvent sound;

		Mark(Look look, int color, SoundEvent sound) {
			this.look = look;
			this.color = color;
			this.sound = sound;
		}

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The captain's trait that is this mark. */
		Captains.Trait trait() {
			return Captains.Trait.valueOf(name());
		}

		ParticleOptions particle(float size) {
			return switch (this.look) {
				case MOTE -> Fx.mote(this.color, size);
				case MIASMA -> Fx.miasma(this.color, size);
				case SPARK -> Fx.spark(this.color, size);
			};
		}
	}

	enum Look {
		MOTE, MIASMA, SPARK
	}

	/** A wild monster's marks, as bits by {@link Mark#ordinal}; 0 once it has risen unmarked (it rolls only once). */
	public static final AttachmentType<Integer> MARKS = AttachmentRegistry.create(JaymosKingdoms.id("marked"),
		builder -> builder.persistent(Codec.INT));

	/** Loaded marked wild monsters (captains are stepped by CaptainWays). */
	private static final Set<Mob> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
	/** When each marked one may next loose a fireball, step behind, call the storm. */
	private static final Map<UUID, Long> NEXT_GHAST = new HashMap<>(), NEXT_NINJA = new HashMap<>(), NEXT_STORM = new HashMap<>();
	/** The bars of those with {@link #BAR_AT} marks or more. */
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	private record Web(ServerLevel level, BlockPos pos, long until) {
	}

	private record Bolt(ServerLevel level, Vec3 at, long when) {
	}

	private static final List<Web> WEBS = new ArrayList<>();
	private static final List<Bolt> BOLTS = new ArrayList<>();

	private MarkedMonsters() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof Mob mob)) {
				return;
			}
			if (!mob.hasAttached(MARKS) && markable(mob)) {
				roll(level, mob);
			}
			if (mob.getAttachedOrElse(MARKS, 0) != 0 && !Captains.is(mob)) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				LOADED.remove(mob);
			}
			forget(entity.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MarkedMonsters::damaged);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			int n = of(entity).size();
			if (n > 0 && entity.level() instanceof ServerLevel level && entity.getLastHurtByPlayer() != null && entity.shouldDropExperience()) {
				ExperienceOrb.award(level, entity.position(), n * XP_PER_MARK);
			}
			forget(entity.getUUID());
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
			if (!(entity instanceof LivingEntity living) || entity instanceof Player
				|| table.unwrapKey().filter(k -> k.identifier().getPath().startsWith("entities/")).isEmpty()) {
				return;
			}
			int n = of(living).size();
			if (n == 0) {
				return;
			}
			List<ItemStack> extra = new ArrayList<>();
			for (ItemStack stack : drops) {
				if (!stack.isEmpty() && context.getRandom().nextFloat() < n * LOOT_PER_MARK) {
					extra.add(stack.copy());
				}
			}
			drops.addAll(extra);
		});
		ServerTickEvents.END_SERVER_TICK.register(MarkedMonsters::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (ServerBossEvent bar : BARS.values()) {
				bar.removeAllPlayers();
			}
			BARS.clear();
			LOADED.clear();
			NEXT_GHAST.clear();
			NEXT_NINJA.clear();
			NEXT_STORM.clear();
			WEBS.clear();
			BOLTS.clear();
		});
	}

	// ---- who is marked ---------------------------------------------------------------------------------------------------

	/** {@code e}'s marks: a wild monster's own, or a captain's marked traits. */
	public static Set<Mark> of(Entity e) {
		EnumSet<Mark> out = EnumSet.noneOf(Mark.class);
		int bits = e.getAttachedOrElse(MARKS, 0);
		boolean captain = Captains.is(e);
		for (Mark m : Mark.values()) {
			if (!captain && (bits & (1 << m.ordinal())) != 0 || captain && Captains.has(e, m.trait())) {
				out.add(m);
			}
		}
		return out;
	}

	/** Whether {@code e} carries a boss bar for its marks (its blows are tallied by {@link BossBars}). */
	static boolean barred(Entity e) {
		return of(e).size() >= BAR_AT;
	}

	/** A wild monster, nobody's, no boss, no captain, no villager or folk. */
	private static boolean markable(Mob mob) {
		return WorldTiers.hostile(mob) && !(mob instanceof Folk) && !(mob instanceof AbstractVillager) && !Orcs.isOrc(mob) && !Goblins.isGoblin(mob)
			&& !Captains.is(mob) && !Senses.boss(mob) && !TurnedVillagers.turned(mob)
			&& com.jaymoskingdoms.wild.SpiderKinds.of(mob) != com.jaymoskingdoms.wild.SpiderKinds.Kind.SPIDERLING;
	}

	/** The one roll a monster gets as it rises: by the highest tier of the players near it. */
	private static void roll(ServerLevel level, Mob mob) {
		int tier = mob.getAttachedOrElse(WorldTiers.MOB_TIER, 0);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(mob) <= TIER_NEAR * TIER_NEAR) {
				tier = Math.max(tier, WorldTiers.of(p).ordinal());
			}
		}
		tier = Math.clamp(tier, 0, CHANCE.length - 1);
		RandomSource random = mob.getRandom();
		int bits = 0;
		if (random.nextFloat() < CHANCE[tier]) {
			List<Mark> open = new ArrayList<>(List.of(Mark.values()));
			int n = 1;
			while (n < MOST && random.nextFloat() < MORE[tier]) {
				n++;
			}
			for (int i = 0; i < n; i++) {
				bits |= 1 << open.remove(random.nextInt(open.size())).ordinal();
			}
		}
		mob.setAttached(MARKS, bits);
	}

	private static void forget(UUID id) {
		NEXT_GHAST.remove(id);
		NEXT_NINJA.remove(id);
		NEXT_STORM.remove(id);
		ServerBossEvent bar = BARS.remove(id);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (Mob mob : List.copyOf(LOADED)) {
			if (!mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				LOADED.remove(mob);
				continue;
			}
			step(level, mob, mob.getTarget());
		}
		for (Iterator<Web> it = WEBS.iterator(); it.hasNext(); ) {
			Web web = it.next();
			if (web.level().getGameTime() >= web.until() && Loaded.made(web.level(), web.pos())) {
				if (web.level().getBlockState(web.pos()).is(Blocks.COBWEB)) {
					web.level().removeBlock(web.pos(), false);
				}
				it.remove();
			}
		}
		for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
			Bolt bolt = it.next();
			if (bolt.level().getGameTime() >= bolt.when()) {
				it.remove();
				if (Loaded.made(bolt.level(), BlockPos.containing(bolt.at()))) {
					LightningBolt strike = EntityTypes.LIGHTNING_BOLT.create(bolt.level(), EntitySpawnReason.TRIGGERED);
					if (strike != null) {
						strike.snapTo(bolt.at());
						bolt.level().addFreshEntity(strike);
					}
				}
			} else if (bolt.level().getGameTime() % 5 == 0) {
				// the air there crackles before it comes down
				bolt.level().sendParticles(Mark.STORM.particle(0.9F), bolt.at().x, bolt.at().y + 0.6, bolt.at().z, 3, 0.3, 0.5, 0.3, 0.0);
			}
		}
	}

	/** What a marked one's marks do each tick against its foe {@code target}: its aura and sound, its bar, and the marks that act
	 * on their own. A wild one's from {@link #tick}, a captain's from {@link CaptainWays#step}. */
	static void step(ServerLevel level, Mob mob, @Nullable LivingEntity target) {
		if (Captains.is(mob) && mob.hasAttached(MARKS)) {
			// a captain's body carries his own traits, never a wild roll
			mob.removeAttached(MARKS);
			LOADED.remove(mob);
		}
		Set<Mark> marks = of(mob);
		if (marks.isEmpty()) {
			return;
		}
		RandomSource random = mob.getRandom();
		if (mob.tickCount % AURA_EVERY == 0) {
			for (Mark m : marks) {
				level.sendParticles(m.particle(AURA_SIZE), mob.getRandomX(0.6), mob.getRandomY(), mob.getRandomZ(0.6), 1, 0.0, 0.02, 0.0, 0.0);
			}
		}
		if (random.nextInt(SOUND_EVERY) == 0) {
			Mark m = List.copyOf(marks).get(random.nextInt(marks.size()));
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), m.sound, SoundSource.HOSTILE, SOUND_VOLUME * (m == Mark.STORM ? 0.4F : 1.0F),
				0.8F + random.nextFloat() * 0.4F);
		}
		if (marks.size() >= BAR_AT && mob.tickCount % 5 == 0) {
			ServerBossEvent bar = BARS.computeIfAbsent(mob.getUUID(), id -> new ServerBossEvent(id, mob.getDisplayName(), BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.NOTCHED_6));
			bar.setProgress(mob.getHealth() / mob.getMaxHealth());
			BossBars.show(level, mob, bar);
		}
		if (marks.contains(Mark.REGEN) && mob.tickCount % REGEN_EVERY == 0 && mob.getHealth() < mob.getMaxHealth()) {
			mob.heal(REGEN_HEAL);
		}
		if (target == null || !target.isAlive() || target.level() != level) {
			return;
		}
		long now = level.getGameTime();
		UUID id = mob.getUUID();
		double d = mob.distanceTo(target);
		if (marks.contains(Mark.GHASTLY) && now >= NEXT_GHAST.getOrDefault(id, 0L) && d >= GHAST_MIN && d <= GHAST_MAX && mob.hasLineOfSight(target)) {
			NEXT_GHAST.put(id, now + GHAST_EVERY + random.nextInt(GHAST_EVERY / 2));
			Vec3 to = new Vec3(target.getX() - mob.getX(), target.getY(0.5) - mob.getY(0.5), target.getZ() - mob.getZ());
			SmallFireball ball = new SmallFireball(level, mob, to.normalize());
			ball.setPos(ball.getX(), mob.getY(0.5) + 0.5, ball.getZ());
			level.addFreshEntity(ball);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 0.6F, 1.4F);
		}
		if (marks.contains(Mark.NINJA) && now >= NEXT_NINJA.getOrDefault(id, 0L) && d <= NINJA_RANGE && d > NINJA_BEHIND + 1.0) {
			NEXT_NINJA.put(id, now + NINJA_EVERY + random.nextInt(NINJA_EVERY));
			behind(level, mob, target);
		}
		if (marks.contains(Mark.STORM) && now >= NEXT_STORM.getOrDefault(id, 0L) && d >= STORM_MIN && d <= STORM_RANGE) {
			NEXT_STORM.put(id, now + STORM_EVERY + random.nextInt(STORM_EVERY));
			if (level.canSeeSky(target.blockPosition())) {
				double angle = random.nextDouble() * Math.PI * 2.0, off = STORM_OFF_MIN + random.nextDouble() * (STORM_OFF_MAX - STORM_OFF_MIN);
				BlockPos column = BlockPos.containing(target.getX() + Math.cos(angle) * off, target.getY(), target.getZ() + Math.sin(angle) * off);
				BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, column);
				BOLTS.add(new Bolt(level, Vec3.atBottomCenterOf(ground), now + STORM_WARN));
				level.playSound(null, ground, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.5F, 1.6F);
			}
		}
	}

	/** Ninja: gone in a puff, and there behind its foe, if there's room and ground there. */
	private static void behind(ServerLevel level, Mob mob, LivingEntity target) {
		double yaw = Math.toRadians(target.getYRot());
		Vec3 back = new Vec3(Math.sin(yaw), 0.0, -Math.cos(yaw)).scale(NINJA_BEHIND);
		Vec3 to = target.position().add(back);
		BlockPos feet = BlockPos.containing(to);
		if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
			|| !level.noCollision(mob, mob.getBoundingBox().move(to.subtract(mob.position())))) {
			return;
		}
		ParticleOptions smoke = Mark.NINJA.particle(1.4F);
		level.sendParticles(smoke, mob.getX(), mob.getY(0.5), mob.getZ(), 10, 0.3, 0.5, 0.3, 0.01);
		mob.teleportTo(to.x, to.y, to.z);
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		level.sendParticles(smoke, to.x, to.y + mob.getBbHeight() * 0.5, to.z, 10, 0.3, 0.5, 0.3, 0.01);
		level.playSound(null, to.x, to.y, to.z, Mark.NINJA.sound, SoundSource.HOSTILE, 0.7F, 1.3F);
	}

	// ---- blows -----------------------------------------------------------------------------------------------------------

	private static void damaged(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (taken <= 0.0F || !(victim.level() instanceof ServerLevel level) || source.is(DamageTypes.THORNS)) {
			return;
		}
		RandomSource random = victim.getRandom();
		// a melee blow: the striker's own hand, not a shot or a spell
		if (source.getEntity() instanceof LivingEntity striker && source.getDirectEntity() == striker && striker != victim) {
			Set<Mark> theirs = of(striker);
			if (!theirs.isEmpty()) {
				struck(level, striker, victim, theirs, random);
			}
			if (of(victim).contains(Mark.VENGEANCE) && striker.isAlive() && striker.distanceTo(victim) <= VENGEANCE_REACH) {
				striker.hurtServer(level, level.damageSources().thorns(victim), taken * VENGEANCE_SHARE);
				level.sendParticles(Mark.VENGEANCE.particle(0.9F), striker.getX(), striker.getY(0.6), striker.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
				level.playSound(null, striker.getX(), striker.getY(), striker.getZ(), Mark.VENGEANCE.sound, SoundSource.HOSTILE, 0.8F, 0.8F);
			}
		}
	}

	/** {@code striker}'s marks on the one it hit. */
	private static void struck(ServerLevel level, LivingEntity striker, LivingEntity victim, Set<Mark> marks, RandomSource random) {
		if (marks.contains(Mark.SAPPER) && victim instanceof Player player && !player.getAbilities().invulnerable) {
			player.causeFoodExhaustion(SAPPER_EXHAUSTION);
			level.sendParticles(Mark.SAPPER.particle(1.0F), victim.getX(), victim.getY(0.6), victim.getZ(), 5, 0.3, 0.3, 0.3, 0.01);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, 0.6F, 0.6F);
		}
		if (marks.contains(Mark.WEBBER) && random.nextFloat() < WEB_CHANCE) {
			BlockPos feet = victim.blockPosition();
			if (level.getBlockState(feet).isAir()) {
				level.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
				WEBS.add(new Web(level, feet, level.getGameTime() + WEB_HOLD));
				level.playSound(null, feet, Mark.WEBBER.sound, SoundSource.HOSTILE, 0.9F, 0.9F);
			}
		}
		if (marks.contains(Mark.BLASTOFF) && random.nextFloat() < BLAST_CHANCE) {
			Vec3 v = victim.getDeltaMovement();
			victim.setDeltaMovement(v.x, BLAST_UP, v.z);
			victim.needsSync = true;
			Fx.ring(level, victim.position(), 1.2F, Mark.BLASTOFF.color);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 0.8F, 1.0F);
		}
		if (marks.contains(Mark.STICKY) && random.nextFloat() < STICKY_CHANCE) {
			ItemStack held = victim.getMainHandItem();
			if (!held.isEmpty() && (held.has(DataComponents.WEAPON) || held.isDamageableItem())) {
				victim.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				double angle = random.nextDouble() * Math.PI * 2.0;
				ItemEntity dropped = new ItemEntity(level, victim.getX() + Math.cos(angle) * STICKY_THROW, victim.getY() + 0.3,
					victim.getZ() + Math.sin(angle) * STICKY_THROW, held);
				dropped.setPickUpDelay(STICKY_DELAY);
				level.addFreshEntity(dropped);
				level.sendParticles(Mark.STICKY.particle(0.9F), victim.getX(), victim.getY(0.6), victim.getZ(), 6, 0.3, 0.2, 0.3, 0.01);
				level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), Mark.STICKY.sound, SoundSource.HOSTILE, 1.0F, 0.8F);
			}
		}
		if (marks.contains(Mark.RUST)) {
			boolean wore = false;
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack piece = victim.getItemBySlot(slot);
				if (!piece.isEmpty() && piece.isDamageableItem()) {
					piece.hurtAndBreak(RUST_WEAR, victim, slot);
					wore = true;
				}
			}
			if (wore) {
				level.sendParticles(Mark.RUST.particle(0.8F), victim.getX(), victim.getY(0.5), victim.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
				level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), Mark.RUST.sound, SoundSource.HOSTILE, 0.4F, 1.4F);
			}
		}
	}
}
