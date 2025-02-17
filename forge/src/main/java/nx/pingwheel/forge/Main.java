package nx.pingwheel.forge;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import nx.pingwheel.common.core.ServerCore;
import nx.pingwheel.common.networking.PingLocationPacketC2S;
import nx.pingwheel.common.networking.PingLocationPacketS2C;
import nx.pingwheel.common.networking.UpdateChannelPacketC2S;

import static nx.pingwheel.common.Global.LOGGER;
import static nx.pingwheel.common.Global.ModVersion;
import static nx.pingwheel.forge.Main.FORGE_ID;

@Mod(FORGE_ID)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Main {

    public static final String FORGE_ID = "pingwheel";

    public static final EventNetworkChannel PING_LOCATION_CHANNEL_C2S = ChannelBuilder.named(PingLocationPacketC2S.ID).optional().eventNetworkChannel();
    public static final EventNetworkChannel PING_LOCATION_CHANNEL_S2C = ChannelBuilder.named(PingLocationPacketS2C.ID).optional().eventNetworkChannel();
    public static final EventNetworkChannel UPDATE_CHANNEL_C2S = ChannelBuilder.named(UpdateChannelPacketC2S.ID).optional().eventNetworkChannel();

    @SuppressWarnings({"java:S1118", "the public constructor is required by forge"})
    public Main() {
        LOGGER.info("Init");

        ModVersion = ModList.get().getModContainerById(FORGE_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("Unknown");

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> Client::new);

        PING_LOCATION_CHANNEL_C2S.addListener((event) -> {
            var ctx = event.getSource();
            var packet = event.getPayload();

            if (packet != null) {
                var packetCopy = new PacketByteBuf(packet.copy());
                ctx.enqueueWork(() -> ServerCore.onPingLocation(ctx.getSender(), packetCopy));
            }

            ctx.setPacketHandled(true);
        });

        UPDATE_CHANNEL_C2S.addListener((event) -> {
            var ctx = event.getSource();
            var packet = event.getPayload();

            if (packet != null) {
                var packetCopy = new PacketByteBuf(packet.copy());
                ctx.enqueueWork(() -> ServerCore.onChannelUpdate(ctx.getSender(), packetCopy));
            }

            ctx.setPacketHandled(true);
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerCore.onPlayerDisconnect((ServerPlayerEntity) event.getEntity());
    }
}
