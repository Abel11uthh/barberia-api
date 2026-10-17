# Alcance y requisitos

## Fuente de requisitos

La fuente de requisitos de negocio es la especificación proporcionada por el usuario en el adjunto `Texto pegado.txt`, incorporada en este documento. Se complementa con las instrucciones de preparación de la base, su actualización a Java 25 LTS, cuentas/autenticación JWT y los adjuntos posteriores de sucursales/catálogo/perfiles y disponibilidad/reserva de citas para clientes autenticados.

Esta entrega añade disponibilidad diaria y creación/consulta de citas propias de clientes autenticados. Los demás requisitos describen el alcance previsto. Las decisiones pendientes se conservan abiertas, salvo el proveedor de pagos confirmado: Mercado Pago, cuya integración sigue pendiente.

## Objetivo

API REST para una barbería con dos sucursales. Busca reducir esperas mediante citas y registrar servicios, ventas de productos e ingresos. No incluye frontend.

## Base técnica

Java 25 LTS sin características preview, Spring Boot 3.5.16, PostgreSQL y Maven Wrapper. Monolito organizado por módulos de negocio, con capas controller, service y repository, además de DTOs y entidades. Cuentas y JWT incorporan Spring Security, Nimbus JOSE + JWT, BCrypt y Flyway 11.20.3 con soporte PostgreSQL. Hibernate valida el esquema con `ddl-auto=validate` después de las migraciones.

El flujo es controller → service → repository: contrato HTTP y validación de entrada, casos de uso y reglas de negocio, y acceso a datos, respectivamente. Existen los módulos `accounts`, `auth`, `branches`, `services`, `barbers` y `appointments`, además de errores HTTP y paginación compartidos. No se anticipan módulos ni entidades de otros casos de uso.

## Usuarios y permisos

- Cliente: gestiona sus propias citas y pedidos.
- Barbero: gestiona su propia agenda y registra cobros y ventas presenciales en su sucursal.
- Administrador: gestiona ambas sucursales, usuarios, agendas, bloqueos, catálogos, inventario y reportes.
- El dueño es administrador y también tiene perfil de barbero.
- Una cuenta admite varios roles: `CLIENT`, `BARBER` y `ADMIN`; el dueño puede tener `ADMIN` y `BARBER`. ADMIN puede crear una cuenta con perfil o asignar un perfil a una cuenta existente, conservando sus roles y contraseña. No se permite asignar roles desde el registro público ni mediante un endpoint genérico.
- Un barbero pertenece a una sola sucursal.
- Cada cuenta admite como máximo un perfil de barbero. La sucursal asignada debe estar activa y es fija en esta versión, sin traslados. Desactivar el perfil no desactiva la cuenta ni elimina sus roles o el acceso ADMIN del dueño.
- Hay inicialmente tres barberos por sucursal, incluido el dueño. Esta cantidad no debe ser un límite fijo en el código.

## Agenda

- Horario inicial: atención de lunes a sábado, de 08:00 a 18:00; domingo cerrado. ADMIN puede configurar el horario semanal de cada sucursal.
- Zona horaria: America/Mexico_City.
- Descanso inicial de Sucursal principal: 10:00 a 11:00.
- Descanso inicial de Sucursal 2: 11:00 a 12:00.
- Cada día tiene una apertura/cierre y un descanso opcional, contenido en el horario. Apertura anterior a cierre e inicio de descanso anterior a fin; un día cerrado no tiene horas. No se implementa un motor genérico de calendarios.
- Las citas comienzan a horas exactas y cada una ocupa una hora completa.
- Incluye un corte principal y complementos opcionales.
- Los precios son iguales en ambas sucursales.
- Terminar antes no libera automáticamente el bloque.
- No puede haber empalmes con citas, descansos o bloqueos, incluso ante solicitudes simultáneas.
- El administrador puede bloquear horarios de un barbero.
- El cliente debe iniciar sesión para reservar en línea.
- Solo un administrador o barbero puede registrar una cita inmediata presencial para un invitado, siempre que exista disponibilidad.
- El invitado aporta nombre y teléfono; no se crea una cuenta automáticamente. Las citas de invitados no se implementan en esta entrega.
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
- Proveedor elegido: Mercado Pago. La integración, sus dependencias y los cobros no se implementan en esta entrega.

## Reportes

- Servicios, productos y total combinado.
- Filtros por día, semana, mes y sucursal.
- Distinguir cobros, reembolsos e ingresos netos.
- No denominarlos ganancias: no registraremos gastos.

## Cuentas y autenticación JWT implementadas

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

El [README](../README.md) detalla variables, ejemplos, endpoints, inicio del primer administrador y deshabilitación posterior del mecanismo. El bootstrap solo asigna `ADMIN`. La asignación administrativa de un perfil añade exclusivamente `BARBER`; no se incorpora gestión genérica de roles.

## Sucursales, servicios y perfiles de barberos implementados

