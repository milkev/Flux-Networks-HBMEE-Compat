package sonar.fluxnetworks.register;

import icyllis.modernui.forge.NetworkHandler;
import icyllis.modernui.forge.PacketDispatcher;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.player.Player;
import sonar.fluxnetworks.FluxNetworks;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.device.IFluxDevice;
import sonar.fluxnetworks.api.network.SecurityLevel;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.FluxNetworkData;
import sonar.fluxnetworks.common.device.TileFluxDevice;
import sonar.fluxnetworks.common.util.FluxUtils;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static sonar.fluxnetworks.register.Registration.sNetwork;

/**
 * Network messages, TCP protocol. This class contains common messages, S2C message specs
 * and C2S message handling.
 * <p>
 * Security check is necessary on the server side. However, due to network latency,
 * players cannot be kicked out unless the packet is <em>seriously illegal</em>.
 * <p>
 * Some terms:
 * <ul>
 *   <li><b>Protocol</b>: controls message codec and handling, if some of them changed, then protocol
 *   needs to update.</li>
 *   <li><b>Index</b>: identifies message body, unsigned short, 0-based indexing, must be sequential
 *   for table lookup (switch statement), server-to-client and client-to-server are independent.</li>
 *   <li><b>Token</b>: the container id, unsigned byte, generated by Minecraft, ranged from 1 to 100.
 *   Container menu is required for C/S communication, if the initiator (the server side) is destroyed,
 *   then the token is expired.</li>
 *   <li><b>Result</b>: the return code sent by the server to the client to respond to a client request.
 *   There's a key used to identify the request, generally, it is the same as the message index.
 *   That is, blocking, client is waiting for the server.</li>
 * </ul>
 *
 * @author BloCamLimb
 * @since 7.0
 */
@ParametersAreNonnullByDefault
public class Messages {

    /**
     * Note: Increment this if this class is changed.
     */
    static final String PROTOCOL = "701";

    /**
     * C->S message indices, must be sequential, 0-based indexing
     */
    static final int C2S_DEVICE_BUFFER = 0;
    static final int C2S_SUPER_ADMIN = 1;
    static final int C2S_EDIT_DEVICE = 2;
    static final int C2S_CREATE_NETWORK = 3;

    /**
     * S->C message indices, must be sequential, 0-based indexing
     */
    static final int S2C_DEVICE_BUFFER = 0;
    static final int S2C_RESULT = 1;
    static final int S2C_SUPER_ADMIN = 2;
    static final int S2C_NETWORK_UPDATE = 3;
    static final int S2C_NETWORK_DELETE = 4;

    @Nonnull
    public static PacketDispatcher getDeviceBuffer(TileFluxDevice device, byte id) {
        assert id < 0;
        var buf = NetworkHandler.buffer(S2C_DEVICE_BUFFER);
        buf.writeBlockPos(device.getBlockPos());
        buf.writeByte(id);
        device.writePacket(buf, id);
        return sNetwork.dispatch(buf);
    }

    private static void sendResult(int token, int key, int code, Player player) {
        FriendlyByteBuf buf = NetworkHandler.buffer(S2C_RESULT);
        buf.writeByte(token);
        buf.writeShort(key);
        buf.writeByte(code);
        sNetwork.sendToPlayer(buf, player);
    }

    public static void sendSuperAdmin(Player player, boolean activated) {
        var buf = NetworkHandler.buffer(S2C_SUPER_ADMIN);
        buf.writeBoolean(activated);
        sNetwork.dispatch(buf).sendToPlayer(player);
    }

    @Nonnull
    public static PacketDispatcher getNetworkUpdate(FluxNetwork network, byte type) {
        var buf = NetworkHandler.buffer(S2C_NETWORK_UPDATE);
        buf.writeByte(type);
        buf.writeVarInt(1);
        buf.writeVarInt(network.getID());
        final CompoundTag tag = new CompoundTag();
        network.writeCustomTag(tag, type);
        buf.writeNbt(tag);
        return sNetwork.dispatch(buf);
    }

    @Nonnull
    public static PacketDispatcher getNetworkUpdate(Collection<FluxNetwork> networks, byte type) {
        assert !networks.isEmpty();
        var buf = NetworkHandler.buffer(S2C_NETWORK_UPDATE);
        buf.writeByte(type);
        buf.writeVarInt(networks.size());
        for (var network : networks) {
            buf.writeVarInt(network.getID());
            final CompoundTag tag = new CompoundTag();
            network.writeCustomTag(tag, type);
            buf.writeNbt(tag);
        }
        return sNetwork.dispatch(buf);
    }

    public static void sendNetworkDelete(int id) {
        var buf = NetworkHandler.buffer(S2C_NETWORK_DELETE);
        buf.writeVarInt(id);
        sNetwork.dispatch(buf).sendToAll();
    }

    @Nonnull
    static NetworkHandler.ClientListener msg() {
        return ClientMessages::msg;
    }

