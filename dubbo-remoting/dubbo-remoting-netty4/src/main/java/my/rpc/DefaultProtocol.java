package my.rpc;

import my.server.*;

import java.util.LinkedList;
import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.LAZY_CONNECT_KEY;
import static org.apache.dubbo.remoting.Constants.*;

/**
 * @author geyu
 * @date 2021/2/4 11:46
 */
public class DefaultProtocol extends AbstractProtocol {

    private ExchangeHandler requestHandler = new DefaultExchangeHandler();

    @Override
    protected <T> Invoker<T> doRefer(Class<T> type, URL url) {
        DefaultInvoker<T> invoker = new DefaultInvoker<T>(type, url, getClients(url), invokerSet);
        invokerSet.add(invoker);
        return invoker;
    }

    private Client[] getClients(URL url) {
        int connections = url.getParameter(Constants.CONNECTIONS_KEY, 0);
        if (connections == 0) { // 1.isShared = true;
            int shardConnections = url.getParameter(Constants.SHARE_CONNECTIONS_KEY, 1);
            List<ReferenceCountClient> referenceCountClientList = getReferenceCountClientList(url, shardConnections < 0 ? 1 : shardConnections);
            return referenceCountClientList.toArray(new Client[0]);
        } else { // 2.isShared = false;
            Client[] clients = new Client[connections];
            for (int i = 0; i < connections; i++) {
                clients[i] = initClient(url);
            }
            return clients;
        }
    }

    private Client initClient(URL url) {
        Client client = null;
        try {
//            String str = url.getParameter(CLIENT_KEY, url.getParameter(SERVER_KEY, DEFAULT_REMOTING_CLIENT));
//            if (str != null && str.length() > 0 && !ExtensionLoader.getExtensionLoader(org.apache.dubbo.remoting.Transporter.class).hasExtension(str)) {
//                throw new RpcException("Unsupported client type: " + str + "," +
//                        " supported client type is " + StringUtils.join(ExtensionLoader.getExtensionLoader(org.apache.dubbo.remoting.Transporter.class).getSupportedExtensions(), " "));
//            }
            url.addParameter(CODEC_KEY, DefaultCodec.NAME);
            url.addParameterIfAbsent(HEARTBEAT_KEY, String.valueOf(DEFAULT_HEARTBEAT));
            if (url.getParameter(LAZY_CONNECT_KEY, false)) {
                client = new LazyConnectClient(url, requestHandler);
            } else {
                client = transporter.connect(url, requestHandler);
            }
        } catch (RemotingException e) {
            e.printStackTrace();
        }
        return client;
    }

    private List<ReferenceCountClient> getReferenceCountClientList(URL url, int shardConnections) {
        String address = url.getAddress();
        List<ReferenceCountClient> referenceCountClients = referenceCountClientMap.get(address);
        if (checkClientCanUse(referenceCountClients)) {// 进去
            // 增加引用计数 进去
            batchClientRefIncr(referenceCountClients);
            return referenceCountClients;
        }
        locks.putIfAbsent(address, new Object());
        synchronized (locks.get(address)) {
            referenceCountClients = referenceCountClientMap.get(address);
            if (checkClientCanUse(referenceCountClients)) {// 进去
                // 增加引用计数 进去
                batchClientRefIncr(referenceCountClients);
                return referenceCountClients;
            }
            if(referenceCountClients == null || referenceCountClients.isEmpty()){
                List<ReferenceCountClient> clients = new LinkedList<>();
                for(int i=0;i<shardConnections;i++){
                    clients.add(new ReferenceCountClient(initClient(url)));
                }
                referenceCountClientMap.put(address,clients);
            }else{
//                for(ReferenceCountClient client : referenceCountClients){
//                    if()// todo myRPC
//                }

            }

        }
        return null;
    }

    private void batchClientRefIncr(List<ReferenceCountClient> referenceCountClients) {
        if (referenceCountClients == null || referenceCountClients.isEmpty()) {
            return;
        }
        for (ReferenceCountClient client : referenceCountClients) {
            client.incrementAndGetCount();
        }
    }

    private boolean checkClientCanUse(List<ReferenceCountClient> referenceCountClients) {
        if (referenceCountClients == null || referenceCountClients.isEmpty()) {
            return false;
        }
        for (ReferenceCountClient client : referenceCountClients) {
            if (client == null || !client.isConnected()) {
                return false;
            }
        }
        return true;
    }


    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RemotingException {
        URL url = invoker.getURL();
        String serviceKey = GroupServiceKeyCache.serviceKey(url);
        DefaultExporter defaultExporter = new DefaultExporter(invoker, serviceKey, exporterMap);
        exporterMap.putIfAbsent(serviceKey, defaultExporter);
        // todo myRPC 本地存根处理
        String address = url.getAddress();
        boolean isServer = url.getParameter(Constants.IS_SERVER_KEY, true);
        if (isServer) {
            Server server = serverMap.get(address);
            if (server == null) {
                serverMap.putIfAbsent(address, transporter.bind(url, requestHandler));// todo myRPC createServer的逻辑需要补充
            } else {
                // todo myRPC reset
            }
        }
        return defaultExporter;
    }

}
