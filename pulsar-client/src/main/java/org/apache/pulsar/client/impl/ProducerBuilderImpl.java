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
package org.apache.pulsar.client.impl;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.CompressionType;
import org.apache.pulsar.client.api.CryptoKeyReader;
import org.apache.pulsar.client.api.HashingScheme;
import org.apache.pulsar.client.api.MessageRouter;
import org.apache.pulsar.client.api.MessageRoutingMode;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.ProducerCryptoFailureAction;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.conf.ProducerConfigurationData;
import org.apache.pulsar.common.util.FutureUtil;

public class ProducerBuilderImpl implements ProducerBuilder {

    private static final long serialVersionUID = 1L;

    private final PulsarClientImpl client;
    private final ProducerConfigurationData conf;

    ProducerBuilderImpl(PulsarClientImpl client) {
        this(client, new ProducerConfigurationData());
    }

    private ProducerBuilderImpl(PulsarClientImpl client, ProducerConfigurationData conf) {
        this.client = client;
        this.conf = conf;
    }

    @Override
    public ProducerBuilder clone() {
        return new ProducerBuilderImpl(client, conf.clone());
    }

    @Override
    public Producer create() throws PulsarClientException {
        try {
            return createAsync().get();
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof PulsarClientException) {
                throw (PulsarClientException) t;
            } else {
                throw new PulsarClientException(t);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PulsarClientException(e);
        }
    }

    @Override
    public CompletableFuture<Producer> createAsync() {
        if (conf.getTopicName() == null) {
            return FutureUtil
                    .failedFuture(new IllegalArgumentException("Topic name must be set on the producer builder"));
        }

        return client.createProducerAsync(conf);
    }

    @Override
    public ProducerBuilder topic(String topicName) {
        conf.setTopicName(topicName);
        return this;
    }

    @Override
    public ProducerBuilder producerName(String producerName) {
        conf.setProducerName(producerName);
        return this;
    }

    @Override
    public ProducerBuilder sendTimeout(int sendTimeout, TimeUnit unit) {
        conf.setSendTimeoutMs(unit.toMillis(sendTimeout));
        return this;
    }

    @Override
    public ProducerBuilder maxPendingMessages(int maxPendingMessages) {
        conf.setMaxPendingMessages(maxPendingMessages);
        return this;
    }

    @Override
    public ProducerBuilder maxPendingMessagesAcrossPartitions(int maxPendingMessagesAcrossPartitions) {
        conf.setMaxPendingMessagesAcrossPartitions(maxPendingMessagesAcrossPartitions);
        return this;
    }

    @Override
    public ProducerBuilder blockIfQueueFull(boolean blockIfQueueFull) {
        conf.setBlockIfQueueFull(blockIfQueueFull);
        return this;
    }

    @Override
    public ProducerBuilder messageRoutingMode(MessageRoutingMode messageRouteMode) {
        conf.setMessageRoutingMode(messageRouteMode);
        return this;
    }

    @Override
    public ProducerBuilder compressionType(CompressionType compressionType) {
        conf.setCompressionType(compressionType);
        return this;
    }

    @Override
    public ProducerBuilder hashingScheme(HashingScheme hashingScheme) {
        conf.setHashingScheme(hashingScheme);
        return this;
    }

    @Override
    public ProducerBuilder messageRouter(MessageRouter messageRouter) {
        conf.setCustomMessageRouter(messageRouter);
        return this;
    }

    @Override
    public ProducerBuilder enableBatching(boolean batchMessagesEnabled) {
        conf.setBatchingEnabled(batchMessagesEnabled);
        return this;
    }

    @Override
    public ProducerBuilder cryptoKeyReader(CryptoKeyReader cryptoKeyReader) {
        conf.setCryptoKeyReader(cryptoKeyReader);
        return this;
    }

    @Override
    public ProducerBuilder addEncryptionKey(String key) {
        conf.getEncryptionKeys().add(key);
        return this;
    }

    @Override
    public ProducerBuilder cryptoFailureAction(ProducerCryptoFailureAction action) {
        conf.setCryptoFailureAction(action);
        return this;
    }

    @Override
    public ProducerBuilder batchingMaxPublishDelay(long batchDelay, TimeUnit timeUnit) {
        conf.setBatchingMaxPublishDelayMicros(timeUnit.toMicros(batchDelay));
        return this;
    }

    @Override
    public ProducerBuilder batchingMaxMessages(int batchMessagesMaxMessagesPerBatch) {
        conf.setBatchingMaxMessages(batchMessagesMaxMessagesPerBatch);
        return this;
    }

    @Override
    public ProducerBuilder initialSequenceId(long initialSequenceId) {
        conf.setInitialSequenceId(initialSequenceId);
        return this;
    }

    @Override
    public ProducerBuilder property(String key, String value) {
        conf.getProperties().put(key, value);
        return this;
    }

    @Override
    public ProducerBuilder properties(Map<String, String> properties) {
        conf.getProperties().putAll(properties);
        return this;
    }
}
