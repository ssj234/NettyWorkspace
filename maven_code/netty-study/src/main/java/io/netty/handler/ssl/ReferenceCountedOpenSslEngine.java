/*
 * Copyright 2016 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeak;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.SSL;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_WRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
import static javax.net.ssl.SSLEngineResult.Status.BUFFER_OVERFLOW;
import static javax.net.ssl.SSLEngineResult.Status.CLOSED;
import static javax.net.ssl.SSLEngineResult.Status.OK;

/**
 * Implements a {@link SSLEngine} using
 * <a href="https://www.openssl.org/docs/crypto/BIO_s_bio.html#EXAMPLE">OpenSSL BIO abstractions</a>.
 * <p>Instances of this class must be {@link #release() released} or else native memory will leak!
 *
 * <p>Instances of this class <strong>must</strong> be released before the {@link ReferenceCountedOpenSslContext}
 * the instance depends upon are released. Otherwise if any method of this class is called which uses the
 * the {@link ReferenceCountedOpenSslContext} JNI resources the JVM may crash.
 */
public class ReferenceCountedOpenSslEngine extends SSLEngine implements ReferenceCounted {

	@Override
	public int refCnt() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ReferenceCounted retain() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ReferenceCounted retain(int increment) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ReferenceCounted touch() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ReferenceCounted touch(Object hint) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean release() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean release(int decrement) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void beginHandshake() throws SSLException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void closeInbound() throws SSLException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void closeOutbound() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Runnable getDelegatedTask() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean getEnableSessionCreation() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String[] getEnabledCipherSuites() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getEnabledProtocols() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public HandshakeStatus getHandshakeStatus() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean getNeedClientAuth() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public SSLSession getSession() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getSupportedCipherSuites() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getSupportedProtocols() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean getUseClientMode() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean getWantClientAuth() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isInboundDone() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isOutboundDone() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setEnableSessionCreation(boolean flag) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setEnabledCipherSuites(String[] suites) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setEnabledProtocols(String[] protocols) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setNeedClientAuth(boolean need) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setUseClientMode(boolean mode) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setWantClientAuth(boolean want) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) throws SSLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) throws SSLException {
		// TODO Auto-generated method stub
		return null;
	}}

