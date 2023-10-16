package com.kgb_8375.sighted.mixin;

import com.kgb_8375.sighted.Sighted;
import com.kgb_8375.sighted.ext.ClientChunkProviderExt;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketJoinGame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

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

    @Inject(method = "handleJoinGame", at = @At(value = "TAIL"))
    private void notifyMessage(SPacketJoinGame packetIn, CallbackInfo ci) {
        Sighted.enableStatusMessage();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Sighted.disableStatusMessage();
            }
        }, TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES));
    }
}
