package netty.server.serilization;
import netty.server.serialization.ObjectInput;
import netty.server.serialization.ObjectOutput;
import netty.server.serialization.hessian2.Hessian2Serialization;
import netty.server.support.World;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author geyu
 * @date 2021/2/2 19:43
 */
public class Hessian2Test {
    @Test
    public void test() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Hessian2Serialization hessian2Serialization = new Hessian2Serialization();
        ObjectOutput objectOutput = hessian2Serialization.serialize(bos);
        objectOutput.writeObject(new World("haha"));
        objectOutput.flushBuffer();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInput objectInput = hessian2Serialization.deSerialize(bis);
        Object o = objectInput.readObject();
        System.out.println(o.getClass());
    }
}
