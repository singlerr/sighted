package com.kgb_8375.sighted;

import com.kgb_8375.sighted.config.ClientConfiguration;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class FakeChunkStorage extends AnvilChunkLoader {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<File, FakeChunkStorage> active = new HashMap<>();
    private static final NibbleArray COMPLETELY_DARK = new NibbleArray();
    private static final NibbleArray COMPLETELY_LIT = new NibbleArray();

    static {
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    COMPLETELY_LIT.set(x, y, z, 15);
                }
            }
        }
    }

    private final World world;
    private final BiomeProvider biomeProvider;

    private FakeChunkStorage(File file, World world, BiomeProvider biomeProvider, DataFixer dataFixer) {
        super(file, dataFixer);
        this.world = world;
        this.biomeProvider = biomeProvider;
    }

    public static FakeChunkStorage getFor(File file, World world, BiomeProvider biomeProvider, DataFixer dataFixer) {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Must be called from main thread.");
        }
        return active.computeIfAbsent(file, f -> new FakeChunkStorage(file, world, biomeProvider, dataFixer));
    }

    public static void closeAll() {
        for (FakeChunkStorage storage : active.values()) {
            storage.flush();
        }
        active.clear();
    }

    public void save(ChunkPos pos, NBTTagCompound chunk) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("DataVersion", 1343); //1.12.2 DataVersion is 1.2.2
        FMLCommonHandler.instance().getDataFixer().writeVersionData(tag);//Forge: data fixer
        tag.setTag("Level", chunk);
        addChunkToPending(pos, tag);
    }

    public @Nullable NBTTagCompound loadTag(ChunkPos pos) throws IOException {
        NBTTagCompound tag = read(pos);
        if (tag == null) {
            return null;
        }
        return tag.getCompoundTag("Level");
    }

    private NBTTagCompound read(ChunkPos pos) throws IOException {
        NBTTagCompound tag = this.chunksToSave.get(pos);

        if (tag == null) {
            DataInputStream dis = RegionFileCache.getChunkInputStream(this.chunkSaveLocation, pos.x, pos.z);

            if (dis == null) {
                return null;
            }

            tag = this.fixer.process(FixTypes.CHUNK, CompressedStreamTools.read(dis));
            dis.close(); // Forge: close stream after use
        }
        return tag;
    }

    public NBTTagCompound serialize(Chunk chunk) {
        NBTTagCompound level = new NBTTagCompound();
        writeChunkToNBT(chunk, world, level);
        return level;
    }

    // Note: This method is called asynchronously, so any methods called must either be verified to be thread safe (and
    //       must be unlikely to loose that thread safety in the presence of third party mods) or must be delayed
    //       by moving them into the returned supplier which is executed on the main thread.
    //       For performance reasons though: The more stuff we can do async, the better.
    public @Nullable
    Supplier<Chunk> deserialize(ChunkPos pos, NBTTagCompound level, World world) {


        ChunkPos chunkPos = new ChunkPos(level.getInteger("xPos"), level.getInteger("zPos"));

        int i = level.getInteger("xPos");
        int j = level.getInteger("zPos");
        Chunk chunk = new FakeChunk(world, chunkPos);

        chunk.setHeightMap(level.getIntArray("HeightMap"));
        chunk.setTerrainPopulated(level.getBoolean("TerrainPopulated"));
        chunk.setLightPopulated(level.getBoolean("LightPopulated"));
        chunk.setInhabitedTime(level.getLong("InhabitedTime"));
        NBTTagList nbttaglist = level.getTagList("Sections", 10);
        int k = 16;
        ExtendedBlockStorage[] aextendedblockstorage = new ExtendedBlockStorage[16];
        boolean flag = world.provider.hasSkyLight();

        for (int l = 0; l < nbttaglist.tagCount(); ++l) {
            NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(l);
            int i1 = nbttagcompound.getByte("Y");
            ExtendedBlockStorage extendedblockstorage = new ExtendedBlockStorage(i1 << 4, flag);
            byte[] abyte = nbttagcompound.getByteArray("Blocks");
            NibbleArray nibblearray = new NibbleArray(nbttagcompound.getByteArray("Data"));
            NibbleArray nibblearray1 = nbttagcompound.hasKey("Add", 7) ? new NibbleArray(nbttagcompound.getByteArray("Add")) : null;
            extendedblockstorage.getData().setDataFromNBT(abyte, nibblearray, nibblearray1);
            extendedblockstorage.setBlockLight(new NibbleArray(nbttagcompound.getByteArray("BlockLight")));

            if (flag) {
                extendedblockstorage.setSkyLight(new NibbleArray(nbttagcompound.getByteArray("SkyLight")));
            }

            extendedblockstorage.recalculateRefCounts();
            aextendedblockstorage[i1] = extendedblockstorage;
        }

        chunk.setStorageArrays(aextendedblockstorage);

        if (level.hasKey("Biomes", 7)) {
            chunk.setBiomeArray(level.getByteArray("Biomes"));
        }

        if (chunk.getCapabilities() != null && level.hasKey("ForgeCaps")) {
            chunk.getCapabilities().deserializeNBT(level.getCompoundTag("ForgeCaps"));
        }


        return () -> {
            if (!ClientConfiguration.noBlockEntities) {
                NBTTagList nbttaglist1 = level.getTagList("Entities", 10);

                for (int j1 = 0; j1 < nbttaglist1.tagCount(); ++j1) {
                    NBTTagCompound nbttagcompound1 = nbttaglist1.getCompoundTagAt(j1);
                    readChunkEntity(nbttagcompound1, world, chunk);
                    chunk.setHasEntities(true);
                }

                NBTTagList nbttaglist2 = level.getTagList("TileEntities", 10);

                for (int k1 = 0; k1 < nbttaglist2.tagCount(); ++k1) {
                    NBTTagCompound nbttagcompound2 = nbttaglist2.getCompoundTagAt(k1);
                    TileEntity tileentity = TileEntity.create(world, nbttagcompound2);

                    if (tileentity != null) {
                        chunk.addTileEntity(tileentity);
                    }
                }

                if (level.hasKey("TileTicks", 9)) {
                    NBTTagList nbttaglist3 = level.getTagList("TileTicks", 10);

                    for (int l1 = 0; l1 < nbttaglist3.tagCount(); ++l1) {
                        NBTTagCompound nbttagcompound3 = nbttaglist3.getCompoundTagAt(l1);
                        Block block;

                        if (nbttagcompound3.hasKey("i", 8)) {
                            block = Block.getBlockFromName(nbttagcompound3.getString("i"));
                        } else {
                            block = Block.getBlockById(nbttagcompound3.getInteger("i"));
                        }

                        world.scheduleBlockUpdate(new BlockPos(nbttagcompound3.getInteger("x"), nbttagcompound3.getInteger("y"), nbttagcompound3.getInteger("z")), block, nbttagcompound3.getInteger("t"), nbttagcompound3.getInteger("p"));
                    }
                }
            }

            return chunk;
        };
    }
}
