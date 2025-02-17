package nx.pingwheel.common.core;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nx.pingwheel.common.networking.PingLocationPacketC2S;
import nx.pingwheel.common.networking.PingLocationPacketS2C;
import nx.pingwheel.common.networking.UpdateChannelPacketC2S;

import java.util.HashMap;
import java.util.UUID;

import static nx.pingwheel.common.Global.LOGGER;
import static nx.pingwheel.common.Global.ModVersion;

public class ServerCore {
    private static final HashMap<UUID, String> playerChannels = new HashMap<>();

    private ServerCore() {
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        playerChannels.remove(player.getUuid());
    }

    public static void onChannelUpdate(ServerPlayerEntity player, PacketByteBuf packet) {
        var channelUpdatePacket = UpdateChannelPacketC2S.parse(packet);

        if (channelUpdatePacket.isEmpty()) {
            LOGGER.warn("invalid channel update from " + String.format("%s (%s)", player.getGameProfile().getName(), player.getUuid()));
            player.sendMessage(Text.of("§c[Ping-Wheel] Channel couldn't be updated. Make sure your version matches the server's version: " + ModVersion), false);
            return;
        }

        updatePlayerChannel(player, channelUpdatePacket.get().getChannel());
    }

    public static void onPingLocation(ServerPlayerEntity player, PacketByteBuf packet) {
        var packetCopy = new PacketByteBuf(packet.copy());
        var pingLocationPacket = PingLocationPacketC2S.parse(packet);

        if (pingLocationPacket.isEmpty()) {
            LOGGER.warn("invalid ping location from " + String.format("%s (%s)", player.getGameProfile().getName(), player.getUuid()));
            player.sendMessage(Text.of("§c[Ping-Wheel] Ping couldn't be sent. Make sure your version matches the server's version: " + ModVersion), false);
            return;
        }

        var channel = pingLocationPacket.get().getChannel();

        if (!channel.equals(playerChannels.getOrDefault(player.getUuid(), ""))) {
            updatePlayerChannel(player, channel);
        }

        var packetOut = new PacketByteBuf(Unpooled.buffer())
                .writeIdentifier(PingLocationPacketS2C.ID)
                .writeBytes(packetCopy)
                .writeUuid(player.getUuid());

        for (ServerPlayerEntity p : player.getServerWorld().getPlayers()) {
            if (!channel.equals(playerChannels.getOrDefault(p.getUuid(), ""))) {
                continue;
            }

            p.networkHandler.sendPacket(new CustomPayloadS2CPacket(packetOut));
            packetOut.resetReaderIndex();
        }
    }

    private static void updatePlayerChannel(ServerPlayerEntity player, String channel) {
        if (channel.isEmpty()) {
            playerChannels.remove(player.getUuid());
            LOGGER.info("Channel update: " + String.format("%s -> Global", player.getGameProfile().getName()));
        } else {
            playerChannels.put(player.getUuid(), channel);
            LOGGER.info("Channel update: " + String.format("%s -> \"%s\"", player.getGameProfile().getName(), channel));
        }
    }
}
