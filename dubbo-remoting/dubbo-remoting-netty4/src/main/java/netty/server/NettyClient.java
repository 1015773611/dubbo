package netty.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.remoting.Constants.HEARTBEAT_CHECK_TICK;
import static org.apache.dubbo.remoting.Constants.LEAST_HEARTBEAT_DURATION;

/**
 * @author geyu
 * @date 2021/1/28 18:10
 */
public class NettyClient extends AbstractClient {
    private Bootstrap bootstrap;
    private volatile InnerChannel channel;
    private ReconnectTimerTask reconnectTimerTask;

    public NettyClient(URL url, ChannelHandler channelHandler) throws RemotingException {
        super(url, ChannelHandlers.wrap(channelHandler));
    }

    @Override
    protected void doOpen() {
        bootstrap = new Bootstrap();
        int coreSize = Runtime.getRuntime().availableProcessors();
        EventLoopGroup workerGroup = NettyEventLoopFactory.eventLoopGroup(coreSize, "NettyClientWorker");
        bootstrap.group(workerGroup)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(3000, getConnectTimeout()))
                .channel(NettyEventLoopFactory.socketChannelClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        NettyCodecAdapter nettyCodecAdapter = new NettyCodecAdapter(getCodec());
                        pipeline.addLast("decoder", nettyCodecAdapter.getInternalDecoder());
                        pipeline.addLast("encoder", nettyCodecAdapter.getInternalEncoder());
                        pipeline.addLast("idle-state", new IdleStateHandler(getIdleTimeout(), 0, 0, TimeUnit.MILLISECONDS));
                        pipeline.addLast("handler", new NettyClientHandler(getChannelHandler(), getUrl()));
                    }
                });
    }

    protected void doConnect() throws RemotingException {
        ChannelFuture channelFuture = bootstrap.connect(getServerAddress());
        boolean ret = channelFuture.awaitUninterruptibly(getConnectTimeout());
        if (ret && channelFuture.isSuccess()) {
            InnerChannel oldChannel = channel;
            if (oldChannel != null) {
                oldChannel.close();
            }
            channel = NettyChannel.getOrAddChannel(channelFuture.channel(), getUrl());
        } else if (channelFuture.cause() != null) {
            throw new RemotingException();
        } else { // 超时
            throw new RemotingException();
        }
    }

    @Override
    protected void doStartTimer() {
        startReconnectTask();
        startHeartBeatTask();

    }

    private void startHeartBeatTask() {
    }

    private void startReconnectTask() {
        if (shouldReconnect(getUrl())) {
            AbstractTimerTask.ChannelProvider channelProvider = () -> Collections.singletonList(getChannel());
            int idleTimeout = UrlUtils.getIdleTimeout(getUrl());
            long heartbeatTimeoutTick = calculateLeastDuration(idleTimeout);
            this.reconnectTimerTask = new ReconnectTimerTask(channelProvider, heartbeatTimeoutTick, idleTimeout,this);
        }
    }

    private boolean shouldReconnect(URL url) {
        return url.getParameter(Constants.RECONNECT_KEY, Constants.DEFAULT_RECONNECT);
    }


    private long calculateLeastDuration(int time) {
        if (time / HEARTBEAT_CHECK_TICK <= 0) {
            return LEAST_HEARTBEAT_DURATION;
        } else {
            return time / HEARTBEAT_CHECK_TICK;
        }
    }

    @Override
    protected void doClose() {
        reconnectTimerTask.stop();
    }

    @Override
    public InnerChannel getChannel() {
        return channel;
    }
}
