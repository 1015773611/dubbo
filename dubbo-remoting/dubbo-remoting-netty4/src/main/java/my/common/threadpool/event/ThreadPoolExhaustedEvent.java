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
package my.common.threadpool.event;

import org.apache.dubbo.event.Event;

/**
 * An {@link Event Dubbo event} when the Dubbo thread pool is exhausted.
 *
 * @see Event
 */
// OK
// 类作用看上面注释。Exhausted  筋疲力尽的，疲惫不堪的；耗尽的，枯竭的
public class ThreadPoolExhaustedEvent extends Event {

    final String msg;

    // gx 主要在AbortPolicyWithReport类中使用，用以在线程池Exhausted的时候，构建这个Event（并派遣给对应的监听器处理）
    public ThreadPoolExhaustedEvent(Object source, String msg) {
        super(source);
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }
}
