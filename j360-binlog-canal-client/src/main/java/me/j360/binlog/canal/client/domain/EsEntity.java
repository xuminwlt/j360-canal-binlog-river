package me.j360.binlog.canal.client.domain;

import lombok.Data;



@Data
public class EsEntity  {

    private String id;
    private String source;
    private boolean update;

    public EsEntity(String id, String source, boolean update) {
        this.id = id;
        this.source = source;
        this.update = update;
    }
}
