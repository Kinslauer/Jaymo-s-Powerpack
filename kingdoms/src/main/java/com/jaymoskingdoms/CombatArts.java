package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.6.5) The Combat Arts: the second page of the Strength tree ({@link StrengthSkill}). Every melee blow that lands within
 * {@link #CHAIN_TICKS} of the last is a link in a chain (one link a swing, however many a sweep hits), and the perks work on
 * that chain:
 *
 * <ul>
 * <li>Momentum: each link makes the next blow heavier, up to {@link #MOMENTUM_LINKS} links.
 * <li>Cleave: a full-strength blow carries part of its weight into the foes beside what it hit.
 * <li>Finishers, on every {@link #FINISHER_LINK}th link when it's a full-strength blow: Earthsplitter (axes, maces, two-handed
 * and blunt weapons: the ground cracks and throws the foes round the target into the air), Rend (swords, knives and
 * spears: the target bleeds) and Executioner (any weapon: a finisher on a badly hurt foe lands much harder).
 * <li>Active skills on the {@code Y} key: Charge (shoulder into the first foe ahead) and War Cry (sneak and {@code Y}, or
 * {@code Y} alone if it's the only one you know: foes round you are cowed, you and your friends emboldened).
 * <li>Bladestorm: every {@link #BLADESTORM_LINK}th link, you whirl and strike every foe round you.
 * <li>(0.7.3) Shield Throw on {@code Y} with a shield in hand ({@link ShieldThrow}).
 * </ul>
 *
 * <p>Area blows (Cleave, Earthsplitter, Bladestorm, War Cry, Charge) only ever touch foes: monsters, and whatever is
 * fighting you. Bystanders and other players are left alone, so a fight in a town doesn't turn into a crime.
 */
public final class CombatArts {
	/** A blow landing within this many ticks of the last keeps the chain going. */
	static final long CHAIN_TICKS = 40L;
	/** A blow at least this charged is a full-strength one. */
	static final float FULL_STRENGTH = 0.9F;
	/** Momentum: per rank, each link adds this share to the next blow, counting at most this many links. */
	static final float MOMENTUM = 0.04F;
	static final int MOMENTUM_LINKS = 5;
	/** Cleave: per rank, this share of a full-strength blow carries into at most this many foes within this reach of the target. */
	static final float CLEAVE = 0.2F;
	static final int CLEAVE_FOES = 3;
	static final double CLEAVE_REACH = 2.5;
	/** Finishers land on every this-many-th link. */
	static final int FINISHER_LINK = 4;
	/** Earthsplitter: foes this near the target take this share of the blow and are thrown up this hard. */
	static final double EARTHSPLITTER_REACH = 3.5, EARTHSPLITTER_LIFT = 0.55;
	static final float EARTHSPLITTER_SHARE = 0.5F;
	/** Rend: the target bleeds this much every second, for this many seconds. */
	static final float REND_DAMAGE = 1.5F;
	static final int REND_SECONDS = 5;
	/** Executioner: a finisher on a foe below this share of its health deals this much more. */
	static final float EXECUTE_HEALTH = 0.3F, EXECUTE_BONUS = 1.0F;
	/** Charge: how fast you go (blocks a tick, kept up the whole way), for how long (ticks), what it deals (times your attack damage), how hard it shoves, its wait (ticks). */
	static final double CHARGE_SPEED = 0.8, CHARGE_LIFT = 0.12, CHARGE_SHOVE = 1.2, CHARGE_SHOVE_UP = 0.35;
	static final int CHARGE_TICKS = 10, CHARGE_COOLDOWN = 240, CHARGE_STUN_TICKS = 40;
	static final float CHARGE_DAMAGE = 1.5F, CHARGE_HUNGER = 2.0F;
	/** War Cry: how far it carries, how long foes are cowed and friends emboldened (ticks), its wait (ticks). */
	static final double WAR_CRY_REACH = 10.0;
	static final int WAR_CRY_COWED = 120, WAR_CRY_BOLD = 160, WAR_CRY_COOLDOWN = 900;
	static final float WAR_CRY_HUNGER = 3.0F;
	/** Bladestorm: every this-many-th link, every foe within this reach takes this share of the blow. */
	static final int BLADESTORM_LINK = 8;
	static final double BLADESTORM_REACH = 3.5;
	static final float BLADESTORM_SHARE = 1.0F;
	/** The colour of blood. */
	static final int BLOOD = 0x8A0303;

	/** Player → server: use a Combat Art ({@code Y}); sneaking picks War Cry when both are known. */
	public record Use(boolean sneaking) implements CustomPacketPayload {
		public static final Type<Use> TYPE = new Type<>(JaymosKingdoms.id("combat_art"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.sneaking), buf -> new Use(buf.readBoolean()));

		@Override
		public Type<Use> type() {
			return TYPE;
		}
	}

	/** A player's chain: how many links, when the last landed, and the tick a finisher was last struck (so a sweep strikes it once). */
	private static final class Chain {
		int links;
		long last = Long.MIN_VALUE / 2, linkTick = -1L, finisherTick = -1L, finishedTick = -1L;
		boolean execute;
	}

	/** A foe bleeding from Rend. */
	private record Bleed(LivingEntity target, UUID by, int[] left) {
	}

	private static final Map<UUID, Chain> CHAINS = new HashMap<>();
	/** Player → how charged their last swing was, and the tick of it (the swing resets the charge before the blow lands). */
	private static final Map<UUID, Float> SWING_CHARGE = new HashMap<>();
	private static final Map<UUID, Long> SWING_TICK = new HashMap<>();
	/** Player → game time each art is ready again. */
	private static final Map<UUID, Long> CHARGE_READY = new HashMap<>(), WAR_CRY_READY = new HashMap<>();
	/** Player → ticks left on a Charge under way. */
	private static final Map<UUID, Integer> CHARGING = new HashMap<>();
	private static final List<Bleed> BLEEDS = new ArrayList<>();
	/** While true, a blow is one of ours (a cleave, a shockwave, a bleed): it feeds no chain and strikes no finisher. */
	private static boolean striking;

	private CombatArts() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> use(context.player(), payload.sneaking()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatArts::landed);
		ServerTickEvents.END_SERVER_TICK.register(CombatArts::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHAINS.clear();
			SWING_CHARGE.clear();
			SWING_TICK.clear();
			CHARGE_READY.clear();
			WAR_CRY_READY.clear();
			CHARGING.clear();
			BLEEDS.clear();
		});
	}

	/** A player swings at something (from {@code Player.attack}, so Better Combat's swings count too): how charged it was. */
	public static void swing(Player player) {
		if (player instanceof ServerPlayer sp) {
			SWING_CHARGE.put(sp.getUUID(), sp.getAttackStrengthScale(0.5F));
			SWING_TICK.put(sp.getUUID(), sp.level().getGameTime());
		}
	}

	private static boolean fullStrength(ServerPlayer sp, long now) {
		return SWING_TICK.getOrDefault(sp.getUUID(), -1L) == now && SWING_CHARGE.getOrDefault(sp.getUUID(), 0.0F) >= FULL_STRENGTH;
	}

	/** A player's own melee blow (not one of ours), or null. */
	private static ServerPlayer melee(DamageSource source) {
		return !striking && source.getEntity() instanceof ServerPlayer sp && source.getDirectEntity() == sp && source.is(DamageTypes.PLAYER_ATTACK)
			&& !sp.isSpectator() ? sp : null;
	}

	/** How many links {@code chain} has at {@code now}, or 0 if it has run out. */
	private static int links(Chain chain, long now) {
		return now - chain.last <= CHAIN_TICKS ? chain.links : 0;
	}

	/** What a blow deals, from {@code amount} (from LivingEntity.hurtServer): Momentum, and Executioner on a finisher. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		ServerPlayer sp = melee(source);
		if (sp == null || target == sp) {
			return amount;
		}
		Skills.Progress p = Skills.of(sp);
		if (p.rank(Skills.Perk.MOMENTUM) == 0 && p.rank(Skills.Perk.EXECUTIONER) == 0 && p.rank(Skills.Perk.EARTHSPLITTER) == 0
			&& p.rank(Skills.Perk.REND) == 0 && p.rank(Skills.Perk.BLADESTORM) == 0) {
			return amount;
		}
		long now = sp.level().getGameTime();
		Chain chain = CHAINS.computeIfAbsent(sp.getUUID(), id -> new Chain());
		int before = links(chain, now);
		// (a sweep that hits several is still one link: the first blow this tick already counted it)
		int link = chain.linkTick == now ? before : before + 1;
		int built = Math.min(MOMENTUM_LINKS, link - 1);
		float factor = 1.0F + MOMENTUM * p.rank(Skills.Perk.MOMENTUM) * built;
		if (chain.finisherTick != now && link % FINISHER_LINK == 0 && fullStrength(sp, now)) {
			chain.finisherTick = now;
			chain.execute = false;
		}
		if (chain.finisherTick == now && p.rank(Skills.Perk.EXECUTIONER) > 0 && target.getHealth() < target.getMaxHealth() * EXECUTE_HEALTH) {
			factor += EXECUTE_BONUS;
			chain.execute = true;
		}
		return amount * factor;
	}

	/** A blow landed: the chain grows, and a finisher, Cleave or Bladestorm strikes. */
	private static void landed(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		ServerPlayer sp = melee(source);
		if (sp == null || target == sp || damageTaken <= 0.0F || !(sp.level() instanceof ServerLevel level)) {
			return;
		}
		Skills.Progress p = Skills.of(sp);
		long now = level.getGameTime();
		Chain chain = CHAINS.computeIfAbsent(sp.getUUID(), id -> new Chain());
		boolean newLink = chain.linkTick != now;
		if (newLink) {
			chain.links = links(chain, now) + 1;
			chain.linkTick = now;
			chain.last = now;
			if (chain.links >= 2 && hasChainPerk(p)) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combo", chain.links).withStyle(ChatFormatting.GOLD));
			}
		}
		boolean full = fullStrength(sp, now);
		if (newLink && full && p.rank(Skills.Perk.CLEAVE) > 0) {
			float share = CLEAVE * p.rank(Skills.Perk.CLEAVE);
			int n = 0;
			for (LivingEntity foe : foes(level, sp, target.getBoundingBox().inflate(CLEAVE_REACH))) {
				if (foe != target && n++ < CLEAVE_FOES) {
					strike(level, sp, foe, damageTaken * share);
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, foe.getX(), foe.getY(0.5), foe.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}
		if (chain.finisherTick == now && chain.finishedTick != now) {
			chain.finishedTick = now;
			finisher(level, sp, p, target, damageTaken, chain.execute);
		}
		if (newLink && full && p.rank(Skills.Perk.BLADESTORM) > 0 && chain.links % BLADESTORM_LINK == 0) {
			bladestorm(level, sp, target, damageTaken);
		}
	}

	private static boolean hasChainPerk(Skills.Progress p) {
		return p.rank(Skills.Perk.MOMENTUM) > 0 || p.rank(Skills.Perk.EARTHSPLITTER) > 0 || p.rank(Skills.Perk.REND) > 0
			|| p.rank(Skills.Perk.EXECUTIONER) > 0 || p.rank(Skills.Perk.BLADESTORM) > 0;
	}

	/** The finisher for the weapon in hand. */
	private static void finisher(ServerLevel level, ServerPlayer sp, Skills.Progress p, LivingEntity target, float dealt, boolean executed) {
		ItemStack weapon = sp.getMainHandItem();
		if (executed) {
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 20, 0.3, 0.4, 0.3, 0.3);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combo.executioner").withStyle(ChatFormatting.DARK_RED));
		}
		if (heavy(weapon) && p.rank(Skills.Perk.EARTHSPLITTER) > 0) {
			earthsplitter(level, sp, target, dealt);
		} else if (blade(weapon) && p.rank(Skills.Perk.REND) > 0 && target.isAlive()) {
			BLEEDS.removeIf(b -> b.target() == target);
			BLEEDS.add(new Bleed(target, sp.getUUID(), new int[] {REND_SECONDS * 20}));
			level.sendParticles(new DustParticleOptions(BLOOD, 1.2F), target.getX(), target.getY(0.6), target.getZ(), 16, 0.3, 0.3, 0.3, 0.0);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.6F);
			if (!executed) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combo.rend").withStyle(ChatFormatting.RED));
			}
		}
	}

	/** Axes, maces, two-handed and blunt weapons. */
	static boolean heavy(ItemStack weapon) {
		return weapon.is(ItemTags.AXES) || weapon.is(Items.MACE) || weapon.is(ClassPerks.TWO_HANDED) || weapon.is(ClassPerks.BLUDGEONS);
	}

	/** Swords, knives and spears. */
	static boolean blade(ItemStack weapon) {
		return weapon.is(ItemTags.SWORDS) || weapon.is(ItemTags.SPEARS) || weapon.is(ClassPerks.LIGHT_BLADES);
	}

	private static void earthsplitter(ServerLevel level, ServerPlayer sp, LivingEntity target, float dealt) {
		BlockState ground = level.getBlockState(target.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), target.getX(), target.getY() + 0.1, target.getZ(), 60,
				EARTHSPLITTER_REACH / 2, 0.1, EARTHSPLITTER_REACH / 2, 0.15);
		}
		level.sendParticles(ParticleTypes.DUST_PLUME, target.getX(), target.getY() + 0.1, target.getZ(), 30, EARTHSPLITTER_REACH / 2, 0.1, EARTHSPLITTER_REACH / 2, 0.05);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
		for (LivingEntity foe : foes(level, sp, target.getBoundingBox().inflate(EARTHSPLITTER_REACH, 1.0, EARTHSPLITTER_REACH))) {
			if (foe != target) {
				strike(level, sp, foe, dealt * EARTHSPLITTER_SHARE);
			}
			Vec3 out = foe.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
			Vec3 push = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(0.3);
			foe.setDeltaMovement(foe.getDeltaMovement().add(push.x, EARTHSPLITTER_LIFT, push.z));
			foe.needsSync = true;
		}
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combo.earthsplitter").withStyle(ChatFormatting.GOLD));
	}

	private static void bladestorm(ServerLevel level, ServerPlayer sp, LivingEntity target, float dealt) {
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, sp.getX() + Math.cos(a) * 1.8, sp.getY(0.6), sp.getZ() + Math.sin(a) * 1.8, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
		for (LivingEntity foe : foes(level, sp, sp.getBoundingBox().inflate(BLADESTORM_REACH, 1.0, BLADESTORM_REACH))) {
			if (foe != target) {
				strike(level, sp, foe, dealt * BLADESTORM_SHARE);
			}
		}
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combo.bladestorm").withStyle(ChatFormatting.GOLD));
	}

	/**
	 * The foes in {@code box}: monsters, and anything whose target is {@code player}. Never another player, and never a
	 * bystander, so area blows don't drag townsfolk or friends into it.
	 */
	static List<LivingEntity> foes(ServerLevel level, ServerPlayer player, AABB box) {
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !(e instanceof Player)
			&& (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player));
	}

	/** One of our own blows: counted as {@code player}'s, but not as a link in their chain, and through the hurt cooldown. */
	static void strike(ServerLevel level, ServerPlayer player, LivingEntity target, float amount) {
		if (amount <= 0.0F || !target.isAlive()) {
			return;
		}
		striking = true;
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, player.damageSources().playerAttack(player), amount);
		} finally {
			striking = false;
		}
	}

	// ---- active skills ---------------------------------------------------------------------------------------------

	/** The {@code Y} key: Charge, or War Cry (sneaking, or when it's the only one known). */
	static void use(ServerPlayer sp, boolean sneaking) {
		if (!(sp.level() instanceof ServerLevel level) || sp.isSpectator() || !sp.isAlive()) {
			return;
		}
		boolean charge = Skills.rank(sp, Skills.Perk.CHARGE) > 0, cry = Skills.rank(sp, Skills.Perk.WAR_CRY) > 0;
		// with a shield in hand, Y throws it (sneak still roars)
		if (ShieldThrow.wanted(sp) && !(sneaking && cry)) {
			ShieldThrow.use(level, sp);
			return;
		}
		if (!charge && !cry) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combat_art.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		long now = level.getGameTime();
		if (cry && (sneaking || !charge)) {
			if (ready(sp, WAR_CRY_READY, now, "war_cry")) {
				warCry(level, sp);
				WAR_CRY_READY.put(sp.getUUID(), now + WAR_CRY_COOLDOWN);
			}
		} else if (ready(sp, CHARGE_READY, now, "charge")) {
			if (sp.isPassenger() || sp.isFallFlying()) {
				return;
			}
			Vec3 look = sp.getLookAngle().multiply(1.0, 0.0, 1.0);
			if (look.lengthSqr() < 1.0E-4) {
				return;
			}
			look = look.normalize();
			sp.setDeltaMovement(look.x * CHARGE_SPEED, CHARGE_LIFT, look.z * CHARGE_SPEED);
			sp.needsSync = true;
			sp.causeFoodExhaustion(CHARGE_HUNGER);
			CHARGING.put(sp.getUUID(), CHARGE_TICKS);
			CHARGE_READY.put(sp.getUUID(), now + CHARGE_COOLDOWN);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
	}

	/** Whether {@code player}'s art is ready; if not, says how long it has to go. */
	private static boolean ready(ServerPlayer player, Map<UUID, Long> readyAt, long now, String art) {
		long left = readyAt.getOrDefault(player.getUUID(), 0L) - now;
		if (left <= 0) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combat_art.wait",
			Component.translatable("skill.jaymoskingdoms.perk." + art), (left + 19) / 20).withStyle(ChatFormatting.GRAY));
		return false;
	}

	private static void warCry(ServerLevel level, ServerPlayer sp) {
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2F, 1.3F);
		level.sendParticles(ParticleTypes.POOF, sp.getX(), sp.getY(0.5), sp.getZ(), 30, 1.5, 0.3, 1.5, 0.05);
		sp.causeFoodExhaustion(WAR_CRY_HUNGER);
		AABB box = sp.getBoundingBox().inflate(WAR_CRY_REACH, 3.0, WAR_CRY_REACH);
		for (LivingEntity foe : foes(level, sp, box)) {
			foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WAR_CRY_COWED, 0), sp);
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WAR_CRY_COWED, 1), sp);
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, foe.getX(), foe.getY(1.0) + 0.3, foe.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
		}
		sp.addEffect(new MobEffectInstance(MobEffects.STRENGTH, WAR_CRY_BOLD, 0), sp);
		for (ServerPlayer friend : level.getEntitiesOfClass(ServerPlayer.class, box, e -> e != sp && e.isAlive() && !e.isSpectator())) {
			friend.addEffect(new MobEffectInstance(MobEffects.STRENGTH, WAR_CRY_BOLD, 0), sp);
		}
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combat_art.war_cry").withStyle(ChatFormatting.GOLD));
	}

	private static void tick(MinecraftServer server) {
		// charges under way: the first foe in the way takes the blow
		for (Iterator<Map.Entry<UUID, Integer>> it = CHARGING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Integer> e = it.next();
			ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
			if (sp == null || !sp.isAlive() || !(sp.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			level.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 0.1, sp.getZ(), 2, 0.2, 0.0, 0.2, 0.0);
			List<LivingEntity> hit = foes(level, sp, sp.getBoundingBox().inflate(0.8));
			if (!hit.isEmpty()) {
				LivingEntity foe = hit.get(0);
				Vec3 dir = sp.getDeltaMovement().multiply(1.0, 0.0, 1.0);
				dir = dir.lengthSqr() < 1.0E-4 ? sp.getLookAngle().multiply(1.0, 0.0, 1.0) : dir;
				strike(level, sp, foe, (float) sp.getAttributeValue(Attributes.ATTACK_DAMAGE) * CHARGE_DAMAGE);
				Vec3 shove = dir.normalize().scale(CHARGE_SHOVE);
				foe.setDeltaMovement(foe.getDeltaMovement().add(shove.x, CHARGE_SHOVE_UP, shove.z));
				foe.needsSync = true;
				foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHARGE_STUN_TICKS, 3), sp);
				sp.setDeltaMovement(sp.getDeltaMovement().multiply(0.2, 1.0, 0.2));
				sp.needsSync = true;
				level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
				level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.5), foe.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
				it.remove();
				continue;
			}
			if (e.getValue() <= 1 || sp.horizontalCollision) {
				it.remove();
			} else {
				e.setValue(e.getValue() - 1);
				// (kept up against the ground's drag, and steered by where you look)
				Vec3 look = sp.getLookAngle().multiply(1.0, 0.0, 1.0);
				if (look.lengthSqr() > 1.0E-4) {
					look = look.normalize().scale(CHARGE_SPEED);
					sp.setDeltaMovement(look.x, sp.getDeltaMovement().y, look.z);
					sp.needsSync = true;
				}
			}
		}
		// Rend: bleeding once a second
		for (Iterator<Bleed> it = BLEEDS.iterator(); it.hasNext(); ) {
			Bleed b = it.next();
			LivingEntity target = b.target();
			ServerPlayer by = server.getPlayerList().getPlayer(b.by());
			if (!target.isAlive() || target.isRemoved() || by == null || !(target.level() instanceof ServerLevel level) || --b.left()[0] <= 0) {
				it.remove();
				continue;
			}
			if (b.left()[0] % 20 == 0) {
				strike(level, by, target, REND_DAMAGE);
				level.sendParticles(new DustParticleOptions(BLOOD, 1.0F), target.getX(), target.getY(0.5), target.getZ(), 6, 0.25, 0.3, 0.25, 0.0);
			}
		}
	}
}
