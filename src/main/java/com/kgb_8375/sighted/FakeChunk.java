package com.kgb_8375.sighted;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;


// Fake chunks are of this subclass, primarily so we have an easy way of identifying them.
public class FakeChunk extends Chunk {
    public FakeChunk(World world, ChunkPos pos) {
        super(world, pos.x, pos.z);

    }
}
