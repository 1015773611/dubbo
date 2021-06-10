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
package org.apache.dubbo.event;

import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.lang.Prioritized;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.utils.ReflectUtils.findParameterizedTypes;

/**
 * The {@link Event Dubbo Event} Listener that is based on Java standard {@link java.util.EventListener} interface supports
 * the generic {@link Event}.
 * <p>
 * The {@link #onEvent(Event) handle method} will be notified when the matched-type {@link Event Dubbo Event} is
 * published, whose priority could be changed by {@link #getPriority()} method.
 *
 * @param <E> the concrete class of {@link Event Dubbo Event}
 * @see Event 
 * @see java.util.EventListener
 * @since 2.7.5
 */
// OK
@SPI
@FunctionalInterface
public interface EventListener<E extends Event> extends java.util.EventListener, Prioritized {

    /**
     * Handle a {@link Event Dubbo Event} when it's be published
     *
     * @param event a {@link Event Dubbo Event}
     */
    void onEvent(E event);

    /**
     * The priority of {@link EventListener current listener}.
     *
     * @return the value is more greater, the priority is more lower.
     * {@link Integer#MIN_VALUE} indicates the highest priority. The default value is {@link Integer#MAX_VALUE}.
     * The comparison rule , refer to {@link #compareTo}.
     * @return 值越大，优先级越低。
     * {@link Integer#MIN_VALUE} 表示最高优先级。 默认值为 {@link Integer#MAX_VALUE}。
     * 比较规则，参考{@link #compareTo}。
     */
    default int getPriority() {
        return NORMAL_PRIORITY;
    }

    /**
     * Find the {@link Class type} {@link Event Dubbo event} from the specified {@link EventListener Dubbo event listener}
     *
     * @param listener the {@link Class class} of {@link EventListener Dubbo event listener}
     * @return <code>null</code> if not found
     */
    static Class<? extends Event> findEventType(EventListener<?> listener) {
        // 进去
        return findEventType(listener.getClass());
    }

    /**
     * Find the {@link Class type} {@link Event Dubbo event} from the specified {@link EventListener Dubbo event listener}
     *
     * @param listenerClass the {@link Class class} of {@link EventListener Dubbo event listener}
     * @return <code>null</code> if not found
     */
    // 目的就是找到具体的泛型，比如 FileSystemServiceDiscovery implements ServiceDiscovery, EventListener<ServiceInstancesChangedEvent>
    // 就是拿到<>里的ServiceInstancesChangedEvent
    // 去看下测试程序 EventListenerTest
    static Class<? extends Event> findEventType(Class<?> listenerClass) {
        Class<? extends Event> eventType = null;

        // 首先当前类必须是EventListener的子类
        if (listenerClass != null && EventListener.class.isAssignableFrom(listenerClass)) {
            eventType = findParameterizedTypes(listenerClass)// 进去
                    .stream()
                    .map(EventListener::findEventType)// 进去
                    .filter(Objects::nonNull)
                    .findAny()
                    .orElse((Class) findEventType(listenerClass.getSuperclass()));
        }
        return eventType;
    }


    /**
     * Find the type {@link Event Dubbo event} from the specified {@link ParameterizedType} presents
     * a class of {@link EventListener Dubbo event listener}
     *
     * @param parameterizedType the {@link ParameterizedType} presents a class of {@link EventListener Dubbo event listener}
     * @return <code>null</code> if not found
     */
    static Class<? extends Event> findEventType(ParameterizedType parameterizedType) {
        Class<? extends Event> eventType = null;
        Type rawType = parameterizedType.getRawType();
        // rawType 就是findEventType(Class<?> listenerClass)方法上面例子的 EventListener<ServiceInstancesChangedEvent>部分
        if ((rawType instanceof Class) && EventListener.class.isAssignableFrom((Class) rawType)) {
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            for (Type typeArgument : typeArguments) {
                // 这里判断是否是Class，因为比如一个类 extends A<String,T>，那么上面的typeArguments返回的就是这两个，但是只有第一个满足instanceof Class
                if (typeArgument instanceof Class) {
                    Class argumentClass = (Class) typeArgument;
                    // 拿到具体的类型还要判断是否是Event子类
                    if (Event.class.isAssignableFrom(argumentClass)) {
                        eventType = argumentClass;
                        break;
                    }
                }
            }
        }

        return eventType;
    }
}