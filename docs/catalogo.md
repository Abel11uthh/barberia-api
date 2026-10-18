# Sucursales, servicios y barberos

La entrega del catálogo incorporó los módulos `branches`, `services` y `barbers`. Conserva Java 25, Spring Boot 3.5.16, el login JWT y las convenciones de errores existentes. Las entregas posteriores de [citas](citas.md) añaden reservas y operación de agenda, incluida atención inmediata y bloqueos individuales. [Productos](productos.md) incorpora inventario y [operación presencial](operacion-presencial.md) incorpora ventas, cobros y reportes. Los pagos en línea siguen pendientes.

## Permisos y endpoints

Todas las rutas requieren `Authorization: Bearer <accessToken>` del login existente. Las rutas `/api/v1/admin/**` requieren `ADMIN`; también se comprueba ese permiso en los servicios administrativos. Conocer un identificador no concede permisos. Un token de cuenta desactivada sigue siendo rechazado.

| Método | Ruta | Permiso y resultado |
| --- | --- | --- |
| GET | `/api/v1/branches` y `/api/v1/branches/{id}` | Autenticado: solo sucursales activas |
| GET | `/api/v1/services` y `/api/v1/services/{id}` | Autenticado: solo servicios activos |
| GET | `/api/v1/barbers` y `/api/v1/barbers/{id}` | Autenticado: perfil, cuenta y sucursal activos |
| GET | `/api/v1/admin/branches` y `/api/v1/admin/branches/{id}` | ADMIN: incluye inactivos |
| POST | `/api/v1/admin/branches` | ADMIN: crea sucursal activa, 201 |
| PUT | `/api/v1/admin/branches/{id}` | ADMIN: reemplaza nombre, dirección y horario completo, 200 |
| PATCH | `/api/v1/admin/branches/{id}/deactivate` | ADMIN: desactiva sin borrar, 204 |
| GET | `/api/v1/admin/services` y `/api/v1/admin/services/{id}` | ADMIN: incluye inactivos |
| POST | `/api/v1/admin/services` | ADMIN: crea servicio activo, 201 |
| PUT | `/api/v1/admin/services/{id}` | ADMIN: reemplaza datos del servicio, 200 |
| PATCH | `/api/v1/admin/services/{id}/deactivate` | ADMIN: desactiva sin borrar, 204 |
| GET | `/api/v1/admin/barbers` y `/api/v1/admin/barbers/{id}` | ADMIN: incluye perfiles no disponibles y sus estados |
| POST | `/api/v1/admin/barbers` | ADMIN: crea cuenta y perfil conjuntamente, 201 |
| POST | `/api/v1/admin/barbers/assign` | ADMIN: asigna perfil a cuenta existente, 201 |
| PATCH | `/api/v1/admin/barbers/{id}/deactivate` | ADMIN: desactiva solo el perfil, 204 |

No existen operaciones DELETE, traslados, un endpoint genérico de roles ni reactivación en esta versión. Actualizar nombre/horario/precio no reactiva un registro desactivado. Un segundo perfil se rechaza incluso cuando el primero está inactivo. La desactivación de una sucursal conserva sus perfiles; estos dejan de aparecer en el catálogo disponible. Desactivar un perfil conserva la cuenta y todos sus roles, incluido `ADMIN` del dueño. El rol `BARBER` tampoco se elimina; estar disponible exige los tres estados activos.

Los errores conservan `{timestamp, status, code, message}`: 400 para datos o paginación inválidos, 401 para autenticación ausente/inválida, 403 para permisos insuficientes, 404 para un identificador inexistente o no visible en el catálogo normal, y 409 para correo duplicado, perfil duplicado o asignación a sucursal inactiva. Campos desconocidos en los DTOs —por ejemplo, roles, duración o estado activo— se rechazan. Los PATCH de desactivación no requieren cuerpo y no permiten cambiar la sucursal.

## Paginación y privacidad

Todos los listados, incluidos los administrativos, aceptan `page` (desde 0), `size` (1–100, por defecto 20) y un único `sort=campo,asc|desc` (por defecto `name,asc`). Se añade `id` como desempate estable. Se rechazan campos de ordenación no permitidos.

| Recurso | Campos de ordenación permitidos | Filtro |
| --- | --- | --- |
| Sucursales | `id`, `name` | — |
| Servicios | `id`, `name`, `price`, `type` | — |
| Barberos | `id`, `name` | `branchId` opcional, también en rutas administrativas |

