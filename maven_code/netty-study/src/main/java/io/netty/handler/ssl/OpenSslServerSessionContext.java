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

import java.util.Enumeration;

import javax.net.ssl.SSLSession;

import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;


/**
 * {@link OpenSslSessionContext} implementation which offers extra methods which are only useful for the server-side.
 */
public final class OpenSslServerSessionContext extends OpenSslSessionContext {

	@Override
	public Enumeration<byte[]> getIds() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SSLSession getSession(byte[] sessionId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getSessionCacheSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getSessionTimeout() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setSessionCacheSize(int size) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setSessionTimeout(int seconds) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		
	}}
