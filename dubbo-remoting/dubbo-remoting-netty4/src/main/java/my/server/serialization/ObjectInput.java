package my.server.serialization;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * @author gy821075
 * @date 2021/1/29 13:17
 */
public interface ObjectInput extends DataInput {
    Object readObject() throws IOException;

    <T> T readObject(Class<T> clz) throws IOException;

    <T> T readObject(Class<T> clz, Type type) throws IOException;

    String readUTF() throws IOException;


    default Map<String, Object> readAttachments() throws IOException {
        return readObject(Map.class);
    }

    default Throwable readThrowable() throws IOException {
        Object obj = readObject();
        if (!(obj instanceof Throwable)) {
            throw new IOException("Response data error, expect Throwable, but get " + obj);
        }
        return (Throwable) obj;
    }
}
