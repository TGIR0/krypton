package me.steinborn.krypton.mod.shared.network.util;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocketTuningTest {
    private interface WithConnection {
        void run(Channel client) throws Exception;
    }

    /** Opens a real TCP connection on the loopback interface, with Nagle's algorithm left ON. */
    private static void withLoopbackClient(boolean tcpNoDelay, WithConnection body) throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            Channel server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                        }
                    }).bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
            int port = ((InetSocketAddress) server.localAddress()).getPort();

            Channel client = new Bootstrap().group(group).channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, tcpNoDelay)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                        }
                    }).connect(new InetSocketAddress("127.0.0.1", port)).sync().channel();
            try {
                body.run(client);
            } finally {
                client.close().sync();
                server.close().sync();
            }
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    @Test
    void turnsNagleOffOnARealTcpSocket() throws Exception {
        withLoopbackClient(false, client -> {
            SocketChannelConfig config = (SocketChannelConfig) client.config();
            assertFalse(config.isTcpNoDelay(), "precondition: Nagle's algorithm is on");

            assertTrue(SocketTuning.applyLowLatency(client));
            assertTrue(config.isTcpNoDelay());
        });
    }

    @Test
    void leavesAnAlreadyTunedSocketAlone() throws Exception {
        withLoopbackClient(true, client -> {
            assertTrue(SocketTuning.applyLowLatency(client));
            assertTrue(SocketTuning.applyLowLatency(client), "applying twice is harmless");
            assertTrue(((SocketChannelConfig) client.config()).isTcpNoDelay());
        });
    }

    @Test
    void ignoresChannelsThatAreNotTcpSockets() {
        // Single player uses an in-memory channel; it has no TCP socket to tune.
        assertFalse(SocketTuning.applyLowLatency(new EmbeddedChannel()));
    }

    @Test
    void ignoresNull() {
        assertFalse(SocketTuning.applyLowLatency(null));
    }

    @Test
    void neverThrowsOnAClosedSocket() throws Exception {
        withLoopbackClient(false, client -> {
            client.close().sync();
            SocketTuning.applyLowLatency(client); // must not throw, whatever it returns
        });
    }
}
