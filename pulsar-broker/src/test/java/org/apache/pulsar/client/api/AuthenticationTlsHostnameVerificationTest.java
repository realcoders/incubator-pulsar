/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.client.api;

import static org.mockito.Mockito.spy;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.util.PublicSuffixMatcher;
import org.apache.pulsar.broker.authentication.AuthenticationProviderBasic;
import org.apache.pulsar.broker.authentication.AuthenticationProviderTls;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.impl.auth.AuthenticationTls;
import org.apache.pulsar.common.policies.data.PropertyAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class AuthenticationTlsHostnameVerificationTest extends ProducerConsumerBase {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationTlsHostnameVerificationTest.class);

    // Man in middle certificate which tries to act as a broker by sending its own valid certificate
    private final String TLS_MIM_TRUST_CERT_FILE_PATH = "./src/test/resources/authentication/tls/hn-verification/cacert.pem";
    private final String TLS_MIM_SERVER_CERT_FILE_PATH = "./src/test/resources/authentication/tls/hn-verification/broker-cert.pem";
    private final String TLS_MIM_SERVER_KEY_FILE_PATH = "./src/test/resources/authentication/tls/hn-verification/broker-key.pem";

    private final String TLS_TRUST_CERT_FILE_PATH = "./src/test/resources/authentication/tls/cacert.pem";
    private final String TLS_SERVER_CERT_FILE_PATH = "./src/test/resources/authentication/tls/broker-cert.pem";
    private final String TLS_SERVER_KEY_FILE_PATH = "./src/test/resources/authentication/tls/broker-key.pem";

    private final String TLS_CLIENT_CERT_FILE_PATH = "./src/test/resources/authentication/tls/client-cert.pem";
    private final String TLS_CLIENT_KEY_FILE_PATH = "./src/test/resources/authentication/tls/client-key.pem";

    private final String BASIC_CONF_FILE_PATH = "./src/test/resources/authentication/basic/.htpasswd";

    private final static String brokerHostName = "localhost";
    private boolean hostnameVerificationEnabled = true;

    protected void setup() throws Exception {
        if (methodName.equals("testAnonymousSyncProducerAndConsumer")) {
            conf.setAnonymousUserRole("anonymousUser");
        }

        conf.setAuthenticationEnabled(true);
        conf.setAuthorizationEnabled(true);

        conf.setTlsEnabled(true);
        conf.setTlsAllowInsecureConnection(true);

        Set<String> superUserRoles = new HashSet<>();
        superUserRoles.add("localhost");
        superUserRoles.add("superUser");
        superUserRoles.add("superUser2");
        conf.setSuperUserRoles(superUserRoles);

        conf.setBrokerClientAuthenticationPlugin(AuthenticationTls.class.getName());
        conf.setBrokerClientAuthenticationParameters(
                "tlsCertFile:" + TLS_CLIENT_CERT_FILE_PATH + "," + "tlsKeyFile:" + TLS_SERVER_KEY_FILE_PATH);

        Set<String> providers = new HashSet<>();
        providers.add(AuthenticationProviderTls.class.getName());
        providers.add(AuthenticationProviderBasic.class.getName());
        System.setProperty("pulsar.auth.basic.conf", BASIC_CONF_FILE_PATH);
        conf.setAuthenticationProviders(providers);

        conf.setClusterName("use");

        super.init();

        setupClient();
    }

    protected void setupClient() throws Exception {

        Map<String, String> authParams = new HashMap<>();
        authParams.put("tlsCertFile", TLS_CLIENT_CERT_FILE_PATH);
        authParams.put("tlsKeyFile", TLS_CLIENT_KEY_FILE_PATH);
        Authentication authTls = new AuthenticationTls();
        authTls.configure(authParams);

        org.apache.pulsar.client.api.ClientConfiguration clientConf = new org.apache.pulsar.client.api.ClientConfiguration();
        clientConf.setStatsInterval(0, TimeUnit.SECONDS);
        clientConf.setTlsTrustCertsFilePath(TLS_MIM_TRUST_CERT_FILE_PATH);
        clientConf.setTlsAllowInsecureConnection(true);
        clientConf.setAuthentication(authTls);
        clientConf.setUseTls(true);
        clientConf.setTlsHostnameVerificationEnable(hostnameVerificationEnabled);

        admin = spy(new PulsarAdmin(brokerUrlTls, clientConf));
        String lookupUrl;
        lookupUrl = new URI("pulsar+ssl://" + brokerHostName + ":" + BROKER_PORT_TLS).toString();
        pulsarClient = PulsarClient.create(lookupUrl, clientConf);

        admin.properties().createProperty("my-property",
                new PropertyAdmin(Lists.newArrayList("appid1", "appid2"), Sets.newHashSet("use")));
        admin.namespaces().createNamespace("my-property/use/my-ns");
    }

    @AfterMethod
    @Override
    protected void cleanup() throws Exception {
        if (!methodName.equals("testDefaultHostVerifier")) {
            super.internalCleanup();
        }
    }

    @DataProvider(name = "hostnameVerification")
    public Object[][] codecProvider() {
        return new Object[][] { { Boolean.TRUE }, { Boolean.FALSE } };
    }

    /**
     * It verifies that client performs host-verification in order to create producer/consumer.
     * 
     * <pre>
     * 1. Client tries to connect to broker with hostname="localhost"
     * 2. Broker sends x509 certificates with CN = "pulsar"
     * 3. Client verifies the host-name and closes the connection and fails consumer creation
     * </pre>
     * 
     * @throws Exception
     */
    @Test(dataProvider = "hostnameVerification")
    public void testTlsSyncProducerAndConsumerWithInvalidBrokerHost(boolean hostnameVerificationEnabled)
            throws Exception {
        log.info("-- Starting {} test --", methodName);

        this.hostnameVerificationEnabled = hostnameVerificationEnabled;
        // setup broker cert which has CN = "pulsar" different than broker's hostname="localhost"
        conf.setTlsTrustCertsFilePath(TLS_MIM_TRUST_CERT_FILE_PATH);
        conf.setTlsCertificateFilePath(TLS_MIM_SERVER_CERT_FILE_PATH);
        conf.setTlsKeyFilePath(TLS_MIM_SERVER_KEY_FILE_PATH);
        conf.setBrokerClientAuthenticationParameters(
                "tlsCertFile:" + TLS_CLIENT_CERT_FILE_PATH + "," + "tlsKeyFile:" + TLS_MIM_SERVER_KEY_FILE_PATH);

        setup();

        ConsumerConfiguration conf = new ConsumerConfiguration();
        conf.setSubscriptionType(SubscriptionType.Exclusive);
        try {
            Consumer consumer = pulsarClient.subscribe("persistent://my-property/use/my-ns/my-topic",
                    "my-subscriber-name", conf);
            if (hostnameVerificationEnabled) {
                Assert.fail("Connection should be failed due to hostnameVerification enabled");
            }
        } catch (PulsarClientException e) {
            if (!hostnameVerificationEnabled) {
                Assert.fail("Consumer should be created because hostnameverification is disabled");
            }
        }

        log.info("-- Exiting {} test --", methodName);
    }

    /**
     * It verifies that client performs host-verification in order to create producer/consumer.
     * 
     * <pre>
     * 1. Client tries to connect to broker with hostname="localhost"
     * 2. Broker sends x509 certificates with CN = "localhost"
     * 3. Client verifies the host-name and continues
     * </pre>
     * 
     * @throws Exception
     */
    @Test
    public void testTlsSyncProducerAndConsumerCorrectBrokerHost() throws Exception {
        log.info("-- Starting {} test --", methodName);

        // setup broker cert which has CN = "localhost"
        conf.setTlsTrustCertsFilePath(TLS_TRUST_CERT_FILE_PATH);
        conf.setTlsCertificateFilePath(TLS_SERVER_CERT_FILE_PATH);
        conf.setTlsKeyFilePath(TLS_SERVER_KEY_FILE_PATH);

        setup();

        ConsumerConfiguration conf = new ConsumerConfiguration();
        conf.setSubscriptionType(SubscriptionType.Exclusive);
        Consumer consumer = pulsarClient.subscribe("persistent://my-property/use/my-ns/my-topic", "my-subscriber-name",
                conf);

        ProducerConfiguration producerConf = new ProducerConfiguration();

        Producer producer = pulsarClient.createProducer("persistent://my-property/use/my-ns/my-topic", producerConf);
        for (int i = 0; i < 10; i++) {
            String message = "my-message-" + i;
            producer.send(message.getBytes());
        }

        Message msg = null;
        Set<String> messageSet = Sets.newHashSet();
        for (int i = 0; i < 10; i++) {
            msg = consumer.receive(5, TimeUnit.SECONDS);
            String receivedMessage = new String(msg.getData());
            log.debug("Received message: [{}]", receivedMessage);
            String expectedMessage = "my-message-" + i;
            testMessageOrderAndDuplicates(messageSet, receivedMessage, expectedMessage);
        }
        // Acknowledge the consumption of all messages at once
        consumer.acknowledgeCumulative(msg);
        consumer.close();

        log.info("-- Exiting {} test --", methodName);
    }

    /**
     * This test verifies {@link DefaultHostnameVerifier} behavior and gives fair idea about host matching result
     * 
     * @throws Exception
     */
    @Test
    public void testDefaultHostVerifier() throws Exception {
        log.info("-- Starting {} test --", methodName);
        Method matchIdentityStrict = DefaultHostnameVerifier.class.getDeclaredMethod("matchIdentityStrict",
                String.class, String.class, PublicSuffixMatcher.class);
        matchIdentityStrict.setAccessible(true);
        Assert.assertTrue((boolean) matchIdentityStrict.invoke(null, "pulsar", "pulsar", null));
        Assert.assertFalse((boolean) matchIdentityStrict.invoke(null, "pulsar.com", "pulsar", null));
        Assert.assertTrue((boolean) matchIdentityStrict.invoke(null, "pulsar-broker1.com", "pulsar*.com", null));
        // unmatched remainder: "1-broker." should not contain "."
        Assert.assertFalse((boolean) matchIdentityStrict.invoke(null, "pulsar-broker1.com", "pulsar*com", null));
        Assert.assertFalse((boolean) matchIdentityStrict.invoke(null, "pulsar.com", "*", null));
        log.info("-- Exiting {} test --", methodName);
    }

}
