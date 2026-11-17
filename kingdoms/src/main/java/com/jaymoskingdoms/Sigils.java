package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Sorcerer's tree (class trees, docs/ClassTrees-Plan.md): the sigil from the start, and the perks that grow it.
 * <ul>
 * <li><b>The sigil</b> (a tap of the class key): a glyph flicks off the blade (or the hand) and flies until it meets
 * something, then bursts with the element ({@link Spellcraft#strike}: fire alights, ice freezes, lightning chains, earth
 * staggers, air launches, water douses). Small damage, grown by practice ({@link ClassTrees#practice}), on a short wait.</li>
 * <li><b>Sigilcraft</b> (3 ranks): a wider burst, a longer flight, a shorter wait.</li>
 * <li><b>Ward Sigil</b> (a crossroads with Twin Imbue): sneak and the key lay the sigil on the ground as a trap that bursts
 * under the first foe to step on it.</li>
 * <li><b>Elemental Burst</b> (a crossroads with Lingering Imbue): the key held on a blade already burning ends the imbue in
 * a blast round you, as strong as what was left of it.</li>
 * <li><b>Etched Blade</b>: while imbued, every {@link #ETCHED_EVERY}th link of a combo throws a sigil for nothing.</li>
 * </ul>
 * Imbue, Twin Imbue and Lingering Imbue are {@link Spellcraft#holdImbue}'s.
 */
public final class Sigils {
	/** The sigil: its damage before practice, flight (blocks), speed (blocks a tick), burst radius, wait (ticks). */
	static final float SIGIL_DAMAGE = 3.0F;
	static final double SIGIL_RANGE = 16.0, SIGIL_SPEED = 1.2, SIGIL_RADIUS = 1.5;
	static final int SIGIL_COOLDOWN = 30;
	/** Sigilcraft, a rank: this much wider, this much further, this share off the wait. */
	static final double CRAFT_RADIUS = 0.75, CRAFT_RANGE = 6.0;
	static final float CRAFT_COOLDOWN = 0.15F;
	/** Ward Sigil: how long one lies (ticks), how many at once, how far ahead it can be laid, how near a foe sets it off. */
	static final int WARD_TICKS = 20 * 60, WARD_MOST = 2;
	static final double WARD_REACH = 5.0, WARD_TRIGGER = 1.2;
	/** A ward bursts this much harder than a thrown sigil (it waited for its moment). */
	static final float WARD_DAMAGE = 1.5F;
	/** Elemental Burst: its damage with the whole imbue left, its radius. */
	static final float BURST_DAMAGE = 8.0F;
	static final double BURST_RADIUS = 4.0;
	/** Lingering Imbue: an imbue lasts this many times as long. */
	static final float LINGER_FACTOR = 2.0F;
	/** Etched Blade: a free sigil on every this-many-th link. */
	static final int ETCHED_EVERY = 4;

	/** The tree's own perks. */
	public static final String CRAFT = "sor_craft", IMBUE = "sor_imbue", WARD = "sor_ward", TWIN = "sor_twin", BURST = "sor_burst",
		LINGER = "sor_linger", ETCHED = "sor_etched";

	/** A sigil in flight. */
	private static final class Flight {
		final ServerLevel level;
		final UUID owner;
		final String element;
		final float damage;
		final double radius, range;
		Vec3 pos;
		final Vec3 velocity;
		double flown;

		Flight(ServerLevel level, UUID owner, String element, Vec3 pos, Vec3 velocity, float damage, double radius, double range) {
			this.level = level;
			this.owner = owner;
			this.element = element;
			this.pos = pos;
			this.velocity = velocity;
			this.damage = damage;
			this.radius = radius;
			this.range = range;
		}
	}

	/** A sigil laid on the ground. */
	private record Ward(ServerLevel level, UUID owner, String element, Vec3 at, long until, float damage, double radius) {
	}

	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static final List<Ward> WARDS = new ArrayList<>();

	private Sigils() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Sigils::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLIGHTS.clear();
			WARDS.clear();
		});
	}

	private static float damage(ServerPlayer player) {
		return SIGIL_DAMAGE * ClassTrees.practice(player, PlayerClass.SORCERER);
	}

	private static double radius(ServerPlayer player) {
		return SIGIL_RADIUS + CRAFT_RADIUS * ClassTrees.power(player, CRAFT);
	}

	/** Throws the sigil along {@code player}'s look ({@code free}: Etched Blade's, no cost and no wait). */
	static void cast(ServerPlayer player, ServerLevel level, String element, boolean free) {
		ItemStack key = Arcana.BONE_STAFF.getDefaultInstance();
		if (!free) {
			if (player.getCooldowns().isOnCooldown(key) || !Spellcraft.pay(player, element, "bolt")) {
				return;
			}
			float craft = ClassTrees.power(player, CRAFT);
			player.getCooldowns().addCooldown(key, Math.max(5, Math.round(SIGIL_COOLDOWN * (1.0F - CRAFT_COOLDOWN * craft) * ArcaneExtras.glyph(player))));
		}
		Vec3 look = player.getLookAngle();
		boolean blade = Spellcraft.blade(player.getMainHandItem());
		// off the blade's tip, or the open hand
		Vec3 from = player.getEyePosition().add(0.0, -0.3, 0.0).add(look.scale(blade ? 1.2 : 0.6));
		int colour = Absorption.colour(element);
		FLIGHTS.add(new Flight(level, player.getUUID(), element, from, look.scale(SIGIL_SPEED), damage(player), radius(player),
			SIGIL_RANGE + CRAFT_RANGE * ClassTrees.power(player, CRAFT)));
		Fx.shape(level, 4, from, look, 0.35F, colour, 6);
		Casting.gesture(player, Casting.Gesture.PUSH, colour);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Spellcraft.sound(element), SoundSource.PLAYERS, 0.7F, 1.5F);
		if (!free) {
			Spellcraft.afterCast(player, level, element);
		}
	}

	/** Ward Sigil: lays the sigil on the ground where {@code player} looks (or at their feet); the oldest goes past {@link #WARD_MOST}. */
	static void ward(ServerPlayer player, ServerLevel level, String element) {
		ItemStack key = Arcana.BONE_STAFF.getDefaultInstance();
		if (player.getCooldowns().isOnCooldown(key) || !Spellcraft.pay(player, element, "bolt")) {
			return;
		}
		float craft = ClassTrees.power(player, CRAFT);
		player.getCooldowns().addCooldown(key, Math.max(5, Math.round(SIGIL_COOLDOWN * (1.0F - CRAFT_COOLDOWN * craft) * ArcaneExtras.glyph(player))));
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(WARD_REACH)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == net.minecraft.core.Direction.UP ? hit.getLocation() : player.position();
		List<Ward> mine = WARDS.stream().filter(w -> w.owner().equals(player.getUUID())).toList();
		if (mine.size() >= WARD_MOST) {
			WARDS.remove(mine.getFirst());
		}
		double radius = radius(player);
		WARDS.add(new Ward(level, player.getUUID(), element, at, level.getGameTime() + WARD_TICKS, damage(player) * WARD_DAMAGE, radius));
		Fx.sigil(level, at.add(0.0, 0.05, 0.0), (float) radius, Absorption.colour(element), 40);
		Casting.gesture(player, Casting.Gesture.SWEEP, Absorption.colour(element));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
		Spellcraft.afterCast(player, level, element);
	}

	/** Elemental Burst: {@code element} blasting out round {@code player}; {@code left}: the share of the imbue that was left. */
	static void burst(ServerPlayer player, ServerLevel level, String element, float left) {
		Vec3 at = player.getBoundingBox().getCenter();
		int colour = Absorption.colour(element);
		Fx.blast(level, at, (float) BURST_RADIUS, colour);
		Fx.ring(level, player.position(), (float) BURST_RADIUS, colour);
		level.playSound(null, at.x, at.y, at.z, Spellcraft.sound(element), SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.5F);
		float hurt = BURST_DAMAGE * Math.max(0.25F, left) * Math.max(Classes.SECOND_SHARE, Classes.share(player, PlayerClass.SORCERER));
		for (LivingEntity foe : CombatArts.foes(level, player, player.getBoundingBox().inflate(BURST_RADIUS))) {
			foe.hurtServer(level, level.damageSources().indirectMagic(player, player), hurt);
			Spellcraft.strike(level, player, foe, element, true);
		}
		Spellcraft.afterCast(player, level, element);
	}

	/** A link of {@code player}'s combo landed on {@code target} ({@link CombatArts}): Etched Blade's free sigil. */
	static void linked(ServerPlayer player, int links) {
		if (links % ETCHED_EVERY != 0 || ClassTrees.has(player, ETCHED) == 0 || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		String element = Spellcraft.ownImbue(player);
		if (element != null) {
			cast(player, level, element, true);
		}
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
			Flight f = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(f.owner);
			if (owner == null || owner.level() != f.level || !fly(owner, f)) {
				it.remove();
			}
		}
		if (WARDS.isEmpty()) {
			return;
		}
		for (Iterator<Ward> it = WARDS.iterator(); it.hasNext(); ) {
			Ward w = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(w.owner());
			long now = w.level().getGameTime();
			if (owner == null || owner.level() != w.level() || now >= w.until()) {
				it.remove();
				continue;
			}
			if (now % 40 == 0) {
				// the marks glow on for as long as the ward lies
				Fx.sigil(w.level(), w.at().add(0.0, 0.05, 0.0), (float) w.radius(), Absorption.colour(w.element()), 40);
			}
			List<LivingEntity> near = CombatArts.foes(w.level(), owner, new AABB(w.at(), w.at()).inflate(WARD_TRIGGER, 1.0, WARD_TRIGGER).move(0.0, 0.5, 0.0));
			if (!near.isEmpty()) {
				it.remove();
				explode(w.level(), owner, w.element(), w.at().add(0.0, 0.5, 0.0), near.getFirst(), w.damage(), w.radius());
			}
		}
	}

	/** A tick of a sigil's flight: false once it has burst or flown out. */
	private static boolean fly(ServerPlayer owner, Flight f) {
		ServerLevel level = f.level;
		Vec3 from = f.pos, to = from.add(f.velocity);
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(owner, from, end, new AABB(from, end).inflate(0.6),
			e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != owner && !Thralls.refuses(owner, l),
			from.distanceToSqr(end));
		int colour = Absorption.colour(f.element);
		level.sendParticles(Fx.rune(colour, 0.8F), true, false, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0.0);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			explode(level, owner, f.element, hit.getLocation(), target, f.damage, f.radius);
			return false;
		}
		if (block.getType() != HitResult.Type.MISS) {
			explode(level, owner, f.element, end, null, f.damage, f.radius);
			return false;
		}
		f.pos = to;
		f.flown += f.velocity.length();
		if ((int) f.flown % 3 == 0) {
			Fx.shape(level, 4, to, f.velocity, 0.3F, colour, 4);
		}
		return f.flown < f.range;
	}

	/** The sigil bursting at {@code at}: {@code direct} (if any) and the foes round it take the element. */
	private static void explode(ServerLevel level, ServerPlayer owner, String element, Vec3 at, @org.jspecify.annotations.Nullable LivingEntity direct,
		float damage, double radius) {
		int colour = Absorption.colour(element);
		Fx.blast(level, at, (float) radius, colour);
		Fx.shape(level, 4, at, Vec3.ZERO, (float) radius, colour, 10);
		level.playSound(null, at.x, at.y, at.z, Spellcraft.sound(element), SoundSource.PLAYERS, 0.9F, 1.2F);
		Set<LivingEntity> struck = new HashSet<>(CombatArts.foes(level, owner, new AABB(at, at).inflate(radius)));
		if (direct != null) {
			struck.add(direct);
		}
		for (LivingEntity target : struck) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, level.damageSources().indirectMagic(owner, owner), damage);
			Spellcraft.strike(level, owner, target, element, true);
		}
		// (class trees) Sigil Chain: it jumps to one more foe near, at half strength
		ArcaneExtras.chain(level, owner, element, direct != null ? direct.getBoundingBox().getCenter() : at, struck, damage);
		if (element.equals("water")) {
			// water douses the fires round it too
			int r = (int) Math.ceil(radius);
			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(at).offset(-r, -1, -r), BlockPos.containing(at).offset(r, 1, r))) {
				if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
					level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}
}
