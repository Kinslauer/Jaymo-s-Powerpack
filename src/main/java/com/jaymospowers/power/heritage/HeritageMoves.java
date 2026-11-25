package com.jaymospowers.power.heritage;

import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.Loaded;
import com.jaymospowers.core.TerrainReturn;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.72) The Stormborn's and the Dawnborn's own left-mouse holds (Jaymo's Kingdoms heritages; anyone of that blood,
 * power or not), like the Emberborn's Kindle. Andy's picks: hold left mouse bare-handed to charge ({@link #CHARGE_TICKS}),
 * and let go once it's full.
 * <ul>
 * <li><b>Thunderclap</b> (Stormborn): a lightning bolt called down where you look, out to {@link #CLAP_RANGE} blocks, on
 * open sky only. Lightning never hurts a Stormborn and charges them (Kingdoms), so it can be called on yourself in melee.
 * Every {@link #CLAP_COOLDOWN} ticks, {@link #CLAP_STORM_COOLDOWN} in a thunderstorm.</li>
 * <li><b>Sunflare</b> (Dawnborn): a flash of sunlight, by day only: the undead within {@link #FLARE_RADIUS} blocks are
 * set alight, other mobs blinded, and friends (other players, your own pets) healed a little. Every
 * {@link #FLARE_COOLDOWN} ticks.</li>
 * <li><b>Wood path</b> (Sylvan): the wood lets you through for {@link #PATH_TICKS}: logs, planks and leaves at your body are lent
 * and come back behind you (the same engine as terrain return, {@link TerrainReturn#lend}). Every {@link #PATH_COOLDOWN} ticks.</li>
 * </ul>
 * The client asks through {@code RaceHoldClient} like Kindle (sneak + left hold beside another power): it sends
 * {@link Charge}{@code (true)} when the charge starts and every few ticks while held, and {@code (false)} when let go;
 * without word for {@link #TIMEOUT} ticks it stops without going off.
 */
public final class HeritageMoves {
	// ---- tuning ----
	/** Ticks of holding to a full charge. */
	public static final int CHARGE_TICKS = 30;
	/** Thunderclap: how far it reaches (blocks), and its cooldowns (ticks), plain and in a thunderstorm. */
	static final double CLAP_RANGE = 48.0;
	static final int CLAP_COOLDOWN = 1200, CLAP_STORM_COOLDOWN = 400;
	/** Sunflare: its reach (blocks), how long the undead burn (seconds), the blindness (ticks), the heal, the cooldown (ticks). */
	static final double FLARE_RADIUS = 6.0;
	static final float FLARE_BURN = 8.0F, FLARE_HEAL = 4.0F;
	static final int FLARE_BLIND_TICKS = 100, FLARE_COOLDOWN = 1200;
	/** The client's word lasts this long (ticks). */
	static final int TIMEOUT = 40;
	/** How often (ticks) the heritage is read off the Kingdoms tags. */
	static final int READ_EVERY = 20;
	/** Wood path: how long (ticks) it can be walked, its cooldown, and how long a block of wood is lent before it comes back (it waits longer while anything stands in it). */
	static final int PATH_TICKS = 20 * 12, PATH_COOLDOWN = 20 * 40, PATH_LEND = 30;
	/** Wood path: how far past the body (blocks) wood is let through, and the most blocks lent in one tick. */
	static final double PATH_REACH = 0.4;
	static final int PATH_PER_TICK = 48;
	/** Jaymo's Kingdoms' tags for the three heritages. */
	public static final String STORM_TAG = "jaymoskingdoms_heritage_stormborn", DAWN_TAG = "jaymoskingdoms_heritage_dawnborn",
		SYLVAN_TAG = "jaymoskingdoms_heritage_sylvan";
	/** The bloods, as synced. */
	public static final int NONE = 0, STORM = 1, DAWN = 2, SYLVAN = 3;

	/** Which of the two bloods {@code player} is of (from Kingdoms' tags): the client needs it to hand the hold over. */
	public static final AttachmentType<Integer> BLOOD = AttachmentRegistry.create(JaymosPowers.id("heritage_blood"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** The charge (0-1) while it's held, seen by all (the bar, the pose). */
	public static final AttachmentType<Float> CHARGE = AttachmentRegistry.create(JaymosPowers.id("heritage_charge"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));

	/** Client → server: the charge held ({@code on}: started or still held), or let go. */
	public record Charge(boolean on) implements CustomPacketPayload {
		public static final Type<Charge> TYPE = new Type<>(JaymosPowers.id("heritage_charge"));
		public static final StreamCodec<ByteBuf, Charge> CODEC = ByteBufCodecs.BOOL.map(Charge::new, Charge::on);

		@Override
		public Type<Charge> type() {
			return TYPE;
		}
	}

	/** One player's charge on the server. */
	private static final class Live {
		boolean on;
		long since, heard;
		float sent = -1.0F;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	/** When each player may next use theirs (game time). */
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** Until when (game time) each Sylvan's wood path lets them through. */
	private static final Map<UUID, Long> WALKING = new HashMap<>();

	private HeritageMoves() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Charge.TYPE, Charge.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Charge.TYPE, (payload, context) -> hold(context.player(), payload.on()));
		ServerTickEvents.END_SERVER_TICK.register(HeritageMoves::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.clear();
			READY.clear();
			WALKING.clear();
		});
	}

	/** {@link #STORM}, {@link #DAWN} or {@link #NONE} (either side). */
	public static int blood(Player player) {
		Integer blood = player.getAttached(BLOOD);
		return blood == null ? NONE : blood;
	}

	/** {@code player}'s charge, 0-1, while it's held (either side); -1 when it isn't. */
	public static float charge(Player player) {
		Float charge = player.getAttached(CHARGE);
		return charge == null ? -1.0F : charge;
	}

	private static void hold(ServerPlayer player, boolean on) {
		Live live = LIVE.computeIfAbsent(player.getUUID(), k -> new Live());
		long now = player.level().getGameTime();
		if (!on) {
			if (live.on) {
				boolean full = now - live.since >= CHARGE_TICKS;
				stop(player, live);
				if (full && usable(player)) {
					fire(player);
				}
			}
			return;
		}
		live.heard = now;
		if (live.on || !usable(player)) {
			return;
		}
		long ready = READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready && !player.getAbilities().instabuild) {
			say(player, Component.translatable("message.jaymospowers.heritage." + key(player) + ".cooldown", (ready - now + 19) / 20));
			return;
		}
		String why = blocked(player);
		if (why != null) {
			say(player, Component.translatable(why));
			return;
		}
		live.on = true;
		live.since = now;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
			blood(player) == STORM ? SoundEvents.TRIDENT_THUNDER.value() : blood(player) == SYLVAN ? SoundEvents.AZALEA_LEAVES_BREAK : SoundEvents.BEACON_ACTIVATE,
			SoundSource.PLAYERS, 0.4F, 1.6F);
	}

	private static String key(ServerPlayer player) {
		return blood(player) == STORM ? "thunderclap" : blood(player) == SYLVAN ? "woodpath" : "sunflare";
	}

	/** Why it can't be called here and now (a lang key), or null: Thunderclap needs open sky, Sunflare the day. */
	private static String blocked(ServerPlayer player) {
		if (blood(player) == STORM) {
			return player.level().canSeeSky(player.blockPosition().above()) ? null : "message.jaymospowers.heritage.thunderclap.no_sky";
		}
		if (blood(player) == SYLVAN) {
			return null;
		}
		return player.level().isBrightOutside() ? null : "message.jaymospowers.heritage.sunflare.no_sun";
	}

	private static void say(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.GRAY));
	}

	/** Of either blood, alive, bare-handed. */
	private static boolean usable(ServerPlayer player) {
		// (0.15.136) raised in the main hand, as Kindle is: not with that arm gone
		return blood(player) != NONE && player.isAlive() && !player.isSpectator() && player.getMainHandItem().isEmpty()
			&& com.jaymospowers.core.wounds.LimbUse.usable(player, com.jaymospowers.core.wounds.LimbUse.MAIN_ARM);
	}

	private static void stop(ServerPlayer player, Live live) {
		live.on = false;
		live.sent = -1.0F;
		player.removeAttached(CHARGE);
	}

	private static void serverTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.tickCount % READ_EVERY == 0) {
				var tags = player.entityTags();
				int blood = tags.contains(STORM_TAG) ? STORM : tags.contains(DAWN_TAG) ? DAWN : tags.contains(SYLVAN_TAG) ? SYLVAN : NONE;
				if (blood != blood(player)) {
					if (blood == NONE) {
						player.removeAttached(BLOOD);
					} else {
						player.setAttached(BLOOD, blood);
					}
				}
			}
			Live live = LIVE.get(player.getUUID());
			if (live != null && live.on) {
				tick(player, live);
			}
			Long until = WALKING.get(player.getUUID());
			if (until != null) {
				walk(player, until);
			}
		}
		WALKING.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		LIVE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static void tick(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!usable(player) || now - live.heard > TIMEOUT || now < live.heard) {
			stop(player, live);
			return;
		}
		float charge = Math.min(1.0F, (now - live.since) / (float)CHARGE_TICKS);
		// the charge gathering at the raised hand (Thunderclap) or between the cupped hands (Sunflare)
		Vec3 at = blood(player) == STORM ? player.position().add(0.0, player.getBbHeight() + 0.5, 0.0)
			: blood(player) == SYLVAN ? player.position().add(0.0, player.getBbHeight() * 0.5, 0.0)
			: player.getEyePosition().add(player.getViewVector(1.0F).scale(0.7)).add(0.0, -0.4, 0.0);
		if ((now - live.since) % 2 == 0) {
			level.sendParticles(blood(player) == STORM ? MagicFx.CRACKLE : blood(player) == SYLVAN ? MagicFx.GLOW : MagicFx.LIGHT, at.x, at.y, at.z,
				1 + (int)(charge * 3), 0.15, 0.15, 0.15, 0.02);
		}
		if (charge >= 1.0F && live.sent < 1.0F) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
		}
		float shown = (float)Math.floor(charge * 20.0F) / 20.0F;
		if (shown != live.sent) {
			live.sent = shown;
			player.setAttached(CHARGE, shown);
		}
	}

	/** Let go at full charge: the bolt or the flash, and the cooldown starts. */
	private static void fire(ServerPlayer player) {
		if (blocked(player) != null) {
			return;
		}
		ServerLevel level = player.level();
		boolean done = blood(player) == STORM ? thunderclap(player, level) : blood(player) == SYLVAN ? woodPath(player, level) : sunflare(player, level);
		if (done) {
			int cooldown = blood(player) == STORM ? level.isThundering() || com.jaymospowers.eden.EdenBrainstorm.in(player) ? CLAP_STORM_COOLDOWN : CLAP_COOLDOWN
				: blood(player) == SYLVAN ? PATH_COOLDOWN : FLARE_COOLDOWN;
			// (Blood Awakening) awakened, it comes back in half the time
			READY.put(player.getUUID(), level.getGameTime() + com.jaymospowers.core.TreeLocks.wait(player, cooldown));
		}
	}

	/** Lightning where they look: the first creature or block in the line, else the ground under its end. */
	private static boolean thunderclap(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(CLAP_RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, eye.distanceToSqr(end));
		BlockPos at;
		if (hit != null) {
			at = hit.getEntity().blockPosition();
		} else if (block.getType() != HitResult.Type.MISS) {
			at = block.getBlockPos().relative(block.getDirection());
		} else {
			at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(end));
		}
		if (!level.canSeeSky(at)) {
			say(player, Component.translatable("message.jaymospowers.heritage.thunderclap.no_sky"));
			return false;
		}
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) {
			return false;
		}
		bolt.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		bolt.setCause(player);
		level.addFreshEntity(bolt);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		return true;
	}

	/**
	 * The wood lets a Sylvan through: for {@link #PATH_TICKS} the wood and leaves at the body (logs, planks, leaves; nothing
	 * else, and never the floor underfoot) are lent ({@link TerrainReturn#lend}) and come back behind, once nothing stands in
	 * them. Where it is not wood the Sylvan walks as ever.
	 */
	private static boolean woodPath(ServerPlayer player, ServerLevel level) {
		WALKING.put(player.getUUID(), level.getGameTime() + PATH_TICKS);
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		MagicFx.ring(level, player.position(), 2.5F, 0x7FCF6A);
		level.sendParticles(MagicFx.GLOW, at.x, at.y, at.z, 24, 0.5, 0.7, 0.5, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 1.2F, 0.7F);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		return true;
	}

	/** One tick of a wood path: the wood at the body is let through. */
	private static void walk(ServerPlayer player, long until) {
		ServerLevel level = player.level();
		if (level.getGameTime() >= until || !player.isAlive() || player.isSpectator()) {
			WALKING.remove(player.getUUID());
			return;
		}
		AABB body = player.getBoundingBox().inflate(PATH_REACH, 0.0, PATH_REACH);
		int lent = 0;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ), BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
			// never the floor: a block whose top is at or under the feet stays
			if (lent >= PATH_PER_TICK || pos.getY() + 1 <= player.getY() + 0.05 || !Loaded.made(level, pos)) {
				continue;
			}
			var state = level.getBlockState(pos);
			if ((state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.PLANKS)) && TerrainReturn.lend(level, pos.immutable(), PATH_LEND)) {
				lent++;
			}
		}
		if (lent > 0 && level.getGameTime() % 4 == 0) {
			level.sendParticles(MagicFx.GLOW, player.getX(), player.getY(0.5), player.getZ(), 2, 0.3, 0.5, 0.3, 0.02);
		}
	}

	/** The flash: the undead set alight, other mobs blinded, friends healed a little. */
	private static boolean sunflare(ServerPlayer player, ServerLevel level) {
		Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		AABB box = player.getBoundingBox().inflate(AbilityPower.size(player, FLARE_RADIUS));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e != player && e.isAlive() && !e.isSpectator() && e.distanceToSqr(player) <= AbilityPower.size(player, FLARE_RADIUS) * AbilityPower.size(player, FLARE_RADIUS))) {
			if (e.is(EntityTypeTags.UNDEAD)) {
				e.igniteForSeconds(FLARE_BURN);
			} else if (e instanceof Player || e instanceof OwnableEntity owned && player.equals(owned.getOwner())) {
				e.heal(FLARE_HEAL);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY(0.8), e.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
			} else if (e instanceof Mob) {
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FLARE_BLIND_TICKS, 0), player);
			}
		}
		// a ring of light thrown out, and a burst at the hands
		for (int i = 0; i < 48; i++) {
			double a = i * Math.PI * 2.0 / 48.0;
			level.sendParticles(MagicFx.LIGHT, center.x, center.y, center.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.45);
		}
		level.sendParticles(MagicFx.LIGHT, center.x, center.y + 0.3, center.z, 20, 0.2, 0.2, 0.2, 0.15);
		level.sendParticles(MagicFx.EMBER, center.x, center.y, center.z, 30, 2.0, 1.0, 2.0, 0.0);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.5F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		return true;
	}
}
