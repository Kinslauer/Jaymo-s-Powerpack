package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.Wounds;
import com.jaymospowers.ground.FirstTools;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.9) Andy: "a gravity user should utilize rocks and pebbles for more interactions with its kit". Carrying more than
 * {@link #MIN_ROCKS - 1} loose rocks ({@link FirstTools#rock}), B+G (hold B, tap G) gathers them out of your pack into a
 * <b>rock ball</b> that hangs by your shoulder, bigger the more rocks you carry. B+G again splits it into a ring of
 * rocks <b>orbiting</b> you; once more and they go back in the pack. Nothing is used up: the rocks stay in your inventory.
 * <ul>
 * <li>Ball: a few seconds ({@link #BALL_DELAY}) after you hurt something, the ball slams into it for one lump of
 * {@link #DAMAGE_PER_ROCK} per rock (up to {@link #ROCK_CAP} rocks), as long as you're still within {@link #RANGE}.</li>
 * <li>Orbit: after you hurt something, up to {@link #VOLLEY} rocks peel off one by one and pelt it, each for
 * {@link #DAMAGE_PER_ROCK} and a bleed tick ({@link Bleeding#BLEED_DAMAGE}). And any foe that comes close
 * ({@link #GRAZE_REACH}) is clipped by the ring for the same, every {@link #GRAZE_EVERY} ticks.</li>
 * </ul>
 * One follow-up at a time: a new hit while the ball or the volley is still out doesn't send another. Drop below
 * {@link #MIN_ROCKS} and the rocks fall back into the pack. Drawn for everyone by GravityRocksLayer.
 */
public final class GravityRocks {
	// ===== the knobs ================================================================================================
	/** Rocks you must carry (more than 8). */
	public static final int MIN_ROCKS = 9;
	/** Rocks that count toward the damage, and that the ball grows with. */
	public static final int ROCK_CAP = 32;
	/** Each rock's hit (half-hearts). */
	static final float DAMAGE_PER_ROCK = 0.2F;
	/** The ball's lump comes this long after your hit (ticks), if the foe is still within {@link #RANGE} blocks. */
	public static final int BALL_DELAY = 50;
	/** How long the ball (or one orbiting rock) takes to fly to the foe, and as long again to come back (ticks). */
	public static final int BALL_FLIGHT = 6, ROCK_FLIGHT = 5;
	static final double RANGE = 10.0;
	/** Most rocks one hit sends off the ring, and the ticks between them. */
	public static final int VOLLEY = 8, VOLLEY_EVERY = 3;
	/** The ring's radius (blocks from your middle), how far past it a foe is clipped, and how often each foe can be. */
	public static final double ORBIT_RADIUS = 1.5;
	static final double GRAZE_REACH = 0.6;
	static final int GRAZE_EVERY = 20;
	/** How often the pack is counted again (ticks). */
	static final int RECOUNT = 10;
	// ================================================================================================================

	public static final int NONE = 0, BALL = 1, ORBIT = 2;

	/**
	 * On a gravity user, for everyone: the form ({@link #BALL} or {@link #ORBIT}), how many rocks (capped), which rock
	 * they look like (item id), and the follow-up under way: at whom (entity id, -1 none), since when (game time), how
	 * many rocks it sends (1 for the ball).
	 */
	public record Rocks(int form, int count, int item, int target, long from, int shots) {
		public static final StreamCodec<io.netty.buffer.ByteBuf, Rocks> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Rocks::form, ByteBufCodecs.VAR_INT, Rocks::count, ByteBufCodecs.VAR_INT, Rocks::item,
			ByteBufCodecs.VAR_INT, Rocks::target, ByteBufCodecs.VAR_LONG, Rocks::from, ByteBufCodecs.VAR_INT, Rocks::shots, Rocks::new);

		/** When the follow-up under way is over (everything back home). */
		public long busyUntil() {
			if (this.target < 0) {
				return Long.MIN_VALUE;
			}
			return this.form == BALL ? this.from + 2L * BALL_FLIGHT : this.from + (long)(this.shots - 1) * VOLLEY_EVERY + 2L * ROCK_FLIGHT;
		}

		public ItemStack stack() {
			Item item = BuiltInRegistries.ITEM.byId(this.item);
			return new ItemStack(item);
		}
	}

	public static final AttachmentType<Rocks> ROCKS = AttachmentRegistry.create(JaymosPowers.id("gravity_rocks"),
		builder -> builder.syncWith(Rocks.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** A ball's lump waiting to land: on whom, when the ball sets off. */
	private record Pending(UUID target, long launch) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	/** Who the ring clipped last, and when (per gravity user). */
	private static final Map<UUID, Map<Integer, Long>> GRAZED = new HashMap<>();
	/** True while the rocks land their blows (so they don't call up another follow-up). */
	private static boolean striking;

	private GravityRocks() {
	}

	static void init() {
		JaymosPowers.LOGGER.debug("gravity rocks attachment: {}", ROCKS);
		ServerTickEvents.END_SERVER_TICK.register(GravityRocks::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(GravityRocks::afterDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PENDING.clear();
			GRAZED.clear();
		});
	}

	/** Rocks in {@code player}'s inventory (every slot, both hands too). */
	static int count(Player player) {
		int rocks = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (FirstTools.rock(stack)) {
				rocks += stack.getCount();
			}
		}
		return rocks;
	}

	/** The rock they carry most of (what the ball is made of). */
	private static int mostCarried(Player player) {
		Map<Item, Integer> counts = new HashMap<>();
		var inventory = player.getInventory();
		Item best = null;
		int most = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (FirstTools.rock(stack)) {
				int n = counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
				if (n > most) {
					most = n;
					best = stack.getItem();
				}
			}
		}
		return best == null ? 0 : BuiltInRegistries.ITEM.getId(best);
	}

	/** B+G: none, then the ball, then the orbit, then back in the pack. */
	static void cycle(ServerPlayer player) {
		Rocks rocks = player.getAttached(ROCKS);
		ServerLevel level = player.level();
		if (rocks == null) {
			int count = count(player);
			if (count < MIN_ROCKS) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_rocks_few", MIN_ROCKS - 1).withStyle(ChatFormatting.GRAY));
				GravityPower.fizzle(player);
				return;
			}
			PENDING.remove(player.getUUID());
			rocks = new Rocks(BALL, Math.min(count, ROCK_CAP), mostCarried(player), -1, 0L, 0);
			player.setAttached(ROCKS, rocks);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.6F, 0.5F);
			puff(level, rocks, player.position().add(0.0, player.getBbHeight() * 0.8, 0.0), 14);
		} else if (rocks.form() == BALL) {
			PENDING.remove(player.getUUID());
			player.setAttached(ROCKS, new Rocks(ORBIT, rocks.count(), rocks.item(), -1, 0L, 0));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 1.3F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.7F, 0.7F);
			puff(level, rocks, player.position().add(0.0, player.getBbHeight() * 0.55, 0.0), 18);
		} else {
			dismiss(player, true);
		}
	}

	/** The rocks drop back into the pack. */
	static void dismiss(ServerPlayer player, boolean sound) {
		PENDING.remove(player.getUUID());
		GRAZED.remove(player.getUUID());
		if (player.getAttached(ROCKS) == null) {
			return;
		}
		player.removeAttached(ROCKS);
		if (sound) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRAVEL_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
	}

	static void forget(ServerPlayer player) {
		dismiss(player, false);
	}

	// ---------------------------------------------------------------- the follow-ups

	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (striking || blocked || damageTaken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || player == victim
			|| source.is(Bleeding.BLEEDING_DAMAGE) || source.is(Wounds.WOUNDS_DAMAGE) || !victim.isAlive()) {
			return;
		}
		Rocks rocks = player.getAttached(ROCKS);
		if (rocks == null || player.level() != victim.level()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < rocks.busyUntil() || PENDING.containsKey(player.getUUID())) {
			return;
		}
		if (rocks.form() == BALL) {
			PENDING.put(player.getUUID(), new Pending(victim.getUUID(), now + BALL_DELAY - BALL_FLIGHT));
		} else {
			int shots = Math.min(rocks.count(), VOLLEY);
			player.setAttached(ROCKS, new Rocks(ORBIT, rocks.count(), rocks.item(), victim.getId(), now, shots));
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Rocks rocks = player.getAttached(ROCKS);
			if (rocks == null) {
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || !Powers.has(player, GravityPower.INSTANCE)) {
				dismiss(player, false);
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			if (player.tickCount % RECOUNT == 0) {
				int count = count(player);
				if (count < MIN_ROCKS) {
					dismiss(player, true);
					continue;
				}
				int capped = Math.min(count, ROCK_CAP);
				int item = mostCarried(player);
				if (capped != rocks.count() || item != rocks.item()) {
					rocks = new Rocks(rocks.form(), capped, item, rocks.target(), rocks.from(), rocks.shots());
					player.setAttached(ROCKS, rocks);
				}
			}
			if (rocks.form() == BALL) {
				tickBall(player, level, rocks, now);
			} else {
				tickVolley(player, level, rocks, now);
				graze(player, level, rocks, now);
			}
		}
	}

	private static void tickBall(ServerPlayer player, ServerLevel level, Rocks rocks, long now) {
		Pending pending = PENDING.get(player.getUUID());
		if (pending != null && now >= pending.launch()) {
			PENDING.remove(player.getUUID());
			// still in range: the ball sets off (everyone sees it go)
			if (level.getEntity(pending.target()) instanceof LivingEntity target && inRange(player, target)) {
				player.setAttached(ROCKS, new Rocks(BALL, rocks.count(), rocks.item(), target.getId(), now, 1));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F);
			}
			return;
		}
		if (rocks.target() >= 0 && now == rocks.from() + BALL_FLIGHT && level.getEntity(rocks.target()) instanceof LivingEntity target
			&& inRange(player, target)) {
			hit(player, level, target, DAMAGE_PER_ROCK * rocks.count(), false);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.9F, 0.8F);
			puff(level, rocks, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), 10 + rocks.count() / 2);
		}
	}

	private static void tickVolley(ServerPlayer player, ServerLevel level, Rocks rocks, long now) {
		if (rocks.target() < 0) {
			return;
		}
		long since = now - rocks.from() - ROCK_FLIGHT;
		if (since < 0 || since % VOLLEY_EVERY != 0 || since / VOLLEY_EVERY >= rocks.shots()) {
			return;
		}
		if (level.getEntity(rocks.target()) instanceof LivingEntity target && inRange(player, target)) {
			hit(player, level, target, DAMAGE_PER_ROCK, true);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F,
				0.9F + level.getRandom().nextFloat() * 0.4F);
			puff(level, rocks, target.position().add(0.0, target.getBbHeight() * 0.6, 0.0), 3);
		}
	}

	/** Orbiting, a foe that comes too close is clipped by the ring. */
	private static void graze(ServerPlayer player, ServerLevel level, Rocks rocks, long now) {
		if (player.tickCount % 5 != 0) {
			return;
		}
		Map<Integer, Long> grazed = GRAZED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		grazed.values().removeIf(at -> now - at > GRAZE_EVERY * 4L);
		double reach = ORBIT_RADIUS + GRAZE_REACH;
		Vec3 middle = player.position().add(0.0, player.getBbHeight() * 0.55, 0.0);
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> foe(player, e))) {
			double dx = foe.getX() - player.getX(), dz = foe.getZ() - player.getZ();
			double flat = Math.sqrt(dx * dx + dz * dz) - foe.getBbWidth() * 0.5;
			if (flat > reach || middle.y < foe.getY() - 0.3 || middle.y > foe.getY() + foe.getBbHeight() + 0.3) {
				continue;
			}
			Long last = grazed.get(foe.getId());
			if (last != null && now - last < GRAZE_EVERY) {
				continue;
			}
			grazed.put(foe.getId(), now);
			hit(player, level, foe, DAMAGE_PER_ROCK, true);
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.7F, 1.2F);
			puff(level, rocks, foe.position().add(0.0, foe.getBbHeight() * 0.5, 0.0), 3);
		}
	}

	/** Someone the ring goes for: a monster, anything after you, or whoever hurt you last. */
	private static boolean foe(ServerPlayer player, LivingEntity entity) {
		if (entity == player || !entity.isAlive() || entity.isSpectator() || entity.isAlliedTo(player)) {
			return false;
		}
		if (entity instanceof Player other && (other.isCreative() || !player.canHarmPlayer(other))) {
			return false;
		}
		return entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget() == player || player.getLastHurtByMob() == entity
			|| player.getLastHurtMob() == entity && player.tickCount - player.getLastHurtMobTimestamp() < 200;
	}

	private static boolean inRange(ServerPlayer player, LivingEntity target) {
		return target.isAlive() && target.level() == player.level() && player.distanceTo(target) <= RANGE;
	}

	/** A rock (or the ball) lands: its damage, and with a bleed tick when it's one of the small ones. */
	private static void hit(ServerPlayer player, ServerLevel level, LivingEntity target, float damage, boolean bleed) {
		GravityPower.touch(target, player);
		striking = true;
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, player.damageSources().playerAttack(player), damage);
			if (bleed && target.isAlive() && bleeds(target)) {
				Wounds.hurtPart(target, BodyPart.BODY, Bleeding.BLEED_DAMAGE, player, Bleeding.BLEEDING_DAMAGE);
			}
		} finally {
			striking = false;
		}
	}

	/** Something with blood in it and a body Wounds parts (not a creative player). */
	private static boolean bleeds(LivingEntity target) {
		return BodyPlan.of(target) != null && !target.getType().builtInRegistryHolder().is(com.jaymospowers.core.BladeBlood.BLOODLESS)
			&& !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
	}

	/** Grit thrown off the rocks. */
	private static void puff(ServerLevel level, Rocks rocks, Vec3 at, int count) {
		ItemStack stone = rocks.stack();
		if (stone.isEmpty()) {
			level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, count / 2 + 1, 0.3, 0.3, 0.3, 0.02);
			return;
		}
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stone.getItem()), at.x, at.y, at.z, count, 0.3, 0.3, 0.3, 0.08);
	}

	/** @return the form showing on {@code player}, {@link #NONE} if none. */
	public static int form(@Nullable Player player) {
		Rocks rocks = player == null ? null : player.getAttached(ROCKS);
		return rocks == null ? NONE : rocks.form();
	}
}
