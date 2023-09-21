package com.kgb_8375.sighted.mixin;

import com.kgb_8375.sighted.ext.ClientChunkProviderExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldClient.class)
public abstract class WorldClientMixin {

    @Final
    @Shadow
    private Minecraft mc;

    @Shadow
    public abstract ChunkProviderClient getChunkProvider();

    @Inject(method = "refreshVisibleChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;floor(D)I", shift = At.Shift.AFTER, ordinal = 1))
    private void updateViewCenter(CallbackInfo ci) {

        int j = MathHelper.floor(this.mc.player.posX / 16.0D);
        int k = MathHelper.floor(this.mc.player.posZ / 16.0D);

        ClientChunkProviderExt mixin = (ClientChunkProviderExt) getChunkProvider();

        if (mixin.sighted_getFakeChunkManager() == null)
            return;

        mixin.getRealChunksTracker().updateCenter(j, k, mixin::saveRealChunk, null);
        mixin.getRealChunksTracker().updateRenderDistance(8192, mixin::saveRealChunk, null);
        mixin.substituteFakeChunksForUnloadedRealOnes();
    }
}
