package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * (0.7.82) World tiers, our own take on Apotheosis's (its idea only: none of its code, art or text). Each player picks a
 * tier from the ones they've unlocked, from Hearth (the plain game) up to Legend. Monsters and hostile folk (camps'
 * raiders, orcs, goblins, assassins) that come near a player take that player's tier, the highest of anyone who's
 * come near them: more health, armour, toughness, damage and speed. In return the player's luck rises (better chest
 * and fishing loot), their kills give more experience, and both a tiered foe's drops and the chests they open can come
 * with stacks doubled. Tiers unlock by feats: great foes slain (anything with a boss bar, a monster of legend, a
 * vanilla boss), a trip to the Nether, the Wither, the Ender Dragon. Everyone near a great foe when it falls shares the
 * kill. A player can go back down at any time, a minute after their last change.
 */
public final class WorldTiers {
	/** How near a player a hostile has to come to take their tier (blocks), and how often that's looked for (ticks). */
	static final double TIER_RANGE = 40.0;
	static final int SCAN_TICKS = 20;
	/** How near a great foe a player has to be when it falls to share the kill (the Ender Dragon: much further). */
	static final double CREDIT_RANGE = 64.0, DRAGON_CREDIT_RANGE = 160.0;
	/** The wait between a player's tier changes (ticks; never for creative players). */
	static final int CHANGE_COOLDOWN_TICKS = 1200;

	/** Per tier, Hearth to Legend: what a tiered foe gains. Health, damage and speed are shares of its own. */
	static final double[] HEALTH = {0.0, 0.15, 0.35, 0.60, 1.00};
	static final double[] DAMAGE = {0.0, 0.10, 0.25, 0.40, 0.60};
	static final double[] ARMOR = {0.0, 2.0, 4.0, 7.0, 10.0};
	static final double[] TOUGHNESS = {0.0, 0.0, 2.0, 4.0, 6.0};
	static final double[] SPEED = {0.0, 0.0, 0.05, 0.08, 0.12};
	/** Per tier: the player's luck, the extra experience from their kills (a share), and the chance each loot stack is doubled. */
	static final double[] LUCK = {0.0, 1.0, 2.0, 3.0, 5.0};
	static final double[] EXPERIENCE = {0.0, 0.15, 0.30, 0.50, 0.75};
	static final double[] DOUBLE_LOOT = {0.0, 0.10, 0.20, 0.35, 0.50};
	/** Per tier, what unlocks it: great foes slain, and the feat it also needs (0: none). */
	static final int[] FOES_NEEDED = {0, 1, 5, 12, 25};
	static final int[] FEAT_NEEDED = {0, 0, Progress.NETHER, Progress.WITHER, Progress.DRAGON};

	public enum Tier {
		HEARTH(Items.CAMPFIRE, 0xFF8FB86A),
		BORDERLANDS(Items.IRON_SWORD, 0xFFE0C04A),
		WILDLANDS(Items.CRYING_OBSIDIAN, 0xFFE08A3A),
		DREAD(Items.WITHER_SKELETON_SKULL, 0xFFD04848),
		LEGEND(Items.DRAGON_HEAD, 0xFFB070E8);

		public final net.minecraft.world.item.Item icon;
		public final int color;

		Tier(net.minecraft.world.item.Item icon, int color) {
			this.icon = icon;
			this.color = color;
		}

		public String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public Component title() {
			return Component.translatable("tier.jaymoskingdoms." + key());
		}

		public static Tier byOrdinal(int i) {
			Tier[] all = values();
			return all[Math.clamp(i, 0, all.length - 1)];
		}

		/** What this tier does, a line each: foes, then rewards. */
		public List<Component> effects() {
			List<Component> out = new ArrayList<>();
			int i = ordinal();
			if (i == 0) {
				out.add(Component.translatable("tier.jaymoskingdoms.effect.none"));
				return out;
			}
			out.add(Component.translatable("tier.jaymoskingdoms.effect.health", pct(HEALTH[i])));
			out.add(Component.translatable("tier.jaymoskingdoms.effect.damage", pct(DAMAGE[i])));
			out.add(Component.translatable("tier.jaymoskingdoms.effect.armor", num(ARMOR[i]), num(TOUGHNESS[i])));
			if (SPEED[i] > 0.0) {
				out.add(Component.translatable("tier.jaymoskingdoms.effect.speed", pct(SPEED[i])));
			}
			out.add(Component.translatable("tier.jaymoskingdoms.effect.luck", num(LUCK[i])));
			out.add(Component.translatable("tier.jaymoskingdoms.effect.experience", pct(EXPERIENCE[i])));
			out.add(Component.translatable("tier.jaymoskingdoms.effect.loot", pct(DOUBLE_LOOT[i])));
			return out;
		}

