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
package my.common.threadpool.support.fixed;

import my.common.threadpool.ThreadPool;
import my.common.threadpool.support.AbortPolicyWithReport;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadlocal.NamedInternalThreadFactory;

import java.util.concurrent.*;

import static org.apache.dubbo.common.constants.CommonConstants.*;

/**
 * Creates a thread pool that reuses a fixed number of threads
 *
 * @see java.util.concurrent.Executors#newFixedThreadPool(int) ---这个注释就表明当前类其实就是模仿了原生的newFixedThreadPool
 */
// OK
// 少部分注释，更多解释看LimitedThreadPool
public class FixedThreadPool implements ThreadPool {

    @Override
    public Executor getExecutor(URL url) {
        String name = url.getParameter(THREAD_NAME_KEY, DEFAULT_THREAD_NAME);
        int threads = url.getParameter(THREADS_KEY, DEFAULT_THREADS);
        int queues = url.getParameter(QUEUES_KEY, DEFAULT_QUEUES);
        // 所谓的固定就是体现在核心线程数 = 最大线程数
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                queues == 0 ? new SynchronousQueue<Runnable>() :
                        (queues < 0 ? new LinkedBlockingQueue<Runnable>()
                                : new LinkedBlockingQueue<Runnable>(queues)),
                new NamedInternalThreadFactory(name, true), new AbortPolicyWithReport(name, url));
    }

}
