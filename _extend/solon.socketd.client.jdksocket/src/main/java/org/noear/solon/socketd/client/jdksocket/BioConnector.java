package org.noear.solon.socketd.client.jdksocket;

import org.noear.solon.Solon;
import org.noear.solon.Utils;
import org.noear.solon.core.message.Message;
import org.noear.solon.core.message.Session;
import org.noear.solon.socketd.ConnectorBase;
import org.noear.solon.socketd.SocketProps;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;

public class BioConnector extends ConnectorBase<Socket> {
    public BioConnector(URI uri, boolean autoReconnect) {
        super(uri, autoReconnect);
    }

    @Override
    public Class<Socket> driveType() {
        return Socket.class;
    }

    @Override
    public Socket open(Session session) {
        try {
            SocketAddress socketAddress = new InetSocketAddress(uri().getHost(), uri().getPort());
            Socket socket = new Socket();

            if (SocketProps.socketTimeout() > 0) {
                socket.setSoTimeout(SocketProps.socketTimeout());
            }

            if (SocketProps.connectTimeout() > 0) {
                socket.connect(socketAddress, SocketProps.connectTimeout());
            } else {
                socket.connect(socketAddress);
            }

            startReceive(session, socket);

            return socket;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    void startReceive(Session session, Socket socket) {
        Utils.pools.submit(() -> {
            while (true) {
                if (socket.isClosed()) {
                    Solon.global().listener().onClose(session);
                    break;
                }

                try {
                    Message message = BioReceiver.receive(socket);

                    if (message != null) {
                        Utils.pools.execute(() -> {
                            try {
                                Solon.global().listener().onMessage(session, message);
                            } catch (Throwable ex) {
                                Solon.global().listener().onError(session, ex);
                            }
                        });
                    }
                } catch (Exception ex) {
                    Solon.global().listener().onError(session, ex);
                }
            }
        });
    }
}
