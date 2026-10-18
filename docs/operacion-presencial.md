# Cobros, ventas presenciales y reportes

Todos los endpoints de negocio requieren Bearer JWT y cuenta activa. CASH y CARD son confirmaciones del personal de un pago **ya recibido fuera de la API**. No hay procesamiento bancario, campos de tarjeta, CVV ni almacenamiento de datos bancarios.

## Cobro de servicios

| Ruta | Permiso | Resultado |
| --- | --- | --- |
| `POST /api/v1/staff/appointments/{id}/charge` | BARBER propietario de la cita o ADMIN | 201 con comprobante de cobro. |
| `GET /api/v1/staff/appointments/{id}/charge` | Mismo alcance de personal | Datos del cobro registrado; 404 si no existe. |
| `GET /api/v1/appointments/{id}/payment` | CLIENT propietario | `{paid, amount, currency, paidAt}`, sin actor ni información de otras citas. |

Request de cobro: `{"method":"CASH"}` o `{"method":"CARD"}`. Solo se admiten citas COMPLETED y aún no cobradas; CONFIRMED, IN_PROGRESS, CANCELLED y NO_SHOW devuelven 409. El importe se copia del **total histórico de la cita**; cambiar el catálogo no lo modifica. La fecha se toma del Clock del servidor después de adquirir los bloqueos, no del request ni de la fecha de reserva. El comprobante del personal incluye identificador, cita, sucursal, actor, importe, MXN, método e instante UTC.

Una segunda petición devuelve 409, sin crear otro cobro. El bloqueo de fila de cita serializa peticiones simultáneas y PostgreSQL impone `UNIQUE(appointment_id)`. BARBER necesita perfil activo y solo cobra sus propias citas, incluso si otro barbero pertenece a su misma sucursal. ADMIN no necesita perfil de barbero; puede cobrar cualquiera de las sucursales activas. No se aceptan nuevos cobros en sucursales inactivas. La consulta administrativa conserva el historial aunque se haya desactivado la sucursal.

## Ventas de productos

`POST /api/v1/staff/sales` requiere cabecera **`Idempotency-Key: <UUID nuevo>`** y un cuerpo como:

```json
{
  "branchId":"10000000-0000-0000-0000-000000000001",
  "method":"CARD",
  "items":[
    {"productId":"40000000-0000-0000-0000-000000000001","quantity":2},
    {"productId":"40000000-0000-0000-0000-000000000002","quantity":1}
  ]
}
```

Los identificadores son ejemplos: sustituir por productos existentes. ADMIN debe indicar sucursal. Para BARBER, omitir `branchId` utiliza la de su perfil; enviar otra devuelve 404. Se exige perfil activo para BARBER y sucursal activa para toda venta nueva. El cuerpo no admite actor, fecha, precios ni total. Las cantidades son enteras positivas; de 1 a 100 líneas sin productos repetidos. Se rechazan productos inactivos y la falta de stock con 409.

La respuesta 201 identifica la venta e incluye sucursal, actor, método, fecha real, total en MXN y líneas con producto, nombre, SKU, precio unitario, cantidad y subtotal aplicados. Los precios se leen de PostgreSQL mientras se bloquean los productos; las copias guardadas no cambian con el catálogo. No se asocia automáticamente a una cuenta de cliente ni al contador comercial de pedidos, que continúa pendiente.

`GET /api/v1/staff/sales/{id}` permite consultar después la venta: BARBER solo las de su sucursal, ADMIN todas. CLIENT recibe 403. No se exponen correos, teléfonos, contraseñas ni entidades de cuentas.

### Idempotencia y transacciones

La clave se limita a **cuenta del actor + UUID**. Reenviar la misma operación devuelve 200 y la venta original, sin descontar ni sumar ingresos otra vez. El orden de las líneas y omitir la sucursal propia de BARBER no cambian el contenido semántico. Usar esa clave con distinta sucursal autorizada, método, productos o cantidades devuelve 409. La misma clave en otra cuenta pertenece a otra operación; el cliente del sistema debe conservarla para reintentar con el actor original.

PostgreSQL inserta/bloquea una fila de `sale_operations` dentro de la transacción. Peticiones simultáneas con igual clave esperan la confirmación de la primera. Una operación fallida revierte también esa fila, por lo que la clave se puede reutilizar al corregirla. Las repeticiones autorizadas devuelven el resultado histórico aun si el producto o la sucursal fue desactivado después; una cuenta o perfil desactivado sigue sin poder operar.

Las ventas toman los bloqueos de productos en orden estable de UUID, independientemente del orden del request. Primero comprueban todas las existencias; después guardan venta, líneas, descuentos y movimientos `SALE` con `saleId`, todo en **una sola transacción**. Si falla una línea o la escritura de un movimiento, todo se revierte. PostgreSQL conserva stock no negativo, unicidad de la operación y una sola línea/movimiento por producto vendido. Los ajustes administrativos siguen siendo `ADJUSTMENT`; no representan ventas ni ingresos.

