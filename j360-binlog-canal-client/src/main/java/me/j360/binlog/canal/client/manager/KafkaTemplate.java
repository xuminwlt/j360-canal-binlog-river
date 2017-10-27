package me.j360.binlog.canal.client.manager;

import me.j360.binlog.canal.client.listener.MsgListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


@Component
public class KafkaTemplate {

    private Properties producerProps;
    private Properties consumerProps;

    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;

    private int pollTimeout = 15 * 1000;

    private ExecutorService executor;
    private boolean stoped = false;

    @PostConstruct
    private void init() {
        executor = Executors.newFixedThreadPool(1);
        producer = new KafkaProducer<String, String>(producerProps);
        consumer = new KafkaConsumer<String, String>(consumerProps);
    }

    @PreDestroy
    private void destory() {
        stoped = true;

        producer.flush();
        producer.close();

        consumer.wakeup();

        List<Runnable> runableList = executor.shutdownNow();
        while (runableList.size() > 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            runableList = executor.shutdownNow();
        }
        consumer.unsubscribe();
        consumer.close();
    }

    public long sendBroker(String topic, String msg, boolean... sync) {
        ProducerRecord<String, String> record = new ProducerRecord<String, String>(topic, msg);
        Future<RecordMetadata> future = producer.send(record);
        if (sync.length > 0 && sync[0]) {
            try {
                RecordMetadata recordMetadata = future.get();
                return recordMetadata.offset();
            } catch (Exception e) {
            }
        }
        return -1;
    }

    public void receive(final String topic, final MsgListener<String> msgListener) {
        consumer.subscribe(Arrays.asList(topic));
        Runnable task = new Runnable() {
            @Override
            public void run() {
                while (!stoped) {
                    ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
                    for (ConsumerRecord<String, String> record : records) {
                        if (msgListener != null) {
                            msgListener.onMessage(record.topic(), record.value());
                        }
                    }
                }
            }
        };
        executor.submit(task);
    }

    public void pause(String topic) {
        Set<TopicPartition> topicPartitions = consumer.assignment();
        consumer.pause(topicPartitions);
    }

    public void resume(String topic) {
        Set<TopicPartition> topicPartitions = consumer.assignment();
        consumer.resume(topicPartitions);
    }
    // ---------------------------------------------------------

    public void setProducerProps(Properties producerProps) {
        this.producerProps = producerProps;
    }

    public void setConsumerProps(Properties consumerProps) {
        this.consumerProps = consumerProps;
    }

}
