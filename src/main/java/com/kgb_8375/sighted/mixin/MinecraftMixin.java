package com.kgb_8375.sighted.mixin;

import com.kgb_8375.sighted.FakeChunkManager;
import com.kgb_8375.sighted.FakeChunkStorage;
import com.kgb_8375.sighted.ext.ClientChunkProviderExt;
import net.minecraft.client.AbstractOption;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow private IProfiler profiler;

    @Shadow @Final public GameSettings options;

    @Shadow @Nullable public ClientWorld level;

    @Inject(method = "runTick", at = @At(value = "CONSTANT", args = "stringValue=tick"))
    private void sightedUpdate(CallbackInfo ci) {
        if (level == null) {
            return;
        }

        FakeChunkManager sightedChunkManager = ((ClientChunkProviderExt) level.getChunkSource()).sighted_getFakeChunkManager();
        if (sightedChunkManager == null) {
            return;
        }

        profiler.push("sightedUpdate");

        int maxFps = options.framerateLimit;
        long frameTime = 1_000_000_000 / (maxFps == AbstractOption.FRAMERATE_LIMIT.getMaxValue() ? 120 : maxFps);
        // Arbitrarily choosing 1/4 of frame time as our max budget, that way we're hopefully not noticeable.
        long frameBudget = frameTime / 4;
        long timeLimit = Util.getNanos() + frameBudget;
        sightedChunkManager.update(() -> Util.getNanos() < timeLimit);

        profiler.pop();
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("RETURN"))
    private void sightedClose(CallbackInfo ci) {
        FakeChunkStorage.closeAll();
    }
}
