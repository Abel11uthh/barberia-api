# barberia-api

API REST para una barbería con dos sucursales iniciales, sin frontend ni límite fijo de sucursales. Implementa **cuentas/JWT, catálogo de servicios, reservas, operación de agenda, incidencias de citas, productos para venta e inventario por sucursal**. Incidencias de pedidos, vinculación de invitados, reprogramación, ventas, pedidos, pagos y reembolsos siguen pendientes; ver [docs/alcance.md](docs/alcance.md). Contratos y ejemplos: [catálogo](docs/catalogo.md), [citas](docs/citas.md) y [productos](docs/productos.md). Mercado Pago será el proveedor de pagos futuro; todavía no hay integración.

## Tecnologías y decisiones

| Componente | Versión o elección |
| --- | --- |
| Java | 25 LTS, sin preview |
| Spring Boot | 3.5.16 |
| Maven / Wrapper | 3.9.11 / 3.3.4 (`only-script`) |
| HTTP y validación | Spring Web / Bean Validation |
| Persistencia | Spring Data JPA y PostgreSQL; comprobado con PostgreSQL 18.1 |
| Migraciones | Flyway 11.20.3, con `flyway-database-postgresql` |
| Seguridad | Spring Security 6.5.11, OAuth2 Resource Server y Nimbus JOSE + JWT 9.37.4 |
| Contraseñas | BCrypt, coste 12 |
| Pruebas | JUnit Jupiter, AssertJ, Spring Test/MockMvc y PostgreSQL real |

