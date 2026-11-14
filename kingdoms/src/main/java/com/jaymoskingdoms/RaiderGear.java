package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.reaped.Reaped;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * What the living illagers carry, and what they think of you (Andy's picks from Immersive Pillagers, GPL-3.0: the ideas
 * only, our own code and art).
 * <ul>
 * <li><b>Wanted</b> ({@link #WANTED}, 0 to {@link #MOST}): every living illager a player kills adds to it (a warlord a
 * lot). From {@link #MARKED} up their hit squads ({@link ZombieHordes}) come at any hour, not only in the dark. It fades a
 * little each day, a friendly illager will sell your name back ({@link IllagerTalk}), and tearing up a poster of
 * yourself takes some off.</li>
 * <li><b>Wanted posters</b> ({@link #POSTER}): a squad's leader carries the poster of whoever it hunts. Use one that
 * names you to tear it up. A blank one (paper and ink) named on an anvil and pinned up on illager ground sets them on
 * that player.</li>
 * <li><b>The raider's horn</b> ({@link #HORN}): a warlord's. Blow it and a squad comes for you now; kill them all and the
 * illagers leave that ground alone for {@link #QUIET_DAYS} days ({@link #quiet}).</li>
 * <li><b>Rusty keys</b> ({@link #KEY}): now and then on an illager's body; one opens any lock of level
 * {@link #KEY_LEVEL} or less ({@link Lockpicking}), as every illager cache is.</li>
 * <li><b>Evoker's notes</b> ({@link #NOTES}) and the <b>crude totem</b> ({@link #TOTEM}): notes lie in the labs the
 * camps raise ({@link IllagerBuildings#lab}) and on evokers. Reading one teaches a little of their tongue (once a day).
 * {@link #WORKING} of them worked over a brewing stand with an emerald block in the other hand make a crude Totem of
 * Undying, or (two times in five) only smoke.</li>
 * </ul>
 */
public final class RaiderGear {
	/** The most a mark grows to, where squads start coming by day, and what adds to it and takes from it. */
	static final int MOST = 100, MARKED = 30, KILL = 6, WARLORD = 20, PINNED = 40, TORN = 25, BEATEN = 15, FADE = 3;
	/** By day a marked player's squads come this often against the night's rate, and a horde that rolls for them is a squad this often. */
	static final float DAY_SHARE = 0.6F, SQUAD_SHARE = 0.5F;
	/** Emeralds' worth to buy a point of the mark back. */
	static final int PRICE = 1;
	/** A beaten horn's quiet: how far round (blocks) and how long (days). */
	static final double QUIET_RANGE = 160.0;
	static final int QUIET_DAYS = 5;
	/** The horn: its uses, and how long between them (ticks). */
	static final int HORN_USES = 6, HORN_WAIT = 20 * 120;
	/** How often a key, and an evoker's notes, are on a body; the hardest lock a key opens. */
	static final float KEY_CHANCE = 0.06F, NOTES_CHANCE = 0.35F, HORN_CHANCE = 0.2F;
	static final int KEY_LEVEL = 2;
	/** Notes for the working, how often it comes off, and the tongue a reading teaches. */
	static final int WORKING = 5, READ_POINTS = 2;
	static final float WORKING_CHANCE = 0.6F;
	/** How near an illager camp's fire (blocks) counts as illager ground for pinning a poster. */
	static final double CAMP_GROUND = 40.0;

	public static Item POSTER, HORN, KEY, NOTES, TOTEM;

	/** On a player: how wanted by the illagers they are. Saved, kept through death. */
	static final AttachmentType<Integer> WANTED = AttachmentRegistry.create(JaymosKingdoms.id("illager_wanted"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** On the Overworld: ground the illagers leave alone (a packed position → the game time it ends). Saved. */
	static final AttachmentType<Map<String, Long>> QUIET = AttachmentRegistry.create(JaymosKingdoms.id("illager_quiet"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** Who read notes when (the day), this session. */
	private static final Map<UUID, Long> READ = new HashMap<>();

	private RaiderGear() {
	}

	static void register() {
		POSTER = item("wanted_poster", new Poster(props("wanted_poster").stacksTo(16)));
		HORN = item("raiders_horn", new Horn(props("raiders_horn").durability(HORN_USES)));
		KEY = item("rusty_key", new Hinted(props("rusty_key").stacksTo(16), "rusty_key"));
		NOTES = item("evoker_notes", new Notes(props("evoker_notes").stacksTo(16)));
		TOTEM = item("crude_totem", new Hinted(props("crude_totem").stacksTo(1).component(DataComponents.DEATH_PROTECTION, new DeathProtection(List.of(
			new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.REGENERATION, 300, 0), new MobEffectInstance(MobEffects.WEAKNESS, 600, 0)))))),
			"crude_totem"));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(POSTER);
			output.accept(HORN);
			output.accept(KEY);
			output.accept(NOTES);
			output.accept(TOTEM);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(RaiderGear::died);
		ServerTickEvents.END_SERVER_TICK.register(RaiderGear::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READ.clear());
	}

	private static Item.Properties props(String name) {
		return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name)));
	}

	private static Item item(String name, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, JaymosKingdoms.id(name), item);
	}

	// ---- wanted --------------------------------------------------------------------------------------------------------

	static int mark(Player player) {
		return player.getAttachedOrElse(WANTED, 0);
	}

	/** The illagers want {@code player} enough to come by day. */
	static boolean wanted(Player player) {
		return mark(player) >= MARKED;
	}

	static void add(ServerPlayer player, int n) {
		int before = mark(player), after = Math.clamp(before + n, 0, MOST);
		if (after == before) {
			return;
		}
		player.setAttached(WANTED, after);
		if (before < MARKED && after >= MARKED) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wanted.marked").withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8.0F, 0.9F);
		} else if (before >= MARKED && after < MARKED) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wanted.cleared").withStyle(ChatFormatting.GRAY));
		}
	}

	private static void died(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof AbstractIllager) || victim instanceof Reaped || !(source.getEntity() instanceof ServerPlayer player)
			|| !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		add(player, Illagers.warlord(victim) ? WARLORD : KILL);
		if (Illagers.warlord(victim) || victim.entityTags().contains(ZombieHordes.HORN_TAG)) {
			victim.spawnAtLocation(level, new ItemStack(HORN));
		}
		if (victim.getRandom().nextFloat() < KEY_CHANCE) {
			victim.spawnAtLocation(level, new ItemStack(KEY));
		}
		if (victim instanceof SpellcasterIllager && victim.getRandom().nextFloat() < NOTES_CHANCE) {
			victim.spawnAtLocation(level, new ItemStack(NOTES));
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getGameTime() % 24000L != 0L) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			add(player, -FADE);
		}
		Map<String, Long> quiet = level.getAttachedOrElse(QUIET, Map.of());
		if (!quiet.isEmpty()) {
			Map<String, Long> left = new HashMap<>(quiet);
			left.values().removeIf(until -> until <= level.getGameTime());
			level.setAttached(QUIET, Map.copyOf(left));
		}
	}

	/** What a friendly illager asks to forget {@code player}'s face (thousandths of the local coin), or 0 with nothing to forget. */
	static long price(ServerPlayer player) {
		return Money.worth((long) mark(player) * PRICE);
	}

	/** {@code player} buys their name back: true if they could pay. */
	static boolean buyOff(ServerPlayer player) {
		long price = price(player);
		if (price <= 0L || !Money.canPay(player, price)) {
			return false;
		}
		Money.charge(player, price);
		add(player, -MOST);
		return true;
	}

	// ---- the horn's quiet ---------------------------------------------------------------------------------------------

	/** The illagers leave the ground round {@code at} alone for now (a horn's squad was beaten there). */
	static boolean quiet(ServerLevel level, BlockPos at) {
		ServerLevel overworld = level.getServer().overworld();
		if (level != overworld) {
			return false;
		}
		long now = overworld.getGameTime();
		for (Map.Entry<String, Long> e : overworld.getAttachedOrElse(QUIET, Map.<String, Long>of()).entrySet()) {
			if (e.getValue() > now && BlockPos.of(Long.parseLong(e.getKey())).closerThan(at, QUIET_RANGE)) {
				return true;
			}
		}
		return false;
	}

	/** A horn's squad was killed to the last round {@code at}: the ground is left alone, and {@code hunted} is wanted a little less. */
	static void beaten(ServerLevel level, BlockPos at, @Nullable ServerPlayer hunted) {
		ServerLevel overworld = level.getServer().overworld();
		if (level == overworld) {
			Map<String, Long> quiet = new HashMap<>(overworld.getAttachedOrElse(QUIET, Map.of()));
			quiet.put(Long.toString(at.asLong()), overworld.getGameTime() + QUIET_DAYS * 24000L);
			overworld.setAttached(QUIET, Map.copyOf(quiet));
		}
		if (hunted != null) {
			add(hunted, -BEATEN);
			hunted.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wanted.quiet").withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- posters ------------------------------------------------------------------------------------------------------

	/** A poster of {@code name}. */
	static ItemStack poster(String name) {
		ItemStack stack = new ItemStack(POSTER);
		CompoundTag tag = new CompoundTag();
		tag.putString("wanted", name);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** Who {@code stack} names: its own mark, else the name it was given on an anvil; null for a blank one. */
	static @Nullable String named(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			String name = data.copyTag().getStringOr("wanted", "");
			if (!name.isEmpty()) {
				return name;
			}
		}
		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		return custom == null ? null : custom.getString().trim();
	}

	/** {@code at} is illager ground: near a living illager camp's fire, or inside one of the illagers' places. */
	static boolean illagerGround(ServerLevel level, BlockPos at) {
		MinecraftServer server = level.getServer();
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.type() == FactionType.BARBARIAN && Camps.illager(server, f.id()) && !ReapedHolds.is(server, f.id())
				&& f.seat().closerThan(at, CAMP_GROUND)) {
				return true;
			}
		}
		return false;
	}

	static final class Poster extends Item {
		Poster(Properties properties) {
			super(properties);
		}

		@Override
		public Component getName(ItemStack stack) {
			String name = named(stack);
			return name == null ? super.getName(stack) : Component.translatable("item.jaymoskingdoms.wanted_poster.of", name);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = sp.getItemInHand(hand);
			String name = named(stack);
			if (name == null || !name.equalsIgnoreCase(sp.getGameProfile().name())) {
				return InteractionResult.PASS;
			}
			// your own face: torn up
			stack.consume(1, sp);
			add(sp, -TORN);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.6F);
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = context.getItemInHand();
			String name = named(stack);
			ServerPlayer target = name == null ? null : level.getServer().getPlayerList().getPlayerByName(name);
			Direction face = context.getClickedFace();
			if (target == null || target == sp || face.getAxis().isVertical() || !illagerGround(level, context.getClickedPos())) {
				return InteractionResult.PASS;
			}
			ItemFrame frame = new ItemFrame(level, context.getClickedPos().relative(face), face);
			if (frame.survives()) {
				frame.setItem(stack.copyWithCount(1), false);
				level.addFreshEntity(frame);
			}
			stack.consume(1, sp);
			add(target, PINNED);
			level.playSound(null, context.getClickedPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 1.0F, 0.8F);
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
			out.accept(Component.translatable("item.jaymoskingdoms.wanted_poster.tooltip" + (named(stack) == null ? ".blank" : "")).withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- the horn -----------------------------------------------------------------------------------------------------

	static final class Horn extends Item {
		Horn(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = sp.getItemInHand(hand);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 16.0F, 1.0F);
			sp.getCooldowns().addCooldown(stack, HORN_WAIT);
			if (ZombieHordes.horn(sp) > 0) {
				stack.hurtAndBreak(1, sp, hand);
			}
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
			out.accept(Component.translatable("item.jaymoskingdoms.raiders_horn.tooltip").withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- notes and the working ------------------------------------------------------------------------------------------

	static final class Notes extends Item {
		Notes(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			long day = level.getGameTime() / 24000L;
			Long read = READ.get(sp.getUUID());
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			if (read == null || read < day) {
				READ.put(sp.getUUID(), day);
				IllagerTongue.learn(sp, READ_POINTS, "read");
			}
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer sp)
				|| !level.getBlockState(context.getClickedPos()).is(Blocks.BREWING_STAND)) {
				return InteractionResult.PASS;
			}
			ItemStack notes = context.getItemInHand();
			InteractionHand other = context.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			ItemStack gem = sp.getItemInHand(other);
			if (notes.getCount() < WORKING || !gem.is(Items.EMERALD_BLOCK)) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.working.lacking", WORKING).withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			notes.consume(WORKING, sp);
			gem.consume(1, sp);
			BlockPos at = context.getClickedPos();
			if (level.getRandom().nextFloat() < WORKING_CHANCE) {
				Fx.sigil(level, net.minecraft.world.phys.Vec3.atBottomCenterOf(at).add(0.0, 1.0, 0.0), 1.0F, 0x3FBF6F, 40);
				level.playSound(null, at, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
				if (!sp.getInventory().add(new ItemStack(TOTEM))) {
					sp.spawnAtLocation(level, new ItemStack(TOTEM));
				}
			} else {
				level.sendParticles(Fx.miasma(0x2A2A2A, 1.4F), at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.02);
				level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.7F);
				sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.working.failed").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
			out.accept(Component.translatable("item.jaymoskingdoms.evoker_notes.tooltip").withStyle(ChatFormatting.GRAY));
		}
	}

	/** An item with one grey line under its name. */
	static final class Hinted extends Item {
		private final String key;

		Hinted(Properties properties, String key) {
			super(properties);
			this.key = key;
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
			out.accept(Component.translatable("item.jaymoskingdoms." + this.key + ".tooltip").withStyle(ChatFormatting.GRAY));
		}
	}
}
