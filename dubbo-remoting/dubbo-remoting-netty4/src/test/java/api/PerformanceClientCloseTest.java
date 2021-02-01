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
package api;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.remoting.exchange.Exchangers;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * ProformanceClient
 * The test class will report abnormal thread pool, because the judgment on the thread pool concurrency problems produced in DefaultChannelHandler (connected event has been executed asynchronously, judgment, then closed the thread pool, thread pool and execution error, this problem can be specified through the Constants.CHANNEL_HANDLER_KEY=connection.)
 * 测试类将报告异常的线程池,线程池并发性问题上的判断产生 DefaultChannelHandler(连接事件异步执行,判断,然后关闭线程池,线程池和执行错误,这个问题可以通过指定Constants.CHANNEL_HANDLER_KEY =连接。)
 */
// OK
public class PerformanceClientCloseTest  {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceClientCloseTest.class);

    // 注意先去 PerformanceServerMain 启动服务端，在启动下面的测试程序
    @Test
    public void testClient() throws Throwable {

        // read server info from property
        // 下面临时注释掉
//        if (PerformanceUtils.getProperty("server", null) == null) {
//            logger.warn("Please set -Dserver=127.0.0.1:9911");
//            return;
//        }
        final String server = System.getProperty("server", "127.0.0.1:9911");
        final String transporter = PerformanceUtils.getProperty(Constants.TRANSPORTER_KEY, Constants.DEFAULT_TRANSPORTER);
        final String serialization = PerformanceUtils.getProperty(Constants.SERIALIZATION_KEY, Constants.DEFAULT_REMOTING_SERIALIZATION);
        final int timeout = PerformanceUtils.getIntProperty(TIMEOUT_KEY, DEFAULT_TIMEOUT);
        final int concurrent = PerformanceUtils.getIntProperty("concurrent", 10); // 并发数/线程数 （10*100的10）
        final int runs = PerformanceUtils.getIntProperty("runs", 1000); // 每个线程执行的次数  （10*100的100）
        final String onerror = PerformanceUtils.getProperty("onerror", "continue");

        final String url = "exchange://" + server + "?transporter=" + transporter
                + "&serialization=" + serialization
//            + "&"+Constants.CHANNEL_HANDLER_KEY+"=connection"
                + "&connect.timeout=" + 5000 // 这个自己加的，因为发现好多都连接超时了（默认3000ms，不过大概都是30xx ms就能完成，所以这里调到5000）
                + "&timeout=" + timeout+"&heartbeat="+1000;// 原来没有&heartbeat="+1000，我自己加的，因为默认的空闲时间太长（60s）

        final AtomicInteger count = new AtomicInteger();
        final AtomicInteger error = new AtomicInteger();
        for (int n = 0; n < concurrent; n++) {
            new Thread(new Runnable() { // concurrent个并发线程
                public void run() {
                    for (int i = 0; i < runs; i++) { // 每个线程做runs次操作
                        ExchangeClient client = null;
                        try {
                            client = Exchangers.connect(url); // 连接  。 表明 concurrent*runs 个客户端（连接同一个server）
                            int c = count.incrementAndGet();
                            if (c % 100 == 0) {
                                // 每隔100次打印
                                System.out.println("count: " + count.get() + ", error: " + error.get());
                            }
                        } catch (Exception e) {
                            error.incrementAndGet();
                            e.printStackTrace();
                            System.out.println("count: " + count.get() + ", error: " + error.get());
                            // 这里是个小技巧
                            if ("exit".equals(onerror)) {
                                System.exit(-1);
                            } else if ("break".equals(onerror)) {
                                break;
                            } else if ("sleep".equals(onerror)) {
                                try {
                                    Thread.sleep(30000);
                                } catch (InterruptedException e1) {
                                }
                            }
                        } finally {
                            if (client != null) {
                                client.close();
                            }
                        }
                    }
                }
            }).start();
        }
        // 最后全部连接成功如下（前提服务端开启了）

        // 这里没啥的，主要是为了阻塞，直接System.in.read()亦可
        synchronized (PerformanceServerTest.class) {
            while (true) {
                try {
                    PerformanceServerTest.class.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

}
