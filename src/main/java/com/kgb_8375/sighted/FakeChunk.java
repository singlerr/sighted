package com.kgb_8375.sighted;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.palette.UpgradeData;
import net.minecraft.world.EmptyTickList;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

// Fake chunks are of this subclass, primarily so we have an easy way of identifying them.
public class FakeChunk extends Chunk {
    public FakeChunk(World world, ChunkPos pos, BiomeContainer biomes, ChunkSection[] sections) {
        super(world, pos, biomes, UpgradeData.EMPTY, EmptyTickList.empty(), EmptyTickList.empty(), 0L, sections, null);
    }
}
