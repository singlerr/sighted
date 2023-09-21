package com.kgb_8375.sighted.mixin;

import com.kgb_8375.sighted.ext.ClientChunkProviderExt;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class NetHandlerPlayerClientMixin {

    @Shadow
    private WorldClient world;

    @Inject(method = "handleChunkData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;read(Lnet/minecraft/network/PacketBuffer;IZ)V", shift = At.Shift.BEFORE))
    private void sightedUnloadFakeChunk(SPacketChunkData packetIn, CallbackInfo ci) {
        ClientChunkProviderExt ext = (ClientChunkProviderExt) world.getChunkProvider();
        if (ext.sighted_getFakeChunkManager() == null)
            return;
        ext.sighted_getFakeChunkManager().unload(packetIn.getChunkX(), packetIn.getChunkZ(), true);
    }
}
