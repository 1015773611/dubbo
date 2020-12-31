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
package org.apache.dubbo.common.infra.support;

import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.infra.InfraAdapter;
import org.apache.dubbo.common.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_ENV_KEYS;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_LABELS;
import static org.apache.dubbo.common.constants.CommonConstants.EQUAL_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.SEMICOLON_SPLIT_PATTERN;

@Activate
public class EnvironmentAdapter implements InfraAdapter {

    /**
     * 1. OS Environment: DUBBO_LABELS=tag=pre;key=value
     * 2. JVM Options: -Denv_keys = DUBBO_KEY1, DUBBO_KEY2
     */
    @Override
    public Map<String, String> getExtraAttributes(Map<String, String> params) {
        // params参数根本没用到

        Map<String, String> parameters = new HashMap<>();

        // 进去
        String rawLabels = ConfigurationUtils.getProperty(DUBBO_LABELS);
        if (StringUtils.isNotEmpty(rawLabels)) {
            // 分号分割  看上面1.例子
            String[] labelPairs = SEMICOLON_SPLIT_PATTERN.split(rawLabels);
            for (String pair : labelPairs) {
                // = 分割
                String[] label = EQUAL_SPLIT_PATTERN.split(pair);
                if (label.length == 2) {
                    parameters.put(label[0], label[1]);
                }
            }
        }

        String rawKeys = ConfigurationUtils.getProperty(DUBBO_ENV_KEYS);
        if (StringUtils.isNotEmpty(rawKeys)) {
            // , 分割  看上面2.例子
            String[] keys = COMMA_SPLIT_PATTERN.split(rawKeys);
            for (String key : keys) {
                // 这个key就是系统属性的key了，比如上面2.例子的DUBBO_KEY1
                String value = ConfigurationUtils.getProperty(key);
                if (value != null) {
                    parameters.put(key, value);
                }
            }
        }
        return parameters;
    }

    @Override
    public String getAttribute(String key) {
        return ConfigurationUtils.getProperty(key);
    }
}
