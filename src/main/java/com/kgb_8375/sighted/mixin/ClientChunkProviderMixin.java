package com.kgb_8375.sighted.mixin;

import com.kgb_8375.sighted.*;
import com.kgb_8375.sighted.ext.ClientChunkProviderExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(ChunkProviderClient.class)
public abstract class ClientChunkProviderMixin implements ClientChunkProviderExt {
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    // Tracks which real chunks are visible (whether or not the were actually received), so we can
    // properly unload (i.e. save and replace with fake) them when the server center pos or view distance changes.
    @Unique
    private final VisibleChunksTracker realChunksTracker = new VisibleChunksTracker();
    // List of real chunks saved just before they are unloaded, so we can restore fake ones in their place afterwards
    private final List<Pair<Long, NBTTagCompound>> sightedChunkReplacements = new ArrayList<>();
    protected FakeChunkManager sightedChunkManager;
    @Shadow
    @Final
    private World world;
    @Shadow
    @Final
    private Long2ObjectMap<Chunk> loadedChunks;

    @Override
    public VisibleChunksTracker getRealChunksTracker() {
        return realChunksTracker;
    }

    @Shadow
    @Nullable
    public abstract Chunk getLoadedChunk(int x, int z);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sightedInit(World world, CallbackInfo ci) throws ExecutionException, InterruptedException {

        if (Sighted.getInstance().isEnabled()) {
            sightedChunkManager = new FakeChunkManager((WorldClient) world, (ChunkProviderClient) (Object) this);
            realChunksTracker.update(0, 0, 8192, null, null);
        }
    }

    @Override
    public FakeChunkManager sighted_getFakeChunkManager() {
        return sightedChunkManager;
    }

    @Inject(method = "getLoadedChunk", at = @At(value = "RETURN", shift = At.Shift.BEFORE), cancellable = true)
    private void getSightedChunk(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        // Did we find a live chunk?
        if (loadedChunks.containsKey(ChunkPos.asLong(x, z))) {
            return;
        }

        if (sightedChunkManager == null) {
            return;
        }

        // Otherwise, see if we've got one

        Chunk chunk = sightedChunkManager.getChunk(x, z);

        if (chunk != null) {
            cir.setReturnValue(chunk);
            cir.cancel();
        }

    }
    //Moved to NetHandlerPlayClientMixin
    /*
    @Inject(method = "replaceWithPacketData", at = @At("HEAD"))
    private void sightedUnloadFakeChunk(int x, int z, BiomeContainer biomes, PacketBuffer buf, CompoundNBT tag, int verticalStripBuffer, boolean complete, CallbackInfoReturnable<Chunk> cir) {
        if (sightedChunkManager == null) {
            return;
        }

        // This needs to be called unconditionally because even if there is no chunk loaded at the moment,
        // we might already have one queued which we need to cancel as otherwise it will overwrite the real one later.
        sightedChunkManager.unload(x, z, true);
    }

    */

    @Unique
    @Override
    public void saveRealChunk(long chunkPos) {
        int chunkX = CompatChunkPos.getX(chunkPos);
        int chunkZ = CompatChunkPos.getZ(chunkPos);

        Chunk chunk = getLoadedChunk(chunkX, chunkZ);
        if (chunk == null || chunk instanceof FakeChunk) {
            return;
        }

        FakeChunkStorage storage = sightedChunkManager.getStorage();
        NBTTagCompound tag = storage.serialize(chunk);

        threadPool.submit(() -> storage.save(chunk, chunk.getPos(), world, tag));

        if (sightedChunkManager.shouldBeLoaded(chunkX, chunkZ)) {
            sightedChunkReplacements.add(Pair.of(chunkPos, tag));
        }
    }

    @Unique
    @Override
    public void substituteFakeChunksForUnloadedRealOnes() {

        for (Pair<Long, NBTTagCompound> entry : sightedChunkReplacements) {
            long chunkPos = entry.getKey();
            int chunkX = CompatChunkPos.getX(chunkPos);
            int chunkZ = CompatChunkPos.getZ(chunkPos);
            sightedChunkManager.load(chunkX, chunkZ, entry.getValue(), sightedChunkManager.getStorage());
        }
        sightedChunkReplacements.clear();
    }

    @Inject(method = "unloadChunk", at = @At("HEAD"))
    private void sightedSaveChunk(int chunkX, int chunkZ, CallbackInfo ci) {
        if (sightedChunkManager == null) {
            return;
        }

        saveRealChunk(ChunkPos.asLong(chunkX, chunkZ));
    }
    //Moved to WorldClientMixin
    /*
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

     */

    @Inject(method = {"unloadChunk"}, at = @At("RETURN"))
    private void sightedSubstituteFakeChunksForUnloadedRealOnes(CallbackInfo ci) {
        if (sightedChunkManager == null) {
            return;
        }

        substituteFakeChunksForUnloadedRealOnes();
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