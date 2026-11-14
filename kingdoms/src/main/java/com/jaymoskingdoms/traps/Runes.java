package com.jaymoskingdoms.traps;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.Reaper;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Runes a player lays as traps. Each is a stone made in the alchemist's crucible (Jaymo's Powers' alchemy: the recipes
 * are data, {@code data/jaymospowers/alchemy/crucible/*_rune.json}, so without Powers there is no way to make one; the
 * Raid-King leaves one of each). Used on the top of a block it is laid there and fed: {@link #MANA} of the layer's mana
 * if they are attuned, else {@link #TOLL} of the Reaper's Toll if they bear it; with neither it won't take. A laid rune
 * is no block: a mark on the ground that shows its sigil to anyone near, holds {@link #CHARGES} charges and goes off
 * under any monster that steps on it, never under a player, a villager or an animal:
 * <ul>
 * <li><b>frost</b>: everything within {@link #REACH} slowed hard and chilled;</li>
 * <li><b>spike</b>: the one that trod on it struck from below and thrown up;</li>
 * <li><b>ember</b>: a burst of fire over everything within {@link #REACH}.</li>
 * </ul>
 * Spent, it crumbles. Its layer takes it up again (as the stone, its charges lost) by using an empty hand on it while
 * sneaking. A layer keeps at most {@link #MOST} laid at once.
 */
public final class Runes {
	/** What laying one costs, in mana or (without) in Toll; how many times it goes off. */
	static final float MANA = 12.0F;
	static final int TOLL = 2, CHARGES = 3;
	/** How near a monster's feet set one off, and how far a frost or ember rune reaches (blocks). */
	static final double TRIGGER = 0.9, REACH = 3.0;
	static final float FROST_DAMAGE = 2.0F, SPIKE_DAMAGE = 9.0F, EMBER_DAMAGE = 5.0F;
	static final int FROST_TICKS = 120, FROST_SLOW = 3, EMBER_BURN_TICKS = 120;
	static final double SPIKE_LIFT = 0.9;
	/** The most one player keeps laid; how often runes are looked at, and shown (ticks); how near a player must be for either (blocks). */
	static final int MOST = 8, EVERY = 4, SHOW_EVERY = 24;
	static final double AWAKE = 32.0;
	/** One goes off again no sooner than this (ticks). */
	static final int REARM = 30;

	public enum Kind {
		FROST("frost_rune", 0xDDF6FF), SPIKE("spike_rune", 0xC9B38A), EMBER("ember_rune", 0xFF8A3A);

		final String id;
		final int color;
		Item item;

		Kind(String id, int color) {
			this.id = id;
			this.color = color;
		}

		public Item item() {
			return this.item;
		}
	}

	/** A laid rune: where, which kind, whose, its charges left, and the game time it last went off. */
	record Rune(long pos, int kind, String owner, int charges, long fired) {
		static final Codec<Rune> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(Rune::pos), Codec.INT.fieldOf("kind").forGetter(Rune::kind),
			Codec.STRING.fieldOf("owner").forGetter(Rune::owner), Codec.INT.fieldOf("charges").forGetter(Rune::charges),
			Codec.LONG.optionalFieldOf("fired", 0L).forGetter(Rune::fired)).apply(i, Rune::new));

		Kind what() {
			return Kind.values()[Math.floorMod(this.kind, Kind.values().length)];
		}
	}

	/** On a level: the runes laid in it. Saved. */
	static final AttachmentType<List<Rune>> RUNES = AttachmentRegistry.create(JaymosKingdoms.id("laid_runes"),
		builder -> builder.persistent(Rune.CODEC.listOf()));

	private Runes() {
	}

	public static void register() {
		for (Kind kind : Kind.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(kind.id));
			kind.item = Registry.register(BuiltInRegistries.ITEM, key, new RuneItem(new Item.Properties().setId(key).stacksTo(16), kind));
		}
		ServerTickEvents.END_SERVER_TICK.register(Runes::tick);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp
			&& sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && lift(sp, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS);
	}

	static final class RuneItem extends Item {
		private final Kind kind;

		RuneItem(Properties properties, Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos().above();
			if (context.getClickedFace() != Direction.UP || !level.getBlockState(at).isAir()
				|| !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
				return InteractionResult.PASS;
			}
			List<Rune> runes = new ArrayList<>(level.getAttachedOrElse(RUNES, List.of()));
			String owner = player.getUUID().toString();
			if (runes.stream().anyMatch(r -> r.pos() == at.asLong()) || runes.stream().filter(r -> r.owner().equals(owner)).count() >= MOST) {
				return InteractionResult.FAIL;
			}
			// fed by mana if they have it to give, else by the Toll
			boolean fed = Mana.attuned(player) && Mana.current(player) >= MANA && Mana.pay(player, MANA, Mana.WARD) || Reaper.spendToll(player, TOLL);
			Vec3 centre = Vec3.atBottomCenterOf(at);
			if (!fed) {
				level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
				return InteractionResult.FAIL;
			}
			runes.add(new Rune(at.asLong(), this.kind.ordinal(), owner, CHARGES, 0L));
			level.setAttached(RUNES, List.copyOf(runes));
			context.getItemInHand().shrink(1);
			Fx.sigil(level, centre.add(0.0, 0.05, 0.0), 0.7F, this.kind.color, SHOW_EVERY + 4);
			level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
			return InteractionResult.SUCCESS;
		}
	}

	/** {@code player}, sneaking with an empty hand, takes up their own rune at {@code at} (the stone back, its charges lost). */
	public static boolean lift(ServerPlayer player, BlockPos at) {
		ServerLevel level = (ServerLevel) player.level();
		List<Rune> runes = level.getAttachedOrElse(RUNES, List.of());
		String owner = player.getUUID().toString();
		for (Rune rune : runes) {
			if ((rune.pos() == at.asLong() || rune.pos() == at.above().asLong()) && rune.owner().equals(owner)) {
				List<Rune> left = new ArrayList<>(runes);
				left.remove(rune);
				level.setAttached(RUNES, List.copyOf(left));
				BlockPos pos = BlockPos.of(rune.pos());
				level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, new ItemStack(rune.what().item())));
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<Rune> runes = level.getAttachedOrElse(RUNES, List.of());
			if (runes.isEmpty()) {
				continue;
			}
			List<Rune> next = null;
			long now = level.getGameTime();
			for (int i = 0; i < runes.size(); i++) {
				Rune rune = runes.get(i);
				BlockPos at = BlockPos.of(rune.pos());
				Vec3 centre = Vec3.atBottomCenterOf(at);
				if (!level.isLoaded(at) || level.getNearestPlayer(centre.x, centre.y, centre.z, AWAKE, false) == null) {
					continue;
				}
				Rune after = rune;
				if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
					// the ground it was cut into is gone
					after = null;
				} else if (now - rune.fired() >= REARM) {
					List<LivingEntity> on = level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(TRIGGER, 0.6, TRIGGER),
						e -> e instanceof Enemy && e.isAlive());
					if (!on.isEmpty()) {
						fire(level, rune.what(), centre, on.get(0), server.getPlayerList().getPlayer(java.util.UUID.fromString(rune.owner())));
						after = rune.charges() <= 1 ? null : new Rune(rune.pos(), rune.kind(), rune.owner(), rune.charges() - 1, now);
						if (after == null) {
							level.playSound(null, at, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 0.8F, 0.8F);
						}
					}
				}
				if (after != null && server.getTickCount() % SHOW_EVERY == 0) {
					Fx.sigil(level, centre.add(0.0, 0.05, 0.0), 0.7F, rune.what().color, SHOW_EVERY + 4);
				}
				if (after != rune) {
					if (next == null) {
						next = new ArrayList<>(runes);
					}
					next.set(i, after);
				}
			}
			if (next != null) {
				next.removeIf(java.util.Objects::isNull);
				level.setAttached(RUNES, List.copyOf(next));
			}
		}
	}

	/** The rune at {@code centre} goes off under {@code victim}; the harm is its layer's doing if they are about. */
	private static void fire(ServerLevel level, Kind kind, Vec3 centre, LivingEntity victim, ServerPlayer layer) {
		var source = layer != null ? level.damageSources().indirectMagic(layer, layer) : level.damageSources().magic();
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(REACH, 1.5, REACH),
			e -> e instanceof Enemy && e.isAlive());
		switch (kind) {
			case FROST -> {
				Fx.ring(level, centre.add(0.0, 0.1, 0.0), (float) REACH, kind.color);
				level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
				for (LivingEntity e : near) {
					e.hurtServer(level, source, FROST_DAMAGE);
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_TICKS, FROST_SLOW));
					e.setTicksFrozen(Math.max(e.getTicksFrozen(), FROST_TICKS));
				}
			}
			case SPIKE -> {
				Fx.beam(level, centre, centre.add(0.0, 2.2, 0.0), 0.25F, kind.color, 8);
				level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.4F, 0.6F);
				if (victim.hurtServer(level, source, SPIKE_DAMAGE)) {
					victim.push(0.0, SPIKE_LIFT, 0.0);
					victim.needsSync = true;
				}
			}
			case EMBER -> {
				Fx.blast(level, centre.add(0.0, 0.5, 0.0), (float) REACH, kind.color);
				level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2F, 0.8F);
				for (LivingEntity e : near) {
					e.hurtServer(level, source, EMBER_DAMAGE);
					e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), EMBER_BURN_TICKS));
				}
			}
		}
	}
}
