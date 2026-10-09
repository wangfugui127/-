# 接口清单（运行中应用导出）

共34个业务接口，前缀为/api/v1。字段示例与完整业务规则见开发执行指南第6章和附录A。Swagger： http://127.0.0.1:8080/swagger-ui/index.html 。

| 方法 | 路径 |
|---|---|
| GET | /api/v1/admin/orders |
| GET | /api/v1/admin/orders/{id} |
| POST | /api/v1/admin/orders/{id}/shipments |
| GET | /api/v1/admin/products |
| POST | /api/v1/admin/products |
| PUT | /api/v1/admin/products/{id} |
| POST | /api/v1/admin/products/{id}/status |
| POST | /api/v1/admin/products/{id}/stock-adjustments |
| GET | /api/v1/admin/seckill-activities |
| POST | /api/v1/admin/seckill-activities |
| PUT | /api/v1/admin/seckill-activities/{id} |
| POST | /api/v1/admin/seckill-activities/{id}/status |
| POST | /api/v1/auth/login |
| POST | /api/v1/auth/logout |
| GET | /api/v1/auth/me |
| POST | /api/v1/auth/register |
| GET | /api/v1/cart |
| POST | /api/v1/cart/items |
| DELETE | /api/v1/cart/items/{id} |
| PATCH | /api/v1/cart/items/{id} |
| GET | /api/v1/categories |
| POST | /api/v1/internal/mock-payments/callback |
| GET | /api/v1/orders |
| POST | /api/v1/orders |
| GET | /api/v1/orders/{id} |
| POST | /api/v1/orders/{id}/cancel |
| POST | /api/v1/orders/{id}/confirm |
| POST | /api/v1/orders/{id}/mock-payments |
| GET | /api/v1/payments/{id} |
| GET | /api/v1/products |
| GET | /api/v1/products/{id} |
| GET | /api/v1/seckill-activities |
| GET | /api/v1/seckill-activities/{id} |
| POST | /api/v1/seckill-activities/{id}/orders |
