/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package backtype.storm.messaging.netty;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SaslNettyServer {

	private static final Logger LOG = LoggerFactory
			.getLogger(SaslNettyServer.class);

	private SaslServer saslServer;

	SaslNettyServer(String topologyToken) throws IOException {
		LOG.debug("SaslNettyServer: Topology token is: " + topologyToken
				+ " with authmethod " + SaslUtils.AUTH_DIGEST_MD5);

		try {

			SaslDigestCallbackHandler ch = new SaslNettyServer.SaslDigestCallbackHandler(
					topologyToken);

			saslServer = Sasl.createSaslServer(SaslUtils.AUTH_DIGEST_MD5, null,
					SaslUtils.DEFAULT_REALM, SaslUtils.getSaslProps(), ch);

		} catch (SaslException e) {
			LOG.error("SaslNettyServer: Could not create SaslServer: " + e);
		}

	}

	public boolean isComplete() {
		return saslServer.isComplete();
	}

	public String getUserName() {
		return saslServer.getAuthorizationID();
	}

	

	/** CallbackHandler for SASL DIGEST-MD5 mechanism */
	public static class SaslDigestCallbackHandler implements CallbackHandler {

		/** Used to authenticate the clients */
		private String topologyToken;

		public SaslDigestCallbackHandler(String topologyToken) {
			LOG.debug("SaslDigestCallback: Creating SaslDigestCallback handler "
					+ "with topology token: " + topologyToken);
			this.topologyToken = topologyToken;
		}

		@Override
		public void handle(Callback[] callbacks) throws IOException,
				UnsupportedCallbackException {
			NameCallback nc = null;
			PasswordCallback pc = null;
			AuthorizeCallback ac = null;

			for (Callback callback : callbacks) {
				if (callback instanceof AuthorizeCallback) {
					ac = (AuthorizeCallback) callback;
				} else if (callback instanceof NameCallback) {
					nc = (NameCallback) callback;
				} else if (callback instanceof PasswordCallback) {
					pc = (PasswordCallback) callback;
				} else if (callback instanceof RealmCallback) {
					continue; // realm is ignored
				} else {
					throw new UnsupportedCallbackException(callback,
							"handle: Unrecognized SASL DIGEST-MD5 Callback");
				}
			}
			
			if(nc!=null) {
				LOG.debug("handle: SASL server DIGEST-MD5 callback: setting "
						+ "username for client: " + topologyToken);

				nc.setName(topologyToken);
			}

			if (pc != null) {
				char[] password = SaslUtils.encodePassword(topologyToken.getBytes());

				LOG.debug("handle: SASL server DIGEST-MD5 callback: setting "
						+ "password for client: " + topologyToken);

				pc.setPassword(password);
			}
			if (ac != null) {

				String authid = ac.getAuthenticationID();
				String authzid = ac.getAuthorizationID();

				if (authid.equals(authzid)) {
					ac.setAuthorized(true);
				} else {
					ac.setAuthorized(false);
				}

				if (ac.isAuthorized()) {
					if (LOG.isDebugEnabled()) {
						String username = topologyToken;
						LOG.debug("handle: SASL server DIGEST-MD5 callback: setting "
								+ "canonicalized client ID: " + username);
					}
					ac.setAuthorizedID(authzid);
				}
			}
		}
	}

	/**
	 * Used by SaslTokenMessage::processToken() to respond to server SASL
	 * tokens.
	 * 
	 * @param token
	 *            Server's SASL token
	 * @return token to send back to the server.
	 */
	public byte[] response(byte[] token) {
		try {
			LOG.debug("response: Responding to input token of length: "
					+ token.length);
			byte[] retval = saslServer.evaluateResponse(token);
			LOG.debug("response: Response token length: " + retval.length);
			return retval;
		} catch (SaslException e) {
			LOG.error("response: Failed to evaluate client token of length: "
					+ token.length + " : " + e);
			return null;
		}
	}
}