package nx.pingwheel.common.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import nx.pingwheel.common.config.Config;
import nx.pingwheel.common.helper.Draw;
import nx.pingwheel.common.helper.MathUtils;
import nx.pingwheel.common.helper.PingData;
import nx.pingwheel.common.helper.Raycast;
import nx.pingwheel.common.networking.PingLocationPacketC2S;
import nx.pingwheel.common.networking.PingLocationPacketS2C;
import nx.pingwheel.common.sound.DirectionalSoundInstance;
import org.joml.Matrix4f;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static nx.pingwheel.common.ClientGlobal.*;

@Environment(EnvType.CLIENT)
public class ClientCore {
    private static final int TPS = 20;
    private static final Config Config = ConfigHandler.getConfig();
    private static final ArrayList<PingData> pingRepo = new ArrayList<>();
    private static boolean queuePing = false;
    private static ClientWorld lastWorld = null;
    private static int lastPing = 0;
    private static int pingSequence = 0;

    private ClientCore() {
    }

    private static long lastMarkTime = 0;
    private static final long COOLDOWN_DURATION = 3;

    public static void markLocation() {
        long currentTime = Instant.now().toEpochMilli();
        long timeLeft = (lastMarkTime + (COOLDOWN_DURATION * 1000)) - currentTime;

        if (timeLeft <= 0) {
            queuePing = true;
            lastMarkTime = currentTime;
        } else {
            ClientPlayerEntity player = Game.player;
            if (player != null) {
                int secondsLeft = (int) Math.ceil(timeLeft / 1000.0);
                player.sendMessage(Text.translatable("ping-wheel.cooldown", String.format("%d", secondsLeft)).formatted(Formatting.RED), true);
            }
        }
    }

    public static void onPingLocation(PacketByteBuf packet) {
        var pingLocationPacket = PingLocationPacketS2C.parse(packet);

        if (pingLocationPacket.isEmpty() || Game.player == null || Game.world == null) {
            return;
        }

        var pingLocation = pingLocationPacket.get();

        if (!pingLocation.getChannel().equals(Config.getChannel())) {
            return;
        }

        if (Config.getPingDistance() < 2048) {
            var vecToPing = Game.player.getPos().relativize(pingLocation.getPos());

            if (vecToPing.length() > Config.getPingDistance()) {
                return;
            }
        }

        Game.execute(() -> {
            addOrReplacePing(new PingData(
                    pingLocation.getPos(),
                    pingLocation.getEntity(),
                    pingLocation.getAuthor(),
                    pingLocation.getAuthorName(),
                    pingLocation.getSequence(),
                    (int) Game.world.getTime()
            ));

            Game.getSoundManager().play(
                    new DirectionalSoundInstance(
                            PING_SOUND_EVENT,
                            SoundCategory.MASTER,
                            Config.getPingVolume() / 100f,
                            1f,
                            pingLocation.getPos()
                    )
            );
        });
    }

    public static void onRenderWorld(MatrixStack matrixStack, Matrix4f projectionMatrix, float tickDelta) {
        if (Game.world == null) {
            return;
        }

        if (lastWorld != Game.world) {
            lastWorld = Game.world;
            pingRepo.clear();
        }

        var time = (int) Game.world.getTime();

        if (queuePing) {
            if (time - lastPing > Config.getCorrectionPeriod() * TPS) {
                ++pingSequence;
            }

            lastPing = time;
            queuePing = false;
            executePing(tickDelta);
        }

        processPings(matrixStack, projectionMatrix, tickDelta, time);
    }

