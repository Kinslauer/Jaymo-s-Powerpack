package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.PowerInput;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Web Slinger, server side. Swinging itself is simulated on the client (player motion is client-authoritative); the
 * server checks each swing web the client reports, shows the line to everyone, keeps the swinger from being kicked
 * for flying, and cancels fall damage until they land. Zip, Web Shot and Yank are decided here.
 */
public final class WebPower implements Power {
	public static final WebPower INSTANCE = new WebPower();
	public static final Identifier ID = JaymosPowers.id("web");

	/** Cooldowns, in ticks. */
	public static final int ZIP_COOLDOWN = 60;
	public static final int SHOT_COOLDOWN = 40;
	/** Web Shot charges for up to this long (hold V); the cooldown grows from SHOT_COOLDOWN to this at a full charge. */
	public static final int SHOT_CHARGE_TICKS = 60;
	public static final int SHOT_COOLDOWN_MAX = 160;
	public static final int YANK_COOLDOWN = 120;
	/** Zip reach, and how fast the client reels you in (blocks/tick; used here to time the zip). */
	public static final double ZIP_RANGE = 48.0;
	public static final double ZIP_SPEED = 1.8;
	/** Yank reach, and the bounding-box volume above which a target is "heavy" (you fly to it instead). Player = 0.65. */
	public static final double YANK_RANGE = 24.0;
	public static final double YANK_HEAVY_VOLUME = 2.6;
	/**
	 * Holding Yank keeps the web stuck to its target like a lead: past {@code TETHER_LENGTH} it's pulled back toward
	 * you (springy, at most {@code TETHER_MAX_PULL} b/t a tick), so you can drag it along or let it hang under you. The
	 * line snaps if it's stretched past {@code TETHER_BREAK}, or if the target hits something (a player: swings).
	 */
	public static final double TETHER_LENGTH = 3.0;
	public static final double TETHER_SPRING = 0.25;
	public static final double TETHER_MAX_PULL = 0.9;
	public static final double TETHER_BREAK = 4.5;
	/**
	 * A <i>player</i> on the Yank line can fight it: they're pulled more gently than a mob ({@code PLAYER_TETHER_*}),
	 * so they can lean away against it, and holding it stretched past {@code TETHER_LENGTH + PLAYER_STRAIN_OVER} for
	 * {@code PLAYER_STRAIN_TICKS} snaps it. Attacking (a swing) breaks it outright, as before.
	 */
	public static final double PLAYER_TETHER_SPRING = 0.08;
	public static final double PLAYER_TETHER_MAX_PULL = 0.22;
	public static final double PLAYER_STRAIN_OVER = 1.5;
	public static final int PLAYER_STRAIN_TICKS = 30;
	/** Wings spread work like Feather Falling IV: fall damage is cut by this fraction (and for a moment after folding). */
	public static final double WINGS_FEATHER_FALLING = 0.48;
	public static final int WINGS_FEATHER_LINGER = 10;
	private static final Identifier FEATHER_ID = JaymosPowers.id("web_wings_feather");
	/** Web Shot launch speed (a snowball is 1.5). */
	public static final float SHOT_SPEED = 2.4F;
	/** Fall-damage grace after a swing/zip lasts until you land, or this long at most. */
	private static final int GRACE_MAX_TICKS = 1200;

	private static final Identifier SAFE_FALL_ID = JaymosPowers.id("web_safe_fall");