Formato: `{items: [...], page: 0, size: 20, totalElements: 2, totalPages: 1}`. Un filtro sin coincidencias devuelve página vacía. Los barberos disponibles son aquellos habilitados en el catálogo; los bloques horarios se consultan mediante el endpoint de disponibilidad de [citas](citas.md).

Cada elemento del catálogo normal de barberos contiene **exactamente** `id` (del perfil), `name` y `branch: {id, name}`. No incluye identificador de cuenta, correo, teléfono, contraseña o hash. La respuesta administrativa añade únicamente `active`, `accountActive` y `branchActive`; tampoco revela esas credenciales o datos de contacto.

## Horario semanal

La zona del negocio es `America/Mexico_City`. Cada sucursal tiene exactamente siete entradas, una por día (`MONDAY` … `SUNDAY`), sin duplicados. Cada día admite una apertura/cierre y, opcionalmente, un descanso. No hay calendarios especiales ni intervalos nocturnos.

- Día abierto: `closed=false`, `opensAt` anterior a `closesAt`.
- Descanso opcional: ambos extremos presentes, inicio anterior al fin y contenido en apertura/cierre; se permiten límites coincidentes con la apertura o el cierre.
- Día cerrado: `closed=true`; los cuatro campos horarios se omiten o valen `null`.
- Nombre obligatorio, hasta 120 caracteres; dirección opcional, hasta 300. No se inventan direcciones iniciales.

Al crear o actualizar una sucursal se envía la semana completa. Ejemplo PowerShell de cuerpo, sin autenticación ni secretos incorporados:

```powershell
$weeklyHours = @('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY') | ForEach-Object {
    @{day=$_; closed=$false; opensAt='08:00'; closesAt='18:00'; breakStart='10:00'; breakEnd='11:00'}
}
$weeklyHours += @{day='SUNDAY'; closed=$true}
$branchBody = @{name='Sucursal de ejemplo'; address=$null; weeklyHours=$weeklyHours} | ConvertTo-Json -Depth 5
# Usar $headers de una sesión ADMIN sin imprimirlo.
$branch = Invoke-RestMethod "$baseUrl/api/v1/admin/branches" -Method Post -ContentType 'application/json' -Headers $headers -Body $branchBody
```

## Servicios compartidos

El catálogo y los precios son comunes a todas las sucursales. `name` es obligatorio (máximo 120), `description` opcional (máximo 1000), `price` debe ser positivo y tener como máximo dos decimales y diez dígitos enteros, y `type` es `HAIRCUT` o `ADD_ON`. Se usa `BigDecimal` y PostgreSQL `NUMERIC(12,2)`, sin redondear importes de entrada con exceso de decimales. La moneda es siempre `MXN` y no se recibe del cliente.

Ejemplo de cuerpo para POST/PUT:

```json
{"name":"Servicio de ejemplo","description":null,"price":100.00,"type":"HAIRCUT"}
```

No hay duración por servicio: todas las citas ocupan una hora completa. Las reservas comienzan a horas exactas; la atención inmediata comienza al minuto del reloj del servidor.

## Cuentas y perfiles de barbero

Crear nueva cuenta y perfil (`POST /api/v1/admin/barbers`):

```powershell
$staffSecret = Read-Host 'Contraseña de la nueva cuenta de barbero' -AsSecureString
$staffPassword = [Net.NetworkCredential]::new('', $staffSecret).Password
$barberBody = @{
    account = @{name='Barbero de ejemplo'; phone='5550100000'; email='barbero@example.test'; password=$staffPassword}
    branchId = $branch.id
} | ConvertTo-Json -Depth 4
$barber = Invoke-RestMethod "$baseUrl/api/v1/admin/barbers" -Method Post -ContentType 'application/json' -Headers $headers -Body $barberBody
Remove-Variable staffSecret, staffPassword, barberBody
```

La cuenta nueva recibe únicamente `BARBER`; reutiliza normalización del correo, validaciones de registro, contraseña y BCrypt. El personal usa `/api/v1/auth/login` sin un mecanismo distinto. La contraseña se introduce de forma oculta y nunca se incluye como literal en el ejemplo.

