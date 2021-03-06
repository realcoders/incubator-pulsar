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
package org.apache.pulsar.admin.cli;

import org.apache.pulsar.client.admin.NonPersistentTopics;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;

@Parameters(commandDescription = "Operations on non-persistent topics")
public class CmdNonPersistentTopics extends CmdBase {
    private final NonPersistentTopics nonPersistentTopics;

    public CmdNonPersistentTopics(PulsarAdmin admin) {
        super("non-persistent", admin);
        nonPersistentTopics = admin.nonPersistentTopics();

        jcommander.addCommand("create-partitioned-topic", new CreatePartitionedCmd());
        jcommander.addCommand("lookup", new Lookup());
        jcommander.addCommand("stats", new GetStats());
        jcommander.addCommand("stats-internal", new GetInternalStats());
        jcommander.addCommand("get-partitioned-topic-metadata", new GetPartitionedTopicMetadataCmd());
        jcommander.addCommand("list", new GetList());
        jcommander.addCommand("list-in-bundle", new GetListInBundle());
    }

    @Parameters(commandDescription = "Lookup a destination from the current serving broker")
    private class Lookup extends CliCommand {
        @Parameter(description = "non-persistent://property/cluster/namespace/topic\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String destination = validateDestination(params);
            print(admin.lookups().lookupDestination(destination));
        }
    }

    @Parameters(commandDescription = "Get the stats for the topic and its connected producers and consumers. \n"
            + "\t       All the rates are computed over a 1 minute window and are relative the last completed 1 minute period.")
    private class GetStats extends CliCommand {
        @Parameter(description = "non-persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validateNonPersistentTopic(params);
            print(nonPersistentTopics.getStats(persistentTopic));
        }
    }

    @Parameters(commandDescription = "Get the internal stats for the topic")
    private class GetInternalStats extends CliCommand {
        @Parameter(description = "non-persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validateNonPersistentTopic(params);
            print(nonPersistentTopics.getInternalStats(persistentTopic));
        }
    }

    @Parameters(commandDescription = "Create a partitioned topic. \n"
            + "\t\tThe partitioned topic has to be created before creating a producer on it.")
    private class CreatePartitionedCmd extends CliCommand {

        @Parameter(description = "non-persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Parameter(names = { "-p",
                "--partitions" }, description = "Number of partitions for the topic", required = true)
        private int numPartitions;

        @Override
        void run() throws Exception {
            String persistentTopic = validateNonPersistentTopic(params);
            nonPersistentTopics.createPartitionedTopic(persistentTopic, numPartitions);
        }
    }

    @Parameters(commandDescription = "Get the partitioned topic metadata. \n"
            + "\t\tIf the topic is not created or is a non-partitioned topic, it returns empty topic with 0 partitions")
    private class GetPartitionedTopicMetadataCmd extends CliCommand {

        @Parameter(description = "non-persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws Exception {
            String persistentTopic = validateNonPersistentTopic(params);
            print(nonPersistentTopics.getPartitionedTopicMetadata(persistentTopic));
        }
    }
    
    @Parameters(commandDescription = "Get list of non-persistent topics present under a namespace")
    private class GetList extends CliCommand {
        @Parameter(description = "property/cluster/namespace\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String namespace = validateNamespace(params);
            print(nonPersistentTopics.getList(namespace));
        }
    }
    
    @Parameters(commandDescription = "Get list of non-persistent topics present under a namespace bundle")
    private class GetListInBundle extends CliCommand {
        @Parameter(description = "property/cluster/namespace\n", required = true)
        private java.util.List<String> params;
        
        @Parameter(names = { "-b",
                "--bundle" }, description = "bundle range", required = true)
        private String bundleRange;

        @Override
        void run() throws PulsarAdminException {
            String namespace = validateNamespace(params);
            print(nonPersistentTopics.getListInBundle(namespace, bundleRange));
        }
    }
}