Se eligió Nimbus mediante el [soporte JWT de Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): biblioteca mantenida, integración oficial para emisión y validación, y versiones compatibles administradas por Spring Boot. No se implementa un filtro criptográfico propio ni se necesita un proveedor OAuth externo. Spring Boot administra las dependencias excepto Flyway, fijado en 11.20.3 para el [soporte de PostgreSQL 18](https://documentation.red-gate.com/fd/postgresql-database-277579325.html); el 11.7.2 administrado originalmente advertía que solo se había probado hasta PostgreSQL 17.

## Requisitos y compilación

Se necesitan JDK 25, acceso a Maven Central en la primera construcción y, para arrancar o ejecutar integración, PostgreSQL accesible con una base dedicada y permisos para aplicar migraciones. La aplicación no crea bases de datos.

Seleccionar el JDK para la terminal sin desinstalar Java 21. Adaptar la ruta a la instalación local:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -version
.\mvnw.cmd clean verify
```

Maven debe indicar Java 25. En el IDE, seleccionar JDK 25 como SDK del proyecto y como JDK para importar/ejecutar Maven; nivel de lenguaje 25 sin preview. Estos ajustes son locales. En Linux/macOS, configurar `JAVA_HOME` y ejecutar `sh ./mvnw clean verify`.

El comando anterior compila, empaqueta y ejecuta **15 pruebas unitarias**, sin necesitar PostgreSQL. Las pruebas de integración requieren el perfil descrito más adelante. El JAR queda en `target/barberia-api-0.0.1-SNAPSHOT.jar`.

## Variables y ejecución

Proporcionar estas variables al proceso que arranca la aplicación; no tienen credenciales ni claves predeterminadas:

| Variable | Contenido |
| --- | --- |
| `DB_URL` | URL JDBC, por ejemplo `jdbc:postgresql://localhost:5432/barberia_db` |
| `DB_USERNAME` | Usuario autorizado en esa base |
| `DB_PASSWORD` | Contraseña PostgreSQL |
| `JWT_SECRET_BASE64` | Clave aleatoria de al menos 32 bytes, codificada en Base64 |
| `JWT_EXPIRATION_SECONDS` | Duración obligatoria entre 60 y 3600 segundos; se recomienda 900 |
| `SERVER_PORT` | Opcional; 8080 por defecto |

Reutilizar las variables existentes. No imprimirlas. Para completar las que falten en PowerShell:

```powershell
if (!$env:DB_URL) { $env:DB_URL = 'jdbc:postgresql://localhost:5432/barberia_db' }
if (!$env:DB_USERNAME) { $env:DB_USERNAME = Read-Host 'Usuario PostgreSQL' }
if (!$env:DB_PASSWORD) {
    $dbSecret = Read-Host 'Contraseña PostgreSQL' -AsSecureString
    $env:DB_PASSWORD = [Net.NetworkCredential]::new('', $dbSecret).Password
    Remove-Variable dbSecret
}
# Desarrollo local: generar una clave solo si todavía no se proporcionó una.
if (!$env:JWT_SECRET_BASE64) {
    $keyBytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($keyBytes)
    $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($keyBytes)
    $rng.Dispose()
    Remove-Variable keyBytes, rng
}
$env:JWT_EXPIRATION_SECONDS = '900'
.\mvnw.cmd spring-boot:run
```

El ejemplo no imprime ni guarda secretos. En un entorno persistente, suministrar la misma clave mediante la configuración protegida del entorno; cambiarla invalida todos los tokens anteriores. No regenerarla en cada arranque de ese entorno. Spring Boot no carga `.env` automáticamente. También se pueden configurar las variables en la ejecución local del IDE, sin versionarlas. Tras compilar, es posible usar `java -jar target/barberia-api-0.0.1-SNAPSHOT.jar` con el mismo entorno. Detener con `Ctrl+C`.

Flyway aplica `V1__create_accounts_and_roles.sql`: cuentas, catálogo de roles, relación de varios roles y restricciones de correo único y normalizado. Después, Hibernate comprueba las entidades con `ddl-auto=validate`. La inicialización SQL está desactivada, `clean-disabled=true` y `baseline-on-migrate=false`. No se borran esquemas ni se adopta automáticamente un esquema previo sin historial de migraciones; si Flyway detecta ese caso, revisar la base antes de continuar. No usar `ddl-auto=update` ni editar una migración ya aplicada: añadir una nueva versión.

V2 (`V2__business_catalog.sql`) añade sucursales, horarios, servicios y perfiles, con los datos iniciales acordados. V3 añade citas y precios históricos. V4 (`V4__agenda_operations.sql`) adapta citas existentes, incorpora estados, invitados, auditoría, bloqueos y una tabla común de ocupaciones con exclusión PostgreSQL contra cualquier solapamiento. V5 añade contador de incidencias de citas, bloqueo comercial e historial ordenado de eventos. V6 añade productos, existencias por sucursal e historial de movimientos, sin productos ni precios iniciales. V1–V5 permanecen intactas.

V4 necesita `btree_gist`: la instala en la base conectada si el usuario de migración tiene permisos; alternativamente puede preinstalarla su administrador. No cambia privilegios globales. Consultar [instalación, permisos y garantía de concurrencia](docs/citas.md#extensión-y-permisos) antes de migrar. La nueva protección cubre intervalos distintos que se solapan, no solo comienzos iguales.

## Contrato HTTP de autenticación

Todos los cuerpos usan JSON. Los campos desconocidos se rechazan con 400, incluyendo `roles`, `active` o `id` en el registro.

| Método y ruta | Entrada | Resultado |
| --- | --- | --- |
| `POST /api/v1/auth/register` | `name`, `phone`, `email`, `password` | 201: `id`, `name`, `phone`, `email`, `roles` (solo `CLIENT`) |
| `POST /api/v1/auth/login` | `email`, `password` | 200: `accessToken`, `tokenType` (`Bearer`), `expiresIn` (segundos), `expiresAt` (UTC) |
| `GET /api/v1/users/me` | Cabecera `Authorization: Bearer <accessToken>` | 200: datos públicos y roles actuales de la cuenta |

Nombre: 1–120 caracteres; teléfono: 1–32; correo válido: hasta 254. Se eliminan espacios exteriores de nombre, teléfono y correo; el correo se convierte a minúsculas antes de consultar o almacenar. PostgreSQL también impide correos duplicados y valores sin normalizar, incluso ante registros concurrentes. La contraseña requiere al menos 12 caracteres y como máximo 72 bytes UTF-8, límite de BCrypt; no se recorta ni se normaliza. La API nunca devuelve contraseñas ni hashes.

Ejemplo PowerShell con datos ficticios y contraseña introducida de forma oculta; ejecutarlo una vez para registrar ese correo:

```powershell
$baseUrl = 'http://localhost:8080'
$userSecret = Read-Host 'Contraseña de la nueva cuenta (mínimo 12 caracteres)' -AsSecureString
$userPassword = [Net.NetworkCredential]::new('', $userSecret).Password
$registration = @{
    name = 'Cliente de ejemplo'; phone = '5550100000'
    email = 'cliente@example.test'; password = $userPassword
} | ConvertTo-Json
$account = Invoke-RestMethod "$baseUrl/api/v1/auth/register" -Method Post -ContentType 'application/json' -Body $registration
$login = @{email='cliente@example.test'; password=$userPassword} | ConvertTo-Json
$session = Invoke-RestMethod "$baseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $login
$headers = @{Authorization="Bearer $($session.accessToken)"}
$me = Invoke-RestMethod "$baseUrl/api/v1/users/me" -Headers $headers
$me # Solo datos públicos de esta cuenta; no imprimir $session ni $headers.
Remove-Variable userSecret, userPassword, registration, login, session, headers
```

Flujo: **registro → login → JWT → endpoint protegido**. Registrar crea una cuenta activa con `CLIENT`; iniciar sesión verifica BCrypt y el estado de la cuenta. El JWT contiene la identidad UUID y tiempos de emisión/expiración. Cada petición protegida valida firma HS256, expiración, emisor y audiencia, y consulta la cuenta en PostgreSQL para obtener su estado y roles actuales. Los permisos no se aceptan del cliente ni de claims de roles del token. Una cuenta desactivada queda rechazada incluso con un JWT aún vigente.

La API es stateless: no crea sesión ni usa cookies de autenticación. Solo registro y login son públicos entre los endpoints implementados; otras rutas quedan denegadas. La raíz ya no es un endpoint de comprobación 404: sin autenticación responde 401. Usar HTTPS al exponer la API. No registrar cuerpos de login/registro ni cabeceras Bearer en clientes o intermediarios.

Los errores usan el mismo formato, sin revelar datos internos:

```json
{"timestamp":"2026-10-17T00:00:00Z","status":401,"code":"UNAUTHORIZED","message":"Credenciales inválidas."}
```

| Estado | Caso |
| --- | --- |
| 400 | Datos inválidos, JSON incorrecto o campos no permitidos |
| 401 | Login incorrecto o cuenta inactiva (mismo mensaje genérico); JWT ausente, inválido, expirado o cuenta ya desactivada en una ruta protegida |
| 403 | Cuenta autenticada intentando una ruta no permitida |
| 404 | Recurso de catálogo inexistente o no disponible para esa consulta |
| 409 | Correo ya registrado, perfil duplicado o asignación a sucursal inactiva |

No hay refresh tokens ni revocación individual de tokens en esta versión. Su vigencia está limitada por `JWT_EXPIRATION_SECONDS`; cerrar sesión en un cliente consiste en descartar el token, que permanece válido hasta expirar salvo desactivación de cuenta o rotación global de clave. El campo `active` representa acceso a la cuenta: el bloqueo comercial de citas es independiente y no impide iniciar sesión ni consultar compromisos existentes. Su aplicación a futuras compras queda preparada, sin implementar pedidos.

## Primer administrador

El mecanismo está **desactivado por defecto**. Para una inicialización explícita, proporcionar además:

| Variable | Uso |
| --- | --- |
| `BOOTSTRAP_ADMIN_ENABLED` | `true` para habilitar la inicialización en ese arranque |
| `BOOTSTRAP_ADMIN_NAME` | Nombre válido |
| `BOOTSTRAP_ADMIN_PHONE` | Teléfono válido |
| `BOOTSTRAP_ADMIN_EMAIL` | Correo válido, normalizado como en el registro |
| `BOOTSTRAP_ADMIN_PASSWORD` | Contraseña que cumpla las mismas reglas del registro |

Con las variables de conexión y JWT ya configuradas:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:BOOTSTRAP_ADMIN_NAME = Read-Host 'Nombre del administrador'
$env:BOOTSTRAP_ADMIN_PHONE = Read-Host 'Teléfono'
$env:BOOTSTRAP_ADMIN_EMAIL = Read-Host 'Correo'
$adminSecret = Read-Host 'Contraseña del administrador' -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = [Net.NetworkCredential]::new('', $adminSecret).Password
Remove-Variable adminSecret
.\mvnw.cmd spring-boot:run
```

Si el correo no existe, crea una cuenta activa con `ADMIN`. Si ya existe, **no cambia contraseña, estado ni roles**, aunque sea un cliente; no sirve para recuperar cuentas ni elevar privilegios. No hay credenciales en las migraciones. Comprobar el login y el rol con `/users/me`.

Tras la creación, detener la aplicación, deshabilitar el mecanismo, retirar las credenciales del entorno y reiniciar:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = 'false'
Remove-Item Env:BOOTSTRAP_ADMIN_NAME, Env:BOOTSTRAP_ADMIN_PHONE, Env:BOOTSTRAP_ADMIN_EMAIL, Env:BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
.\mvnw.cmd spring-boot:run
```

Si esas variables se definieron también en la configuración persistente del IDE o del sistema, retirarlas allí. El bootstrap crea únicamente `ADMIN`. Después, una operación administrativa de asignación de perfil permite añadir `BARBER` conservando `ADMIN` y la contraseña; ver [perfiles de barbero](docs/catalogo.md#cuentas-y-perfiles-de-barbero). No existe un endpoint genérico de asignación de roles.

## Productos e inventario

ADMIN mantiene productos, entradas y ajustes auditados por sucursal; CLIENT consulta productos activos y stock de sucursales activas (incluido cero); BARBER consulta su propia sucursal. El stock inicial se registra con una entrada y motivo, sin editar tablas. No hay productos iniciales, ventas, pedidos ni reservas de stock. Una venta futura deberá guardar su precio aplicado. Rutas, permisos y ejemplos en [docs/productos.md](docs/productos.md). No se requieren nuevas variables de entorno.

## Pruebas con PostgreSQL

Crear una base exclusiva llamada **`barberia_auth_test`** y conceder acceso al usuario de pruebas. No utilizar `barberia_db`. Definir `TEST_DB_URL`, `TEST_DB_USERNAME` y `TEST_DB_PASSWORD` para ese entorno. Ejemplo cuando se reutiliza el mismo servidor y usuario local ya configurado:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/barberia_auth_test'
$env:TEST_DB_USERNAME = $env:DB_USERNAME
$env:TEST_DB_PASSWORD = $env:DB_PASSWORD
.\mvnw.cmd -Ppostgres-tests clean verify
```

El perfil contiene **15 pruebas unitarias y 99 de integración** (16 de autenticación, 18 de catálogo de servicios/perfiles, 22 de reservas, 32 de operación de agenda e incidencias y 11 de productos/inventario). Las pruebas rechazan cualquier URL que no termine exactamente en `/barberia_auth_test`, sin parámetros, y comprueban `current_database()`. No toman la conexión de negocio como alternativa. Flyway migra únicamente esa base; no se usa H2 ni se ejecutan limpieza, truncado o borrado de esquemas. Los datos aleatorios de prueba se acumulan en ella. La clave JWT de pruebas se genera en memoria y no depende del secreto de la aplicación.

La integración comprueba registro, normalización y restricciones PostgreSQL, duplicados concurrentes, rechazo de asignación de privilegios, login válido/inválido, JWT válido/ausente/expirado/manipulado, cuentas inactivas, varios roles y cambios de permisos actuales, errores JSON e inicialización administrativa sin elevar cuentas existentes.

## Productos e inventario — verificación del 17 de octubre de 2026

- PR #5 confirmado integrado como `c2d46d1`; main se actualizó con `git pull --ff-only` y se creó `feature/product-inventory`. La implementación se entregó inicialmente sin commit, push ni PR; su publicación fue autorizada después para revisión, sin merge.
- Compilación y empaquetado con Java 25.0.1 y Maven Wrapper correctos. La ejecución completa final aprobó **15 unitarias y 97 integraciones**, sin fallos ni omisiones, exclusivamente en PostgreSQL 18.1 / `barberia_auth_test`. V6 aplicada allí, V1–V5 intactas y Hibernate `validate` correcto.
- Las 9 integraciones nuevas comprueban SKU normalizado/duplicado (también al actualizar), precio y cantidades inválidos, cantidades fraccionarias rechazadas sin truncar, stock independiente y cero visible, total calculado, concurrencia sin perder unidades ni permitir negativos, rollback conjunto ante fallo del movimiento, permisos acumulativos, sucursal propia de BARBER, inactivos, filtros y privacidad. Se ejecutaron también las regresiones completas de cuentas, catálogo y citas.
- Una ejecución se detuvo por falta de memoria nativa del JDK. La ejecución final utilizó `MAVEN_OPTS=-Xmx256m` solo en esa sesión y las opciones siguientes, sin guardarlas en la configuración del proyecto:

```powershell
.\mvnw.cmd -B -ntp '-Dmaven.repo.local=.local/maven-repository' -Ppostgres-tests '-DargLine=-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=96m' '-DreuseForks=false' clean verify
```

Se reutilizó `TEMP/TMP` corto en `.local/tmp` para las pruebas HTTP de Windows. La regresión de reservas arrancó un servidor HTTP real en la base de pruebas; no se hizo un arranque manual adicional ni se migró `barberia_db`. No se implementaron ventas, pedidos, reservas de stock ni pagos.

## Revisión final de productos e inventario

- Corregida la suma del stock para conservar el total exacto aunque supere el rango de `long`; el contrato sigue devolviendo un número JSON calculado, sin segundo total persistido.
- Añadidas pruebas de creación concurrente del mismo SKU normalizado (201/409 con una sola fila), suma superior al rango de `long` y rechazo directo de stock negativo por PostgreSQL.
- Maven Wrapper con las opciones temporales de memoria anteriores y `-Dit.test=ProductInventoryPostgresIT verify`: **BUILD SUCCESS**, compilación/empaquetado, 15 unitarias y las 11 integraciones afectadas aprobadas. Se reutilizan las 88 integraciones de cuentas, catálogo y citas aprobadas en la ejecución completa anterior. No se afirma una nueva ejecución conjunta de las 99 integraciones.
- V1–V5 intactas, pruebas solo en `barberia_auth_test` y archivos revisados sin secretos o temporales. AGENTS solo actualiza el alcance implementado y conserva sus restricciones. Publicación autorizada para revisión, sin merge.

## Incidencias de citas — verificación del 17 de octubre de 2026

- Java 25.0.1 y Maven Wrapper: `-Ppostgres-tests clean verify`, **BUILD SUCCESS**; compilación, empaquetado, 15 pruebas unitarias y 88 de integración sin fallos. V5 migró y Hibernate validó el esquema en PostgreSQL 18.1, exclusivamente en `barberia_auth_test`. Se usó `TEMP/TMP` local corto para las pruebas HTTP en Windows; no se cambió el entorno global.
- La suite cubre advertencia en la segunda incidencia, bloqueo en la tercera, reinicio por COMPLETED, personal e invitados sin penalización, login y consultas tras bloquear, cancelación existente, reactivación solo ADMIN con motivo e historial, transiciones repetidas y dos citas concurrentes del mismo cliente. Comprueba COMPLETED solo desde el fin, NO_SHOW al fin y exactamente 15 minutos después, rechazo al segundo siguiente, cita IN_PROGRESS y lunes sin resultado → martes COMPLETED → intento tardío el miércoles sin penalización. Se repitieron las regresiones de horarios, permisos y ocupaciones.
- En la carrera reserva/presencial PostgreSQL puede devolver SQLSTATE 40P01 al escoger una víctima entre inserciones especulativas de ocupaciones. Esa escritura ahora lo traduce a 409, junto con 23P01 y 23505. Las carreras de la suite dan 201/409. No se ejecutó un arranque manual nuevo de la aplicación ni se migró `barberia_db`.
- `GET /api/v1/users/me/booking-status` expone solo estado y contador propios; `POST /api/v1/admin/clients/{id}/reactivate` exige ADMIN y motivo. La cancelación propia devuelve `bookingStatus` con la advertencia cuando corresponda. Detalles y ejemplos en [docs/citas.md](docs/citas.md#incidencias-consecutivas-y-bloqueo-comercial).

En esa entrega seguían pendientes las incidencias de pedidos, pagos, reembolsos, inventario y ventas. El inventario se incorpora en la entrega siguiente.

## Verificación de operación de agenda — 17 de octubre de 2026

- Java 25.0.1 y Maven Wrapper: `mvnw.cmd -B -ntp -Dmaven.repo.local=.local/maven-repository -Ppostgres-tests clean verify`, **BUILD SUCCESS**; compilación, empaquetado, **15 unitarias y 79 integraciones** sin fallos ni omisiones.
- V4 y btree_gist aplicados exclusivamente en PostgreSQL 18.1, base `barberia_auth_test`. Se migraron las reservas existentes y Hibernate validó el esquema.
- Tres carreras nuevas con transacciones independientes y barreras sin sleeps: presencial/presencial con inicios distintos, reserva/presencial y bloqueo/reserva. Cada una dejó exactamente una operación persistida, con respuestas 201 y 409.
- Las 23 pruebas nuevas incluyen ejemplos presenciales, permisos, privacidad de agenda, precios históricos, invitados sin cuentas, estados/idempotencia, cancelación a 60/59 minutos, auditoría y conservación/liberación de ocupación. Se ejecutaron todas las regresiones.
- Arranque automatizado HTTP de la suite anterior en puerto 51307; las nuevas operaciones se verificaron con MockMvc y PostgreSQL real. No se realizó una prueba manual nueva ni un arranque contra barberia_db. El proceso de pruebas terminó.
- TEMP/TMP se limitaron a la ruta corta local ignorada descrita abajo; no se cambiaron ajustes globales ni se incluyeron secretos. No se añadieron dependencias Java ni se cambiaron versiones.
- Consulta final de solo lectura: barberia_db conserva cero tablas de usuario. V1/V2/V3 sin diff; git diff --check correcto; 103 archivos candidatos revisados sin coincidencias de secretos del entorno/tokens reales ni archivos locales/generados.

## Historial: verificación de citas — 17 de octubre de 2026

- Compilación y empaquetado correctos con Java 25.0.1 y Maven Wrapper. Comando final: `mvnw.cmd -B -ntp -Dmaven.repo.local=.local/maven-repository -Ppostgres-tests verify`, **BUILD SUCCESS**.
- **15 pruebas unitarias y 56 de integración**, sin fallos ni omisiones: 7 unitarias de horarios, las 8 previas, 22 integraciones de citas y las 34 previas.
- V3 aplicada y Hibernate validado en PostgreSQL 18.1, exclusivamente en `barberia_auth_test`. V1 y V2 no cambian.
- Servidor de prueba iniciado en el puerto HTTP 55151. Dos peticiones HTTP concurrentes, con transacciones independientes y barrera antes de guardar, dieron exactamente **201 y 409**, una sola cita y sus copias históricas completas.
- No se realizó una prueba HTTP manual ni un arranque nuevo contra la base de negocio. El servidor de integración terminó con Maven.
- Consulta final de solo lectura: `barberia_db` conserva cero tablas de usuario. V1/V2 sin diff; `git diff --check` correcto. Revisión de 87 archivos candidatos sin coincidencias de secretos del entorno, tokens reales ni archivos locales/generados.
- Se corrigió un import ambiguo detectado al compilar los tests. El primer intento de integración no inició el nuevo contexto HTTP por un error del JDK al crear un socket interno con la ruta temporal larga de Windows; sí pasaron las pruebas previas. La ejecución final completa pasó usando `TEMP` y `TMP` cortos, solo en esa sesión.

Si el entorno Windows presenta `Unable to establish loopback connection` al iniciar el cliente HTTP del JDK, usar una ruta temporal corta local antes de Maven:

```powershell
$testTemp = Join-Path (Get-Location) '.local\tmp'
New-Item -ItemType Directory -Path $testTemp -Force | Out-Null
$env:TEMP = $testTemp
$env:TMP = $testTemp
.\mvnw.cmd -Ppostgres-tests verify
```

`.local/` está ignorado. No modificar variables globales ni desactivar las pruebas HTTP. Los contratos y la garantía contra dobles reservas se detallan en [citas.md](docs/citas.md).

## Historial: verificación del catálogo — 17 de octubre de 2026

- `mvnw.cmd -B -ntp -Dmaven.repo.local=.local/maven-repository -Ppostgres-tests clean verify`: **BUILD SUCCESS**, Java 25.0.1 y Maven 3.9.11.
- **8 pruebas unitarias y 34 de integración correctas**, sin fallos ni omisiones. Se comprobaron permisos, precios, horarios, datos iniciales, perfiles, rollback, concurrencia, privacidad y regresiones de autenticación.
- PostgreSQL 18.1 real en `barberia_auth_test`: V2 aplicada y esquema validado con Hibernate; V1 sin modificaciones. No se aplicaron migraciones en `barberia_db`.
- Consulta posterior de solo lectura: `barberia_db` conserva cero tablas de usuario. `git diff --check` correcto; revisión de archivos sin secretos detectados ni archivos generados entre candidatos.
- El primer intento de integración falló por falta de `TEST_DB_USERNAME`, antes de conectar. Tras proporcionar el usuario confirmado y reutilizar `DB_PASSWORD` sin imprimirla, la ejecución completa pasó.
- **No se realizaron nuevas pruebas manuales con un servidor HTTP en esta entrega**. Los tests usan MockMvc con el contexto Spring completo y PostgreSQL real; los arranques manuales siguientes pertenecen a la entrega anterior.

## Historial de verificación: cuentas y JWT

| Comprobación | Resultado |
| --- | --- |
| Compilación y empaquetado | Java 25.0.1, Maven 3.9.11, `-Ppostgres-tests clean verify`: **BUILD SUCCESS** |
| Pruebas unitarias | 8 ejecutadas, sin fallos |
| Integración | 16 ejecutadas con PostgreSQL 18.1 en `barberia_auth_test`, sin fallos ni omisiones |
| Migración y validación JPA | V1 aplicada en la base de pruebas y validada en las ejecuciones posteriores |
| Arranque real | Dos ejecuciones con Maven Wrapper y Java 25, PostgreSQL conectado y Spring Boot iniciado; puertos asignados 63788 y 61718 |
| Comprobación HTTP manual | Registro 201; login y perfil 200; duplicado 409; asignación de ADMIN 400; sin JWT 401; ruta no permitida 403 |
| Bootstrap real | Creó ADMIN; segundo arranque conservó la contraseña original pese a cambiar la variable de inicialización |
| Finalización | Detenidos únicamente los dos árboles de procesos iniciados para verificar |
| Base de negocio | Consulta posterior de solo lectura: `barberia_db` conserva cero tablas de usuario |
| Revisión de archivos | `git diff --check` correcto; sin coincidencias con secretos del entorno ni patrones de claves/tokens en archivos del proyecto; sin archivos locales/generados entre candidatos |

La verificación usa una caché local ignorada: `MAVEN_USER_HOME=.local/maven-home` y `-Dmaven.repo.local=.local/maven-repository`. Maven emite una advertencia de su Guice interno sobre `sun.misc.Unsafe`, y el soporte de pruebas de Mockito avisa de carga dinámica de agente en Java 25; no impiden las comprobaciones. No se añadieron opciones para ocultarlas.

La comprobación histórica de la base inicial arrancó en `barberia_db` con cero tablas y `ddl-auto=none`. Esta entrega cambia a migraciones y `validate`: las verificaciones de autenticación utilizan la base exclusiva de pruebas; todavía no aplican V1 en `barberia_db`.

## Organización

Monolito bajo `com.barberia.api`: `accounts` contiene cuentas y roles; `auth`, autenticación y seguridad; `branches`, sucursales y horarios semanales; `services`, catálogo de servicios; `barbers`, perfiles y asignaciones; `appointments`, disponibilidad y reservas propias. `shared.error` unifica errores HTTP y `shared.pagination` limita y representa listados. No se exponen entidades JPA.

Se respeta **controller → service → repository**: contrato HTTP y validación mediante DTOs, reglas y transacciones en servicios, persistencia en repositorios. La inyección es por constructor; las entidades JPA no se exponen como respuestas. No se crean módulos vacíos para requisitos futuros. Las instrucciones de trabajo están en [AGENTS.md](AGENTS.md).
