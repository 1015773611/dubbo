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
package my.common.extension;

import my.common.utils.StringUtils;
import my.server.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Code generator for Adaptive class
 */
public class AdaptiveClassCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveClassCodeGenerator.class);

    private static final String CLASSNAME_INVOCATION = "org.apache.dubbo.rpc.Invocation";

    private static final String CODE_PACKAGE = "package %s;\n";

    private static final String CODE_IMPORTS = "import %s;\n";

    private static final String CODE_CLASS_DECLARATION = "public class %s$Adaptive implements %s {\n";

    private static final String CODE_METHOD_DECLARATION = "public %s %s(%s) %s {\n%s}\n";

    private static final String CODE_METHOD_ARGUMENT = "%s arg%d";

    private static final String CODE_METHOD_THROWS = "throws %s";

    private static final String CODE_UNSUPPORTED = "throw new UnsupportedOperationException(\"The method %s of interface %s is not adaptive method!\");\n";

    private static final String CODE_URL_NULL_CHECK = "if (arg%d == null) throw new IllegalArgumentException(\"url == null\");\n%s url = arg%d;\n";

    private static final String CODE_EXT_NAME_ASSIGNMENT = "String extName = %s;\n";

    private static final String CODE_EXT_NAME_NULL_CHECK = "if(extName == null) "
                    + "throw new IllegalStateException(\"Failed to get extension (%s) name from url (\" + url.toString() + \") use keys(%s)\");\n";

    private static final String CODE_INVOCATION_ARGUMENT_NULL_CHECK = "if (arg%d == null) throw new IllegalArgumentException(\"invocation == null\"); "
                    + "String methodName = arg%d.getMethodName();\n";


    private static final String CODE_EXTENSION_ASSIGNMENT = "%s extension = (%<s)%s.getExtensionLoader(%s.class).getExtension(extName);\n";

    private static final String CODE_EXTENSION_METHOD_INVOKE_ARGUMENT = "arg%d";

    private final Class<?> type;

    private String defaultExtName;

    public AdaptiveClassCodeGenerator(Class<?> type, String defaultExtName) {
        this.type = type;
        this.defaultExtName = defaultExtName;
    }

    /**
     * test if given type has at least one method annotated with <code>Adaptive</code>
     */
    // 看上面注释
    private boolean hasAdaptiveMethod() {
        // 遍历方法列表。 检测方法上是否有 Adaptive 注解
        return Arrays.stream(type.getMethods()).anyMatch(m -> m.isAnnotationPresent(Adaptive.class));
    }

    /**
     * generate and return class code
     */
    public String generate() {
        // no need to generate adaptive class since there's no adaptive method found.
        // 方法首先会通过反射检测接口方法是否包含 Adaptive 注解。对于要生成自适应拓展的接口，Dubbo 要求该接口至少有一个方法被 Adaptive 注解修饰。
        // 若不满足此条件，就会抛出运行时异常（比如AddExt1接口就没有Adaptive子类，但是接口的方法是带有 @Adaptive注解的，详见test_AddExtension_Adaptive_ExceptionWhenExistedAdaptive测试方法）
        // 类名没有@Adaptive，这里判断方法是否含有该注解， 进去
        if (!hasAdaptiveMethod()) {
            // 日志
            throw new IllegalStateException("No adaptive method exist on extension " + type.getName() + ", refuse to create the adaptive class!");
        }
        // 下面硬生生拼一个（比如type为）Protocol的实现类
        StringBuilder code = new StringBuilder();
        // 生成 package 代码：package + type 所在包，进去
        code.append(generatePackageInfo());
        // 生成 import 代码：import + ExtensionLoader 全限定名，进去
        code.append(generateImports());
        // 生成类代码：public class + type简单名称 + $Adaptive + implements + type全限定名 + { ，进去
        code.append(generateClassDeclaration());

        // 生成方法的实现体，一个方法可以被 Adaptive 注解修饰，也可以不被修饰。以 Protocol 接口为例，该接口的 destroy 和 getDefaultPort
        // 未标注 Adaptive 注解，其他方法均标注了 Adaptive 注解

        // 前面说过方法代理逻辑会从 URL 中提取目标拓展的名称，因此代码生成逻辑的一个重要的任务是从方法的参数列表或者其他参数中获取 URL 数据。
        // 举例说明一下，我们要为 Protocol 接口的 refer 和 export 方法生成代理逻辑。在运行时，通过反射得到的方法定义大致如下：
        //      Invoker refer(Class<T> arg0, URL arg1) throws RpcException;
        //      Exporter export(Invoker<T> arg0) throws RpcException;
        // 对于 refer 方法，通过遍历 refer 的参数列表即可获取 URL 数据，这个还比较简单。对于 export 方法，获取 URL 数据则要麻烦一些。
        // export 参数列表中没有 URL 参数，因此需要从 Invoker 参数中获取 URL 数据。获取方式是调用 Invoker 中可返回 URL 的 getter 方法，
        // 比如 getUrl。如果 Invoker 中无相关 getter 方法，此时则会抛出异常。整个逻辑如下
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            // generateMethod核心！！！ 进去
            code.append(generateMethod(method));
        }
        code.append("}");

        if (logger.isDebugEnabled()) {
            logger.debug(code.toString());
        }
        return code.toString();

        // eg 只会为SPI接口里面带有@Adaptive注解的方法生成相关逻辑，其他的方法都是throw new UnsupportedOperationException(balala...)
        /*
        package org.apache.dubbo.rpc;
        import org.apache.dubbo.common.extension.ExtensionLoader;

        public class Protocol$Adaptive implements org.apache.dubbo.rpc.Protocol {

            public org.apache.dubbo.rpc.Exporter export(org.apache.dubbo.rpc.Invoker arg0) throws org.apache.dubbo.rpc.RpcException {
                if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
                if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
                org.apache.dubbo.common.URL url = arg0.getUrl();
                String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
                if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
                org.apache.dubbo.rpc.Protocol extension = (org.apache.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension(extName);
                return extension.export(arg0);
            }

            public java.util.List getServers()  {
                throw new UnsupportedOperationException("The method public default java.util.List org.apache.dubbo.rpc.Protocol.getServers() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
            }

            public org.apache.dubbo.rpc.Invoker refer(java.lang.Class arg0, org.apache.dubbo.common.URL arg1) throws org.apache.dubbo.rpc.RpcException {
                if (arg1 == null) throw new IllegalArgumentException("url == null");
                org.apache.dubbo.common.URL url = arg1;
                String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
                if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
                org.apache.dubbo.rpc.Protocol extension = (org.apache.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension(extName);
                return extension.refer(arg0, arg1);
            }

            public void destroy()  {
                throw new UnsupportedOperationException("The method public abstract void org.apache.dubbo.rpc.Protocol.destroy() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
            }

            public int getDefaultPort()  {
                throw new UnsupportedOperationException("The method public abstract int org.apache.dubbo.rpc.Protocol.getDefaultPort() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
            }
        }

        ============= 再比如（如下实际格式的紧凑的，这里我稍微格式化了下）
        package org.apache.dubbo.common.extension.ext8_add;
        import org.apache.dubbo.common.extension.ExtensionLoader;
        public class AddExt1$Adaptive implements org.apache.dubbo.common.extension.ext8_add.AddExt1 {
            public java.lang.String echo(org.apache.dubbo.common.URL arg0, java.lang.String arg1)  {
                if (arg0 == null) throw new IllegalArgumentException("url == null");
                org.apache.dubbo.common.URL url = arg0;
                String extName = url.getParameter("add.ext1", "impl1");
                if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.common.extension.ext8_add.AddExt1) name from url (" + url.toString() + ") use keys([add.ext1])");
                org.apache.dubbo.common.extension.ext8_add.AddExt1 extension = (org.apache.dubbo.common.extension.ext8_add.AddExt1)ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.extension.ext8_add.AddExt1.class).getExtension(extName);
                return extension.echo(arg0, arg1);
            }
        }
        */
    }

    /**
     * generate package info
     */
    private String generatePackageInfo() {
        // package [org.apache.dubbo.common.extension.adaptive];
        return String.format(CODE_PACKAGE, type.getPackage().getName());
    }

    /**
     * generate imports
     */
    private String generateImports() {
        // import [org.apache.dubbo.common.extension.ExtensionLoader];
        return String.format(CODE_IMPORTS, ExtensionLoader.class.getName());
    }

    /**
     * generate class declaration
     */
    private String generateClassDeclaration() {
        //      type.getCanonicalName()是类的全限定名称（和getName的返回值一样），比如org.apache.dubbo.common.extension.ext8_add.AddExt1
        //  public class [HasAdaptiveExt]$Adaptive implements [org.apache.dubbo.common.extension.adaptive.HasAdaptiveExt] {
        return String.format(CODE_CLASS_DECLARATION, type.getSimpleName(), type.getCanonicalName());
    }

    /**
     * generate method not annotated with Adaptive with throwing unsupported exception
     */
    private String generateUnsupported(Method method) {
        // 生成的代码格式如下：
        // throw new UnsupportedOperationException(
        //     "method " + 方法签名 + of interface + 全限定接口名 + is not adaptive method!”)
        // 以 Protocol 接口的 destroy 方法为例，上面代码生成的内容如下
        // throw new UnsupportedOperationException(
        //            "method public abstract void com.alibaba.dubbo.rpc.Protocol.destroy() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
        return String.format(CODE_UNSUPPORTED, method, type.getName());
    }

    /**
     * get index of parameter with type URL
     */
    private int getUrlTypeIndex(Method method) {
        int urlTypeIndex = -1;
        // 获取所有参数类型
        Class<?>[] pts = method.getParameterTypes();
        for (int i = 0; i < pts.length; ++i) {
            // 判断是否是URL类型
            if (pts[i].equals(URL.class)) {
                urlTypeIndex = i;
                break;
            }
        }
        return urlTypeIndex;
    }

    /**
     * generate method declaration
     */
    private String generateMethod(Method method) {
        // 以AddExt1类为例，里面的echo方法，这里getCanonicalName返回的字符串eg:java.lang.String
        String methodReturnType = method.getReturnType().getCanonicalName();
        String methodName = method.getName();// 以AddExt1类为例 ，echo
        // 关键！进去
        String methodContent = generateMethodContent(method);
        String methodArgs = generateMethodArguments(method);// 以AddExt1类为例 ，org.apache.dubbo.common.URL arg0, java.lang.String arg1
        String methodThrows = generateMethodThrows(method);
        // 上面几部分填充到CODE_METHOD_DECLARATION
        return String.format(CODE_METHOD_DECLARATION, methodReturnType, methodName, methodArgs, methodThrows, methodContent);
    }

    /**
     * generate method arguments
     */
    private String generateMethodArguments(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return IntStream.range(0, pts.length)
                        .mapToObj(i -> String.format(CODE_METHOD_ARGUMENT, pts[i].getCanonicalName(), i))
                        .collect(Collectors.joining(", "));
    }

    /**
     * generate method throws
     */
    private String generateMethodThrows(Method method) {
        Class<?>[] ets = method.getExceptionTypes();
        if (ets.length > 0) {
            String list = Arrays.stream(ets).map(Class::getCanonicalName).collect(Collectors.joining(", "));
            return String.format(CODE_METHOD_THROWS, list);
        } else {
            return "";
        }
    }

    /**
     * generate method URL argument null check
     */
    private String generateUrlNullCheck(int index) {
        // 为 URL 类型参数生成判空代码，格式如下：
        // if (arg + index(0、1、2) == null)
        //     throw new IllegalArgumentException("url == null");
        // 为 URL 类型参数生成赋值代码，形如 URL url = arg1
        // 具体看CODE_URL_NULL_CHECK
        return String.format(CODE_URL_NULL_CHECK, index, URL.class.getName(), index);
        // 比如Protocol接口的refer方法
        // if (arg1 == null) throw new IllegalArgumentException("url == null");
        // org.apache.dubbo.common.URL url = arg1;


    }

    /**
     * generate method content
     */
    private String generateMethodContent(Method method) {
        Adaptive adaptiveAnnotation = method.getAnnotation(Adaptive.class);
        StringBuilder code = new StringBuilder(512);
        // 如果方法上无 Adaptive 注解，则生成 throw new UnsupportedOperationException(...) 代码
        if (adaptiveAnnotation == null) {
            // 进去
            return generateUnsupported(method);
        } else {
            // 遍历参数列表，确定 URL 参数位置，进去
            int urlTypeIndex = getUrlTypeIndex(method);

            // urlTypeIndex != -1，表示参数列表中存在 URL 参数
            if (urlTypeIndex != -1) {
                // Null Point check 进去
                code.append(generateUrlNullCheck(urlTypeIndex));
            } else {
                // 参数列表中不存在 URL 类型参数。按照之前说的就是，从 Invoker 参数中获取 URL 数据。获取方式是调用 Invoker 中可返回 URL 的
                // getter 方法，比如 getUrl。如果 Invoker 中无相关 getter 方法，此时则会抛出异常。进去
                code.append(generateUrlAssignmentIndirectly(method));
            }

            // 获取 Adaptive 注解值，如果没有的话，那么就是对类名进行转化（比如AddExt1，返回的就是add.ext1）， 进去
            String[] value = getMethodAdaptiveValue(adaptiveAnnotation);

            // 此段逻辑是检测方法列表中是否存在 Invocation 类型的参数，若存在，则为其生成判空代码和其他一些代码
            // 检测 Invocation 参数 进去
            boolean hasInvocation = hasInvocationArgument(method);

            // 进去
            code.append(generateInvocationArgumentNullCheck(method));

            // 从url以前面获取到的value作为key获取extName，比如：String extName = url.getParameter("add.ext1", "adaptive");进去
            code.append(generateExtNameAssignment(value, hasInvocation));

            // 生成 extName 判空代码 进去
            // check extName == null?
            code.append(generateExtNameNullCheck(value));

            //  生成拓展获取代码，很重要！ 进去
            code.append(generateExtensionAssignment());

            // 生成返回值和目标方法调用逻辑，进去
            // return statement
            code.append(generateReturnAndInvocation(method));
        }

        // 上面代码比较复杂，不是很好理解。对于这段代码，建议大家写点测试用例，对 Protocol、LoadBalance 以及 Transporter 等接口的自适应拓展
        // 类代码生成过程进行调试。这里我以 Transporter 接口的自适应拓展类代码生成过程举例说明。首先看一下 Transporter 接口的定义，如下：
        return code.toString();
    }

    /**
     * generate code for variable extName null check
     */
    private String generateExtNameNullCheck(String[] value) {
        // eg: if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
        return String.format(CODE_EXT_NAME_NULL_CHECK, type.getName(), Arrays.toString(value));
    }

    /**
     * generate extName assigment code
     */
    private String generateExtNameAssignment(String[] value, boolean hasInvocation) {
        // TODO: refactor it
        String getNameCode = null;
        // 遍历 value，这里的 value 是 Adaptive 的注解值，2.2.3.3 节分析过 value 变量的获取过程。
        // 此处循环目的是生成从 URL 中获取拓展名的代码，生成的代码会赋值给 getNameCode 变量。注意这
        // 个循环的遍历顺序是由后向前遍历的。
        for (int i = value.length - 1; i >= 0; --i) {
            // 当 i 为最后一个元素的坐标时
            if (i == value.length - 1) {
                // 默认拓展名非空
                if (null != defaultExtName) {
                    // protocol 是 url 的一部分，可通过 getProtocol 方法获取，其他的则是从
                    // URL 参数中获取。因为获取方式不同，所以这里要判断 value[i] 是否为 protocol
                    if (!"protocol".equals(value[i])) {
                        // hasInvocation 用于标识方法参数列表中是否有 Invocation 类型参数
                        if (hasInvocation) {
                            // 生成的代码功能等价于下面的代码：
                            //   url.getMethodParameter(methodName, value[i], defaultExtName)
                            // 以 LoadBalance 接口的 select 方法为例，最终生成的代码如下：
                            //   url.getMethodParameter(methodName, "loadbalance", "random")
                            getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        } else {
                            // 生成的代码功能等价于下面的代码：
                            //   url.getParameter(value[i], defaultExtName)
                            getNameCode = String.format("url.getParameter(\"%s\", \"%s\")", value[i], defaultExtName);
                        }
                    } else {
                        // 生成的代码功能等价于下面的代码：
                        //   ( url.getProtocol() == null ? defaultExtName : url.getProtocol() )
                        getNameCode = String.format("( url.getProtocol() == null ? \"%s\" : url.getProtocol() )", defaultExtName);
                    }
                    // 默认拓展名为空
                } else {
                    if (!"protocol".equals(value[i])) {
                        if (hasInvocation) {
                            // 生成代码格式同上
                            getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        } else {
                            // 生成的代码功能等价于下面的代码：
                            //   url.getParameter(value[i])
                            getNameCode = String.format("url.getParameter(\"%s\")", value[i]);
                        }
                    } else {
                        // 生成从 url 中获取协议的代码，比如 "dubbo"
                        getNameCode = "url.getProtocol()";
                    }
                }
            } else {
                if (!"protocol".equals(value[i])) {
                    if (hasInvocation) {
                        // 生成代码格式同上
                        getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                    } else {
                        // 生成的代码功能等价于下面的代码：
                        //   url.getParameter(value[i], getNameCode)
                        // 以 Transporter 接口的 connect 方法为例，最终生成的代码如下：
                        //   url.getParameter("client", url.getParameter("transporter", "netty"))
                        getNameCode = String.format("url.getParameter(\"%s\", %s)", value[i], getNameCode);
                    }
                } else {
                    // 生成的代码功能等价于下面的代码：
                    //   url.getProtocol() == null ? getNameCode : url.getProtocol()
                    // 以 Protocol 接口的 connect 方法为例，最终生成的代码如下：
                    //   url.getProtocol() == null ? "dubbo" : url.getProtocol()
                    getNameCode = String.format("url.getProtocol() == null ? (%s) : url.getProtocol()", getNameCode);
                }
            }
        }
        // 生成 extName 赋值代码
        // eg :String extName = url.getParameter("add.ext1", "impl1"); ---->AddExt1接口的生成的Adaptive扩展类的echo方法内部会获取默认的(impl1)扩展类
        //     String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
        //     String extName = url.getMethodParameter(methodName, "loadbalance", "random")
        return String.format(CODE_EXT_NAME_ASSIGNMENT, getNameCode);
    }

    /**
     * @return
     */
    private String generateExtensionAssignment() {

        // 生成拓展获取代码，格式如下：
        // type全限定名 extension = (type全限定名)ExtensionLoader全限定名
        //     .getExtensionLoader(type全限定名.class).getExtension(extName);
        // Tips: 格式化字符串中的 %<s 表示使用前一个转换符所描述的参数，即 type 全限定名
        return String.format(CODE_EXTENSION_ASSIGNMENT, type.getName(), ExtensionLoader.class.getSimpleName(), type.getName());
        // eg:org.apache.dubbo.rpc.Protocol extension = (org.apache.dubbo.rpc.Protocol)ExtensionLoader.
        //                              getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension(extName);
    }

    /**
     * generate method invocation statement and return it if necessary
     */
    private String generateReturnAndInvocation(Method method) {

        // 如果方法返回值类型非 void，则生成 return 语句。
        String returnStatement = method.getReturnType().equals(void.class) ? "" : "return ";

        String args = IntStream.range(0, method.getParameters().length)
                .mapToObj(i -> String.format(CODE_EXTENSION_METHOD_INVOKE_ARGUMENT, i))
                .collect(Collectors.joining(", "));

        // 生成目标方法调用逻辑（有点代理的意思），格式为：
        //     extension.方法名(arg0, arg2, ..., argN);

        // eg:Protocol的refer方法，return extension.refer(arg0, arg1);
        // eg:AddExt1接口的echo方法，return extension.echo(arg0, arg1);
        return returnStatement + String.format("extension.%s(%s);\n", method.getName(), args);
    }

    /**
     * test if method has argument of type <code>Invocation</code>
     */
    //
    private boolean hasInvocationArgument(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        // 遍历参数类型列表,判断当前参数名称是否等于 com.alibaba.dubbo.rpc.Invocation
        return Arrays.stream(pts).anyMatch(p -> CLASSNAME_INVOCATION.equals(p.getName()));
    }

    /**
     * generate code to test argument of type <code>Invocation</code> is null
     */
    private String generateInvocationArgumentNullCheck(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        // 遍历参数类型列表,选出参数名称等于 com.alibaba.dubbo.rpc.Invocation
        // 为 Invocation 类型参数生成判空代码
        // 生成 getMethodName 方法调用代码，格式为：String methodName = argN.getMethodName();
        return IntStream.range(0, pts.length).filter(i -> CLASSNAME_INVOCATION.equals(pts[i].getName()))
                        .mapToObj(i -> String.format(CODE_INVOCATION_ARGUMENT_NULL_CHECK, i, i))
                        .findFirst().orElse("");
    }


    /**
     * get value of adaptive annotation or if empty return splitted simple name
     */
    // Adaptive 注解值 value 类型为 String[]，可填写多个值，默认情况下为空数组。若 value 为非空数组，直接获取数组内容即可。
    // 若 value 为空数组，则需进行额外处理。处理过程是将类名转换为字符数组，然后遍历字符数组，并将字符放入 StringBuilder 中。
    // 若字符为大写字母，则向 StringBuilder 中添加点号，随后将字符变为小写存入 StringBuilder 中。比如 LoadBalance 经过处理后，
    // 得到 load.balance。
    private String[] getMethodAdaptiveValue(Adaptive adaptiveAnnotation) {
        String[] value = adaptiveAnnotation.value();
        // value is not set, use the value generated from class name as the key
        if (value.length == 0) {
            // 获取类名，并将类名转换为字符传数组（比如AddExt1，返回的就是add.ext1），camelToSplitName，驼峰切割，进去
            String splitName = StringUtils.camelToSplitName(type.getSimpleName(), ".");
            value = new String[]{splitName};
        }
        return value;
    }

    /**
     * get parameter with type <code>URL</code> from method parameter:
     * <p>
     * test if parameter has method which returns type <code>URL</code>
     * <p>
     * if not found, throws IllegalStateException
     */
    private String generateUrlAssignmentIndirectly(Method method) {

        Class<?>[] pts = method.getParameterTypes();

        Map<String, Integer> getterReturnUrl = new HashMap<>();
        // 遍历方法的参数类型列表 to find URL getter method
        for (int i = 0; i < pts.length; ++i) {
            // 获取某一类型参数的全部方法。 遍历方法列表，寻找可返回 URL 的 getter 方法
            for (Method m : pts[i].getMethods()) {
                // 1. 方法名以 get 开头，或方法名大于3个字符
                // 2. 方法的访问权限为 public
                // 3. 非静态方法
                // 4. 方法参数数量为0
                // 5. 方法返回值类型为 URL
                String name = m.getName();
                if ((name.startsWith("get") || name.length() > 3)
                        && Modifier.isPublic(m.getModifiers())
                        && !Modifier.isStatic(m.getModifiers())
                        && m.getParameterTypes().length == 0
                        && m.getReturnType() == URL.class) {
                    // 填充到容器（name一般是getUrl）
                    getterReturnUrl.put(name, i);
                }
            }
        }

        // 如果所有参数中均不包含可返回 URL 的 getter 方法，则抛出异常
        if (getterReturnUrl.size() <= 0) {
            // getter method not found, throw
            throw new IllegalStateException("Failed to create adaptive class for interface " + type.getName()
                    + ": not found url parameter or url attribute in parameters of method " + method.getName());
        }


        Integer index = getterReturnUrl.get("getUrl");
        if (index != null) {
            // 进去
            return generateGetUrlNullCheck(index, pts[index], "getUrl");
        } else {
            Map.Entry<String, Integer> entry = getterReturnUrl.entrySet().iterator().next();
            return generateGetUrlNullCheck(entry.getValue(), pts[entry.getValue()], entry.getKey());
        }

        //  generateGetUrlNullCheck内部代码有点多，需要耐心看一下。这段代码主要目的是为了获取 URL 数据，并为之生成判空和赋值代码。
        //  以 Protocol 的 refer 和 export 方法为例，上面的代码为它们生成如下内容（代码已格式化）
        /*
            refer:
            if (arg1 == null)
                throw new IllegalArgumentException("url == null");
            com.alibaba.dubbo.common.URL url = arg1;

            export:
            if (arg0 == null)
                throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
            if (arg0.getUrl() == null)
                throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
            com.alibaba.dubbo.common.URL url = arg0.getUrl();
        */
    }

    /**
     * 1, test if argi is null
     * 2, test if argi.getXX() returns null
     * 3, assign url with argi.getXX()
     */


    private String generateGetUrlNullCheck(int index, Class<?> type, String method) {

        // 1. 为可返回 URL 的参数生成判空代码，格式如下：
        // if (arg + urlTypeIndex == null)
        //     throw new IllegalArgumentException("参数全限定名 + argument == null");
        // Null point check
        StringBuilder code = new StringBuilder();
        code.append(String.format("if (arg%d == null) throw new IllegalArgumentException(\"%s argument == null\");\n",
                index, type.getName()));
        // 2. 为 getter 方法返回的 URL 生成判空代码，格式如下：
        // if (argN.getter方法名() == null)
        //     throw new IllegalArgumentException(参数全限定名 + argument getUrl() == null);
        code.append(String.format("if (arg%d.%s() == null) throw new IllegalArgumentException(\"%s argument %s() == null\");\n",
                index, method, type.getName(), method));
        // 3. 生成赋值语句，格式如下：
        // URL全限定名 url = argN.getter方法名()，比如
        // com.alibaba.dubbo.common.URL url = invoker.getUrl();
        code.append(String.format("%s url = arg%d.%s();\n", URL.class.getName(), index, method));
        return code.toString();
        // 以Protocol的export为例，生成如下:
        //            if (arg0 == null)
        //                throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
        //            if (arg0.getUrl() == null)
        //                throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
        //            com.alibaba.dubbo.common.URL url = arg0.getUrl();
    }

}
