package com.jaymoskingdoms;

import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.310, Andy: "they should look like smaller versions of the user, not zombies") A Bio-built's bud: a zombie's
 * brain (so {@link Thralls} leads it as before) in a small copy of its maker, drawn by the client's
 * {@code BioBudRenderer} in the maker's own skin ({@link #MAKER}) with the third form's plates. The sun doesn't burn it,
 * water doesn't turn it, it never makes zombie villagers, it groans like nobody and it drops nothing.
 */
public final class BioBud extends Zombie {
	/** Its size beside its maker. */
	static final double SIZE = 0.6;

	/** Who it was budded from (their skin), synced to everyone. Saved. */
	public static final AttachmentType<UUID> MAKER = AttachmentRegistry.create(JaymosKingdoms.id("bio_bud_maker"),
		builder -> builder.persistent(UUIDUtil.CODEC).syncWith(UUIDUtil.STREAM_CODEC, AttachmentSyncPredicate.all()));

	public static EntityType<BioBud> TYPE;

	public BioBud(EntityType<? extends BioBud> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("bio_copy"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<BioBud>of(BioBud::new, MobCategory.MONSTER)
			.noLootTable()
			.sized(0.6F, 1.95F)
			.eyeHeight(1.74F)
			.clientTrackingRange(8)
			.build(key));
		FabricDefaultAttributeRegistry.register(TYPE, Zombie.createAttributes());
	}

	/** The maker of {@code entity}, if it's a bud. */
	public static @Nullable UUID maker(net.minecraft.world.entity.Entity entity) {
		return entity.getAttached(MAKER);
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	public boolean convertVillagerToZombieVillager(ServerLevel level, Villager villager) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_BLOCK_BREAK;
	}
}
