package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Spells that find and foresee. Detect magic is Find Magic ({@link Rituals}); this class holds the other three, none of which
 * ever says anything in words:
 * <ul>
 * <li><b>Locate object</b> (the thing to find in the other hand) and <b>locate creature</b> (a lock of hair, {@link Hair}): a pull.
 * For half a minute a thread of motes drifts from your eyes toward it, thicker the nearer it is, with a chime that rises as you
 * close. If nothing is within reach, nothing stirs but a dull note.</li>
 * <li><b>Augury:</b> two bones thrown, and a sign: gold motes rising and a bright chime for weal, dark smoke and a low bell for woe,
 * both for a mixed fate, only a dry clack for nothing. It reads the way you face, the next day's road: the monsters and lairs
 * on it, the people and the weather; it is right nine times in ten.</li>
 * </ul>
 */
public final class Divination {
	public static final Set<String> SPELLS = Set.of("locate_object", "locate_creature", "augury", "scrying");
	/** How far locate looks for an object (blocks) and a creature; how long the pull lasts (ticks). */
	static final double OBJECT_REACH = 128.0, CREATURE_REACH = 600.0;
	static final int PULL_TICKS = 20 * 30;
	/** The pull's draw: how often (ticks), how far ahead of the eyes it stretches (blocks), how near ends it (blocks). */
	static final int DRAW_EVERY = 4, PING_EVERY = 20;
	static final double THREAD_NEAR = 1.6, THREAD_FAR = 5.0, ARRIVED = 2.5;
	/** Augury: how far ahead and wide it reads (blocks, cosine of the half angle), the time before the sign shows (ticks). */
	static final double READ_REACH = 96.0, READ_WIDTH = 0.0;
	static final int SIGN_AFTER = 30, SIGN_TICKS = 60;
	/** Augury's odds of reading the wrong way. */
	static final float WRONG = 0.1F;
	/** Weal and woe: the score that makes either, and what each thing on the road is worth. */
	static final float GOOD_AT = 2.0F, ILL_AT = -2.0F, NOTHING_UNDER = 0.5F;
	static final float PER_MONSTER = -1.0F, MONSTERS_MOST = -8.0F, PER_BOSS = -6.0F, IN_LAIR = -5.0F, STORM = -2.0F, NIGHT = -1.0F;
	static final float PER_FOLK = 0.5F, FOLK_MOST = 4.0F, PER_BEAST = 0.25F, BEASTS_MOST = 2.0F, REALM = 2.0F, WEAK = -2.0F;
	static final int GOLD = 0xF2D98A, SMOKE = 0x2C1F3A, WHITE = 0xE8E8E8;

	private record Pull(ServerLevel level, @Nullable UUID entity, @Nullable Vec3 spot, long until, boolean creature) {
	}

	private record Sign(ServerLevel level, Vec3 at, int kind, long when) {
	}

	private static final Map<UUID, Pull> PULLS = new HashMap<>();
	private static final Map<UUID, Sign> SIGNS = new HashMap<>();

