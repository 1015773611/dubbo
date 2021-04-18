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

package org.apache.dubbo.registry.retry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.registry.support.FailbackRegistry;

/**
 * FailedRegisteredTask
 */
// OK
public final class FailedRegisteredTask extends AbstractRetryTask {

    // 赋值给父类的taskName属性，做打印使用的
    private static final String NAME = "retry register";

    // gx
    public FailedRegisteredTask(URL url, FailbackRegistry registry) {
        super(url, registry, NAME);
    }

    // 模板方法，被父类的run方法调用
    @Override
    protected void doRetry(URL url, FailbackRegistry registry) {
        // 再次/重试注册
        registry.doRegister(url);
        // 进去
        registry.removeFailedRegisteredTask(url);
    }
}
