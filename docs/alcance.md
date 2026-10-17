# Alcance y requisitos

## Fuente de requisitos

La fuente de requisitos de negocio es la especificación proporcionada por el usuario en el adjunto `Texto pegado.txt`, incorporada en este documento. Se complementa con las instrucciones de preparación de la base, su actualización a Java 25 LTS y la tarea confirmada de cuentas/autenticación JWT.

Esta entrega implementa exclusivamente cuentas y autenticación. Los demás requisitos describen el alcance previsto. Las decisiones pendientes se conservan abiertas, sin implementar supuestos.

## Objetivo

API REST para una barbería con dos sucursales. Busca reducir esperas mediante citas y registrar servicios, ventas de productos e ingresos. No incluye frontend.

## Base técnica

Java 25 LTS sin características preview, Spring Boot 3.5.16, PostgreSQL y Maven Wrapper. Monolito organizado por módulos de negocio, con capas controller, service y repository, además de DTOs y entidades. Cuentas y JWT incorporan Spring Security, Nimbus JOSE + JWT, BCrypt y Flyway 11.20.3 con soporte PostgreSQL. Hibernate valida el esquema con `ddl-auto=validate` después de las migraciones.

El flujo es controller → service → repository: contrato HTTP y validación de entrada, casos de uso y reglas de negocio, y acceso a datos, respectivamente. Se han creado los módulos `accounts` y `auth`, y errores HTTP compartidos. No se anticipan módulos ni entidades de otros casos de uso.

## Usuarios y permisos

- Cliente: gestiona sus propias citas y pedidos.
- Barbero: gestiona su propia agenda y registra cobros y ventas presenciales en su sucursal.
- Administrador: gestiona ambas sucursales, usuarios, agendas, bloqueos, catálogos, inventario y reportes.
- El dueño es administrador y también tiene perfil de barbero.
- Una cuenta admite varios roles: `CLIENT`, `BARBER` y `ADMIN`; el dueño podrá tener `ADMIN` y `BARBER`. El perfil de barbero se implementará posteriormente.
- Un barbero pertenece a una sola sucursal.
- Hay inicialmente tres barberos por sucursal, incluido el dueño. Esta cantidad no debe ser un límite fijo en el código.

## Agenda

- Atención de lunes a sábado, de 08:00 a 18:00.
- Zona horaria: America/Mexico_City.
- Descanso sucursal 1: 10:00 a 11:00.
- Descanso sucursal 2: 11:00 a 12:00.
- Las citas comienzan a horas exactas y cada una ocupa una hora completa.
- Incluye un corte principal y complementos opcionales.
- Los precios son iguales en ambas sucursales.
- Terminar antes no libera automáticamente el bloque.
- No puede haber empalmes con citas, descansos o bloqueos, incluso ante solicitudes simultáneas.
- El administrador puede bloquear horarios de un barbero.
- El cliente debe iniciar sesión para reservar en línea.
- Solo un administrador o barbero puede registrar una cita inmediata presencial para un invitado, siempre que exista disponibilidad.
- El invitado aporta nombre y teléfono; no se crea una cuenta automáticamente. Citas e invitados no se implementan en esta entrega.
- Las citas se cancelan; no se borran del historial.

## Cancelaciones e incidencias

- Cliente: cancelación con al menos 60 minutos de anticipación, incluyendo exactamente 60 minutos.
- Barbero y administrador: cancelación en cualquier momento, con motivo obligatorio y sin penalizar al cliente.
- Barbero y administrador pueden marcar inasistencias.
- Cancelaciones del cliente e inasistencias cuentan como incidencias consecutivas de citas.
- Segunda incidencia: advertencia. Tercera: bloqueo.
- Una asistencia completada reinicia el contador de citas.
- Los pedidos tienen un contador independiente: cancelación del cliente o vencimiento sin recogida suma; recogida completada reinicia.
- Llegar a tres en cualquiera bloquea nuevas citas y compras.
- El bloqueo no cancela compromisos existentes ni impide recoger pedidos pagados o recibir reembolsos.
- Un bloqueo comercial para reservar o comprar no impide iniciar sesión ni consultar compromisos existentes. Es independiente del estado activo de la cuenta; los bloqueos comerciales todavía no están implementados.
- El administrador puede reactivar al cliente.
- Cancelaciones causadas por el negocio no suman incidencias.
- Las bajas de usuarios conservan su historial.

