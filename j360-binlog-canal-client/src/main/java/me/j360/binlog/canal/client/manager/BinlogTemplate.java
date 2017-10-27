package me.j360.binlog.canal.client.manager;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.common.utils.AddressUtils;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.google.common.collect.Maps;
import me.j360.binlog.canal.client.constant.BinlogConfig;
import me.j360.binlog.canal.client.domain.EsEntity;
import me.j360.binlog.canal.client.util.JsonMapper;
import me.j360.binlog.canal.client.util.Underline2Camel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;



@Component
public class BinlogTemplate {

    private CanalConnector connector;
    private boolean stoped;

    @Autowired
    private ElasticSearchTemplate elasticSearchTemplate;
    @Autowired
    private KafkaTemplate kafkaTemplate;

    @PostConstruct
    private void init() {
        connector = CanalConnectors.newSingleConnector(new InetSocketAddress(AddressUtils.getHostIp(),
                11111), "elasticsearch", "", "");
        stoped = false;
    }

    public void startBinlog(){
        int batchSize = 1000;
        int emptyCount = 0;

        connector.connect();
        //指定范围为所有
        //connector.subscribe(".*\\..*");

        connector.subscribe();
        //指定具体表
        //connector.subscribe("paomiantv.user, paomiantv.topic");
        connector.rollback();
        int totalEmptyCount = 100000;
        while (emptyCount < totalEmptyCount && !stoped) {
            Message message = connector.getWithoutAck(batchSize); // 获取指定数量的数据
            long batchId = message.getId();
            int size = message.getEntries().size();
            if (batchId == -1 || size == 0) {
                emptyCount++;
                System.out.println("empty count : " + emptyCount);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            } else {
                emptyCount = 0;
                // System.out.printf("message[batchId=%s,size=%s] \n", batchId, size);
                printEntry(message.getEntries());
            }

            connector.ack(batchId); // 提交确认
            // connector.rollback(batchId); // 处理失败, 回滚数据
        }

        System.out.println("empty too many times, exit");
    }

    public void stopBinlog(){
        stoped = true;
    }


    private void printEntry(List<CanalEntry.Entry> entrys) {
        for (CanalEntry.Entry entry : entrys) {
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                continue;
            }

            CanalEntry.RowChange rowChage = null;
            try {
                rowChage = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("ERROR ## parser of eromanga-event has an error , data:" + entry.toString(),
                        e);
            }

            CanalEntry.EventType eventType = rowChage.getEventType();

            //按表划分
            doTableSelect(entry, rowChage, eventType);
        }
    }

    private void printColumn(List<CanalEntry.Column> columns) {
        for (CanalEntry.Column column : columns) {
            System.out.println(column.getName() + " : " + column.getValue() + "    update=" + column.getUpdated());
        }
    }


    private void doTableSelect(CanalEntry.Entry entry, CanalEntry.RowChange rowChage, CanalEntry.EventType eventType) {

        System.out.println(String.format("================> binlog[%s:%s] , name[%s,%s] , eventType : %s",
                entry.getHeader().getLogfileName(), entry.getHeader().getLogfileOffset(),
                entry.getHeader().getSchemaName(), entry.getHeader().getTableName(),
                eventType));

        //过滤user
        if (Objects.equals(BinlogConfig.SCHEMA_NAME, entry.getHeader().getSchemaName()) && Objects.equals(entry.getHeader().getTableName(), BinlogConfig.TABLE_USER))  {
            userTableSelect(rowChage, eventType);
        }

    }


    private void userTableSelect(CanalEntry.RowChange rowChage, CanalEntry.EventType eventType) {

        for (CanalEntry.RowData rowData : rowChage.getRowDatasList()) {
            if (eventType == CanalEntry.EventType.DELETE) {
                //printUpdateColumnJson(rowData.getBeforeColumnsList());
                EsEntity esEntity = getIdColumn(rowData.getBeforeColumnsList());

                System.out.println("-------> delete before");
                System.out.println(esEntity);
            } else if (eventType == CanalEntry.EventType.INSERT) {
                EsEntity esEntity = printUpdateColumnJson(rowData.getAfterColumnsList());

                System.out.println("-------> insert after");
                System.out.println(esEntity);

                if (esEntity.isUpdate()) {
                    //同步给es
                    elasticSearchTemplate.add(BinlogConfig.PRE_USER_URL, esEntity.getId(), esEntity.getSource());
                    //发送给kafka
                    kafkaTemplate.sendBroker(BinlogConfig.TOPIC, esEntity.getSource());
                }
            } else {
                //System.out.println("-------> before");
                //printColumn(rowData.getBeforeColumnsList());
                //System.out.println("-------> after");
                EsEntity esEntity = printUpdateColumnJson(rowData.getAfterColumnsList());
                System.out.println("-------> update after");
                System.out.println(esEntity);

                if (esEntity.isUpdate()) {
                    //同步给es
                    elasticSearchTemplate.update(BinlogConfig.PRE_USER_URL, esEntity.getId(), esEntity.getSource());

                    //发送给kafka
                    kafkaTemplate.sendBroker(BinlogConfig.TOPIC, esEntity.getSource());
                }
            }
        }


    }

    private EsEntity printUpdateColumnJson(List<CanalEntry.Column> columns) {
        Map<String, Object> columnMap = Maps.newHashMap();
        String id = "";
        boolean update = false;
        for (CanalEntry.Column column : columns) {
            //
            if (column.getIsKey()) {
                id = column.getValue();
            }
            if (column.getUpdated()) {
                String javaName = Underline2Camel.underline2Camel(column.getName(), true);
                columnMap.put(javaName, column.getValue());
                if (Objects.equals("username",javaName)) {
                    columnMap.put("usernameKey", column.getValue());
                }
                update = true;
            }
        }
        return new EsEntity(id, JsonMapper.INSTANCE.toJson(columnMap), update);
    }

    private EsEntity getIdColumn(List<CanalEntry.Column> columns) {
        String id = "";
        for (CanalEntry.Column column : columns) {
            if (column.getIsKey()) {
                id = column.getValue();
            }
        }
        return new EsEntity(id , null, false);
    }

    @PreDestroy
    private void destory() {
        connector.disconnect();
    }
}