		/** What unlocks it. */
		public Component requirement() {
			int i = ordinal();
			if (i == 0) {
				return Component.translatable("tier.jaymoskingdoms.need.none");
			}
			Component foes = Component.translatable(FOES_NEEDED[i] == 1 ? "tier.jaymoskingdoms.need.foe" : "tier.jaymoskingdoms.need.foes", FOES_NEEDED[i]);
			return FEAT_NEEDED[i] == 0 ? foes : Component.translatable("tier.jaymoskingdoms.need.and", foes,
				Component.translatable("tier.jaymoskingdoms.feat." + Progress.featKey(FEAT_NEEDED[i])));
		}

		private static String pct(double share) {
			return Math.round(share * 100.0) + "%";
		}

		private static String num(double v) {
			return v == Math.rint(v) ? Integer.toString((int)v) : Double.toString(v);
		}
	}

	/** A player's way up: great foes slain, feats done (bits), and the highest tier opened (by feats or an op). */
	public record Progress(int foes, int feats, int unlocked) {
		public static final int NETHER = 1, WITHER = 2, DRAGON = 4;
		public static final Progress NONE = new Progress(0, 0, 0);
		static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("foes", 0).forGetter(Progress::foes),
			Codec.INT.optionalFieldOf("feats", 0).forGetter(Progress::feats),
			Codec.INT.optionalFieldOf("unlocked", 0).forGetter(Progress::unlocked)
		).apply(i, Progress::new));
		static final StreamCodec<RegistryFriendlyByteBuf, Progress> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Progress::foes, ByteBufCodecs.VAR_INT, Progress::feats, ByteBufCodecs.VAR_INT, Progress::unlocked, Progress::new);

		public boolean has(int feat) {
			return (this.feats & feat) != 0;
		}

		/** Whether its feats alone open tier {@code i}. */
		public boolean earned(int i) {
			return this.foes >= FOES_NEEDED[i] && (FEAT_NEEDED[i] == 0 || has(FEAT_NEEDED[i]));
		}

		/** The highest tier open to it: earned in order, or opened by an op. */
		public int open() {
			int top = 0;
			while (top + 1 < FOES_NEEDED.length && earned(top + 1)) {
				top++;
			}
			return Math.max(top, this.unlocked);
		}

		static String featKey(int feat) {
			return feat == NETHER ? "nether" : feat == WITHER ? "wither" : "dragon";
		}
	}

	/** Each player's chosen tier (its ordinal). Saved, kept through death, synced to its own player (the screen). */
	public static final AttachmentType<Integer> TIER = AttachmentRegistry.create(JaymosKingdoms.id("world_tier"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** Each player's {@link Progress}. Saved, kept through death, synced to its own player. */
	public static final AttachmentType<Progress> PROGRESS = AttachmentRegistry.create(JaymosKingdoms.id("world_tier_progress"),
		builder -> builder.persistent(Progress.CODEC).copyOnDeath().syncWith(Progress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** When a player last changed tier (game time). Not saved. */
	static final AttachmentType<Long> CHANGED = AttachmentRegistry.create(JaymosKingdoms.id("world_tier_changed"));
	/** The tier a hostile has taken (its ordinal). Saved with it. */
	static final AttachmentType<Integer> MOB_TIER = AttachmentRegistry.create(JaymosKingdoms.id("mob_world_tier"), builder -> builder.persistent(Codec.INT));

	private static final Identifier HEALTH_ID = JaymosKingdoms.id("world_tier_health"), DAMAGE_ID = JaymosKingdoms.id("world_tier_damage"),
		ARMOR_ID = JaymosKingdoms.id("world_tier_armor"), TOUGHNESS_ID = JaymosKingdoms.id("world_tier_toughness"),
		SPEED_ID = JaymosKingdoms.id("world_tier_speed"), LUCK_ID = JaymosKingdoms.id("world_tier_luck");

	/** Player → server: enter tier {@code tier} (its ordinal). */
	public record Choose(int tier) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(JaymosKingdoms.id("world_tier_choose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.tier), buf -> new Choose(buf.readVarInt()));

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	private WorldTiers() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (payload, context) -> choose(context.player(), payload.tier()));
		ServerTickEvents.END_SERVER_TICK.register(WorldTiers::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> luck(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> luck(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register(WorldTiers::died);
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
			int tier;
			if (entity instanceof ServerPlayer player) {
				// a chest's loot, for the player opening it
				boolean chest = table.unwrapKey().filter(k -> k.identifier().getPath().startsWith("chests/")).isPresent();
				tier = chest ? of(player).ordinal() : 0;
			} else {
				tier = entity == null ? 0 : entity.getAttachedOrElse(MOB_TIER, 0);
				// (BWM batch 5) slain in an emerald beacon's watch: a step higher
				tier = entity == null ? 0 : Math.min(DOUBLE_LOOT.length - 1, tier + BeaconWatch.lootStep(entity));
			}
			if (tier <= 0) {
				return;
			}
			List<ItemStack> extra = new ArrayList<>();
			for (ItemStack stack : drops) {
				if (!stack.isEmpty() && context.getRandom().nextDouble() < DOUBLE_LOOT[tier]) {
					extra.add(stack.copy());
				}
			}
			drops.addAll(extra);
		});
	}

	public static Tier of(Player player) {
		return Tier.byOrdinal(player.getAttachedOrElse(TIER, 0));
	}

	public static Progress progress(Player player) {
		return player.getAttachedOrElse(PROGRESS, Progress.NONE);
	}

	/** Whether {@code player} may enter {@code tier}: it's open to them, or they're in creative. */
	public static boolean open(Player player, Tier tier) {
		return player.isCreative() || tier.ordinal() <= progress(player).open();
	}

	/** Game ticks until {@code player} may change tier again (0: now). */
	public static long waitLeft(Player player) {
		if (player.isCreative()) {
			return 0L;
		}
		Long at = player.getAttached(CHANGED);
		return at == null ? 0L : Math.max(0L, at + CHANGE_COOLDOWN_TICKS - player.level().getGameTime());
	}

	private static void choose(ServerPlayer player, int ordinal) {
		if (ordinal < 0 || ordinal >= Tier.values().length) {
			return;
		}
		Tier tier = Tier.values()[ordinal];
		if (tier == of(player) || !open(player, tier)) {
			return;
		}
		long left = waitLeft(player);
		if (left > 0L) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.tier.wait", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		set(player, tier);
	}

	/** Puts {@code player} in {@code tier}: their luck, and the word of it. */
	static void set(ServerPlayer player, Tier tier) {
		Tier was = of(player);
		player.setAttached(TIER, tier.ordinal());
		player.setAttached(CHANGED, player.level().getGameTime());
		luck(player);
		boolean up = tier.ordinal() > was.ordinal();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), up ? SoundEvents.RESPAWN_ANCHOR_CHARGE : SoundEvents.BEACON_DEACTIVATE,
			SoundSource.PLAYERS, 0.8F, up ? 0.8F + 0.1F * tier.ordinal() : 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.tier.entered", tier.title().copy().withColor(tier.color))
			.withStyle(ChatFormatting.GOLD));
	}

	/** The player's luck for their tier (a modifier that isn't saved, so it's put back on joining and respawning). */
	private static void luck(ServerPlayer player) {
		AttributeInstance luck = player.getAttribute(Attributes.LUCK);
		if (luck == null) {
			return;
		}
		double amount = LUCK[of(player).ordinal()];
		if (amount <= 0.0) {
			luck.removeModifier(LUCK_ID);
		} else {
			luck.addOrUpdateTransientModifier(new AttributeModifier(LUCK_ID, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	// ---- hostiles take a tier ------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SCAN_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			if (player.level().dimension() == Level.NETHER) {
				feat(player, Progress.NETHER);
			}
			int tier = of(player).ordinal();
			if (tier <= 0) {
				continue;
			}
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(TIER_RANGE),
				m -> m.isAlive() && m.getAttachedOrElse(MOB_TIER, 0) < tier && hostile(m))) {
				raise(mob, tier);
			}
		}
	}

	/** Monsters (not a player's own) and the folk who are always foes: camps' raiders, orcs, goblins, assassins. */
	static boolean hostile(Mob mob) {
		if (mob instanceof Folk folk) {
			return folk.style() == FactionType.BARBARIAN || Orcs.isOrc(folk) || Goblins.isGoblin(folk) || Assassins.is(folk);
		}
		if (!(mob instanceof Enemy) || Thralls.isThrall(mob)) {
			return false;
		}
		if (mob instanceof com.jaymoskingdoms.monsters.Werewolf w && WerewolfPlayers.puppet(w)) {
			return false;
		}
		return !(mob instanceof OwnableEntity owned) || owned.getOwnerReference() == null;
	}

	/** Gives {@code mob} tier {@code tier}'s strength, keeping how hurt it is. */
	static void raise(Mob mob, int tier) {
		boolean first = mob.getAttachedOrElse(MOB_TIER, 0) == 0;
		float share = mob.getMaxHealth() > 0.0F ? mob.getHealth() / mob.getMaxHealth() : 1.0F;
		modify(mob, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH[tier], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE[tier], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(mob, Attributes.ARMOR, ARMOR_ID, ARMOR[tier], AttributeModifier.Operation.ADD_VALUE);
		modify(mob, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, TOUGHNESS[tier], AttributeModifier.Operation.ADD_VALUE);
		modify(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED[tier], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		mob.setHealth(mob.getMaxHealth() * share);
		mob.setAttached(MOB_TIER, tier);
		// (0.7.105) and the tier shows: gear for it (MonsterGear), the first time it takes one
		if (first && mob.level() instanceof net.minecraft.server.level.ServerLevel level) {
			MonsterGear.arm(level, mob, tier);
		}
	}

	private static void modify(Mob mob, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
		}
	}

	// ---- kills: experience, and feats ----------------------------------------------------------------------------------

	private static void died(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		// a tiered foe's extra experience, for the player who killed it
		Player killer = entity.getLastHurtByPlayer();
		int tier = entity.getAttachedOrElse(MOB_TIER, 0);
		if (tier > 0 && killer instanceof ServerPlayer && entity.shouldDropExperience()) {
			int extra = (int)Math.round(entity.getExperienceReward(level, killer) * EXPERIENCE[tier]);
			if (extra > 0) {
				ExperienceOrb.award(level, entity.position(), extra);
			}
		}
		EntityType<?> type = entity.getType();
		boolean dragon = type == EntityTypes.ENDER_DRAGON, wither = type == EntityTypes.WITHER;
		if (!(dragon || wither || type == EntityTypes.ELDER_GUARDIAN || type == EntityTypes.WARDEN || Monsters.legend(type) || BossBars.boss(entity))) {
			return;
		}
		// a great foe: everyone near shares the kill
		double range = dragon ? DRAGON_CREDIT_RANGE : CREDIT_RANGE;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || (player != killer && player.distanceToSqr(entity) > range * range)) {
				continue;
			}
			Progress p = progress(player);
			int feats = p.feats() | (dragon ? Progress.DRAGON : 0) | (wither ? Progress.WITHER : 0);
			update(player, new Progress(p.foes() + 1, feats, p.unlocked()));
		}
	}

	private static void feat(ServerPlayer player, int feat) {
		Progress p = progress(player);
		if (!p.has(feat)) {
			update(player, new Progress(p.foes(), p.feats() | feat, p.unlocked()));
		}
	}

	/** Saves {@code player}'s progress, and tells them of any tier it opens. */
	private static void update(ServerPlayer player, Progress now) {
		int before = progress(player).open();
		player.setAttached(PROGRESS, now);
		for (int i = before + 1; i <= now.open(); i++) {
			Tier tier = Tier.byOrdinal(i);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.tier.unlocked", tier.title().copy().withColor(tier.color))
				.withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.0F);
		}
	}

	// ---- for ops -------------------------------------------------------------------------------------------------------

	/** {@code /kingdom tier set|unlock|get}, for ops. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		var set = Commands.argument("players", EntityArgument.players());
		var unlock = Commands.argument("players", EntityArgument.players());
		for (Tier tier : Tier.values()) {
			set.then(Commands.literal(tier.key()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					Progress pr = progress(p);
					update(p, new Progress(pr.foes(), pr.feats(), Math.max(pr.unlocked(), tier.ordinal())));
					set(p, tier);
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.tier.set", count, tier.title()), true);
				return n;
			}));
			unlock.then(Commands.literal(tier.key()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					Progress pr = progress(p);
					update(p, new Progress(pr.foes(), pr.feats(), tier.ordinal()));
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.tier.unlock", count, tier.title()), true);
				return n;
			}));
		}
		return Commands.literal("tier").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("set").then(set))
			.then(Commands.literal("unlock").then(unlock))
			.then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
				ServerPlayer p = EntityArgument.getPlayer(c, "player");
				Progress pr = progress(p);
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.tier.is", p.getDisplayName(), of(p).title(),
					Tier.byOrdinal(pr.open()).title(), pr.foes()), false);
				return 1;
			})));
	}
}
