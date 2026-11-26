package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Ship;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * People who fish (Andy, 2026-11-26: "all npcs should have to fight fish and be seen doing so as well"). Whoever fishes, a
 * villager at MCA's fishing chore, a ship's hand at her rail or a pillager over the side of a long ship, does it the way a
 * player does, where you can see it: they cast (a float flies out on a line from the rod's tip, {@link AnglerFloat}), wait
 * with it bobbing, and when a fish takes it they fight it. The fish is a real one, one of the fish swimming near the float
 * if there is one (so a water fished hard runs short, and comes back as fish swim in), else the one the catch says: it
 * thrashes and runs at the surface at the line's end, and the line brings it in toward them; a run that comes while they
 * strain may tear it free, and then it swims off. Landed, it's swung up out of the water to them, and it's theirs (what
 * they do with it is up to who sent them: {@code Provisions}, MCA's chore). Their arms move through it all
 * ({@link #pose}: cast, wait, fight, land, the client's {@code MobAnimations}). Not saved: a line out when the world
 * stops is simply reeled in.
 */
public final class Anglers {
	/** How long a cast takes, and the swing up of a landed fish (ticks): the length of their animations. */
	static final int CAST_TICKS = 20, LAND_TICKS = 15;
	/** Waiting for a bite (ticks): at least this, plus up to this more; a fisherman's bites come this much sooner. */
	static final int BITE_LEAST = 160, BITE_MORE = 640;
	static final float FISHERMAN_BITE = 0.6F;
	/** How long the float dips before the fish is on (ticks). */
	static final int NIBBLE = 12;
	/** The fight: how long each kind takes to land at the least (ticks), and its spread. */
	static final int FIGHT_COD = 90, FIGHT_SALMON = 150, FIGHT_SMALL = 55, FIGHT_SPREAD = 40;
	/** A run starts with this chance a tick, lasts this long (ticks, plus up to as much more), and takes back this much line a tick. */
	static final float RUN_CHANCE = 0.035F, RUN_LINE = 0.006F;
	static final int RUN_LEAST = 12;
	/** A run tears the hook free with this chance (a fisherman's this share of it). */
	static final float ESCAPE = 0.1F, FISHERMAN_ESCAPE = 0.4F;
	/** A leaping fish (a salmon) leaps out of a run with this chance. */
	static final float LEAP = 0.3F;
	/** How far a run swings the fish aside (blocks), how fast it's steered (share of the gap, at most blocks a tick). */
	static final double SWING_MOST = 3.0, STEER = 0.2, STEER_MOST = 0.3;
	/** The fish is never drawn nearer than this to the angler (blocks), and comes in this share of the way by the end. */
	static final double NEAREST = 1.8, DRAWN_IN = 0.8;
	/** A fish near the float takes it (blocks); one is made only if none is. */
	static final double TAKER_NEAR = 10.0;
	/** A line goes slack and comes in if the angler is this far from the float (blocks). */
	static final double LINE_MOST = 28.0;

	public enum Stage {
		NONE, CAST, WAIT, FIGHT, LAND
	}

	/** On the angler: their stage (its ordinal; none when they aren't fishing), sent to everyone for their pose. Not saved. */
	public static final AttachmentType<Integer> STAGE = AttachmentRegistry.create(JaymosKingdoms.id("angling"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On the angler: what was in their hand before the rod (kept, so it's given back even after a restart). */
	static final AttachmentType<ItemStack> STOWED = AttachmentRegistry.create(JaymosKingdoms.id("angler_stowed"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC));

	/** Where the float lies: a fixed spot, or (cast from a ship's rail) a spot in her frame, carried with her. */
	private record Spot(Vec3 world, @Nullable Ship ship, Vec3 local) {
		Vec3 now(ServerLevel level) {
			if (this.ship == null || this.ship.isRemoved()) {
				return this.world;
			}
			Vec3 at = this.ship.toWorld(this.local.x, 0.0, this.local.z);
			com.jaymoskingdoms.sailing.Hull h = this.ship.hull();
			double y = surface(level, at.x, this.ship.getY() + (h == null ? 6.0 : h.minY + h.draft + 2.0), at.z);
			return new Vec3(at.x, Double.isNaN(y) ? this.world.y : y, at.z);
		}
	}

	private static final class Line {
		final Mob angler;
		final Spot spot;
		final Consumer<ItemStack> landed;
		final boolean ownRod;
		Stage stage = Stage.CAST;
		long until;
		@Nullable AnglerFloat bob;
		@Nullable LivingEntity fish;
		boolean madeFish;
		ItemStack catchItem = ItemStack.EMPTY;
		float progress;
		int fightTicks;
		int run;
		long runUntil;
		double swing;
		Vec3 bit = Vec3.ZERO;
		double startDist;
		Vec3 landFrom = Vec3.ZERO;

		Line(Mob angler, Spot spot, Consumer<ItemStack> landed, boolean ownRod) {
			this.angler = angler;
			this.spot = spot;
			this.landed = landed;
			this.ownRod = ownRod;
		}
	}

	private static final Map<UUID, Line> LINES = new HashMap<>();

	private Anglers() {
	}

	static void register() {
		AnglerFloat.register();
		ServerTickEvents.END_SERVER_TICK.register(Anglers::tick);
		// a rod left in a hand by a stopped world goes back to what was there
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(STOWED) && !LINES.containsKey(mob.getUUID())) {
				giveBack(mob);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Line line : new ArrayList<>(LINES.values())) {
				end(line, false);
			}
			LINES.clear();
		});
	}

	/** Whether {@code e} has a line out. */
	public static boolean angling(Entity e) {
		return e.level().isClientSide() ? e.getAttachedOrElse(STAGE, 0) != 0 : LINES.containsKey(e.getUUID());
	}

	/** Whether {@code e} is fighting a fish (or swinging one up), not just waiting for a bite. */
	public static boolean fighting(Entity e) {
		Line line = LINES.get(e.getUUID());
		return line != null && (line.stage == Stage.FIGHT || line.stage == Stage.LAND);
	}

	/** The animation {@code e}'s arms play while it fishes (Player Animation Library's id), or null. Client and server. */
	public static @Nullable String pose(Entity e) {
		int stage = e.getAttachedOrElse(STAGE, 0);
		return switch (stage) {
			case 1 -> "jaymoskingdoms:angler_cast";
			case 2 -> "jaymoskingdoms:angler_wait";
			case 3 -> "jaymoskingdoms:angler_fight";
			case 4 -> "jaymoskingdoms:angler_land";
			default -> null;
		};
	}

	/**
	 * {@code angler} casts at {@code spot} (on the water), a rod in hand (theirs, or one put there for it and taken back
	 * after), and fishes till a fish is landed or lost. {@code landed} gets the catch. False if they can't (fishing already,
	 * or no water there).
	 */
	public static boolean cast(ServerLevel level, Mob angler, Vec3 spot, Consumer<ItemStack> landed) {
		return cast(level, angler, new Spot(spot, null, Vec3.ZERO), landed);
	}

	/** As {@link #cast(ServerLevel, Mob, Vec3, Consumer)}, from {@code ship}'s rail: the float at {@code local} in her frame, trailing with her. */
	public static boolean castFrom(ServerLevel level, Mob angler, Ship ship, Vec3 local, Consumer<ItemStack> landed) {
		Spot spot = new Spot(Vec3.ZERO, ship, local);
		Vec3 at = spot.now(level);
		return cast(level, angler, new Spot(at, ship, local), landed);
	}

	private static boolean cast(ServerLevel level, Mob angler, Spot spot, Consumer<ItemStack> landed) {
		if (LINES.containsKey(angler.getUUID()) || !level.getFluidState(BlockPos.containing(spot.world().x, spot.world().y - 0.2, spot.world().z)).is(FluidTags.WATER)) {
			return false;
		}
		boolean own = angler.getMainHandItem().is(Items.FISHING_ROD);
		if (!own) {
			angler.setAttached(STOWED, angler.getMainHandItem().copy());
			angler.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
		}
		Line line = new Line(angler, spot, landed, own);
		line.until = level.getGameTime() + CAST_TICKS;
		LINES.put(angler.getUUID(), line);
		stage(line, Stage.CAST);
		angler.getLookControl().setLookAt(spot.world().x, spot.world().y, spot.world().z);
		level.playSound(null, angler.getX(), angler.getY(), angler.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F,
			0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
		return true;
	}

	/**
	 * (MCA's fishing chore, its villager's float taken: {@code McaFishingMixin}) A fish is on already, at {@code at}: {@code angler}
	 * fights it from there, the catch ({@code catchItem}, MCA's) going to {@code landed}. False if it can't be fought (no
	 * fish in it, no water), and MCA brings it in its own way.
	 */
	public static boolean hooked(ServerLevel level, Mob angler, Vec3 at, ItemStack catchItem, Consumer<ItemStack> landed) {
		if (LINES.containsKey(angler.getUUID()) || kind(catchItem) == null) {
			return false;
		}
		BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
		if (!level.getFluidState(below).is(FluidTags.WATER) && !level.getFluidState(below.above()).is(FluidTags.WATER)) {
			return false;
		}
		double y = surface(level, at.x, at.y + 1.0, at.z);
		Line line = new Line(angler, new Spot(new Vec3(at.x, Double.isNaN(y) ? at.y : y, at.z), null, Vec3.ZERO), landed,
			angler.getMainHandItem().is(Items.FISHING_ROD));
		line.catchItem = catchItem.copy();
		LINES.put(angler.getUUID(), line);
		line.bob = AnglerFloat.at(level, angler, line.spot.world());
		if (!strike(level, line)) {
			LINES.remove(angler.getUUID());
			if (line.bob != null) {
				line.bob.discard();
			}
			return false;
		}
		return true;
	}

	/** {@code angler}'s line comes in, whatever's on it (a hooked fish swims off). */
	public static void reelIn(Mob angler) {
		Line line = LINES.remove(angler.getUUID());
		if (line != null) {
			end(line, false);
		}
	}

	// ---- each tick ----

	private static void tick(MinecraftServer server) {
		if (LINES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Line>> it = LINES.entrySet().iterator(); it.hasNext();) {
			Line line = it.next().getValue();
			if (!(line.angler.level() instanceof ServerLevel level) || !line.angler.isAlive() || line.angler.isRemoved() || Downed.isDowned(line.angler)
				|| line.angler.getTarget() != null || !line.angler.getMainHandItem().is(Items.FISHING_ROD)) {
				it.remove();
				end(line, false);
				continue;
			}
			if (!step(level, line)) {
				it.remove();
			}
		}
	}

	/** One tick of {@code line}; false once it's done with (reeled in and ended). */
	private static boolean step(ServerLevel level, Line line) {
		long now = level.getGameTime();
		Mob angler = line.angler;
		RandomSource random = level.getRandom();
		Vec3 spot = line.spot.now(level);
		if (line.stage != Stage.LAND && (line.bob != null ? line.bob.position() : spot).distanceTo(angler.position()) > LINE_MOST) {
			end(line, false);
			return false;
		}
		switch (line.stage) {
			case CAST -> {
				if (now >= line.until) {
					// the float lands with a plop, and they wait
					line.bob = AnglerFloat.at(level, angler, spot);
					level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.2F);
					level.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y, spot.z, 6, 0.1, 0.0, 0.1, 0.05);
					int wait = BITE_LEAST + random.nextInt(BITE_MORE);
					if (fisherman(angler)) {
						wait = Math.round(wait * FISHERMAN_BITE);
					}
					if (level.isRainingAt(BlockPos.containing(spot))) {
						wait = wait * 3 / 4;
					}
					line.until = now + wait;
					stage(line, Stage.WAIT);
				}
			}
			case WAIT -> {
				AnglerFloat bob = line.bob;
				if (bob == null || bob.isRemoved()) {
					end(line, false);
					return false;
				}
				long left = line.until - now;
				// bobbing on the swell; the nibble pulls it under
				double dip = left <= NIBBLE ? -0.25 - 0.1 * Mth.sin(now * 1.3F) : 0.04 * Mth.sin(now * 0.15F);
				bob.setPos(spot.x, spot.y - 0.1 + dip, spot.z);
				angler.getLookControl().setLookAt(bob.getX(), bob.getY(), bob.getZ());
				if (left == NIBBLE) {
					level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.25F,
						1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
					level.sendParticles(ParticleTypes.BUBBLE, spot.x, spot.y - 0.2, spot.z, 6, 0.1, 0.0, 0.1, 0.2);
					level.sendParticles(ParticleTypes.FISHING, spot.x, spot.y, spot.z, 6, 0.1, 0.0, 0.1, 0.2);
				}
				if (left <= 0 && !strike(level, line)) {
					end(line, false);
					return false;
				}
			}
			case FIGHT -> {
				if (!fight(level, line, now)) {
					return false;
				}
			}
			case LAND -> {
				LivingEntity fish = line.fish;
				float t = 1.0F - (line.until - now) / (float)LAND_TICKS;
				Vec3 hand = angler.position().add(0.0, angler.getBbHeight() * 0.7, 0.0);
				if (fish != null && fish.isAlive()) {
					// swung up out of the water, over the rail or the bank, to their hand
					Vec3 at = line.landFrom.lerp(hand, t).add(0.0, Math.sin(t * Math.PI) * 1.5, 0.0);
					fish.setPos(at.x, at.y, at.z);
					fish.setDeltaMovement(Vec3.ZERO);
					if (line.bob != null) {
						line.bob.setPos(at.x, at.y + fish.getBbHeight() * 0.5, at.z);
					}
				}
				if (now >= line.until) {
					ItemStack caught = line.catchItem.isEmpty() && fish != null ? catchOf(fish) : line.catchItem;
					if (fish != null) {
						fish.discard();
					}
					line.fish = null;
					level.playSound(null, angler.getX(), angler.getY(), angler.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.6F, 1.0F);
					end(line, false);
					if (!caught.isEmpty()) {
						line.landed.accept(caught);
					}
					return false;
				}
			}
			default -> {
			}
		}
		return true;
	}

	/** The fish takes the float: one of those near it, or (none) the catch's own kind, hooked, and the fight begins. */
	private static boolean strike(ServerLevel level, Line line) {
		Vec3 at = line.bob != null ? line.bob.position() : line.spot.now(level);
		if (line.catchItem.isEmpty()) {
			line.catchItem = roll(level, line.angler, at);
		}
		EntityType<? extends AbstractFish> kind = kind(line.catchItem);
		LivingEntity fish = null;
		if (kind != null) {
			// one of the fish about the float, the catch's kind first
			AbstractFish best = null;
			double bestD = Double.MAX_VALUE;
			for (AbstractFish f : level.getEntitiesOfClass(AbstractFish.class, new AABB(at, at).inflate(TAKER_NEAR), f -> f.isAlive() && !f.isNoAi()
				&& !hookedFish(f))) {
				double d = f.distanceToSqr(at) * (f.getType() == kind ? 1.0 : 4.0);
				if (d < bestD) {
					best = f;
					bestD = d;
				}
			}
			if (best != null) {
				fish = best;
				line.catchItem = catchOf(best);
			} else {
				AbstractFish made = kind.create(level, EntitySpawnReason.EVENT);
				if (made != null) {
					made.snapTo(at.x, at.y - 0.4, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
					level.addFreshEntity(made);
					fish = made;
					line.madeFish = true;
				}
			}
		}
		if (fish == null) {
			// junk or treasure on the line: no fight in it, it simply comes up
			line.landFrom = at;
			line.until = level.getGameTime() + LAND_TICKS;
			stage(line, Stage.LAND);
			return true;
		}
		if (fish instanceof Mob mob) {
			mob.setNoAi(true);
		}
		line.fish = fish;
		line.bit = fish.position();
		line.startDist = Math.max(NEAREST + 1.0, Math.hypot(fish.getX() - line.angler.getX(), fish.getZ() - line.angler.getZ()));
		int least = fightLeast(line.catchItem);
		line.fightTicks = least + level.getRandom().nextInt(FIGHT_SPREAD);
		line.progress = 0.0F;
		if (line.bob != null) {
			line.bob.setTaut(true);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.2F);
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 14, 0.3, 0.1, 0.3, 0.2);
		stage(line, Stage.FIGHT);
		return true;
	}

	/** One tick of the fight; false once it's over and done with (the fish lost). */
	private static boolean fight(ServerLevel level, Line line, long now) {
		LivingEntity fish = line.fish;
		Mob angler = line.angler;
		RandomSource random = level.getRandom();
		if (fish == null || !fish.isAlive() || fish.isRemoved()) {
			end(line, false);
			return false;
		}
		// the fish runs now and then, taking line; between runs the line comes in
		if (line.run == 0 && random.nextFloat() < RUN_CHANCE) {
			line.run = random.nextBoolean() ? 1 : -1;
			line.runUntil = now + RUN_LEAST + random.nextInt(RUN_LEAST * 2);
			float escape = ESCAPE * (fisherman(angler) ? FISHERMAN_ESCAPE : 1.0F);
			if (line.progress > 0.15F && random.nextFloat() < escape) {
				// torn free: it's away
				level.playSound(null, fish.getX(), fish.getY(), fish.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.6F, 1.4F);
				level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + fish.getBbHeight(), fish.getZ(), 10, 0.3, 0.1, 0.3, 0.2);
				end(line, false);
				return false;
			}
			if (line.catchItem.is(Items.SALMON) && random.nextFloat() < LEAP) {
				// a salmon leaps clear
				fish.setPos(fish.getX(), fish.getY() + 0.3, fish.getZ());
				level.playSound(null, fish.getX(), fish.getY(), fish.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.3F);
				level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.5, fish.getZ(), 16, 0.3, 0.3, 0.3, 0.2);
			}
		}
		if (line.run != 0 && now >= line.runUntil) {
			line.run = 0;
		}
		line.progress = Mth.clamp(line.progress + (line.run != 0 ? -RUN_LINE : 1.0F / line.fightTicks), 0.0F, 1.0F);
		// where it's held: on the line from them to where it bit, in as the line comes in, swung out on a run
		double ox = line.bit.x - angler.getX(), oz = line.bit.z - angler.getZ();
		double len = Math.max(1.0E-3, Math.hypot(ox, oz));
		double want = Math.max(NEAREST, line.startDist * (1.0 - DRAWN_IN * line.progress));
		double cx = angler.getX() + ox / len * want, cz = angler.getZ() + oz / len * want;
		line.swing = line.run != 0 ? Mth.clamp(line.swing + 0.15 * line.run, -SWING_MOST, SWING_MOST) : line.swing * 0.95;
		double tx = cx - oz / len * line.swing, tz = cz + ox / len * line.swing;
		double surface = surface(level, tx, fish.getY() + 2.0, tz);
		if (Double.isNaN(surface)) {
			// (no water there: it stays where it is in it)
			tx = fish.getX();
			tz = fish.getZ();
			surface = surface(level, tx, fish.getY() + 2.0, tz);
			line.swing *= 0.5;
		}
		boolean thrash = line.run != 0 || now % 23 < 5;
		double ty = Double.isNaN(surface) ? fish.getY() : thrash ? surface - fish.getBbHeight() * 0.5 : surface - fish.getBbHeight() - 0.05;
		double dx = Mth.clamp((tx - fish.getX()) * STEER, -STEER_MOST, STEER_MOST);
		double dz = Mth.clamp((tz - fish.getZ()) * STEER, -STEER_MOST, STEER_MOST);
		double dy = Mth.clamp((ty - fish.getY()) * 0.3, -STEER_MOST, STEER_MOST);
		// (on a line from a ship under way, it's towed along with her too)
		Vec3 carry = line.spot.ship() != null && !line.spot.ship().isRemoved() ? line.spot.ship().drift() : Vec3.ZERO;
		dx += carry.x;
		dz += carry.z;
		fish.setPos(fish.getX() + dx, fish.getY() + dy, fish.getZ() + dz);
		fish.setDeltaMovement(dx, dy, dz);
		// it faces away from them and pulls
		double fx = fish.getX() - angler.getX() - oz / len * line.run * 2.0, fz = fish.getZ() - angler.getZ() + ox / len * line.run * 2.0;
		float yaw = (float)(Mth.atan2(fz, fx) * Mth.RAD_TO_DEG) - 90.0F;
		float turned = Mth.approachDegrees(fish.getYRot(), yaw, 25.0F);
		fish.setYRot(turned);
		fish.setYBodyRot(turned);
		fish.setYHeadRot(turned);
		if (line.bob != null) {
			// the hook rides in its mouth
			Vec3 look = Vec3.directionFromRotation(0.0F, turned);
			line.bob.setPos(fish.getX() - look.x * fish.getBbWidth() * 0.5, fish.getY() + fish.getBbHeight() * 0.5, fish.getZ() - look.z * fish.getBbWidth() * 0.5);
		}
		angler.getLookControl().setLookAt(fish, 30.0F, 30.0F);
		if (!Double.isNaN(surface) && now % 2 == 0) {
			double w = fish.getBbWidth() * 0.5;
			if (thrash) {
				level.sendParticles(ParticleTypes.SPLASH, fish.getX(), surface, fish.getZ(), 6, w + 0.2, 0.1, w + 0.2, 0.15);
			} else {
				level.sendParticles(ParticleTypes.FISHING, fish.getX(), surface, fish.getZ(), 1, w, 0.0, w, 0.02);
			}
			if (thrash && now % 10 == 0) {
				level.playSound(null, fish.getX(), surface, fish.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.4F + random.nextFloat() * 0.3F);
			}
		}
		if (line.progress >= 1.0F) {
			// in: up and out of the water
			line.landFrom = fish.position();
			line.until = now + LAND_TICKS;
			stage(line, Stage.LAND);
		}
		return true;
	}

	/** The line's done with: the float gone, a fish still on it let go (one that was made for it, a free fish now), the rod back. */
	private static void end(Line line, boolean keep) {
		LivingEntity fish = line.fish;
		if (fish instanceof Mob mob && fish.isAlive() && !fish.isRemoved()) {
			mob.setNoAi(false);
			fish.setDeltaMovement(fish.getDeltaMovement().add(0.0, -0.05, 0.0));
		}
		line.fish = null;
		if (line.bob != null) {
			line.bob.discard();
			line.bob = null;
		}
		line.angler.removeAttached(STAGE);
		if (!keep && !line.ownRod) {
			giveBack(line.angler);
		}
	}

	private static void giveBack(Mob mob) {
		ItemStack had = mob.getAttached(STOWED);
		mob.removeAttached(STOWED);
		if (mob.getMainHandItem().is(Items.FISHING_ROD)) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, had == null ? ItemStack.EMPTY : had);
		}
	}

	private static void stage(Line line, Stage stage) {
		line.stage = stage;
		line.angler.setAttached(STAGE, stage.ordinal());
	}

	// ---- the fish ----

	/** Whether a fish is on someone's line already. */
	private static boolean hookedFish(LivingEntity fish) {
		for (Line l : LINES.values()) {
			if (l.fish == fish) {
				return true;
			}
		}
		return false;
	}

	/** What the water gives up to {@code angler} at {@code at}: vanilla's fish table, as a rod there would. */
	private static ItemStack roll(ServerLevel level, Mob angler, Vec3 at) {
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, at)
			.withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD)).withParameter(LootContextParams.THIS_ENTITY, angler)
			.create(LootContextParamSets.FISHING);
		List<ItemStack> items = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING_FISH).getRandomItems(params);
		return items.isEmpty() ? new ItemStack(Items.COD) : items.get(0);
	}

	/** The fish a catch is (null: not one: junk, treasure, or a fish we have no body for). */
	static @Nullable EntityType<? extends AbstractFish> kind(ItemStack stack) {
		if (stack.is(Items.COD)) {
			return net.minecraft.world.entity.EntityTypes.COD;
		}
		if (stack.is(Items.SALMON)) {
			return net.minecraft.world.entity.EntityTypes.SALMON;
		}
		if (stack.is(Items.TROPICAL_FISH)) {
			return net.minecraft.world.entity.EntityTypes.TROPICAL_FISH;
		}
		if (stack.is(Items.PUFFERFISH)) {
			return net.minecraft.world.entity.EntityTypes.PUFFERFISH;
		}
		return null;
	}

	/** What a landed fish is in hand. */
	private static ItemStack catchOf(LivingEntity fish) {
		EntityType<?> type = fish.getType();
		return new ItemStack(type == net.minecraft.world.entity.EntityTypes.SALMON ? Items.SALMON : type == net.minecraft.world.entity.EntityTypes.TROPICAL_FISH ? Items.TROPICAL_FISH
			: type == net.minecraft.world.entity.EntityTypes.PUFFERFISH ? Items.PUFFERFISH : Items.COD);
	}

	private static int fightLeast(ItemStack stack) {
		return stack.is(Items.SALMON) ? FIGHT_SALMON : stack.is(Items.COD) ? FIGHT_COD : FIGHT_SMALL;
	}

	/** A fisherman by trade (bites come sooner, fish get away less). */
	static boolean fisherman(Mob mob) {
		return mob instanceof Villager v && v.getVillagerData().profession().is(VillagerProfession.FISHERMAN);
	}

	/** The top of the water at (x, z), looking down from {@code fromY} a few blocks, or up from it if that's under water (NaN: none there). */
	static double surface(ServerLevel level, double x, double fromY, double z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(fromY), Mth.floor(z));
		for (int i = 0; i < 12 && Loaded.made(level, p) && level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p.above()).is(FluidTags.WATER); i++) {
			p.move(net.minecraft.core.Direction.UP);
		}
		for (int i = 0; i < 10; i++, p.move(net.minecraft.core.Direction.DOWN)) {
			if (!Loaded.made(level, p)) {
				return Double.NaN;
			}
			var fluid = level.getFluidState(p);
			if (fluid.is(FluidTags.WATER)) {
				return p.getY() + fluid.getHeight(level, p);
			}
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return Double.NaN;
			}
		}
		return Double.NaN;
	}
}