## Reportes de ingresos cobrados

Solo ADMIN: `GET /api/v1/admin/reports/income?period=DAY&date=2032-02-16&branchId=<opcional>`.

| Periodo | Inicio local inclusivo | Fin local exclusivo |
| --- | --- | --- |
| DAY | Fecha indicada, 00:00 | Día siguiente, 00:00 |
| WEEK | Lunes de la semana que contiene `date`, 00:00 | Lunes siguiente, 00:00 |
| MONTH | Primer día del mes que contiene `date`, 00:00 | Primer día del mes siguiente, 00:00 |

Zona **America/Mexico_City**. Se convierten esos límites a Instant y se consulta `paid_at >= inicio AND paid_at < fin`. Por ejemplo, el día local 2032-02-16 cubre desde 2032-02-16T06:00:00Z hasta antes de 2032-02-17T06:00:00Z. Un pago en el límite final pertenece al siguiente periodo. Se usa fecha del **cobro**, aunque la cita haya sido reservada o atendida en otro día. `date` admite años 1–9998 para mantener representables los límites calculados.

Sin `branchId`, suma todas las sucursales; con él, solo esa sucursal, incluso si ahora está inactiva. Devuelve límites locales/UTC, zona, `servicesTotal`, `productsTotal`, `combinedTotal`, `serviceCharges` y `productSales`, en MXN. No suma citas sin cobro ni multiplica ventas por sus líneas. Las dos agregaciones se leen en una instantánea consistente. `basis=COLLECTED_INCOME_WITHOUT_REFUNDS` indica **ingresos cobrados sin devoluciones implementadas**: no son ganancias ni ingresos netos después de reembolsos.

## Migración y límites de esta versión

V7 añade `appointment_charges`, `sale_operations`, `product_sales`, `product_sale_lines` y el vínculo de movimientos de inventario con una venta. V1–V6 quedan intactas. No añade productos, credenciales ni precios iniciales; no limpia datos existentes. Hibernate continúa en `validate`. La verificación anterior aplicó V7 sobre barberia_auth_test con V6 y datos existentes, y aplicó V1–V7 sobre barberia_demo vacía; ambas migraciones finalizaron correctamente.

400: datos, método, cantidad, clave/cabecera o campos no permitidos; 401: JWT/cuenta no disponible; 403: rol insuficiente; 404: cita/venta/sucursal ajena o inexistente; 409: estado incompatible, ya cobrada, stock insuficiente, recurso inactivo o clave usada con otro contenido. Se conserva el formato JSON de errores de la API.

Continúan pendientes Mercado Pago, compras en línea, pedidos y códigos de recogida, reservas de stock en línea, pagos de citas en línea, reembolsos y devoluciones. Los recibos presenciales no ejecutan esas operaciones.

## Recorrido HTTP local reproducible

Con la API arrancada y un ADMIN inicializado según README:

```powershell
$env:DEMO_ADMIN_EMAIL = Read-Host 'Correo del ADMIN de demostración'
$demoAdminSecret = Read-Host 'Contraseña de ADMIN' -AsSecureString
$env:DEMO_ADMIN_PASSWORD = [Net.NetworkCredential]::new('', $demoAdminSecret).Password
Remove-Variable demoAdminSecret
.\scripts\demo-presencial.ps1 -BaseUrl 'http://localhost:8080' -AdjustDemoAppointmentClock
Remove-Item Env:DEMO_ADMIN_PASSWORD, Env:DEMO_ADMIN_EMAIL
```

El script crea datos ficticios mediante las rutas reales: login ADMIN → crear BARBER → registro/login CLIENT → disponibilidad/reserva → IN_PROGRESS → COMPLETED → cobro CASH → consulta propia → producto → entrada de stock → venta CARD → reintento idempotente → consulta de venta y reporte. Comprueba el 409 de segundo cobro, un solo descuento de stock y OpenAPI/Swagger. Las contraseñas de las cuentas ficticias se generan en memoria; no imprime tokens, cabeceras ni credenciales. Cada ejecución crea registros nuevos y conserva los anteriores.

**Opción temporal autorizada para la demostración:** `-AdjustDemoAppointmentClock` exige una API local, `DB_URL=jdbc:postgresql://<host>:<puerto>/barberia_demo`, las variables de conexión y `psql` en PATH (o `PSQL_PATH` local). Solo cambia inicio/fin de la cita recién creada en esa base para situar su bloque en el pasado. Los estados siguen pasando por los endpoints y el Clock y las fechas de cobro permanecen reales. No modifica stock por SQL, otras citas, precios, cobros ni datos existentes. Es una preparación explícita de demostración; no demuestra que haya transcurrido una hora real ni elimina esa regla de la API. Sin la opción, el script espera hasta el inicio y fin reales de la reserva (puede tardar más de una hora) y renueva el login al continuar.
