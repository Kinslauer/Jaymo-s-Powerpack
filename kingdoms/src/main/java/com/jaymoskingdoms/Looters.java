package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.126) Robbing the dead (with Jaymo's Kitchen's corpses). Where bodies lie with something still on them, someone
 * comes for it:
 * <ul>
 * <li><b>Looters.</b> Where three or more bodies lie together (a battlefield, a raid beaten off, a camp put to the sword),
 * with a player near enough to see it, a band of one to three ragged strangers with sacks may creep in, most likely at
 * night. Crouching, they go from body to body, kneel and strip each one of its pockets, armour and weapons, then slip
 * away with their sacks. They're cowards: one who's struck, or spotted by a guard or a golem, runs for it (the guards
 * after him). A player close by sends them off too, unless the player is sneaking (and can catch them at it) or has been
 * robbing the dead themselves ("plenty for both of us"). Cut one down and his sack spills; if he'd robbed a settlement's
 * dead, it thanks you ({@link #REP_STOPPED}).</li>
 * <li><b>The poor.</b> A settlement's jobless (and nitwit) villagers, now and then, creep out to a body lying in its land
 * that nobody's seen to, rob it and take it home. Seen by a guard (or a golem), they're shamed into dropping it all; hit
 * by a player while at it, they drop it and beg, and the settlement thanks the player ({@link #REP_CAUGHT}).</li>
 * <li><b>Players.</b> Taking anything off one of a settlement's dead by hand (a villager, its folk) inside its land is
 * grave robbing ({@link Theft.Crime#ROBBING}), a crime if anyone sees it. The enemy's dead, monsters and the wilds are
 * fair game.</li>
 * </ul>
 * A player's body is never robbed by anyone but players (Kitchen keeps its hotbar for its owner).
 */
public final class Looters {
	/** The looter's role (see {@link Folk#defineRole}). */
	static final int LOOTER = 80;

	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** How often a battlefield is looked for (ticks); round each player, this far; this many bodies within {@link #FIELD_SPREAD} of each other make one. */
	static final int FIELD_CHECK = 200;
	static final double FIELD_SIGHT = 48.0, FIELD_SPREAD = 16.0;
	static final int FIELD_BODIES = 3;
	/** The chance each check, by night and by day, that looters come to a battlefield. */
	static final float NIGHT_CHANCE = 0.15F, DAY_CHANCE = 0.04F;
	/** A band: one looter, another at this many bodies, a third at this many. */
	static final int SECOND_AT = 6, THIRD_AT = 10;
	/** No second band within this far of a band at work; a field looted is left alone this long after (ticks). */
	static final double BAND_SPACE = 64.0;
	static final long FIELD_COOLDOWN = 6000L;
	/** They come in from this far off, never closer than this to a player. */
	static final int SPAWN_MIN = 20, SPAWN_MAX = 32;
	static final double PLAYER_SPACE = 14.0;
	/** A looter: health, speed (attribute), creeping and running speeds (navigation). */
	static final double HEALTH = 16.0, SPEED = 0.3, CREEP = 0.55, RUN = 1.35;
	/** How far round the field a looter looks for bodies; close enough to rob one; robbing one takes this long (ticks). */
	static final double LOOT_REACH = 24.0, REACH = 1.8;
	static final int ROB_TICKS = 60, RUMMAGE_EVERY = 10;
	/** He's off after this many bodies; he gives up and goes after this long (ticks). */
	static final int MOST_BODIES = 5;
	static final long WORK_TICKS = 2400L;
	/** He's gone once this far from the field with no player watching within {@link #UNSEEN}; or after this long leaving, whatever. */
	static final double GONE = 40.0, UNSEEN = 24.0;
	static final long LEAVE_TICKS = 1200L, RUN_TICKS = 200L;
	/** He sees a player this close (one who isn't sneaking) and runs. Guards and golems spot him this far. */
	static final double SPOOK = 8.0, SPOTTED = 16.0;
	/** A player who robbed a body this recently (ticks) is one of them. */
	static final long KINDRED_TICKS = 1200L;
	/** The looter's rags (leather dyed this colour). */
	static final int RAGS = 0x4A3B2A;

	/** The poor: how often a settlement's poor are tempted (ticks), the chance then; how far from its seat a body may lie. */
	static final int POOR_CHECK = 400;
	static final float POOR_CHANCE = 0.08F;
	static final double POOR_SWEEP = 64.0, POOR_REACH = 40.0;
	/** A poor villager's walk (its brain's scale); robbing takes this long (ticks); they give up after this long. */
	static final float POOR_SPEED = 0.5F;
	static final int POOR_ROB_TICKS = 80;
	static final long POOR_GIVE_UP = 1800L;

	/** Reputation for cutting down a looter who robbed a settlement's dead; for catching one of its poor at it. */
	static final int REP_STOPPED = 6, REP_CAUGHT = 3;
	/** Lines a looter, a poor villager, a guard who catches one, may say. */
	static final int LINES = 3;
	static final double SAY_RANGE = 16.0;

	enum Stage { CREEP, ROB, LEAVE, RUN }

	private static final class Plot {
		final UUID who;
		final BlockPos field;
		final Vec3 from;
		final boolean poor;
		final List<ItemStack> sack = new ArrayList<>();
		/** Settlements whose dead he robbed. */
		final Set<Integer> robbedFrom = new HashSet<>();
		Stage stage = Stage.CREEP;
		@Nullable UUID body;
		int robbing, robbed;
		long stageAt;
		final long until;
		/** Players who've already been thanked for catching him. */
		boolean caught;

		Plot(UUID who, BlockPos field, Vec3 from, boolean poor, long now, long until) {
			this.who = who;
			this.field = field;
			this.from = from;
			this.poor = poor;
			this.stageAt = now;
			this.until = until;
		}
	}

	/** Looter's (or poor villager's) UUID → his plot. */
	private static final Map<UUID, Plot> PLOTS = new HashMap<>();
	/** Fields looted lately, and till when they're left alone. */
	private static final Map<BlockPos, Long> LOOTED = new HashMap<>();
	/** Players who've lately robbed the dead (by hand), and when. */
	private static final Map<UUID, Long> KINDRED = new HashMap<>();
	/** Which players have already been charged with robbing which bodies (one crime a body). */
	private static final Map<UUID, Set<UUID>> CHARGED = new HashMap<>();

	private Looters() {
	}

	static void register() {
		Folk.defineRole(new Folk.Role(LOOTER, "looter", false, false, null, (folk, faction) -> dress(folk)));
		ServerTickEvents.END_SERVER_TICK.register(Looters::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PLOTS.clear();
			LOOTED.clear();
			KINDRED.clear();
			CHARGED.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				died(level, entity, source.getEntity());
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (entity.level() instanceof ServerLevel level && PLOTS.get(entity.getUUID()) instanceof Plot p) {
				struck(level, entity, p, source.getEntity());
			}
		});
		KitchenCompat.onRobbed((player, corpse) -> {
			if (player instanceof ServerPlayer sp && corpse.level() instanceof ServerLevel level) {
				playerRobbed(level, sp, corpse);
			}
		});
	}

	static boolean is(Entity e) {
		return e instanceof Folk f && f.role() == LOOTER;
	}

	/** {@code corpse} is some robber's mark just now (so a bearer leaves it a moment). */
	static boolean claimed(UUID corpse) {
		for (Plot p : PLOTS.values()) {
			if (corpse.equals(p.body)) {
				return true;
			}
		}
		return false;
	}

	/** {@code mob} is out robbing the dead. */
	static boolean busy(Entity mob) {
		return PLOTS.containsKey(mob.getUUID());
	}

	/** A body with something on it worth taking, lying free and not a player's. */
	private static boolean robbable(Entity e) {
		return KitchenCompat.isFreeCorpse(e) && !KitchenCompat.isPlayerCorpse(e) && KitchenCompat.hasValuables(e) && !Bearers.claimed(e.getUUID())
			&& !claimed(e.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (!KitchenCompat.burials()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 5 == 0 && !PLOTS.isEmpty()) {
			for (Iterator<Plot> it = PLOTS.values().iterator(); it.hasNext(); ) {
				Plot p = it.next();
				if (!step(level, p, now)) {
					it.remove();
					if (level.getEntity(p.who) instanceof LivingEntity who && who.isAlive()) {
						if (who.getPose() == Pose.CROUCHING) {
							who.setPose(Pose.STANDING);
						}
						if (p.poor) {
							// home with it: what he took is his now
							p.sack.clear();
						}
					}
				}
			}
		}
		if (now % FIELD_CHECK == 0) {
			LOOTED.values().removeIf(until -> until < now);
			KINDRED.values().removeIf(at -> now - at > KINDRED_TICKS);
			strays(level, now);
			fields(level, now);
		}
		if (now % POOR_CHECK == 0) {
			for (Faction f : Factions.all(server)) {
				if (!f.fallen() && f.type() != FactionType.BARBARIAN && level.isLoaded(f.seat()) && level.getRandom().nextFloat() < POOR_CHANCE) {
					tempt(level, f, now);
				}
			}
		}
	}

	// ---- looters --------------------------------------------------------------------------------------------------------

	/** Looters left over (after a restart) with no plot: off they go. */
	private static void strays(ServerLevel level, long now) {
		for (Entity e : level.getAllEntities()) {
			if (is(e) && e.isAlive() && !PLOTS.containsKey(e.getUUID())) {
				Plot p = new Plot(e.getUUID(), e.blockPosition(), e.position(), false, now, now + LEAVE_TICKS);
				p.stage = Stage.LEAVE;
				PLOTS.put(e.getUUID(), p);
			}
		}
	}

	/** Looks round each player for a battlefield nobody's looting, and maybe sends a band to it. */
	private static void fields(ServerLevel level, long now) {
		RandomSource random = level.getRandom();
		boolean night = !Royals.daytime(level);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			List<Entity> bodies = level.getEntities((Entity)null, player.getBoundingBox().inflate(FIELD_SIGHT, 16.0, FIELD_SIGHT), Looters::robbable);
			if (bodies.size() < FIELD_BODIES) {
				continue;
			}
			// the body with the most others round it is the field's heart
			Entity heart = null;
			int most = 0;
			for (Entity b : bodies) {
				int n = 0;
				for (Entity o : bodies) {
					if (o.distanceToSqr(b) <= FIELD_SPREAD * FIELD_SPREAD) {
						n++;
					}
				}
				if (n > most) {
					most = n;
					heart = b;
				}
			}
			if (heart == null || most < FIELD_BODIES || random.nextFloat() >= (night ? NIGHT_CHANCE : DAY_CHANCE)) {
				continue;
			}
			BlockPos field = heart.blockPosition();
			if (bandNear(level, field) || lootedNear(field)) {
				continue;
			}
			int band = 1 + (most >= SECOND_AT ? 1 : 0) + (most >= THIRD_AT ? 1 : 0);
			int came = 0;
			for (int i = 0; i < band; i++) {
				if (send(level, field, now, random)) {
					came++;
				}
			}
			if (came > 0) {
				LOOTED.put(field, now + FIELD_COOLDOWN);
				JaymosKingdoms.LOGGER.info("{} looter(s) creep in to rob the {} dead at {}", came, most, field);
			}
		}
	}

	private static boolean bandNear(ServerLevel level, BlockPos field) {
		for (Plot p : PLOTS.values()) {
			if (!p.poor && p.field.distSqr(field) < BAND_SPACE * BAND_SPACE) {
				return true;
			}
		}
		return false;
	}

	private static boolean lootedNear(BlockPos field) {
		for (BlockPos f : LOOTED.keySet()) {
			if (f.distSqr(field) < BAND_SPACE * BAND_SPACE) {
				return true;
			}
		}
		return false;
	}

	/** One looter comes in toward {@code field}; false if there was nowhere for him to come from. */
	private static boolean send(ServerLevel level, BlockPos field, long now, RandomSource random) {
		BlockPos spot = approach(level, field, random);
		MinecraftServer server = level.getServer();
		Faction dress = Factions.realmNear(server, field, 100000);
		if (dress == null && !Factions.all(server).isEmpty()) {
			dress = Factions.all(server).get(0);
		}
		if (spot == null || dress == null) {
			return false;
		}
		Folk l = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (l == null) {
			return false;
		}
		l.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		l.setup(LOOTER, dress, field, (int)LOOT_REACH * 2);
		l.setFactionId(-1);
		l.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
		l.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPEED);
		l.setHealth((float)HEALTH);
		dress(l);
		level.addFreshEntity(l);
		PLOTS.put(l.getUUID(), new Plot(l.getUUID(), field, l.position(), false, now, now + WORK_TICKS));
		return true;
	}

	/** Where he comes in from: on the ground {@link #SPAWN_MIN}-{@link #SPAWN_MAX} blocks off, no player close. */
	private static @Nullable BlockPos approach(ServerLevel level, BlockPos field, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			BlockPos near = BlockPos.containing(field.getX() + Math.cos(angle) * r, field.getY(), field.getZ() + Math.sin(angle) * r);
			if (!level.isPositionEntityTicking(near)) {
				continue;
			}
			BlockPos spot = Construction.standable(level, near, 3);
			if (!level.getBlockState(spot.below()).isSolid() || Math.abs(spot.getY() - field.getY()) > 8) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(spot);
			if (level.getEntitiesOfClass(ServerPlayer.class, new AABB(spot).inflate(PLAYER_SPACE), p -> p.position().distanceTo(at) < PLAYER_SPACE).isEmpty()) {
				return spot;
			}
		}
		return null;
	}

	/** Rags and a sack: a dyed leather cap and tunic, a bundle at his side, a looter's name. */
	static void dress(Folk l) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			l.setItemSlot(slot, ItemStack.EMPTY);
			l.setDropChance(slot, 0.0F);
		}
		ItemStack cap = new ItemStack(Items.LEATHER_HELMET), tunic = new ItemStack(Items.LEATHER_CHESTPLATE);
		cap.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new DyedItemColor(RAGS));
		tunic.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new DyedItemColor(RAGS));
		l.setItemSlot(EquipmentSlot.HEAD, cap);
		l.setItemSlot(EquipmentSlot.CHEST, tunic);
		l.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BUNDLE));
		l.setCustomName(Component.translatable("entity.jaymoskingdoms.looter"));
	}

	/** Walks one robber on; false when he's done (gone, home, or dead). */
	private static boolean step(ServerLevel level, Plot p, long now) {
		if (!(level.getEntity(p.who) instanceof PathfinderMob who) || !who.isAlive()) {
			return false;
		}
		if (p.poor) {
			return stepPoor(level, p, (Villager)who, now);
		}
		if (p.stage == Stage.CREEP || p.stage == Stage.ROB) {
			LivingEntity threat = threat(level, who);
			if (threat != null) {
				run(level, p, who, threat, now);
				return true;
			}
			if (now > p.until || p.robbed >= MOST_BODIES) {
				leave(p, who, now);
			}
		}
		switch (p.stage) {
			case CREEP -> {
				Entity body = p.body == null ? null : level.getEntity(p.body);
				if (body == null || !KitchenCompat.isFreeCorpse(body) || !KitchenCompat.hasValuables(body) || Bearers.claimed(body.getUUID())) {
					p.body = null;
					body = next(level, p, who);
					if (body == null) {
						leave(p, who, now);
						return true;
					}
					p.body = body.getUUID();
				}
				who.setPose(Pose.CROUCHING);
				if (who.distanceToSqr(body) > REACH * REACH) {
					if (who.getNavigation().isDone() || now % 20 == 0) {
						who.getNavigation().moveTo(body.getX(), body.getY(), body.getZ(), CREEP);
					}
					return true;
				}
				who.getNavigation().stop();
				p.stage = Stage.ROB;
				p.robbing = 0;
			}
			case ROB -> {
				Entity body = p.body == null ? null : level.getEntity(p.body);
				if (body == null || !KitchenCompat.isFreeCorpse(body) || who.distanceToSqr(body) > (REACH + 1.0) * (REACH + 1.0)) {
					p.stage = Stage.CREEP;
					return true;
				}
				who.getNavigation().stop();
				who.setPose(Pose.CROUCHING);
				who.getLookControl().setLookAt(body);
				p.robbing += 5;
				if (p.robbing % RUMMAGE_EVERY < 5) {
					who.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					level.playSound(null, body.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.HOSTILE, 0.4F,
						0.8F + level.getRandom().nextFloat() * 0.4F);
				}
				if (p.robbing >= ROB_TICKS) {
					take(level, p, who, body);
					p.body = null;
					p.stage = Stage.CREEP;
				}
			}
			case LEAVE, RUN -> {
				who.setPose(Pose.STANDING);
				if (p.stage == Stage.RUN && now - p.stageAt > RUN_TICKS) {
					leave(p, who, now);
				}
				Vec3 away = who.position().subtract(Vec3.atCenterOf(p.field)).multiply(1.0, 0.0, 1.0);
				if (away.lengthSqr() < 1.0E-4) {
					away = p.from.subtract(Vec3.atCenterOf(p.field)).multiply(1.0, 0.0, 1.0);
				}
				away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
				boolean far = who.distanceToSqr(Vec3.atCenterOf(p.field)) > GONE * GONE;
				if (far && !watched(level, who) || p.stage == Stage.LEAVE && now - p.stageAt > LEAVE_TICKS && !watched(level, who)) {
					// slipped away with his sack
					who.discard();
					return false;
				}
				if (who.getNavigation().isDone() || now % 20 == 0) {
					Vec3 to = who.position().add(away.scale(16.0));
					who.getNavigation().moveTo(to.x, to.y, to.z, p.stage == Stage.RUN ? RUN : 1.0);
				}
			}
		}
		return true;
	}

	/** The nearest body round the field with something still on it. */
	private static @Nullable Entity next(ServerLevel level, Plot p, PathfinderMob who) {
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : level.getEntities((Entity)null, new AABB(p.field).inflate(LOOT_REACH, 8.0, LOOT_REACH), Looters::robbable)) {
			double d = e.distanceToSqr(who);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Strips {@code body} into the sack; a settlement's dead remembered. */
	private static void take(ServerLevel level, Plot p, PathfinderMob who, Entity body) {
		List<ItemStack> taken = KitchenCompat.robBy(body, who);
		if (taken.isEmpty()) {
			return;
		}
		p.sack.addAll(taken);
		p.robbed++;
		Faction law = Theft.lawAt(level, body.blockPosition());
		if (law != null) {
			p.robbedFrom.add(law.id());
		}
	}

	/** A guard or golem who sees him, a player near and upright, or whoever just struck him; null if he's unseen. */
	private static @Nullable LivingEntity threat(ServerLevel level, PathfinderMob who) {
		if (who.getLastHurtByMob() != null && who.tickCount - who.getLastHurtByMobTimestamp() < 100) {
			return who.getLastHurtByMob();
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, who.getBoundingBox().inflate(SPOTTED), LivingEntity::isAlive)) {
			if (e == who || !e.hasLineOfSight(who)) {
				continue;
			}
			if (e instanceof Player player) {
				if (!player.isSpectator() && !player.isCreative() && !player.isShiftKeyDown() && player.distanceTo(who) < SPOOK
					&& !KINDRED.containsKey(player.getUUID())) {
					return player;
				}
				continue;
			}
			if (guard(e)) {
				return e;
			}
		}
		return null;
	}

	/** One who keeps the law: a realm's or village's fighting folk, or an iron golem a village made. */
	private static boolean guard(LivingEntity e) {
		if (e instanceof Folk f) {
			Faction faction = f.level().getServer() == null ? null : Factions.get(f.level().getServer(), f.factionId());
			return f.combatant() && !is(f) && !Assassins.is(f) && faction != null && !faction.fallen() && faction.type() != FactionType.BARBARIAN;
		}
		return e instanceof IronGolem g && !g.isPlayerCreated();
	}

	/** He's seen: he bolts, crying out, and a guard who saw him gives chase. */
	private static void run(ServerLevel level, Plot p, PathfinderMob who, LivingEntity threat, long now) {
		p.stage = Stage.RUN;
		p.stageAt = now;
		p.body = null;
		who.setPose(Pose.STANDING);
		if (threat instanceof Player player && KINDRED.containsKey(player.getUUID())) {
			return;
		}
		Speech.say(level, who, Component.translatable("message.jaymoskingdoms.looter.flee." + level.getRandom().nextInt(LINES)), Speech.YELLOW, SAY_RANGE);
		if (guard(threat) && threat instanceof PathfinderMob g) {
			Speech.say(level, g, Component.translatable("message.jaymoskingdoms.looter.spotted." + level.getRandom().nextInt(LINES)), Speech.RED, SAY_RANGE);
			g.setTarget(who);
		}
	}

	private static void leave(Plot p, PathfinderMob who, long now) {
		if (p.stage != Stage.LEAVE && p.stage != Stage.RUN) {
			p.stage = Stage.LEAVE;
			p.stageAt = now;
			p.body = null;
		}
	}

	/** A player within {@link #UNSEEN} who can see him. */
	private static boolean watched(ServerLevel level, Entity who) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(who) < UNSEEN * UNSEEN && p.hasLineOfSight(who)) {
				return true;
			}
		}
		return false;
	}

	/** Struck: a looter runs; a poor villager drops it all and begs. */
	private static void struck(ServerLevel level, LivingEntity who, Plot p, @Nullable Entity by) {
		long now = level.getGameTime();
		if (!p.poor) {
			if (who instanceof PathfinderMob mob && p.stage != Stage.RUN && by instanceof LivingEntity attacker) {
				run(level, p, mob, attacker, now);
			}
			return;
		}
		if (p.sack.isEmpty() && p.stage != Stage.ROB) {
			return;
		}
		spill(level, who, p);
		Speech.say(level, who, Component.translatable("message.jaymoskingdoms.looter.beg." + level.getRandom().nextInt(LINES)), Speech.YELLOW, SAY_RANGE);
		if (by instanceof ServerPlayer player && !p.caught) {
			p.caught = true;
			Faction f = Theft.lawAt(level, who.blockPosition());
			if (f != null) {
				Reputation.change(player, f, REP_CAUGHT);
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.looter.caught", f.name()).withStyle(ChatFormatting.GREEN));
			}
		}
		// shamed: off home with nothing
		p.stage = Stage.LEAVE;
		p.stageAt = now;
		p.body = null;
	}

	/** Dead: his sack spills where he fell; whoever cut him down is thanked by the settlements he robbed. */
	private static void died(ServerLevel level, LivingEntity entity, @Nullable Entity killer) {
		Plot p = PLOTS.remove(entity.getUUID());
		if (p == null) {
			return;
		}
		spill(level, entity, p);
		if (!p.poor && killer instanceof ServerPlayer player) {
			Faction here = Theft.lawAt(level, entity.blockPosition());
			Set<Integer> thankful = new HashSet<>(p.robbedFrom);
			if (here != null && p.robbed > 0) {
				thankful.add(here.id());
			}
			for (int id : thankful) {
				Faction f = Factions.get(level.getServer(), id);
				if (f != null && !f.fallen()) {
					Reputation.change(player, f, REP_STOPPED);
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.looter.stopped", f.name()).withStyle(ChatFormatting.GREEN));
				}
			}
		}
	}

	private static void spill(ServerLevel level, LivingEntity who, Plot p) {
		for (ItemStack stack : p.sack) {
			ItemEntity item = new ItemEntity(level, who.getX(), who.getY() + 0.5, who.getZ(), stack.copy());
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
		if (!p.sack.isEmpty()) {
			level.sendParticles(ParticleTypes.POOF, who.getX(), who.getY() + 0.6, who.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
			level.playSound(null, who.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
		p.sack.clear();
	}

	// ---- the poor --------------------------------------------------------------------------------------------------------

	/** One of {@code f}'s poor creeps out to a body lying in its land, if there's one nobody's seen to. */
	private static void tempt(ServerLevel level, Faction f, long now) {
		List<Entity> bodies = level.getEntities((Entity)null, new AABB(f.seat()).inflate(POOR_SWEEP, 24.0, POOR_SWEEP), Looters::robbable);
		if (bodies.isEmpty()) {
			return;
		}
		Entity body = bodies.get(level.getRandom().nextInt(bodies.size()));
		Faction law = Theft.lawAt(level, body.blockPosition());
		if (law == null || law.id() != f.id() || !Bearers.calm(level, body.blockPosition())) {
			return;
		}
		Villager best = null;
		double bestD = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, body.getBoundingBox().inflate(POOR_REACH, 12.0, POOR_REACH),
			v -> v.isAlive() && !v.isBaby() && !v.isSleeping())) {
			boolean poor = v.getVillagerData().profession().is(VillagerProfession.NONE) || v.getVillagerData().profession().is(VillagerProfession.NITWIT);
			Faction theirs = Theft.lawAt(level, v.blockPosition());
			if (!poor || theirs == null || theirs.id() != f.id() || !Bearers.freeVillager(v)) {
				continue;
			}
			double d = v.distanceToSqr(body);
			if (d < bestD) {
				bestD = d;
				best = v;
			}
		}
		if (best == null) {
			return;
		}
		Plot p = new Plot(best.getUUID(), body.blockPosition(), best.position(), true, now, now + POOR_GIVE_UP);
		p.body = body.getUUID();
		PLOTS.put(best.getUUID(), p);
		JaymosKingdoms.LOGGER.debug("{} of {} creeps out to rob the dead at {}", best.getName().getString(), f.name(), body.blockPosition());
	}

	/** A poor villager: to the body, rob it, and home; shamed if a guard sees. */
	private static boolean stepPoor(ServerLevel level, Plot p, Villager v, long now) {
		if (v.isSleeping() || McaCompat.panicking(v) || now > p.until) {
			return false;
		}
		switch (p.stage) {
			case CREEP, ROB -> {
				Entity body = p.body == null ? null : level.getEntity(p.body);
				if (body == null || !KitchenCompat.isFreeCorpse(body) || !KitchenCompat.hasValuables(body)) {
					return false;
				}
				if (v.distanceToSqr(body) > REACH * REACH) {
					p.stage = Stage.CREEP;
					if (v.distanceToSqr(body) < 8.0 * 8.0) {
						v.setPose(Pose.CROUCHING);
					}
					Undertakers.walk(v, body.blockPosition());
					return true;
				}
				p.stage = Stage.ROB;
				v.setPose(Pose.CROUCHING);
				v.getNavigation().stop();
				v.getLookControl().setLookAt(body);
				p.robbing += 5;
				LivingEntity seen = null;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(SPOTTED), LivingEntity::isAlive)) {
					if (guard(e) && e.hasLineOfSight(v)) {
						seen = e;
						break;
					}
				}
				if (seen != null) {
					// caught at it: a guard's rebuke, and it all goes back on the ground
					Speech.say(level, seen, Component.translatable("message.jaymoskingdoms.looter.shame." + level.getRandom().nextInt(LINES)), Speech.RED, SAY_RANGE);
					spill(level, v, p);
					v.setPose(Pose.STANDING);
					return false;
				}
				if (p.robbing % RUMMAGE_EVERY < 5) {
					level.playSound(null, body.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.3F, 1.0F);
				}
				if (p.robbing >= POOR_ROB_TICKS) {
					p.sack.addAll(KitchenCompat.robBy(body, v));
					p.body = null;
					p.stage = Stage.LEAVE;
					p.stageAt = now;
					v.setPose(Pose.STANDING);
					Speech.say(level, v, Component.translatable("message.jaymoskingdoms.looter.poor." + level.getRandom().nextInt(LINES)), Speech.WHITE, 8.0);
				}
			}
			default -> {
				v.setPose(Pose.STANDING);
				// home, if he has one; else just away from the body
				java.util.Optional<net.minecraft.core.GlobalPos> home = McaCompat.home(v);
				BlockPos to = home.map(net.minecraft.core.GlobalPos::pos).orElse(null);
				if (to == null || now - p.stageAt > 600L || v.distanceToSqr(Vec3.atCenterOf(to)) < 4.0 * 4.0) {
					return false;
				}
				Undertakers.walk(v, to);
			}
		}
		return true;
	}

	// ---- players --------------------------------------------------------------------------------------------------------

	/**
	 * {@code player} took something off {@code corpse} by hand. They're one of the looters' kind a while; and if it was
	 * one of a settlement's dead in its own land, it's grave robbing (once a body).
	 */
	static void playerRobbed(ServerLevel level, ServerPlayer player, Entity corpse) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		KINDRED.put(player.getUUID(), level.getGameTime());
		if (level != Factions.level(level.getServer())) {
			return;
		}
		Faction f = Theft.lawAt(level, corpse.blockPosition());
		if (f == null || !Bearers.who(level, corpse).ours(f)) {
			return;
		}
		Set<UUID> charged = CHARGED.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
		if (!charged.add(corpse.getUUID())) {
			return;
		}
		if (charged.size() > 256) {
			charged.clear();
			charged.add(corpse.getUUID());
		}
		Theft.commit(player, level, f, Theft.Crime.ROBBING, corpse.blockPosition(), null, false);
	}
}
