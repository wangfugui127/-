package com.lsy.minimall.cart;

import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.persistence.MapperRows;
import com.lsy.minimall.persistence.mapper.*;
import com.lsy.minimall.product.ProductMapper;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CartService {
  private final CartItemMapper cartItems;
  private final ProductMapper products;
  private final Transactions tx;

  public CartService(CartItemMapper cartItems, ProductMapper products, Transactions tx) {
    this.cartItems = cartItems;
    this.products = products;
    this.tx = tx;
  }

  public List<Map<String, Object>> list(long user) {
    return cartItems.findCartViewByUserId(user);
  }

  public Map<String, Object> add(long user, long product, int quantity) {
    check(quantity);
    return tx.run(
        () -> {
          var p = MapperRows.require(products.findByIdForUpdate(product));
          if (!"ON_SALE".equals(p.get("status")))
            throw BusinessException.conflict("PRODUCT_UNAVAILABLE", "商品未上架");
          var old = cartItems.findByUserAndProduct(user, product);
          int next =
              old == null ? quantity : Math.toIntExact(Values.num(old.get("quantity"))) + quantity;
          check(next);
          cartItems.upsertQuantity(user, product, next, next);
          return MapperRows.require(cartItems.findByUserAndProduct(user, product));
        });
  }

  public Map<String, Object> change(long user, long id, int quantity) {
    check(quantity);
    if (cartItems.updateQuantity(quantity, id, user) != 1) throw BusinessException.missing();
    return MapperRows.require(cartItems.findByIdAndUser(id, user));
  }

  public void remove(long user, long id) {
    cartItems.deleteByIdAndUser(id, user);
  }

  private void check(int n) {
    if (n < 1 || n > 99) throw BusinessException.bad("数量需1到99");
  }
}
