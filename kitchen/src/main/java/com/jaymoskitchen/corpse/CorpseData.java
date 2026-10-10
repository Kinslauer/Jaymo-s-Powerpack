package com.jaymoskitchen.corpse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * A carried animal corpse (on the corpse item): how it looks (the mob's saved data, as SNBT text) and the loot
 * it will give up at the butcher block.
 */
public record CorpseData(String look, List<ItemStackTemplate> loot) {
	public static final Codec<CorpseData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("look").forGetter(CorpseData::look),
		ItemStackTemplate.CODEC.listOf().fieldOf("loot").forGetter(CorpseData::loot)
	).apply(instance, CorpseData::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, CorpseData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(Corpses.MAX_LOOK_LENGTH), CorpseData::look,
		ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), CorpseData::loot,
		CorpseData::new);

	public CorpseData {
		loot = List.copyOf(loot);
	}
}
