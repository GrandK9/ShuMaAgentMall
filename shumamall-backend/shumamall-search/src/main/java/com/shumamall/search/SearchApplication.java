package com.shumamall.search;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 搜索服务启动类。
 * <p>
 * 基于 Elasticsearch 提供商品全文检索/排序/过滤 API，
 * 数据来源：全量重建（管理端触发）+ 增量同步（RabbitMQ 消费商品变更）。
 */
@SpringBootApplication
@EnableFeignClients
@EnableRabbit
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
