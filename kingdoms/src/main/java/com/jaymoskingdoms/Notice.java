package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.Creatures;
import com.jaymoskingdoms.creatures.Piercer;
import com.jaymoskingdoms.monsters.Mimic;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Noticing without looking (fifth edition's passive Perception, Andy's pick A5), and how fast you travel changing it
 * (pick F1). Every second a player's eyes and ears are at work by themselves: a tripwire across the path, a chest that
 * isn't a chest, something hunting them from where they aren't looking, a foe hidden by magic. Noticed, it gives itself
 * away to that player alone: a soft sound from where it is and a glint there. Nothing is written; a low score walks
 * into it.
 * <p>
 * The chance comes from Wits and from how far up the Stealth tree they are (a thief knows a thief's tricks); sneaking along
 * is better, and running flat out or riding hard is much worse. Noticed here: tripwires and a warren's covered pits; a lever,
 * button or pressure plate sitting in the gloom away from any settlement; a chest that isn't one; a thing hanging from the
 * ceiling; whoever is after them from where they aren't looking (archers in the brush from farther off); and a crook's hand
 * coming for their purse. Other systems add more ({@link #add}): quicksand, ground magic, a hidden vessel.
 */
public final class Notice {
	/** The chance a second of noticing a thing: with no Wits, and what the top of Wits adds. */
	static final float LEAST = 0.08F, WITS = 0.5F;
	/** What the top of the Stealth tree adds. */
	static final float STEALTH = 0.25F;
	/** Sneaking adds this; sprinting or riding fast keeps this share of it. */
	static final float SNEAKING = 0.15F, RUSHING = 0.3F;
	/** A mount moving this fast (blocks a tick) is riding hard. */
	static final double HARD_RIDE = 0.25;
	/** How near (blocks) a tripwire, a sleeping mimic, and a hunter may be. */
	static final int WIRE = 5;
	static final double MIMIC = 8.0, HUNTER = 14.0;
	/** How near one after them with a bow, crossbow or gun may be; a thing hanging overhead; a crook after their purse. */
	static final double ARCHER = 26.0, OVERHEAD = 9.0, PICKER = 6.0;
	/** A lever, button or plate is hidden when the light on it is this low or under (0-15). */
	static final int HIDDEN_LIGHT = 7;
	/** A hunter outside this half-angle of the player's look (as a cosine) is one they're not looking at. */
	static final double UNSEEN_COS = 0.5;
	/** A thing once noticed isn't given away again for this long (ticks). */
	static final int AGAIN = 20 * 120;

	/** One thing to be noticed: a key that stays the same while it is there, and where it is. */
	public record Spot(long key, Vec3 at, SoundEvent sound) {
	}

	/** What else a player might notice about them. */
	@FunctionalInterface
	public interface Finder {
		void find(ServerPlayer player, List<Spot> into);
	}

	private static final List<Finder> FINDERS = new ArrayList<>();
	/** When each player last noticed each thing (game time). */
	private static final Map<UUID, Map<Long, Long>> SEEN = new HashMap<>();

	private Notice() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Notice::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SEEN.clear());
		add(Notice::builtIn);
	}

	/** Adds more that can be noticed. */
	public static void add(Finder finder) {
		FINDERS.add(finder);
	}

	/** {@code player}'s chance (0-1) a second of noticing a thing. */
	public static float chance(ServerPlayer player) {
		Skills.Progress skills = Skills.of(player);
		float c = LEAST + WITS * Saves.share(player, Stats.Stat.WITS)
			+ STEALTH * skills.level(Skills.Pillar.STEALTH) / (float) Skills.Pillar.STEALTH.maxLevel
			+ FeatsBody.noticing(player) + Paths.noticing(player);
		if (player.isShiftKeyDown()) {
			c += SNEAKING;
		} else if (Pace.slow(player)) {
			c += Pace.NOTICING;
		}
		if (player.isSprinting() || player.getVehicle() != null && player.getVehicle().getDeltaMovement().horizontalDistance() > HARD_RIDE) {
			c *= RUSHING;
		}
		return Math.min(0.95F, c);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((player.tickCount + player.getId()) % 20 != 0 || !player.isAlive() || player.isSpectator() || PowersCompat.helpless(player) || Deafened.is(player)) {
				continue;
			}
			List<Spot> spots = new ArrayList<>();
			for (Finder f : FINDERS) {
				f.find(player, spots);
			}
			if (spots.isEmpty()) {
				continue;
			}
			long now = player.level().getGameTime();
			Map<Long, Long> seen = SEEN.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
			seen.values().removeIf(t -> now - t > AGAIN);
			float chance = chance(player);
			for (Spot spot : spots) {
				if (seen.containsKey(spot.key()) || player.getRandom().nextFloat() >= chance) {
					continue;
				}
				seen.put(spot.key(), now);
				giveAway(player, spot);
			}
		}
	}

	/** The thing gives itself away to {@code player} alone. */
	private static void giveAway(ServerPlayer player, Spot spot) {
		Vec3 at = spot.at();
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(spot.sound()), SoundSource.AMBIENT,
			at.x, at.y, at.z, 0.6F, 1.2F + player.getRandom().nextFloat() * 0.2F, player.getRandom().nextLong()));
		player.level().sendParticles(player, ParticleTypes.WAX_OFF, true, false, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.0);
	}

	/**
	 * Tripwires and covered pits, hidden levers, sleeping mimics, things hanging overhead, whatever hunts them from where they
	 * aren't looking or can't be seen, and a crook's hand.
	 */
	private static void builtIn(ServerPlayer player, List<Spot> into) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		// (in a settlement a lever is only a lever; out in a ruin or a cave, in the gloom, it is somebody's secret)
		boolean wild = Theft.lawAt(level, at) == null;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-WIRE, -2, -WIRE), at.offset(WIRE, 2, WIRE))) {
			BlockState state = level.getBlockState(p);
			if (state.isAir()) {
				continue;
			}
			if (state.is(Blocks.TRIPWIRE)) {
				into.add(new Spot(p.asLong(), Vec3.atCenterOf(p), SoundEvents.TRIPWIRE_ATTACH));
			} else if (state.is(Creatures.TRAP_COVER)) {
				into.add(new Spot(p.asLong() ^ 0xC0FEL << 40, Vec3.atCenterOf(p).add(0, 0.4, 0), SoundEvents.GRASS_BREAK));
			} else if (wild && (state.is(Blocks.LEVER) || state.is(Blocks.TRIPWIRE_HOOK) || state.is(BlockTags.BUTTONS) || state.is(BlockTags.PRESSURE_PLATES))
				&& level.getMaxLocalRawBrightness(p) <= HIDDEN_LIGHT) {
				into.add(new Spot(p.asLong() ^ 0x1E7EL << 40, Vec3.atCenterOf(p), state.is(BlockTags.PRESSURE_PLATES) ? SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON : SoundEvents.LEVER_CLICK));
			}
		}
		for (Piercer piercer : level.getEntitiesOfClass(Piercer.class, player.getBoundingBox().inflate(OVERHEAD), Piercer::hanging)) {
			into.add(new Spot(~(long) piercer.getId() ^ 0xCE11L << 40, piercer.position(), SoundEvents.DRIPSTONE_BLOCK_STEP));
		}
		Villager picker = Crooks.picking(player, PICKER);
		if (picker != null) {
			into.add(new Spot(~(long) picker.getId() ^ 0x9A5EL << 40, picker.position().add(0, picker.getBbHeight() * 0.55, 0), SoundEvents.BUNDLE_REMOVE_ONE));
		}
		for (Mimic mimic : level.getEntitiesOfClass(Mimic.class, player.getBoundingBox().inflate(MIMIC), Mimic::shut)) {
			into.add(new Spot(~(long) mimic.getId(), mimic.position().add(0, 0.6, 0), SoundEvents.CHEST_LOCKED));
		}
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		// (monsters, and anyone else after them: a bandit band's archers in the brush are heard from farther off)
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ARCHER), m -> m.isAlive() && m.getTarget() == player)) {
			if (mob.distanceToSqr(player) > HUNTER * HUNTER && !(mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem)
				&& !(mob instanceof Folk folk && Muskets.armed(folk))) {
				continue;
			}
			Vec3 to = mob.getEyePosition().subtract(eye).normalize();
			if (mob.hasEffect(MobEffects.INVISIBILITY) || to.dot(look) < UNSEEN_COS) {
				into.add(new Spot(~(long) mob.getId() ^ 0x5EE5L << 40, mob.position().add(0, mob.getBbHeight() * 0.5, 0), SoundEvents.GRASS_STEP));
			}
		}
	}
}
