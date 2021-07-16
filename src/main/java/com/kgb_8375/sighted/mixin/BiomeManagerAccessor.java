package com.kgb_8375.sighted.mixin;

import net.minecraft.world.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeManager.class)
public interface BiomeManagerAccessor {
    @Accessor
    long getBiomeZoomSeed();
}
