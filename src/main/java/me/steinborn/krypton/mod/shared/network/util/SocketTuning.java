package me.steinborn.krypton.mod.shared.network.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.socket.SocketChannelConfig;

/**
 * Makes sure a connection's TCP socket is set up for low latency.
 * <p>
 * Nagle's algorithm holds back small writes while earlier data is still unacknowledged. Combined
 * with the receiver's "delayed ACK" this can stall a request for roughly 40 ms, which is a visible
 * lag spike in a game that sends many small packets. {@code TCP_NODELAY} turns that behaviour off.
 * <p>
 * The game normally enables it already, so in most cases this does nothing. It is applied here as a
 * cheap safety net, because the cost of it being missing is large and the cost of setting it twice
 * is zero.
 */
public final class SocketTuning {
    private SocketTuning() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Enables {@code TCP_NODELAY} if the channel is a TCP socket and it is not already on.
     * Does nothing for other channel types (such as the in-memory channel used by single player),
     * and never throws.
     *
     * @return {@code true} if the channel is a TCP socket that now has {@code TCP_NODELAY} enabled
     */
    public static boolean applyLowLatency(Channel channel) {
        if (channel == null) {
            return false;
        }
        ChannelConfig config = channel.config();
        if (!(config instanceof SocketChannelConfig socketConfig)) {
            return false;
        }
        try {
            if (!socketConfig.isTcpNoDelay()) {
                socketConfig.setTcpNoDelay(true);
            }
            return socketConfig.isTcpNoDelay();
        } catch (ChannelException | UnsupportedOperationException e) {
            // The socket may already be closing, or the transport may not allow changing the option.
            return false;
        }
    }
}