	private Divination() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Divination::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PULLS.clear();
			SIGNS.clear();
		});
	}

	public static boolean ours(String spell) {
		return SPELLS.contains(spell);
	}

	static boolean cast(String spell, ServerLevel level, net.minecraft.world.entity.player.Player player, float power) {
		if (!(player instanceof ServerPlayer caster)) {
			return false;
		}
		return switch (spell) {
			case "locate_object" -> locateObject(level, caster);
			case "locate_creature" -> locateCreature(level, caster);
			case "scrying" -> Scrying.cast(level, caster);
			default -> augury(level, caster);
		};
	}

	// ---- locate ------------------------------------------------------------------------------------------------------------------

	/** The thing to look for: what is held in the hand that is not a focus or a book, else null. */
	private static @Nullable ItemStack pattern(ServerPlayer player) {
		for (ItemStack s : List.of(player.getOffhandItem(), player.getMainHandItem())) {
			if (!s.isEmpty() && !SpellParts.focus(s) && !s.is(Items.BOOK) && !s.is(Items.WRITABLE_BOOK) && !s.is(Items.WRITTEN_BOOK)
				&& !(s.getItem() instanceof Arcana.Scroll)) {
				return s;
			}
		}
		return null;
	}

	private static boolean locateObject(ServerLevel level, ServerPlayer caster) {
		ItemStack want = pattern(caster);
		if (want == null) {
			return false;
		}
		Vec3 eye = caster.getEyePosition();
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, caster.getBoundingBox().inflate(OBJECT_REACH), x -> x.getItem().is(want.getItem()))) {
			double d = e.distanceToSqr(eye);
			if (d < bestD) {
				best = e.position();
				bestD = d;
			}
		}
		int span = (int) Math.ceil(OBJECT_REACH / 16.0);
		int cx = BlockPos.containing(eye).getX() >> 4, cz = BlockPos.containing(eye).getZ() >> 4;
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
					if (be instanceof BaseContainerBlockEntity c && holds(c, want)) {
						double d = Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(eye);
						if (d < bestD && d <= OBJECT_REACH * OBJECT_REACH) {
							best = Vec3.atCenterOf(be.getBlockPos());
							bestD = d;
						}
					}
				}
			}
		}
		start(level, caster, null, best, false);
		return true;
	}

	private static boolean holds(BaseContainerBlockEntity c, ItemStack want) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(want.getItem())) {
				return true;
			}
		}
		return false;
	}

	private static boolean locateCreature(ServerLevel level, ServerPlayer caster) {
		ItemStack lock = Hair.carried(caster);
		UUID owner = Hair.ownerOf(lock);
		if (owner == null) {
			return false;
		}
		Entity e = Hair.find(level, owner);
		if (e != null && e.level() == level && e.distanceTo(caster) <= CREATURE_REACH && e.isAlive()) {
			start(level, caster, e.getUUID(), null, true);
		} else {
			start(level, caster, null, null, true);
		}
		return true;
	}

	private static void start(ServerLevel level, ServerPlayer caster, @Nullable UUID entity, @Nullable Vec3 spot, boolean creature) {
		boolean found = entity != null || spot != null;
		if (!found) {
			// nothing within reach: a dull note and a few motes that fall
			level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
			level.sendParticles(caster, ParticleTypes.SMOKE, true, true, caster.getX(), caster.getEyeY(), caster.getZ(), 6, 0.3, 0.1, 0.3, 0.01);
			return;
		}
		PULLS.put(caster.getUUID(), new Pull(level, entity, spot, level.getGameTime() + PULL_TICKS, creature));
		Concentration.hold(caster, creature ? "locate_creature" : "locate_object", PULL_TICKS, () -> PULLS.remove(caster.getUUID()));
		level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	private static void tick(MinecraftServer server) {
		if (!PULLS.isEmpty() && server.getTickCount() % DRAW_EVERY == 0) {
			for (Map.Entry<UUID, Pull> e : List.copyOf(PULLS.entrySet())) {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				Pull pull = e.getValue();
				if (p == null || p.level() != pull.level() || pull.level().getGameTime() >= pull.until() || !p.isAlive()) {
					PULLS.remove(e.getKey());
					continue;
				}
				draw(p, pull);
			}
		}
		if (!SIGNS.isEmpty()) {
			for (Map.Entry<UUID, Sign> e : List.copyOf(SIGNS.entrySet())) {
				Sign s = e.getValue();
				if (s.level().getGameTime() >= s.when()) {
					SIGNS.remove(e.getKey());
					show(server.getPlayerList().getPlayer(e.getKey()), s);
				}
			}
		}
	}

	private static void draw(ServerPlayer p, Pull pull) {
		ServerLevel level = pull.level();
		Vec3 goal = pull.spot();
		if (pull.entity() != null) {
			Entity e = level.getEntity(pull.entity());
			if (e == null || !e.isAlive()) {
				PULLS.remove(p.getUUID());
				return;
			}
			goal = e.getBoundingBox().getCenter();
		}
		Vec3 eye = p.getEyePosition();
		Vec3 to = goal.subtract(eye);
		double dist = to.length();
		if (dist <= ARRIVED) {
			Fx.burst(level, goal.x, goal.y, goal.z, GOLD, 1.0F, 12, 0.3, 0.3, 0.3);
			level.playSound(null, goal.x, goal.y, goal.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6F, 1.6F);
			PULLS.remove(p.getUUID());
			Concentration.drop(p);
			return;
		}
		Vec3 dir = to.scale(1.0 / dist);
		// thicker the nearer: from a lone mote to a bright thread
		int motes = dist < 16.0 ? 5 : dist < 64.0 ? 3 : 1;
		long now = level.getGameTime();
		for (int i = 0; i < motes; i++) {
			double along = THREAD_NEAR + (THREAD_FAR - THREAD_NEAR) * ((now / DRAW_EVERY + i * 3) % 8) / 8.0;
			Vec3 at = eye.add(dir.scale(along)).add(0.0, -0.35, 0.0);
			level.sendParticles(p, Fx.mote(pull.creature() ? 0xE0A0FF : GOLD, 0.7F), true, true, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		if (now % PING_EVERY < DRAW_EVERY) {
			float pitch = (float) Math.max(0.6, 2.0 - dist / 300.0);
			hear(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, pitch);
		}
	}

	/** A sound only {@code p} hears, at their own ear. */
	public static void hear(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(sound), SoundSource.PLAYERS,
			p.getX(), p.getEyeY(), p.getZ(), volume, pitch, p.getRandom().nextLong()));
	}

	// ---- augury ------------------------------------------------------------------------------------------------------------------

	private static boolean augury(ServerLevel level, ServerPlayer caster) {
		Vec3 hand = caster.getEyePosition().add(caster.getLookAngle().scale(1.2)).add(0.0, -0.5, 0.0);
		Vec3 ground = NewSpells.aim(level, caster, 2.5);
		for (int i = 0; i < 3; i++) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.BONE), hand.x, hand.y, hand.z, 4, 0.1, 0.1, 0.1, 0.15);
		}
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.SKELETON_STEP, SoundSource.PLAYERS, 0.9F, 0.8F);
		float score = read(level, caster);
		int kind;
		if (level.getRandom().nextFloat() < WRONG) {
			score = -score;
		}
		if (Math.abs(score) < NOTHING_UNDER) {
			kind = 0;
		} else if (score >= GOOD_AT) {
			kind = 1;
		} else if (score <= ILL_AT) {
			kind = 2;
		} else {
			kind = 3;
		}
		SIGNS.put(caster.getUUID(), new Sign(level, ground, kind, level.getGameTime() + SIGN_AFTER));
		return true;
	}

	/** What lies on the road {@code p} faces: the score of its dangers and its comforts. */
	private static float read(ServerLevel level, ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		float monsters = 0.0F, folk = 0.0F, beasts = 0.0F, score = 0.0F;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(READ_REACH), x -> x != p && x.isAlive())) {
			Vec3 to = e.position().subtract(eye).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(look) < READ_WIDTH) {
				continue;
			}
			if (e instanceof Mob m && Senses.boss(m)) {
				score += PER_BOSS;
			} else if (e instanceof Enemy) {
				monsters += PER_MONSTER;
			} else if (e instanceof Villager || e instanceof Player || e instanceof com.jaymoskingdoms.Folk) {
				folk += PER_FOLK;
			} else if (e instanceof net.minecraft.world.entity.animal.Animal) {
				beasts += PER_BEAST;
			}
		}
		score += Math.max(MONSTERS_MOST, monsters) + Math.min(FOLK_MOST, folk) + Math.min(BEASTS_MOST, beasts);
		for (double d = 16.0; d <= READ_REACH; d += 16.0) {
			BlockPos at = BlockPos.containing(eye.add(look.scale(d)));
			if (LairLand.inLand(level, at)) {
				score += IN_LAIR;
				break;
			}
		}
		for (double d = 16.0; d <= READ_REACH; d += 24.0) {
			if (Theft.lawAt(level, BlockPos.containing(eye.add(look.scale(d)))) != null) {
				score += REALM;
				break;
			}
		}
		if (level.isThundering()) {
			score += STORM;
		}
		if (level.isDarkOutside()) {
			score += NIGHT;
		}
		if (p.getHealth() < p.getMaxHealth() * 0.4F || p.getFoodData().getFoodLevel() < 6) {
			score += WEAK;
		}
		return score;
	}

	private static void show(@Nullable ServerPlayer p, Sign s) {
		if (p == null || p.level() != s.level()) {
			return;
		}
		ServerLevel level = s.level();
		Vec3 at = s.at();
		switch (s.kind()) {
			case 1, 3 -> {
				level.sendParticles(Fx.mote(GOLD, 1.0F), at.x, at.y + 0.2, at.z, s.kind() == 1 ? 24 : 10, 0.35, 0.1, 0.35, 0.05);
				level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, s.kind() == 1 ? 10 : 4, 0.2, 0.1, 0.2, 0.04);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.8F);
			}
			default -> {
			}
		}
		switch (s.kind()) {
			case 2, 3 -> {
				level.sendParticles(Fx.miasma(SMOKE, 1.6F), at.x, at.y + 0.2, at.z, s.kind() == 2 ? 20 : 8, 0.3, 0.1, 0.3, 0.0);
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.2, at.z, 8, 0.3, 0.2, 0.3, 0.02);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.5F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.3F, 0.5F);
			}
			case 0 -> {
				level.sendParticles(Fx.mote(WHITE, 0.4F), at.x, at.y + 0.1, at.z, 3, 0.2, 0.0, 0.2, 0.0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 1.0F);
			}
			default -> {
			}
		}
	}
}
