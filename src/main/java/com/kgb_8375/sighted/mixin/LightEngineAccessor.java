package com.kgb_8375.sighted.mixin;

import net.minecraft.world.lighting.LightEngine;
import net.minecraft.world.lighting.WorldLightManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

@Mixin(WorldLightManager.class)
public interface LightEngineAccessor {
    @Accessor
    @Nullable
    LightEngine<?, ?> getBlockEngine();
    @Accessor
    @Nullable
    LightEngine<?, ?> getSkyEngine();
}
