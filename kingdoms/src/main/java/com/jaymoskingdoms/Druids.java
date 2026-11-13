package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Druid: wild shape into beasts they have studied, and nature's rites.
 *
 * <ul>
 * <li><b>Studying.</b> A Druid knows a beast's shape ({@link #WOLF}, {@link #BEAR}, {@link #HAWK}, {@link #FISH}) once they have
 * either fought it ({@link FoeLore} one star of that kind of beast) or watched it a while: {@link #STUDY_TICKS} of calm
 * looking (standing still, sneaking or walking slowly) within {@link #STUDY_RANGE} blocks of one in plain sight.</li>
 * <li><b>The shape.</b> The class key takes the selected shape (sneak and the key picks the next one studied) for
 * {@link #SHAPE_TICKS}, or lets it go sooner. It is a choice with a timer, not a curse ({@link WerewolfPlayers} is the curse): a
 * stand-in beast takes the body's place for everyone's eyes and a blow to it is a blow to the Druid. Each shape gives its
 * strengths and a price: the wolf is quick and keen-nosed, the bear tough and strong but slow, the hawk flies, light
 * and slow-falling but frail, the fish swift and breathing in water but helpless on land. A beast casts nothing (no focus, no
 * scroll). {@link #SHAPES} shapes come back with each short rest ({@link Rests}). A Druid downed or dead is themselves again.</li>
 * <li><b>Rites.</b> The sprig casts the spell it is turned to (plant growth, moonbeam, and the rest of nature's rites from
 * scrolls, {@link ClassCasters}); control weather and speak with animals are the Druid's own rituals ({@link NatureRites}).</li>
 * </ul>
 */
public final class Druids {
	public static Item SPRIG;
	public static final String WOLF = "wolf", BEAR = "bear", HAWK = "hawk", FISH = "fish";
	public static final List<String> FORMS = List.of(WOLF, BEAR, HAWK, FISH);
	/** How long a shape lasts (ticks), how many a short rest brings back, and the pause after letting one go (ticks). */
	public static final int SHAPE_TICKS = 20 * 60 * 3;
	static final int SHAPES = 2, AFTER_TICKS = 100;
	/** Watching a beast: how long it takes (ticks of calm looking), how near (blocks). */
	static final int STUDY_TICKS = 20 * 90;
	static final double STUDY_RANGE = 8.0, STUDY_SPEED = 0.03;
	/** Each shape's extra health, the bear's push-back resistance and the frail hawk's loss. */
	/** The hawk flies: how fast (a player's own creative flight is 0.05), and the tag that marks wings to take back. */
	static final float HAWK_FLY_SPEED = 0.035F, OWN_FLY_SPEED = 0.05F;
	static final String WINGS_TAG = "jaymoskingdoms_hawk_wings";
	static final double WOLF_HEALTH = 4.0, BEAR_HEALTH = 10.0, HAWK_HEALTH = -4.0, FISH_HEALTH = 0.0, BEAR_KNOCKBACK = 0.5;
	/** The stand-in beasts' kin: the entity paths each shape is studied from. */
	static final Map<String, List<String>> KIN = Map.of(WOLF, List.of("wolf", "feral_wolf", "coyote"), BEAR, List.of("polar_bear", "grizzly"),
		HAWK, List.of("parrot", "pigeon", "bat"), FISH, List.of("cod", "salmon", "tropical_fish", "pufferfish"));

	/** The beasts watched so far: kind → ticks. Saved, kept through death. */
	static final AttachmentType<Map<String, Integer>> WATCHED = AttachmentRegistry.create(JaymosKingdoms.id("druid_watched"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
	/** The shape the key will take. Saved. */
	static final AttachmentType<String> SELECTED = AttachmentRegistry.create(JaymosKingdoms.id("druid_selected"),
		b -> b.persistent(Codec.STRING).copyOnDeath());
	/** The shape a Druid wears now ("" for none), for every client's eyes (the body is hidden by it). Not saved. */
	public static final AttachmentType<String> FORM = AttachmentRegistry.create(JaymosKingdoms.id("druid_form"),
		b -> b.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	private static final Identifier HEALTH = JaymosKingdoms.id("druid_shape_health");
	private static final Identifier KNOCKBACK = JaymosKingdoms.id("druid_shape_knockback");

	/** A shape worn now: its stand-in, and the game time it ends. */
	private static final class Shape {
		final Mob beast;
		final long until;

		Shape(Mob beast, long until) {
			this.beast = beast;
			this.until = until;
		}
	}

	private static final Map<UUID, Shape> SHAPES_WORN = new HashMap<>();
	private static final Map<UUID, Long> RESTING = new HashMap<>();

	private Druids() {
	}

	static void register() {
		SPRIG = Arcana.item("druid_sprig", p -> new CastingFocus(p.stacksTo(1), false));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(SPRIG));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Druids::tick);
		// a beast casts nothing: no focus, no scroll, no staff
		UseItemCallback.EVENT.register((player, level, hand) -> player instanceof ServerPlayer sp && shaped(sp) && casts(player.getItemInHand(hand))
			? InteractionResult.FAIL : InteractionResult.PASS);
		// a blow to the stand-in is a blow to the Druid
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (SHAPES_WORN.isEmpty() || !(entity instanceof Mob mob) || mob.level().getServer() == null) {
				return true;
			}
			for (Map.Entry<UUID, Shape> e : SHAPES_WORN.entrySet()) {
				if (e.getValue().beast == mob) {
					if (mob.level().getServer().getPlayerList().getPlayer(e.getKey()) instanceof ServerPlayer owner && source.getEntity() != owner
						&& owner.level() instanceof ServerLevel level) {
						owner.hurtServer(level, source, amount);
					}
					return false;
				}
			}
			return true;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player, false));
		// (wings left on by a crash are taken back at the door)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> fold(handler.player));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				end(player, false);
			}
		});
	}

	static void clear() {
		SHAPES_WORN.clear();
		RESTING.clear();
	}

	static void begin(ServerPlayer player) {
		player.setAttached(SELECTED, "");
	}

	/** The class is unmade: the shape is shed. */
	static void unmake(ServerPlayer player) {
		end(player, false);
	}

	private static boolean casts(ItemStack stack) {
		return stack.getItem() instanceof CastingFocus || Arcana.isStaff(stack) || stack.getItem() instanceof Arcana.Scroll || stack.getItem() instanceof ManaWand;
	}

	/** Whether {@code player} wears a shape now. */
	public static boolean shaped(Player player) {
		return !player.getAttachedOrElse(FORM, "").isEmpty();
	}

	/** Whether {@code player} has studied {@code form}. */
	public static boolean knows(Player player, String form) {
		if (player.isCreative()) {
			return true;
		}
		if (player.getAttachedOrElse(WATCHED, Map.<String, Integer>of()).getOrDefault(form, 0) >= STUDY_TICKS) {
			return true;
		}
		for (String kind : KIN.get(form)) {
			if (FoeLore.stars(player, kind) >= 1) {
				return true;
			}
		}
		return false;
	}

	private static List<String> known(Player player) {
		return FORMS.stream().filter(f -> knows(player, f)).toList();
	}

	// ---- the key ------------------------------------------------------------------------------------------------------------------

	/** The class key: take the selected shape or let it go; with sneak, pick the next one studied. */
	static void key(ServerPlayer player, boolean sneak) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		List<String> known = known(player);
		if (sneak && !shaped(player)) {
			if (known.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.none"));
				return;
			}
			String now = player.getAttachedOrElse(SELECTED, "");
			String next = known.get((known.indexOf(now) + 1) % known.size());
			player.setAttached(SELECTED, next);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.selected", Component.translatable("druid.jaymoskingdoms.form." + next)));
			return;
		}
		if (shaped(player)) {
			end(player, true);
			return;
		}
		if (known.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.none"));
			return;
		}
		String form = player.getAttachedOrElse(SELECTED, "");
		if (!known.contains(form)) {
			form = known.getFirst();
		}
		Long rest = RESTING.get(player.getUUID());
		if (rest != null && rest > level.getGameTime()) {
			return;
		}
		if (Downed.isDowned(player) || player.isPassenger() && !player.isCreative()) {
			return;
		}
		if (!Rests.spend(player, Rests.SHAPE, 1, SHAPES)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.tired"));
			return;
		}
		take(player, level, form);
	}

	private static void take(ServerPlayer player, ServerLevel level, String form) {
		Mob beast = stand(level, form);
		if (beast == null) {
			return;
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		follow(player, beast);
		level.addFreshEntity(beast);
		SHAPES_WORN.put(player.getUUID(), new Shape(beast, level.getGameTime() + SHAPE_TICKS));
		player.setAttached(FORM, form);
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		double extra = switch (form) {
			case WOLF -> WOLF_HEALTH;
			case BEAR -> BEAR_HEALTH;
			case HAWK -> HAWK_HEALTH;
			default -> FISH_HEALTH;
		};
		if (health != null && extra != 0.0) {
			health.removeModifier(HEALTH);
			health.addTransientModifier(new AttributeModifier(HEALTH, extra, AttributeModifier.Operation.ADD_VALUE));
			if (extra > 0.0) {
				player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + (float) extra));
			}
		}
		if (form.equals(BEAR)) {
			AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (kb != null) {
				kb.removeModifier(KNOCKBACK);
				kb.addTransientModifier(new AttributeModifier(KNOCKBACK, BEAR_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		if (form.equals(HAWK)) {
			// a hawk flies
			player.addTag(WINGS_TAG);
			player.getAbilities().mayfly = true;
			player.getAbilities().setFlyingSpeed(HAWK_FLY_SPEED);
			player.onUpdateAbilities();
		}
		buffs(player, form);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.5), player.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
		level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, player.getX(), player.getY(0.5), player.getZ(), 16, 0.5, 0.6, 0.5, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.8F, 1.3F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.shaped", Component.translatable("druid.jaymoskingdoms.form." + form), SHAPE_TICKS / 1200));
	}

	private static @Nullable Mob stand(ServerLevel level, String form) {
		Mob beast = switch (form) {
			case WOLF -> EntityTypes.WOLF.create(level, EntitySpawnReason.EVENT);
			case BEAR -> EntityTypes.POLAR_BEAR.create(level, EntitySpawnReason.EVENT);
			case HAWK -> EntityTypes.PARROT.create(level, EntitySpawnReason.EVENT);
			default -> EntityTypes.COD.create(level, EntitySpawnReason.EVENT);
		};
		if (beast != null) {
			beast.setNoAi(true);
			beast.setPermanentlyInvulnerable(true);
			beast.setSilent(true);
			beast.setNoGravity(true);
			beast.setPersistenceRequired();
			beast.setCustomNameVisible(false);
		}
		return beast;
	}

	private static void follow(ServerPlayer player, Mob beast) {
		beast.setPos(player.getX(), player.getY(), player.getZ());
		beast.setDeltaMovement(player.getDeltaMovement());
		beast.setYRot(player.getYRot());
		beast.setXRot(player.getXRot());
		beast.setYBodyRot(player.yBodyRot);
		beast.setYHeadRot(player.getYHeadRot());
		beast.setOnGround(player.onGround());
	}

	/** A shape's strengths, put on for a moment more (so they lapse by themselves). */
	private static void buffs(ServerPlayer player, String form) {
		boolean wet = player.isInWater();
		switch (form) {
			case WOLF -> {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 0, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
			}
			case BEAR -> {
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 2, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false, false));
			}
			case HAWK -> {
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 3, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, false));
			}
			default -> {
				player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, false));
				if (wet) {
					player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 1, true, false, false));
				} else {
					// a fish out of water flops
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3, true, false, false));
					player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, true, false, false));
				}
			}
		}
	}

	/** Takes the hawk's wings back (creative and spectator keep their own flight). */
	private static void fold(ServerPlayer player) {
		if (!player.removeTag(WINGS_TAG)) {
			return;
		}
		player.getAbilities().setFlyingSpeed(OWN_FLY_SPEED);
		if (!player.isCreative() && !player.isSpectator()) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
		}
		player.onUpdateAbilities();
	}

	/** The shape is shed ({@code pause}: they chose it, and wait a moment before the next). */
	static void end(ServerPlayer player, boolean pause) {
		Shape shape = SHAPES_WORN.remove(player.getUUID());
		if (shape == null) {
			return;
		}
		shape.beast.discard();
		player.removeAttached(FORM);
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(HEALTH);
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
		AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) {
			kb.removeModifier(KNOCKBACK);
		}
		fold(player);
		for (var effect : List.of(MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.JUMP_BOOST, MobEffects.RESISTANCE, MobEffects.SLOWNESS, MobEffects.SLOW_FALLING,
			MobEffects.WATER_BREATHING, MobEffects.DOLPHINS_GRACE, MobEffects.WEAKNESS, MobEffects.NIGHT_VISION)) {
			MobEffectInstance on = player.getEffect(effect);
			if (on != null && on.isAmbient() && !on.isVisible() && on.getDuration() <= 300) {
				player.removeEffect(effect);
			}
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.5), player.getZ(), 16, 0.4, 0.6, 0.4, 0.04);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6F, 1.5F);
			if (pause) {
				RESTING.put(player.getUUID(), level.getGameTime() + AFTER_TICKS);
			}
		}
	}

	// ---- every tick ---------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
				continue;
			}
			Shape shape = SHAPES_WORN.get(player.getUUID());
			if (shape != null) {
				if (!Classes.is(player, PlayerClass.DRUID) || Downed.isDowned(player) || level.getGameTime() >= shape.until) {
					end(player, true);
					continue;
				}
				Mob beast = shape.beast;
				if (beast.isRemoved() || beast.level() != level) {
					// left behind in another world, or unloaded: a new one where they are
					end(player, false);
					continue;
				}
				follow(player, beast);
				if ((level.getGameTime() + player.getId()) % 20 == 0) {
					buffs(player, player.getAttachedOrElse(FORM, WOLF));
				}
			} else if (Classes.is(player, PlayerClass.DRUID) && (level.getGameTime() + player.getId()) % 20 == 0) {
				study(player, level);
			}
		}
	}

	/** A second of a Druid's calm looking: any beast of the four kinds within reach, in sight, counts. */
	private static void study(ServerPlayer player, ServerLevel level) {
		boolean calm = player.isShiftKeyDown() || player.getDeltaMovement().horizontalDistance() < STUDY_SPEED || Pace.slow(player);
		if (!calm) {
			return;
		}
		Map<String, Integer> watched = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(STUDY_RANGE),
			x -> x.isAlive() && x != player && !(x instanceof Player) && !isStandIn(x))) {
			String path = FoeLore.kind(e);
			for (String form : FORMS) {
				if (KIN.get(form).contains(path) && player.hasLineOfSight(e)) {
					if (watched == null) {
						watched = new HashMap<>(player.getAttachedOrElse(WATCHED, Map.<String, Integer>of()));
					}
					int before = watched.getOrDefault(form, 0);
					if (before < STUDY_TICKS) {
						watched.put(form, Math.min(STUDY_TICKS, before + 20));
						if (before + 20 >= STUDY_TICKS) {
							learned(player, level, form);
						}
					}
				}
			}
		}
		if (watched != null) {
			player.setAttached(WATCHED, watched);
		}
	}

	private static boolean isStandIn(LivingEntity e) {
		for (Shape shape : SHAPES_WORN.values()) {
			if (shape.beast == e) {
				return true;
			}
		}
		return false;
	}

	private static void learned(ServerPlayer player, ServerLevel level, String form) {
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY(1.0), player.getZ(), 14, 0.4, 0.5, 0.4, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.learned", Component.translatable("druid.jaymoskingdoms.form." + form)));
		if (player.getAttachedOrElse(SELECTED, "").isEmpty()) {
			player.setAttached(SELECTED, form);
		}
	}
}
