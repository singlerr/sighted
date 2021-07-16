package com.kgb_8375.sighted.mixin;

import com.kgb_8375.sighted.*;
import com.kgb_8375.sighted.ext.ClientChunkProviderExt;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.lighting.WorldLightManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(ClientChunkProvider.class)
public abstract class ClientChunkProviderMixin implements ClientChunkProviderExt {

    @Shadow @Final private Chunk emptyChunk;

    @Shadow @Nullable public abstract Chunk getChunk(int i, int j, ChunkStatus chunkStatus, boolean bl);
    @Shadow public abstract WorldLightManager getLightEngine();
    @Shadow private static int calculateStorageRange(int loadDistance) { throw new AssertionError(); }

    protected FakeChunkManager sightedChunkManager;

    // Tracks which real chunks are visible (whether or not the were actually received), so we can
    // properly unload (i.e. save and replace with fake) them when the server center pos or view distance changes.
    private final VisibleChunksTracker realChunksTracker = new VisibleChunksTracker();

    // List of real chunks saved just before they are unloaded, so we can restore fake ones in their place afterwards
    private final List<Pair<Long, CompoundNBT>> sightedChunkReplacements = new ArrayList<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sightedInit(ClientWorld world, int loadDistance, CallbackInfo ci) {
        if (Sighted.getInstance().isEnabled()) {
            sightedChunkManager = new FakeChunkManager(world, (ClientChunkProvider) (Object) this);
            realChunksTracker.update(0, 0, calculateStorageRange(loadDistance), null, null);
        }
    }

    @Override
    public FakeChunkManager sighted_getFakeChunkManager() {
        return sightedChunkManager;
    }

    @Inject(method = "getChunk", at = @At("RETURN"), cancellable = true)
    private void getSightedChunk(int x, int z, ChunkStatus chunkStatus, boolean orEmpty, CallbackInfoReturnable<Chunk> ci) {
        // Did we find a live chunk?
        if (ci.getReturnValue() != (orEmpty ? emptyChunk : null)) {
            return;
        }

        if (sightedChunkManager == null) {
            return;
        }

        // Otherwise, see if we've got one
        Chunk chunk = sightedChunkManager.getChunk(x, z);
        if (chunk != null) {
            ci.setReturnValue(chunk);
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"))
    private void sightedUnloadFakeChunk(int x, int z, BiomeContainer biomes, PacketBuffer buf, CompoundNBT tag, int verticalStripBuffer, boolean complete, CallbackInfoReturnable<Chunk> cir) {
        if (sightedChunkManager == null) {
            return;
        }

        // This needs to be called unconditionally because even if there is no chunk loaded at the moment,
        // we might already have one queued which we need to cancel as otherwise it will overwrite the real one later.
        sightedChunkManager.unload(x, z, true);
    }

    @Unique
    private void saveRealChunk(long chunkPos) {
        int chunkX = ChunkPos.getX(chunkPos);
        int chunkZ = ChunkPos.getZ(chunkPos);

        Chunk chunk = getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null || chunk instanceof FakeChunk) {
            return;
        }

        FakeChunkStorage storage = sightedChunkManager.getStorage();
        CompoundNBT tag = storage.serialize(chunk, getLightEngine());
        storage.save(chunk.getPos(), tag);

        if (sightedChunkManager.shouldBeLoaded(chunkX, chunkZ)) {
            sightedChunkReplacements.add(Pair.of(chunkPos, tag));
        }
    }

    @Unique
    private void substituteFakeChunksForUnloadedRealOnes() {
        for (Pair<Long, CompoundNBT> entry : sightedChunkReplacements) {
            long chunkPos = entry.getFirst();
            int chunkX = ChunkPos.getX(chunkPos);
            int chunkZ = ChunkPos.getZ(chunkPos);
            sightedChunkManager.load(chunkX, chunkZ, entry.getSecond(), sightedChunkManager.getStorage());
        }
        sightedChunkReplacements.clear();
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void sightedSaveChunk(int chunkX, int chunkZ, CallbackInfo ci) {
        if (sightedChunkManager == null) {
            return;
        }

        saveRealChunk(ChunkPos.asLong(chunkX, chunkZ));
    }

    @Inject(method = "updateViewCenter", at = @At("HEAD"))
    private void sightedSaveChunksBeforeMove(int x, int z, CallbackInfo ci) {
        if (sightedChunkManager == null) {
            return;
        }

        realChunksTracker.updateCenter(x, z, this::saveRealChunk, null);
    }

    @Inject(method = "updateViewRadius", at = @At("HEAD"))
    private void sightedSaveChunksBeforeResize(int loadDistance, CallbackInfo ci) {
        if (sightedChunkManager == null) {
            return;
        }

        realChunksTracker.updateRenderDistance(calculateStorageRange(loadDistance), this::saveRealChunk, null);
    }

    @Inject(method = { "drop", "updateViewCenter", "updateViewRadius" }, at = @At("RETURN"))
    private void sightedSubstituteFakeChunksForUnloadedRealOnes(CallbackInfo ci) {
        if (sightedChunkManager == null) {
            return;
        }

        substituteFakeChunksForUnloadedRealOnes();
    }

    @Inject(method = "gatherStats", at = @At("RETURN"), cancellable = true)
    private void sightedDebugString(CallbackInfoReturnable<String> cir) {
        if (sightedChunkManager == null) {
            return;
        }

        cir.setReturnValue(cir.getReturnValue() + " " + sightedChunkManager.getDebugString());
    }

    @Override
    public void sighted_onFakeChunkAdded(int x, int z) {
        // Vanilla polls for chunks each frame, this is only of interest for Sodium (see SodiumChunkManagerMixin)
    }

    @Override
    public void sighted_onFakeChunkRemoved(int x, int z) {
        // Vanilla polls for chunks each frame, this is only of interest for Sodium (see SodiumChunkManagerMixin)
    }
}