	/** The web line a player has out from their main hand (swing, zip or yank), for everyone to draw. */
	public static final AttachmentType<WebLine> LINE = AttachmentRegistry.create(JaymosPowers.id("web_line"),
		builder -> builder.syncWith(WebLine.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** Web wings spread (glide), for everyone to draw. */
	public static final AttachmentType<Boolean> WINGS = AttachmentRegistry.create(JaymosPowers.id("web_wings"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** Wings last a few seconds; the server folds them itself after this long in case the client never says so. */
	public static final int WINGS_TICKS = 80;
	public static final int WINGS_COOLDOWN = 60;
	/** The entity a player's Yank web is holding (entity id), for everyone to draw. */
	public static final AttachmentType<Integer> TETHER = AttachmentRegistry.create(JaymosPowers.id("web_tether"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On a creature held by someone's Yank web: it's being dragged (clients ragdoll it). */
	public static final AttachmentType<Boolean> HELD = AttachmentRegistry.create(JaymosPowers.id("web_held"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** On a creature hitched to a block by a web: where the line is tied (clients draw it, and ragdoll the creature). */
	public static final AttachmentType<java.util.List<WebHitches.Line>> HITCH = AttachmentRegistry.create(JaymosPowers.id("web_hitch"),
		builder -> builder.syncWith(WebHitches.Line.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()), AttachmentSyncPredicate.all()));
	/** Reach for tying a held creature off to a block (release Yank while aiming at it). */
	public static final double HITCH_REACH = 24.0;
	/** The off-hand swing web, if one's out. */
	public static final AttachmentType<WebLine> LINE_OFF = AttachmentRegistry.create(JaymosPowers.id("web_line_off"),
		builder -> builder.syncWith(WebLine.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** Cooldown end times, for the owner's HUD. */
	public static final AttachmentType<WebCooldowns> COOLDOWNS = AttachmentRegistry.create(JaymosPowers.id("web_cooldowns"),
		builder -> builder.syncWith(WebCooldowns.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** Server-only bookkeeping (not saved, not synced). */
	private static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosPowers.id("web_state"));

	public static Item WEB_BALL_ITEM;
	public static EntityType<WebBall> WEB_BALL;
	/** The symbiote's goo, found at the heart of a meteor crater. */
	public static net.minecraft.world.level.block.Block GOO;

	private static final class State {
		/** Which hands have a swing web stuck ({@link WebNet#MAIN}, {@link WebNet#OFF}). */
		final boolean[] swinging = new boolean[2];
		/** Ticks left of a zip / yank-to pull the client is doing. */
		int pullTicks;
		/** No fall damage until landing. */
		boolean grace;
		int graceTicks;
		/** Ticks left on a zip/yank line (swing lines last while swinging). */
		int lineTicks;
		/** Ticks the wings have been spread (0 = folded). */
		int wingsTicks;
		/** Ticks left of the wings' Feather Falling (it lingers a moment past folding, for the landing). */
		int featherTicks;
		/** Ticks a player on the Yank line has held it stretched. */
		int tetherStrain;
		/** A webline's first end, while B is held aimed at a block (null otherwise). */
		@org.jspecify.annotations.Nullable Vec3 weblineFrom;
		/** Game time a Web Shot charge started, or -1. */
		long shotChargeStart = -1;
		/** The entity held by the Yank web (-1 for none), and whether it was mid-swing when caught. */
		int tetherId = -1;
		boolean tetherWasSwinging;
	}

	private WebPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}: register the power and everything it needs. */
	public static void init() {
		Powers.register(INSTANCE);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("web_ball"));
		WEB_BALL_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new Item(new Item.Properties().setId(itemKey)));
		ResourceKey<EntityType<?>> entityKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("web_ball"));
		WEB_BALL = Registry.register(BuiltInRegistries.ENTITY_TYPE, entityKey,
			EntityType.Builder.<WebBall>of(WebBall::new, MobCategory.MISC)
				.noLootTable()
				.sized(0.3F, 0.3F)
				.clientTrackingRange(4)
				.updateInterval(10)
				.build(entityKey));

		// the symbiote's goo, and the meteor craters it's found in
		ResourceKey<net.minecraft.world.level.block.Block> gooKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("web_symbiote_goo"));
		GOO = Registry.register(BuiltInRegistries.BLOCK, gooKey, new WebSymbioteGooBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
			.mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
			.strength(-1.0F, 3600000.0F)
			.noLootTable()
			.noOcclusion()
			.sound(net.minecraft.world.level.block.SoundType.HONEY_BLOCK)
			.pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE)
			.setId(gooKey)));
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("web_crater"), WebCraterFeature.CODEC);
		net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
			net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld()
				.and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_OCEAN).negate())
				.and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN).negate())
				.and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_RIVER).negate())
				.and(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_BEACH).negate()),
			net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id("web_crater")));

		WebNet.register();
		WebTraps.register();
		WebHitches.register();
		WebBridges.register();
		WebTendrils.register();
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WebTendrils.releaseAll(handler.player, false);
			WebTendrils.forget(handler.player);
		});
		WebSymbiote.register();
		WebAscension.register();
		WebDebugCommand.register();
		ServerPlayNetworking.registerGlobalReceiver(WebNet.Swing.TYPE, (payload, ctx) -> INSTANCE.onSwing(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(WebNet.Wings.TYPE, (payload, ctx) -> INSTANCE.onWings(ctx.player(), payload.open()));
		// a held target that hits anything breaks free
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			Entity attacker = source.getEntity();
			if (attacker == null || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			for (ServerPlayer caster : level.players()) {
				State state = caster.getAttached(STATE);
				if (state != null && state.tetherId == attacker.getId()) {
					snapTether(caster, state);
				}
			}
		});
	}

	@Override
	public Identifier id() {
		return ID;
	}

	/** Swinging, the second web, Yank, and the charged web blast last. */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_4, PowerKey.ABILITY_3};
	}

	@Override
	public int color() {
		return 0xE0303A;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.COBWEB);
	}

	// ---- lifecycle ------------------------------------------------------------------------------------------------

	@Override
	public void onGained(ServerPlayer player) {
		ensurePassive(player);
	}

	@Override
	public void onLost(ServerPlayer player) {
		AttributeInstance safeFall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
		if (safeFall != null) {
			safeFall.removeModifier(SAFE_FALL_ID);
		}
		feather(player, false);
		player.removeAttached(STATE);
		player.removeAttached(LINE);
		player.removeAttached(LINE_OFF);
		player.removeAttached(WINGS);
		player.removeAttached(TETHER);
		player.removeAttached(COOLDOWNS);
		WebTendrils.releaseAll(player, false);
		WebSymbiote.onLost(player);
		WebAscension.onLost(player);
	}

	/** Bonded to a symbiote, the badge says so (and something worse once ascended too). */
	@Override
	public String statusSuffix(Player player) {
		if (!WebSymbiote.isBonded(player)) {
			return null;
		}
		return com.jaymospowers.core.Fate.isAscended(player) ? ".symbiote_ascended" : ".symbiote";
	}

	/** The Power Mode key (Z) is Web Zip; it unlocks with the second web (G). */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (!down) {
			return;
		}
		if (!com.jaymospowers.core.Mastery.isUnlocked(player, PowerKey.ABILITY_2)) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.locked")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
			return;
		}
		zip(player);
	}

	/** The ascended ability (C): Web Strike. */
	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		WebAscension.ultimate(player, down);
	}

	private static boolean swinging(State state) {
		return state.swinging[WebNet.MAIN] || state.swinging[WebNet.OFF];
	}

	/** The synced line for a hand's web. */
	public static AttachmentType<WebLine> line(int hand) {
		return hand == WebNet.OFF ? LINE_OFF : LINE;
	}

	/** The off-hand web needs a free hand to shoot from. */
	public static boolean offHandFree(Player player) {
		return player.getOffhandItem().isEmpty();
	}

	private static void ensurePassive(ServerPlayer player) {
		AttributeInstance safeFall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
		// (there used to be a safe-fall bonus; fall damage now always applies, so clear it off anyone who has it)
		if (safeFall != null && safeFall.hasModifier(SAFE_FALL_ID)) {
			safeFall.removeModifier(SAFE_FALL_ID);
		}
	}

	private static void feather(ServerPlayer player, boolean on) {
		AttributeInstance fall = player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
		if (fall == null) {
			return;
		}
		if (on) {
			fall.addOrUpdateTransientModifier(new AttributeModifier(FEATHER_ID, -WINGS_FEATHER_FALLING, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			fall.removeModifier(FEATHER_ID);
		}
	}

	private static State state(ServerPlayer player) {
		State state = player.getAttached(STATE);
		if (state == null) {
			state = new State();
			player.setAttached(STATE, state);
		}
		return state;
	}

	private static boolean inGrace(ServerPlayer player) {
		State state = player.getAttached(STATE);
		return state != null && (state.grace || swinging(state) || state.pullTicks > 0);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player) {
		return inGrace(player) || WebSymbiote.allowsFloating(player);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player) {
		return inGrace(player);
	}

	@Override
	public void tick(ServerPlayer player) {
		if (player.tickCount % 20 == 0) {
			ensurePassive(player);
		}
		State state = player.getAttached(STATE);
		WebSymbiote.tick(player);
		WebTendrils.tick(player);
		WebAscension.tick(player, state != null && swinging(state));
		if (state == null) {
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			if (state.featherTicks > 0) {
				feather(player, false);
			}
			player.removeAttached(STATE);
			player.removeAttached(LINE);
			player.removeAttached(LINE_OFF);
			player.removeAttached(WINGS);
			player.removeAttached(TETHER);
			return;
		}
		if (state.tetherId >= 0) {
			tickTether(player, state);
		}
		if (state.wingsTicks > 0 && (++state.wingsTicks > WINGS_TICKS + 20 || player.isInWater() || player.onGround() && state.wingsTicks > 3)) {
			state.wingsTicks = 0;
			player.removeAttached(WINGS);
		}
		// the wings break a fall like Feather Falling (fall damage itself still applies)
		if (player.getAttached(WINGS) != null) {
			if (state.featherTicks == 0) {
				feather(player, true);
			}
			state.featherTicks = WINGS_FEATHER_LINGER;
		} else if (state.featherTicks > 0 && --state.featherTicks == 0) {
			feather(player, false);
		}
		if (state.pullTicks > 0) {
			state.pullTicks--;
		}
		if (state.lineTicks > 0 && --state.lineTicks == 0 && !state.swinging[WebNet.MAIN]) {
			player.removeAttached(LINE);
		}
		for (int hand = WebNet.MAIN; hand <= WebNet.OFF; hand++) {
			if (!state.swinging[hand]) {
				continue;
			}
			boolean broken = false;
			if (player.tickCount % 10 == 0) {
				// the block it was stuck to is gone
				WebLine line = player.getAttached(line(hand));
				broken = line == null || !WebAnchors.nearSolid(player.level(), line.anchor());
			}
			if (broken || hand == WebNet.OFF && !offHandFree(player)) {
				dropSwing(player, state, hand);
				ServerPlayNetworking.send(player, new WebNet.SwingReject(hand));
			}
		}
		if (state.grace || swinging(state) || state.pullTicks > 0) {
			// fall damage always applies; only a wing glide (slower than 0.5 b/t down) isn't a fall, as with an elytra
			if (player.getAttached(WINGS) != null && player.getY() - player.yo > -0.5) {
				player.fallDistance = Math.min(player.fallDistance, 1.0);
			}
			state.graceTicks++;
			boolean landed = player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying
				|| player.isPassenger() || player.onClimbable();
			if (!swinging(state) && state.pullTicks == 0 && (landed && state.graceTicks > 3 || state.graceTicks > GRACE_MAX_TICKS)) {
				state.grace = false;
			}
		}
	}

	// ---- swing (reported by the client) -----------------------------------------------------------------------------

	private void onSwing(ServerPlayer player, WebNet.Swing payload) {
		int hand = payload.hand();
		if (hand != WebNet.MAIN && hand != WebNet.OFF) {
			return;
		}
		if (!Powers.has(player, this) || !player.isAlive() || player.isSpectator()) {
			if (payload.attach()) {
				ServerPlayNetworking.send(player, new WebNet.SwingReject(hand));
			}
			return;
		}
		State state = state(player);
		if (!payload.attach()) {
			if (state.swinging[hand]) {
				dropSwing(player, state, hand);
			}
			return;
		}
		Vec3 anchor = payload.anchor();
		if (!WebAnchors.valid(player, anchor, WebAnchors.SWING_RANGE * WebSymbiote.reachScale(player) + 4.0) || hand == WebNet.OFF && !offHandFree(player)) {
			ServerPlayNetworking.send(player, new WebNet.SwingReject(hand));
			return;
		}
		state.swinging[hand] = true;
		state.grace = true;
		state.graceTicks = 0;
		state.pullTicks = 0;
		if (hand == WebNet.MAIN) {
			state.lineTicks = 0;
		}
		player.setAttached(line(hand), new WebLine(WebLine.SWING, anchor, -1));
		ServerLevel level = player.level();
		// the swinger hears their own thwip client-side; this is for everyone else
		level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8F, 1.8F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, anchor.x, anchor.y, anchor.z, 5, 0.15, 0.15, 0.15, 0.01);
	}

	private void onWings(ServerPlayer player, boolean open) {
		if (!Powers.has(player, this) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		State state = state(player);
		if (open) {
			state.wingsTicks = 1;
			state.grace = true;
			state.graceTicks = 0;
			player.setAttached(WINGS, true);
			ServerLevel level = player.level();
			level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.7F, 1.4F);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.6, 0.2, 0.6, 0.02);
		} else {
			state.wingsTicks = 0;
			player.removeAttached(WINGS);
		}
	}

	private static void dropSwing(ServerPlayer player, State state, int hand) {
		state.swinging[hand] = false;
		state.graceTicks = 0;
		if (hand == WebNet.OFF || state.lineTicks == 0) {
			player.removeAttached(line(hand));
		}
	}

	// ---- abilities --------------------------------------------------------------------------------------------------

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_3) {
			// V charges on the way down and fires on the way up
			if (down) {
				startShot(player);
			} else {
				shoot(player);
			}
			return;
		}
		if (key == PowerKey.ABILITY_4 && !down) {
			State state = player.getAttached(STATE);
			// a webline being strung: let go aimed at its other end
			if (state != null && state.weblineFrom != null) {
				finishWebline(player, state);
				return;
			}
			// letting go of Yank lets go of whatever it's holding; aimed at a block, it ties it off there instead
			if (state != null && state.tetherId >= 0) {
				Entity target = player.level().getEntity(state.tetherId);
				releaseTether(player, state);
				if (target != null && target.isAlive()) {
					Vec3 eye = player.getEyePosition();
					BlockHitResult hit = WebAnchors.clip(player, eye, eye.add(player.getViewVector(1.0F).scale(HITCH_REACH * WebSymbiote.reachScale(player))));
					if (hit.getType() == HitResult.Type.BLOCK) {
						WebHitches.hitch(player.level(), player, target, hit.getLocation(), hit.getBlockPos(), hit.getDirection());
						thwip(player);
					}
				}
			}
			return;
		}
		if (!down) {
			return;
		}
		if (key == PowerKey.ABILITY_4 && com.jaymospowers.core.Mastery.starterOnly(player)) {
			// a fresh awakening can only string weblines
			startWebline(player);
			return;
		}
		switch (key) {
			// G is the off-hand swing web (all client-side); Zip is on the Power Mode key (see onMode)
			case ABILITY_4 -> yank(player);
			default -> {
			}
		}
	}

	/** Mastered (every ability): B has no cooldown at all, and up to five lines can be out (see WebHitches). */
	static boolean mastered(Player player) {
		return com.jaymospowers.core.Mastery.unlocked(player) >= com.jaymospowers.core.Mastery.ALL;
	}

	static int yankCooldown(Player player) {
		return mastered(player) ? 0 : YANK_COOLDOWN;
	}

	/** The starter, on B (kept for good): weblines. */
	@Override
	public PowerKey starterKey() {
		return PowerKey.ABILITY_4;
	}

	/** B down aimed at a block: the webline's first end sticks there, and the line pays out from your hand. */
	private static void startWebline(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (now < cooldowns(player).yank()) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = WebAnchors.clip(player, eye, eye.add(player.getViewVector(1.0F).scale(WebBridges.REACH * WebSymbiote.reachScale(player))));
		if (hit.getType() != HitResult.Type.BLOCK) {
			fwip(player);
			return;
		}
		State state = state(player);
		state.weblineFrom = hit.getLocation();
		showLine(player, new WebLine(WebLine.ZIP, hit.getLocation(), -1), 200);
		thwip(player);
	}

	/** B let go: aimed at a second block within reach, the line is pulled taut between the two and becomes walkable. */
	private static void finishWebline(ServerPlayer player, State state) {
		Vec3 from = state.weblineFrom;
		state.weblineFrom = null;
		state.lineTicks = 1;
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = WebAnchors.clip(player, eye, eye.add(player.getViewVector(1.0F).scale(WebBridges.REACH * WebSymbiote.reachScale(player))));
		if (from == null || hit.getType() != HitResult.Type.BLOCK || !WebBridges.string(player.level(), from, hit.getLocation())) {
			fwip(player);
			return;
		}
		WebCooldowns cooldowns = cooldowns(player);
		player.setAttached(COOLDOWNS, new WebCooldowns(cooldowns.zip(), cooldowns.shot(),
			player.level().getGameTime() + (mastered(player) ? 0 : WebBridges.COOLDOWN)));
		thwip(player);
	}

	private static WebCooldowns cooldowns(ServerPlayer player) {
		WebCooldowns cooldowns = player.getAttached(COOLDOWNS);
		return cooldowns == null ? WebCooldowns.NONE : cooldowns;
	}

	private static void fwip(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4F, 2.0F);
	}

	private static void thwip(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.9F, 1.7F);
	}

	/** The Yank web holding on: a lead-like pull back toward you past its length, or a snap. */
	private static void tickTether(ServerPlayer player, State state) {
		Entity target = player.level().getEntity(state.tetherId);
		if (target == null || !target.isAlive() || target.isRemoved() || target.level() != player.level()
			|| !PowerInput.isDown(player, PowerKey.ABILITY_4)) {
			releaseTether(player, state);
			return;
		}
		Vec3 hand = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0);
		Vec3 to = hand.subtract(target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
		double distance = to.length();
		boolean swinging = target instanceof LivingEntity living && living.isSwinging();
		if (distance > TETHER_BREAK || swinging && !state.tetherWasSwinging) {
			// pulled too far, or a player fought it off
			snapTether(player, state);
			return;
		}
		state.tetherWasSwinging = swinging;
		boolean player2 = target instanceof Player;
		// a caught player can lean away against the line until it gives
		if (player2 && distance > TETHER_LENGTH + PLAYER_STRAIN_OVER) {
			if (++state.tetherStrain >= PLAYER_STRAIN_TICKS) {
				snapTether(player, state);
				return;
			}
		} else if (state.tetherStrain > 0) {
			state.tetherStrain--;
		}
		if (distance > TETHER_LENGTH) {
			Vec3 dir = to.scale(1.0 / distance);
			Vec3 v = target.getDeltaMovement();
			double inward = v.dot(dir);
			double want = player2
				? Math.min((distance - TETHER_LENGTH) * PLAYER_TETHER_SPRING, PLAYER_TETHER_MAX_PULL)
				: Math.min((distance - TETHER_LENGTH) * TETHER_SPRING, TETHER_MAX_PULL);
			if (inward < want) {
				target.setDeltaMovement(v.add(dir.scale(want - inward)));
				target.needsSync = true;
				target.syncVelocity = true;
			}
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		if (player.tickCount % 6 == 0) {
			Vec3 mid = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).add(to.scale(0.5));
			player.level().sendParticles(ParticleTypes.ITEM_COBWEB, mid.x, mid.y, mid.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	private static void releaseTether(ServerPlayer player, State state) {
		state.tetherStrain = 0;
		Entity target = player.level().getEntity(state.tetherId);
		if (target != null) {
			target.removeAttached(HELD);
		}
		state.tetherId = -1;
		player.removeAttached(TETHER);
	}

	/** The line breaks under strain. */
	private static void snapTether(ServerPlayer player, State state) {
		Entity target = player.level().getEntity(state.tetherId);
		releaseTether(player, state);
		Vec3 at = target == null ? player.position() : target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.level().sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.05);
	}

	/** Web Zip (Z): fire a web at the crosshair block and get yanked to it. */
	private void zip(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebCooldowns cooldowns = cooldowns(player);
		if (now < cooldowns.zip()) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = WebAnchors.clip(player, eye, eye.add(player.getViewVector(1.0F).scale(ZIP_RANGE * WebSymbiote.reachScale(player))));
		if (hit.getType() == HitResult.Type.MISS) {
			fwip(player);
			return;
		}
		Vec3 anchor = hit.getLocation();
		Direction face = hit.getDirection();
		Vec3 target;
		if (face == Direction.UP) {
			target = anchor.add(0.0, 0.05, 0.0);
		} else if (face == Direction.DOWN) {
			target = anchor.add(0.0, -player.getBbHeight() - 0.1, 0.0);
		} else {
			// hang on the wall with the hands at the hit point; the client hops up onto the ledge at the end
			target = anchor.add(face.getUnitVec3().scale(player.getBbWidth() * 0.5 + 0.2)).add(0.0, -1.3, 0.0);
		}
		int maxTicks = Mth.ceil(target.distanceTo(player.position()) / ZIP_SPEED) + 12;
		startPull(player, new WebNet.Pull(anchor, target, -1, maxTicks, (byte)face.ordinal()), new WebLine(WebLine.ZIP, anchor, -1));
		player.setAttached(COOLDOWNS, new WebCooldowns(now + WebSymbiote.zipCooldown(player), cooldowns.shot(), cooldowns.yank()));
		thwip(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.6F, 1.5F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, anchor.x, anchor.y, anchor.z, 6, 0.15, 0.15, 0.15, 0.01);
	}

	/** The entity under the crosshair within {@code range} (not behind a block), or null. */
	static @org.jspecify.annotations.Nullable Entity entityInSight(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult block = WebAnchors.clip(player, eye, end);
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		Vec3 to = end;
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, to, box,
			e -> !e.isSpectator() && e.isPickable() && e != player.getVehicle() && !player.hasPassenger(e), eye.distanceToSqr(to));
		return hit == null ? null : hit.getEntity();
	}

	/** Shows a (zip/yank-style) line from the main hand for {@code ticks}, unless a swing web is out. */
	static void showLine(ServerPlayer player, WebLine line, int ticks) {
		State state = state(player);
		if (state.swinging[WebNet.MAIN]) {
			return;
		}
		state.lineTicks = ticks;
		player.setAttached(LINE, line);
	}

	static void startPull(ServerPlayer player, WebNet.Pull pull, WebLine line) {
		State state = state(player);
		state.swinging[WebNet.MAIN] = false;
		state.swinging[WebNet.OFF] = false;
		player.removeAttached(LINE_OFF);
		state.pullTicks = pull.maxTicks() + 5;
		state.lineTicks = pull.maxTicks() + 2;
		state.grace = true;
		state.graceTicks = 0;
		player.setAttached(LINE, line);
		ServerPlayNetworking.send(player, pull);
	}

	/** V down: start charging a web blast (if it's ready). */
	private void startShot(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (now < cooldowns(player).shot()) {
			return;
		}
		state(player).shotChargeStart = now;
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	/** V up: fire the web blast, as strong as it was charged. */
	private void shoot(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		State state = player.getAttached(STATE);
		if (state == null || state.shotChargeStart < 0) {
			return;
		}
		float charge = Math.min(1.0F, (now - state.shotChargeStart) / (float)SHOT_CHARGE_TICKS);
		state.shotChargeStart = -1;
		WebCooldowns cooldowns = cooldowns(player);
		if (now < cooldowns.shot()) {
			return;
		}
		Projectile.spawnProjectileFromRotation((lvl, owner, stack) -> new WebBall(lvl, owner, stack).charged(charge), level,
			new ItemStack(WEB_BALL_ITEM), player, 0.0F, SHOT_SPEED, 0.5F);
		int cooldown = Mth.lerpInt(charge, SHOT_COOLDOWN, SHOT_COOLDOWN_MAX);
		player.setAttached(COOLDOWNS, new WebCooldowns(cooldowns.zip(), now + cooldown, cooldowns.yank()));
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.7F);
	}

	/** B: web the entity under the crosshair and pull it in (or, if it's big, pull yourself to it). */
	private void yank(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebCooldowns cooldowns = cooldowns(player);
		if (now < cooldowns.yank()) {
			return;
		}
		Entity target = entityInSight(player, YANK_RANGE * WebSymbiote.reachScale(player));
		if (target == null) {
			// nothing to yank: B aimed at a block starts a webline instead
			startWebline(player);
			return;
		}
		Vec3 center = target.getBoundingBox().getCenter();
		double volume = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
		if (volume > YANK_HEAVY_VOLUME) {
			// too big to budge: reel yourself in instead
			int maxTicks = Mth.ceil(center.distanceTo(player.position()) / ZIP_SPEED) + 12;
			startPull(player, new WebNet.Pull(center, target.position(), target.getId(), maxTicks, (byte)-1),
				new WebLine(WebLine.YANK, center, target.getId()));
		} else {
			Vec3 to = player.position().subtract(target.position());
			double horizontal = to.horizontalDistance();
			// roughly ballistic: lands it a couple of blocks in front of you
			double speed = Math.min(2.0, Math.max(0.0, horizontal - 2.0) * 0.14 + 0.15);
			double up = Mth.clamp(0.42 + to.y * 0.08 + horizontal * 0.008, 0.25, 1.1);
			Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize().scale(speed);
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			target.setDeltaMovement(flat.x, up, flat.z);
			target.needsSync = true;
			target.syncVelocity = true;
			// and keep hold of it for as long as Yank is held
			State state = state(player);
			state.tetherId = target.getId();
			state.tetherWasSwinging = target instanceof LivingEntity living && living.isSwinging();
			player.setAttached(TETHER, target.getId());
			target.setAttached(HELD, true);
		}
		player.setAttached(COOLDOWNS, new WebCooldowns(cooldowns.zip(), cooldowns.shot(), now + yankCooldown(player)));
		thwip(player);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, center.x, center.y, center.z, 8, 0.2, 0.2, 0.2, 0.02);
	}
}
