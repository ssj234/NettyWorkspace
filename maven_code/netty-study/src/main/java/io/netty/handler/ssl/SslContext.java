/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.util.internal.EmptyArrays;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;

/**
 * A secure socket protocol implementation which acts as a factory for {@link SSLEngine} and {@link SslHandler}.
 * Internally, it is implemented via JDK's {@link SSLContext} or OpenSSL's {@code SSL_CTX}.
 *
 * <h3>Making your server support SSL/TLS</h3>
 * <pre>
 * // In your {@link ChannelInitializer}:
 * {@link ChannelPipeline} p = channel.pipeline();
 * {@link SslContext} sslCtx = {@link SslContextBuilder#forServer(File, File) SslContextBuilder.forServer(...)}.build();
 * p.addLast("ssl", {@link #newHandler(ByteBufAllocator) sslCtx.newHandler(channel.alloc())});
 * ...
 * </pre>
 *
 * <h3>Making your client support SSL/TLS</h3>
 * <pre>
 * // In your {@link ChannelInitializer}:
 * {@link ChannelPipeline} p = channel.pipeline();
 * {@link SslContext} sslCtx = {@link SslContextBuilder#forClient() SslContextBuilder.forClient()}.build();
 * p.addLast("ssl", {@link #newHandler(ByteBufAllocator, String, int) sslCtx.newHandler(channel.alloc(), host, port)});
 * ...
 * </pre>
 */
public abstract class SslContext {}
