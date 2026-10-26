package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.236) Old Ambrose, the Warden: a grandfather of a wizard, endlessly patient, who calls his keeper "little one" and
 * would rather mend a hurt than win a fight. Andy: "some just give you buffs ... the ones with personalities can kick in on
 * their own to assist you at times similar to how the symbiote operates". Ward, blessing and healing; a broad open hand
 * twice yours behind the shoulder, soft green.
 * <ul>
 * <li><b>Aegis</b> (level 1): a shimmering ward for ten seconds that soaks the next hit of {@link #AEGIS_MIN_HIT} or more
 * whole. <b>Steady hands</b> (2): while on, knockback on you is halved and the bow draws steady ({@link #steady}).
 * <b>Battle blessing</b> (3): strength, speed or resistance for twenty seconds by what you face. <b>Catch me</b> (1): slow
 * fall; on his own he catches a long fall and hauls you out of lava or fire. <b>Mending touch</b> (4): heals a little each
 * second through {@link PowersCompat#mend}, until you are whole.</li>
 * <li>On his own: the aegis against a blow that would leave you under a third ({@link #hurt}), the catch, the blessing as a
 * fight begins, and the mending below half health from bond {@value #MEND_BOND} ({@link #tick}). Advice in battle comes from the core.</li>
 * </ul>
 * Spirit armour and Spirit ride, which every spirit has, are in {@link HandExtras}.
 */
public final class Ambrose extends Spirit {
	/** Aegis: how long the ward lasts (ticks), and the least gap between his own wards. */
	static final int AEGIS_TICKS = 200, AEGIS_EVERY = 600;
	/** Aegis: the least hit it soaks, and what it costs when he casts it himself. */
	static final float AEGIS_MIN_HIT = 4.0F, AEGIS_AUTO_COST = 12.0F;
	/** The share of your health a blow must leave you under for him to ward it himself. */
	static final float AEGIS_AUTO_BELOW = 1.0F / 3.0F;
	/** Steady hands: mana a second. */
	static final float STEADY_COST_PER_SECOND = 2.0F;
	/** Blessing: how long it lasts (ticks), the level from which it is stronger, and the least gap between his own. */
	static final int BLESS_TICKS = 400, BLESS_BOOST_LEVEL = 10, BLESS_EVERY = 400;
	/** Blessing: what it costs when he casts it himself. */
	static final float BLESS_AUTO_COST = 16.0F;
	/** A fight is over when no foe has been near for this many ticks; the blessing comes when it starts, with a foe this near. */
	static final int FIGHT_GAP = 200;
	static final double FIGHT_RANGE = 8.0;
	/** Catch me: a fall this hard (damage) is caught on his own, and it costs this. */
	static final float CATCH_FALL = 5.0F, CATCH_COST = 10.0F;
	/** Catch me: the least gap between his own catches, and the slow fall (ticks) he gives, and when it is cast. */
	static final int CATCH_EVERY = 300, CATCH_SLOW_AUTO = 60, CATCH_SLOW_CAST = 200;
	/** Out of lava or fire: how far he hauls you back (blocks), the fire resistance he leaves you (ticks). */
	static final double HAUL = 3.0;
	static final int HAUL_FIRE_PROOF = 60;
	/** Mending: mana and health each second, and the share of your health below which his own mending starts. */
	static final float MEND_COST_PER_SECOND = 3.0F, MEND_PER_SECOND = 0.5F, MEND_AUTO_BELOW = 0.5F, MEND_AUTO_COST = 3.0F;
	/** Mending: the bond from which he starts it himself, and the least gap between his own. */
	static final int MEND_BOND = 40, MEND_AUTO_EVERY = 600;
	/** The chance a cast move is remarked on. */
	static final float MOVE_SAY = 0.15F;

	/** A fight under way: when a foe was last near, and whether the blessing has been tried. */
	private record Fight(long seen, boolean blessed) {
	}

	/** Game time each player's aegis ward ends. */
	private static final Map<UUID, Long> WARD = new HashMap<>();
	/** Players whose spirit steadies their hands. */
	private static final Map<UUID, Boolean> STEADY = new HashMap<>();
	/** Players being mended: true if he began it himself. */
	private static final Map<UUID, Boolean> MENDING = new HashMap<>();
	/** Fights under way. */
	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
	/** The last safe, solid place each player stood (where "back the way you came" is). */
	private static final Map<UUID, Vec3> TRAIL = new HashMap<>();

	/** What a small move does when it is cast. */
	@FunctionalInterface
	interface Cast {
		boolean cast(ServerPlayer player, SpiritState state, int charge);
	}

	/** One of Ambrose's (or the shared extras') moves: its numbers and its cast. */
	static final class Def implements Move {
		private final String id;
		private final float cost;
		private final int cooldown, level, pose, intent;
		private final Cast cast;

		Def(String id, float cost, int cooldown, int level, int pose, int intent, Cast cast) {
			this.id = id;
			this.cost = cost;
			this.cooldown = cooldown;
			this.level = level;
			this.pose = pose;
			this.intent = intent;
			this.cast = cast;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public float cost() {
			return cost;
		}

		@Override
		public int cooldown() {
			return cooldown;
		}

		@Override
		public int level() {
			return level;
		}

		@Override
		public int pose() {
			return pose;
		}

		@Override
		public int intent() {
			return intent;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			boolean did = cast.cast(player, state, charge);
			if (did && player.getRandom().nextFloat() < MOVE_SAY) {
				SpiritVoice.say(player, "move");
			}
			return did;
		}
	}

	/** Its own moves, after the shared hands. */
	public static final List<Move> MOVES = new ArrayList<>();

	static {
		MOVES.add(new Def("aegis", 12.0F, 200, 1, Poses.AEGIS, Mana.WARD, Ambrose::aegis));
		MOVES.add(new Def("catch_me", CATCH_COST, 300, 1, Poses.CATCH_ME, Mana.WARD, Ambrose::catchMe));
		MOVES.add(new Def("steady", STEADY_COST_PER_SECOND, 20, 2, Poses.HOLD, Mana.WARD, Ambrose::steadyHands));
		MOVES.add(new Def("bless", 16.0F, 400, 3, Poses.BLESS, Mana.WILL, Ambrose::bless));
		MOVES.add(new Def("mend", MEND_COST_PER_SECOND, 20, 4, Poses.MEND, Mana.WARD, Ambrose::mendingTouch));
	}

	Ambrose() {
		super(Spirits.AMBROSE, "ambrose", 0xFF9BE08F, 2.0F);
	}

	static void register() {
		for (Move move : MOVES) {
			Moves.register(move);
		}
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Ambrose::soak);
		ServerTickEvents.END_SERVER_TICK.register(Ambrose::tickAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WARD.clear();
			STEADY.clear();
			MENDING.clear();
			FIGHTS.clear();
			TRAIL.clear();
		});
	}

	/** A player left: forget what is kept for them. */
	static void dropState(UUID id) {
		WARD.remove(id);
		STEADY.remove(id);
		MENDING.remove(id);
		FIGHTS.remove(id);
		TRAIL.remove(id);
	}

	@Override
	public List<Move> moves() {
		return MOVES;
	}

	/** Whether {@code player}'s spirit is steadying their hands (halved knockback, no bow jitter). */
	public static boolean steady(ServerPlayer player) {
		return STEADY.containsKey(player.getUUID()) && Spirits.out(player);
	}

	/** Whether {@code player} has an aegis ward up now. */
	public static boolean warded(ServerPlayer player) {
		Long until = WARD.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until && Spirits.out(player);
	}

	// ---- on his own -------------------------------------------------------------------------------------------------

	/**
	 * A blow is about to land. From bond 20, on his own: a long fall is caught (no damage, a slow fall), the lava or fire you
	 * stand in is hauled out of, and a blow that would leave you under a third of your health is warded whole.
	 */
	@Override
	public boolean hurt(ServerPlayer player, SpiritState state, DamageSource source, float amount) {
		if (amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || warded(player)) {
			return false;
		}
		long now = player.level().getGameTime();
		if (source.is(DamageTypeTags.IS_FALL)) {
			return amount >= CATCH_FALL && cooled(player, "fall", now) && catchFall(player, now);
		}
		if (source.is(DamageTypes.LAVA) || source.is(DamageTypes.IN_FIRE)) {
			return haulOut(player, now);
		}
		if (player.getHealth() - amount < player.getMaxHealth() * AEGIS_AUTO_BELOW && cooled(player, "aegis", now)
			&& Spirits.act(player, Spirits.AMBROSE, Spirits.FLASH_WARD, AEGIS_AUTO_COST)) {
			cool(player, "aegis", now, AEGIS_EVERY);
			Spirits.show(player, Poses.AEGIS);
			shimmer((ServerLevel)player.level(), player);
			return true;
		}
		return false;
	}

	/** Every ten ticks: the trail, the way out of lava or fire, the blessing at a fight's start, the mending when hurt. */
	@Override
	public void tick(ServerPlayer player, SpiritState state, @Nullable LivingEntity threat, long now) {
		UUID id = player.getUUID();
		if (player.isInLava() || standingInFire(player)) {
			haulOut(player, now);
		} else if (player.onGround() && !player.isInWater()) {
			TRAIL.put(id, player.position());
		}
		if (threat != null) {
			Fight fight = FIGHTS.get(id);
			if (fight == null || now - fight.seen() > FIGHT_GAP) {
				fight = new Fight(now, false);
			}
			boolean blessed = fight.blessed();
			if (!blessed && threat.distanceTo(player) <= FIGHT_RANGE) {
				blessed = true;
				if (cooled(player, "bless", now) && Spirits.act(player, Spirits.AMBROSE, Spirits.FLASH_ACT, BLESS_AUTO_COST)) {
					cool(player, "bless", now, BLESS_EVERY);
					Spirits.show(player, Poses.BLESS);
					blessWith(player, state, threat);
				}
			}
			FIGHTS.put(id, new Fight(now, blessed));
		}
		if (state.bond() >= MEND_BOND && !MENDING.containsKey(id) && player.getHealth() < player.getMaxHealth() * MEND_AUTO_BELOW
			&& cooled(player, "mend", now) && Spirits.act(player, Spirits.AMBROSE, Spirits.FLASH_ACT, MEND_AUTO_COST)) {
			cool(player, "mend", now, MEND_AUTO_EVERY);
			MENDING.put(id, true);
			Spirits.show(player, Poses.MEND);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
		}
	}

	// ---- aegis ------------------------------------------------------------------------------------------------------

	private static boolean aegis(ServerPlayer player, SpiritState state, int charge) {
		WARD.put(player.getUUID(), player.level().getGameTime() + AEGIS_TICKS);
		shimmer((ServerLevel)player.level(), player);
		return true;
	}

	private static void shimmer(ServerLevel level, ServerPlayer player) {
		Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.6, 0.8, 0.6, 0.02);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.5, 0.7, 0.5, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.1F);
	}

	/** A blow is about to land on a warded player: soaked whole if it is hard enough. True lets it through. */
	private static boolean soak(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayer player) || amount < AEGIS_MIN_HIT || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| !warded(player) || !(player.level() instanceof ServerLevel level)) {
			return true;
		}
		WARD.remove(player.getUUID());
		player.setAttached(Spirits.FLASH, level.getGameTime() * 8L + Spirits.FLASH_WARD);
		Spirits.show(player, Poses.AEGIS);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.7, 0.5, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.aegis.soaked").withStyle(ChatFormatting.GREEN));
		return false;
	}

	// ---- catch me ---------------------------------------------------------------------------------------------------

	/** The cast: a slow fall for ten seconds, and whatever fall is under way forgotten. */
	private static boolean catchMe(ServerPlayer player, SpiritState state, int charge) {
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, CATCH_SLOW_CAST, 0, true, false, true));
		player.resetFallDistance();
		ServerLevel level = (ServerLevel)player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.4, 0.1, 0.4, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
		return true;
	}

	/** A hard fall, caught: its damage cancelled, a slow fall, a word. */
	private boolean catchFall(ServerPlayer player, long now) {
		if (!Spirits.act(player, Spirits.AMBROSE, Spirits.FLASH_WARD, CATCH_COST)) {
			return false;
		}
		cool(player, "fall", now, CATCH_EVERY);
		Spirits.show(player, Poses.CATCH_ME);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, CATCH_SLOW_AUTO, 0, true, false, true));
		ServerLevel level = (ServerLevel)player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 16, 0.4, 0.1, 0.4, 0.03);
		Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 0.5, player.getZ(), 14, 0.5, 0.4, 0.5, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.ambrose.catch").withStyle(ChatFormatting.GREEN));
		return true;
	}

	private static boolean standingInFire(ServerPlayer player) {
		return player.level().getBlockState(player.blockPosition()).is(BlockTags.FIRE);
	}

	/** Lava or fire round you: hauled {@link #HAUL} blocks back the way you came, and put out. */
	private boolean haulOut(ServerPlayer player, long now) {
		if (!cooled(player, "fire", now) || !Spirits.act(player, Spirits.AMBROSE, Spirits.FLASH_WARD, CATCH_COST)) {
			return false;
		}
		cool(player, "fire", now, CATCH_EVERY);
		Spirits.show(player, Poses.CATCH_ME);
		ServerLevel level = (ServerLevel)player.level();
		Vec3 pos = player.position();
		Vec3 trail = TRAIL.get(player.getUUID());
		Vec3 back = trail == null ? Vec3.ZERO : new Vec3(trail.x - pos.x, 0.0, trail.z - pos.z);
		if (back.lengthSqr() < 0.25) {
			Vec3 look = player.getLookAngle();
			back = new Vec3(-look.x, 0.0, -look.z);
		}
		back = back.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : back.normalize();
		boolean moved = false;
		for (double d = HAUL; d >= 1.0 && !moved; d -= 1.0) {
			for (int dy : new int[] {0, 1, -1, 2}) {
				double x = pos.x + back.x * d, y = pos.y + dy, z = pos.z + back.z * d;
				if (safe(level, player, x, y, z)) {
					level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 1.0, pos.z, 12, 0.3, 0.5, 0.3, 0.05);
					player.teleportTo(x, y, z);
					moved = true;
					break;
				}
			}
		}
		if (!moved) {
			// (nowhere to set you down: he heaves you up and back)
			player.setDeltaMovement(back.x * 0.6, 0.7, back.z * 0.6);
			player.syncVelocity = true;
		}
		player.clearFire();
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, HAUL_FIRE_PROOF, 0, true, false, true));
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.ambrose.catch").withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Whether {@code player} could stand at this spot: room for them, no lava or fire in it, and something solid to stand on. */
	private static boolean safe(ServerLevel level, ServerPlayer player, double x, double y, double z) {
		Vec3 pos = player.position();
		AABB box = player.getBoundingBox().move(x - pos.x, y - pos.y, z - pos.z);
		if (!level.noCollision(player, box)) {
			return false;
		}
		BlockPos feet = BlockPos.containing(x, y, z), head = feet.above(), below = feet.below();
		for (BlockPos at : new BlockPos[] {feet, head}) {
			BlockState state = level.getBlockState(at);
			if (state.is(BlockTags.FIRE) || level.getFluidState(at).is(FluidTags.LAVA)) {
				return false;
			}
		}
		BlockState floor = level.getBlockState(below);
		return floor.isFaceSturdy(level, below, Direction.UP) && !level.getFluidState(below).is(FluidTags.LAVA);
	}

	// ---- steady hands -----------------------------------------------------------------------------------------------

	private static boolean steadyHands(ServerPlayer player, SpiritState state, int charge) {
		if (STEADY.remove(player.getUUID()) != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.steady.off").withStyle(ChatFormatting.GRAY));
			return false;
		}
		STEADY.put(player.getUUID(), true);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.steady.on").withStyle(ChatFormatting.GREEN));
		return true;
	}

	// ---- blessing ---------------------------------------------------------------------------------------------------

	private static boolean bless(ServerPlayer player, SpiritState state, int charge) {
		LivingEntity foe = Arcana.sighted(player);
		if (foe == null || !SpiritFoes.foe(player, foe, false)) {
			foe = null;
			double best = Double.MAX_VALUE;
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(Spirits.SEE), m -> m.isAlive() && m.getTarget() == player)) {
				double d = mob.distanceToSqr(player);
				if (d < best) {
					best = d;
					foe = mob;
				}
			}
		}
		blessWith(player, state, foe);
		return true;
	}

	/** The blessing for what {@code foe} is (null: nothing in particular): strength, speed or resistance. */
	private static void blessWith(ServerPlayer player, SpiritState state, @Nullable LivingEntity foe) {
		String key = foe == null ? "foe" : Spirits.foeKey(player, foe);
		Holder<MobEffect> effect;
		String name;
		switch (key) {
			case "archer", "many" -> {
				effect = MobEffects.SPEED;
				name = "speed";
			}
			case "caster", "creeper" -> {
				effect = MobEffects.RESISTANCE;
				name = "resistance";
			}
			default -> {
				effect = MobEffects.STRENGTH;
				name = "strength";
			}
		}
		int amp = state.level() >= BLESS_BOOST_LEVEL ? 1 : 0;
		player.addEffect(new MobEffectInstance(effect, BLESS_TICKS, amp, true, true, true));
		ServerLevel level = (ServerLevel)player.level();
		Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.6, 0.9, 0.6, 0.04);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.5, 0.8, 0.5, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.bless." + name).withStyle(ChatFormatting.GREEN));
	}

	// ---- mending ----------------------------------------------------------------------------------------------------

	private static boolean mendingTouch(ServerPlayer player, SpiritState state, int charge) {
		if (MENDING.remove(player.getUUID()) != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.mend.off").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (player.getHealth() >= player.getMaxHealth()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.mend.full").withStyle(ChatFormatting.GRAY));
			return false;
		}
		MENDING.put(player.getUUID(), false);
		PowersCompat.closeCut(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.mend.on").withStyle(ChatFormatting.GREEN));
		return true;
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	private static void tickAll(MinecraftServer server) {
		if (WARD.isEmpty() && STEADY.isEmpty() && MENDING.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Long>> it = WARD.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive() || owner.level().getGameTime() > entry.getValue() || !Spirits.out(owner)) {
				it.remove();
			} else if (now % 10 == 0) {
				Mana.sendSeen((ServerLevel)owner.level(), Mana.MOTE, owner.getX(), owner.getY() + 1.0, owner.getZ(), 3, 0.5, 0.8, 0.5, 0.01);
			}
		}
		for (Iterator<UUID> it = STEADY.keySet().iterator(); it.hasNext();) {
			ServerPlayer owner = server.getPlayerList().getPlayer(it.next());
			if (owner == null || !owner.isAlive() || !Spirits.out(owner)) {
				it.remove();
				continue;
			}
			if (now % 20 == 0 && !Mana.pay(owner, STEADY_COST_PER_SECOND, Mana.WARD)) {
				it.remove();
				owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.steady.off").withStyle(ChatFormatting.GRAY));
				continue;
			}
			holdPose(owner, Poses.HOLD, now);
		}
		for (Iterator<UUID> it = MENDING.keySet().iterator(); it.hasNext();) {
			ServerPlayer owner = server.getPlayerList().getPlayer(it.next());
			if (owner == null || !owner.isAlive() || !Spirits.out(owner)) {
				it.remove();
				continue;
			}
			if (now % 20 == 0) {
				if (!Mana.canPay(owner, MEND_COST_PER_SECOND) || PowersCompat.mend(owner, MEND_PER_SECOND) <= 0.0F) {
					it.remove();
					owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.mend.done").withStyle(ChatFormatting.GREEN));
					continue;
				}
				Mana.pay(owner, MEND_COST_PER_SECOND, Mana.WARD);
				PowersCompat.closeCut(owner);
				ServerLevel level = (ServerLevel)owner.level();
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, owner.getX(), owner.getY() + 1.0, owner.getZ(), 6, 0.4, 0.6, 0.4, 0.0);
				Mana.sendSeen(level, Mana.MOTE, owner.getX(), owner.getY() + 1.0, owner.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
			}
			holdPose(owner, Poses.MEND, now);
		}
	}

	/** Keeps the arms in {@code pose} while a toggle is on (the client lets a pose go after 16 ticks). */
	static void holdPose(ServerPlayer owner, int pose, long now) {
		if (now % 12 != 0) {
			return;
		}
		SpiritState state = Spirits.state(owner);
		if (state != null && (state.pose() == Poses.IDLE || state.pose() == pose)) {
			Spirits.show(owner, pose);
		}
	}
}
