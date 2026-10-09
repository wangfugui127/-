package com.lsy.minimall.cart;

import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CartService {
  private final Db db;
  private final Transactions tx;

  public CartService(Db db, Transactions tx) {
    this.db = db;
    this.tx = tx;
  }

  public List<Map<String, Object>> list(long user) {
    return db.rows(
        "SELECT"
            + " c.id,c.product_id,c.quantity,p.name,p.image_url,p.specification,p.price_fen,p.stock_available,p.status,CASE"
            + " WHEN p.status='ON_SALE' AND p.stock_available>=c.quantity THEN 1 ELSE 0 END"
            + " available FROM cart_item c JOIN product p ON p.id=c.product_id WHERE c.user_id=?"
            + " ORDER BY c.id",
        user);
  }

  public Map<String, Object> add(long user, long product, int quantity) {
    check(quantity);
    return tx.run(
        () -> {
          var p = db.require("SELECT * FROM product WHERE id=? FOR UPDATE", product);
          if (!"ON_SALE".equals(p.get("status")))
            throw BusinessException.conflict("PRODUCT_UNAVAILABLE", "商品未上架");
          var old =
              db.row("SELECT * FROM cart_item WHERE user_id=? AND product_id=?", user, product);
          int next =
              old == null ? quantity : Math.toIntExact(Values.num(old.get("quantity"))) + quantity;
          check(next);
          db.update(
              "INSERT INTO cart_item(user_id,product_id,quantity) VALUES (?,?,?) ON DUPLICATE KEY"
                  + " UPDATE quantity=?",
              user,
              product,
              next,
              next);
          return db.require(
              "SELECT * FROM cart_item WHERE user_id=? AND product_id=?", user, product);
        });
  }

  public Map<String, Object> change(long user, long id, int quantity) {
    check(quantity);
    if (db.update("UPDATE cart_item SET quantity=? WHERE id=? AND user_id=?", quantity, id, user)
        != 1) throw BusinessException.missing();
    return db.require("SELECT * FROM cart_item WHERE id=? AND user_id=?", id, user);
  }

  public void remove(long user, long id) {
    db.update("DELETE FROM cart_item WHERE id=? AND user_id=?", id, user);
  }

  private void check(int n) {
    if (n < 1 || n > 99) throw BusinessException.bad("数量需1到99");
  }
}
