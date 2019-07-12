/**
 * This file is part of Waarp Project.
 * <p>
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author tags. See the COPYRIGHT.txt in the
 * distribution for a full listing of individual contributors.
 * <p>
 * All Waarp Project is free software: you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * Waarp is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with Waarp .  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.waarp.commandexec.ssl.client.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.waarp.commandexec.ssl.client.LocalExecSslClientHandler;
import org.waarp.commandexec.ssl.client.LocalExecSslClientInitializer;
import org.waarp.commandexec.utils.LocalExecResult;
import org.waarp.common.crypto.ssl.WaarpSecureKeyStore;
import org.waarp.common.crypto.ssl.WaarpSslContextFactory;
import org.waarp.common.crypto.ssl.WaarpSslUtility;
import org.waarp.common.logging.WaarpLogLevel;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.logging.WaarpSlf4JLoggerFactory;
import org.waarp.common.utility.DetectionUtils;
import org.waarp.common.utility.WaarpNettyUtil;
import org.waarp.common.utility.WaarpThreadFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LocalExecSsl client.
 * <p>
 * This class is an example of client.
 * <p>
 * No client authentication On a bi-core Centrino2 vPro: 5/s in 50 sequential, 29/s in 10 threads with 50 sequential<br>
 * With client authentication On a bi-core Centrino2 vPro: 3/s in 50 sequential, 27/s in 10 threads with 50
 * sequential<br> No client authentication On a quad-core i7: 20/s in 50 sequential, 178/s in 10 threads with 50
 * sequential<br> With client authentication On a quad-core i7: 17/s in 50 sequential, 176/s in 10 threads with 50
 * sequential<br>
 */
public class LocalExecSslClientTest extends Thread {

    static int nit = 50;
    static int nth = 10;
    static String command = "/opt/R66/testexec.sh";
    static int port = 9999;
    static InetSocketAddress address;
    // with client authentication
    static String keyStoreFilename = "/opt/R66/AllJarsWaarpR66-2.4.28-2/config/certs/testclient2.jks";
    // without client authentication
    // static String keyStoreFilename = null;
    static String keyStorePasswd = "testclient2";
    static String keyPasswd = "client2";
    static String keyTrustStoreFilename = "/opt/R66/AllJarsWaarpR66-2.4.28-2/config/certs/testclient.jks";
    static String keyTrustStorePasswd = "testclient";
    static LocalExecResult result;

    static int ok = 0;
    static int ko = 0;
    static AtomicInteger atomicInteger = new AtomicInteger();

    static EventLoopGroup workerGroup = new NioEventLoopGroup();
    static EventExecutorGroup executor = new DefaultEventExecutorGroup(DetectionUtils.numberThreads(),
                                                                       new WaarpThreadFactory("LocalExecServer"));
    // Configure the client.
    static Bootstrap bootstrap;
    // Configure the pipeline factory.
    static LocalExecSslClientInitializer localExecClientInitializer;
    private Channel channel;

    /**
     * Simple constructor
     */
    public LocalExecSslClientTest() {
    }

