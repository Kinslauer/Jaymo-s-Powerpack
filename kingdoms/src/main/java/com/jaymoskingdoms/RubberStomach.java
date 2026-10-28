package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.306) What a Rubber-folk has inside them.
 * <p>
 * Andy: "rubbers should be able to eject their last swallow, leaving a pink mass that slowly reveals the now corpse."
 * Whatever they swallow leaves its body in them ({@link #STOMACH}: the Kitchen corpse it would have left, saved, and for a
 * player, what they carried). Sneak and press the heritage key and they bring it back up ({@link #bringUp}): the body lies
 * in front of them under a mound of their pink rubber ({@link #MASS}, on a short-lived cloud that drips pink), which sags
 * and melts away over {@link #MELT_TICKS} until only the body is left (the client's {@code RubberMassView}). What they
 * took on from it (its stats, its power, its bulk) goes with it.
 * <p>
 * "If a downed player is absorbed they stay alive, but wounded and appear inside the rubber folk. able to fight their way
 * out by disturbing structures representing the rubber's internal organs." A downed player who is swallowed
 * ({@link #swallowLive}) gets up, wounded ({@link #WOUNDED_HEALTH}), inside them. No new world: they're held high over the
 * swallower where nothing is (as {@link StoryStasis} holds a player making their character, following the swallower about),
 * and their own screen draws the inside of the body round them: walls, and four organs ({@link Organ}) they can hit (the
 * client's {@code RubberInsideView}, {@link Strike}). Every blow hurts the swallower and upsets that organ (a lurch of the
 * heart, a lung's breath knocked out, a sick stomach); once {@link #BURST_SHARE} of the organs is beaten they're thrown up
 * alive. Meanwhile the stomach works on them ({@link #DIGEST}): if they go down again in there they're digested, and their
 * body (and power, and build) are the swallower's like any other swallow. The swallower can also spit them out themselves.
 */
public final class RubberStomach {
	/** The pink mound over a brought-up body melts away over this many ticks (30 s); a spat-out player's over this many. */
	static final int MELT_TICKS = 600, SPIT_MELT_TICKS = 160;
	/** A swallowed player gets up with this much health at most: wounded. */
	static final float WOUNDED_HEALTH = 8.0F;
	/** The stomach works on them this much every this many ticks. */
	static final float DIGEST = 1.0F;
	static final int DIGEST_EVERY = 50;
	/** They're held this far over the world's build limit (blocks), following the swallower. */
	static final int ABOVE_BUILD_LIMIT = 24;
	/** Blows on the organs at most this often (ticks); the share of a blow the swallower feels; the share of the organs beaten that throws them up. */
	static final int STRIKE_EVERY = 5;
	static final float HURT_SHARE = 0.5F, BURST_SHARE = 0.6F;
	/** Thrown up, the swallower takes this much more, and both are sick for this long (ticks). */
	static final float BURST_HURT = 4.0F;
	static final int BURST_SICK = 140;
	/** A digested player's body and things are looked for over this many ticks (Kitchen lays the body down a tick after the death). */
	static final int SWEEP_TICKS = 20;
	/** The bar on the swallowed's screen this often (ticks). */
	static final int BAR_EVERY = 10;
	/** How far in front of them a body comes up (blocks). */
	static final double BRING_UP_AHEAD = 1.3;
	private static final DustParticleOptions PINK = new DustParticleOptions(0xF08CB8, 1.2F);

	/**
	 * The organs a swallowed player can get at (index = id on the wire), their health, how much more (or less) the
	 * swallower feels a blow to each, and the message when one gives out.
	 */
	public enum Organ {
		HEART(14.0F, 1.5F), LEFT_LUNG(10.0F, 1.0F), RIGHT_LUNG(10.0F, 1.0F), STOMACH(18.0F, 0.8F);

		public final float health, hurt;

		Organ(float health, float hurt) {
			this.health = health;
			this.hurt = hurt;
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * A swallow's body, kept: the corpse it left (Kitchen's, saved; none without Kitchen or for what leaves none), what a
	 * swallowed player carried, its type (for the mound's size when there's no body), and the MCA Size and Width it added.
	 */
	record Stomach(Optional<CompoundTag> corpse, List<ItemStack> items, String type, float size, float width) {
		static final Codec<Stomach> CODEC = RecordCodecBuilder.create(i -> i.group(
			CompoundTag.CODEC.optionalFieldOf("corpse").forGetter(Stomach::corpse),
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Stomach::items),
			Codec.STRING.optionalFieldOf("type", "").forGetter(Stomach::type),
			Codec.FLOAT.optionalFieldOf("size", 0.0F).forGetter(Stomach::size),
			Codec.FLOAT.optionalFieldOf("width", 0.0F).forGetter(Stomach::width)).apply(i, Stomach::new));
	}

	/** On a Rubber-folk: their last swallow's body. Saved; a new swallow replaces it, and it's lost with them on death. */
	static final AttachmentType<Stomach> STOMACH = AttachmentRegistry.create(JaymosKingdoms.id("rubber_stomach"), builder -> builder.persistent(Stomach.CODEC));

	/** A melting mound: the game time it came up, how long it takes to melt (ticks), and the body's size under it (blocks). */
	public record Mass(long start, int length, float width, float height) {
		static final Codec<Mass> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("start").forGetter(Mass::start),
			Codec.INT.fieldOf("length").forGetter(Mass::length),
			Codec.FLOAT.fieldOf("width").forGetter(Mass::width),
			Codec.FLOAT.fieldOf("height").forGetter(Mass::height)).apply(i, Mass::new));
		static final StreamCodec<ByteBuf, Mass> STREAM = StreamCodec.composite(ByteBufCodecs.VAR_LONG, Mass::start, ByteBufCodecs.VAR_INT, Mass::length,
			ByteBufCodecs.FLOAT, Mass::width, ByteBufCodecs.FLOAT, Mass::height, Mass::new);
	}

	/** On the cloud that marks a melting mound, synced to everyone (drawn by the client). */
	public static final AttachmentType<Mass> MASS = AttachmentRegistry.create(JaymosKingdoms.id("rubber_mass"),
		builder -> builder.persistent(Mass.CODEC).syncWith(Mass.STREAM, AttachmentSyncPredicate.all()));

	/** On a swallowed player, synced to them alone: who holds them (entity id), and how whole each organ is (0 .. 1, {@link Organ} order). */
	public record Inside(int holder, List<Float> organs) {
		static final StreamCodec<ByteBuf, Inside> STREAM = StreamCodec.composite(ByteBufCodecs.VAR_INT, Inside::holder,
			ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), Inside::organs, Inside::new);
	}

	public static final AttachmentType<Inside> INSIDE = AttachmentRegistry.create(JaymosKingdoms.id("rubber_inside"),
		builder -> builder.syncWith(Inside.STREAM, AttachmentSyncPredicate.targetOnly()));
	/** On a Rubber-folk holding someone alive, synced to everyone: their entity id (the belly stays full, and heaves). */
	public static final AttachmentType<Integer> HOLDING = AttachmentRegistry.create(JaymosKingdoms.id("rubber_holding"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On a swallowed player, saved: where to put them down if the server stopped with them inside. */
	static final AttachmentType<GlobalPos> SAFE = AttachmentRegistry.create(JaymosKingdoms.id("rubber_inside_safe"), builder -> builder.persistent(GlobalPos.CODEC));

	/** Client → server: the swallowed player struck an organ ({@link Organ#ordinal}). */
	public record Strike(int organ) implements CustomPacketPayload {
		public static final Type<Strike> TYPE = new Type<>(JaymosKingdoms.id("rubber_strike"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Strike> CODEC = CustomPacketPayload.codec((p, buf) -> buf.writeVarInt(p.organ),
			buf -> new Strike(buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** One player held alive inside a Rubber-folk, and what they could do before (put back as it was). */
	static final class Held {
		final UUID holder;
		final ServerPlayer victim;
		final float[] organs = new float[Organ.values().length];
		final float size, width;
		final boolean mayfly, flying;
		int ticks;
		long struck = -STRIKE_EVERY;
		boolean up;

		Held(UUID holder, ServerPlayer victim, float size, float width) {
			this.holder = holder;
			this.victim = victim;
			this.size = size;
			this.width = width;
			this.mayfly = victim.getAbilities().mayfly;
			this.flying = victim.getAbilities().flying;
			for (Organ organ : Organ.values()) {
				organs[organ.ordinal()] = organ.health;
			}
		}

		/** The share of all the organs' health that's been beaten out of them (0 .. 1). */
		float beaten() {
			float left = 0.0F, all = 0.0F;
			for (Organ organ : Organ.values()) {
				left += organs[organ.ordinal()];
				all += organ.health;
			}
			return 1.0F - left / all;
		}
	}

	/** Swallower → the one alive inside them; swallowed → their swallower. */
	private static final Map<UUID, Held> HELD = new HashMap<>();
	private static final Map<UUID, UUID> HOLDER = new HashMap<>();

	/** A digested player whose body and things are being gathered into their swallower. */
	private record Sweep(UUID holder, ServerLevel level, AABB box, float size, float width, String type, int[] ticks) {
	}

	private static final List<Sweep> SWEEPS = new ArrayList<>();

	private RubberStomach() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Strike.TYPE, Strike.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Strike.TYPE, (payload, context) -> strike(context.player(), payload.organ()));
		ServerTickEvents.END_SERVER_TICK.register(RubberStomach::tick);
		// either one leaving: out they come, where the swallower stands (no one is saved hanging in the sky)
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Held held = HELD.get(handler.player.getUUID());
			if (held == null) {
				UUID holder = HOLDER.get(handler.player.getUUID());
				held = holder == null ? null : HELD.get(holder);
			}
			if (held != null) {
				release(server, held, null);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> putDown(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Held held : List.copyOf(HELD.values())) {
				release(server, held, null);
			}
			HELD.clear();
			HOLDER.clear();
			SWEEPS.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			UUID holder = HOLDER.get(player.getUUID());
			Held held = holder == null ? null : HELD.get(holder);
			if (held != null) {
				digested(player.level().getServer(), held);
			}
			// what a Rubber-folk had in them dies with them
			if (player.hasAttached(STOMACH)) {
				player.removeAttached(STOMACH);
			}
		});
	}

	/** {@code player} has someone alive inside them. */
	public static boolean holding(ServerPlayer player) {
		return HELD.containsKey(player.getUUID());
	}

	/** {@code player} is inside a Rubber-folk. */
	public static boolean inside(ServerPlayer player) {
		return HOLDER.containsKey(player.getUUID());
	}

	// ---- the last swallow, kept and brought up ---------------------------------------------------------------------

	/**
	 * {@code player}'s last swallow left {@code corpse} (null: none) and {@code items}, and added {@code gained} (MCA Size,
	 * Width) to them: it's kept in them now, in place of the one before. The corpse leaves the world.
	 */
	static void fill(ServerPlayer player, @Nullable Entity corpse, List<ItemStack> items, float[] gained, String type) {
		Optional<CompoundTag> saved = Optional.empty();
		if (corpse != null) {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, corpse.registryAccess());
			if (corpse.saveAsPassenger(output)) {
				saved = Optional.of(output.buildResult());
			}
			corpse.discard();
		}
		player.setAttached(STOMACH, new Stomach(saved, List.copyOf(items), type, gained[0], gained[1]));
	}

	static void fill(ServerPlayer player, @Nullable Entity corpse, List<ItemStack> items, float[] gained) {
		RubberFolk.Adopted adopted = player.getAttached(RubberFolk.ADOPTED);
		fill(player, corpse, items, gained, adopted == null ? "" : adopted.type());
	}

	/**
	 * Sneak and the heritage key: someone alive inside, spat out; else the last swallow's body brought back up under a pink
	 * mound, and what they took from it gone with it. False if there's nothing in them.
	 */
	static boolean bringUp(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Held held = HELD.get(player.getUUID());
		if (held != null) {
			release(server, held, "message.jaymoskingdoms.rubber.spat");
			return true;
		}
		Stomach stomach = player.getAttached(STOMACH);
		RubberFolk.Adopted adopted = player.getAttached(RubberFolk.ADOPTED);
		if (stomach == null && adopted == null || !(player.level() instanceof ServerLevel level) || Downed.isDowned(player)) {
			return false;
		}
		Vec3 spot = ahead(level, player);
		float width = 0.6F, height = 0.6F;
		Entity body = null;
		if (stomach != null && stomach.corpse().isPresent()) {
			body = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), stomach.corpse().get()), level,
				EntitySpawnReason.LOAD, EntityProcessor.NOP);
			if (body != null) {
				body.snapTo(spot.x, spot.y, spot.z, player.getYRot() + 180.0F, 0.0F);
				body.setDeltaMovement(Vec3.ZERO);
				if (!level.addFreshEntity(body)) {
					body = null;
				}
			}
		}
		if (body != null) {
			width = body.getBbWidth();
			height = body.getBbHeight();
		} else {
			String type = stomach != null && !stomach.type().isEmpty() ? stomach.type() : adopted != null ? adopted.type() : "";
			Identifier id = Identifier.tryParse(type);
			EntityType<?> kind = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(id);
			if (kind != null) {
				width = kind.getDimensions().width();
				height = kind.getDimensions().height();
			}
		}
		if (stomach != null) {
			for (ItemStack stack : stomach.items()) {
				if (!stack.isEmpty()) {
					ItemEntity item = new ItemEntity(level, spot.x, spot.y + 0.3, spot.z, stack.copy(), 0.0, 0.1, 0.0);
					item.setPickUpDelay(40);
					level.addFreshEntity(item);
				}
			}
		}
		mound(level, spot, width, height, MELT_TICKS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0F, 0.6F);
		// what they took from it goes with it: its stats, its power, its bulk
		RubberFolk.giveUp(player);
		PowersCompat.dropPower(player);
		if (stomach != null) {
			shrinkBack(player, stomach.size(), stomach.width());
			player.removeAttached(STOMACH);
		}
		player.sendOverlayMessage(Component.translatable(body != null ? "message.jaymoskingdoms.rubber.brought_up" : "message.jaymoskingdoms.rubber.brought_up_nothing")
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	/** A pink mound over a body {@code width} by {@code height} at {@code spot}, melting over {@code length} ticks. */
	static void mound(ServerLevel level, Vec3 spot, float width, float height, int length) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, spot.x, spot.y, spot.z);
		float radius = Math.max(0.6F, Math.max(width, Math.min(height, 2.0F)) * 0.6F);
		cloud.setRadius(radius);
		cloud.setRadiusPerTick(-(radius - 0.55F) / length);
		cloud.setDuration(length);
		cloud.setWaitTime(0);
		cloud.setCustomParticle(PINK);
		cloud.setAttached(MASS, new Mass(level.getGameTime(), length, width, height));
		level.addFreshEntity(cloud);
	}

	/** Where something brought up lands: on the ground a little in front of {@code player}, or at their feet if that's in a wall. */
	private static Vec3 ahead(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize().scale(BRING_UP_AHEAD);
		BlockPos at = BlockPos.containing(player.getX() + look.x, player.getY() + 0.5, player.getZ() + look.z);
		if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
			return player.position();
		}
		for (int i = 0; i < 4 && level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty(); i++) {
			at = at.below();
		}
		return new Vec3(player.getX() + look.x, at.getY(), player.getZ() + look.z);
	}

	/** {@code player} gives back the bulk a swallow added ({@code size}, {@code width}: MCA genes). */
	private static void shrinkBack(ServerPlayer player, float size, float width) {
		if ((size != 0.0F || width != 0.0F) && McaCompat.LOADED) {
			float[] genes = McaBridge.bodyGenes(player);
			if (genes != null) {
				McaBridge.setBody(player, genes[0] - size, genes[1] - width);
			}
		}
	}

	// ---- a player held alive inside ---------------------------------------------------------------------------------

	/** {@code holder} swallowed the downed {@code victim}: up, wounded, and inside them. {@code gained}: the bulk it added. */
	static void swallowLive(ServerLevel level, ServerPlayer holder, ServerPlayer victim, float[] gained) {
		Held before = HELD.get(holder.getUUID());
		if (before != null) {
			release(level.getServer(), before, "message.jaymoskingdoms.rubber.spat");
		}
		Held held = new Held(holder.getUUID(), victim, gained[0], gained[1]);
		HELD.put(holder.getUUID(), held);
		HOLDER.put(victim.getUUID(), holder.getUUID());
		victim.setAttached(SAFE, GlobalPos.of(level.dimension(), holder.blockPosition()));
		// up (Jaymo's Powers gets them up on its next tick), but no better than wounded
		victim.addTag(Downed.REVIVE_TAG);
		holder.setAttached(HOLDING, victim.getId());
		sync(held, holder);
		keep(holder, held);
		holder.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rubber.holding", victim.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		victim.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rubber.swallowed", holder.getDisplayName()).withStyle(ChatFormatting.RED));
	}

	private static void tick(MinecraftServer server) {
		if (!SWEEPS.isEmpty()) {
			sweep(server);
		}
		if (HELD.isEmpty()) {
			return;
		}
		for (Held held : List.copyOf(HELD.values())) {
			ServerPlayer holder = server.getPlayerList().getPlayer(held.holder);
			ServerPlayer victim = held.victim;
			if (victim.isRemoved() || !victim.isAlive()) {
				forget(held);
				continue;
			}
			if (holder == null || !holder.isAlive() || Downed.isDowned(holder) || !Heritage.is(holder, Heritage.Kind.RUBBERFOLK)) {
				// (they went down, or died, or stopped being rubber: out it comes)
				release(server, held, "message.jaymoskingdoms.rubber.let_out");
				continue;
			}
			held.ticks++;
			boolean rising = victim.entityTags().contains(Downed.REVIVE_TAG);
			if (!rising && !held.up && !Downed.isDowned(victim)) {
				// up (Powers got them up), and no better than wounded
				held.up = true;
				victim.setHealth(Math.min(victim.getHealth(), WOUNDED_HEALTH));
			}
			// down again once up: the stomach has had them
			if (held.up && Downed.isDowned(victim)) {
				digest(holder, held);
				continue;
			}
			keep(holder, held);
			if (held.ticks % DIGEST_EVERY == 0 && victim.level() instanceof ServerLevel level) {
				victim.setInvulnerableTime(0);
				victim.hurtServer(level, level.damageSources().magic(), DIGEST);
				victim.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rubber.digesting").withStyle(ChatFormatting.DARK_RED));
			} else if (held.ticks % BAR_EVERY == 0) {
				victim.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rubber.inside", holder.getDisplayName(),
					Math.round(held.beaten() / BURST_SHARE * 100.0F)).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	/** Each tick: the swallowed held over their swallower, out of the world, flying so they don't fall, and shrunk small. */
	private static void keep(ServerPlayer holder, Held held) {
		ServerPlayer victim = held.victim;
		ServerLevel level = holder.level();
		Vec3 spot = new Vec3(holder.getX(), level.getMaxY() + ABOVE_BUILD_LIMIT, holder.getZ());
		if (victim.level() != level || victim.position().distanceToSqr(spot) > 0.25) {
			victim.teleportTo(level, spot.x, spot.y, spot.z, Set.<Relative>of(), victim.getYRot(), victim.getXRot(), false);
		}
		Abilities abilities = victim.getAbilities();
		if (!abilities.mayfly || !abilities.flying) {
			abilities.mayfly = true;
			abilities.flying = true;
			victim.onUpdateAbilities();
		}
		victim.setDeltaMovement(Vec3.ZERO);
		victim.resetFallDistance();
		victim.clearFire();
		HeritageHold.shrink(victim, HeritageHold.SHRINK);
		victim.setAttached(SAFE, GlobalPos.of(level.dimension(), holder.blockPosition()));
	}

	/** The swallowed struck {@code index}: it's upset, the swallower feels it, and enough of it throws them up. */
	private static void strike(ServerPlayer victim, int index) {
		UUID holderId = HOLDER.get(victim.getUUID());
		Held held = holderId == null ? null : HELD.get(holderId);
		MinecraftServer server = victim.level().getServer();
		ServerPlayer holder = held == null ? null : server.getPlayerList().getPlayer(holderId);
		if (held == null || holder == null || index < 0 || index >= Organ.values().length || !victim.isAlive() || Downed.isDowned(victim)) {
			return;
		}
		long now = victim.level().getGameTime();
		if (now - held.struck < STRIKE_EVERY || held.organs[index] <= 0.0F) {
			return;
		}
		held.struck = now;
		Organ organ = Organ.values()[index];
		float strength = victim.getAttackStrengthScale(0.5F);
		victim.resetAttackStrengthTicker();
		float blow = (float)victim.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + 0.8F * strength * strength);
		held.organs[index] = Math.max(0.0F, held.organs[index] - blow);
		boolean burst = held.organs[index] <= 0.0F;
		ServerLevel level = holder.level();
		hurt(level, holder, victim, blow * HURT_SHARE * organ.hurt);
		switch (organ) {
			case HEART -> {
				level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 1.3F);
				if (burst) {
					hurt(level, holder, victim, BURST_HURT);
				}
			}
			case LEFT_LUNG, RIGHT_LUNG -> {
				holder.setAirSupply(Math.max(0, holder.getAirSupply() - 60));
				holder.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, burst ? 200 : 40, burst ? 1 : 0));
				level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 0.9F, 1.4F);
			}
			case STOMACH -> {
				holder.addEffect(new MobEffectInstance(MobEffects.NAUSEA, burst ? 240 : 100, 0));
				holder.getFoodData().setFoodLevel(Math.max(0, holder.getFoodData().getFoodLevel() - 1));
				level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
		}
		PowersCompat.shake(holder, burst ? 1.0F : 0.35F);
		level.sendParticles(PINK, holder.getX(), holder.getY(0.55), holder.getZ(), burst ? 24 : 8, 0.3, 0.3, 0.3, 0.02);
		if (burst) {
			holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rubber.organ." + organ.key(), victim.getDisplayName()).withStyle(ChatFormatting.RED));
		}
		if (held.beaten() >= BURST_SHARE) {
			hurt(level, holder, victim, BURST_HURT);
			holder.addEffect(new MobEffectInstance(MobEffects.NAUSEA, BURST_SICK, 0));
			release(server, held, "message.jaymoskingdoms.rubber.burst");
			return;
		}
		sync(held, holder);
	}

	/** The swallower feels {@code amount} of a blow from inside (credited to the one inside, or just harm if players can't hurt players here). */
	private static void hurt(ServerLevel level, ServerPlayer holder, ServerPlayer victim, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		holder.setInvulnerableTime(0);
		if (!holder.hurtServer(level, level.damageSources().playerAttack(victim), amount)) {
			holder.setInvulnerableTime(0);
			holder.hurtServer(level, level.damageSources().generic(), amount);
		}
	}

	/** The organs as the swallowed's screen sees them. */
	private static void sync(Held held, ServerPlayer holder) {
		List<Float> organs = new ArrayList<>();
		for (Organ organ : Organ.values()) {
			organs.add(held.organs[organ.ordinal()] / organ.health);
		}
		held.victim.setAttached(INSIDE, new Inside(holder.getId(), organs));
	}

	/**
	 * Out they come, alive, in front of the swallower (or where they were swallowed, with no swallower about), under a
	 * thin pink coat that melts off them; the bulk they added goes too. {@code why} goes on both bars (null: nothing).
	 */
	static void release(MinecraftServer server, Held held, @Nullable String why) {
		forget(held);
		ServerPlayer victim = held.victim;
		ServerPlayer holder = server.getPlayerList().getPlayer(held.holder);
		Abilities abilities = victim.getAbilities();
		abilities.mayfly = held.mayfly;
		abilities.flying = held.flying;
		victim.onUpdateAbilities();
		HeritageHold.shrink(victim, 0.0);
		if (holder != null) {
			ServerLevel level = holder.level();
			Vec3 spot = ahead(level, holder);
			victim.teleportTo(level, spot.x, spot.y, spot.z, Set.<Relative>of(), holder.getYRot() + 180.0F, 0.0F, false);
			mound(level, spot, victim.getBbWidth(), victim.getBbHeight(), SPIT_MELT_TICKS);
			level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.5F);
			level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0F, 0.6F);
			shrinkBack(holder, held.size, held.width);
			if (why != null) {
				holder.sendOverlayMessage(Component.translatable(why + ".holder", victim.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		} else {
			putDown(victim);
		}
		victim.setDeltaMovement(Vec3.ZERO);
		victim.resetFallDistance();
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
		if (victim.hasAttached(SAFE)) {
			victim.removeAttached(SAFE);
		}
		if (why != null) {
			victim.sendOverlayMessage(Component.translatable(why).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	/** The hold's bookkeeping gone (the swallowed's screen back to the world). */
	private static void forget(Held held) {
		HELD.remove(held.holder);
		HOLDER.remove(held.victim.getUUID());
		if (held.victim.hasAttached(INSIDE)) {
			held.victim.removeAttached(INSIDE);
		}
		ServerPlayer holder = held.victim.level().getServer().getPlayerList().getPlayer(held.holder);
		if (holder != null && holder.hasAttached(HOLDING)) {
			holder.removeAttached(HOLDING);
		}
	}

	/** A player left over the world by a stop with them inside: put down where they were swallowed. */
	private static void putDown(ServerPlayer player) {
		GlobalPos safe = player.getAttached(SAFE);
		if (safe == null) {
			return;
		}
		player.removeAttached(SAFE);
		HeritageHold.shrink(player, 0.0);
		ServerLevel level = player.level().getServer().getLevel(safe.dimension());
		if (level == null) {
			level = player.level();
		}
		BlockPos at = safe.pos();
		if (!player.isCreative() && !player.isSpectator()) {
			player.getAbilities().flying = false;
			player.getAbilities().mayfly = false;
			player.onUpdateAbilities();
		}
		player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
	}

	/** Down again inside: the stomach has them. Their power and what they were are the swallower's, and they die of it. */
	private static void digest(ServerPlayer holder, Held held) {
		ServerPlayer victim = held.victim;
		ServerLevel level = holder.level();
		PowersCompat.takePower(holder, victim);
		RubberFolk.takeOn(holder, victim, true);
		victim.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rubber.digested", holder.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
		if (!Necromancy.execute(level, victim, holder)) {
			victim.setInvulnerableTime(0);
			victim.hurtServer(level, level.damageSources().playerAttack(holder), Float.MAX_VALUE);
		}
		if (victim.isAlive()) {
			// (a totem: they live after all, and come out)
			release(level.getServer(), held, "message.jaymoskingdoms.rubber.burst");
		}
	}

	/** {@code held}'s swallowed died inside: their body and the things they dropped are gathered into the swallower. */
	private static void digested(MinecraftServer server, Held held) {
		forget(held);
		ServerPlayer victim = held.victim;
		// (their next body mustn't wake able to fly)
		victim.getAbilities().mayfly = held.mayfly;
		victim.getAbilities().flying = false;
		victim.onUpdateAbilities();
		ServerPlayer holder = server.getPlayerList().getPlayer(held.holder);
		if (holder != null) {
			holder.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rubber.digested.holder", victim.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (victim.hasAttached(SAFE)) {
			victim.removeAttached(SAFE);
		}
		AABB box = victim.getBoundingBox().inflate(3.0).expandTowards(0.0, -12.0, 0.0);
		SWEEPS.add(new Sweep(held.holder, victim.level(), box, held.size, held.width, BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString(), new int[1]));
	}

	/** Each tick a few: the digested's body (Kitchen's, a tick or so after the death) and things taken in. */
	private static void sweep(MinecraftServer server) {
		for (Iterator<Sweep> it = SWEEPS.iterator(); it.hasNext();) {
			Sweep sweep = it.next();
			int ticks = ++sweep.ticks()[0];
			ServerPlayer holder = server.getPlayerList().getPlayer(sweep.holder());
			if (holder == null || ticks > SWEEP_TICKS) {
				it.remove();
				continue;
			}
			Entity corpse = null;
			for (Entity e : sweep.level().getEntities((Entity)null, sweep.box(), KitchenCompat::isCorpse)) {
				corpse = e;
				break;
			}
			if (corpse == null && ticks < SWEEP_TICKS && KitchenCompat.loaded()) {
				continue;
			}
			List<ItemStack> items = new ArrayList<>();
			for (ItemEntity item : sweep.level().getEntitiesOfClass(ItemEntity.class, sweep.box())) {
				items.add(item.getItem().copy());
				item.discard();
			}
			fill(holder, corpse, items, new float[] {sweep.size(), sweep.width()}, sweep.type());
			it.remove();
		}
	}
}
