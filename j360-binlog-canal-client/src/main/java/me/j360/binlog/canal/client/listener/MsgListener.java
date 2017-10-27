package me.j360.binlog.canal.client.listener;

public interface MsgListener<V> {

	public void onMessage(String topic, V messge);

}
