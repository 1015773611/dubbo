package my.server;

import my.rpc.DefaultCodec;

import java.net.InetSocketAddress;

/**
 * @author geyu
 * @date 2021/1/28 13:52
 */
public abstract class AbstractServer implements Server {

    private ChannelHandler handler;
    private InetSocketAddress bindAddress;

    private int idleTimeout;

    private Codec2 codec;

    private URL url;

    public AbstractServer(URL url, ChannelHandler handler) {// 待 需要传入一个对象，根据对象取值
        this.url = url;
        this.bindAddress = new InetSocketAddress(url.getHost(), url.getPort());
        this.idleTimeout = UrlUtils.getIdleTimeout(url);
        this.codec = getChannelCodec(url);
        this.handler = handler;
        doOpen();
        doStartTimer();
    }

    @Override
    public void close() {
        doClose();
    }

    @Override
    public void close(int timeout) {
        // balalala
        doClose();
    }

    protected abstract void doClose();

    protected abstract void doOpen();

    protected abstract void doStartTimer();

    public Codec2 getCodec() {
        return codec;
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public int getIdleTimeout() {
        System.out.println("服务端读写空闲：" + idleTimeout);
        return idleTimeout;
    }

    protected Codec2 getChannelCodec(URL url) {
        if(url.getParameter("codec") == "default"){
            return new DefaultCodec(url);
        }else{
            return new ExchangeCodec(url);
        }
        // todo myRPC 需要支持spi
    }

    public ChannelHandler getChannelHandler() {
        return handler;
    }

    public URL getUrl() {
        return url;
    }
}