## Productos y ventas

- Solo productos para venta; no materiales de trabajo.
- Existencias por sucursal y consulta del total general.
- El cliente puede buscar un producto y consultar dónde hay stock.
- Solo puede comprar en una sucursal con existencias disponibles.
- Barbero y administrador registran ventas presenciales y cobros de servicios en efectivo o tarjeta.
- Conservar los precios aplicados en cada operación histórica.

## Pagos y recogida en línea

- Pago en línea opcional para citas.
- Pago en línea obligatorio para compras de productos en línea.
- Recogida mediante código en la sucursal seleccionada.
- Sin entrega a domicilio.
- Plazo: 72 horas desde la confirmación del pago.
- La compra compromete existencias para evitar sobreventa.
- Cancelar o vencer el pedido libera las unidades comprometidas.
- Citas o pedidos pagados que no se completen deben reembolsarse.
- El reembolso tiene seguimiento propio: pendiente, completado o fallido.
- Cancelar no equivale a confirmar que ya se devolvió el dinero.
- La integración se demostrará inicialmente con pagos de prueba.

## Reportes

- Servicios, productos y total combinado.
- Filtros por día, semana, mes y sucursal.
- Distinguir cobros, reembolsos e ingresos netos.
- No denominarlos ganancias: no registraremos gastos.

## Etapa actual: cuentas y autenticación JWT

| Requisito confirmado | Implementación |
| --- | --- |
| Registro público | `POST /api/v1/auth/register`, nombre, teléfono, correo y contraseña; 201 con datos públicos y únicamente `CLIENT`. No admite campos de roles o privilegios. |
| Correo | Normalizado a minúsculas, sin espacios exteriores, único mediante restricción PostgreSQL; duplicados devuelven 409 incluso en concurrencia. |
| Login | `POST /api/v1/auth/login`; BCrypt, cuenta activa y correo/contraseña válidos. Error genérico 401 ante credenciales incorrectas o cuenta inactiva. |
| Cuenta autenticada | `GET /api/v1/users/me`; DTO de datos públicos y roles actuales, sin contraseña ni hash. |
| JWT | Bearer stateless; Nimbus con Spring Security, HS256, validación de firma, expiración, emisor, audiencia e identidad UUID. |
| Variables JWT | `JWT_SECRET_BASE64` (mínimo 32 bytes aleatorios codificados en Base64) y `JWT_EXPIRATION_SECONDS` (60–3600); sin secreto predeterminado. |
| Permisos y desactivación | Identidad del JWT validado; estado y roles consultados en PostgreSQL en cada petición autenticada. Los roles no se toman del cliente ni del token. |
| Errores | Formato JSON común para 400, 401, 403 y 409, sin datos internos ni credenciales. |
| Persistencia | Migración V1 para `accounts`, `roles` y `account_roles`. Flyway con limpieza deshabilitada y sin baseline automático; Hibernate `validate`. |
| Primer administrador | Bootstrap explícito desactivado por defecto; datos mediante `BOOTSTRAP_ADMIN_*`. Crea `ADMIN` si no existe; nunca cambia la contraseña, el estado ni los privilegios de una cuenta existente. |
| Vida de tokens | No hay refresh ni revocación individual; vigencia limitada. Desactivar una cuenta rechaza sus tokens; rotar la clave invalida todos. |
| Separación comercial | Cuenta activa es independiente de futuros bloqueos de reservas/compras. No se implementan esos bloqueos. |

El [README](../README.md) detalla variables, ejemplos, endpoints, inicio del primer administrador y deshabilitación posterior del mecanismo. El bootstrap solo asigna `ADMIN`; la gestión posterior de roles y los perfiles de barbero están pendientes.

## Etapas

1. Base técnica y documentación.
2. Seguridad, usuarios, sucursales, servicios, agendas y citas.
3. Inventario, ventas y cobros presenciales.
4. Reportes.
5. Pedidos, pagos en línea, recogidas y reembolsos.