    static void msg(short index, FriendlyByteBuf payload, Supplier<ServerPlayer> player) {
        MinecraftServer server = player.get().getLevel().getServer();
        switch (index) {
            case C2S_DEVICE_BUFFER -> onDeviceBuffer(payload, player, server);
            case C2S_SUPER_ADMIN -> onSuperAdmin(payload, player, server);
            case C2S_EDIT_DEVICE -> onEditDevice(payload, player, server);
            case C2S_CREATE_NETWORK -> onCreateNetwork(payload, player, server);
            default -> kick(player.get(), new RuntimeException("Unidentified message index " + index));
        }
    }

    private static void kick(ServerPlayer p, RuntimeException e) {
        // do not kick the player in single-player mode
        if (p.server.isDedicatedServer()) {
            p.connection.disconnect(new TranslatableComponent("multiplayer.disconnect.invalid_packet"));
            FluxNetworks.LOGGER.info("Kicked {} due to protocol attack", p.getGameProfile().getName(), e);
        } else {
            FluxNetworks.LOGGER.debug("Received invalid packet", e);
        }
    }

    private static void consumed(FriendlyByteBuf payload) {
        if (payload.isReadable()) {
            throw new DecoderException();
        }
    }

    private static void onDeviceBuffer(FriendlyByteBuf payload, Supplier<ServerPlayer> player,
                                       BlockableEventLoop<?> looper) {
        looper.execute(() -> {
            ServerPlayer p = player.get();
            try {
                if (p != null && p.level.getBlockEntity(payload.readBlockPos()) instanceof TileFluxDevice e) {
                    if (e.canPlayerAccess(p)) {
                        byte id = payload.readByte();
                        if (id > 0) {
                            e.readPacket(payload, id);
                        } else {
                            throw new IllegalArgumentException();
                        }
                        consumed(payload);
                    }
                }
            } catch (RuntimeException e) {
                kick(p, e);
            }
            payload.release();
        });
        throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
    }

    private static void onSuperAdmin(FriendlyByteBuf payload, Supplier<ServerPlayer> player,
                                     BlockableEventLoop<?> looper) {

    }

    private static void onEditDevice(FriendlyByteBuf payload, Supplier<ServerPlayer> player,
                                     BlockableEventLoop<?> looper) {
        int networkId = payload.readVarInt();
        if (networkId == FluxConstants.INVALID_NETWORK_ID) {
            BlockPos pos = payload.readBlockPos();
            CompoundTag tag = payload.readNbt();
            consumed(payload);
            Objects.requireNonNull(tag);
            looper.execute(() -> {
                ServerPlayer p = player.get();
                if (p == null) return;
                try {
                    if (p.level.getBlockEntity(pos) instanceof TileFluxDevice e) {
                        if (e.canPlayerAccess(p)) {
                            e.readCustomTag(tag, FluxConstants.TYPE_TILE_SETTING);
                        }
                    }
                } catch (RuntimeException e) {
                    kick(p, e);
                }
            });
        } else {
            int size = payload.readVarInt();
            if (size <= 0) {
                throw new IllegalArgumentException();
            }
            List<GlobalPos> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(FluxUtils.readGlobalPos(payload));
            }
            CompoundTag tag = payload.readNbt();
            consumed(payload);
            Objects.requireNonNull(tag);
            looper.execute(() -> {
                ServerPlayer p = player.get();
                if (p == null) return;
                try {
                    FluxNetwork network = FluxNetworkData.getNetwork(networkId);
                    if (network.getPlayerAccess(p).canEdit()) {
                        for (GlobalPos pos : list) {
                            IFluxDevice f = network.getConnection(pos);
                            if (f instanceof TileFluxDevice e) {
                                e.readCustomTag(tag, FluxConstants.TYPE_TILE_SETTING);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    kick(p, e);
                }
            });
        }
    }

    private static void onCreateNetwork(FriendlyByteBuf payload, Supplier<ServerPlayer> player,
                                        BlockableEventLoop<?> looper) {
        // decode
        final int token = payload.readByte();
        final String name = payload.readUtf();
        final int color = payload.readInt();
        final SecurityLevel security = SecurityLevel.get(payload.readByte());
        final String password = security.isEncrypted() ? payload.readUtf() : "";

        // verify
        consumed(payload);
        if (FluxUtils.isBadNetworkName(name)) {
            throw new IllegalArgumentException("Invalid network name: " + name);
        }
        if (security.isEncrypted() && FluxUtils.isBadPassword(password)) {
            throw new IllegalArgumentException("Invalid network password: " + password);
        }

        looper.execute(() -> {
            final ServerPlayer p = player.get();
            if (p == null) {
                return;
            }
            sendResult(token, FluxConstants.KEY_CREATE_NETWORK, FluxConstants.RES_REJECT, p);
            /*if (FluxNetworkManager.createNetwork(p, name, color, security, password) != null) {
                sendResponse(transaction, FluxConstants.RES_SUCCESS, p);
            } else {
                sendResponse(transaction, FluxConstants.RES_REJECT, p);
            }*/
        });
    }
}
