# barberia-api

API REST para una barbería con dos sucursales iniciales, sin frontend ni límite fijo de sucursales. Implementa **cuentas y autenticación JWT, sucursales, catálogo compartido, perfiles de barberos y disponibilidad/reserva de citas para clientes autenticados**. Cancelaciones, reprogramación, inasistencias, invitados, bloqueos, inventario, ventas y pagos siguen pendientes; ver [docs/alcance.md](docs/alcance.md). Contratos y ejemplos: [catálogo](docs/catalogo.md) y [citas](docs/citas.md). Mercado Pago será el proveedor de pagos futuro; todavía no hay integración.

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

V2 (`V2__business_catalog.sql`) añade sucursales con horario semanal, servicios compartidos y perfiles de barberos. Incluye las dos sucursales y cinco servicios iniciales acordados, sin direcciones ni cuentas inventadas. V3 (`V3__appointments.sql`) añade citas, copias históricas de servicios y protección PostgreSQL contra reservas simultáneas. V1 y V2 permanecen intactas. Al arrancar sobre una base aún sin migrar, Flyway aplicará las tres versiones.

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

No hay refresh tokens ni revocación individual de tokens en esta versión. Su vigencia está limitada por `JWT_EXPIRATION_SECONDS`; cerrar sesión en un cliente consiste en descartar el token, que permanece válido hasta expirar salvo desactivación de cuenta o rotación global de clave. El campo `active` representa acceso a la cuenta: los futuros bloqueos para reservar/comprar se modelarán aparte y no impedirán iniciar sesión ni consultar compromisos existentes.

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

## Pruebas con PostgreSQL

Crear una base exclusiva llamada **`barberia_auth_test`** y conceder acceso al usuario de pruebas. No utilizar `barberia_db`. Definir `TEST_DB_URL`, `TEST_DB_USERNAME` y `TEST_DB_PASSWORD` para ese entorno. Ejemplo cuando se reutiliza el mismo servidor y usuario local ya configurado:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/barberia_auth_test'
$env:TEST_DB_USERNAME = $env:DB_USERNAME
$env:TEST_DB_PASSWORD = $env:DB_PASSWORD
.\mvnw.cmd -Ppostgres-tests clean verify
```

El perfil ejecuta **15 pruebas unitarias y 56 de integración** (16 de autenticación, 18 de catálogo y 22 de citas). Las pruebas rechazan cualquier URL que no termine exactamente en `/barberia_auth_test`, sin parámetros, y comprueban `current_database()`. No toman la conexión de negocio como alternativa. Flyway migra únicamente esa base; no se usa H2 ni se ejecutan limpieza, truncado o borrado de esquemas. Los datos aleatorios de prueba se acumulan en ella. La clave JWT de pruebas se genera en memoria y no depende del secreto de la aplicación.

La integración comprueba registro, normalización y restricciones PostgreSQL, duplicados concurrentes, rechazo de asignación de privilegios, login válido/inválido, JWT válido/ausente/expirado/manipulado, cuentas inactivas, varios roles y cambios de permisos actuales, errores JSON e inicialización administrativa sin elevar cuentas existentes.

## Verificación de citas — 17 de octubre de 2026

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
