package my.server;


/**
 * @author geyu
 * @date 2021/1/28 18:14
 */
public class NettyTransporter implements Transporter {

    @Override
    public Server bind(URL url, ExchangeHandler handler) throws RemotingException {
        return new NettyServer(url, new DecodeHandler(new HeaderExchangeHandler(handler)));
    }

    @Override
    public Server bind(URL url) throws RemotingException {
        return bind(url, new ExchangeHandlerDispatcher(new ChannelHandlerAdapter()));
    }

    @Override
    public Server bind(URL url, Replier<?> replier) throws RemotingException {
        return bind(url, new ExchangeHandlerDispatcher(replier, new ChannelHandlerAdapter()));
    }

    @Override
    public Client connect(URL url, ExchangeHandler handler) throws RemotingException {
        return new NettyClient(url, new DecodeHandler(new HeaderExchangeHandler(handler)));
    }

    @Override
    public Client connect(URL url) throws RemotingException {
        return connect(url, new ExchangeHandlerDispatcher(new ChannelHandlerAdapter()));
    }

    @Override
    public Client connect(URL url, Replier<?> replier) throws RemotingException {
        return connect(url, new ExchangeHandlerDispatcher(replier, new ChannelHandlerAdapter()));
    }


}