Para una cuenta existente, enviar a `/api/v1/admin/barbers/assign`:

```powershell
$assignment = @{accountId=$existingAccountId; branchId=$branch.id} | ConvertTo-Json
$barber = Invoke-RestMethod "$baseUrl/api/v1/admin/barbers/assign" -Method Post -ContentType 'application/json' -Headers $headers -Body $assignment
```

`$existingAccountId` es el UUID de una cuenta ya creada; para el propio administrador puede obtenerse de `/api/v1/users/me`. Se añade únicamente `BARBER`, conservando contraseña, estado y demás roles. Una cuenta inactiva sigue inactiva y su perfil no aparece como disponible. La sucursal de destino debe existir y estar activa. No se admite cambiarla después.

## Modelo, migración y transacciones

V2 crea `branches`, `branch_hours`, `catalog_services` y `barber_profiles`; V1 se mantiene intacta. El horario es una colección de valores de la sucursal, con clave `(branch_id, day)` y restricciones SQL sobre apertura y descanso. El catálogo no tiene relación de precio por sucursal ni campo de duración. Cada perfil referencia una cuenta única y una sucursal: relación uno a uno con cuenta y muchos a uno con sucursal, protegidas por claves foráneas y unicidad de `account_id`.

Los controllers solo delegan en servicios y devuelven DTOs. Crear cuenta y perfil ocurre dentro de una única transacción: si falla el perfil, se revierte también la cuenta y sus roles. Asignar perfil y añadir `BARBER` es igualmente atómico. Se bloquea la fila de sucursal para serializar asignación/desactivación y la de cuenta para serializar perfiles simultáneos hacia distintas sucursales. Actualizar/desactivar sucursales y servicios también bloquea la fila correspondiente.

Datos iniciales modificables por ADMIN:

| Sucursal | Horario inicial | Descanso |
| --- | --- | --- |
| Sucursal principal | Lunes–sábado 08:00–18:00; domingo cerrado | 10:00–11:00 |
| Sucursal 2 | Lunes–sábado 08:00–18:00; domingo cerrado | 11:00–12:00 |

| Servicio | Precio MXN | Tipo |
| --- | --- | --- |
| Corte básico | 100.00 | HAIRCUT |
| Corte escolar | 120.00 | HAIRCUT |
| Corte personalizado | 150.00 | HAIRCUT |
| Arreglo de barba | 50.00 | ADD_ON |
| Arreglo de ceja | 30.00 | ADD_ON |

No se crean cuentas o perfiles iniciales ni se impone un máximo de sucursales o barberos. Flyway aplica estas inserciones una sola vez y no restablece sus valores en cada arranque. Se conserva `ddl-auto=validate`, limpieza deshabilitada y sin baseline automático.

## Verificación

Ejecutar con JDK 25 y Maven Wrapper, configurando `TEST_DB_URL`, `TEST_DB_USERNAME` y `TEST_DB_PASSWORD` como indica el README:

```powershell
.\mvnw.cmd -Ppostgres-tests clean verify
```

Se comparte la validación de conexión entre las pruebas de autenticación y catálogo: solo se admite la base dedicada `barberia_auth_test`, sin parámetros JDBC adicionales; antes de cada prueba se comprueba `current_database()`. No se usa H2, no se limpia la base y cada prueba crea sus propios datos aleatorios. Los datos iniciales se consultan sin modificarlos.

Las pruebas de catálogo cubren permisos ADMIN/CLIENT/BARBER, consultas autenticadas, semillas, horarios, precios, paginación, errores, creación y asignación de perfiles, concurrencia, privacidad y desactivación. Una prueba provoca un fallo controlado al persistir el perfil mediante un spy del repositorio para comprobar el rollback real de la cuenta en PostgreSQL. Las demás operaciones usan los repositorios reales. Se ejecutan también las pruebas anteriores de autenticación.

Resultado del 17 de octubre de 2026: **BUILD SUCCESS**, 8 pruebas unitarias y 34 de integración (16 previas y 18 nuevas), sin fallos, errores ni omisiones. V2 se aplicó únicamente en `barberia_auth_test`. El primer intento se detuvo por falta de usuario de conexión; se repitió correctamente después de recibirlo del usuario. No se realizó una comprobación manual de servidor HTTP en esta entrega; MockMvc y la conexión real a PostgreSQL no se presentan como tal.
