package com.kgb_8375.sighted.mixin;

import com.kgb_8375.sighted.ext.ChunkLightProviderExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class ChunkLightProviderMixin implements ChunkLightProviderExt {
    private final Long2ObjectMap<NibbleArray> sightedSectionData = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    @Override
    public void sighted_addSectionData(long pos, NibbleArray data) {
        this.sightedSectionData.put(pos, data);
    }

    @Override
    public void sighted_removeSectionData(long pos) {
        this.sightedSectionData.remove(pos);
    }

    @Inject(method = "getDataLayerData", at = @At("HEAD"), cancellable = true)
    private void sighted_getLightSection(SectionPos pos, CallbackInfoReturnable<NibbleArray> ci) {
        NibbleArray data = this.sightedSectionData.get(pos.asLong());
        if (data != null) {
            ci.setReturnValue(data);
        }
    }

    @Inject(method = "getLightValue", at = @At("HEAD"), cancellable = true)
    private void sighted_getLightSection(BlockPos blockPos, CallbackInfoReturnable<Integer> ci) {
        NibbleArray data = this.sightedSectionData.get(SectionPos.of(blockPos).asLong());
        if (data != null) {
            ci.setReturnValue(data.get(
                    SectionPos.sectionRelative(blockPos.getX()),
                    SectionPos.sectionRelative(blockPos.getY()),
                    SectionPos.sectionRelative(blockPos.getZ())
            ));
        }
    }
}