Estas son las etapas indicadas en la especificación. La primera está completada y la segunda comienza con cuentas y autenticación. Sucursales, servicios, agendas y citas siguen pendientes. Ya existen migraciones y pruebas para este primer caso de uso; documentar los demás requisitos no implica haberlos implementado.

## Fuera de esta tarea

- Citas, invitados, agendas, sucursales, servicios y perfiles de barbero.
- Gestión administrativa de usuarios/roles, bloqueos comerciales, recuperación de contraseña y verificación de correo.
- Refresh tokens y revocación individual de access tokens.
- Pagos e integraciones de pagos.
- Docker, infraestructura y despliegue.
- Carpetas vacías para posibles módulos futuros.
- Merge del PR. El commit, push y apertura del primer PR se autorizaron en la revisión final posterior a la implementación.

## Decisiones pendientes

Los siguientes puntos permanecen abiertos; no se adoptan valores predeterminados ni soluciones implícitas:

- Control de duplicados de invitados (ya se confirmó identificación mediante nombre y teléfono, sin cuenta automática).
- Canal de las advertencias.
- Vencimientos de recogida fuera del horario de atención.
- Proveedor de pagos.
- Procedimiento de reembolsos presenciales.
- Efecto de la reactivación administrativa sobre los contadores.

## Verificación de cuentas y JWT — 17 de octubre de 2026

- Antes de implementar se comprobó `main` limpia y sincronizada con `origin/main`; se creó `feature/auth-users`. La implementación se entregó inicialmente sin commit, push, merge ni PR; la publicación se autorizó después, para su revisión sin merge.
- `mvnw.cmd -B -ntp -Dmaven.repo.local=.local/maven-repository -Ppostgres-tests clean verify`: **BUILD SUCCESS** con Java 25.0.1, Maven 3.9.11 y Spring Boot 3.5.16.
- **8 pruebas unitarias y 16 de integración ejecutadas**, todas correctas. Cubren registro, duplicados concurrentes, normalización y restricciones PostgreSQL, rechazo de privilegios públicos, login, JWT válido/ausente/expirado/manipulado, cuentas inactivas, varios roles y bootstrap sin elevación de cuentas existentes.
- Se creó y utilizó exclusivamente `barberia_auth_test` para las migraciones y los datos de autenticación. No se sustituyó PostgreSQL por H2 ni se limpiaron esquemas. Las pruebas conservan sus datos aleatorios en esa base. No se aplicó V1 en `barberia_db`.
- Una consulta posterior de solo lectura confirmó que `barberia_db` conserva cero tablas de usuario. La revisión de archivos no encontró secretos del entorno ni patrones de claves/tokens; `git diff --check` pasó.
- La primera ejecución detectó un error en el tratamiento de duplicados simultáneos; se corrigió usando SQLSTATE 23505, independiente del idioma de PostgreSQL. La ejecución completa posterior pasó. Se actualizó Flyway de 11.7.2 a 11.20.3 por compatibilidad con PostgreSQL 18.
- **Comprobación HTTP manual**: dos arranques reales mediante Maven Wrapper con Java 25, conexión PostgreSQL y puertos efectivos 63788 y 61718. Registro 201, login y perfil 200, duplicado 409, campo de roles 400, perfil sin token 401 y ruta denegada 403.
- El bootstrap habilitado creó un administrador. Un segundo arranque con otra contraseña de inicialización conservó la contraseña original y el rol. Se detuvieron únicamente los procesos iniciados para verificar.
- Advertencias no bloqueantes de Guice/Maven (`sun.misc.Unsafe`) y carga dinámica del agente de Mockito en Java 25. No se ocultaron mediante opciones de JVM.

## Historial: PostgreSQL y arranque de la base inicial

- Se comprobó que `barberia_db` no existía y se creó por solicitud del usuario usando `DB_USERNAME` y `DB_PASSWORD` del entorno. No se imprimió la contraseña ni se modificaron otras bases.
- Se confirmó la conexión a PostgreSQL 18.1 y el arranque completo con Maven Wrapper 3.9.11, Java 25.0.1 y Spring Boot 3.5.16.
- Se observaron conexiones JDBC a `barberia_db`; el proceso de la API escuchó en el puerto 8080 y la raíz respondió HTTP 404.
- Se conservaron `ddl-auto=none` y la inicialización SQL desactivada. La base tuvo cero tablas de usuario antes y después.
- Se detuvieron exclusivamente la aplicación y el árbol de procesos iniciado para comprobarla. No se ejecutaron pruebas automatizadas.