    public static void onRenderGUI(DrawContext ctx, float tickDelta) {
        if (Game.player == null || pingRepo.isEmpty()) {
            return;
        }

        var m = ctx.getMatrices();

        var wnd = Game.getWindow();
        var screenBounds = new Vec3d(wnd.getScaledWidth(), wnd.getScaledHeight(), 0);
        var safeZoneTopLeft = new Vec2f(Config.getSafeZoneLeft(), Config.getSafeZoneTop());
        var safeZoneBottomRight = new Vec2f((float) screenBounds.x - Config.getSafeZoneRight(), (float) screenBounds.y - Config.getSafeZoneBottom());
        var safeScreenCentre = new Vec2f((safeZoneBottomRight.x - safeZoneTopLeft.x) * 0.5f, (safeZoneBottomRight.y - safeZoneTopLeft.y) * 0.5f);
        var showDirectionIndicator = Config.isDirectionIndicatorVisible();

        m.push();
        m.translate(0f, 0f, -pingRepo.size());

        for (var ping : pingRepo) {
            if (ping.screenPos == null || (ping.screenPos.z <= 0 && !showDirectionIndicator)) {
                continue;
            }

            m.translate(0f, 0f, 1f);

            var pos = ping.screenPos;
            var pingSize = Config.getPingSize() / 100f;
            var pingScale = getDistanceScale(ping.distance) * pingSize * 0.4f;

            var pingDirectionVec = new Vec2f(pos.x - safeZoneTopLeft.x - safeScreenCentre.x, pos.y - safeZoneTopLeft.y - safeScreenCentre.y);
            var behindCamera = false;

            if (pos.z <= 0) {
                behindCamera = true;
                pingDirectionVec = pingDirectionVec.multiply(-1);
            }

            var pingAngle = (float) Math.atan2(pingDirectionVec.y, pingDirectionVec.x);
            var isOffScreen = behindCamera || pos.x < 0 || pos.x > screenBounds.x || pos.y < 0 || pos.y > screenBounds.y;

            if (isOffScreen && showDirectionIndicator) {
                var indicator = MathUtils.calculateAngleRectIntersection(pingAngle, safeZoneTopLeft, safeZoneBottomRight);

                m.push();
                m.translate(indicator.x, indicator.y, 0f);

                m.push();
                m.scale(pingScale, pingScale, 1f);
                var indicatorOffsetX = Math.cos(pingAngle + Math.PI) * 12;
                var indicatorOffsetY = Math.sin(pingAngle + Math.PI) * 12;
                m.translate(indicatorOffsetX, indicatorOffsetY, 0);
                Draw.renderPing(ctx, ping.itemStack, Config.isItemIconVisible());
                m.pop();

                m.push();
                MathUtils.rotateZ(m, pingAngle);
                m.scale(pingSize, pingSize, 1f);

                m.scale(0.25f, 0.25f, 1f);
                m.translate(-5f, 0f, 0f);
                Draw.renderArrow(m, true);
                m.scale(0.9f, 0.9f, 1f);
                Draw.renderArrow(m, false);
                m.pop();

                m.pop();
            }

            if (!behindCamera) {
                m.push();
                m.translate(pos.x, pos.y, 0);
                m.scale(pingScale, pingScale, 1f);
                List<String> texts = List.of(String.format("%.1fm", ping.distance), ping.getAuthorName());
                Draw.renderLabels(ctx, texts, new Vec2f(0, 1.3F));
                Draw.renderPing(ctx, ping.itemStack, Config.isItemIconVisible());

                m.pop();
            }
        }

        m.pop();
    }

    private static void processPings(MatrixStack matrixStack, Matrix4f projectionMatrix, float tickDelta, int time) {
        if (Game.player == null || pingRepo.isEmpty()) {
            return;
        }

        var modelViewMatrix = matrixStack.peek().getPositionMatrix();
        var cameraPos = Game.player.getCameraPosVec(tickDelta);

        for (var ping : pingRepo) {
            if (ping.getUuid() != null) {
                var ent = getEntity(ping.getUuid());

                if (ent != null) {
                    if (ent.getType() == EntityType.ITEM && Config.isItemIconVisible()) {
                        ping.itemStack = ((ItemEntity) ent).getStack().copy();
                    }

                    ping.setPos(ent.getLerpedPos(tickDelta).add(0, ent.getBoundingBox().getLengthY(), 0));
                }
            }

            ping.distance = cameraPos.distanceTo(ping.getPos());
            ping.screenPos = MathUtils.worldToScreen(ping.getPos(), modelViewMatrix, projectionMatrix);
            ping.aliveTime = time - ping.getSpawnTime();
        }

        pingRepo.removeIf(p -> p.aliveTime > Config.getPingDuration() * TPS);
        pingRepo.sort((a, b) -> Double.compare(b.distance, a.distance));
    }

    private static void executePing(float tickDelta) {
        var cameraEntity = Game.cameraEntity;

        if (cameraEntity == null) {
            return;
        }

        var cameraDirection = cameraEntity.getRotationVec(tickDelta);
        var hitResult = Raycast.traceDirectional(
                cameraDirection,
                tickDelta,
                Math.min(Config.getRaycastDistance(), Config.getPingDistance()),
                cameraEntity.isSneaking());

        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }

        UUID uuid = null;

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            uuid = ((EntityHitResult) hitResult).getEntity().getUuid();
        }

        ClientPlayerEntity player = Game.player;
        if (player == null) {
            return;
        }

        new PingLocationPacketC2S(Config.getChannel(), hitResult.getPos(), uuid, pingSequence, player.getGameProfile().getName()).send();
    }

    private static void addOrReplacePing(PingData newPing) {
        int index = -1;

        for (int i = 0; i < pingRepo.size(); i++) {
            var entry = pingRepo.get(i);

            if (entry.getAuthor().equals(newPing.getAuthor()) && entry.getSequence() == newPing.getSequence()) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            pingRepo.set(index, newPing);
        } else {
            pingRepo.add(newPing);
        }
    }

    private static Entity getEntity(UUID uuid) {
        if (Game.world == null) {
            return null;
        }

        for (var entity : Game.world.getEntities()) {
            if (entity.getUuid().equals(uuid)) {
                return entity;
            }
        }

        return null;
    }

    private static float getDistanceScale(double distance) {
        var scale = 2.0 / Math.pow(distance, 0.3);

        return (float) Math.max(1.0, scale);
    }
}