- Sucursales: nombre, dirección opcional y estado activo; horario semanal configurable y zona `America/Mexico_City`. Crear, actualizar y desactivar requiere ADMIN; los registros se conservan.
- Servicios: nombre, descripción opcional, precio positivo con dos decimales y estado activo. Tipos `HAIRCUT` y `ADD_ON`; precio compartido por todas las sucursales en MXN, `BigDecimal` y `NUMERIC(12,2)`. No se añade duración por servicio: las citas ocupan siempre una hora completa.
- Perfiles: cuenta existente única y sucursal activa. ADMIN puede crear cuenta y perfil en una transacción reutilizando normalización de correo y BCrypt, o asignar perfil a una cuenta existente sin cambiar su contraseña ni eliminar roles. Añadir `BARBER` y crear perfil es atómico. No hay traslados, cuentas inventadas ni máximo fijo de barberos.
- Consultas autenticadas: solo sucursales/servicios activos y barberos con cuenta, perfil y sucursal activos. Barberos filtrables por sucursal; respuesta normal limitada a identificador de perfil, nombre y sucursal, sin datos de contacto ni credenciales. Rutas administrativas protegidas permiten consultar inactivos.
- Listados paginados con ordenación limitada a campos permitidos. Errores JSON coherentes 400, 401, 403, 404 y 409. Se conservan los controles JWT y la separación controller → service → repository.
- Nueva migración V2, sin editar V1: tablas `branches`, `branch_hours`, `catalog_services` y `barber_profiles`, con claves foráneas, unicidad de cuenta del perfil y restricciones de horario/precio.
- Datos iniciales acordados y modificables: Sucursal principal y Sucursal 2 con sus horarios/descansos, sin direcciones; Corte básico 100.00, Corte escolar 120.00 y Corte personalizado 150.00 como `HAIRCUT`; Arreglo de barba 50.00 y Arreglo de ceja 30.00 como `ADD_ON`, todos MXN. No se crean cuentas ni perfiles iniciales. No hay límite de dos sucursales o tres barberos.

El detalle de entidades, relaciones, transacciones, endpoints, permisos y ejemplos se encuentra en [catalogo.md](catalogo.md).

## Etapa actual: disponibilidad y reservas propias

La entrega actual incorpora el módulo `appointments`:

- Disponibilidad autenticada por sucursal, perfil y fecha local; verifica pertenencia y cuenta/perfil/sucursal activos. Lee horarios semanales persistidos; excluye descansos, ocupación y comienzos pasados. Intervalos [inicio, fin), bloques completos de una hora y comienzos exactos, Clock inyectable.
- Reserva exclusiva CLIENT; identidad del JWT, sucursal inferida del perfil. Un HAIRCUT activo obligatorio y complementos ADD_ON activos sin duplicados. DTO sin cliente, sucursal, estado ni importes elegibles.
- Estado inicial CONFIRMED. Instantes TIMESTAMPTZ convertidos desde America/Mexico_City, nombres/precios históricos y total BigDecimal. No representa cobro ni ingreso.
- Revalidación transaccional e índice único parcial PostgreSQL para impedir dos reservas confirmadas del mismo barbero/bloque. V3 conserva V1 y V2. La futura cancelación liberará el índice al cambiar el estado, conservando el historial mediante una nueva migración.
- Listado paginado y detalle exclusivamente propios; cita ajena devuelve el mismo 404 que inexistente. Disponibilidad sin datos de ocupantes. Sin consultas administrativas ni agenda del barbero.

Rutas, DTOs, permisos, ejemplos y garantías: [citas.md](citas.md).

## Etapas

1. Base técnica y documentación.
2. Seguridad, usuarios, sucursales, servicios, agendas y citas.
3. Inventario, ventas y cobros presenciales.
4. Reportes.
5. Pedidos, pagos en línea, recogidas y reembolsos.

Estas son las etapas indicadas en la especificación. La primera está completada y la segunda ya incorpora cuentas, autenticación, sucursales, servicios, perfiles y reserva/consulta de citas propias. Agendas de administración/barbero, cancelaciones, reprogramación e incidencias siguen pendientes; documentar los demás requisitos no implica haberlos implementado.

## Fuera de esta tarea

- Cancelaciones, reprogramación, inasistencias, invitados, agendas administrativas o de barbero y bloqueos individuales de horarios.
- Gestión genérica de usuarios/roles, traslados de barberos, bloqueos comerciales, recuperación de contraseña y verificación de correo.
- Inventario, ventas y cobros.
- Refresh tokens y revocación individual de access tokens.
- Pagos e integraciones de pagos.
- Docker, infraestructura y despliegue.
- Carpetas vacías para posibles módulos futuros.
- Merge de esta entrega de citas. El commit, push y apertura del PR fueron autorizados en la revisión final posterior a la implementación.

## Decisiones pendientes

Los siguientes puntos permanecen abiertos; no se adoptan valores predeterminados ni soluciones implícitas:

- Control de duplicados de invitados (ya se confirmó identificación mediante nombre y teléfono, sin cuenta automática).
- Canal de las advertencias.
- Vencimientos de recogida fuera del horario de atención.
- Procedimiento de reembolsos presenciales.
- Efecto de la reactivación administrativa sobre los contadores.

