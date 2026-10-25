package com.jaymoskingdoms.whaling;

import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Ship;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.206) What a great whale leaves: its carcass, floating belly up, with whatever irons were in it. You can stand on
 * it, tow it on harpoon lines (lodge fresh ones in it too), and flense it where it floats: use an axe, a sword or a
 * knife on it and each stroke ({@link #STROKE} ticks apart) cuts away blubber, meat or bone, {@link #FLESH} strokes in
 * all, one of them (now and then) turning up ambergris. Stripped, it sinks, and so does one left to rot
 * ({@link #ROT} ticks).
 * <p>
 * Stage 2: a whale is too big to flense from the water or a little boat. Only {@link #LOOSE} strokes can be cut from it
 * so; for the rest it must be cut in: lashed alongside a big ship (one of at least {@link #BIG_SHIP} blocks) by a
 * harpoon line made fast to her, and within {@link #ALONGSIDE} blocks of her side. Its blubber is rendered down in a
 * {@link TryworksBlock tryworks}.
 * <p>
 * Stage 4: sharks come to the blood and tear at it ({@link Sharks}); the white whale's carcass always holds ambergris,
 * {@link #WHITE_AMBERGRIS} lumps of it.
 */
public final class WhaleCarcass extends Entity {
	/** How tall it floats (blocks); its width is the whale's. */
	static final float HEIGHT = 2.2F;
	/** Strokes of the flensing knife in one whale, and how far apart (ticks). */
	static final int FLESH = 48, STROKE = 24;
	/** What a stroke cuts away: the chances of meat and of bone (the rest is blubber), and how many of each. */
	static final float MEAT = 0.3F, BONE = 0.1F;
	static final int BLUBBER_MIN = 2, BLUBBER_MAX = 4, MEAT_MIN = 1, MEAT_MAX = 3;
	/** The chance a whale carries ambergris, and the bones left at the end. */
	static final float AMBERGRIS = 0.12F;
	static final int LAST_BONES = 6;
	/** (stage 4) The lumps of ambergris the white whale holds. */
	static final int WHITE_AMBERGRIS = 3;
	/** It sinks after this long (ticks; three days) or once stripped, and goes down this fast (blocks a tick) for this long. */
	static final int ROT = 72000, SINKING = 120;
	static final double SINK = 0.03;
	/** How heavy it is to tow (a boat being 1), and how it rides: its keel this far under the surface. */
	static final double HEFT = 4.0, DRAFT = 1.2;
	/** Strokes that can be cut with it loose in the water, the blocks a ship needs to cut in the rest, and how near her side it must lie. */
	static final int LOOSE = 16, BIG_SHIP = 600;
	static final double ALONGSIDE = 6.0;

	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(WhaleCarcass.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LEFT = SynchedEntityData.defineId(WhaleCarcass.class, EntityDataSerializers.INT);

	private int age, sinking, amber = -1;
	private Vec3 towed = Vec3.ZERO;

	public WhaleCarcass(EntityType<? extends WhaleCarcass> type, Level level) {
		super(type, level);
	}

	static WhaleCarcass of(ServerLevel level, GreatWhale whale) {
		WhaleCarcass carcass = new WhaleCarcass(Whaling.WHALE_CARCASS, level);
		carcass.snapTo(whale.getX(), whale.getY(), whale.getZ(), whale.getYRot(), 0.0F);
		carcass.entityData.set(VARIANT, whale.variant());
		// (the white whale always carries it)
		carcass.amber = whale.white() || carcass.random.nextFloat() < AMBERGRIS ? carcass.random.nextInt(FLESH) : -1;
		carcass.setDeltaMovement(whale.getDeltaMovement().scale(0.5));
		return carcass;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(VARIANT, 0);
		builder.define(LEFT, FLESH);
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	/** How much of it is left to flense, from 1 (whole) to 0 (bones). */
	public float left() {
		return this.entityData.get(LEFT) / (float)FLESH;
	}

	/** Pulls it (a harpoon line, taut), taken up at its next move. */
	void tow(Vec3 pull) {
		this.towed = this.towed.add(pull.scale(1.0 / HEFT));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 v = this.getDeltaMovement();
		if (this.sinking > 0) {
			if (--this.sinking <= 0) {
				this.discard();
				return;
			}
			v = new Vec3(v.x * 0.8, -SINK, v.z * 0.8);
		} else if (this.isInWater()) {
			// it rides at the surface, its keel DRAFT under
			double rise = Mth.clamp((level.getSeaLevel() - DRAFT - this.getY()) * 0.1, -0.05, 0.05);
			v = new Vec3(v.x * 0.9, v.y * 0.6 + rise, v.z * 0.9);
		} else {
			v = new Vec3(v.x * 0.8, v.y - 0.08, v.z * 0.8);
		}
		v = v.add(this.towed);
		if (this.towed.horizontalDistanceSqr() > 1.0E-5) {
			// towed, it swings round to follow
			float toward = GreatWhale.yawTo(this.towed);
			float behind = Mth.wrapDegrees(toward - this.getYRot()) > 90.0F || Mth.wrapDegrees(toward - this.getYRot()) < -90.0F ? toward + 180.0F : toward;
			this.setYRot(Mth.approachDegrees(this.getYRot(), behind, 0.6F));
		}
		this.towed = Vec3.ZERO;
		this.setDeltaMovement(v);
		this.move(MoverType.SELF, v);
		if (++this.age > ROT && this.sinking == 0) {
			this.sink(level);
		}
		if (this.sinking == 0) {
			Sharks.tick(level, this, this.age);
		}
		if (this.age % 100 == 0) {
			level.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + 0.2, this.getZ(), 4, GreatWhale.WIDTH * 0.4, 0.1,
				GreatWhale.WIDTH * 0.4, 0.0);
		}
	}

	/** It goes down (stripped, or rotten), leaving the last of its bones floating. */
	private void sink(ServerLevel level) {
		this.sinking = SINKING;
		if (this.entityData.get(LEFT) <= 0) {
			this.spawnAtLocation(level, new ItemStack(Whaling.WHALE_BONE, LAST_BONES));
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.NEUTRAL, 1.5F, 0.6F);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		ItemStack held = player.getItemInHand(hand);
		if (!flenses(held) || this.sinking > 0 || this.entityData.get(LEFT) <= 0) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(held)) {
			return InteractionResult.CONSUME;
		}
		if (!(this.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		if (FLESH - this.entityData.get(LEFT) >= LOOSE && !this.alongside(level)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.whale_cut_in").withStyle(ChatFormatting.GRAY));
			return InteractionResult.CONSUME;
		}
		int left = this.entityData.get(LEFT) - 1;
		this.entityData.set(LEFT, left);
		player.getCooldowns().addCooldown(held, STROKE);
		held.hurtAndBreak(1, player, hand);
		ItemStack cut = this.cut(left);
		Vec3 at = location.add(this.position());
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, cut.getItem()), at.x, at.y + 0.3, at.z, 12, 0.3, 0.2, 0.3, 0.08);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.5F + this.random.nextFloat() * 0.2F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.8F, 0.6F);
		if (!player.addItem(cut)) {
			player.spawnAtLocation(level, cut);
		}
		if (left <= 0) {
			this.sink(level);
		}
		return InteractionResult.SUCCESS;
	}

	/** Lashed alongside a big ship: a harpoon in it made fast to a ship of {@link #BIG_SHIP} blocks or more, and it lying by her side. */
	boolean alongside(ServerLevel level) {
		for (Harpoon h : Harpoon.in(level, this)) {
			if (h.anchor() instanceof Ship ship) {
				Hull hull = ship.hull();
				if (hull == null || hull.pieces.size() < BIG_SHIP) {
					continue;
				}
				Vec3 local = ship.toLocal(this.position());
				double reach = ALONGSIDE + this.getBbWidth() * 0.5;
				if (local.x > hull.minX - 0.5 - reach && local.x < hull.maxX + 0.5 + reach && local.z > hull.minZ - 0.5 - reach
					&& local.z < hull.maxZ + 0.5 + reach) {
					return true;
				}
			}
		}
		return false;
	}

	/** (stage 4) A shark tore a stroke's worth away (false if there was nothing left to take). */
	boolean bitten(ServerLevel level) {
		int left = this.entityData.get(LEFT);
		if (left <= 0 || this.sinking > 0) {
			return false;
		}
		this.entityData.set(LEFT, left - 1);
		if (left - 1 <= 0) {
			this.sink(level);
		}
		return true;
	}

	/** What one stroke cuts away (the stroke that finds the ambergris, if it has any, gives that as well). */
	private ItemStack cut(int stroke) {
		if (stroke == this.amber && this.level() instanceof ServerLevel level) {
			this.spawnAtLocation(level, new ItemStack(Whaling.AMBERGRIS, this.variant() == GreatWhale.WHITE ? WHITE_AMBERGRIS : 1));
		}
		float roll = this.random.nextFloat();
		if (roll < BONE) {
			return new ItemStack(Whaling.WHALE_BONE);
		}
		if (roll < BONE + MEAT) {
			return new ItemStack(Whaling.WHALE_MEAT, Mth.nextInt(this.random, MEAT_MIN, MEAT_MAX));
		}
		return new ItemStack(Whaling.WHALE_BLUBBER, Mth.nextInt(this.random, BLUBBER_MIN, BLUBBER_MAX));
	}

	/** What a whale can be flensed with: axes, swords and knives. */
	static boolean flenses(ItemStack held) {
		if (held.is(ItemTags.AXES) || held.is(ItemTags.SWORDS)) {
			return true;
		}
		Item item = held.getItem();
		String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
		return path.contains("knife") || path.contains("cleaver") || item == Items.SHEARS;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 192.0 * 192.0;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("Variant", this.variant());
		output.putInt("Left", this.entityData.get(LEFT));
		output.putInt("Age", this.age);
		output.putInt("Amber", this.amber);
		output.putInt("Sinking", this.sinking);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(VARIANT, Math.floorMod(input.getIntOr("Variant", 0), GreatWhale.VARIANTS));
		this.entityData.set(LEFT, Mth.clamp(input.getIntOr("Left", FLESH), 0, FLESH));
		this.age = input.getIntOr("Age", 0);
		this.amber = input.getIntOr("Amber", -1);
		this.sinking = input.getIntOr("Sinking", 0);
	}
}
