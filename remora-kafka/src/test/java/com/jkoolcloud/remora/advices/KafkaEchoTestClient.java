/*
 * Copyright 2019-2020 NASTEL TECHNOLOGIES, INC.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jkoolcloud.nisha.advices;

import java.io.BufferedInputStream;
import java.io.FileReader;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaEchoTestClient {

	public static final String CONSUMMER_PROPERTIES = "../../../../consumer.properties";
	public static final String PRODUCER_PROPERTIES = "../../../../producer.properties";
	private static String receiveTopicName;
	private static String sendTopicName;

	public static void main(String[] args) throws Exception {
		Consumer<String, String> consumer = initConsumer();
		Producer<String, String> producer = initProducer();

		while (true) {
			ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
			Iterator<ConsumerRecord<String, String>> iterator = records.iterator();
			while (iterator.hasNext()) {
				ConsumerRecord<String, String> next = iterator.next();
				System.out.println("Key " + next.key() + "Value " + next.value());
				producer.send(new ProducerRecord<>(sendTopicName, next.key(), "Echo" + next.value()));
			}
		}
	}

	private static Consumer<String, String> initConsumer() throws Exception {
		Properties props = new Properties();
		props.load(new FileReader(KafkaEchoTestClient.class.getResource(CONSUMMER_PROPERTIES).getFile())); // NON-NLS
		receiveTopicName = props.getProperty("test.app.topic.name", "tnt4j_streams_kafka_intercept_test_page_visits"); // NON-NLS
		props.remove("test.app.topic.name");

		Consumer<String, String> consumer = new KafkaConsumer<>(props);
		consumer.subscribe(Collections.singletonList(receiveTopicName));

		return consumer;
	}

	private static Producer<String, String> initProducer() throws Exception {
		Properties props = new Properties();
		props.load(new BufferedInputStream(KafkaEchoTestClient.class.getResourceAsStream(PRODUCER_PROPERTIES)));// NON-NLS

		@SuppressWarnings("unused")
		Integer eventsToProduce = Integer.valueOf(props.getProperty("events.count"), 10);
		props.remove("events.count");

		sendTopicName = props.getProperty("test.app.topic.name", "tnt4j_streams_kafka_intercept_test_page_visits"); // NON-NLS

		props.remove("test.app.topic.name");

		Producer<String, String> producer = new KafkaProducer<>(props);

		return producer;
	}
}
