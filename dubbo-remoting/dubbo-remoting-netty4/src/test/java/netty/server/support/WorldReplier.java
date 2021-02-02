package netty.server.support;

import netty.server.InnerChannel;
import netty.server.Replier;

/**
 * @author geyu
 * @date 2021/2/2 12:55
 */
public class WorldReplier implements Replier<World> {
    @Override
    public Object reply(InnerChannel channel, World request) {
        return new World("hello " + request.getName());
    }
}
