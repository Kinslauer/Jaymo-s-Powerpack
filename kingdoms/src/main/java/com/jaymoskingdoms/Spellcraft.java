package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.0, stage 2) The casting classes' magic ({@link Classes}).
 *
 * <p><b>The Mage's staff:</b> a wizard's staff in a Mage's hand casts (right-click) the spell on it, one of those they
 * know ({@link Classes.Chosen#spells}); sneak and right-click turns to the next. A Mage casts harder than a scroll
 * ({@link #MAGE_POWER}) and each spell has its own cooldown. A Mage who reads a scroll learns its spell instead of
 * spending it ({@link #learn}); a scroll of a spell they know is read as anyone's.
 *
 * <p><b>The Sorcerer's Imbue:</b> on the {@code J} key a Sorcerer charges the blade in their hand with their element
 * (picked with the class: water, fire, earth, air, lightning or ice) for a while; every blow it lands carries the
 * element ({@link #strike}), and the blade trails its colour. Sneak and {@code J} throws a bolt of the element at what
 * they're looking at instead, on a short cooldown: few spells, quick ones.
 */
public final class Spellcraft {
	/** A Mage's spells hit this much harder (and last longer) than a scroll's. */
	static final float MAGE_POWER = 1.5F;
	/** A Mage's cooldown for each spell (ticks). */
	static final Map<String, Integer> MAGE_COOLDOWN = Map.of("fire", 60, "arcane", 40, "warding", 20 * 45, "draining", 20 * 8, "raising", 20 * 90);
	/** How long an imbued blade keeps its element (ticks); the Imbue's own cooldown; the bolt's. */
	static final int IMBUE_TICKS = 20 * 120, IMBUE_COOLDOWN = 20 * 10, BOLT_COOLDOWN = 40;
	/** The bolt's reach and damage before its element. */
	static final double BOLT_REACH = 20.0;
	static final float BOLT_DAMAGE = 5.0F;
	/** The elements' numbers: fire's burn (s) and extra damage; water's slow (ticks) and the hurt to what fears water;
	 * earth's stagger (ticks); air's lift; lightning's damage, how far it chains and to how many; ice's freeze (ticks). */
	static final float FIRE_SECONDS = 4.0F, FIRE_DAMAGE = 1.0F, WATER_DAMAGE = 6.0F, LIGHTNING_DAMAGE = 3.0F;
	static final int WATER_SLOW = 60, EARTH_STAGGER = 40, ICE_FREEZE = 160, LIGHTNING_CHAIN = 2;
	/** (0.6.7) One blow in this many bursts with the element; the rest only spark. A thrown bolt always does. */
	static final int PROC_ONE_IN = 20;
	/** (0.6.7) Air instead lifts on every this-many-th blow, so the next one lands in the air: an air combo. */
	static final int AIR_EVERY = 2;
	/** (0.6.7) How long a lightning burst blinds, in ticks (0.3 s). */
	static final int LIGHTNING_BLIND = 6;
	static final double AIR_LIFT = 0.9, LIGHTNING_RANGE = 4.0;

	/** Player → server: the Imbue key ({@code bolt}: with sneak, the bolt). */
	public record Imbue(boolean bolt) implements CustomPacketPayload {
		public static final Type<Imbue> TYPE = new Type<>(JaymosKingdoms.id("imbue"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Imbue> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.bolt), buf -> new Imbue(buf.readBoolean()));

		@Override
		public Type<Imbue> type() {
			return TYPE;
		}
	}

	/** Sorcerer → game time their blade's element wears off. */
	private static final Map<UUID, Long> IMBUED = new HashMap<>();
	/** Blows landed since an air blade's last lift, by player. */
	private static final Map<UUID, Integer> AIR_BLOWS = new HashMap<>();

	private Spellcraft() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Imbue.TYPE, Imbue.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Imbue.TYPE, (payload, context) -> imbue(context.player(), payload.bolt()));
		// the Mage's staff
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Arcana.WIZARD_STAFF) || !Classes.is(player, PlayerClass.MAGE)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && world instanceof ServerLevel level) {
				if (sp.isShiftKeyDown()) {
					turn(sp);
				} else {
					castStaff(sp, level, stack);
				}
			}
			return InteractionResult.SUCCESS;
		});
		// an imbued blade's blows
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer sp && source.getDirectEntity() == sp && entity != sp && damageTaken > 0.0F && !blocked
				&& imbued(sp) && entity.level() instanceof ServerLevel level) {
				boolean burst;
				if (element(sp).equals("air")) {
					int blows = AIR_BLOWS.merge(sp.getUUID(), 1, Integer::sum);
					burst = blows % AIR_EVERY == 0;
				} else {
					burst = sp.getRandom().nextInt(PROC_ONE_IN) == 0;
				}
				if (burst) {
					strike(level, sp, entity, false);
				} else {
					level.sendParticles(particle(element(sp)), entity.getX(), entity.getY(0.6), entity.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
				}
			}
		});
	}

	static void clear() {
		IMBUED.clear();
		AIR_BLOWS.clear();
	}

	// ---- the Mage -----------------------------------------------------------------------------------------------

	/** A Mage reading a scroll of a spell they don't know: they learn it. False for anyone else, or a spell they know. */
	static boolean learn(ServerPlayer player, String spell) {
		Classes.Chosen c = Classes.of(player);
		if (c.playerClass() != PlayerClass.MAGE || c.spells().contains(spell)) {
			return false;
		}
		List<String> spells = new ArrayList<>(c.spells());
		spells.add(spell);
		player.setAttached(Classes.CLASS, new Classes.Chosen(c.cls(), c.option(), List.copyOf(spells), c.selected()));
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.4, 0.5, 0.4, 0.8);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spell.learned", spellName(spell)).withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	static Component spellName(String spell) {
		return Component.translatable("class.jaymoskingdoms.option." + spell);
	}

	/** Turns the staff to the next spell the Mage knows. */
	private static void turn(ServerPlayer player) {
		Classes.Chosen c = Classes.of(player);
		if (c.spells().size() < 2) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spell.only", spellName(c.spell())));
			return;
		}
		Classes.Chosen next = new Classes.Chosen(c.cls(), c.option(), c.spells(), Math.floorMod(c.selected() + 1, c.spells().size()));
		player.setAttached(Classes.CLASS, next);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spell.turned", spellName(next.spell())));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	private static void castStaff(ServerPlayer player, ServerLevel level, ItemStack staff) {
		String spell = Classes.of(player).spell();
		if (spell.isEmpty()) {
			return;
		}
		if (player.getCooldowns().isOnCooldown(staff)) {
			return;
		}
		if (!Arcana.cast(spell, level, player, MAGE_POWER)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
			return;
		}
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 16, 0.3, 0.4, 0.3, 0.5);
		player.getCooldowns().addCooldown(staff, MAGE_COOLDOWN.getOrDefault(spell, 40));
		Theft.sorcery(player, level, player.blockPosition());
	}

	// ---- the Sorcerer ---------------------------------------------------------------------------------------------

	static boolean imbued(ServerPlayer player) {
		Long until = IMBUED.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	/** The Sorcerer's element, or "" for anyone else. */
	static String element(ServerPlayer player) {
		Classes.Chosen c = Classes.of(player);
		return c.playerClass() == PlayerClass.SORCERER ? c.option() : "";
	}

	/** A blade the Imbue takes: a sword, or anything the classes count as a blade. */
	private static boolean blade(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ClassPerks.LIGHT_BLADES) || stack.is(ClassPerks.TWO_HANDED);
	}

	private static void imbue(ServerPlayer player, boolean bolt) {
		String element = element(player);
		if (element.isEmpty() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (bolt) {
			bolt(level, player, element);
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (!blade(held)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.imbue.no_blade"));
			return;
		}
		if (player.getCooldowns().isOnCooldown(Arcana.WIZARD_STAFF.getDefaultInstance())) {
			return;
		}
		IMBUED.put(player.getUUID(), level.getGameTime() + IMBUE_TICKS);
		player.getCooldowns().addCooldown(Arcana.WIZARD_STAFF.getDefaultInstance(), IMBUE_COOLDOWN);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.sendParticles(particle(element), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound(element), SoundSource.PLAYERS, 0.8F, 1.1F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.imbue.done", spellName(element)));
		Theft.sorcery(player, level, player.blockPosition());
	}

	/** A bolt of the element at what the Sorcerer is looking at. */
	private static void bolt(ServerLevel level, ServerPlayer player, String element) {
		ItemStack key = Arcana.BONE_STAFF.getDefaultInstance();
		if (player.getCooldowns().isOnCooldown(key)) {
			return;
		}
		LivingEntity target = Arcana.sighted(player);
		if (target == null || player.distanceTo(target) > BOLT_REACH) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
			return;
		}
		player.getCooldowns().addCooldown(key, BOLT_COOLDOWN);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Vec3 start = player.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 step = target.getBoundingBox().getCenter().subtract(start);
		int n = Math.max(2, (int) (step.length() * 3.0));
		ParticleOptions trail = particle(element);
		for (int i = 0; i <= n; i++) {
			Vec3 p = start.add(step.scale(i / (double) n));
			level.sendParticles(trail, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound(element), SoundSource.PLAYERS, 1.0F, 1.3F);
		target.hurtServer(level, level.damageSources().indirectMagic(player, player), BOLT_DAMAGE);
		strike(level, player, target, true);
		Theft.sorcery(player, level, player.blockPosition());
	}

	/** The element bursting on a blow {@code player} landed on {@code target} ({@code bolt}: thrown, not struck). */
	static void strike(ServerLevel level, ServerPlayer player, LivingEntity target, boolean bolt) {
		String element = element(player);
		Vec3 away = target.position().subtract(player.position()).normalize();
		switch (element) {
			case "fire" -> {
				target.igniteForSeconds(FIRE_SECONDS);
				target.hurtServer(level, level.damageSources().indirectMagic(player, player), FIRE_DAMAGE);
			}
			case "water" -> {
				target.clearFire();
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WATER_SLOW, 1), player);
				if (target.isSensitiveToWater() || target.fireImmune()) {
					target.hurtServer(level, level.damageSources().indirectMagic(player, player), WATER_DAMAGE);
				}
			}
			case "earth" -> {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EARTH_STAGGER, 2), player);
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EARTH_STAGGER, 0), player);
				target.setDeltaMovement(target.getDeltaMovement().add(away.x * 0.6, -0.4, away.z * 0.6));
				target.syncVelocity = true;
			}
			case "air" -> {
				target.setDeltaMovement(target.getDeltaMovement().add(away.x * 0.3, AIR_LIFT, away.z * 0.3));
				target.syncVelocity = true;
			}
			case "lightning" -> {
				target.hurtServer(level, level.damageSources().indirectMagic(player, player), LIGHTNING_DAMAGE);
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, LIGHTNING_BLIND, 0), player);
				LightningBolt flash = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (flash != null) {
					flash.setVisualOnly(true);
					flash.setPos(target.getX(), target.getY(), target.getZ());
					level.addFreshEntity(flash);
				}
				int chained = 0;
				for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(LIGHTNING_RANGE),
					e -> e != target && e != player && e.isAlive() && !(e instanceof ServerPlayer))) {
					other.hurtServer(level, level.damageSources().indirectMagic(player, player), LIGHTNING_DAMAGE);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, other.getX(), other.getY(0.6), other.getZ(), 10, 0.2, 0.3, 0.2, 0.2);
					if (++chained >= LIGHTNING_CHAIN) {
						break;
					}
				}
			}
			case "ice" -> {
				target.setTicksFrozen(Math.max(target.getTicksFrozen(), ICE_FREEZE));
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WATER_SLOW, 2), player);
			}
			default -> {
				return;
			}
		}
		level.sendParticles(particle(element), target.getX(), target.getY(0.6), target.getZ(), bolt ? 16 : 8, 0.3, 0.3, 0.3, 0.05);
	}

	/** The element's look. */
	static ParticleOptions particle(String element) {
		return switch (element) {
			case "fire" -> ParticleTypes.FLAME;
			case "water" -> ParticleTypes.SPLASH;
			case "earth" -> new DustParticleOptions(0x8B6A3C, 1.2F);
			case "air" -> ParticleTypes.CLOUD;
			case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
			case "ice" -> ParticleTypes.SNOWFLAKE;
			default -> ParticleTypes.ENCHANT;
		};
	}

	static SoundEvent sound(String element) {
		return switch (element) {
			case "fire" -> SoundEvents.BLAZE_SHOOT;
			case "water" -> SoundEvents.GENERIC_SPLASH;
			case "earth" -> SoundEvents.ANVIL_LAND;
			case "air" -> SoundEvents.BREEZE_SHOOT;
			case "lightning" -> SoundEvents.LIGHTNING_BOLT_IMPACT;
			case "ice" -> SoundEvents.GLASS_BREAK;
			default -> SoundEvents.ILLUSIONER_CAST_SPELL;
		};
	}

	/** The imbued blade's trail; the element wearing off. */
	static void tick(ServerPlayer player) {
		Long until = IMBUED.get(player.getUUID());
		if (until == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (until <= now) {
			IMBUED.remove(player.getUUID());
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.imbue.faded"));
			return;
		}
		if (now % 4L == 0L && player.level() instanceof ServerLevel level) {
			Vec3 hand = player.position().add(player.getLookAngle().scale(0.6)).add(0.0, 1.0, 0.0);
			level.sendParticles(particle(element(player)), hand.x, hand.y, hand.z, 1, 0.15, 0.2, 0.15, 0.01);
		}
	}

	/** Anyone's imbued element, for a HUD or a look ("" for none). */
	public static @Nullable String imbuedElement(Entity entity) {
		return entity instanceof ServerPlayer sp && imbued(sp) ? element(sp) : null;
	}
}
