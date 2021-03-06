/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.remoting.transport.ChannelHandlerAdapter;
import org.apache.dubbo.remoting.transport.ChannelHandlerDispatcher;

/**
 * Transporter facade. (API, Static, ThreadSafe)
 */
// OK
// 和Exchanges一样也是一个门面，方便用户使用复杂的子系统，不和子系统直接交互。其bind、connect交给默认的NettyTransporter
// 整个是这样的 Exchanges -> HeaderExchanger -> Transporters -> NettyTransporter -> new NettyServer/Client
public class Transporters {

    static {
        // check duplicate jar package
        Version.checkDuplicate(Transporters.class);
        Version.checkDuplicate(RemotingException.class);
    }

    private Transporters() {
    }

    public static RemotingServer bind(String url, ChannelHandler... handler) throws RemotingException {
        return bind(URL.valueOf(url), handler);
    }

    public static RemotingServer bind(URL url, ChannelHandler... handlers) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handlers == null || handlers.length == 0) {
            throw new IllegalArgumentException("handlers == null");
        }
        ChannelHandler handler;
        if (handlers.length == 1) {
            handler = handlers[0];// 这个是DecodeHandler，DecodeHandler 包装 HeaderExchangeHandler 包装 HeartbeatHandlerTest$TestHeartbeatHandler
        } else {
            // 如果 handlers 元素数量大于1，则创建 ChannelHandler 分发器
            handler = new ChannelHandlerDispatcher(handlers);
        }
        // 获取自适应 Transporter 实例，并调用实例方法
        // getTransporter() 方法获取的 Transporter 是在运行时动态创建的，类名为 Transporter$Adaptive，也就是自适应拓展类(看文件:其他/Adaptive/Transporter&Adaptive)。
        // Transporter$Adaptive 会在运行时根据传入的 URL 参数决定加载什么类型的 Transporter，默认为 NettyTransporter。下面我们继续
        // 跟下去，这次分析的是 NettyTransporter 的 bind 方法 ，去看下
        return getTransporter().bind(url, handler);
    }

    public static Client connect(String url, ChannelHandler... handler) throws RemotingException {
        return connect(URL.valueOf(url), handler);
    }

    public static Client connect(URL url, ChannelHandler... handlers) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        ChannelHandler handler;
        if (handlers == null || handlers.length == 0) {
            handler = new ChannelHandlerAdapter();
        } else if (handlers.length == 1) {
            handler = handlers[0];
        } else {
            // 如果 handler 数量大于1，则创建一个 ChannelHandler 分发器
            handler = new ChannelHandlerDispatcher(handlers);
        }
        // 获取 Transporter 自适应拓展类，并调用 connect 方法生成 Client 实例
        return getTransporter().connect(url, handler);
        // getTransporter 方法返回的是自适应拓展类，该类会在运行时根据客户端类型加载指定的 Transporter 实现类。若用户未配置客户端类型，
        // 则默认加载 NettyTransporter，并调用该类的 connect 方法。
    }

    public static Transporter getTransporter() {
        return ExtensionLoader.getExtensionLoader(Transporter.class).getAdaptiveExtension();
    }

}