## Verificación de disponibilidad y citas — 17 de octubre de 2026

- Se partió de main limpia y sincronizada, commit `64ccd65`, y se creó `feature/appointment-booking`. La implementación se entregó inicialmente sin commit, push, merge ni PR; posteriormente se autorizó su publicación para revisión, sin merge.
- Compilación inicial `mvnw.cmd -B -ntp -Dmaven.repo.local=.local/maven-repository -DskipTests compile`: BUILD SUCCESS. Esta comprobación no se presenta como pruebas ejecutadas.
- La primera ejecución de `-Ppostgres-tests clean verify` detectó un import ambiguo en el test nuevo, corregido. La siguiente pasó 15 unitarias y las 34 integraciones previas, pero el contexto HTTP nuevo falló por el socket interno del JDK con una ruta temporal larga de Windows. V3 se aplicó en la base de pruebas.
- Ejecución final `mvnw.cmd -B -ntp -Dmaven.repo.local=.local/maven-repository -Ppostgres-tests verify`: **BUILD SUCCESS**, Java 25.0.1, **15 unitarias y 56 integraciones** sin fallos, errores u omisiones. TEMP/TMP se ajustaron solo para ese proceso a `.local/tmp`, sin cambios globales.
- Las 22 integraciones nuevas cubren horarios persistidos, cierres, fechas/horas inválidas, pertenencia, estados activos, tipos de servicios y duplicados, precios históricos, permisos, identidad/privacidad, paginación, restricciones SQL, bloques consecutivos, barberos distintos y concurrencia HTTP.
- Arranque automatizado real en puerto 55151 y PostgreSQL 18.1 en `barberia_auth_test`. Dos peticiones HTTP con transacciones independientes, sincronizadas sin sleeps antes de guardar, produjeron una reserva 201 y un conflicto 409. El proceso terminó al finalizar Maven. No se hizo una prueba manual adicional.
- V1 y V2 sin modificaciones. Flyway y Hibernate verificaron V3. No se ejecutó limpieza, truncado ni migraciones en barberia_db. AGENTS solo actualiza el alcance implementado y conserva las demás instrucciones.
- Consulta final de solo lectura: barberia_db conserva cero tablas de usuario. `git diff --check` correcto; revisión de 87 archivos candidatos sin coincidencias de secretos del entorno/tokens reales ni archivos locales o generados.

## Revisión final de citas para PR

- Revisado el diff completo respecto de main: identidad JWT, consultas propias, reglas temporales compartidas, revalidación transaccional, unicidad PostgreSQL y traducción a 409, precios históricos y futura liberación del bloque conservando historial.
- V1/V2 intactas; las pruebas usan exclusivamente barberia_auth_test. El ajuste del JDK es opcional y limitado a TEMP/TMP de la sesión, con ruta relativa al proyecto, sin cambios de configuración ni rutas personales versionadas.
- Sin correcciones de código ni configuración. Se reutiliza la verificación anterior: 15 pruebas unitarias y 56 de integración aprobadas, incluida concurrencia HTTP 201/409. Revisión de archivos sin secretos o temporales detectados; git diff --check correcto. AGENTS conserva sus restricciones y solo actualiza el alcance implementado.

## Historial: verificación del catálogo — 17 de octubre de 2026

- Se confirmó `main` limpia y sincronizada con `origin/main` y se creó `feature/business-catalog`. La implementación se entregó inicialmente sin commit, push, merge ni PR; después se autorizó su publicación para revisión, sin merge.
- Compilación y empaquetado con Java 25 y Maven Wrapper correctos. Verificación completa: `mvnw.cmd -B -ntp -Dmaven.repo.local=.local/maven-repository -Ppostgres-tests clean verify`, **BUILD SUCCESS**.
- **8 pruebas unitarias y 34 de integración** (16 de autenticación y 18 de catálogo) correctas, sin fallos ni omisiones. Incluyen permisos, semillas, horarios, precios, paginación, rollback real, asignaciones concurrentes, privacidad, perfil del dueño y disponibilidad según los tres estados activos.
- V2 aplicada y validada únicamente en PostgreSQL 18.1, base dedicada `barberia_auth_test`; V1 sin modificaciones. Sin H2 ni limpieza de bases. Se centralizó la configuración protegida de conexión de pruebas para reutilizarla en ambos conjuntos.
- Una consulta posterior de solo lectura confirmó cero tablas de usuario en `barberia_db`. La revisión de archivos no detectó secretos ni archivos generados; `git diff --check` pasó.
- El primer intento de integración falló antes de conectar por falta de usuario de conexión. Tras solicitar solo ese dato, se reutilizó la contraseña del entorno sin imprimirla y se ejecutó la suite completa correctamente.
- No se hizo una prueba manual nueva con servidor HTTP; las peticiones se verificaron mediante MockMvc con PostgreSQL real. Los arranques manuales del historial corresponden a autenticación.

## Historial: verificación de cuentas y JWT — 17 de octubre de 2026

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
