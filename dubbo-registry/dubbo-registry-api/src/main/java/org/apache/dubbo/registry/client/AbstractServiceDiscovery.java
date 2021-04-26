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
package org.apache.dubbo.registry.client;

import static org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils.isInstanceUpdated;

public abstract class AbstractServiceDiscovery implements ServiceDiscovery {

    protected ServiceInstance serviceInstance;

    @Override
    public ServiceInstance getLocalInstance() {
        return serviceInstance;
    }

    @Override
    public final void register(ServiceInstance serviceInstance) throws RuntimeException {
        this.serviceInstance = serviceInstance;
        // 进去，默认看ZookeeperServiceDiscovery实现类的方法
        doRegister(serviceInstance);
    }

    /**
     * It should be implement in kinds of service discovers.
     */
    public abstract void doRegister(ServiceInstance serviceInstance);

    @Override
    public final void update(ServiceInstance serviceInstance) throws RuntimeException {
        // 想更新的前提是带有"dubbo.instance.revision.updated"参数，且参数值为true
        if (!isInstanceUpdated(serviceInstance)) {
            return;
        }
        this.serviceInstance = serviceInstance;
        doUpdate(serviceInstance);
        // 移除"dubbo.instance.revision.updated"参数
        // resetInstanceUpdateKey(serviceInstance);
    }

    /**
     * It should be implement in kinds of service discovers.
     */
    public abstract void doUpdate(ServiceInstance serviceInstance);
}
