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
package org.apache.dubbo.registry.client.metadata;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.metadata.MetadataService;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.String.valueOf;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_PROTOCOL;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PORT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.metadata.MetadataConstants.DEFAULT_METADATA_TIMEOUT_VALUE;
import static org.apache.dubbo.metadata.MetadataConstants.METADATA_PROXY_TIMEOUT_KEY;
import static org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils.getMetadataServiceURLsParams;

/**
 * Standard Dubbo provider enabling introspection service discovery mode.
 *
 * @see MetadataService
 * @since 2.7.5
 */
public class StandardMetadataServiceURLBuilder implements MetadataServiceURLBuilder {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String NAME = "standard";

    /**
     * Build the {@link URL urls} from {@link ServiceInstance#getMetadata() the metadata} of {@link ServiceInstance}
     *
     * @param serviceInstance {@link ServiceInstance}
     * @return the not-null {@link List}
     */
    @Override
    public List<URL> build(ServiceInstance serviceInstance) {

        //key = "dubbo"
        //value = {JSONObject@4667}  size = 3
        // "port" -> "20881"
        // "dubbo" -> "2.0.2"
        // "version" -> "1.0.0"
        Map<String, Map<String, String>> paramsMap = getMetadataServiceURLsParams(serviceInstance);

        List<URL> urls = new ArrayList<>(paramsMap.size());

        String serviceName = serviceInstance.getServiceName();

        String host = serviceInstance.getHost();

        for (Map.Entry<String, Map<String, String>> entry : paramsMap.entrySet()) {
            String protocol = entry.getKey();
            Map<String, String> params = entry.getValue();
            int port = Integer.parseInt(params.get(PORT_KEY));
            URLBuilder urlBuilder = new URLBuilder()
                    .setHost(host)
                    .setPort(port)
                    .setProtocol(protocol)
                    .setPath(MetadataService.class.getName())
                    .addParameter(TIMEOUT_KEY, ConfigurationUtils.get(METADATA_PROXY_TIMEOUT_KEY, DEFAULT_METADATA_TIMEOUT_VALUE))
                    .addParameter(SIDE_KEY, CONSUMER);

            // add parameters
            params.forEach((name, value) -> urlBuilder.addParameter(name, valueOf(value)));

            // add the default parameters
            urlBuilder.addParameter(GROUP_KEY, serviceName);

            // dubbo://30.25.58.166:20881/org.apache.dubbo.metadata.MetadataService?dubbo=2.0.2&group=demo-provider&port=20881&side=consumer&timeout=5000&version=1.0.0
            urls.add(urlBuilder.build());
        }
        return urls;
    }
}
