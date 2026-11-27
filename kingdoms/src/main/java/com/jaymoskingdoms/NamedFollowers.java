package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Monsters that rise by a name (Tensura Neo Otherworld's naming, ARR: the idea only, our own code). A tamed wolf, or a goblin who
 * follows you ({@link Bonds#lordOf}), is given a name with a name tag (one named at an anvil): the name takes hold of it, and
 * it grows into something more as the days pass ({@link #STAGE_DAYS}: a first rise, then a second, bigger, stronger, tougher),
 * wearing its new rank before its name (a Hobgoblin, a Dire Wolf). Naming costs you: for {@link #TOLL_DAYS} days you are
 * {@link #TOLL_DAMAGE} weaker in the blow and {@link #TOLL_HEALTH} lighter in health for each name you have given (at most
 * {@link #MOST} named). A named follower standing inside a settlement you belong to settles there: once a day, for as long as it is
 * there, the settlement thinks {@link #SETTLE_REP} better of you.
 */
public final class NamedFollowers {
	/** The most followers one player names at once. */
	static final int MOST = 3;
	/** Days a name costs, and what each name in force takes: a share of the blow, and health. */
	static final int TOLL_DAYS = 3;
	static final double TOLL_DAMAGE = 0.08, TOLL_HEALTH = 2.0;
	/** Days after naming for the first and the second rise. */
	static final int[] STAGE_DAYS = {2, 6};
	/** Each rise: the share of health added, the blow added, the share of size added. */
	static final double[] STAGE_HEALTH = {0.5, 1.0}, STAGE_DAMAGE = {1.0, 2.5}, STAGE_SCALE = {0.15, 0.35};
	/** Reputation a day with the settlement a named follower has settled in. */
	static final int SETTLE_REP = 3;
	/** How often (ticks) named followers are looked over. */
	static final int EVERY = 200;
	static final long DAY = 24000L;

	/** A name given: who gave it, when, the rise reached (0, 1 or 2) and the last day the settlement was thanked. */
	record Named(UUID owner, long since, int stage, long settledDay) {
		static final Codec<Named> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Named::owner), Codec.LONG.fieldOf("since").forGetter(Named::since),
				Codec.INT.fieldOf("stage").forGetter(Named::stage), Codec.LONG.fieldOf("settled").forGetter(Named::settledDay))
			.apply(i, Named::new));
	}

	static final AttachmentType<Named> NAMED = AttachmentRegistry.create(JaymosKingdoms.id("named_follower"), b -> b.persistent(Named.CODEC));
	/** Per player: the game times their names' tolls end. Saved, kept through death. */
	static final AttachmentType<List<Long>> TOLLS = AttachmentRegistry.create(JaymosKingdoms.id("naming_tolls"),
		b -> b.persistent(Codec.LONG.listOf()).copyOnDeath());

	private static final Set<Mob> LOADED = new HashSet<>();

	private NamedFollowers() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.getAttached(NAMED) != null) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				LOADED.remove(mob);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
		UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(target instanceof Mob mob) || hand != InteractionHand.MAIN_HAND || !sp.getMainHandItem().is(Items.NAME_TAG)
				|| sp.getMainHandItem().get(DataComponents.CUSTOM_NAME) == null || !sp.getUUID().equals(lordOf(mob))) {
				return InteractionResult.PASS;
			}
			name(sp, mob);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(NamedFollowers::tick);
	}

	/** The player {@code mob} follows, if it is a wolf or a goblin that can be named. */
	static @Nullable UUID lordOf(Mob mob) {
		if (mob instanceof Wolf wolf && wolf.isTame() && wolf.getOwnerReference() != null) {
			return wolf.getOwnerReference().getUUID();
		}
		return mob instanceof Folk folk && folk.role() == Goblins.GOBLIN ? Bonds.lordOf(mob) : null;
	}

	static boolean named(Mob mob) {
		return mob.getAttached(NAMED) != null;
	}

	private static void name(ServerPlayer player, Mob mob) {
		long now = player.level().getGameTime();
		if (named(mob)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.named.already"));
			return;
		}
		int mine = 0;
		for (Mob m : LOADED) {
			Named n = m.getAttached(NAMED);
			if (n != null && n.owner().equals(player.getUUID()) && m.isAlive()) {
				mine++;
			}
		}
		if (mine >= MOST) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.named.most", MOST));
			return;
		}
		Component name = player.getMainHandItem().get(DataComponents.CUSTOM_NAME);
		if (!player.isCreative()) {
			player.getMainHandItem().shrink(1);
		}
		mob.setCustomName(name);
		mob.setCustomNameVisible(true);
		mob.setAttached(NAMED, new Named(player.getUUID(), now, 0, -1L));
		LOADED.add(mob);
		List<Long> tolls = new java.util.ArrayList<>(player.getAttachedOrElse(TOLLS, List.of()));
		tolls.add(now + TOLL_DAYS * DAY);
		player.setAttached(TOLLS, List.copyOf(tolls));
		applyToll(player, now);
		Deeds.done(player, RoadLocks.NAMED_DEED);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY(1.0), mob.getZ(), 30, 0.4, 0.6, 0.4, 0.5);
			level.playSound(null, mob.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.7F, 1.5F);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.named.done", name, TOLL_DAYS));
	}

	/** The toll of every name still in force: the blow and the health. */
	private static void applyToll(ServerPlayer player, long now) {
		List<Long> tolls = player.getAttachedOrElse(TOLLS, List.of());
		List<Long> live = tolls.stream().filter(t -> t > now).toList();
		if (live.size() != tolls.size()) {
			player.setAttached(TOLLS, live);
		}
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, "naming_toll", -TOLL_DAMAGE * live.size(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.MAX_HEALTH, "naming_toll", -TOLL_HEALTH * live.size(), AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			applyToll(p, now);
		}
		for (Mob mob : List.copyOf(LOADED)) {
			Named n = mob.getAttached(NAMED);
			if (n == null || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				continue;
			}
			long days = (now - n.since()) / DAY;
			int stage = days >= STAGE_DAYS[1] ? 2 : days >= STAGE_DAYS[0] ? 1 : 0;
			if (stage > n.stage()) {
				n = new Named(n.owner(), n.since(), stage, n.settledDay());
				mob.setAttached(NAMED, n);
				rise(level, mob, stage);
			}
			settle(server, level, mob, n, now / DAY);
		}
	}

	/** A rise: bigger, tougher, harder-hitting, and a rank before its name. */
	private static void rise(ServerLevel level, Mob mob, int stage) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		grow(mob, Attributes.MAX_HEALTH, "health", health == null ? 0.0 : health.getBaseValue() * STAGE_HEALTH[stage - 1], AttributeModifier.Operation.ADD_VALUE);
		grow(mob, Attributes.ATTACK_DAMAGE, "damage", STAGE_DAMAGE[stage - 1], AttributeModifier.Operation.ADD_VALUE);
		grow(mob, Attributes.SCALE, "scale", STAGE_SCALE[stage - 1], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		mob.setHealth(mob.getMaxHealth());
		Component name = mob.getCustomName() == null ? mob.getName() : mob.getCustomName();
		String kind = mob instanceof Wolf ? "wolf" : "goblin";
		if (!(name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) || !t.getKey().startsWith("named.jaymoskingdoms.")) {
			mob.setCustomName(Component.translatable("named.jaymoskingdoms." + kind + "." + stage, name));
		} else {
			mob.setCustomName(Component.translatable("named.jaymoskingdoms." + kind + "." + stage, t.getArgs()[0]));
		}
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mob.getX(), mob.getY(1.0), mob.getZ(), 30, 0.5, 0.7, 0.5, 0.3);
		level.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8F, 0.8F);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(mob.getAttached(NAMED).owner());
		if (owner != null) {
			owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.named.rose", mob.getCustomName()));
		}
	}

	/** Replaces {@code attribute}'s growth modifier (the second rise's replaces the first's, the tougher of the two). */
	private static void grow(Mob mob, net.minecraft.core.Holder<Attribute> attribute, String what, double amount, AttributeModifier.Operation op) {
		AttributeInstance a = mob.getAttribute(attribute);
		if (a != null) {
			a.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("named_growth_" + what), amount, op));
		}
	}

	/** A named follower inside a settlement its owner belongs to settles there: the settlement thinks better of its owner, once a day. */
	private static void settle(MinecraftServer server, ServerLevel level, Mob mob, Named n, long today) {
		ServerPlayer owner = server.getPlayerList().getPlayer(n.owner());
		if (owner == null || n.settledDay() >= today) {
			return;
		}
		Faction f = Factions.near(server, mob.blockPosition(), KingdomEvents.TOWN_RADIUS);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN || !f.members().containsKey(owner.getUUID())) {
			return;
		}
		mob.setAttached(NAMED, new Named(n.owner(), n.since(), n.stage(), today));
		Reputation.change(owner, f, SETTLE_REP);
		owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.named.settled", mob.getCustomName() == null ? mob.getName() : mob.getCustomName(), f.name()));
	}

	/** Whether {@code entity} is a named follower of {@code player} (for other systems). */
	public static boolean namedBy(LivingEntity entity, UUID player) {
		Named n = entity.getAttached(NAMED);
		return n != null && n.owner().equals(player);
	}
}