    /**
     * Test & example main
     *
     * @param args ignored
     *
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        WaarpLoggerFactory.setDefaultFactory(new WaarpSlf4JLoggerFactory(
                WaarpLogLevel.WARN));
        InetAddress addr;
        byte[] loop = { 127, 0, 0, 1 };
        try {
            addr = InetAddress.getByAddress(loop);
        } catch (UnknownHostException e) {
            return;
        }
        address = new InetSocketAddress(addr, port);
        // Configure the client.
        bootstrap = new Bootstrap();
        WaarpNettyUtil.setBootstrap(bootstrap, workerGroup, 30000);
        // Configure the pipeline factory.
        // First create the SSL part
        WaarpSecureKeyStore WaarpSecureKeyStore;
        // For empty KeyStore
        if (keyStoreFilename == null) {
            WaarpSecureKeyStore =
                    new WaarpSecureKeyStore(keyStorePasswd, keyPasswd);
        } else {
            WaarpSecureKeyStore =
                    new WaarpSecureKeyStore(keyStoreFilename, keyStorePasswd, keyPasswd);
        }

        if (keyTrustStoreFilename != null) {
            // Load the client TrustStore
            WaarpSecureKeyStore.initTrustStore(keyTrustStoreFilename, keyTrustStorePasswd, false);
        } else {
            WaarpSecureKeyStore.initEmptyTrustStore();
        }
        WaarpSslContextFactory waarpSslContextFactory = new WaarpSslContextFactory(WaarpSecureKeyStore, false);
        localExecClientInitializer =
                new LocalExecSslClientInitializer(waarpSslContextFactory);
        bootstrap.handler(localExecClientInitializer);

        try {
            // Parse options.
            LocalExecSslClientTest client = new LocalExecSslClientTest();
            // run once
            long first = System.currentTimeMillis();
            if (client.connect()) {
                client.runOnce();
                client.disconnect();
            }
            long second = System.currentTimeMillis();
            // print time for one exec
            System.err.println("1=Total time in ms: " + (second - first) + " or " + (1 * 1000 / (second - first))
                               + " exec/s");
            System.err.println("Result: " + ok + ":" + ko);
            ok = 0;
            ko = 0;

            // Now run multiple within one thread
            first = System.currentTimeMillis();
            for (int i = 0; i < nit; i++) {
                if (client.connect()) {
                    client.runOnce();
                    client.disconnect();
                }
            }
            second = System.currentTimeMillis();
            // print time for one exec
            System.err.println(nit + "=Total time in ms: " + (second - first) + " or "
                               + (nit * 1000 / (second - first)) + " exec/s");
            System.err.println("Result: " + ok + ":" + ko);
            ok = 0;
            ko = 0;

            // Now run multiple within multiple threads
            // Create multiple threads
            ExecutorService executorService = Executors.newFixedThreadPool(nth);
            first = System.currentTimeMillis();
            // Starts all thread with a default number of execution
            for (int i = 0; i < nth; i++) {
                executorService.submit(new LocalExecSslClientTest());
            }
            Thread.sleep(500);
            executorService.shutdown();
            while (!executorService.awaitTermination(200, TimeUnit.MILLISECONDS)) {
                Thread.sleep(50);
            }
            second = System.currentTimeMillis();

            // print time for one exec
            System.err.println((nit * nth) + "=Total time in ms: " + (second - first) + " or "
                               + (nit * nth * 1000 / (second - first)) + " exec/s");
            System.err.println("Result: " + ok + ":" + ko);
            ok = 0;
            ko = 0;

            // run once
            first = System.currentTimeMillis();
            if (client.connect()) {
                client.runFinal();
                client.disconnect();
            }
            second = System.currentTimeMillis();
            // print time for one exec
            System.err.println("1=Total time in ms: " + (second - first) + " or " + (1 * 1000 / (second - first))
                               + " exec/s");
            System.err.println("Result: " + ok + ":" + ko);
            ok = 0;
            ko = 0;
        } finally {
            // Shut down all thread pools to exit.
            workerGroup.shutdownGracefully();
            localExecClientInitializer.releaseResources();
        }
    }

    /**
     * Run method for thread
     */
    public void run() {
        if (connect()) {
            for (int i = 0; i < nit; i++) {
                this.runOnce();
            }
            disconnect();
        }
    }

    /**
     * Connect to the Server
     */
    private boolean connect() {
        // Start the connection attempt.
        ChannelFuture future = bootstrap.connect(address);

        // Wait until the connection attempt succeeds or fails.
        channel = WaarpSslUtility.waitforChannelReady(future);
        if (channel == null) {
            System.err.println("Client Not Connected");
            if (future.cause() != null) {
                future.cause().printStackTrace();
            }
            return false;
        }
        return true;
    }

    /**
     * Disconnect from the server
     */
    private void disconnect() {
        WaarpSslUtility.closingSslChannel(channel);
        WaarpSslUtility.waitForClosingSslChannel(channel, 10000);
    }

    /**
     * Run method both for not threaded execution and threaded execution
     */
    private void runOnce() {
        // Initialize the command context
        LocalExecSslClientHandler clientHandler =
                (LocalExecSslClientHandler) channel.pipeline().last();
        // Command to execute
        String line = command + " " + atomicInteger.incrementAndGet();
        clientHandler.initExecClient(0, line);
        // Wait for the end of the exec command
        LocalExecResult localExecResult = clientHandler.waitFor(10000);
        int status = localExecResult.getStatus();
        if (status < 0) {
            System.err.println("Status: " + status + "\nResult: " +
                               localExecResult.getResult());
            ko++;
        } else {
            ok++;
            result = localExecResult;
        }
    }

    /**
     * Run method for closing Server
     */
    private void runFinal() {
        // Initialize the command context
        LocalExecSslClientHandler clientHandler =
                (LocalExecSslClientHandler) channel.pipeline().last();
        // Command to execute
        clientHandler.initExecClient(-1000, "stop");
        // Wait for the end of the exec command
        LocalExecResult localExecResult = clientHandler.waitFor(10000);
        int status = localExecResult.getStatus();
        if (status < 0) {
            System.err.println("Status: " + status + "\nResult: " +
                               localExecResult.getResult());
            ko++;
        } else {
            ok++;
            result = localExecResult;
        }
    }
}
