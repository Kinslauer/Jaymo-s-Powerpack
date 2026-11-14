package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.Production;
import com.jaymoskingdoms.Thralls;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Grue and the dark. Andy (2026-11-14), after the Reaper: "can the rest of the spirits get an upgrade to this caliber?
 * especially the grue." What the Reaper has and the Grue lacked: a body that grows, something in the world that makes it
 * stronger, a need, a time anyone can see it, and a top to reach. The Grue is "the thing at the bottom of the dark", so:
 * <ul>
 * <li><b>Its body grows with its hold</b> ({@link #stage}, drawn by the client): a red flicker in the spirit's arms, a shadow
 * at the keeper's back, the hooded shape, the wing-arms, and the whole of it once it has them (or they wear its form).</li>
 * <li><b>The dark feeds it</b> ({@link #dark}): its hold creeps faster in the dark and slower in the light
 * ({@link #creepTimes}), and its blows, a host's or a master's in Grue Form, land harder there ({@link #hurtTimes}). In the dark
 * anyone can see it at a keeper's back, attuned or not.</li>
 * <li><b>A master must feed it</b>: a kill in Grue Form or a forbidden cast ({@link #feed}). Unfed for {@link #HUNGER_DAYS}
 * days it starts to creep again ({@link #HUNGER_CREEP} a look), and at {@link Grue#TAKEN} the mastery is gone and its trial is
 * fought again.</li>
 * <li><b>A master's moves</b> ({@link #MOVES}, with Grue Form and Grue's Grasp): Snuff (every light near put out), Call Shades,
 * and the Long Dark (a minute in which everything near is blind and weak and the dark is as deep as it gets). In Grue Form,
 * sneaking in a fall lets them down on its wings.</li>
 * </ul>
 */
public final class GrueDark {
	/** The hold each stage of its body starts at (stage 1 is any hold at all; stage 5 is {@link Grue#TAKEN}). */
	public static final float SHADOW_AT = Grue.STIR, SHAPE_AT = Grue.ASSAULT, WINGS_AT = 80.0F;
	/** How its hold's creep and its blows scale from full light to full dark. */
	static final float CREEP_LIGHT = 0.6F, CREEP_DARK = 1.4F, HURT_LIGHT = 0.75F, HURT_DARK = 1.25F;
	/** The light (0-15) at or under which anyone sees it at a keeper's back. */
	public static final int SEEN_LIGHT = 4;
	/** Days a master's Grue goes unfed before it creeps, and what it gains each of the Grue's looks after that. */
	static final int HUNGER_DAYS = 3;
	static final float HUNGER_CREEP = 2.0F;
	/** Snuff: how far it reaches (blocks) and how long those near are left in the dark (ticks). */
	static final int SNUFF_REACH = 10, SNUFF_DARK = 100;
	/** Call Shades: how many, and how long they last (ticks). */
	static final int SHADES = 3, SHADE_LIFE = 600;
	/** The Long Dark: how long it lasts (ticks), how far it reaches (blocks). */
	static final int LONG_DARK_TICKS = 1200;
	static final double LONG_DARK_REACH = 24.0;

	/** The day a master's Grue last fed. Saved, kept through death, never synced. */
	public static final AttachmentType<Long> FED = AttachmentRegistry.create(JaymosKingdoms.id("grue_fed"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** Server → player: the Long Dark is over them for {@code ticks}: the sky goes black and the sun out. */
	public record Sky(int ticks) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
		public static final Type<Sky> TYPE = new Type<>(JaymosKingdoms.id("grue_sky"));
		public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Sky> CODEC =
			net.minecraft.network.codec.StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Sky::ticks, Sky::new);

		@Override
		public Type<Sky> type() {
			return TYPE;
		}
	}

	/** Game time each keeper's Long Dark ends. */
	private static final Map<UUID, Long> LONG_DARK = new HashMap<>();

	static final List<Move> MOVES = List.of(
		new Ambrose.Def("grue_snuff", 20.0F, 200, 1, Poses.REACH, Mana.HARM, GrueDark::snuff),
		new Ambrose.Def("grue_shades", 40.0F, 600, 1, Poses.REACH, Mana.HARM, GrueDark::shades),
		new Ambrose.Def("grue_long_dark", 80.0F, 6000, 1, Poses.SLAM, Mana.HARM, GrueDark::longDark));

	private GrueDark() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("grue fed attachment: {}", FED);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(Sky.TYPE, Sky.CODEC);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LONG_DARK.clear());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && killer != entity) {
				SpiritState spirit = Spirits.state(killer);
				if (GrueTrial.mastered(spirit) && spirit.toggled(SpiritState.GRUE_FORM)) {
					feed(killer);
				}
			}
		});
	}

	// ---- its body, and the dark ---------------------------------------------------------------------------------------

	/** The stage of the Grue's body at {@code hold}: 0 none, 1 the flicker, 2 the shadow, 3 the shape, 4 the wing-arms, 5 all of it. */
	public static int stage(float hold) {
		return hold <= 0.0F ? 0 : hold < SHADOW_AT ? 1 : hold < SHAPE_AT ? 2 : hold < WINGS_AT ? 3 : hold < Grue.TAKEN ? 4 : 5;
	}

	/** How dark it is where {@code entity} stands: 0 in full light, 1 in none. A keeper's Long Dark is as dark as it gets. */
	public static float dark(Entity entity) {
		Long until = LONG_DARK.get(entity.getUUID());
		if (until != null && entity.level().getGameTime() < until) {
			return 1.0F;
		}
		return 1.0F - entity.level().getMaxLocalRawBrightness(BlockPos.containing(entity.getEyePosition())) / 15.0F;
	}

	/** What the hold's creep on {@code player} is multiplied by, for the light they stand in. */
	static float creepTimes(ServerPlayer player) {
		return CREEP_LIGHT + (CREEP_DARK - CREEP_LIGHT) * dark(player);
	}

	/** What the Grue's blows from {@code entity} are multiplied by, for the light they stand in. */
	static float hurtTimes(Entity entity) {
		return HURT_LIGHT + (HURT_DARK - HURT_LIGHT) * dark(entity);
	}

	/** What a spirit's blow from {@code caster} is multiplied by: the dark's share, if they wear Grue Form. */
	static float formTimes(LivingEntity caster) {
		SpiritState spirit = Spirits.state(caster);
		return spirit != null && spirit.toggled(SpiritState.GRUE_FORM) ? hurtTimes(caster) : 1.0F;
	}

	// ---- a master's Grue ----------------------------------------------------------------------------------------------

	/** A master's Grue has fed: its creep is gone. */
	static void feed(ServerPlayer player) {
		player.setAttached(FED, Production.day(player.level().getServer()));
		player.removeAttached(Grue.GRUE);
	}

	/** Once a second for one who mastered it ({@link Grue#tick}): the wings in a fall, and the hunger. */
	static void second(ServerPlayer player, SpiritState spirit, long now) {
		if (spirit.toggled(SpiritState.GRUE_FORM) && player.isShiftKeyDown() && !player.onGround() && player.getDeltaMovement().y < -0.5) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false, false));
		}
		if ((now + player.getId()) % Grue.LOOK_EVERY >= 20L) {
			return;
		}
		long day = Production.day(player.level().getServer());
		Long fed = player.getAttached(FED);
		if (fed == null) {
			player.setAttached(FED, day);
			return;
		}
		if (day - fed < HUNGER_DAYS) {
			return;
		}
		Grue.State state = player.getAttached(Grue.GRUE);
		float hold = (state == null ? 0.0F : state.hold()) + HUNGER_CREEP;
		if (state == null) {
			Grue.speakTo(player, "hungry");
		}
		if (hold >= Grue.TAKEN) {
			// the leash has slipped: it is no master's now, and the trial is fought again
			Spirits.toggle(player, SpiritState.GRUE_FORM, false);
			SpiritState now2 = Spirits.state(player);
			if (now2 != null) {
				player.setAttached(Spirits.SPIRIT, now2.withToggle(SpiritState.MASTERED, false));
			}
			player.removeAttached(FED);
		}
		Grue.creepTo(player, hold);
	}

	/** {@code /kingdom grue master}: {@code player} has subjugated the Grue, as if the trial were won. */
	static int masterCommand(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
		SpiritState spirit = Spirits.state(player);
		if (spirit == null) {
			source.sendFailure(Component.literal(player.getGameProfile().name() + " has no spirit"));
			return 0;
		}
		player.setAttached(Spirits.SPIRIT, spirit.withToggle(SpiritState.MASTERED, true));
		feed(player);
		source.sendSuccess(() -> Component.literal("grue mastered"), true);
		return 1;
	}

	/** {@code /kingdom grue starve}: {@code player}'s mastered Grue has gone {@link #HUNGER_DAYS} days unfed. */
	static int starveCommand(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
		player.setAttached(FED, Production.day(player.level().getServer()) - HUNGER_DAYS);
		source.sendSuccess(() -> Component.literal("grue unfed"), true);
		return 1;
	}

	// ---- a master's moves ---------------------------------------------------------------------------------------------

	/** Snuff: every flame and lamp within {@link #SNUFF_REACH} goes out, and whoever is near is left in the dark. */
	private static boolean snuff(ServerPlayer player, SpiritState state, int charge) {
		ServerLevel level = (ServerLevel)player.level();
		int out = douse(level, player.blockPosition(), SNUFF_REACH);
		int blinded = blind(level, player, SNUFF_REACH, SNUFF_DARK, false);
		if (out == 0 && blinded == 0) {
			return false;
		}
		level.sendParticles(Fx.miasma(Grue.COLOR, 2.0F), player.getX(), player.getY() + 1.0, player.getZ(), 60, 3.0, 1.5, 3.0, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2F, 0.5F);
		return true;
	}

	/** Puts out the lights within {@code reach} of {@code centre}: torches and lanterns fall as items, fires die, candles and campfires go dark. */
	private static int douse(ServerLevel level, BlockPos centre, int reach) {
		int out = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach / 2, -reach), centre.offset(reach, reach / 2, reach))) {
			BlockState block = level.getBlockState(pos);
			if (block.isAir() || block.getLightEmission() == 0 || !pos.closerThan(centre, reach)) {
				continue;
			}
			if (block.is(Blocks.TORCH) || block.is(Blocks.WALL_TORCH) || block.is(Blocks.SOUL_TORCH) || block.is(Blocks.SOUL_WALL_TORCH)
				|| block.is(Blocks.LANTERN) || block.is(Blocks.SOUL_LANTERN)) {
				level.destroyBlock(pos, true);
				out++;
			} else if (block.is(Blocks.FIRE) || block.is(Blocks.SOUL_FIRE)) {
				level.removeBlock(pos, false);
				out++;
			} else if (block.hasProperty(BlockStateProperties.LIT) && block.getValue(BlockStateProperties.LIT)) {
				level.setBlockAndUpdate(pos, block.setValue(BlockStateProperties.LIT, false));
				out++;
			}
		}
		return out;
	}

	/** Leaves every foe of {@code player}'s within {@code reach} in the dark for {@code ticks} (and weak, if {@code weaken}); how many. */
	private static int blind(ServerLevel level, ServerPlayer player, double reach, int ticks, boolean weaken) {
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(player.position(), player.position()).inflate(reach),
			e -> e != player && e.isAlive() && SpiritFoes.foe(player, e, false))) {
			e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks, 0, true, false, true));
			if (weaken) {
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, true, false, true));
			}
			n++;
		}
		return n;
	}

	/** Call Shades: {@link #SHADES} of the Grue's shades, the keeper's for {@link #SHADE_LIFE} ticks, set on what they look at. */
	private static boolean shades(ServerPlayer player, SpiritState state, int charge) {
		ServerLevel level = (ServerLevel)player.level();
		LivingEntity foe = com.jaymoskingdoms.Arcana.sighted(player);
		if (foe != null && !SpiritFoes.foe(player, foe, true)) {
			foe = null;
		}
		int called = 0;
		for (int i = 0; i < SHADES; i++) {
			double a = i * Math.PI * 2.0 / SHADES;
			Vec3 at = player.position().add(Math.cos(a) * 1.5, 1.5, Math.sin(a) * 1.5);
			Mob shade = Thralls.raise(level, net.minecraft.world.entity.EntityTypes.VEX, at, -1, player.getUUID(), true, SHADE_LIFE, foe);
			if (shade != null) {
				shade.setCustomName(Component.translatable("entity.jaymoskingdoms.grue_shade").withStyle(s -> s.withColor(Grue.COLOR)));
				called++;
			}
		}
		if (called == 0) {
			return false;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.2F, 0.6F);
		return true;
	}

	/** The Long Dark: for {@link #LONG_DARK_TICKS} the lights near are out, every foe near is blind and weak, and the dark is at its deepest for the keeper. */
	private static boolean longDark(ServerPlayer player, SpiritState state, int charge) {
		if (!state.toggled(SpiritState.GRUE_FORM)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.grue.long_dark_form").withStyle(s -> s.withColor(Grue.COLOR)));
			return false;
		}
		ServerLevel level = (ServerLevel)player.level();
		LONG_DARK.put(player.getUUID(), level.getGameTime() + LONG_DARK_TICKS);
		douse(level, player.blockPosition(), (int)LONG_DARK_REACH);
		blind(level, player, LONG_DARK_REACH, LONG_DARK_TICKS, true);
		for (ServerPlayer other : level.players()) {
			if (other.distanceToSqr(player) > LONG_DARK_REACH * LONG_DARK_REACH * 4.0) {
				continue;
			}
			// the sky goes black over everyone there, the keeper too; only the others are left groping
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(other, new Sky(LONG_DARK_TICKS));
			if (other != player) {
				other.addEffect(new MobEffectInstance(MobEffects.DARKNESS, LONG_DARK_TICKS, 0, true, false, true));
			}
		}
		Fx.ring(level, player.position().add(0.0, 0.2, 0.0), (float)LONG_DARK_REACH, Grue.COLOR);
		level.sendParticles(Fx.miasma(Grue.COLOR, 3.0F), player.getX(), player.getY() + 1.5, player.getZ(), 200, 8.0, 3.0, 8.0, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0F, 0.5F);
		Grue.speakTo(player, "long_dark");
		return true;
	}
}
