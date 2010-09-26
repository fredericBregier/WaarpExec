/**
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author
 * tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3.0 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package goldengate.commandexec.client.test;

import goldengate.commandexec.client.LocalExecClientHandler;
import goldengate.commandexec.client.LocalExecClientPipelineFactory;
import goldengate.commandexec.utils.LocalExecResult;
import goldengate.common.logging.GgSlf4JLoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.logging.InternalLoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * LocalExec client.
 *
 * This class is an example of client.
 *
 * On a bi-core Centrino2 vPro: 18/s in 50 sequential, 30/s in 10 threads with 50 sequential
 */
public class LocalExecClient extends Thread {

    static int nit = 50;
    static int nth = 10;
    static String command = "d:\\GG\\testexec.bat";
    static int port = 9999;
    static InetSocketAddress address;

    static LocalExecResult result;
    static int ok = 0;
    static int ko = 0;

    static ExecutorService threadPool;
    static ExecutorService threadPool2;
    // Configure the client.
    static ClientBootstrap bootstrap;
    // Configure the pipeline factory.
    static LocalExecClientPipelineFactory localExecClientPipelineFactory;

    /**
     * Test & example main
     * @param args ignored
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        InternalLoggerFactory.setDefaultFactory(new GgSlf4JLoggerFactory(
                Level.WARN));
        InetAddress addr;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            return;
        }
        address = new InetSocketAddress(addr, port);
        threadPool = Executors.newCachedThreadPool();
        threadPool2 = Executors.newCachedThreadPool();
        // Configure the client.
        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(threadPool, threadPool2));
        // Configure the pipeline factory.
        localExecClientPipelineFactory =
                new LocalExecClientPipelineFactory();
        bootstrap.setPipelineFactory(localExecClientPipelineFactory);
        try {
            // Parse options.
            LocalExecClient client = new LocalExecClient();
            // run once
            long first = System.currentTimeMillis();
            client.connect();
            client.runOnce();
            client.disconnect();
            long second = System.currentTimeMillis();
            // print time for one exec
            System.err.println("1=Total time in ms: "+(second-first)+" or "+(1*1000/(second-first))+" exec/s");
            System.err.println("Result: " + ok+":"+ko);
            ok = 0;
            ko = 0;
            // Now run multiple within one thread
            first = System.currentTimeMillis();
            for (int i = 0; i < nit; i ++) {
                client.connect();
                client.runOnce();
                client.disconnect();
            }
            second = System.currentTimeMillis();
            // print time for one exec
            System.err.println(nit+"=Total time in ms: "+(second-first)+" or "+(nit*1000/(second-first))+" exec/s");
            System.err.println("Result: " + ok+":"+ko);
            ok = 0;
            ko = 0;
            // Now run multiple within multiple threads
            // Create multiple threads
            ExecutorService executorService = Executors.newFixedThreadPool(nth);
            first = System.currentTimeMillis();
            // Starts all thread with a default number of execution
            for (int i = 0; i < nth; i ++) {
                executorService.submit(new LocalExecClient());
            }
            Thread.sleep(500);
            executorService.shutdown();
            while (! executorService.awaitTermination(200, TimeUnit.MILLISECONDS)) {
                Thread.sleep(50);
            }
            second = System.currentTimeMillis();

            // print time for one exec
            System.err.println((nit*nth)+"=Total time in ms: "+(second-first)+" or "+(nit*nth*1000/(second-first))+" exec/s");
            System.err.println("Result: " + ok+":"+ko);
        } finally {
            // Shut down all thread pools to exit.
            bootstrap.releaseExternalResources();
            localExecClientPipelineFactory.releaseResources();
        }
    }

    /**
     * Simple constructor
     */
    public LocalExecClient() {
    }

    private Channel channel;
    /**
     * Run method for thread
     */
    public void run() {
        connect();
        for (int i = 0; i < nit; i ++) {
            this.runOnce();
        }
        disconnect();
    }

    /**
     * Connect to the Server
     */
    private void connect() {
        // Start the connection attempt.
        ChannelFuture future = bootstrap.connect(address);

        // Wait until the connection attempt succeeds or fails.
        channel = future.awaitUninterruptibly().getChannel();
        if (!future.isSuccess()) {
            System.err.println("Client Not Connected");
            future.getCause().printStackTrace();
            return;
        }
    }
    /**
     * Disconnect from the server
     */
    private void disconnect() {
     // Close the connection. Make sure the close operation ends because
        // all I/O operations are asynchronous in Netty.
        channel.close().awaitUninterruptibly();
    }

    /**
     * Run method both for not threaded execution and threaded execution
     */
    public void runOnce() {
     // Initialize the command context
        LocalExecClientHandler clientHandler =
            (LocalExecClientHandler) channel.getPipeline().getLast();
        clientHandler.initExecClient();
        // Command to execute

        ChannelFuture lastWriteFuture = null;
        String line = command+"\n";
        if (line != null) {
            // Sends the received line to the server.
            lastWriteFuture = channel.write(line);
            // Wait until all messages are flushed before closing the channel.
            if (lastWriteFuture != null) {
                lastWriteFuture.awaitUninterruptibly();
            }
            // Wait for the end of the exec command
            LocalExecResult localExecResult = clientHandler.waitFor();
            int status = localExecResult.status;
            if (status < 0) {
                System.err.println("Status: " + status + "\nResult: " +
                        localExecResult.result);
                ko++;
            } else {
                ok++;
                result = localExecResult;
            }
        }
    }
}
