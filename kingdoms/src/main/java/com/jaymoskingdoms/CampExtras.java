package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What stands round the illagers' and the orcs' camps beyond their own buildings (Reference Mods patch 10), all raised
 * once, when the camp's ground is loaded and nobody is watching, so old camps get them too:
 * <ul>
 * <li><b>The pillagers' drill yard</b> (V18): a gravel lane with three target dummies (hay and a carved pumpkin on a post)
 * before a backstop, a rail to shoot from, a rack of bolts. Where: on the camp's right just outside its fence ring
 * ({@link #DRILL_OUT} from the fire), the lane running outward. Why: a camp that raids keeps its crossbows sharp where its
 * people can watch, and where the sound carries: two pillagers drill there by day, a bolt every {@link #DRILL_TICKS}
 * ticks, and anyone within {@link #DRILL_HEARD} blocks hears the crossbows long before they see the camp.</li>
 * <li><b>The orcs' pen</b> (V22): a pit sunk two deep with a rim of logs and bars, a ramp out, a few captive villagers held in it
 * and two grunts on its rim who now and then lay into one, to learn how a man moves ({@link #BOUT_TICKS}). Where: on the war
 * camp's left outside its stockade ({@link #PEN_OUT}). Why: the orcs raid for the people they take, and fight-training on
 * them costs the camp nothing. The captives are the camp's usual ones ({@link Captives}): break their chains and they follow you
 * and go home to the town they came from, grateful (Captives, Deeds).</li>
 * <li><b>The claim's skull totems</b> (V23): {@link #TOTEMS} skull-topped totems round the camp at {@link #CLAIM_RADIUS}. Step
 * inside them and the camp's orcs turn on you with a roar; stand outside (within {@link #WATCH_BAND} blocks of them) and
 * they only turn to watch ({@link Orcs#prey} lets you be there).</li>
 * </ul>
 */
final class CampExtras {
	/** The drill yard: out from an illager camp's fire; a bolt this often (ticks), by day; heard this far (blocks); the lane's targets (x of each, row, height). */
	static final int DRILL_OUT = IllagerBuildings.RING_RADIUS + 6;
	static final int DRILL_TICKS = 80;
	static final double DRILL_HEARD = 100.0, DRILL_WATCH = 24.0;
	static final int[] DUMMIES = {2, 5, 8};
	static final int DRILLERS = 2;
	/** The pen: out from an orc camp's fire (left); captives held; grunts at its rim; a bout this often (ticks) for a player this near. */
	static final int PEN_OUT = OrcBuildings.RING_RADIUS + 9, PEN_CAPTIVES = 3, PEN_GUARDS = 2;
	static final int BOUT_TICKS = 120;
	static final double BOUT_HEARD = 64.0;
	/** The claim: radius from the fire, totems round it, how far outside it they watch, and how far an orc is called from the camp. */
	static final int CLAIM_RADIUS = OrcBuildings.RING_RADIUS + 14, TOTEMS = 12, WATCH_BAND = 24;
	static final double ORC_CALL = CLAIM_RADIUS + 10.0, WATCHERS = 36.0;
	/** How often (ticks) camps are looked at. */
	static final int CHECK_TICKS = 20;

	/** "kind:camp id" of what's been raised round it. */
	static final AttachmentType<Map<String, String>> DONE = AttachmentRegistry.create(JaymosKingdoms.id("camp_extras"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	/** Players inside each camp's claim (player to camp ids). */
	private static final Map<UUID, Set<Integer>> INSIDE = new HashMap<>();

	private CampExtras() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CampExtras::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> INSIDE.clear());
	}

	private static boolean done(ServerLevel level, String kind, int camp) {
		return level.getAttachedOrElse(DONE, Map.of()).containsKey(kind + ":" + camp);
	}

	private static void finish(ServerLevel level, String kind, int camp) {
		Map<String, String> next = new HashMap<>(level.getAttachedOrElse(DONE, Map.of()));
		next.put(kind + ":" + camp, "1");
		level.setAttached(DONE, Map.copyOf(next));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CHECK_TICKS != 0 || level.players().isEmpty()) {
			return;
		}
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = camp.fallen() ? null : Camps.data(server, camp.id());
			if (data == null || !Loaded.made(level, data.center())) {
				continue;
			}
			if (IllagerCamps.KIND.equals(data.kind())) {
				drill(level, camp, data, now);
			} else if (OrcCamps.KIND.equals(data.kind())) {
				pen(level, camp, data, now);
				claim(level, camp, data);
			}
		}
	}

	// ---- V18: the drill yard ---------------------------------------------------------------------------------------------

	/** The yard's front middle, and the way its front faces (the lane runs away from it, outward from the camp). */
	private static CampBuildings.Placer yard(ServerLevel level, Camps.CampData data) {
		Direction right = data.gate().getClockWise();
		BlockPos at = data.center().relative(right, DRILL_OUT);
		return new CampBuildings.Placer(at.atY(VillageStyles.groundY(level, at.getX(), at.getZ())), right.getOpposite(), 11);
	}

	/** The drill yard (11 x 7): a gravel lane, a shooting rail and a rack at its front, three dummies before a backstop of dark oak. */
	static Blueprint drillYard() {
		Buildings.Sketch s = new Buildings.Sketch("illager_drill_yard", 11, 7, 6);
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, Blocks.GRAVEL.defaultBlockState());
			}
			s.set(x, 1, 0, x % 2 == 0 ? Blocks.DARK_OAK_FENCE.defaultBlockState() : Blocks.AIR.defaultBlockState());
			for (int y = 1; y <= 4; y++) {
				s.set(x, y, 6, Blocks.DARK_OAK_PLANKS.defaultBlockState());
			}
		}
		for (int x : DUMMIES) {
			s.set(x, 1, 5, Blocks.DARK_OAK_FENCE.defaultBlockState());
			s.set(x, 2, 5, Blocks.HAY_BLOCK.defaultBlockState());
			s.set(x, 3, 5, StyleBuildings.named("carved_pumpkin", Blocks.HAY_BLOCK));
		}
		s.set(0, 1, 2, Blocks.FLETCHING_TABLE.defaultBlockState());
		s.set(10, 1, 2, Blocks.BARREL.defaultBlockState());
		s.set(10, 2, 2, Blocks.LANTERN.defaultBlockState());
		return s.done();
	}

	private static void drill(ServerLevel level, Faction camp, Camps.CampData data, long now) {
		CampBuildings.Placer yard = yard(level, data);
		if (!done(level, "drill", camp.id())) {
			BlockPos far = yard.at(0, 0, 6), near = yard.at(10, 0, 0);
			if (!Loaded.made(level, far, near) || Raids.playerNear(level, yard.anchor(), DRILL_WATCH)) {
				return;
			}
			VillageStyles.stamp(level, drillYard(), yard.anchor(), yard.facing(), Blocks.COBBLESTONE.defaultBlockState());
			for (int i = 0; i < DRILLERS; i++) {
				BlockPos spot = Construction.standable(level, yard.at(3 + i * 4, 1, 2), 3);
				Illagers.spawn(level, camp, Illagers.PILLAGER, spot, spot, 4, false);
			}
			finish(level, "drill", camp.id());
			JaymosKingdoms.LOGGER.info("{} raised a drill yard at {}: its crossbows are kept sharp where the sound carries", camp.name(), yard.anchor());
			return;
		}
		if (now % DRILL_TICKS != 0 || level.isDarkOutside() || !Raids.playerNear(level, yard.anchor(), DRILL_HEARD)) {
			return;
		}
		AABB lane = new AABB(yard.at(0, 0, 0)).minmax(new AABB(yard.at(10, 4, 6))).inflate(2.0);
		for (Mob m : level.getEntitiesOfClass(Mob.class, lane, m -> m.isAlive() && m instanceof AbstractIllager && m.getTarget() == null
			&& m.getMainHandItem().is(Items.CROSSBOW))) {
			RandomSource random = level.getRandom();
			int x = DUMMIES[random.nextInt(DUMMIES.length)];
			Vec3 dummy = Vec3.atCenterOf(yard.at(x, 3, 5));
			ItemStack ammo = new ItemStack(Items.ARROW);
			AbstractArrow arrow = ProjectileUtil.getMobArrow(m, ammo, 1.0F, m.getMainHandItem());
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			m.getLookControl().setLookAt(dummy.x, dummy.y, dummy.z);
			Projectile.spawnProjectileUsingShoot(arrow, level, ammo, dummy.x - m.getX(), dummy.y - arrow.getY(), dummy.z - m.getZ(), 2.4F, 1.5F);
			level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 2.0F, 0.9F + random.nextFloat() * 0.2F);
			return;
		}
	}

	// ---- V22: the orcs' pen ------------------------------------------------------------------------------------------------

	private static CampBuildings.Placer pen(ServerLevel level, Camps.CampData data) {
		Direction left = data.gate().getCounterClockWise();
		BlockPos at = data.center().relative(left, PEN_OUT);
		return new CampBuildings.Placer(at.atY(VillageStyles.groundY(level, at.getX(), at.getZ())), left.getOpposite(), 9);
	}

	/**
	 * The pen (9 x 9, sunk two blocks): a rim of dark oak logs with iron bars on it, the floor of coarse dirt, a ramp of two stairs up
	 * to a gap in the rim's front, bone blocks on the corners.
	 */
	static Blueprint penDesign() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState(), bars = Blocks.IRON_BARS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				boolean rim = x == 0 || x == 8 || z == 0 || z == 8, corner = (x == 0 || x == 8) && (z == 0 || z == 8), gap = x == 4 && z == 0;
				if (rim) {
					t.set(x, 0, z, gap ? Blocks.DIRT_PATH.defaultBlockState() : log);
					if (!gap) {
						t.set(x, 1, z, corner ? Blocks.BONE_BLOCK.defaultBlockState() : bars);
						t.set(x, 2, z, corner ? Blocks.SKELETON_SKULL.defaultBlockState() : bars);
					}
				} else {
					for (int y = 0; y <= 3; y++) {
						t.set(x, y, z, air);
					}
					t.set(x, -1, z, air);
					t.set(x, -2, z, (x + z) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
				}
			}
		}
		t.set(4, -1, 2, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		t.set(4, 0, 1, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		return t.done("orc_captive_pen");
	}

	private static void pen(ServerLevel level, Faction camp, Camps.CampData data, long now) {
		CampBuildings.Placer pen = pen(level, data);
		if (!done(level, "pen", camp.id())) {
			if (!Loaded.made(level, pen.at(0, 0, 0), pen.at(8, 0, 8)) || Raids.playerNear(level, pen.anchor(), DRILL_WATCH)) {
				return;
			}
			VillageStyles.stamp(level, penDesign(), pen.anchor(), pen.facing(), Blocks.COBBLESTONE.defaultBlockState());
			RandomSource random = level.getRandom();
			for (int i = 0; i < PEN_CAPTIVES; i++) {
				Captives.take(level, pen.at(2 + random.nextInt(5), -1, 3 + random.nextInt(4)));
			}
			for (int i = 0; i < PEN_GUARDS; i++) {
				BlockPos rim = pen.at(i == 0 ? 2 : 6, 1, -1);
				OrcCamps.spawn(level, camp.id(), Orcs.GRUNT, Construction.standable(level, rim, 2), rim, 5);
			}
			finish(level, "pen", camp.id());
			JaymosKingdoms.LOGGER.info("{} dug a pen at {}: the orcs learn how a man moves on the captives they take", camp.name(), pen.anchor());
			return;
		}
		if (now % BOUT_TICKS != 0 || !Raids.playerNear(level, pen.anchor(), BOUT_HEARD)) {
			return;
		}
		AABB pit = new AABB(pen.at(0, -2, 0)).minmax(new AABB(pen.at(8, 3, 8))).inflate(1.0);
		Villager captive = level.getEntitiesOfClass(Villager.class, pit, v -> v.isAlive() && Captives.bound(v)).stream().findFirst().orElse(null);
		if (captive == null) {
			return;
		}
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, pit.inflate(5.0), m -> m.isAlive() && m instanceof Worker w && w.role() == Orcs.GRUNT
			&& m.getTarget() == null)) {
			m.getLookControl().setLookAt(captive, 30.0F, 30.0F);
			m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.broadcastEntityEvent(captive, (byte)2);
			level.playSound(null, captive.getX(), captive.getY(), captive.getZ(), SoundEvents.PLAYER_HURT, SoundSource.NEUTRAL, 1.0F, 0.8F);
			return;
		}
	}

	// ---- V23: the claim ------------------------------------------------------------------------------------------------------

	private static void claim(ServerLevel level, Faction camp, Camps.CampData data) {
		BlockPos fire = data.center();
		if (!done(level, "totems", camp.id())) {
			List<BlockPos> spots = new java.util.ArrayList<>();
			for (int i = 0; i < TOTEMS; i++) {
				double a = Math.PI * 2.0 * i / TOTEMS;
				BlockPos column = fire.offset((int)Math.round(Math.cos(a) * CLAIM_RADIUS), 0, (int)Math.round(Math.sin(a) * CLAIM_RADIUS));
				if (!Loaded.made(level, column)) {
					return;
				}
				spots.add(column);
			}
			for (BlockPos column : spots) {
				int y = VillageStyles.groundY(level, column.getX(), column.getZ());
				BlockPos base = column.atY(y + 1);
				level.setBlock(base, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(base.above(), Blocks.DARK_OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(base.above(2), Blocks.BONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(base.above(3), Blocks.SKELETON_SKULL.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			finish(level, "totems", camp.id());
			JaymosKingdoms.LOGGER.info("{} claimed its ground with {} skull totems {} blocks out from its fire", camp.name(), TOTEMS, CLAIM_RADIUS);
		}
		for (ServerPlayer player : level.players()) {
			Set<Integer> in = INSIDE.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
			double d = Math.sqrt(Raids.horizontal(player.blockPosition(), fire));
			if (player.isCreative() || player.isSpectator() || d > CLAIM_RADIUS + WATCH_BAND) {
				in.remove(camp.id());
				continue;
			}
			if (d <= CLAIM_RADIUS) {
				if (in.add(camp.id())) {
					// (crossing the totems: the camp's orcs come)
					level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
					for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, new AABB(fire).inflate(ORC_CALL), m -> m.isAlive() && Orcs.isOrc(m) && m.getTarget() == null)) {
						m.setTarget(player);
					}
				}
			} else {
				in.remove(camp.id());
				// (outside it they only watch)
				for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(WATCHERS), m -> m.isAlive() && Orcs.isOrc(m))) {
					m.getLookControl().setLookAt(player, 30.0F, 30.0F);
					if (m.getTarget() == player) {
						m.setTarget(null);
					}
				}
			}
		}
	}

	/** {@code target} is a player standing in the band just outside {@code camp}'s totems, where its orcs only watch (see {@link Orcs#prey}). */
	static boolean watched(int camp, net.minecraft.world.entity.LivingEntity target) {
		if (!(target instanceof ServerPlayer player) || player.level().getServer() == null) {
			return false;
		}
		Camps.CampData data = Camps.data(player.level().getServer(), camp);
		if (data == null || !OrcCamps.KIND.equals(data.kind())) {
			return false;
		}
		double d = Math.sqrt(Raids.horizontal(player.blockPosition(), data.center()));
		return d > CLAIM_RADIUS && d <= CLAIM_RADIUS + WATCH_BAND;
	}
}
