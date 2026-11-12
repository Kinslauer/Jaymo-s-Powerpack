package com.jaymoskingdoms.sailing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rivet (the Xenoform's machine form, in Powers) flows into a cannon the way he climbs into a golem: his B on a gun, set
 * on land or aboard a ship, melds him into its breech. He lays it where he looks (a quarter turn either side of the way
 * it's mounted, as high or low as a gunner lays it), Space touches it off, and his metal arms load it again from what he
 * carries: one gunpowder and one shot for every round, nothing for free. Sneak, or B again, pours him back out.
 *
 * <p>Powers reaches this by reflection ({@code KingdomsCompat}); a gunner it stops calling for a few ticks (he changed
 * back, logged off) lets go.
 */
public final class MeldedGuns {
	/** How far either side of her mounting a melded gun traverses (degrees). */
	static final double TRAVERSE = 45.0;
	/** Ticks to load her again after a shot (his arms are quick, but she's a gun). */
	static final int RELOAD_TICKS = 30;
	/** How far from the breech he may be shoved before he's pulled back to it, and how far before he lets go. */
	static final double HOLD = 0.75, BREAK = 4.0;
	/** A gunner Powers hasn't ticked for this long has let go. */
	static final int STALE = 5;

	private static final Map<UUID, Manned> MANNED = new HashMap<>();

	private static final class Manned {
		/** Her ship, or null for a gun set on land at {@link #pos}. */
		final @Nullable UUID ship;
		final long key;
		final @Nullable BlockPos pos;
		int reload;
		boolean jumped = true;
		long seen;
		int told;

		Manned(@Nullable UUID ship, long key, @Nullable BlockPos pos, long now) {
			this.ship = ship;
			this.key = key;
			this.pos = pos;
			this.seen = now;
		}
	}

	private MeldedGuns() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.overworld().getGameTime();
			for (Iterator<Map.Entry<UUID, Manned>> it = MANNED.entrySet().iterator(); it.hasNext();) {
				if (now - it.next().getValue().seen > STALE) {
					it.remove();
				}
			}
		});
	}

	/** (Powers) Whether {@code player} is melded into a gun. */
	public static boolean manning(ServerPlayer player) {
		return MANNED.containsKey(player.getUUID());
	}

	/** (Powers) He pours out of the gun he's melded into. */
	public static void leave(ServerPlayer player) {
		if (MANNED.remove(player.getUUID()) != null) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8F, 1.2F);
		}
	}

	/** (Powers) Melds {@code player} into the gun he looks at within {@code reach}, on land or aboard. False if there's none. */
	public static boolean man(ServerPlayer player, double reach) {
		ServerLevel level = player.level();
		long now = level.getServer().overworld().getGameTime();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		Manned m = null;
		double best = Double.MAX_VALUE;
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).getBlock() instanceof ShipCannonBlock) {
			m = new Manned(null, 0L, hit.getBlockPos().immutable(), now);
			best = hit.getLocation().distanceTo(eye);
		}
		for (Ship ship : level.getEntitiesOfClass(Ship.class, player.getBoundingBox().inflate(reach + 32.0))) {
			for (long key : ship.guns()) {
				Vec3 at = ship.gunAt(key);
				Vec3 to = at.subtract(eye);
				double along = to.dot(player.getLookAngle());
				if (along < 0.0 || along > reach + 0.5 || along >= best) {
					continue;
				}
				if (to.subtract(player.getLookAngle().scale(along)).length() < 0.8) {
					m = new Manned(ship.getUUID(), key, null, now);
					best = along;
				}
			}
		}
		if (m == null) {
			return false;
		}
		MANNED.put(player.getUUID(), m);
		Vec3 station = station(level, m);
		if (station != null) {
			pull(player, station);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, station.x, station.y + 0.8, station.z, 20, 0.4, 0.4, 0.4, 0.1);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COPPER_GOLEM_SPAWN, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}

	/**
	 * (Powers, every tick he's melded) Holds him at the breech, lays the gun where he looks, fires it on Space and loads it
	 * again from his pack. He lets go if the gun is gone, he sneaks, or he's knocked well clear.
	 */
	public static void tick(ServerPlayer player) {
		Manned m = MANNED.get(player.getUUID());
		if (m == null) {
			return;
		}
		ServerLevel level = player.level();
		m.seen = level.getServer().overworld().getGameTime();
		Input input = player.getLastClientInput();
		Vec3 station = station(level, m);
		if (station == null || input.shift() || !player.isAlive() || player.position().distanceTo(station) > BREAK) {
			leave(player);
			return;
		}
		if (player.position().distanceTo(station) > HOLD) {
			pull(player, station);
		}
		double[] way = way(level, m);
		if (way == null) {
			leave(player);
			return;
		}
		Vec3 dir = laid(way, player);
		if (m.reload > 0) {
			m.reload--;
		}
		boolean loaded = loaded(level, m);
		if (!loaded && m.reload == 0) {
			ShotKind kind = reload(player);
			if (kind != null) {
				load(level, m, kind);
				loaded = true;
				level.playSound(null, station.x, station.y, station.z, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.8F, 0.7F);
			}
		}
		boolean jump = input.jump();
		if (jump && !m.jumped && loaded && m.reload == 0 && fire(level, m, dir, player)) {
			m.reload = RELOAD_TICKS;
			loaded = false;
		}
		m.jumped = jump;
		if (m.told-- <= 0) {
			m.told = 10;
			ShotKind kind = kind(level, m);
			Component state = kind != null ? Component.translatable(kind.item().getDescriptionId())
				: Component.translatable(m.reload > 0 ? "ship.jaymoskingdoms.cannon.melded.loading" : "ship.jaymoskingdoms.cannon.melded.empty");
			player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.cannon.melded", state));
		}
	}

	/** Where he stands to lay her: behind the breech, feet on her deck (or the ground), or null if she's gone. */
	private static @Nullable Vec3 station(ServerLevel level, Manned m) {
		double[] way = way(level, m);
		if (way == null) {
			return null;
		}
		Vec3 centre = centre(level, m);
		return centre == null ? null : new Vec3(centre.x - way[0], centre.y - 0.5, centre.z - way[1]);
	}

	private static @Nullable Ship ship(ServerLevel level, Manned m) {
		return m.ship != null && level.getEntity(m.ship) instanceof Ship s && s.isAlive() && s.hasGun(m.key) ? s : null;
	}

	private static @Nullable Vec3 centre(ServerLevel level, Manned m) {
		if (m.pos != null) {
			return level.getBlockState(m.pos).getBlock() instanceof ShipCannonBlock ? Vec3.atCenterOf(m.pos) : null;
		}
		Ship s = ship(level, m);
		return s == null ? null : s.gunAt(m.key);
	}

	/** The way she's mounted (x, z in the world), or null if she's gone. */
	private static double @Nullable [] way(ServerLevel level, Manned m) {
		if (m.pos != null) {
			BlockState state = level.getBlockState(m.pos);
			if (!(state.getBlock() instanceof ShipCannonBlock)) {
				return null;
			}
			Direction f = state.getValue(ShipCannonBlock.FACING);
			return new double[] {f.getStepX(), f.getStepZ()};
		}
		Ship s = ship(level, m);
		return s == null ? null : s.gunWay(m.key);
	}

	/** Where he lays her: his look, turned no further than {@link #TRAVERSE} from her mounting, raised as he looks. */
	private static Vec3 laid(double[] way, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		double mount = Math.atan2(way[1], way[0]);
		double turn = Mth.wrapDegrees(Math.toDegrees(Math.atan2(look.z, look.x) - mount));
		double a = mount + Math.toRadians(Mth.clamp(turn, -TRAVERSE, TRAVERSE));
		return ShipCannonBlock.aim(Math.cos(a), Math.sin(a), player);
	}

	private static boolean loaded(ServerLevel level, Manned m) {
		if (m.pos != null) {
			BlockState state = level.getBlockState(m.pos);
			return state.getBlock() instanceof ShipCannonBlock && state.getValue(ShipCannonBlock.LOAD).loaded();
		}
		Ship s = ship(level, m);
		return s != null && s.gunLoaded(m.key);
	}

	private static @Nullable ShotKind kind(ServerLevel level, Manned m) {
		if (m.pos != null) {
			BlockState state = level.getBlockState(m.pos);
			return state.getBlock() instanceof ShipCannonBlock ? ShotKind.of(state.getValue(ShipCannonBlock.LOAD)) : null;
		}
		Ship s = ship(level, m);
		return s == null ? null : s.gunKind(m.key);
	}

	private static void load(ServerLevel level, Manned m, ShotKind kind) {
		if (m.pos != null) {
			BlockState state = level.getBlockState(m.pos);
			if (state.getBlock() instanceof ShipCannonBlock) {
				level.setBlock(m.pos, state.setValue(ShipCannonBlock.LOAD, kind.load()), Block.UPDATE_ALL);
			}
			return;
		}
		Ship s = ship(level, m);
		if (s != null) {
			s.loadGun(m.key, kind);
		}
	}

	private static boolean fire(ServerLevel level, Manned m, Vec3 dir, ServerPlayer player) {
		if (m.pos == null) {
			Ship s = ship(level, m);
			return s != null && s.fireGunAlong(level, m.key, dir, player);
		}
		BlockState state = level.getBlockState(m.pos);
		if (!(state.getBlock() instanceof ShipCannonBlock gun)) {
			return false;
		}
		ShotKind kind = ShotKind.of(state.getValue(ShipCannonBlock.LOAD));
		if (kind == null) {
			return false;
		}
		level.setBlock(m.pos, state.setValue(ShipCannonBlock.LOAD, ShipCannonBlock.Load.EMPTY).setValue(ShipCannonBlock.RECOILED, true), Block.UPDATE_ALL);
		level.scheduleTick(m.pos, gun, ShipCannonBlock.RECOIL_TICKS);
		ShipCannonBlock.fireAlong(level, Vec3.atCenterOf(m.pos), dir, player, null, kind);
		return true;
	}

	/** One gunpowder and one shot out of his pack (both, or neither), and the kind of shot; null if he hasn't both. */
	private static @Nullable ShotKind reload(ServerPlayer player) {
		Inventory inv = player.getInventory();
		int powder = -1, shot = -1;
		for (int i = 0; i < inv.getContainerSize() && (powder < 0 || shot < 0); i++) {
			ItemStack stack = inv.getItem(i);
			if (powder < 0 && stack.is(Items.GUNPOWDER)) {
				powder = i;
			} else if (shot < 0 && ShotKind.of(stack) != null) {
				shot = i;
			}
		}
		if (powder < 0 || shot < 0) {
			return null;
		}
		ShotKind kind = ShotKind.of(inv.getItem(shot));
		inv.getItem(powder).shrink(1);
		inv.getItem(shot).shrink(1);
		inv.setChanged();
		return kind;
	}

	/** Pulls him back to the breech, his look left as it is. */
	private static void pull(ServerPlayer player, Vec3 to) {
		player.connection.teleport(new PositionMoveRotation(to, Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
	}
}
