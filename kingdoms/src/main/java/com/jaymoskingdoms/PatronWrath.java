package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * (0.7.581) Andy (seven-mods plan, patch 5): patron wrath and grace, The God's ideas made our own, all on the one score
 * the patrons already keep, favour ({@link Patrons}).
 * <ul>
 * <li><b>Deeds:</b> beasts killed and not eaten (more than {@link #WASTE_FREE} beyond the meat you've eaten) and tame
 * animals slain cost favour with the patrons of the wild and the hearth; a monster cleared now and then pleases any
 * patron a little.</li>
 * <li><b>The wrath ladder:</b> favour only falls under the first tier through sin (neglect stops there, unless faith is
 * strict). Below it, the further it falls the worse it gets: omens, then wilting crops, then beasts fleeing from you, then
 * lightning near, and at nothing, a smiting. Each is only felt; nothing is said.</li>
 * <li><b>The watcher:</b> with favour very low, looking up under the open sky, now and then a vast still figure stands in
 * the clouds. Only you see it (it is sent to you alone).</li>
 * <li><b>Grace:</b> the most devout (the fourth tier) are sometimes left a gift at dawn, at their feet.</li>
 * <li><b>Happenings:</b> thirteen odd small events about the sworn, each of their own patron's kind.</li>
 * <li><b>Strict faith:</b> {@code jaymoskingdoms:patron_strict_faith} for a whole server, or {@code /patron strict} for
 * one player: a broken commandment costs twice the favour and neglect has no floor.</li>
 * </ul>
 */
public final class PatronWrath {
	// ---- tuning ----
	static final int EVERY = 100, LOOK_EVERY = 20;
	/** Favour under which each rung of the ladder begins: omens, wilting, fleeing, lightning (and at 0, the smiting). */
	static final int RUNG_OMEN = Patrons.TIER_AT[0], RUNG_WILT = 7, RUNG_FLEE = 5, RUNG_BOLT = 3;
	/** The chance each {@link #EVERY} that wrath shows itself to someone on the ladder. */
	static final float WRATH_CHANCE = 0.05F;
	static final double WILT_RANGE = 7.0, FLEE_RANGE = 16.0;
	static final int WILT_MOST = 6, BOLT_NEAR = 4, BOLT_FAR = 10;
	static final float SMITE_DAMAGE = 6.0F;
	/** Beasts killed past those eaten before it costs, what each further one costs, and what a tame one costs the Hearthkeeper. */
	static final int WASTE_FREE = 3, WASTE_COST = 2, TAME_COST = 4;
	static final float MONSTER_DEED_CHANCE = 0.25F;
	/** The watcher: under this favour, looking this far up (pitch), the chance each {@link #LOOK_EVERY}, how long it stands. */
	static final int WATCHER_BELOW = RUNG_FLEE;
	static final float LOOK_UP = -35.0F, WATCHER_CHANCE = 0.08F, WATCHER_SCALE = 90.0F;
	static final int WATCHER_TICKS = 140;
	static final double WATCHER_OFF = 110.0, WATCHER_LEAST_Y = 205.0, WATCHER_OVER = 70.0;
	/** Grace: the tier, the chance each dawn, the dawn's window (ticks after sunrise). */
	static final int GRACE_TIER = 4, DAWN_WINDOW = 1200;
	static final float GRACE_CHANCE = 0.25F;
	/** Happenings: the chance each {@link #EVERY} for a sworn player, and at most one a day each. */
	static final float HAPPENING_CHANCE = 0.003F;

	/** (0.7.581) jaymoskingdoms:patron_strict_faith: every player keeps strict faith. */
	public static GameRule<Boolean> STRICT_ALL;
	/** On a player: they chose strict faith ({@code /patron strict}). Kept through death. */
	static final AttachmentType<Boolean> STRICT = AttachmentRegistry.create(JaymosKingdoms.id("strict_faith"),
		b -> b.persistent(Codec.BOOL).copyOnDeath());

	/** Beasts killed and not eaten, as of a day (one forgiven a day). */
	record Waste(int count, long day) {
		static final Codec<Waste> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("count", 0).forGetter(Waste::count),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Waste::day)).apply(i, Waste::new));

		int now(long today) {
			return (int)Math.max(0L, count - Math.max(0L, today - day));
		}
	}

	static final AttachmentType<Waste> WASTE = AttachmentRegistry.create(JaymosKingdoms.id("wasted_beasts"),
		b -> b.persistent(Waste.CODEC).copyOnDeath());

	/** A watcher shown: the fake entity's id, and when it goes. */
	private record Shown(int id, long until) {
	}

	/** 26.3 has no wolf howl: a big wolf's long growl, pitched low and far off, stands in for it (as {@link FeralCurse}'s). */
	private static SoundEvent howl() {
		return SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value();
	}

	private static final Map<UUID, Shown> WATCHERS = new HashMap<>();
	/** The day each player last had a smiting, a watcher, a dawn gift, a happening. Not kept. */
	private static final Map<UUID, Long> SMITTEN = new HashMap<>(), WATCHED = new HashMap<>(), GRACED = new HashMap<>(), HAPPENED = new HashMap<>();

	private PatronWrath() {
	}

	static void register() {
		STRICT_ALL = GameRuleBuilder.forBoolean(false).category(GameRuleCategory.MISC).buildAndRegister(JaymosKingdoms.id("patron_strict_faith"));
		ServerTickEvents.END_SERVER_TICK.register(PatronWrath::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WATCHERS.clear();
			SMITTEN.clear();
			WATCHED.clear();
			GRACED.clear();
			HAPPENED.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("patron")
			.then(Commands.literal("strict").executes(c -> strictCommand(c, null))
				.then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> strictCommand(c, BoolArgumentType.getBool(c, "on")))))));
	}

	// ---- strict faith ----------------------------------------------------------------------------------------------------------

	/** {@code player} keeps strict faith: the server's rule, or their own choice. */
	public static boolean strict(Player player) {
		if (player.level() instanceof ServerLevel level && level.getServer().overworld().getGameRules().get(STRICT_ALL)) {
			return true;
		}
		return Boolean.TRUE.equals(player.getAttached(STRICT));
	}

	private static int strictCommand(CommandContext<CommandSourceStack> c, Boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer sp = c.getSource().getPlayerOrException();
		boolean all = sp.level().getServer().overworld().getGameRules().get(STRICT_ALL);
		if (on != null && !all) {
			if (on) {
				sp.setAttached(STRICT, true);
			} else {
				sp.removeAttached(STRICT);
			}
		}
		String key = all ? "server" : strict(sp) ? "on" : "off";
		sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.patron.strict." + key).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	// ---- deeds -----------------------------------------------------------------------------------------------------------------

	/** (From {@link Patrons#killed}) {@code sp}, sworn, killed {@code victim}. */
	static void killed(ServerPlayer sp, ServerLevel level, LivingEntity victim) {
		Patron mine = Patrons.of(sp);
		if (mine == null) {
			return;
		}
		if (victim instanceof Enemy && !(victim instanceof Player)) {
			if (level.getRandom().nextFloat() < MONSTER_DEED_CHANCE) {
				Patrons.deed(sp, mine, Patrons.DEED_SMALL, "monster");
			}
			return;
		}
		boolean tame = victim instanceof OwnableEntity own && own.getOwnerReference() != null;
		if (tame) {
			slight(sp, Patron.HEARTHKEEPER, TAME_COST);
			return;
		}
		if (victim instanceof Animal || victim instanceof AbstractFish) {
			long today = Patrons.today(level.getServer());
			int wasted = sp.getAttachedOrElse(WASTE, new Waste(0, today)).now(today) + 1;
			sp.setAttached(WASTE, new Waste(wasted, today));
			if (wasted > WASTE_FREE) {
				slight(sp, victim instanceof AbstractFish ? Patron.DEEP_MOTHER : Patron.GREEN_HUNTSMAN, WASTE_COST);
				slight(sp, Patron.HEARTHKEEPER, WASTE_COST / 2);
			}
		}
	}

	/** (From {@link Commandments#ate}) meat or fish eaten pays for a beast killed. */
	static void ate(ServerPlayer sp, ItemStack stack) {
		if (!stack.is(ItemTags.MEAT) && !stack.is(ItemTags.FISHES)) {
			return;
		}
		long today = Patrons.today(sp.level().getServer());
		Waste w = sp.getAttached(WASTE);
		if (w != null && w.now(today) > 0) {
			sp.setAttached(WASTE, new Waste(w.now(today) - 1, today));
		}
	}

	/** {@code p} is slighted: favour lost, if {@code sp} is sworn to them. */
	private static void slight(ServerPlayer sp, Patron p, int n) {
		if (n > 0 && Patrons.of(sp) == p) {
			Patrons.lose(sp, n);
		}
	}

	// ---- every tick ------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		for (Iterator<Map.Entry<UUID, Shown>> it = WATCHERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Shown> e = it.next();
			if (now >= e.getValue().until()) {
				ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
				if (sp != null) {
					sp.connection.send(new ClientboundRemoveEntitiesPacket(e.getValue().id()));
				}
				it.remove();
			}
		}
		int tick = server.getTickCount();
		if (tick % LOOK_EVERY != 0) {
			return;
		}
		long today = Patrons.today(server);
		long clock = overworld.getOverworldClockTime();
		boolean dawn = Math.floorMod(clock, 24000L) < DAWN_WINDOW;
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!sp.isAlive() || sp.isSpectator() || sp.isCreative()) {
				continue;
			}
			Patrons.State s = Patrons.state(sp);
			Patron p = s.patronOf();
			if (p == null) {
				continue;
			}
			ServerLevel level = sp.level();
			RandomSource random = level.getRandom();
			if (s.favour() < WATCHER_BELOW) {
				watcher(sp, level, today, now);
			}
			if (tick % EVERY != 0) {
				continue;
			}
			if (s.favour() < RUNG_OMEN && random.nextFloat() < WRATH_CHANCE) {
				wrath(sp, level, s.favour(), today, random);
			}
			if (dawn && s.tier() >= GRACE_TIER && GRACED.getOrDefault(sp.getUUID(), Long.MIN_VALUE) != today) {
				GRACED.put(sp.getUUID(), today);
				if (random.nextFloat() < GRACE_CHANCE) {
					grace(sp, level, p, random);
				}
			}
			if (HAPPENED.getOrDefault(sp.getUUID(), Long.MIN_VALUE) != today && random.nextFloat() < HAPPENING_CHANCE && happening(sp, level, p, random)) {
				HAPPENED.put(sp.getUUID(), today);
			}
		}
	}

	// ---- the wrath ladder ------------------------------------------------------------------------------------------------------

	/** The rung {@code favour} stands on: 0 none, 1 omens, 2 wilting, 3 fleeing, 4 lightning, 5 the smiting. */
	static int rung(int favour) {
		return favour <= 0 ? 5 : favour < RUNG_BOLT ? 4 : favour < RUNG_FLEE ? 3 : favour < RUNG_WILT ? 2 : favour < RUNG_OMEN ? 1 : 0;
	}

	private static void wrath(ServerPlayer sp, ServerLevel level, int favour, long today, RandomSource random) {
		int top = rung(favour);
		if (top <= 0) {
			return;
		}
		// the worst it may do now, weighted toward the worst
		int r = Math.max(1, top - random.nextInt(2) - (random.nextInt(4) == 0 ? 1 : 0));
		switch (r) {
			case 1 -> omen(sp, level, random);
			case 2 -> {
				if (!wilt(level, sp, random)) {
					omen(sp, level, random);
				}
			}
			case 3 -> {
				if (!flee(level, sp)) {
					wilt(level, sp, random);
				}
			}
			case 4 -> bolt(level, sp, random, false);
			default -> {
				if (SMITTEN.getOrDefault(sp.getUUID(), Long.MIN_VALUE) != today) {
					SMITTEN.put(sp.getUUID(), today);
					smite(level, sp, random);
				} else {
					bolt(level, sp, random, false);
				}
			}
		}
	}

	/** A sign meant for {@code sp} alone: a far bell, a howl, a snuffed flame, a cold breath. */
	private static void omen(ServerPlayer sp, ServerLevel level, RandomSource random) {
		switch (random.nextInt(4)) {
			case 0 -> {
				Vec3 far = sp.position().add((random.nextDouble() - 0.5) * 60.0, 0.0, (random.nextDouble() - 0.5) * 60.0);
				sound(sp, SoundEvents.BELL_RESONATE, far, 1.5F, 0.4F);
			}
			case 1 -> sound(sp, howl(), sp.position().add(random.nextGaussian() * 30.0, 0.0, random.nextGaussian() * 30.0), 1.2F, 0.6F);
			case 2 -> {
				if (!snuff(level, sp, random)) {
					sound(sp, SoundEvents.AMBIENT_CAVE.value(), sp.position(), 0.8F, 0.7F);
				}
			}
			default -> {
				Vec3 look = sp.getLookAngle();
				Vec3 at = sp.getEyePosition().add(look.x * 2.0, 0.0, look.z * 2.0);
				level.sendParticles(sp, ParticleTypes.LARGE_SMOKE, true, false, at.x, at.y, at.z, 12, 0.6, 0.4, 0.6, 0.01);
				sound(sp, SoundEvents.SOUL_ESCAPE.value(), at, 0.8F, 0.5F);
			}
		}
	}

	/** A lit campfire or candle near {@code sp} goes out. */
	private static boolean snuff(ServerLevel level, ServerPlayer sp, RandomSource random) {
		BlockPos from = sp.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-8, -3, -8), from.offset(8, 3, 8))) {
			BlockState st = level.getBlockState(p);
			if ((st.getBlock() instanceof CampfireBlock || st.getBlock() instanceof AbstractCandleBlock) && st.hasProperty(BlockStateProperties.LIT)
				&& st.getValue(BlockStateProperties.LIT) && random.nextInt(3) == 0) {
				level.setBlockAndUpdate(p, st.setValue(BlockStateProperties.LIT, false));
				level.playSound(null, p, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
				level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
				return true;
			}
		}
		return false;
	}

	/** Crops near {@code sp} wilt back a stage or two. */
	private static boolean wilt(ServerLevel level, ServerPlayer sp, RandomSource random) {
		int done = 0;
		BlockPos from = sp.blockPosition();
		int r = (int)WILT_RANGE;
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-r, -2, -r), from.offset(r, 2, r))) {
			BlockState st = level.getBlockState(p);
			if (st.getBlock() instanceof CropBlock crop && crop.getAge(st) > 0 && random.nextInt(2) == 0) {
				level.setBlockAndUpdate(p, crop.getStateForAge(Math.max(0, crop.getAge(st) - 1 - random.nextInt(2))));
				level.sendParticles(ParticleTypes.ASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.25, 0.2, 0.25, 0.0);
				if (++done >= WILT_MOST) {
					break;
				}
			}
		}
		return done > 0;
	}

	/** Every beast near {@code sp} bolts from them. */
	private static boolean flee(ServerLevel level, ServerPlayer sp) {
		boolean any = false;
		for (Animal a : level.getEntitiesOfClass(Animal.class, sp.getBoundingBox().inflate(FLEE_RANGE),
			x -> x.isAlive() && !(x instanceof OwnableEntity o && o.getOwnerReference() != null) && !x.isLeashed())) {
			Vec3 away = DefaultRandomPos.getPosAway(a, 16, 7, sp.position());
			if (away != null) {
				a.getNavigation().moveTo(away.x, away.y, away.z, 1.8);
				any = true;
			}
		}
		if (any) {
			level.playSound(null, sp.blockPosition(), SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 0.6F, 0.6F);
		}
		return any;
	}

	/** Lightning near {@code sp} (or, {@code on} them), under the open sky only. */
	private static boolean bolt(ServerLevel level, ServerPlayer sp, RandomSource random, boolean on) {
		Vec3 at = sp.position();
		if (!on) {
			double angle = random.nextDouble() * Math.PI * 2.0, d = BOLT_NEAR + random.nextDouble() * (BOLT_FAR - BOLT_NEAR);
			at = at.add(Math.cos(angle) * d, 0.0, Math.sin(angle) * d);
		}
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(at));
		if (!level.canSeeSky(ground) || on && !level.canSeeSky(sp.blockPosition().above())) {
			return false;
		}
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt == null) {
			return false;
		}
		bolt.setPos(on ? sp.getX() : ground.getX() + 0.5, on ? sp.getY() : ground.getY(), on ? sp.getZ() : ground.getZ() + 0.5);
		level.addFreshEntity(bolt);
		return true;
	}

	/** The bottom of the ladder: struck down, under the sky by lightning, beneath it by the earth itself. */
	private static void smite(ServerLevel level, ServerPlayer sp, RandomSource random) {
		if (bolt(level, sp, random, true)) {
			return;
		}
		sp.hurtServer(level, level.damageSources().magic(), SMITE_DAMAGE);
		sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, 120, 0, false, false));
		sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200, 1, false, false));
		level.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.8F, 0.4F);
		PowersCompat.shake(sp, 0.6F);
	}

	// ---- the watcher -----------------------------------------------------------------------------------------------------------

	/** Looking up under the open sky, with favour very low: now and then, once a day at most, the figure in the clouds. */
	private static void watcher(ServerPlayer sp, ServerLevel level, long today, long now) {
		if (sp.getXRot() > LOOK_UP || WATCHERS.containsKey(sp.getUUID()) || level.dimension() != Level.OVERWORLD
			|| WATCHED.getOrDefault(sp.getUUID(), Long.MIN_VALUE) == today || !level.canSeeSky(BlockPos.containing(sp.getEyePosition()))
			|| level.getRandom().nextFloat() >= WATCHER_CHANCE) {
			return;
		}
		WATCHED.put(sp.getUUID(), today);
		Vec3 look = Vec3.directionFromRotation(0.0F, sp.getYRot());
		double y = Math.max(WATCHER_LEAST_Y, sp.getEyeY() + WATCHER_OVER);
		Vec3 at = new Vec3(sp.getX() + look.x * WATCHER_OFF, y, sp.getZ() + look.z * WATCHER_OFF);
		Display.ItemDisplay figure = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, JaymosKingdoms.id("watcher"));
		figure.setItemStack(stack);
		figure.setItemTransform(ItemDisplayContext.NONE);
		figure.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		figure.setTransformation(new Transformation(new Vector3f(), null, new Vector3f(WATCHER_SCALE), null));
		figure.setBrightnessOverride(new Brightness(15, 15));
		figure.setViewRange(12.0F);
		figure.setWidth(WATCHER_SCALE);
		figure.setHeight(WATCHER_SCALE);
		figure.setPos(at.x, at.y, at.z);
		sp.connection.send(new ClientboundAddEntityPacket(figure.getId(), figure.getUUID(), at.x, at.y, at.z, 0.0F, 0.0F, EntityTypes.ITEM_DISPLAY, 0,
			Vec3.ZERO, 0.0));
		List<SynchedEntityData.DataValue<?>> data = figure.getEntityData().getNonDefaultValues();
		if (data != null) {
			sp.connection.send(new ClientboundSetEntityDataPacket(figure.getId(), data));
		}
		WATCHERS.put(sp.getUUID(), new Shown(figure.getId(), now + WATCHER_TICKS));
		sound(sp, SoundEvents.AMBIENT_CAVE.value(), at, 2.0F, 0.5F);
	}

	// ---- grace -----------------------------------------------------------------------------------------------------------------

	/** A gift at {@code sp}'s feet at dawn, of their patron's kind. */
	private static void grace(ServerPlayer sp, ServerLevel level, Patron p, RandomSource random) {
		ItemStack gift = switch (p) {
			case HEARTHKEEPER -> pick(random, new ItemStack(Items.BREAD, 3), new ItemStack(Items.HONEY_BOTTLE, 2), new ItemStack(Items.CAKE));
			case ANVIL_FATHER -> random.nextInt(8) == 0 ? new ItemStack(Items.DIAMOND) : pick(random, new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT, 2));
			case DEEP_MOTHER -> pick(random, new ItemStack(Items.NAUTILUS_SHELL), new ItemStack(Items.PRISMARINE_CRYSTALS, 4), new ItemStack(Items.COOKED_SALMON, 3));
			case GREEN_HUNTSMAN -> pick(random, new ItemStack(Items.ARROW, 8), new ItemStack(Items.RABBIT_FOOT), new ItemStack(Items.LEATHER, 4));
			case LANTERN_WARDEN -> pick(random, new ItemStack(Items.CANDLE, 4), new ItemStack(Items.GLOWSTONE_DUST, 6), new ItemStack(Items.LANTERN));
			case CROWNED_SUN -> pick(random, new ItemStack(Items.GOLD_NUGGET, 9), new ItemStack(Items.GOLDEN_CARROT, 2), new ItemStack(Items.SUNFLOWER));
			case ASH_WHISPERER -> pick(random, new ItemStack(Items.GUNPOWDER, 4), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.COAL, 6));
			case TRUTH -> pick(random, new ItemStack(Items.BOOK), new ItemStack(Items.PAPER, 6), new ItemStack(Items.EMERALD, 2));
		};
		ItemEntity item = new ItemEntity(level, sp.getX(), sp.getY() + 0.3, sp.getZ(), gift, 0.0, 0.05, 0.0);
		item.setPickUpDelay(10);
		level.addFreshEntity(item);
		level.sendParticles(Fx.LIGHT, sp.getX(), sp.getY() + 0.5, sp.getZ(), 10, 0.3, 0.3, 0.3, 0.01);
		level.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	private static ItemStack pick(RandomSource random, ItemStack... stacks) {
		return stacks[random.nextInt(stacks.length)];
	}

	// ---- happenings ------------------------------------------------------------------------------------------------------------

	/** Something odd of {@code p}'s kind about {@code sp}: true if it happened. */
	private static boolean happening(ServerPlayer sp, ServerLevel level, Patron p, RandomSource random) {
		boolean second = random.nextBoolean();
		return switch (p) {
			case HEARTHKEEPER -> second ? kindle(level, sp) : lay(level, sp);
			case ANVIL_FATHER -> second ? glint(level, sp) : ringBelow(level, sp);
			case DEEP_MOTHER -> second && level.isRainingAt(sp.blockPosition()) ? fishRain(level, sp, random) : seaHush(level, sp);
			case GREEN_HUNTSMAN -> second ? beastsWatch(level, sp) : howls(sp, random);
			case LANTERN_WARDEN -> second ? undeadStill(level, sp) : soulRing(level, sp);
			case CROWNED_SUN -> halo(level, sp);
			case ASH_WHISPERER -> second ? whisper(sp, random) : gutter(level, sp);
			case TRUTH -> second ? bells(level, sp) : pages(level, sp);
		};
	}

	/** Hearthkeeper: unlit campfires and candles near light of themselves. */
	private static boolean kindle(ServerLevel level, ServerPlayer sp) {
		boolean any = false;
		BlockPos from = sp.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-12, -3, -12), from.offset(12, 3, 12))) {
			BlockState st = level.getBlockState(p);
			if ((st.getBlock() instanceof CampfireBlock || st.getBlock() instanceof AbstractCandleBlock) && st.hasProperty(BlockStateProperties.LIT)
				&& !st.getValue(BlockStateProperties.LIT) && !(st.hasProperty(BlockStateProperties.WATERLOGGED) && st.getValue(BlockStateProperties.WATERLOGGED))) {
				level.setBlockAndUpdate(p, st.setValue(BlockStateProperties.LIT, true));
				level.playSound(null, p, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
				any = true;
			}
		}
		return any;
	}

	/** Hearthkeeper: every hen near lays at once. */
	private static boolean lay(ServerLevel level, ServerPlayer sp) {
		List<Chicken> hens = level.getEntitiesOfClass(Chicken.class, sp.getBoundingBox().inflate(16.0), c -> c.isAlive() && !c.isBaby());
		for (Chicken c : hens) {
			c.spawnAtLocation(level, new ItemStack(Items.EGG));
			level.playSound(null, c.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
		return !hens.isEmpty();
	}

	/** Anvil-Father: the ore round you glints a moment. */
	private static boolean glint(ServerLevel level, ServerPlayer sp) {
		boolean any = false;
		BlockPos from = sp.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-8, -6, -8), from.offset(8, 6, 8))) {
			if (level.getBlockState(p).is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES)) {
				level.sendParticles(sp, Fx.EMBER, true, false, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.0);
				any = true;
			}
		}
		if (any) {
			sound(sp, SoundEvents.AMETHYST_BLOCK_RESONATE, sp.position(), 0.7F, 0.6F);
		}
		return any;
	}

	/** Anvil-Father: an anvil rings three times, deep under you. */
	private static boolean ringBelow(ServerLevel level, ServerPlayer sp) {
		Vec3 deep = sp.position().add(0.0, -24.0, 0.0);
		for (int i = 0; i < 3; i++) {
			sound(sp, SoundEvents.ANVIL_LAND, deep.add(i * 2.0, 0.0, 0.0), 1.0F, 0.5F + i * 0.05F);
		}
		level.sendParticles(sp, ParticleTypes.ASH, true, false, sp.getX(), sp.getY() + 0.2, sp.getZ(), 20, 1.5, 0.1, 1.5, 0.0);
		return true;
	}

	/** Deep Mother: fish fall with the rain. */
	private static boolean fishRain(ServerLevel level, ServerPlayer sp, RandomSource random) {
		int n = 3 + random.nextInt(4);
		for (int i = 0; i < n; i++) {
			ItemStack fish = new ItemStack(random.nextBoolean() ? Items.COD : Items.SALMON);
			ItemEntity e = new ItemEntity(level, sp.getX() + random.nextGaussian() * 5.0, sp.getY() + 14.0 + random.nextInt(6), sp.getZ() + random.nextGaussian() * 5.0,
				fish, 0.0, -0.2, 0.0);
			level.addFreshEntity(e);
		}
		return true;
	}

	/** Deep Mother: the water near you stirs and hums. */
	private static boolean seaHush(ServerLevel level, ServerPlayer sp) {
		boolean any = false;
		BlockPos from = sp.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-8, -3, -8), from.offset(8, 2, 8))) {
			if (level.getBlockState(p).is(Blocks.WATER) && level.getBlockState(p.above()).isAir()) {
				level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5, 2, 0.2, 0.0, 0.2, 0.0);
				any = true;
			}
		}
		if (any) {
			sound(sp, SoundEvents.CONDUIT_AMBIENT, sp.position(), 1.0F, 0.8F);
		}
		return any;
	}

	/** Green Huntsman: every beast near stops and turns to look at you. */
	private static boolean beastsWatch(ServerLevel level, ServerPlayer sp) {
		List<Animal> beasts = level.getEntitiesOfClass(Animal.class, sp.getBoundingBox().inflate(24.0), LivingEntity::isAlive);
		for (Animal a : beasts) {
			a.getNavigation().stop();
			float yaw = (float)(Mth.atan2(sp.getZ() - a.getZ(), sp.getX() - a.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			a.setYRot(yaw);
			a.setYHeadRot(yaw);
			a.setYBodyRot(yaw);
			a.getLookControl().setLookAt(sp);
		}
		return beasts.size() >= 2;
	}

	/** Green Huntsman: a howl answered from all round. */
	private static boolean howls(ServerPlayer sp, RandomSource random) {
		for (int i = 0; i < 3; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			sound(sp, howl(), sp.position().add(Math.cos(angle) * 40.0, 0.0, Math.sin(angle) * 40.0), 1.5F, 0.85F + random.nextFloat() * 0.3F);
		}
		return true;
	}

	/** Lantern Warden: the dead near you stand still a breath. */
	private static boolean undeadStill(ServerLevel level, ServerPlayer sp) {
		List<Mob> dead = level.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(16.0),
			m -> m.isAlive() && m.is(net.minecraft.tags.EntityTypeTags.UNDEAD));
		for (Mob m : dead) {
			m.getNavigation().stop();
			m.setTarget(null);
			m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 6, false, false));
			level.sendParticles(ParticleTypes.SOUL, m.getX(), m.getEyeY(), m.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
		}
		return !dead.isEmpty();
	}

	/** Lantern Warden: a ring of pale flame round you. */
	private static boolean soulRing(ServerLevel level, ServerPlayer sp) {
		ring(level, ParticleTypes.SOUL_FIRE_FLAME, sp.position().add(0.0, 0.2, 0.0), 2.5, 24);
		sound(sp, SoundEvents.SOUL_ESCAPE.value(), sp.position(), 1.0F, 1.0F);
		return true;
	}

	/** Crowned Sun: a halo of light about you, and a chime. */
	private static boolean halo(ServerLevel level, ServerPlayer sp) {
		ring(level, Fx.LIGHT, sp.position().add(0.0, sp.getBbHeight() + 0.6, 0.0), 0.7, 16);
		level.playSound(null, sp.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.5F, 1.8F);
		return true;
	}

	/** Ash Whisperer: a whisper just behind you. */
	private static boolean whisper(ServerPlayer sp, RandomSource random) {
		Vec3 behind = sp.position().subtract(sp.getLookAngle().multiply(1.5, 0.0, 1.5));
		sound(sp, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), behind, 1.0F, 1.2F + random.nextFloat() * 0.3F);
		return true;
	}

	/** Ash Whisperer: every torch near gutters and smokes. */
	private static boolean gutter(ServerLevel level, ServerPlayer sp) {
		boolean any = false;
		BlockPos from = sp.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-10, -3, -10), from.offset(10, 3, 10))) {
			if ((level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.TorchBlock || level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.LanternBlock)) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 4, 0.05, 0.1, 0.05, 0.01);
				any = true;
			}
		}
		return any;
	}

	/** The Truth: every bell near rings at once, untouched. */
	private static boolean bells(ServerLevel level, ServerPlayer sp) {
		boolean any = false;
		BlockPos from = sp.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-32, -8, -32), from.offset(32, 8, 32))) {
			if (level.getBlockState(p).is(Blocks.BELL) && level.getBlockEntity(p) instanceof BellBlockEntity bell) {
				bell.onHit(net.minecraft.core.Direction.NORTH);
				level.playSound(null, p, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
				level.blockEvent(p, Blocks.BELL, 1, net.minecraft.core.Direction.NORTH.get2DDataValue());
				any = true;
			}
		}
		return any;
	}

	/** The Truth: the lecterns and bookshelves near rustle, as if read. */
	private static boolean pages(ServerLevel level, ServerPlayer sp) {
		boolean any = false;
		BlockPos from = sp.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-10, -3, -10), from.offset(10, 3, 10))) {
			BlockState st = level.getBlockState(p);
			if (st.is(Blocks.LECTERN) || st.is(Blocks.BOOKSHELF)) {
				level.sendParticles(Fx.GLYPH, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.5);
				any = true;
			}
		}
		if (any) {
			level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F);
		}
		return any;
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------------------

	private static void ring(ServerLevel level, ParticleOptions particle, Vec3 centre, double radius, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2.0 * i / n;
			level.sendParticles(particle, centre.x + Math.cos(a) * radius, centre.y, centre.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** A sound only {@code sp} hears, from {@code at}. */
	private static void sound(ServerPlayer sp, SoundEvent sound, Vec3 at, float volume, float pitch) {
		sp.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.AMBIENT, at.x, at.y, at.z, volume, pitch, sp.getRandom().nextLong()));
	}
}
