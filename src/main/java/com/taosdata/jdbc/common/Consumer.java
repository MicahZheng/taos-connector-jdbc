package com.taosdata.jdbc.common;

import com.taosdata.jdbc.tmq.ConsumerRecords;
import com.taosdata.jdbc.tmq.Deserializer;
import com.taosdata.jdbc.tmq.OffsetCommitCallback;
import com.taosdata.jdbc.tmq.TopicPartition;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public interface Consumer<V> {

    /**
     * create consumer
     * @param properties ip / port / user / password and so on.
     * @throws SQLException jni exception
     */
    void create(Properties properties) throws SQLException;

    /**
     * subscribe topics
     * @param topics collection of topics
     * @throws SQLException jni exception
     */
    void subscribe(Collection<String> topics) throws SQLException;

    /**
     * unsubscribe topics
     * @throws SQLException jni exception
     */
    void unsubscribe() throws SQLException;

    /**
     * get subscribe topics
     * @return topic set
     * @throws SQLException jni exception
     */
    Set<String> subscription() throws SQLException;

    /**
     * get result records
     * @param timeout wait time for poll data
     * @param deserializer convert resultSet to javaBean
     * @return ConsumerRecord is the collection of javaBean
     * @throws SQLException java reflect exception or resultSet convert exception
     */
    ConsumerRecords<V> poll(Duration timeout, Deserializer<V> deserializer) throws SQLException;

    /**
     * commit offset with sync
     * @throws SQLException jni exception
     */
    void commitSync() throws SQLException;

    /**
     * close consumer
     * @throws SQLException jni exception
     */
    void close() throws SQLException;

    void commitAsync(OffsetCommitCallback<V> callback);

    /**
     * If this API is invoked for the same partition more than once, the latest offset will be used on the next poll().
     */
    void seek(TopicPartition partition, long offset) throws SQLException;

    long position(TopicPartition partition) throws SQLException;

    Map<TopicPartition, Long> position(String topic) throws SQLException;

    Map<TopicPartition, Long> beginningOffsets(String topic) throws SQLException;

    Map<TopicPartition, Long> endOffsets(String topic) throws SQLException;
}
