package com.skywing.proxy.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.logging.LoggingHandler;

import java.util.concurrent.atomic.AtomicLong;

public class HttpProxyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private AtomicLong taskCounter = new AtomicLong();

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline()
                .addLast("decoder", new HttpRequestDecoder())
                .addLast("aggregator", new HttpObjectAggregator(10 * 1024 * 1024))
                .addLast(new LoggingHandler())
                .addLast(new HttpProxyClientHandler("http-proxy-" + taskCounter.getAndIncrement()));
    }
}
