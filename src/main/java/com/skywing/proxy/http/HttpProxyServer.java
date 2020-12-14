package com.skywing.proxy.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class HttpProxyServer {

    private final String host;

    private final int port;

    public void start() {
        log.info("HttpProxyServer listened on: {}:{}", host, port);
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler())
                    .childHandler(new HttpProxyChannelInitializer())
                    .bind(host, port).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("InterruptedException " + e, e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
