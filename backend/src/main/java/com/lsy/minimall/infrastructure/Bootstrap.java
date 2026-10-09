package com.lsy.minimall.infrastructure;

import com.lsy.minimall.auth.AuthService;
import com.lsy.minimall.common.Values;
import com.lsy.minimall.product.ProductService;
import com.lsy.minimall.seckill.ActivityService;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class Bootstrap implements ApplicationRunner {
  private final AuthService auth;
  private final Db db;
  private final ProductService products;
  private final ActivityService activities;
  private final String password;
  private final boolean seed;

  public Bootstrap(
      AuthService auth,
      Db db,
      ProductService products,
      ActivityService activities,
      @Value("${app.bootstrap.admin-password:}") String password,
      @Value("${app.bootstrap.seed-demo:true}") boolean seed) {
    this.auth = auth;
    this.db = db;
    this.products = products;
    this.activities = activities;
    this.password = password;
    this.seed = seed;
  }

  public void run(ApplicationArguments args) {
    auth.bootstrap(password);
    if (!seed || db.count("SELECT COUNT(*) FROM product") > 0) return;
    var admin = db.row("SELECT id FROM mall_user WHERE username='admin'");
    if (admin == null) return;
    long actor = Values.num(admin.get("id"));
    String[][] items = {
      {"随行保温杯", "LIFE", "轻量 · 450ml", "3490", "/images/cup.svg"},
      {"桌面无线耳机", "DIGITAL", "低延迟 · 雾蓝", "12900", "/images/headphones.svg"},
      {"书桌阅读灯", "LIFE", "三档色温 · USB", "6900", "/images/lamp.svg"},
      {"日常灵感手账", "BOOK", "A5 · 160页", "2900", "/images/notebook.svg"}
    };
    long first = 0;
    for (var item : items) {
      var p =
          products.create(
              Map.of(
                  "name",
                  item[0],
                  "categoryCode",
                  item[1],
                  "specification",
                  item[2],
                  "description",
                  "为日常生活挑选的实用好物。支持普通购买与限量活动，库存以提交订单时为准。",
                  "priceFen",
                  Long.parseLong(item[3]),
                  "initialStock",
                  100,
                  "imageUrl",
                  item[4]),
              actor);
      products.status(Values.num(p.get("id")), "ON_SALE", Values.num(p.get("version")), actor);
      if (first == 0) first = Values.num(p.get("id"));
    }
    Instant now = db.now();
    var a =
        activities.create(
            Map.of(
                "productId",
                Long.toString(first),
                "title",
                "随行杯限量尝鲜",
                "activityPriceFen",
                1990,
                "plannedStock",
                20,
                "startAt",
                now.minusSeconds(60).toString(),
                "endAt",
                now.plusSeconds(7 * 86400).toString()),
            actor);
    activities.action(
        Values.num(a.get("id")),
        actor,
        Values.id(),
        Map.of("action", "PUBLISH", "version", a.get("version")));
  }
}
