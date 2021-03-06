package my.server;


import my.server.serialization.Serialization;
import my.server.serialization.fastjson.FastJsonSerialization;
import my.server.serialization.ObjectInput;
import my.server.serialization.ObjectOutput;
import my.server.serialization.hessian2.Hessian2Serialization;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author geyu
 * @date 2021/1/28 19:43
 */
public class ExchangeCodec extends AbstractCodec {

    private static final int HEADER_LENGTH = 16;

    private static final short MAGIC = (short) 0xdabb;

    private static final byte MAGIC_HIGH = Bytes.short2bytes(MAGIC)[0];

    private static final byte MAGIC_LOW = Bytes.short2bytes(MAGIC)[0];

    protected static final byte FLAG_REQUEST = (byte) 0x80;

    protected static final byte FLAG_TWOWAY = (byte) 0x40;

    protected static final byte FLAG_EVENT = (byte) 0x20;

    protected static final int SERIALIZATION_MASK = 0x1f;

    protected static Serialization serialization;

    private final URL url;

    public ExchangeCodec(URL url) {
        this.url = url;
        String serializationExt = url.getParameter("serialization", "hessian2");
        if (serializationExt.equalsIgnoreCase("fastjson")) {  // todo myRPC 支持SPI
            serialization = new FastJsonSerialization();
        } else {
            serialization = new Hessian2Serialization();
        }
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public void encode(ChannelBuffer buffer, Object msg) throws IOException {
        if (msg instanceof Request) {
            encodeRequest(buffer, (Request) msg);
        } else if (msg instanceof Response) {
            encodeResponse(buffer, (Response) msg);
        } else {
            // todo myRPC
        }
    }

    @Override
    public Object decode(ChannelBuffer buffer) throws IOException {
        int readable = buffer.readableBytes();
        byte[] header = new byte[Math.min(readable, HEADER_LENGTH)];
        buffer.readBytes(header);
        if (readable < HEADER_LENGTH) {
            return DecodeResult.NEED_MORE_INPUT;
        }
        int len = Bytes.bytes2int(header, 12);
        checkPayLoad(len);
        if (readable < len + HEADER_LENGTH) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        // todo myRPC 非魔数
        try {
            return decodeBody(buffer, header, len);
        } finally {

        }

    }

    protected Object decodeBody(ChannelBuffer buffer, byte[] header, int len) throws IOException {
        ChannelBufferInputStream bis = new ChannelBufferInputStream(buffer, len);
        ObjectInput input = serialization.deSerialize(bis);

        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        long id = Bytes.bytes2long(header, 4);
        if ((flag & FLAG_REQUEST) == 0) { // todo myRPC CodecSupport
            Response response = new Response(id);
            if ((flag & FLAG_EVENT) != 0) {
                response.setEvent(true);
            }
            byte status = header[3];
            response.setStatus(status);
            if (status == Response.OK) {
                Object data;
                if (response.isHeartbeat()) {
                    data = decodeHeartbeatData(input);
                } else if (response.isEvent()) {
                    data = decodeEventData(input);
                } else {
                    data = decodeResponseData(input, response, bis, proto); // 传response bis是为了给子类用的，在当前类没有实际意义
                }
                response.setResult(data);
            } else {
                response.setErrorMessage(input.readUTF());
            }
            return response;
        } else {
            Request request = new Request(id);
            request.setVersion(Version.DEFAULT_VERSION);
            request.setTwoWay((flag & FLAG_TWOWAY) != 0);
            request.isEvent((flag & FLAG_EVENT) != 0);
            Object data;
            if (request.isHeartbeat()) {
                data = decodeHeartbeatData(input);
            } else if (request.isEvent()) {
                data = decodeEventData(input);
            } else {
                data = decodeRequestData(input, request, bis, proto);// 传request bis 是为了给子类用的，在当前类没有实际意义
            }
            request.setData(data);
            return request;
        }
    }

    /**
     * byte 16
     * 0-1 magic code
     * 2 flag
     * 8 - 1-request/0-response
     * 7 - two way
     * 6 - heartbeat
     * 1-5 serialization id
     * 3 status
     * 20 ok
     * 90 error?
     * 4-11 id (long)
     * 12 -15 datalength
     */
    private void encodeResponse(ChannelBuffer buffer, Response response) throws IOException {
        int savedWriterIndex = buffer.writerIndex();
        try {
            byte[] header = new byte[HEADER_LENGTH];
            Bytes.short2bytes(MAGIC, header);

            header[2] = serialization.getContentTypeId();
            if (response.isHeartbeat()) {
                header[2] |= FLAG_EVENT;
            }
            byte status = response.getStatus();
            header[3] = status;
            Bytes.long2bytes(response.getId(), header, 4);


            buffer.writerIndex(savedWriterIndex + HEADER_LENGTH);

            ChannelBufferOutputStream bos = new ChannelBufferOutputStream(buffer);
            ObjectOutput output = serialization.serialize(bos);
            if (status == Response.OK) {
                if (response.isHeartbeat()) {
                    encodeEventData(output, response.getResult());
                } else {
                    encodeResponseDate(output, response.getResult());
                }
            } else {
                encodeErrorMsg(output, response.getErrorMessage());
            }
            output.flushBuffer();
            bos.flush();
            bos.close();
            int len = bos.writtenBytes();
            checkPayLoad(len);
            Bytes.int2Bytes(len, header, 12);
            buffer.writerIndex(savedWriterIndex);
            buffer.writerBytes(header);
            buffer.writerIndex(savedWriterIndex + HEADER_LENGTH + len);
        } catch (Throwable t) {
            // 下面这部分从原拷贝过来的，主要是测试服务端对没有实现序列化接口的对象进行encode的时候回抛异常，这里捕获异常传给消费者，先暂时不写这段 todo myRPC
//            buffer.writerIndex(savedWriterIndex);
//            if (!response.isEvent() && response.getStatus() != Response.BAD_RESPONSE) {
//                Response r = new Response(response.getId(), response.getVersion());
//                r.setStatus(Response.BAD_RESPONSE);
//
//                if (t instanceof ExceedPayloadLimitException) {
//                    try {
//                        r.setErrorMessage(t.getMessage());
//                        channel.send(r);
//                        return;
//                    } catch (RemotingException e) {
//                        System.out.println("Failed to send bad_response info back: " + t.getMessage() + ", cause: " + e.getMessage());
//                    }
//                } else {
//                    // FIXME log error message in Codec and handle in caught() of IoHanndler?
//                    System.out.println("Fail to encode response: " + res + ", send bad_response info instead, cause: " + t.getMessage(), t);
//                    try {
//                        r.setErrorMessage("Failed to send response: " + res + ", cause: " + StringUtils.toString(t));
//                        channel.send(r);
//                        return;
//                    } catch (RemotingException e) {
//                        logger.warn("Failed to send bad_response info back: " + res + ", cause: " + e.getMessage(), e);
//                    }
//                }
//            }
//            if (t instanceof IOException) {
//                throw (IOException) t;
//            } else if (t instanceof RuntimeException) {
//                throw (RuntimeException) t;
//            } else if (t instanceof Error) {
//                throw (Error) t;
//            } else {
//                throw new RuntimeException(t.getMessage(), t);
//            }
        }


    }


    /**
     * byte 16
     * 0-1 magic code
     * 2 flag
     * 8 - 1-request/0-response
     * 7 - two way
     * 6 - heartbeat
     * 1-5 serialization id
     * 3 status
     * 20 ok
     * 90 error?
     * 4-11 id (long)
     * 12 -15 datalength
     */
    private void encodeRequest(ChannelBuffer buffer, Request request) throws IOException {

        byte[] header = new byte[HEADER_LENGTH];
        Bytes.short2bytes(MAGIC, header);

        header[2] = (byte) (FLAG_REQUEST | serialization.getContentTypeId());
        if (request.isTwoWay()) {
            header[2] |= FLAG_TWOWAY;
        }
        if (request.isEvent()) {
            header[2] |= FLAG_EVENT;
        }
        Bytes.long2bytes(request.getId(), header, 4);
        int savedWriterIndex = buffer.writerIndex();
        buffer.writerIndex(savedWriterIndex + HEADER_LENGTH);

        ChannelBufferOutputStream bos = new ChannelBufferOutputStream(buffer);
        ObjectOutput output = serialization.serialize(bos);
        if (request.isEvent()) {
            encodeEventData(output, request.getData());
        } else {
            encodeRequestData(output, request.getData());
        }
        output.flushBuffer();
        //bos.flush(); 没必要
        //bos.close();
        int len = bos.writtenBytes(); // 体长度
        checkPayLoad(len);
        Bytes.int2Bytes(len, header, 12);
        buffer.writerIndex(savedWriterIndex);
        buffer.writerBytes(header);
        buffer.writerIndex(savedWriterIndex + HEADER_LENGTH + len);
    }

    protected void encodeRequestData(ObjectOutput output, Object data) throws IOException {
        output.writeObject(data);
    }

    protected void encodeResponseDate(ObjectOutput output, Object data) throws IOException {
        output.writeObject(data);
    }


    private void encodeEventData(ObjectOutput output, Object data) throws IOException {
        output.writeObject(data);
    }

    private void encodeErrorMsg(ObjectOutput output, String errorMessage) throws IOException {
        output.writeObject(errorMessage);
    }


    protected Object decodeRequestData(ObjectInput input, Request request, InputStream is, byte proto) throws IOException {
        return input.readObject();
    }

    protected Object decodeResponseData(ObjectInput input, Response response, InputStream is, byte proto) throws IOException {
        return input.readObject();
    }


    protected Object decodeEventData(ObjectInput input) throws IOException {
        return input.readObject();
    }

    protected Object decodeHeartbeatData(ObjectInput input) throws IOException {
        return input.readObject();
    }


}
