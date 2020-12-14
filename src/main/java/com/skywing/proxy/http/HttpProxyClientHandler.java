package com.skywing.proxy.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestEncoder;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {

    private static final Pattern HTTP_PATTERN = Pattern.compile("(http://[^/]*)(/.*)");

    private final String id;
    private Channel remoteChannel;

    public HttpProxyClientHandler(String id) {
        this.id = id;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        if (msg instanceof FullHttpRequest) {

            FullHttpRequest request = (FullHttpRequest) msg;
            final Channel clientChannel = ctx.channel();
            clientChannel.config().setAutoRead(false); // connection is ready, enable AutoRead

            if (request.method() == HttpMethod.CONNECT) {
                //remove http handlers
                ctx.pipeline().remove("decoder");
                ctx.pipeline().remove("aggregator");

                // connect to remote host:port
                clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n"
                        .getBytes(StandardCharsets.UTF_8)));

                HostAndPort hostAndPort;
                try {
                    hostAndPort = getHostAndPortFromString(request.uri(), false);
                } catch (URISyntaxException e) {
                    clientChannel.close();
                    return;
                }
                if (hostAndPort.port == -1) hostAndPort.port = 443;

                Bootstrap b = new Bootstrap();
                b.group(clientChannel.eventLoop()) // use the same EventLoop
                        .channel(clientChannel.getClass())
                        .handler(new HttpProxyRemoteHandler(id, clientChannel));
                ChannelFuture f = b.connect(hostAndPort.host, hostAndPort.port);
                remoteChannel = f.channel();

                f.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            log.info("Open Success: {} {} {}, {} to {}", id, request.method(), request.uri(), clientChannel, remoteChannel);
                            clientChannel.config().setAutoRead(true); // connection is ready, enable AutoRead
                        } else {
                            log.warn("Open Failed: {} {} {}, {} to {}, Error: {}", id, request.method(), request.uri(), clientChannel, remoteChannel, future.cause().toString());
                            clientChannel.close();
                        }
                    }
                });
            } else {
                //normal http proxy
                Matcher matcher = HTTP_PATTERN.matcher(request.uri());
                if (matcher.find() && matcher.groupCount() == 2) {

                    HostAndPort hostAndPort;
                    try {
                        hostAndPort = getHostAndPortFromString(matcher.group(1), true);
                    } catch (URISyntaxException e) {
                        clientChannel.close();
                        return;
                    }
                    if (hostAndPort.port == -1) hostAndPort.port = 80;

                    String path = matcher.group(2);

                    Bootstrap b = new Bootstrap();
                    b.group(clientChannel.eventLoop()) // use the same EventLoop
                            .channel(clientChannel.getClass())
                            .handler(new HttpProxyRemoteHandler(id, clientChannel));
                    ChannelFuture f = b.connect(hostAndPort.host, hostAndPort.port);
                    remoteChannel = f.channel();
                    remoteChannel.pipeline().addFirst(new HttpRequestEncoder());

                    f.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                log.info("Open Success: {} {} {}, {} to {}", id, request.method(), request.uri(), clientChannel, remoteChannel);
                                clientChannel.config().setAutoRead(true); // connection is ready, enable AutoRead
                                request.setUri(path);
                                remoteChannel.writeAndFlush(request);
                            } else {
                                log.warn("Open Failed: {} {} {}, {} to {}, Error: {}", id, request.method(), request.uri(), clientChannel, remoteChannel, future.cause().toString());
                                clientChannel.close();
                            }
                        }
                    });
                } else {
                    clientChannel.close();
                }
            }
        } else {
            remoteChannel.writeAndFlush(msg); // just forward
            return;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(remoteChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception: {} {} to {}, Error: {}", id, ctx.channel(), remoteChannel, cause.toString());
        flushAndClose(ctx.channel());
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private HostAndPort getHostAndPortFromString(String strUri, boolean hasScheme) throws URISyntaxException {
        if (!hasScheme) {
            strUri = "tcp://" + strUri;
        }
        URI uri = new URI(strUri);
        return new HostAndPort(uri.getHost(), uri.getPort());
    }

    static class HostAndPort {
        String host;
        int port;

        public HostAndPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
