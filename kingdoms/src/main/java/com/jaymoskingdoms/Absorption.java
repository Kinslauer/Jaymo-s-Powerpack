package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.571) Andy: "sorcerers should all be able to absorb their elements like the blood sorcerer. i especially want a
 * cone of absorption in front of fire sorcerers that can put out flames and feed their reserve". Every Sorcerer element
 * but blood (which has {@link Hemomancy} and {@link BloodBlasts}) now does what the Blood Sorcerer does with its blood:
 * <ul>
 * <li><b>A reserve</b> of their element ({@link #RESERVE}, the bar by the hotbar in the element's colour). It pays for
 * their {@code J} keys before their mana does ({@link #payKey}, from {@link Mana#cast}), each point worth
 * {@link #MANA_WORTH} mana, and it's what their blasts are thrown from.</li>
 * <li><b>Absorbing</b>, {@code J} held (not sneaking) begun looking at a source of their element: drawn to their hands
 * in a thread of it, far faster than the slow trickle they take just standing in it ({@link #trickle}). A gathering
 * hold never fires. <b>Fire</b> draws from a whole cone in front of them instead ({@link #cone}): fires go out, lit
 * campfires and candles are snuffed, burning creatures (and the Sorcerer) stop burning, fireballs flying through it are
 * swallowed, blazes and magma cubes are drained, and lava gives up some heat (but stays lava). Water: water, a cauldron,
 * the rain. Ice: ice (thawed a step), snow, the frozen. Earth: natural stone, dirt and sand. Air: the open sky, looked
 * up into (richer the higher), and breezes. Lightning: a storm's sky, lightning rods, redstone blocks. Restoration:
 * flowers, leaves, saplings, crops and amethyst. Death: the undead (drained), the downed, bodies, soul sand and bone,
 * and every death near them sends its soul to them.</li>
 * <li><b>Element blasts:</b> {@code J} held empty-handed looking at anything else, for three seconds (or at once after
 * a double tap, {@link #HOLD_TICKS}): the reserve is
 * thrown as a barrage of small blasts of the element, hands taking turns, as the blood barrage. Each hurts and carries
 * a touch of the element; one in {@link #ELEMENT_ONE_IN} bursts with it in full ({@link Spellcraft#strike}).
 * Restoration's blasts heal the living they hit instead and sear only monsters and the undead.</li>
 * <li>A <b>tap</b> of J is still the Imbue (or Restoration's Mend, or lightning's Storm Ring), and sneak and J the keys.</li>
 * </ul>
 */
public final class Absorption {
	/** The most a reserve holds; the mana each point of it stands in for, paying a key. */
	public static final float RESERVE_MAX = 20.0F;
	static final float MANA_WORTH = 2.5F;
	/** Let go before this (ticks) and it was a tap. */
	static final int TAP_TICKS = 6;
	/** Gathering: how often (ticks), how far you look, the most a pulse. */
	static final int GATHER_EVERY = 5;
	static final double GATHER_RANGE = 8.0;
	static final float GATHER_MOST = 1.5F;

	/** Fire's cone: how far, its half-width (degrees), the most sources it takes a pulse. */
	static final double CONE_RANGE = 8.0, CONE_HALF_ANGLE = 35.0;
	static final int CONE_MOST = 8;
	/** What each source in the cone gives: a fire block, a campfire, a candle, a burning creature, a fireball, a blaze's drain, lava. */
	static final float FIRE_BLOCK = 0.5F, CAMPFIRE = 1.0F, CANDLE = 0.25F, BURNING = 1.0F, FIREBALL = 3.0F, BLAZE = 1.0F, LAVA = 0.3F;
	/** A blaze or magma cube in the cone: how hard drawing its fire out hurts it (a pulse). */
	static final float BLAZE_DRAIN = 1.0F;
	/** The most lava blocks a pulse draws heat from. */
	static final int LAVA_MOST = 2;

	/** The trickle a Sorcerer takes a second standing in their element (earth's slower: there's always ground). */
	static final float TRICKLE = 0.25F, EARTH_TRICKLE = 0.1F;
	/** Death: what a death near a Death Sorcerer gives, and how near. */
	static final float DEATH_SOUL = 2.0F;
	static final double DEATH_RANGE = 12.0;

	/** The barrage: a blast every so many ticks, what each costs from the reserve. */
	static final int EVERY = 5;
	/**
	 * (Andy: "this requires a 3s hold with no indicator, or double tap and hold to be instant") A plain hold empty-handed
	 * throws only after {@code HOLD_TICKS}, with nothing shown till then; a tap and a press again within
	 * {@code DOUBLE_TAP} ticks, held, throws at once. (An empty hand's tap waits out that window before it Imbues/Mends.)
	 */
	static final int HOLD_TICKS = 60, DOUBLE_TAP = 8;
	static final float BLAST_COST = 1.0F;
	/** A blast: its damage, speed (blocks a tick), life (ticks), spread, knockback; restoration's heal. */
	static final float BLAST_DAMAGE = 4.0F, BLAST_HEAL = 2.0F;
	static final double BLAST_SPEED = 1.5, SPREAD = 0.04, KNOCKBACK = 0.3;
	static final int BLAST_LIFE = 30;
	/** One blast in this many bursts with the element in full; the rest carry a touch. */
	static final int ELEMENT_ONE_IN = 3;

	/** A Sorcerer's reserve of their element (not blood's: {@link Hemomancy#RESERVOIR}). Not kept over a restart. */
	public static final AttachmentType<Float> RESERVE = AttachmentRegistry.create(JaymosKingdoms.id("element_reserve"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	/** Player → server: J down or up, for a Sorcerer of any element but blood, not sneaking. */
	public record Hold(boolean down) implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosKingdoms.id("element_hold"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hold> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.down), buf -> new Hold(buf.readBoolean()));

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	private static final class Holding {
		final long from;
		int fired;
		boolean counted, dry, gathering;
		/** Begun with a double tap: throws at once, never gathers. */
		final boolean quick;

		Holding(long from, boolean quick) {
			this.from = from;
			this.quick = quick;
		}
	}

	private static final class Blast {
		final ServerLevel level;
		final UUID owner;
		final String element;
		Vec3 pos, velocity;
		final float damage;
		int age;
		final Set<Integer> struck = new HashSet<>();

		Blast(ServerLevel level, UUID owner, String element, Vec3 pos, Vec3 velocity, float damage) {
			this.level = level;
			this.owner = owner;
			this.element = element;
			this.pos = pos;
			this.velocity = velocity;
			this.damage = damage;
		}
	}

	private static final Map<UUID, Holding> HOLDING = new HashMap<>();
	private static final List<Blast> BLASTS = new ArrayList<>();
	/** An empty hand's tap, by player: when it was let go (its Imbue waits to see if a second press makes it a double tap). */
	private static final Map<UUID, Long> TAPPED = new HashMap<>();

	private Absorption() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, context) -> hold(context.player(), payload.down()));
		ServerTickEvents.END_SERVER_TICK.register(Absorption::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOLDING.clear();
			BLASTS.clear();
			TAPPED.clear();
		});
		// death: every soul let go near a Death Sorcerer goes to them
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level) || entity instanceof ArmorStand) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (player != entity && "death".equals(element(player)) && player.isAlive() && player.distanceTo(entity) <= DEATH_RANGE
					&& reserve(player) < RESERVE_MAX) {
					add(player, DEATH_SOUL);
					stream(level, entity.getBoundingBox().getCenter(), player.getBoundingBox().getCenter(), ParticleTypes.SOUL, 0.0);
				}
			}
		});
	}

	// ---- who and the reserve -------------------------------------------------------------------------------------------

	/** A Sorcerer's element, any but blood's (blood keeps its own pool); "" for anyone else. */
	public static String element(Player player) {
		if (!Classes.is(player, PlayerClass.SORCERER)) {
			return "";
		}
		String option = Classes.optionOf(player, PlayerClass.SORCERER);
		return option == null || option.equals("blood") || option.equals("wild") ? "" : option;
	}

	/** A Sorcerer who absorbs their element here (every element but blood). */
	public static boolean absorber(Player player) {
		return !element(player).isEmpty();
	}

	/** The reserve, on either side. */
	public static float reserve(Player player) {
		Float f = player.getAttached(RESERVE);
		return f == null ? 0.0F : f;
	}

	static void setReserve(Player player, float reserve) {
		if (reserve <= 0.0F) {
			player.removeAttached(RESERVE);
		} else {
			player.setAttached(RESERVE, Math.min(RESERVE_MAX, reserve));
		}
	}

	private static void add(Player player, float amount) {
		if (amount > 0.0F) {
			setReserve(player, reserve(player) + amount);
		}
	}

	/**
	 * A key of {@code mana} cost paid from the reserve instead of mana, when it holds enough ({@link Mana#cast} asks
	 * first). False, and nothing taken, when it doesn't (mana pays the whole of it).
	 */
	static boolean payKey(ServerPlayer player, float mana) {
		if (player.isCreative() || !absorber(player) || mana <= 0.0F) {
			return false;
		}
		float points = mana / MANA_WORTH, reserve = reserve(player);
		if (reserve < points) {
			return false;
		}
		setReserve(player, reserve - points);
		return true;
	}

	/** Holding J to absorb or throw (on the server). */
	static boolean holding(Player player) {
		return HOLDING.containsKey(player.getUUID());
	}

	// ---- the hold ------------------------------------------------------------------------------------------------------

	private static void hold(ServerPlayer player, boolean down) {
		long now = player.level().getGameTime();
		if (down) {
			if (absorber(player) && player.isAlive()) {
				Long tapped = TAPPED.remove(player.getUUID());
				boolean quick = tapped != null && now - tapped <= DOUBLE_TAP && player.getMainHandItem().isEmpty();
				if (tapped != null && !quick) {
					Spellcraft.imbue(player, false);
				}
				HOLDING.put(player.getUUID(), new Holding(now, quick));
			} else {
				Spellcraft.imbue(player, false);
			}
			return;
		}
		Holding h = HOLDING.remove(player.getUUID());
		if (h != null && !h.quick && now - h.from < TAP_TICKS) {
			if (player.getMainHandItem().isEmpty()) {
				// it may be the first of a double tap: the Imbue waits for the window to close (tick)
				TAPPED.put(player.getUUID(), now);
			} else {
				Spellcraft.imbue(player, false);
			}
		} else if (h != null && h.fired > 0 && player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 1.6F);
		}
	}

	private static void tick(MinecraftServer server) {
		if (!TAPPED.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Long>> it = TAPPED.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Long> e = it.next();
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				if (player == null) {
					it.remove();
				} else if (player.level().getGameTime() - e.getValue() > DOUBLE_TAP) {
					it.remove();
					Spellcraft.imbue(player, false);
				}
			}
		}
		for (Iterator<Map.Entry<UUID, Holding>> it = HOLDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Holding> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || !absorber(player) || !(player.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			tickHold(level, player, e.getValue());
		}
		for (Iterator<Blast> it = BLASTS.iterator(); it.hasNext(); ) {
			Blast b = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(b.owner);
			if (owner == null || owner.level() != b.level || !fly(owner, b)) {
				it.remove();
			}
		}
		if (server.getTickCount() % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && !holding(player) && player.level() instanceof ServerLevel level && absorber(player)
					&& reserve(player) < RESERVE_MAX) {
					add(player, trickle(level, player, element(player)));
				}
			}
		}
	}

	private static void tickHold(ServerLevel level, ServerPlayer player, Holding h) {
		long held = level.getGameTime() - h.from;
		String element = element(player);
		Vec3 look = player.getLookAngle();
		if (h.quick) {
			// a double tap: straight to the throwing
			if (player.getMainHandItem().isEmpty()) {
				fire(level, player, h, element, look, held);
			}
			return;
		}
		if (held < TAP_TICKS) {
			return;
		}
		if (held == TAP_TICKS) {
			// as the blood: what the hold is for is settled as it begins (looking at a source: it absorbs, and never fires)
			h.gathering = gather(level, player, element, look);
			if (h.gathering) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), gatherSound(element), SoundSource.PLAYERS, 0.6F, 0.8F);
			}
			if (!h.gathering && !player.getMainHandItem().isEmpty()) {
				return;
			}
		}
		if (h.gathering) {
			if (held > TAP_TICKS && (held - TAP_TICKS) % GATHER_EVERY == 0) {
				gather(level, player, element, look);
			}
			return;
		}
		if (!player.getMainHandItem().isEmpty() || held < HOLD_TICKS) {
			// a plain hold throws only after three seconds, with nothing shown till then
			return;
		}
		fire(level, player, h, element, look, held - HOLD_TICKS);
	}

	/** The barrage, {@code firing} ticks into it: the hands take turns, a blast every {@link #EVERY} ticks. */
	private static void fire(ServerLevel level, ServerPlayer player, Holding h, String element, Vec3 look, long firing) {
		float reserve = reserve(player);
		ParticleOptions mote = Spellcraft.particle(element);
		boolean next = (firing / EVERY) % 2 == 0;
		Vec3 forming = palm(player, look, next);
		level.sendParticles(mote, forming.x, forming.y, forming.z, 1, 0.04, 0.04, 0.04, 0.0);
		if (firing % EVERY != 0) {
			return;
		}
		if (!player.isCreative() && reserve < BLAST_COST) {
			if (!h.dry) {
				h.dry = true;
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 0.6F);
			}
			return;
		}
		if (!h.counted) {
			// (death's is forbidden: the spirit stays your hand when the Grue is near)
			if (ForbiddenArts.forbidden(element) && ForbiddenArts.stayed(player)) {
				return;
			}
			h.counted = true;
			// a barrage counts as one cast (a realm's frown on sorcery; death's corruption and crime)
			Spellcraft.afterCast(player, level, element);
		}
		h.dry = false;
		if (!player.isCreative()) {
			setReserve(player, reserve - BLAST_COST);
		}
		boolean left = !next;
		RandomSource random = level.getRandom();
		Vec3 aim = look.add((random.nextDouble() - 0.5) * SPREAD * 2, (random.nextDouble() - 0.5) * SPREAD * 2,
			(random.nextDouble() - 0.5) * SPREAD * 2).normalize();
		BLASTS.add(new Blast(level, player.getUUID(), element, palm(player, look, left), aim.scale(BLAST_SPEED), BLAST_DAMAGE));
		h.fired++;
		player.swing(left ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Spellcraft.sound(element), SoundSource.PLAYERS, 0.35F,
			1.5F + random.nextFloat() * 0.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.3F, 0.9F);
	}

	// ---- absorbing -----------------------------------------------------------------------------------------------------

	/**
	 * One pulse of absorbing what {@code player} looks at, into their reserve. True if there was something of their
	 * element there (fire: anything burning in the cone, put out even when the reserve is full).
	 */
	private static boolean gather(ServerLevel level, ServerPlayer player, String element, Vec3 look) {
		Vec3 hands = player.getEyePosition().add(look.scale(0.5)).add(0.0, -0.45, 0.0);
		float room = Math.min(GATHER_MOST, RESERVE_MAX - reserve(player));
		if (element.equals("fire")) {
			float drawn = cone(level, player, look, hands);
			if (drawn < 0.0F) {
				return false;
			}
			add(player, Math.min(room, drawn));
			return true;
		}
		if (room <= 0.0F) {
			return false;
		}
		float drawn = spot(level, player, element, look, hands);
		if (drawn <= 0.0F) {
			return false;
		}
		add(player, Math.min(room, drawn));
		return true;
	}

	/** Where a block or creature sits, as seen from the eye: within the fire cone in front of {@code look}. */
	private static boolean inCone(Vec3 eye, Vec3 look, Vec3 at) {
		Vec3 to = at.subtract(eye);
		double d = to.length();
		if (d > CONE_RANGE) {
			return false;
		}
		if (d < 1.0) {
			// at arm's length everything's in it
			return true;
		}
		return to.scale(1.0 / d).dot(look) >= Math.cos(Math.toRadians(CONE_HALF_ANGLE));
	}

	private record Source(BlockPos pos, BlockState state, double distance) {
	}

	/**
	 * Fire's cone: everything burning in front of the Sorcerer, nearest first, up to {@link #CONE_MOST} a pulse, is put
	 * out and drawn to their hands. What it gives (it may be more than the reserve has room for), or -1 if nothing in the
	 * cone was burning.
	 */
	private static float cone(ServerLevel level, ServerPlayer player, Vec3 look, Vec3 hands) {
		Vec3 eye = player.getEyePosition();
		float drawn = 0.0F;
		int taken = 0;
		boolean any = false;
		// the Sorcerer's own flames first
		if (player.isOnFire()) {
			player.clearFire();
			drawn += BURNING;
			any = true;
			level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(0.5), player.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
		}
		// burning creatures, fireballs, blazes and magma cubes
		List<Entity> entities = new ArrayList<>(level.getEntities(player, player.getBoundingBox().inflate(CONE_RANGE),
			e -> e.isAlive() && inCone(eye, look, e.getBoundingBox().getCenter()) && player.hasLineOfSight(e)));
		entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
		for (Entity e : entities) {
			if (taken >= CONE_MOST) {
				break;
			}
			Vec3 at = e.getBoundingBox().getCenter();
			float got = 0.0F;
			if (e instanceof Fireball) {
				// swallowed in flight
				got = FIREBALL;
				e.discard();
			} else if ((e instanceof Blaze || e instanceof MagmaCube) && e instanceof LivingEntity l && !Thralls.refuses(player, l)) {
				l.hurtServer(level, level.damageSources().indirectMagic(player, player), BLAZE_DRAIN);
				got = BLAZE;
			} else if (e.isOnFire() && !e.fireImmune()) {
				e.clearFire();
				got = BURNING;
			}
			if (got > 0.0F) {
				drawn += got;
				taken++;
				any = true;
				stream(level, at, hands, ParticleTypes.FLAME, 0.0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.2F);
			}
		}
		// fires, campfires, candles, lava
		List<Source> sources = new ArrayList<>();
		int r = Mth.ceil(CONE_RANGE);
		BlockPos centre = BlockPos.containing(eye);
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			Vec3 at = Vec3.atCenterOf(pos);
			if (!inCone(eye, look, at)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (burning(state) || level.getFluidState(pos).is(FluidTags.LAVA)) {
				sources.add(new Source(pos.immutable(), state, at.distanceTo(eye)));
			}
		}
		sources.sort(Comparator.comparingDouble(Source::distance));
		int lava = 0;
		boolean hissed = false;
		for (Source s : sources) {
			if (taken >= CONE_MOST) {
				break;
			}
			if (!seen(level, player, eye, s.pos())) {
				continue;
			}
			BlockState state = s.state();
			Vec3 at = Vec3.atCenterOf(s.pos());
			float got;
			if (state.is(BlockTags.FIRE)) {
				level.removeBlock(s.pos(), false);
				got = FIRE_BLOCK;
			} else if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
				level.setBlock(s.pos(), state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL_IMMEDIATE);
				got = CAMPFIRE;
			} else if (state.getBlock() instanceof AbstractCandleBlock && state.getValue(AbstractCandleBlock.LIT)) {
				level.setBlock(s.pos(), state.setValue(AbstractCandleBlock.LIT, false), Block.UPDATE_ALL_IMMEDIATE);
				got = CANDLE;
			} else if (lava < LAVA_MOST) {
				// lava gives up some of its heat, and stays lava
				lava++;
				got = LAVA;
				at = at.add(0.0, 0.4, 0.0);
			} else {
				continue;
			}
			drawn += got;
			taken++;
			any = true;
			stream(level, at, hands, ParticleTypes.FLAME, 0.0);
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.2, 0.15, 0.2, 0.01);
			if (!hissed && got != LAVA) {
				hissed = true;
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
			}
		}
		if (!any) {
			return -1.0F;
		}
		// the cone itself: heat drawn in along it
		RandomSource random = level.getRandom();
		for (int i = 0; i < 6; i++) {
			double d = 2.0 + random.nextDouble() * (CONE_RANGE - 2.0);
			double spread = Math.tan(Math.toRadians(CONE_HALF_ANGLE)) * d;
			Vec3 p = eye.add(look.scale(d)).add((random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread,
				(random.nextDouble() - 0.5) * spread);
			Vec3 in = hands.subtract(p).normalize();
			level.sendParticles(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 0, in.x, in.y, in.z, 0.35);
		}
		if (random.nextInt(3) == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.4F, 0.6F);
		}
		return drawn;
	}

	/** A block fire's cone puts out: fire, a lit campfire, a lit candle (or candle cake). */
	private static boolean burning(BlockState state) {
		return state.is(BlockTags.FIRE)
			|| state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)
			|| state.getBlock() instanceof AbstractCandleBlock && state.getValue(AbstractCandleBlock.LIT);
	}

	/** {@code pos} in sight from the eye: nothing solid between (the block itself may be what's hit). */
	private static boolean seen(ServerLevel level, Player player, Vec3 eye, BlockPos pos) {
		BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	/**
	 * Every other element: one source in sight, the creature looked at first, else the block (or the sky). What it gives,
	 * 0 for nothing of the element there.
	 */
	private static float spot(ServerLevel level, ServerPlayer player, String element, Vec3 look, Vec3 hands) {
		ParticleOptions mote = Spellcraft.particle(element);
		LivingEntity sighted = Arcana.sighted(player);
		if (sighted != null && player.distanceTo(sighted) <= GATHER_RANGE) {
			float got = creature(level, player, element, sighted);
			if (got > 0.0F) {
				stream(level, sighted.getBoundingBox().getCenter(), hands, mote, 0.0);
				return got;
			}
		}
		// death: a body lying in sight
		if (element.equals("death")) {
			Entity corpse = Necromancy.corpseInSight(player, level, GATHER_RANGE);
			if (corpse != null) {
				stream(level, corpse.position().add(0.0, 0.3, 0.0), hands, ParticleTypes.SOUL, 0.0);
				return 0.75F;
			}
		}
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(GATHER_RANGE)), ClipContext.Block.OUTLINE,
			element.equals("water") ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			float got = sky(level, player, element, look);
			if (got > 0.0F) {
				Vec3 from = eye.add(look.scale(GATHER_RANGE));
				stream(level, from, hands, mote, 0.0);
			}
			return got;
		}
		BlockPos pos = hit.getBlockPos();
		float got = block(level, element, pos, level.getBlockState(pos));
		if (got > 0.0F) {
			stream(level, hit.getLocation(), hands, mote, 0.0);
		}
		return got;
	}

	/** What a creature looked at gives {@code element} (and what drawing it does to the creature). */
	private static float creature(ServerLevel level, ServerPlayer player, String element, LivingEntity e) {
		switch (element) {
			case "ice" -> {
				if (e.getTicksFrozen() > 0) {
					e.setTicksFrozen(Math.max(0, e.getTicksFrozen() - 60));
					return 1.0F;
				}
			}
			case "air" -> {
				if (e instanceof Breeze && !Thralls.refuses(player, e)) {
					e.hurtServer(level, level.damageSources().indirectMagic(player, player), BLAZE_DRAIN);
					return 1.0F;
				}
			}
			case "water" -> {
				if (e.isInWaterOrRain()) {
					return 0.5F;
				}
			}
			case "death" -> {
				if (Restoration.undead(e) && !Thralls.refuses(player, e)) {
					e.hurtServer(level, level.damageSources().indirectMagic(player, player), BLAZE_DRAIN);
					return 1.0F;
				}
				if (Downed.isDowned(e) || e.getHealth() < e.getMaxHealth() * 0.25F) {
					return 0.75F;
				}
			}
			default -> {
			}
		}
		return 0.0F;
	}

	/** What a block looked at gives {@code element} (and what drawing it does to the block). */
	private static float block(ServerLevel level, String element, BlockPos pos, BlockState state) {
		switch (element) {
			case "water" -> {
				if (state.is(Blocks.WATER_CAULDRON)) {
					LayeredCauldronBlock.lowerFillLevel(state, level, pos);
					return 1.5F;
				}
				if (level.getFluidState(pos).is(FluidTags.WATER)) {
					return 0.75F;
				}
			}
			case "ice" -> {
				// drawing the cold out thaws it a step
				if (state.is(Blocks.BLUE_ICE)) {
					level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState());
					return 1.5F;
				}
				if (state.is(Blocks.PACKED_ICE)) {
					level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
					return 1.5F;
				}
				if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
					level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
					return 1.25F;
				}
				if (state.is(Blocks.SNOW)) {
					int layers = state.getValue(SnowLayerBlock.LAYERS);
					level.setBlockAndUpdate(pos, layers > 1 ? state.setValue(SnowLayerBlock.LAYERS, layers - 1) : Blocks.AIR.defaultBlockState());
					return 0.4F;
				}
				if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
					level.removeBlock(pos, false);
					return 1.25F;
				}
			}
			case "earth" -> {
				if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT)
					|| state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)) {
					return 0.5F;
				}
			}
			case "lightning" -> {
				if (state.getBlock() instanceof LightningRodBlock) {
					return level.isThundering() ? 1.5F : 0.5F;
				}
				if (state.is(Blocks.REDSTONE_BLOCK)) {
					return 0.4F;
				}
			}
			case "restoration" -> {
				if (state.is(Blocks.BUDDING_AMETHYST) || state.is(Blocks.AMETHYST_CLUSTER) || state.is(Blocks.AMETHYST_BLOCK)) {
					return 0.6F;
				}
				if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CROPS)) {
					return 0.4F;
				}
			}
			case "death" -> {
				if (state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.BONE_BLOCK) || state.is(Blocks.WITHER_ROSE)
					|| state.getBlock() instanceof AbstractSkullBlock) {
					return 0.4F;
				}
			}
			default -> {
			}
		}
		return 0.0F;
	}

	/** Looking at nothing within reach: the open sky gives air (richer the higher), a storm's gives lightning, rain gives water. */
	private static float sky(ServerLevel level, ServerPlayer player, String element, Vec3 look) {
		BlockPos head = BlockPos.containing(player.getEyePosition());
		if (look.y < 0.5 || !level.canSeeSky(head)) {
			return 0.0F;
		}
		return switch (element) {
			case "air" -> 0.3F + 0.7F * Mth.clamp((float) (player.getY() - 64.0) / 128.0F, 0.0F, 1.0F);
			case "lightning" -> level.isThundering() ? 1.5F : 0.0F;
			case "water" -> level.isRainingAt(head) ? 0.5F : 0.0F;
			default -> 0.0F;
		};
	}

	/** The trickle a second for a Sorcerer standing in their element, not holding J. */
	private static float trickle(ServerLevel level, ServerPlayer player, String element) {
		BlockPos at = player.blockPosition();
		return switch (element) {
			case "fire" -> player.isOnFire() || player.isInLava() || near(level, at, 2, s -> burning(s) || s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK))
				? TRICKLE : 0.0F;
			case "water" -> player.isInWaterOrRain() ? TRICKLE : 0.0F;
			case "ice" -> player.isInPowderSnow || player.getTicksFrozen() > 0 || level.getBlockState(at).is(Blocks.SNOW)
				|| level.getBlockState(at.below()).is(BlockTags.ICE) || level.getBlockState(at.below()).is(BlockTags.SNOW) ? TRICKLE : 0.0F;
			case "earth" -> {
				BlockState under = level.getBlockState(at.below());
				yield player.onGround() && (under.is(BlockTags.BASE_STONE_OVERWORLD) || under.is(BlockTags.DIRT) || under.is(BlockTags.SAND))
					? EARTH_TRICKLE : 0.0F;
			}
			case "air" -> player.isFallFlying() || !player.onGround() && player.fallDistance > 2.0
				|| player.getY() > 128.0 && level.canSeeSky(at) ? TRICKLE : 0.0F;
			case "lightning" -> level.isThundering() && level.canSeeSky(at) ? TRICKLE : 0.0F;
			case "restoration" -> near(level, at, 2, s -> s.is(BlockTags.FLOWERS) || s.is(Blocks.BUDDING_AMETHYST)) ? TRICKLE : 0.0F;
			// death's comes with each death near (register)
			default -> 0.0F;
		};
	}

	private static boolean near(ServerLevel level, BlockPos at, int r, java.util.function.Predicate<BlockState> what) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, r, r))) {
			if (what.test(level.getBlockState(pos))) {
				return true;
			}
		}
		return false;
	}

	private static SoundEvent gatherSound(String element) {
		return switch (element) {
			case "fire" -> SoundEvents.FIRECHARGE_USE;
			case "water" -> SoundEvents.BUCKET_FILL;
			case "ice" -> SoundEvents.POWDER_SNOW_STEP;
			case "earth" -> SoundEvents.GRAVEL_BREAK;
			case "air" -> SoundEvents.BREEZE_INHALE;
			case "lightning" -> SoundEvents.BEACON_POWER_SELECT;
			case "restoration" -> SoundEvents.AMETHYST_BLOCK_RESONATE;
			case "death" -> SoundEvents.SOUL_ESCAPE.value();
			default -> SoundEvents.ILLUSIONER_PREPARE_MIRROR;
		};
	}

	/** A thread of the element drawn from {@code from} to {@code to}. */
	static void stream(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, double speed) {
		Vec3 step = to.subtract(from);
		int n = Math.max(2, (int) (step.length() * 2.0));
		for (int i = 0; i <= n; i += 2) {
			Vec3 p = from.add(step.scale(i / (double) n));
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, speed);
		}
	}

	/** Where a palm is: in front of the chest, to that side. */
	private static Vec3 palm(Player player, Vec3 look, boolean left) {
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		Vec3 side = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize().cross(new Vec3(0.0, 1.0, 0.0));
		return player.getEyePosition().add(look.scale(0.6)).add(side.scale(left ? -0.35 : 0.35)).add(0.0, -0.4, 0.0);
	}

	// ---- the blasts ----------------------------------------------------------------------------------------------------

	private static boolean fly(ServerPlayer owner, Blast b) {
		ServerLevel level = b.level;
		Vec3 from = b.pos;
		Vec3 to = from.add(b.velocity);
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(owner, from, end, new AABB(from, end).inflate(1.0),
			e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != owner && !b.struck.contains(e.getId())
				&& !Thralls.refuses(owner, l),
			from.distanceToSqr(end));
		ParticleOptions mote = Spellcraft.particle(b.element);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			land(level, owner, target, b);
			level.sendParticles(mote, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 8, 0.2, 0.2, 0.2, 0.05);
			return false;
		}
		Vec3 step = end.subtract(from);
		for (int i = 0; i < 3; i++) {
			Vec3 p = from.add(step.scale(i / 3.0));
			level.sendParticles(mote, true, false, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
		if (block.getType() != HitResult.Type.MISS) {
			Vec3 at = block.getLocation();
			level.sendParticles(mote, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.05);
			level.playSound(null, at.x, at.y, at.z, Spellcraft.sound(b.element), SoundSource.PLAYERS, 0.3F, 1.8F);
			return false;
		}
		b.pos = to;
		return ++b.age < BLAST_LIFE;
	}

	/** A blast landing on {@code target}: its hurt and a touch of the element (one in a few, the element in full). */
	private static void land(ServerLevel level, ServerPlayer owner, LivingEntity target, Blast b) {
		if (b.element.equals("restoration") && Restoration.friend(owner, target)) {
			// healing light: the living are mended, not hurt
			target.heal(BLAST_HEAL);
			return;
		}
		if (b.element.equals("restoration") && !(target instanceof Enemy) && !Restoration.undead(target)) {
			return;
		}
		target.setInvulnerableTime(0);
		float damage = b.damage * (b.element.equals("restoration") && Restoration.undead(target) ? 1.5F : 1.0F);
		if (!target.hurtServer(level, level.damageSources().indirectMagic(owner, owner), damage)) {
			return;
		}
		Vec3 push = b.velocity.normalize().scale(KNOCKBACK);
		target.push(push.x, 0.05, push.z);
		target.syncVelocity = true;
		if (owner.getRandom().nextInt(ELEMENT_ONE_IN) == 0) {
			Spellcraft.strike(level, owner, target, b.element, false);
			return;
		}
		switch (b.element) {
			case "fire" -> target.igniteForSeconds(2.0F);
			case "water" -> target.clearFire();
			case "ice" -> target.setTicksFrozen(target.getTicksFrozen() + 40);
			case "earth" -> target.push(push.x, -0.1, push.z);
			case "air" -> target.push(0.0, 0.25, 0.0);
			case "death" -> Necromancy.blight(target, 40);
			default -> {
			}
		}
	}

	/** The colour of {@code element}'s reserve bar, 0xRRGGBB. */
	public static int colour(@Nullable String element) {
		if (element == null) {
			return 0xB0B0B0;
		}
		return switch (element) {
			case "fire" -> 0xFF7A1A;
			case "water" -> 0x2E7BFF;
			case "earth" -> 0x8B6A3C;
			case "air" -> 0xD8E6EE;
			case "lightning" -> 0xFFE45A;
			case "ice" -> 0x9EE6FF;
			case "restoration" -> 0xF2C94C;
			case "death" -> 0x7B44B0;
			default -> 0xB0B0B0;
		};
	}
}
