package my.server.serialization;


import my.common.extension.SPI;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author gy821075
 * @date 2021/1/28 20:22
 */
@SPI
public interface Serialization {
    byte getContentTypeId();

    String getContentType();

    ObjectOutput serialize(OutputStream bos);

    ObjectInput deSerialize(InputStream bis);
}