## Historial: actualización a Java 25 LTS — 16 de octubre de 2026

- Se actualizaron `pom.xml`, `README.md`, `AGENTS.md` y este documento a Java 25 LTS, conservando Spring Boot 3.5.16 y las versiones de Maven y Wrapper. No se habilitaron características preview ni se añadieron funcionalidades.
- Se encontró Eclipse Temurin JDK 25.0.1 en `C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot`. Se seleccionó mediante `JAVA_HOME` y `PATH` únicamente para la sesión de verificación; no se modificó la configuración global ni se eliminó el JDK 21. Se confirmó que el ejecutable de JDK 21 sigue presente.
- `mvnw.cmd -version`: Maven 3.9.11 indicó `Java version: 25.0.1`, proveedor Eclipse Adoptium y la ruta del JDK 25.
- `mvnw.cmd clean verify`: **BUILD SUCCESS**, compilación con `release 25` y JAR ejecutable generado. Para reutilizar la caché del espacio de trabajo se establecieron `MAVEN_USER_HOME=.local/maven-home` y `MAVEN_ARGS=-B -ntp -Dmaven.repo.local=.local/maven-repository` en la sesión.
- **Pruebas ejecutadas: ninguna.** Maven informó `No tests to run`; el resultado valida compilación y empaquetado, no pruebas funcionales ni integración.
- **Arranque: no ejecutado ni verificado.** La nueva comprobación TCP a `127.0.0.1:5432` no obtuvo conexión y no había variables de conexión configuradas.
- La búsqueda de opciones `--enable-preview` y `enablePreview` en los archivos del proyecto no encontró coincidencias.
- Maven emitió una advertencia de API obsoleta `sun.misc.Unsafe` desde su dependencia interna Guice 5.1.0. No impidió la compilación; no se añadieron opciones para ocultarla.
- La carpeta continúa sin repositorio Git. No se ejecutaron commit, push ni merge. El README documenta la selección de JDK 25 para la terminal y el IDE; no se modificó ningún IDE.

## Historial: verificaciones de la base inicial con Java 21 — 16 de octubre de 2026

Este registro corresponde a la base anterior a la actualización a Java 25 y conserva los resultados realmente obtenidos en esa entrega.

- Inspección inicial de archivos y `git status --short --branch`: carpeta vacía, sin repositorio Git. No se inicializó Git ni se ejecutaron commit, push o merge.
- `mvnw.cmd --version`: Maven 3.9.11 con JDK 21.0.12.1. Se seleccionó explícitamente ese JDK mediante `JAVA_HOME`, porque el Java predeterminado del equipo era 25.
- `mvnw.cmd -B -ntp -Dmaven.repo.local=.local/maven-repository clean verify`: **BUILD SUCCESS**. Compilación con `release 21` y generación del JAR ejecutable `target/barberia-api-0.0.1-SNAPSHOT.jar`.
- Maven informó `No tests to run`: no se ejecutaron pruebas automatizadas ni de integración; solo se añadieron las dependencias de pruebas solicitadas.
- Se comprobó que los archivos `mvnw` y `mvnw.cmd` coinciden por hash con los scripts de la distribución oficial de Maven Wrapper 3.3.4 descargada de Maven Central. No se ejecutó el script Unix en este entorno Windows.
- PostgreSQL está instalado, pero la comprobación TCP a `127.0.0.1:5432` no obtuvo conexión y no había variables de conexión configuradas. **No se ejecutó ni verificó el arranque de la aplicación con PostgreSQL.** No se creó ni modificó ninguna base de datos.
- La descarga inicial requirió acceso de red autorizado. Se usó `MAVEN_USER_HOME` bajo `.local/maven-home` y un repositorio Maven bajo `.local/maven-repository` para mantener las herramientas y la caché dentro del espacio de trabajo. `.local/` y `target/` están excluidos por `.gitignore`.
