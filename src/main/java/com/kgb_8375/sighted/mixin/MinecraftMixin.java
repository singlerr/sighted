package com.kgb_8375.sighted.mixin;

import com.kgb_8375.sighted.FakeChunkManager;
import com.kgb_8375.sighted.FakeChunkStorage;
import com.kgb_8375.sighted.ext.ClientChunkProviderExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Final
    public GameSettings gameSettings;
    @Shadow
    @Nullable
    public WorldClient world;
    @Shadow
    private Profiler profiler;

    @Inject(method = "runGameLoop", at = @At(value = "CONSTANT", args = "stringValue=tick"))
    private void sightedUpdate(CallbackInfo ci) {
        if (world == null) {
            return;
        }

        FakeChunkManager sightedChunkManager = ((ClientChunkProviderExt) world.getChunkProvider()).sighted_getFakeChunkManager();
        if (sightedChunkManager == null) {
            return;
        }

        profiler.startSection("sightedUpdate");

        int maxFps = gameSettings.limitFramerate;
        long frameTime = 1_000_000_000 / (maxFps == 260 ? 120 : maxFps);
        // Arbitrarily choosing 1/4 of frame time as our max budget, that way we're hopefully not noticeable.
        long frameBudget = frameTime / 4;
        long timeLimit = System.nanoTime() + frameBudget;
        sightedChunkManager.update(() -> System.nanoTime() < timeLimit);
        profiler.endSection();
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getConnection()Lnet/minecraft/client/network/NetHandlerPlayClient;", shift = At.Shift.AFTER))
    private void sightedClose(CallbackInfo ci) {
        FakeChunkStorage.closeAll();
    }
}
