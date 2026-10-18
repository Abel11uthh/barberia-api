# Productos e inventario por sucursal

Solo se gestionan productos destinados a venta. Los materiales consumidos por barberos, ventas, pedidos, reservas de stock y cobros no forman parte de esta entrega.

Cada producto tiene SKU único normalizado a mayúsculas y sin espacios exteriores, nombre, descripción opcional, precio positivo en MXN con dos decimales y estado activo. El SKU admite letras A–Z, dígitos, punto, guion y guion bajo; empieza con letra o dígito. Cambiar el precio modifica el catálogo actual: una venta futura deberá guardar su **propio precio aplicado**, sin depender del catálogo posterior. No se crean productos iniciales.

| Ruta | Permiso | Operación |
| --- | --- | --- |
| `GET /api/v1/products?search=&page=0&size=20&sort=name,asc` | CLIENT | Busca solo productos activos, con stock de sucursales activas. |
| `GET /api/v1/products/{id}` | CLIENT | Detalle activo y stock disponible por sucursal. |
| `GET /api/v1/staff/products` y `/{id}` | BARBER | Productos activos y existencias solo de su sucursal. |
| `GET /api/v1/admin/products` y `/{id}` | ADMIN | Todos los productos, incluso inactivos, y stock de todas las sucursales. |
| `POST /api/v1/admin/products` | ADMIN | Crea un producto. |
| `PUT /api/v1/admin/products/{id}` | ADMIN | Cambia SKU, nombre, descripción y precio. |
| `PATCH /api/v1/admin/products/{id}/deactivate` | ADMIN | Desactiva sin borrar producto ni historial. |
| `POST /api/v1/admin/products/{id}/inventory/entries` | ADMIN | Entrada de unidades, cantidad positiva. |
| `POST /api/v1/admin/products/{id}/inventory/adjustments` | ADMIN | Corrección firmada: positiva añade, negativa resta, cero inválido. |
| `GET /api/v1/admin/products/{id}/inventory/movements?branchId=&page=0&size=20&sort=occurredAt,desc` | ADMIN | Historial auditable, con filtro opcional de sucursal. |

Los listados admiten `search` por SKU o nombre (máximo 120 caracteres), `page >= 0`, `size` de 1 a 100 y `sort` limitado a `id`, `sku`, `name` o `price` con `asc`/`desc`. El historial admite solo `id` y `occurredAt`. La respuesta de catálogo contiene `totalStock` como **suma de las sucursales visibles en esa respuesta** y `branches` con `branchId`, nombre, estado y cantidad. La vista del cliente incluye todas las sucursales activas, incluso si la cantidad es cero; la del barbero solo su sucursal; la del administrador todas, incluidas las inactivas. El total general físico es la suma de todas las sucursales en la vista administrativa, no una columna persistida.

Los roles son acumulativos: una cuenta que también tenga CLIENT puede usar la vista de cliente; la ruta de personal sigue limitada a la sucursal de su perfil. ADMIN dispone de su vista completa.

El total se suma con precisión entera sin desbordamiento, aunque la suma de sucursales supere el rango de una cantidad individual. Se sigue devolviendo como número JSON y no se persiste un total adicional.

Ejemplo ficticio de alta:

```json
{"sku":"CERA-01","name":"Cera para cabello","description":"Presentación de ejemplo","price":129.90}
```

El producto empieza con cero unidades en todas las sucursales. Para inicializar su stock, ADMIN registra una entrada, **sin editar tablas**:

```json
{"branchId":"10000000-0000-0000-0000-000000000001","quantity":12,"reason":"Recepción inicial de ejemplo"}
```

La respuesta del movimiento muestra `kind` (`ENTRY` o `ADJUSTMENT`), `delta`, `quantityBefore`, `quantityAfter`, `actorId`, `branchId`, `reason` y `occurredAt`. El actor se obtiene del JWT y la fecha del Clock del servidor; no se aceptan desde el request. Son datos de auditoría visibles solo para ADMIN; no se exponen contraseñas, correos ni entidades de cuenta. Una entrada significa recepción física. Un ajuste corrige una diferencia comprobada; por ejemplo, `quantity:-2` descuenta dos unidades. Las cantidades son enteras: una cantidad fraccionaria se rechaza sin truncarla. El motivo es obligatorio y queda en el historial.

PostgreSQL exige SKU único y normalizado, precio positivo, una sola fila de stock por producto–sucursal, stock no negativo y movimientos con antes/después coherentes. El servicio bloquea la fila del producto antes de crear o actualizar stock, de modo que entradas y ajustes concurrentes no pierden unidades, ni siquiera en la primera operación sin fila de existencias. Stock y movimiento se confirman o revierten juntos. No se aceptan nuevas operaciones de stock en sucursales o productos inactivos; el historial previo se conserva.

Errores JSON: 400 para datos, cantidad, filtro o ordenación inválidos; 403 para rol insuficiente; 404 para producto inexistente/no visible; 409 para SKU duplicado, stock insuficiente u operación nueva sobre producto/sucursal inactivos. Una cuenta sin autenticar recibe 401.
