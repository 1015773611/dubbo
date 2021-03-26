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
package org.apache.dubbo.common.config;

import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.context.FrameworkExt;
import org.apache.dubbo.common.context.LifecycleAdapter;
import org.apache.dubbo.common.extension.DisableInject;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.ConfigCenterConfig;
import org.apache.dubbo.config.context.ConfigConfigurationAdapter;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// OK
// 主要是存放各种Configuration的 模仿spring的Environment ，这里没必要实现接口，都继承了LifecycleAdapter todo need pr
// 上面说的是错误的，之所以需要实现的原因是因为父接口只是一个标记接口，可被识别为SPI
public class Environment extends LifecycleAdapter implements FrameworkExt {
    // SPI扩展名
    public static final String NAME = "environment";

    private final PropertiesConfiguration propertiesConfiguration;
    private final SystemConfiguration systemConfiguration;
    private final EnvironmentConfiguration environmentConfiguration;
    private final InmemoryConfiguration externalConfiguration;
    private final InmemoryConfiguration appExternalConfiguration;

    private CompositeConfiguration globalConfiguration; // 这个主要是聚合前面几种Configuration

    private Map<String, String> externalConfigurationMap = new HashMap<>();// 这两个map和前面两个InmemoryConfiguration对应
    private Map<String, String> appExternalConfigurationMap = new HashMap<>();

    private boolean configCenterFirst = true;

    private DynamicConfiguration dynamicConfiguration;

    // gx没调用处，那显然就是反射newInstance创建的（ExtensionLoader.getExtension(NAME) ）
    public Environment() {
        this.propertiesConfiguration = new PropertiesConfiguration();
        this.systemConfiguration = new SystemConfiguration();
        this.environmentConfiguration = new EnvironmentConfiguration();
        this.externalConfiguration = new InmemoryConfiguration();
        this.appExternalConfiguration = new InmemoryConfiguration();
    }

    // 其实就是将配置中心ConfigCenter的信息存入到本地缓存中
    @Override
    public void initialize() throws IllegalStateException {
        // 获取ConfigManager扩展类实例，进去
        ConfigManager configManager = ApplicationModel.getConfigManager();
        // 获取默认的配置中心对象，进去
        Optional<Collection<ConfigCenterConfig>> defaultConfigs = configManager.getDefaultConfigCenter();
        // 如果存在，那么将ConfigCenterConfig的两个属性（都是map结构）设置到Environment的名字一致的两个属性上。
        defaultConfigs.ifPresent(configs -> {
            for (ConfigCenterConfig config : configs) {
                this.setExternalConfigMap(config.getExternalConfiguration());
                this.setAppExternalConfigMap(config.getAppExternalConfiguration());
            }
        });
        // 下两个都是InmemoryConfiguration类型对象，把参数赋值到里面的store属性
        this.externalConfiguration.setProperties(externalConfigurationMap);
        this.appExternalConfiguration.setProperties(appExternalConfigurationMap);
    }

    // 下两个set方法加了@DisableInject注解，用以在创建Extension实例的时候 在inject的过程忽略该set
    @DisableInject
    public void setExternalConfigMap(Map<String, String> externalConfiguration) {
        if (externalConfiguration != null) {
            this.externalConfigurationMap = externalConfiguration;
        }
    }

    @DisableInject
    public void setAppExternalConfigMap(Map<String, String> appExternalConfiguration) {
        if (appExternalConfiguration != null) {
            this.appExternalConfigurationMap = appExternalConfiguration;
        }
    }

    // gx
    public Map<String, String> getExternalConfigurationMap() {
        return externalConfigurationMap;
    }

    // gx
    public Map<String, String> getAppExternalConfigurationMap() {
        return appExternalConfigurationMap;
    }

    // gx
    public void updateExternalConfigurationMap(Map<String, String> externalMap) {
        this.externalConfigurationMap.putAll(externalMap);
    }

    // gx
    public void updateAppExternalConfigurationMap(Map<String, String> externalMap) {
        this.appExternalConfigurationMap.putAll(externalMap);
    }

