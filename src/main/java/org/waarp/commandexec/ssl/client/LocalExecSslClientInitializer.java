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
package org.waarp.commandexec.ssl.client;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslHandler;
import org.waarp.commandexec.client.LocalExecClientInitializer;
import org.waarp.common.crypto.ssl.WaarpSslContextFactory;
import org.waarp.common.utility.WaarpStringUtils;

/**
 * Version with SSL support
 *
 *
 * @author Frederic Bregier
 *
 */
public class LocalExecSslClientInitializer extends LocalExecClientInitializer {

    private final WaarpSslContextFactory waarpSslContextFactory;

    public LocalExecSslClientInitializer(WaarpSslContextFactory waarpSslContextFactory) {
        this.waarpSslContextFactory = waarpSslContextFactory;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();

        // Add SSL as first element in the pipeline
        SslHandler sslhandler = waarpSslContextFactory.initInitializer(false,
                                                                       waarpSslContextFactory
                                                                               .needClientAuthentication());
        //sslhandler.setIssueHandshake(true);
        pipeline.addLast("ssl", sslhandler);
        // Add the text line codec combination first,
        pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192,
                                                                  Delimiters.lineDelimiter()));
        pipeline.addLast("decoder", new StringDecoder(WaarpStringUtils.UTF8));
        pipeline.addLast("encoder", new StringEncoder(WaarpStringUtils.UTF8));

        // and then business logic.
        LocalExecSslClientHandler localExecClientHandler = new LocalExecSslClientHandler(this);
        pipeline.addLast("handler", localExecClientHandler);
    }

    /**
     * Release internal resources
     */
    public void releaseResources() {
        super.releaseResources();
    }

}
