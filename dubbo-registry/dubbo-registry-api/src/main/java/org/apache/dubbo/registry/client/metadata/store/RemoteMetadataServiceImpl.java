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
package org.apache.dubbo.registry.client.metadata.store;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.metadata.MetadataInfo;
import org.apache.dubbo.metadata.WritableMetadataService;
import org.apache.dubbo.metadata.definition.ServiceDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.MetadataReportInstance;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.RpcException;

import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PID_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMESTAMP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_CLUSTER_KEY;

public class RemoteMetadataServiceImpl {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private WritableMetadataService localMetadataService;

    public RemoteMetadataServiceImpl(WritableMetadataService writableMetadataService) {
        this.localMetadataService = writableMetadataService;
    }

    public Map<String/*registryCluster*/, MetadataReport> getMetadataReports() {
        // 从MetadataReportInstance获取，这个MetadataReportInstance就像一个缓存容器（注意其init方法的调用时机）
        return MetadataReportInstance.getMetadataReports(false);
    }

    public void publishMetadata(String serviceName) {
        Map<String, MetadataInfo> metadataInfos = localMetadataService.getMetadataInfos();
        metadataInfos.forEach((registryCluster, metadataInfo) -> {
            if (!metadataInfo.hasReported()) {
                SubscriberMetadataIdentifier identifier = new SubscriberMetadataIdentifier(serviceName, metadataInfo.calAndGetRevision());
                metadataInfo.calAndGetRevision();
                metadataInfo.getExtendParams().put(REGISTRY_CLUSTER_KEY, registryCluster);
                MetadataReport metadataReport = getMetadataReports().get(registryCluster);
                if (metadataReport == null) {
                    metadataReport = getMetadataReports().entrySet().iterator().next().getValue();
                }
                // 核心，发布--》app级别
                metadataReport.publishAppMetadata(identifier, metadataInfo);
                metadataInfo.markReported();
            }
        });
    }

    public MetadataInfo getMetadata(ServiceInstance instance) {
        SubscriberMetadataIdentifier identifier = new SubscriberMetadataIdentifier(instance.getServiceName(),
                ServiceInstanceMetadataUtils.getExportedServicesRevision(instance));

        String registryCluster = instance.getExtendParams().get(REGISTRY_CLUSTER_KEY);

        MetadataReport metadataReport = getMetadataReports().get(registryCluster);
        if (metadataReport == null) {
            metadataReport = getMetadataReports().entrySet().iterator().next().getValue();
        }
        return metadataReport.getAppMetadata(identifier, instance.getExtendParams());
    }

    public void publishServiceDefinition(URL url) {
        String side = url.getParameter(SIDE_KEY);
        if (PROVIDER_SIDE.equalsIgnoreCase(side)) {
            //TODO, the params part is duplicate with that stored by exportURL(url), can be further optimized in the future.
            //进去
            publishProvider(url);
        } else {
            //TODO, only useful for ops showing the url parameters, this is duplicate with subscribeURL(url), can be removed in the future.
            publishConsumer(url);
        }
    }

    private void publishProvider(URL providerUrl) throws RpcException {
        // first add into the list
        // remove the individual（个人的；个别的；独特的） param 移除特殊参数，因为暴露到远端FullServiceDefinition必须是公共的
        providerUrl = providerUrl.removeParameters(PID_KEY, TIMESTAMP_KEY, Constants.BIND_IP_KEY,
                Constants.BIND_PORT_KEY, TIMESTAMP_KEY);

        try {
            String interfaceName = providerUrl.getParameter(INTERFACE_KEY);
            if (StringUtils.isNotEmpty(interfaceName)) {
                Class interfaceClass = Class.forName(interfaceName);
                //注意两个参数，后者是作为 FullServiceDefinition#parameters属性
                FullServiceDefinition fullServiceDefinition = ServiceDefinitionBuilder.buildFullDefinition(interfaceClass,
                        providerUrl.getParameters());
                // getMetadataReports进去，获取远端元数据报告中心，向每个进行报告，其实就是写到zk上
                for (Map.Entry<String, MetadataReport> entry : getMetadataReports().entrySet()) {
                    MetadataReport metadataReport = entry.getValue();
                    // 方法的两个参数可以理解为 {唯一id ：具体接口的元数据信息}
                    metadataReport.storeProviderMetadata(new MetadataIdentifier(providerUrl.getServiceInterface(),
                            providerUrl.getParameter(VERSION_KEY), providerUrl.getParameter(GROUP_KEY),
                            PROVIDER_SIDE, providerUrl.getParameter(APPLICATION_KEY)), fullServiceDefinition);
                }
                return;
            }
            logger.error("publishProvider interfaceName is empty . providerUrl: " + providerUrl.toFullString());
        } catch (ClassNotFoundException e) {
            //ignore error
            logger.error("publishProvider getServiceDescriptor error. providerUrl: " + providerUrl.toFullString(), e);
        }
    }

    private void publishConsumer(URL consumerURL) throws RpcException {
        final URL url = consumerURL.removeParameters(PID_KEY, TIMESTAMP_KEY, Constants.BIND_IP_KEY,
                Constants.BIND_PORT_KEY, TIMESTAMP_KEY);
        getMetadataReports().forEach((registryKey, config) -> {
            config.storeConsumerMetadata(new MetadataIdentifier(url.getServiceInterface(),
                    url.getParameter(VERSION_KEY), url.getParameter(GROUP_KEY), CONSUMER_SIDE,
                    url.getParameter(APPLICATION_KEY)), url.getParameters());
        });
    }

}