    /**
     * At start-up, Dubbo is driven by various configuration, such as Application, Registry, Protocol, etc.
     * All configurations will be converged into a data bus - URL, and then drive the subsequent process.
     * <p>
     * At present, there are many configuration sources, including AbstractConfig (API, XML, annotation), - D, config center, etc.
     * This method helps us to filter out the most priority values from various configuration sources.
     *
     * 在启动时，Dubbo是由各种配置驱动的，比如应用程序、注册表、协议等。
     * 所有配置都将聚合到一个数据总线URL中，然后驱动后续流程。
     * < p >
     * 目前，有许多配置源，包括AbstractConfig (API、XML、annotation)、- D、config center等。
     * 这个方法帮助我们从各种配置源中过滤出优先级最高的值。
     *
     * @param config
     * @return
     */
    // gx
    public synchronized CompositeConfiguration getPrefixedConfiguration(AbstractConfig config) {
        // 创建一个复合配置CompositeConfiguration，config.getPrefix()的值有默认值（dubbo.xx，去看下），进去
        CompositeConfiguration prefixedConfiguration = new CompositeConfiguration(config.getPrefix(), config.getId());
        // 将config的一些属性信息填充到ConfigConfigurationAdapter，进去
        Configuration configuration = new ConfigConfigurationAdapter(config);
        // 默认true
        if (this.isConfigCenterFirst()) {
            // The sequence would be: SystemConfiguration -> [[ AppExternalConfiguration -> ExternalConfiguration -> AbstractConfig ]] -> PropertiesConfiguration
            // Config center has the highest priority
            // 以下几个变量（除configuration）都在构造方法里面得到赋值的
            prefixedConfiguration.addConfiguration(systemConfiguration);
            prefixedConfiguration.addConfiguration(environmentConfiguration);
            prefixedConfiguration.addConfiguration(appExternalConfiguration);
            prefixedConfiguration.addConfiguration(externalConfiguration);
            prefixedConfiguration.addConfiguration(configuration);
            prefixedConfiguration.addConfiguration(propertiesConfiguration);
        } else {
            // The sequence would be: SystemConfiguration -> [[ AbstractConfig -> AppExternalConfiguration -> ExternalConfiguration ]] -> PropertiesConfiguration
            // Config center has the highest priority todo need pr 这句注释不对
            prefixedConfiguration.addConfiguration(systemConfiguration);
            prefixedConfiguration.addConfiguration(environmentConfiguration);
            prefixedConfiguration.addConfiguration(configuration);
            prefixedConfiguration.addConfiguration(appExternalConfiguration);
            prefixedConfiguration.addConfiguration(externalConfiguration);
            prefixedConfiguration.addConfiguration(propertiesConfiguration);
        }

        // 前面Config center has the highest priority 我的理解是这样的，appExternalConfiguration和externalConfiguration的值都是
        // 在initialize方法通过configCenter的两个map属性赋值过来的， 所以这两个Configuration就代表Config center，而他们的优先级是相对于
        // ConfigConfigurationAdapter的，可以看上面两个sequence的[[ ]] 之间的排列顺序关系，而[[ ]] 之外的排列顺序是固定的

        return prefixedConfiguration;
    }

    /**
     * There are two ways to get configuration during exposure / reference or at runtime:
     * 1. URL, The value in the URL is relatively fixed. we can get value directly.
     * 2. The configuration exposed in this method is convenient for us to query the latest values from multiple
     * prioritized sources, it also guarantees that configs changed dynamically can take effect on the fly.
     *
         有两种方法可以在暴露/引用或运行时获取配置:
         1. URL, URL中的值是相对固定的。我们可以直接获得value。
         2. 这个方法中暴露的配置便于我们从多个优先级的源中查询最新的值，它还保证动态更改的配置可以动态生效。
     *
     */
    // gx
    public Configuration getConfiguration() {
        if (globalConfiguration == null) {
            // 复合的配置（不带prefix）
            globalConfiguration = new CompositeConfiguration();
            if (dynamicConfiguration != null) {
                globalConfiguration.addConfiguration(dynamicConfiguration);
            }
            // 填充这么5个（第一个主要处理System.getProperty的，第二个主要处理System.getenv(key)）
            globalConfiguration.addConfiguration(systemConfiguration);
            globalConfiguration.addConfiguration(environmentConfiguration);
            globalConfiguration.addConfiguration(appExternalConfiguration);
            globalConfiguration.addConfiguration(externalConfiguration);
            globalConfiguration.addConfiguration(propertiesConfiguration);
        }
        return globalConfiguration;
    }

    // gx
    public boolean isConfigCenterFirst() {
        return configCenterFirst;
    }

    @DisableInject
    public void setConfigCenterFirst(boolean configCenterFirst) {
        this.configCenterFirst = configCenterFirst;
    }

    public Optional<DynamicConfiguration> getDynamicConfiguration() {
        return Optional.ofNullable(dynamicConfiguration);
    }

    @DisableInject
    public void setDynamicConfiguration(DynamicConfiguration dynamicConfiguration) {
        this.dynamicConfiguration = dynamicConfiguration;
    }

    @Override
    public void destroy() throws IllegalStateException {
        clearExternalConfigs();
        clearAppExternalConfigs();
    }

    public PropertiesConfiguration getPropertiesConfiguration() {
        return propertiesConfiguration;
    }

    public SystemConfiguration getSystemConfiguration() {
        return systemConfiguration;
    }

    public EnvironmentConfiguration getEnvironmentConfiguration() {
        return environmentConfiguration;
    }

    public InmemoryConfiguration getExternalConfiguration() {
        return externalConfiguration;
    }

    public InmemoryConfiguration getAppExternalConfiguration() {
        return appExternalConfiguration;
    }

    // For test
    public void clearExternalConfigs() {
        this.externalConfiguration.clear();
        this.externalConfigurationMap.clear();
    }

    // For test
    public void clearAppExternalConfigs() {
        this.appExternalConfiguration.clear();
        this.appExternalConfigurationMap.clear();
    }
}
