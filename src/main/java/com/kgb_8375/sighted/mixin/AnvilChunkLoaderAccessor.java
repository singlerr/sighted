package com.kgb_8375.sighted.mixin;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(AnvilChunkLoader.class)
public interface AnvilChunkLoaderAccessor {
    @Accessor
    DataFixer getFixer();

    @Accessor
    Map<ChunkPos, NBTTagCompound> getChunksToSave();

    @Invoker
    void invokeWriteChunkToNBT(Chunk chunkIn, World worldIn, NBTTagCompound compound);

}
