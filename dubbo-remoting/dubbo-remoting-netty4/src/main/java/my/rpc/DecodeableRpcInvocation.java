package my.rpc;

import jdk.internal.util.xml.impl.Input;
import my.common.rpc.model.ApplicationModel;
import my.common.rpc.model.MethodDescriptor;
import my.common.rpc.model.ServiceDescriptor;
import my.common.rpc.model.ServiceRepository;
import my.common.utils.ReflectUtils;
import my.server.CodecSupport;
import my.server.Request;
import my.server.serialization.ObjectInput;
import my.server.serialization.Serialization;
import org.apache.dubbo.common.utils.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static my.common.constants.CommonConstants.PATH_KEY;
import static my.common.constants.CommonConstants.VERSION_KEY;

/**
 * @author geyu
 * @date 2021/2/5 19:36
 */
public class DecodeableRpcInvocation extends RpcInvocation implements Decodeable {
    private final Request request;
    private AtomicBoolean decoded = new AtomicBoolean(false);
    private static Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    private static Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];

    private InputStream is;
    private byte proto;


    public DecodeableRpcInvocation(Request request, InputStream is, byte proto) {
        Assert.notNull(is, "is == null");
        Assert.notNull(request, "request == null");
        this.request = request;
        this.is = is;
        this.proto = proto;
    }

    public void decode() {
        if (decoded.compareAndSet(false, true) && is != null) {
            try {
                doDecode();
            } catch (Throwable e) {
                request.setBroken(true);
                request.setData(e);
            }
        }
    }

    private void doDecode() throws IOException, ClassNotFoundException {
        Serialization serialization = CodecSupport.getSerializationById(proto);
        ObjectInput input = serialization.deSerialize(is);
        String version = input.readUTF();
        String path = input.readUTF();
        String methodName = input.readUTF();
        String desc = input.readUTF();

        setAttachment(VERSION_KEY, version);
        setAttachment(PATH_KEY, path);
        setMethodName(methodName);
        setParameterTypesDesc(desc);


        Class<?>[] pts = EMPTY_CLASS_ARRAY;
        Object[] args = EMPTY_OBJECT_ARRAY;

        if (desc.length() > 0) {
            ServiceRepository repository = ApplicationModel.getServiceRepository();
            ServiceDescriptor serviceDescriptor = repository.lookupService(path);
            if (serviceDescriptor != null) {
                MethodDescriptor methodDescriptor = serviceDescriptor.getMethod(methodName, desc);
                if (methodDescriptor != null) {
                    pts = methodDescriptor.getParameterClasses();
                    setReturnTypes(methodDescriptor.getReturnTypes());
                }
            }
            if (pts == EMPTY_CLASS_ARRAY) {
                if (!RpcUtils.isGenericCall(desc, getMethodName()) && !RpcUtils.isEcho(desc, getMethodName())) {
                    throw new IllegalArgumentException("Service not found:" + path + ", " + getMethodName());
                }
                pts = ReflectUtils.desc2classArray(desc);
            }
            args = new Object[pts.length];
            for (int i = 0; i < args.length; i++) {
                try {
                    args[i] = input.readObject();
                } catch (Exception e) {
                    System.out.println("Decode argument failed: " + e.getMessage());
                }
            }
        }
        setParameterTypes(pts);
        setArguments(args);
        setObjectAttachments(input.readAttachments());

        // todo myRPC  decode argument ,may be callback

        setTargetServiceUniqueName(input.readUTF());
    }

}
