/*
 * Copyright 2005-2008 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.restlet.Server;
import org.restlet.data.Protocol;

/**
 * HTTP server helper based on NIO blocking sockets.
 * 
 * @author Jerome Louvel (contact@noelios.com)
 */
public class StreamServerHelper extends HttpServerHelper {

    /** The connection handler service. */
    private volatile ExecutorService handlerService;

    /** The socket listener service. */
    private volatile ExecutorService listenerService;

    /** The server socket channel. */
    private volatile ServerSocketChannel serverSocketChannel;

    /** The synchronization aid between listener and handler service. */
    private volatile CountDownLatch latch;

    /**
     * Constructor.
     * 
     * @param server
     *                The server to help.
     */
    public StreamServerHelper(Server server) {
        super(server);
        getProtocols().add(Protocol.HTTP);
    }

    @Override
    public synchronized void start() throws Exception {
        super.start();
        getLogger().info("Starting the internal HTTP server");

        ThreadFactory factory = new LoggingThreadFactory(getLogger());

        // Configure the thread services
        handlerService = Executors.newFixedThreadPool(10, factory);
        listenerService = Executors.newSingleThreadExecutor(factory);

        // Create the server socket
        serverSocketChannel = createServerSocket();

        // Sets the ephemeral port is necessary
        setEphemeralPort(serverSocketChannel.socket());

        // Start the socket listener service
        latch = new CountDownLatch(1);
        listenerService.submit(new Listener(this, serverSocketChannel, latch,
                handlerService));

        // Wait for the listener to start up and count down the latch
        // This blocks until the server is ready to receive connections
        try {
            latch.await();
        } catch (InterruptedException ex) {
            getLogger()
                    .log(
                            Level.WARNING,
                            "Interrupted while waiting for starting latch. Stopping...",
                            ex);
            stop();
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        super.stop();
        getLogger().info("Stopping the internal HTTP server");

        if (handlerService != null) {
            // Gracefully shutdown the handlers, they should complete
            // in a timely fashion
            handlerService.shutdown();
            try {
                handlerService.awaitTermination(Long.MAX_VALUE,
                        TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }

        if (listenerService != null) {
            // This must be forcefully interrupted because the thread
            // is most likely blocked on channel.accept()
            listenerService.shutdownNow();

            try {
                listenerService.awaitTermination(Long.MAX_VALUE,
                        TimeUnit.SECONDS);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Close the server socket
        if (serverSocketChannel != null) {
            serverSocketChannel.close();
        }
    }

    /**
     * Create a server socket channel and bind it to the given address
     * 
     * @return Bound server socket channel.
     * @throws IOException
     */
    protected ServerSocketChannel createServerSocket() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().bind(createSocketAddress());
        return server;
    }

    /**
     * Creates a socket address to listen on.
     * 
     * @return The created socket address.
     * @throws IOException
     */
    @SuppressWarnings("unused")
    protected SocketAddress createSocketAddress() throws IOException {
        if (getHelped().getAddress() == null) {
            return new InetSocketAddress(getHelped().getPort());
        } else {
            return new InetSocketAddress(getHelped().getAddress(), getHelped()
                    .getPort());
        }
    }

    /**
     * Class that handles an incoming socket.
     */
    private static class ConnectionHandler implements Runnable {

        /** The target server helper. */
        private final StreamServerHelper helper;

        /** The socket connection to handle. */
        private final Socket socket;

        /**
         * Constructor.
         * 
         * @param helper
         *                The target server helper.
         * @param socket
         *                The socket connection to handle.
         */
        private ConnectionHandler(StreamServerHelper helper, Socket socket) {
            this.helper = helper;
            this.socket = socket;
        }

        /**
         * Handles the given socket connection.
         */
        public void run() {
            try {
                helper.handle(new StreamServerCall(helper.getHelped(), socket
                        .getInputStream(), socket.getOutputStream(), socket));
            } catch (IOException ex) {
                helper.getLogger().log(Level.WARNING,
                        "Unexpected error while handling a call", ex);
            }
        }
    }

    /**
     * Listens on the given socket channel for incoming connections and
     * dispatches them to the given handler pool
     */
    private static class Listener implements Runnable {

        /** The target server helper. */
        private final StreamServerHelper helper;

        /** The server socket channel to listen on. */
        private final ServerSocketChannel serverSocket;

        /**
         * The latch to countdown when the socket is ready to accept
         * connections.
         */
        private final CountDownLatch latch;

        /** The handler service. */
        private final ExecutorService handlerService;

        /**
         * Constructor.
         * 
         * @param helper
         *                The target server helper.
         * @param serverSocket
         *                The server socket channel to listen on.
         * @param latch
         *                The latch to countdown when the socket is ready to
         *                accept connections.
         * @param handlerService
         *                The handler service.
         */
        private Listener(StreamServerHelper helper,
                ServerSocketChannel serverSocket, CountDownLatch latch,
                ExecutorService handlerService) {
            this.helper = helper;
            this.serverSocket = serverSocket;
            this.latch = latch;
            this.handlerService = handlerService;
        }

        /**
         * Listens on the given server socket for incoming connections.
         */
        public void run() {
            latch.countDown();
            for (;;) {
                try {
                    SocketChannel client = serverSocket.accept();
                    if (!handlerService.isShutdown()) {
                        handlerService.submit(new ConnectionHandler(helper,
                                client.socket()));
                    }
                } catch (ClosedByInterruptException ex) {
                    helper.getLogger().log(Level.FINE,
                            "ServerSocket channel was closed by interrupt", ex);
                    break;
                } catch (IOException ex) {
                    helper.getLogger().log(Level.WARNING,
                            "Unexpected error while accepting new connection",
                            ex);
                }
            }
        }
    }

    /**
     * Thread factory that logs uncaught exceptions thrown by the created
     * threads.
     */
    private static class LoggingThreadFactory implements ThreadFactory {

        private final Logger logger;

        public LoggingThreadFactory(Logger logger) {
            this.logger = logger;
        }

        public Thread newThread(Runnable r) {
            Thread result = new Thread(r);
            result.setUncaughtExceptionHandler(new LoggingExceptionHandler());
            return result;
        }

        private class LoggingExceptionHandler implements
                Thread.UncaughtExceptionHandler {

            public void uncaughtException(Thread t, Throwable ex) {
                logger.log(Level.SEVERE, "Thread: " + t.getName()
                        + " terminated with exception: " + ex.getMessage(), ex);
            }
        }
    }
}