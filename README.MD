# 基于canal的binlog river方案


## v1.0+ 整体设计

在需要binlog的设计方案中,binlog解析是mysql的binlog tcp协议解析方案,配合mysql的结构化meta信息,基本上可以画出一个结构化数据图,基于binlog解析的工具很多,python的工具是最常用的python-binlog-river
java框架中maxwell canal都是可选的方案

- inlog在大型业务中的数据同步案例

1. es
2. cache
3. 分库索引

1.0+的canal使用了很久,碰到了各种坑,bug很多,但从中也学会如何更好的使用工具,高并发下尽量轻量化canal在数据流转中的作用,尽量将后端压力下放到业务系统中,其中可以使用rocket、kafka作为中间方案,自行编写对接rocketMQ和kafka的接口
binlog的落地就是对MQ中的数据进行二次解析过程,通过一定mapping映射关系可以实现多存储的数据同步问题,也可以实现mysql同表不同索引的特殊业务要求

> 
    j360-binlog-canal-client 就是针对1.0+版本的特殊处理,当前canal最新版本1.1.2正式版发布,也是bug多多,文档奇缺无比,因为新增了很多功能,上手难度稍大,下面就详细介绍canal 1.1.2+的功能配置及对应的源码分析

## v1.1+ 

canal新版本使用方案

- 当前版本:
    - v1.1.2 RELEASE
    - v1.1.3 SNAPSHOT


### 认识Canal

首先必须去看看wiki文档,一手资料总是最新的渠道
https://github.com/alibaba/canal/wiki

下载地址
https://github.com/alibaba/canal/releases

1.1.0+版本新增了一个独立部署的adapter工程,用于消费binlog的二次数据,根据当前的功能,这样整个设计图可以组合成

```

--------------------------------------------
mysql master

127.0.0.1:3306

--------------------------------------------
canal.deployer

-> |Canal Deployer| -> |RocketMQ/Kafka|(可选)
   
---------------------------------------------   
canal.adapter

-> |Canal Adapter| -> |rdb/es/hbase/log...|


```

### 启动canal.deployer

deployer就是canal的Server,可以singler部署,也可以使用zk实现HA部署,生产环境强烈建议HA,除非监控盯紧点
server的配置wiki上面讲的很清楚,这里贴一份我的配置,顺便标注下解释

canal.properties
instance.properties

见 ./v1.1.3/deployer/canal.properties


通过bin目录下的命令实现启动停止
./startup.sh
./stop.sh
./restart.sh

instance是canal中的一个实例的范围,可以配置多个不同环境的mysql,dump对应的binlog实现canal server复用,同理,后续的消费也是按照instance进行对应

当前canal提供了对应的canal服务的auth接口,在源码中并没有发现对应的实现,所以目前应该是没有对应的验证功能

- server启动监控及相关问题
1. 多instance问题: 
配置

```
canal.conf.dir = ../conf
# auto scan instance dir add/remove and start/stop instance
canal.auto.scan = true
canal.auto.scan.interval = 5
```
到对应的目录下可以生成多个instance,比如不同的数据库实例,这里的设置是自动扫描配置

2. binlog server连接方案tcp、kafka、RocketMQ

```
canal.port = 11111
canal.metrics.pull.port = 11112 #监控对应的端口
canal.zkServers =
# flush data to zk
canal.zookeeper.flush.period = 1000
canal.withoutNetty = false
# tcp, kafka, RocketMQ
canal.serverMode = RocketMQ
```

选择tcp,监控本地的端口查看是否启动完成

netstat -anp | grep 11111

选择kafka、rocketMQ则需要对应的服务已经启动,配置两者共享
```
##################################################
######### 		     MQ 		     #############
##################################################
canal.mq.servers = 10.200.200.6:9876
canal.mq.retries = 0
canal.mq.batchSize = 16384
canal.mq.maxRequestSize = 1048576
canal.mq.lingerMs = 1
canal.mq.bufferMemory = 33554432
canal.mq.canalBatchSize = 50
canal.mq.canalGetTimeout = 100
canal.mq.flatMessage = true
canal.mq.compressionType = none
canal.mq.acks = all
```

3. 本地生成文件
日志文件统一在logs目录,生成一个canal.log和数个instance的log文件
在conf目录生成meta文件, meta.dat、h2的db文件(根据配置需要,也可以设置对应的已经存在的实例)
如果需要恢复执行记录可以删除以上meta文件

4. 启动过程跟踪
可以设置logback.xml的level到debug等级,如果不设置instance的binlog的position,则读取master的status获取最新的数据
canal server启动时从mysql中读取对应的binlog文件,000xx.binlog从最前面开始读取,这段时间启动client消费一直会提示无数据,是因为还未到binlog的position,需要耐心等待
log显示当前已经达到show master status的位置,尝试操作dml,通过简单的client实例可以查看消费进度


### 启动canal.adapter消费数据落地
 
adapter中同样有两个地方需要配置,具体配置说明见wiki,这里贴上我的配置,加以注释

application.yml

见 ./v1.1.3/adapter/application.yml

client设计思路同server很相似,一个应用配置多个消费落地存储方案
一个配置对应消费数据源tcp、kafka、rocketMQ

多套落地方案groups,配置多个落地方案

```
canalAdapters:
  - instance: example # canal instance Name or mq topic name
    groups:
    - groupId: g1
      outerAdapters:
```

- client启动监控及相关问题

1. 如何简单启动client检查server是否有数据导出

建议设置server的serverMode为tcp,
client设置数据源:tcp

```
srcDataSources:
    defaultDS:
      url: jdbc:mysql://loalhost:3306/test?useUnicode=true
      username: root
      password: root
```

group中设置name:logger打印出获取到的数据源,如果有数据变化的log则server、client启动成功
同理使用MQ方案,server启动后检查topic中的offset是否有新的offset生成, client消费和server解耦后各自调试

2. 多套落地方案如何配置

- 以落地es为例,一个index或者type需要一个配置,一个实例需要在es中配置1个或多个yml配置,会自动扫描
- es的index mapping需要预先生成,client不自动生成mapping。否则会报错

srcDataSources配置多份,用于解析不同的数据库的meta信息,和落地mapping时需要对照,在esyml中配置对应的关系
canalAdapters 配置一个或多个instance的导出配置
outerAdapters 配置导出落地的存储的多套方案




