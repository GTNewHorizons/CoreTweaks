package makamys.coretweaks.optimization;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.chunk.IChunkProvider;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import makamys.coretweaks.ducks.IChunkProviderClient;

public final class ClientChunkMapTicker {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        WorldClient world = Minecraft.getMinecraft().theWorld;
        if (world != null) {
            IChunkProvider provider = world.getChunkProvider();
            if (provider instanceof ChunkProviderClient) {
                ChunkProviderClient cp = (ChunkProviderClient) provider;
                LongHashMap cm = ((IChunkProviderClient) cp).getChunkMapping();
                if (cm instanceof ClientChunkMap) {
                    Entity player = Minecraft.getMinecraft().renderViewEntity;
                    ((ClientChunkMap) cm).setCenter(((int) player.posX / 16), ((int) player.posZ / 16));
                }
            }
        }
    }
}
