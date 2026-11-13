package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Three Sorcerer spells that fill gaps in the elements, each cast three ways: as a scroll or staff spell
 * ({@link Arcana#cast}, which a Mage learns and a tome holds), on the {@code J} key of a Sorcerer of its element with no
 * blade in hand (the way the lightning Sorcerer's Storm Ring is cast, a higher art), and on the Spell Engine skill bar
 * (see {@code forms/FormSkills}). Nothing new is bound.
 * <ul>
 * <li><b>Bulwark</b> ({@link #BULWARK}, earth): a dome of packed earth round you for {@link #BULWARK_TICKS} ticks. It
 * follows you. Shots that fly into it are turned back out, and foes inside it are shoved out; it never touches your
 * friends, your party or the harmless.</li>
 * <li><b>Tide Pearl</b> ({@link #TIDE_PEARL}, water): a pearl of water thrown where you look. What it strikes is dragged
 * onto a ring at your feet ({@link #RING_RADIUS} blocks out, on its own side of you), slowed a moment and cut.</li>
 * <li><b>Harrying Gale</b> ({@link #HARRYING_GALE}, air): the foe you look at is tossed a short way, again and again,
 * {@link #HOPS} times, taking a blow on each arrival. Every hop lands on firm ground with room to stand, never in a
 * wall, in water, in fire or off an edge; if there is no such spot the gale drops it.</li>
 * </ul>
 * Already in the game and so not built again: a beam (the arcane bolt, the elements' bolts) and lightning on everything
 * round you ({@link StormArts#ring}). All three count as sorcery ({@link Spellcraft#afterCast}).
 */
public final class SorceryPicks {
	public static final String BULWARK = "bulwark", TIDE_PEARL = "tide_pearl", HARRYING_GALE = "harrying_gale";
	/** The three spells by name. */
	public static final List<String> SPELLS = List.of(BULWARK, TIDE_PEARL, HARRYING_GALE);

	// ---- tuning ----
	/** Each spell's element: the Sorcerer who casts it on the key. */
	static final Map<String, String> ELEMENT = Map.of(BULWARK, "earth", TIDE_PEARL, "water", HARRYING_GALE, "air");
	/** Each element's colour for the effect types. */
	static final int EARTH_COLOUR = 0xB08A4E, WATER_COLOUR = 0x3F76E4, AIR_COLOUR = 0xDDE8F0;
	/** What the key waits between casts (ticks): the dome, the pearl, the gale. */
	static final Map<String, Integer> KEY_COOLDOWN = Map.of(BULWARK, 20 * 40, TIDE_PEARL, 20 * 15, HARRYING_GALE, 20 * 20);
	/** Bulwark: how long it stands (ticks) and how wide (blocks), the shove on a foe inside (blocks per tick), and a turned shot's least speed. */
	static final int BULWARK_TICKS = 20 * 8;
	static final double BULWARK_RADIUS = 4.5, BULWARK_SHOVE = 0.4, TURNED_SPEED = 0.6;
	/** Tide Pearl: its speed (blocks per tick), its flight (ticks), the ring it drags onto (blocks), the damage, the slow (ticks). */
	static final double PEARL_SPEED = 1.6, PEARL_DROP = 0.012, RING_RADIUS = 2.5;
	static final int PEARL_FLIGHT = 16, PEARL_SLOW = 40;
	static final float PEARL_DAMAGE = 3.0F;
	/** Harrying Gale: how many hops, the wait between them (ticks), how far each goes (blocks, least and most), the blow on each arrival, how far away it may be cast. */
	static final int HOPS = 5, HOP_GAP = 12;
	static final double HOP_LEAST = 2.0, HOP_MOST = 3.5, GALE_REACH = 20.0;
	static final float HOP_DAMAGE = 3.0F;

	/** A dome that stands: whose, where it is drawn, when it falls. */
	private static final class Dome {
		final ServerLevel level;
		final UUID caster;
		long until;

		Dome(ServerLevel level, UUID caster, long until) {
			this.level = level;
			this.caster = caster;
			this.until = until;
		}
	}

	/** A pearl in flight. */
	private static final class Pearl {
		final ServerLevel level;
		final UUID caster;
		Vec3 at, heading;
		int left;

		Pearl(ServerLevel level, UUID caster, Vec3 at, Vec3 heading, int left) {
			this.level = level;
			this.caster = caster;
			this.at = at;
			this.heading = heading;
			this.left = left;
		}
	}

	/** A foe being tossed about: who, by whom, hops left, the game time of the next. */
	private static final class Gale {
		final ServerLevel level;
		final UUID caster, target;
		int hops;
		long next;

		Gale(ServerLevel level, UUID caster, UUID target, int hops, long next) {
			this.level = level;
			this.caster = caster;
			this.target = target;
			this.hops = hops;
			this.next = next;
		}
	}

	private static final Map<UUID, Dome> DOMES = new HashMap<>();
	private static final List<Pearl> PEARLS = new ArrayList<>();
	private static final Map<UUID, Gale> GALES = new HashMap<>();
	/** Caster → game time the key's spell is ready again, by spell. */
	private static final Map<String, Map<UUID, Long>> READY = new HashMap<>();

	private SorceryPicks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	static void clear() {
		DOMES.clear();
		PEARLS.clear();
		GALES.clear();
		READY.clear();
	}

	// ---- who casts it, and where ------------------------------------------------------------------------------------------

	/** The spell a Sorcerer's element gives, or null (lightning has the Storm Ring; the rest have theirs elsewhere). */
	public static @Nullable String spellOf(String element) {
		for (Map.Entry<String, String> e : ELEMENT.entrySet()) {
			if (e.getValue().equals(element)) {
				return e.getKey();
			}
		}
		return null;
	}

	/** Whether {@code spell} is one of these. */
	public static boolean ours(String spell) {
		return SPELLS.contains(spell);
	}

	/** The spell on {@code player}'s skill bar: their element's, once they know the higher arts; else null. */
	public static @Nullable String barSpell(Player player) {
		String spell = spellOf(Classes.optionOf(player, Classes.PlayerClass.SORCERER));
		return spell != null && Knowledge.has(player, Knowledge.HIGHER_ARTS) ? spell : null;
	}

	/**
	 * {@code J} for a Sorcerer of earth, water or air with no blade in hand: their element's spell. Always handles the key
	 * for those elements (so the "hold a blade" word is for the rest); false for the other elements.
	 */
	static boolean key(ServerPlayer player, ServerLevel level, String element) {
		String spell = spellOf(element);
		if (spell == null) {
			return false;
		}
		if (!Knowledge.has(player, Knowledge.HIGHER_ARTS)) {
			Knowledge.refuse(player, Knowledge.HIGHER_ARTS);
			return true;
		}
		castKey(player, level, spell);
		return true;
	}

	/** The key's cast of {@code spell}: its wait, its mana, then the spell at a Sorcerer's strength; true if it went. */
	public static boolean castKey(ServerPlayer player, ServerLevel level, String spell) {
		Map<UUID, Long> ready = READY.computeIfAbsent(spell, s -> new HashMap<>());
		long now = level.getGameTime();
		if (ready.getOrDefault(player.getUUID(), 0L) > now) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.sorcery_picks.wait", spellName(spell),
				(ready.get(player.getUUID()) - now + 19) / 20));
			return false;
		}
		if (!Mana.afford(player, Mana.cost(spell))) {
			return false;
		}
		if (!cast(spell, level, player, 1.0F)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
			return false;
		}
		Mana.cast(player, spell);
		ready.put(player.getUUID(), now + KEY_COOLDOWN.getOrDefault(spell, 20 * 20));
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Spellcraft.afterCast(player, level, ELEMENT.getOrDefault(spell, "air"));
		return true;
	}

	private static Component spellName(String spell) {
		return Spellcraft.spellName(spell);
	}

	/** Casts {@code spell} for {@code caster} at {@code power}: true if it took (a gale needs a foe in sight). */
	public static boolean cast(String spell, ServerLevel level, Player caster, float power) {
		return switch (spell) {
			case BULWARK -> bulwark(level, caster, power);
			case TIDE_PEARL -> pearl(level, caster);
			case HARRYING_GALE -> gale(level, caster);
			default -> false;
		};
	}

	/** Whether {@code e} is a foe of {@code caster}: not a friend, and either hostile, set on them, or (a player) fair game. */
	private static boolean foe(Player caster, LivingEntity e) {
		if (e == caster || !e.isAlive() || e.isSpectator() || Downed.isDowned(e) || caster.isAlliedTo(e)) {
			return false;
		}
		if (e instanceof ServerPlayer other) {
			return caster instanceof ServerPlayer c && c.level().isPvpAllowed() && !other.isCreative() && !Parties.allied(c, other);
		}
		return e instanceof Enemy || e instanceof Mob mob && mob.getTarget() != null && (mob.getTarget() == caster || caster.isAlliedTo(mob.getTarget()));
	}

	// ---- bulwark ----------------------------------------------------------------------------------------------------------

	private static boolean bulwark(ServerLevel level, Player caster, float power) {
		long until = level.getGameTime() + Math.round(BULWARK_TICKS * power);
		DOMES.put(caster.getUUID(), new Dome(level, caster.getUUID(), until));
		Vec3 at = caster.position();
		Fx.sigil(level, at.add(0.0, 0.05, 0.0), (float) BULWARK_RADIUS, EARTH_COLOUR, 40);
		Fx.ring(level, at, (float) BULWARK_RADIUS, EARTH_COLOUR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.8F, 0.7F);
		return true;
	}

	private static void tickDome(Dome dome, Player caster) {
		ServerLevel level = dome.level;
		long now = level.getGameTime();
		Vec3 centre = caster.position().add(0.0, caster.getBbHeight() * 0.5, 0.0);
		if (now % 12L == 0L) {
			Fx.orb(level, centre, (float) BULWARK_RADIUS, EARTH_COLOUR, 14);
		}
		AABB box = new AABB(centre, centre).inflate(BULWARK_RADIUS + 3.0);
		for (Projectile shot : level.getEntitiesOfClass(Projectile.class, box, p -> !(p instanceof FishingHook) && p.isAlive())) {
			Entity owner = shot.getOwner();
			if (owner == caster || owner != null && caster.isAlliedTo(owner)) {
				continue;
			}
			Vec3 off = shot.position().subtract(centre);
			Vec3 velocity = shot.getDeltaMovement();
			double dist = off.length();
			if (dist < 1.0E-3 || dist > BULWARK_RADIUS + velocity.length() || velocity.dot(off) >= 0.0) {
				continue;
			}
			// turned: sent back out the way it came in, from just outside the shell
			Vec3 out = off.normalize();
			shot.setPos(centre.add(out.scale(BULWARK_RADIUS + 0.2)));
			shot.setDeltaMovement(out.scale(Math.max(TURNED_SPEED, velocity.length() * 0.6)));
			shot.syncVelocity = true;
			level.sendParticles(Fx.CRACKLE, shot.getX(), shot.getY(), shot.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
			level.playSound(null, shot.getX(), shot.getY(), shot.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.7F, 1.3F);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> foe(caster, x))) {
			Vec3 off = e.position().subtract(centre).multiply(1.0, 0.0, 1.0);
			if (off.lengthSqr() > BULWARK_RADIUS * BULWARK_RADIUS) {
				continue;
			}
			Vec3 out = off.lengthSqr() < 1.0E-4 ? caster.getLookAngle().multiply(-1.0, 0.0, -1.0) : off;
			out = out.normalize();
			e.setDeltaMovement(out.x * BULWARK_SHOVE, Math.max(e.getDeltaMovement().y, 0.12), out.z * BULWARK_SHOVE);
			e.syncVelocity = true;
		}
	}

	// ---- tide pearl -------------------------------------------------------------------------------------------------------

	private static boolean pearl(ServerLevel level, Player caster) {
		Vec3 start = caster.getEyePosition().add(caster.getLookAngle().scale(0.6)).add(0.0, -0.2, 0.0);
		PEARLS.add(new Pearl(level, caster.getUUID(), start, caster.getLookAngle().scale(PEARL_SPEED), PEARL_FLIGHT));
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8F, 1.5F);
		return true;
	}

	private static void tickPearl(Pearl pearl, Player caster) {
		ServerLevel level = pearl.level;
		Vec3 next = pearl.at.add(pearl.heading);
		BlockHitResult wall = level.clip(new ClipContext(pearl.at, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		Vec3 stop = wall.getType() == HitResult.Type.MISS ? next : wall.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, pearl.at, stop, new AABB(pearl.at, stop).inflate(0.6),
			e -> e instanceof LivingEntity l && foe(caster, l) && l.isPickable(), 64.0);
		Fx.orb(level, pearl.at, 0.35F, WATER_COLOUR, 3);
		level.sendParticles(Fx.mote(WATER_COLOUR, 0.8F), pearl.at.x, pearl.at.y, pearl.at.z, 2, 0.08, 0.08, 0.08, 0.0);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			drag(level, caster, target, hit.getLocation());
			pearl.left = 0;
			return;
		}
		if (wall.getType() != HitResult.Type.MISS) {
			Fx.blast(level, wall.getLocation(), 0.8F, WATER_COLOUR);
			pearl.left = 0;
			return;
		}
		pearl.at = next;
		pearl.heading = pearl.heading.add(0.0, -PEARL_DROP, 0.0);
		pearl.left--;
	}

	/** {@code target} dragged onto the ring at {@code caster}'s feet, on its own side of them; it stays where it is if nothing there is safe. */
	private static void drag(ServerLevel level, Player caster, LivingEntity target, Vec3 hitAt) {
		Fx.blast(level, hitAt, 1.0F, WATER_COLOUR);
		Vec3 base = caster.position();
		Vec3 flat = target.position().subtract(base).multiply(1.0, 0.0, 1.0);
		double first = flat.lengthSqr() < 1.0E-4 ? caster.getYRot() * Math.PI / 180.0 : Math.atan2(flat.z, flat.x);
		// its own side first, then either side of that, then behind
		for (int i = 0; i < 8; i++) {
			double angle = first + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * (Math.PI / 4.0);
			Vec3 spot = landing(level, target, base.x + Math.cos(angle) * RING_RADIUS, base.y, base.z + Math.sin(angle) * RING_RADIUS, 1);
			if (spot == null) {
				continue;
			}
			Fx.blast(level, target.position().add(0.0, 0.5, 0.0), 0.8F, WATER_COLOUR);
			target.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), target.getYRot(), target.getXRot(), false);
			target.fallDistance = 0.0;
			target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), PEARL_DAMAGE);
			target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, PEARL_SLOW, 1), caster);
			Fx.ring(level, base.add(0.0, 0.05, 0.0), (float) RING_RADIUS, WATER_COLOUR);
			Fx.sigil(level, base.add(0.0, 0.05, 0.0), (float) RING_RADIUS, WATER_COLOUR, 30);
			level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0F, 0.9F);
			return;
		}
	}

	// ---- harrying gale ----------------------------------------------------------------------------------------------------

	private static boolean gale(ServerLevel level, Player caster) {
		LivingEntity target = Arcana.sighted(caster);
		if (target == null || caster.distanceTo(target) > GALE_REACH || !foe(caster, target)) {
			return false;
		}
		GALES.put(caster.getUUID(), new Gale(level, caster.getUUID(), target.getUUID(), HOPS, level.getGameTime() + 4L));
		Fx.blast(level, target.position().add(0.0, 0.6, 0.0), 1.0F, AIR_COLOUR);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	private static boolean tickGale(Gale gale, Player caster) {
		ServerLevel level = gale.level;
		long now = level.getGameTime();
		if (now < gale.next) {
			return true;
		}
		if (!(level.getEntity(gale.target) instanceof LivingEntity target) || !target.isAlive() || Downed.isDowned(target)) {
			return false;
		}
		Vec3 from = target.position();
		Vec3 spot = null;
		for (int i = 0; i < 10 && spot == null; i++) {
			double angle = target.getRandom().nextDouble() * Math.PI * 2.0;
			double dist = HOP_LEAST + target.getRandom().nextDouble() * (HOP_MOST - HOP_LEAST);
			spot = landing(level, target, from.x + Math.cos(angle) * dist, from.y, from.z + Math.sin(angle) * dist, 1);
		}
		if (spot == null) {
			return false;
		}
		Fx.beam(level, from.add(0.0, 0.8, 0.0), spot.add(0.0, 0.8, 0.0), 0.1F, AIR_COLOUR, 6);
		target.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), target.getYRot(), target.getXRot(), false);
		target.fallDistance = 0.0;
		target.setDeltaMovement(Vec3.ZERO);
		target.syncVelocity = true;
		target.setInvulnerableTime(0);
		target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), HOP_DAMAGE);
		Fx.ring(level, spot, 1.4F, AIR_COLOUR);
		level.sendParticles(Fx.mote(AIR_COLOUR, 0.9F), spot.x, spot.y + 0.8, spot.z, 8, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.8F, 1.4F);
		gale.hops--;
		gale.next = now + HOP_GAP;
		return gale.hops > 0;
	}

	// ---- landing ----------------------------------------------------------------------------------------------------------

	/**
	 * Where {@code e} can stand at {@code x},{@code z} within {@code reach} blocks of {@code y} up or down: room for its whole
	 * body, firm ground under (unless it flies), no water, lava, fire or thorns; null if there is no such spot.
	 */
	static @Nullable Vec3 landing(ServerLevel level, Entity e, double x, double y, double z, int reach) {
		boolean flier = e instanceof Mob mob && mob.getNavigation() instanceof FlyingPathNavigation || e.isNoGravity();
		int base = (int) Math.floor(y);
		for (int i = 0; i <= reach * 2; i++) {
			int dy = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2);
			BlockPos feet = BlockPos.containing(x, base + dy, z);
			Vec3 spot = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			if (!level.noCollision(e, e.getBoundingBox().move(spot.x - e.getX(), spot.y - e.getY(), spot.z - e.getZ()))) {
				continue;
			}
			BlockPos under = feet.below();
			BlockState below = level.getBlockState(under);
			if (!flier && (below.getCollisionShape(level, under).isEmpty() || !below.isFaceSturdy(level, under, net.minecraft.core.Direction.UP))) {
				continue;
			}
			if (hazard(level, feet) || hazard(level, feet.above()) || hazard(level, under)) {
				continue;
			}
			return spot;
		}
		return null;
	}

	private static boolean hazard(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !level.getFluidState(pos).isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
			|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.WITHER_ROSE);
	}

	// ---- the tick ---------------------------------------------------------------------------------------------------------

	private static void tick() {
		if (DOMES.isEmpty() && PEARLS.isEmpty() && GALES.isEmpty()) {
			return;
		}
		for (Iterator<Dome> it = DOMES.values().iterator(); it.hasNext();) {
			Dome dome = it.next();
			Player caster = dome.level.getPlayerByUUID(dome.caster);
			if (caster == null || !caster.isAlive() || dome.level.getGameTime() >= dome.until || caster.level() != dome.level) {
				it.remove();
				continue;
			}
			tickDome(dome, caster);
		}
		for (Iterator<Pearl> it = PEARLS.iterator(); it.hasNext();) {
			Pearl pearl = it.next();
			Player caster = pearl.level.getPlayerByUUID(pearl.caster);
			if (caster == null || !caster.isAlive()) {
				it.remove();
				continue;
			}
			tickPearl(pearl, caster);
			if (pearl.left <= 0) {
				it.remove();
			}
		}
		for (Iterator<Gale> it = GALES.values().iterator(); it.hasNext();) {
			Gale gale = it.next();
			Player caster = gale.level.getPlayerByUUID(gale.caster);
			if (caster == null || !caster.isAlive() || !tickGale(gale, caster)) {
				it.remove();
			}
		}
	}
}
