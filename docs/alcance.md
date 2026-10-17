# Alcance y requisitos

## Fuente de requisitos

La fuente de requisitos de negocio es la especificación proporcionada por el usuario en el adjunto `Texto pegado.txt`, incorporada en este documento. Se complementa con las instrucciones de preparación de la base y su actualización a Java 25 LTS.

Esta entrega solo actualiza documentación. Los requisitos siguientes describen el alcance previsto, no funcionalidades ya implementadas. Las decisiones pendientes se conservan abiertas, sin implementar supuestos.

## Objetivo

API REST para una barbería con dos sucursales. Busca reducir esperas mediante citas y registrar servicios, ventas de productos e ingresos. No incluye frontend.

## Base técnica

Java 25 LTS sin características preview, Spring Boot 3.5.16, PostgreSQL y Maven Wrapper. Monolito organizado por módulos de negocio, con capas controller, service y repository, además de DTOs y entidades. Seguridad JWT y pruebas se incorporarán en sus etapas.

El flujo será controller → service → repository: contrato HTTP y validación de entrada, casos de uso y reglas de negocio, y acceso a datos, respectivamente. Los DTOs, entidades y módulos se añadirán cuando se implemente la funcionalidad correspondiente; esta tarea no los crea.

## Usuarios y permisos

- Cliente: gestiona sus propias citas y pedidos.
- Barbero: gestiona su propia agenda y registra cobros y ventas presenciales en su sucursal.
- Administrador: gestiona ambas sucursales, usuarios, agendas, bloqueos, catálogos, inventario y reportes.
- El dueño es administrador y también tiene perfil de barbero.
- Un barbero pertenece a una sola sucursal.
- Hay inicialmente tres barberos por sucursal, incluido el dueño. Esta cantidad no debe ser un límite fijo en el código.

## Agenda

- Atención de lunes a sábado, de 08:00 a 18:00.
- Zona horaria: America/Mexico_City.
- Descanso sucursal 1: 10:00 a 11:00.
- Descanso sucursal 2: 11:00 a 12:00.
- Cada cita ocupa una hora completa.
- Incluye un corte principal y complementos opcionales.
- Los precios son iguales en ambas sucursales.
- Terminar antes no libera automáticamente el bloque.
- No puede haber empalmes con citas, descansos o bloqueos, incluso ante solicitudes simultáneas.
- El administrador puede bloquear horarios de un barbero.
- El personal puede registrar citas para clientes sin cuenta, siempre que exista disponibilidad.
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

## Etapa actual: base del proyecto

| Requisito confirmado | Decisión |
| --- | --- |
| Inspeccionar y conservar el trabajo existente | La carpeta estaba vacía y no era un repositorio Git; no había trabajo previo que modificar. |
| Java 25 LTS y Maven Wrapper | Java 25 sin preview, Maven 3.9.11 y Wrapper 3.3.4. |
| Conservar Spring Boot compatible con Java 25 | Spring Boot 3.5.16. |
| Dependencias iniciales | Web, Data JPA, Bean Validation, PostgreSQL y starter de pruebas. |
| Nombre provisional | `barberia-api`, paquete raíz provisional `com.barberia.api`. |
| Monolito por módulos de negocio | Un artefacto Maven; crear módulos conforme se implementen requisitos confirmados. |
| Separación de responsabilidades | controller → service → repository, documentada en README y AGENTS. |
| Configuración externa de PostgreSQL | `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`, sin credenciales en archivos. |
| Sin actualización automática del esquema | `ddl-auto=none` e inicialización SQL desactivada. |
| Documentación y archivos locales | README, este alcance, AGENTS y `.gitignore`. |
| Verificar con Maven Wrapper | Compilar y empaquetar; informar por separado el estado del arranque con PostgreSQL. |
| Sin operaciones de publicación Git | No hacer commit, push ni merge. |

## Etapas

1. Base técnica y documentación.
2. Seguridad, usuarios, sucursales, servicios, agendas y citas.
3. Inventario, ventas y cobros presenciales.
4. Reportes.
5. Pedidos, pagos en línea, recogidas y reembolsos.

Estas son las etapas indicadas en la especificación. El estado actual corresponde a la primera; documentar las siguientes no implica haberlas implementado. Las migraciones se incorporarán al crear el primer esquema, y las pruebas se añadirán en sus etapas correspondientes.

## Fuera de esta tarea

- Entidades, CRUD, endpoints o funcionalidades de negocio.
- Autenticación, autorización y Spring Security.
- Pagos e integraciones de pagos.
- Docker, infraestructura y despliegue.
- Carpetas vacías para posibles módulos futuros.
- Creación del primer esquema o adopción anticipada de una herramienta de migraciones.
- Commit, push y merge.

## Decisiones pendientes

Los siguientes puntos permanecen abiertos; no se adoptan valores predeterminados ni soluciones implícitas:

- Horas exactas o inicios de citas en horarios intermedios.
- Identificación de clientes sin cuenta y control de duplicados.
- Canal de las advertencias.
- Vencimientos de recogida fuera del horario de atención.
- Proveedor de pagos.
- Procedimiento de reembolsos presenciales.
- Efecto de la reactivación administrativa sobre los contadores.

## Pendientes técnicos de la base

- Mantener disponible la conexión PostgreSQL para futuras verificaciones; el arranque local ya se comprobó con PostgreSQL 18.1 y `barberia_db`.
- Incorporar las pruebas de negocio e integración al implementar el primer caso de uso. La base solo incluye las dependencias de pruebas.
- El repositorio Git local se inicializó en la rama `main` por solicitud del usuario. No se ha creado un remoto.

## Consistencia de la documentación

La nota de especificación no disponible y la secuencia provisional de etapas quedaron sustituidas por la especificación recibida y sus cinco etapas. No se identificaron contradicciones directas entre las reglas proporcionadas. Las siete decisiones pendientes se conservan sin resolver.

La exclusión de seguridad, funcionalidades y pagos corresponde a esta tarea documental; su inclusión en etapas posteriores forma parte del alcance del producto. Los registros de compilación siguientes pertenecen a entregas anteriores y no acreditan nuevas verificaciones en esta actualización documental.

## Verificación posterior: PostgreSQL y arranque real

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
