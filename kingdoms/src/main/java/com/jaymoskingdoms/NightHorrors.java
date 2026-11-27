package com.jaymoskingdoms;

import com.jaymoskingdoms.nightforms.NightForms;
import com.jaymoskingdoms.nightforms.NightHorror;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A true shape of the night (Night's Blood, patch 4): the fifth shape of a full vampire, a gaunt winged horror
 * ({@link NightHorror}, our own model) worn in the place of the body, taken from the skill bar like the other four
 * ({@link NightForms}). Late on the Blood page ({@link BloodPage.Gift#TRUE_SHAPE}: old blood only).
 * <ul>
 * <li>It hits harder and takes more ({@link #ATTACK}, {@link #HEALTH}, {@link #ARMOR}), and each pact the vampire carries
 * makes it a little stronger ({@link #PACT_ATTACK}, {@link #PACT_HEALTH}, {@link #PACT_ARMOR}).</li>
 * <li>One jump in the air ({@link #airJump}), until it has landed.</li>
 * <li>By hand it tears what is softer than stone, faster than a hand could ({@link #tears}).</li>
 * <li>No armour and no tools, by the werewolf beast's rules: what is in the hands goes into the pack and armour is torn
 * (a share of its durability) and falls ({@link WerewolfPlayers#strip}).</li>
 * <li>It drains blood fast ({@link #DRAIN_MB} every {@link #DRAIN_EVERY} ticks) and ends when the reserve is empty.</li>
 * <li>Folk who see it scream and run: it counts as something that has just hurt a person ({@link Bloodshed#alarm}), so
 * villagers run and guards go for it, villagers cry out ({@link #CRY_LINES}) and are afraid ({@link Frights#scare}), and the
 * ones who see it suspect the player by name ({@link Suspicion}).</li>
 * </ul>
 * And the slipping mist's rules live here too ({@link #slips}): with that gift mist passes what a hand passes, takes no fall
 * damage, and its first blow lands double and ends it.
 */
public final class NightHorrors {
	// ---- tuning ----
	/** What taking the shape costs (mB), the wait after (ticks), the longest it lasts (ticks) and the size it adds to the body. */
	public static final int ENTER_MB = 500, MAX_TICKS = 2400;
	public static final long COOLDOWN = 1200L;
	public static final double SCALE = 0.35;
	/** The blood it drinks fast: this much (mB) every this many ticks. */
	static final int DRAIN_MB = 25, DRAIN_EVERY = 10;
	/** Its blows, its health, its armour and the knockback it shrugs off (attributes added). */
	static final double ATTACK = 4.0, HEALTH = 10.0, ARMOR = 6.0, KNOCKBACK = 0.5;
	/** Each pact adds this much to each. */
	static final double PACT_ATTACK = 0.75, PACT_HEALTH = 2.0, PACT_ARMOR = 0.5;
	/** The jump in the air: up, and along the look. */
	static final double AIR_UP = 0.85, AIR_ALONG = 0.45;
	/** What it tears by hand: blocks softer than stone, this many times as fast. */
	static final float TEAR_SPEED = 3.0F;
	/** Folk see it: how far (blocks), how often (ticks) it scares and alarms, and how often a cry (ticks) and a suspicion are renewed. */
	static final double SCARE_RANGE = 24.0;
	static final int SCARE_EVERY = 20, CRY_GAP = 200, SUSPECT_EVERY = 200, CRY_LINES = 4;
	/** The mist that slips: a double blow. */
	static final float DOUBLE = 2.0F;

	private static final Identifier ATTACK_ID = JaymosKingdoms.id("horror_attack"), HEALTH_ID = JaymosKingdoms.id("horror_health"),
		ARMOR_ID = JaymosKingdoms.id("horror_armor"), KNOCK_ID = JaymosKingdoms.id("horror_knockback");

	/** The horror's entity type (a puppet; no egg, never spawns). */
	public static EntityType<NightHorror> TYPE;

	/** Player to server: the jump in the air. */
	public record AirJump() implements CustomPacketPayload {
		public static final Type<AirJump> TYPE = new Type<>(JaymosKingdoms.id("horror_air_jump"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AirJump> CODEC = StreamCodec.unit(new AirJump());

		@Override
		public Type<AirJump> type() {
			return TYPE;
		}
	}

	/** Who has jumped in the air since they last stood on something. */
	private static final Set<UUID> AIR_JUMPED = new HashSet<>();
	/** Who has just been cried at by whom (a villager's last cry), and the one blow that is to land double. */
	private static final Map<Villager, Long> CRIED = new WeakHashMap<>();
	private static final Map<LivingEntity, UUID> DOUBLED = new WeakHashMap<>();
	private static boolean doubling;

	private NightHorrors() {
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("night_horror"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(NightHorror::new, MobCategory.MISC)
			.sized(0.9F, 2.45F).clientTrackingRange(10).updateInterval(1).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, NightHorror.createAttributes());
		BloodBarrier.register();
		Batstep.register();
		BloodShards.register();
		HungryColony.register();
		PayloadTypeRegistry.serverboundPlay().register(AirJump.TYPE, AirJump.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AirJump.TYPE, (payload, context) -> airJump(context.player()));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && NightForms.HORROR.equals(NightForms.form(sp))
				&& NightForms.puppetOf(sp) instanceof NightHorror horror) {
				horror.strike();
			}
			return InteractionResult.PASS;
		});
		// the slipping mist's first blow lands double
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			UUID by = DOUBLED.get(victim);
			if (by != null && !doubling && taken > 0.0F && source.getEntity() instanceof ServerPlayer striker && striker.getUUID().equals(by)
				&& victim.level() instanceof ServerLevel level) {
				DOUBLED.remove(victim);
				doubling = true;
				try {
					victim.setInvulnerableTime(0);
					victim.hurtServer(level, source, taken * (DOUBLE - 1.0F));
				} finally {
					doubling = false;
				}
				Fx.burst(level, victim.getX(), victim.getY(0.5), victim.getZ(), NightForms.BLOOD, 1.0F, 12, 0.3, 0.4, 0.3);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			AIR_JUMPED.clear();
			CRIED.clear();
			DOUBLED.clear();
		});
	}

	// ---- the shape ----

	private static int pacts(Player p) {
		BloodPage.Blood b = BloodPage.of(p);
		return b == null ? 0 : b.pacts().size();
	}

	private static void modifier(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = p.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(id);
		if (amount != 0.0) {
			instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** The shape is taken: stronger, tougher, the hands and armour given up, the roar, the folk who see it. */
	public static void began(ServerPlayer p, ServerLevel level, @Nullable NightHorror horror) {
		int pacts = pacts(p);
		modifier(p, Attributes.ATTACK_DAMAGE, ATTACK_ID, ATTACK + PACT_ATTACK * pacts);
		modifier(p, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH + PACT_HEALTH * pacts);
		modifier(p, Attributes.ARMOR, ARMOR_ID, ARMOR + PACT_ARMOR * pacts);
		modifier(p, Attributes.KNOCKBACK_RESISTANCE, KNOCK_ID, KNOCKBACK);
		AIR_JUMPED.remove(p.getUUID());
		WerewolfPlayers.strip(p, true);
		if (horror != null) {
			horror.setHost(p.getId());
			horror.roar();
		}
		Vec3 at = p.position().add(0.0, p.getBbHeight() * 0.5, 0.0);
		Fx.ring(level, p.position().add(0.0, 0.2, 0.0), 5.0F, NightForms.BLOOD);
		level.sendParticles(Fx.miasma(0x2A0A12, 1.6F), at.x, at.y, at.z, 30, 0.5, 0.8, 0.5, 0.04);
		level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.4F, 0.55F);
		Suspicion.witnessed(level, p, Suspicion.VAMPIRE, Suspicion.ADD_CHANGE, Suspicion.CHANGE_RANGE, false);
	}

	/** The shape ends: what was added is taken back. */
	public static void ended(ServerPlayer p) {
		modifier(p, Attributes.ATTACK_DAMAGE, ATTACK_ID, 0.0);
		modifier(p, Attributes.MAX_HEALTH, HEALTH_ID, 0.0);
		modifier(p, Attributes.ARMOR, ARMOR_ID, 0.0);
		modifier(p, Attributes.KNOCKBACK_RESISTANCE, KNOCK_ID, 0.0);
		p.setHealth(Math.min(p.getHealth(), p.getMaxHealth()));
		AIR_JUMPED.remove(p.getUUID());
	}

	/** Every tick of the shape; false once the blood has run out. */
	public static boolean tick(ServerPlayer p, ServerLevel level, long age, @Nullable NightHorror horror) {
		// no hands, no armour, as the werewolf's beast has none
		WerewolfPlayers.strip(p, false);
		if (p.onGround()) {
			AIR_JUMPED.remove(p.getUUID());
		}
		if (horror != null) {
			double speed = p.getDeltaMovement().horizontalDistanceSqr();
			horror.setMotion(speed < 1.0E-3 ? NightHorror.STILL : p.isSprinting() ? NightHorror.RUN : NightHorror.WALK);
			Bloodshed.alarm(horror);
		}
		if (age > 0 && age % DRAIN_EVERY == 0) {
			VampireBlood.drain(p, DRAIN_MB);
			if (VampireBlood.get(p) <= 0) {
				return false;
			}
		}
		if (age % SCARE_EVERY == 0) {
			scare(p, level, horror);
		}
		if (age > 0 && age % SUSPECT_EVERY == 0) {
			Suspicion.witnessed(level, p, Suspicion.VAMPIRE, Suspicion.ADD_TELL, Suspicion.CHANGE_RANGE, false);
		}
		return true;
	}

	/** Villagers who see it cry out and run, a realm's other folk run, and (through {@link Bloodshed#alarm}) guards hunt it. */
	private static void scare(ServerPlayer p, ServerLevel level, @Nullable NightHorror horror) {
		long now = level.getGameTime();
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, p.getBoundingBox().inflate(SCARE_RANGE),
			m -> m.isAlive() && (m instanceof Villager || m instanceof Folk) && m.hasLineOfSight(p))) {
			if (m instanceof Villager v) {
				if (Frights.standsFirm(level.getServer(), v)) {
					continue;
				}
				if (now - CRIED.getOrDefault(v, Long.MIN_VALUE / 2) > CRY_GAP) {
					CRIED.put(v, now);
					Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.horror.cry." + level.getRandom().nextInt(CRY_LINES)), Speech.RED, 24.0);
				}
				Frights.scare(level, v, p.position());
			} else if (!(m instanceof Folk folk && folk.combatant())) {
				Vec3 away = m.position().subtract(p.position()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(14.0);
				m.getNavigation().moveTo(m.getX() + away.x, m.getY(), m.getZ() + away.z, 1.3);
			}
		}
	}

	// ---- the jump in the air ----

	private static void airJump(ServerPlayer p) {
		if (!NightForms.HORROR.equals(NightForms.form(p)) || p.onGround() || p.isInWater() || p.isPassenger() || p.getAbilities().flying
			|| !AIR_JUMPED.add(p.getUUID())) {
			return;
		}
		Vec3 look = p.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize().scale(AIR_ALONG);
		NightGifts.push(p, new Vec3(look.x, AIR_UP, look.z));
		NightGifts.fallsSoftly(p);
		if (NightForms.puppetOf(p) instanceof NightHorror horror) {
			horror.leap();
		}
		if (p.level() instanceof ServerLevel level) {
			level.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.2F, 0.5F);
			Fx.burst(level, p.getX(), p.getY() + 0.2, p.getZ(), NightForms.BLOOD, 0.9F, 10, 0.4, 0.1, 0.4);
		}
	}

	// ---- tearing blocks ----

	/** Whether {@code p}, as the horror, tears {@code state} by hand: anything softer than stone (both sides). */
	public static boolean tears(Player p, BlockState state) {
		if (!NightForms.HORROR.equals(NightForms.form(p))) {
			return false;
		}
		float hardness = state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return hardness >= 0.0F && hardness < Blocks.STONE.defaultBlockState().getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** How much faster a hand tears what the horror tears. */
	public static float tearSpeed() {
		return TEAR_SPEED;
	}

	// ---- the mist that slips through ----

	/** Whether {@code p} has the slipping mist gift learned and slotted (both sides: mist passes what it passes). */
	public static boolean slips(Player p) {
		BloodPage.Blood b = BloodPage.of(p);
		return b != null && b.knows(BloodPage.Gift.SLIPPING_MIST) && b.slotted(BloodPage.Gift.SLIPPING_MIST);
	}

	/** The mist's wearer is mist now and slips: what they pass through is for the collision mixin. */
	public static boolean slippingNow(Player p) {
		return NightForms.inMist(p) && slips(p);
	}

	/** {@code striker}'s first blow out of mist on {@code victim}: it is to land double. */
	public static void outOfMist(ServerPlayer striker, LivingEntity victim) {
		DOUBLED.put(victim, striker.getUUID());
	}
}
