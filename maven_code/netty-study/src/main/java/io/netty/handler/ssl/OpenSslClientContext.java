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

import org.apache.tomcat.jni.SSL;

import io.netty.util.ReferenceCounted;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;


/**
 * A client-side {@link SslContext} which uses OpenSSL's SSL/TLS implementation.
 * <p>This class will use a finalizer to ensure native resources are automatically cleaned up. To avoid finalizers
 * and manually release the native memory see {@link ReferenceCountedOpenSslClientContext}.
 */
public final class OpenSslClientContext extends OpenSslContext {

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
	}}
