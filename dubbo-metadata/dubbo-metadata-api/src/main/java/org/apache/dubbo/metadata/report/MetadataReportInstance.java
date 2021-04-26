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
package org.apache.dubbo.metadata.report;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.CommonConstants.*;
import static org.apache.dubbo.metadata.report.support.Constants.METADATA_REPORT_KEY;

/**
 * 2019-08-09
 */
public class MetadataReportInstance {

    private static AtomicBoolean init = new AtomicBoolean(false);

    private static final Map<String/*relatedRegistryId*/, MetadataReport/*ZookeeperMetadataReport*/> metadataReports = new HashMap<>();

    // gx
    public static void init(MetadataReportConfig config) {
        if (init.get()) {
            return;
        }
        //  MetadataReportFactory 的 实现有很多，默认为redis，但是我们用的一般是zk
        MetadataReportFactory metadataReportFactory = ExtensionLoader.getExtensionLoader(MetadataReportFactory.class).getAdaptiveExtension();
        // 进去，内部会拼metadata://
        // cofig->url的转化关系比如 <dubbo:metadata-report address="zookeeper://127.0.0.1:2181" />
        //  ----> metadata://127.0.0.1:2181?metadata=zookeeper
        URL url = config.toUrl();
        // metadata:// 协议
        if (METADATA_REPORT_KEY.equals(url.getProtocol())) {
            // 获取"metadata"参数值，比如zookeeper， 默认dubbo
            String protocol = url.getParameter(METADATA_REPORT_KEY, DEFAULT_DIRECTORY);
            url = URLBuilder.from(url)
                    .setProtocol(protocol)
                    .removeParameter(METADATA_REPORT_KEY)
                    .build();
            // 此时url比如为zookeeper://127.0.0.1:2181
        }
        // 此时url比如为zookeeper://127.0.0.1:2181?application=demo-provider
        url = url.addParameterIfAbsent(APPLICATION_KEY, ApplicationModel.getApplicationConfig().getName());

        String relatedRegistryId = config.getRegistry() == null ? DEFAULT_KEY : config.getRegistry();
        // key为默认default，value很重要，就是涉及到远端metadataReport实例的创建了，进去
        metadataReports.put(relatedRegistryId, metadataReportFactory.getMetadataReport(url));
        init.set(true);
    }

    public static Map<String, MetadataReport> getMetadataReports(boolean checked) {
        if (checked) {
            checkInit();
        }
        return metadataReports;
    }

    public static MetadataReport getMetadataReport(String registryKey) {
        checkInit();
        MetadataReport metadataReport = metadataReports.get(registryKey);
        if (metadataReport == null) {
            // 如果为null，则选择第一个MetadataReport返回
            metadataReport = metadataReports.values().iterator().next();
        }
        return metadataReport;
    }


    private static void checkInit() {
        if (!init.get()) {
            throw new IllegalStateException("the metadata report was not inited.");
        }
    }
}
