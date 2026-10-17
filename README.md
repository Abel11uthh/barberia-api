# barberia-api

API REST para una barbería con dos sucursales, orientada a reducir esperas mediante citas y registrar servicios, ventas de productos e ingresos. No incluye frontend. El alcance contempla clientes, barberos y administradores; agendas, cancelaciones e incidencias; inventario por sucursal; cobros presenciales; reportes; y, en una etapa posterior, pedidos con pagos en línea, recogida y reembolsos.

El proyecto permanece en la etapa de base técnica y documentación: todavía no hay endpoints, entidades ni funcionalidades de negocio. La especificación proporcionada por el usuario, las cinco etapas de desarrollo y las siete decisiones pendientes están recogidas en [docs/alcance.md](docs/alcance.md). La seguridad JWT y las pruebas se incorporarán en sus etapas; los puntos pendientes no se consideran resueltos.

## Tecnologías

| Componente | Versión o dependencia |
| --- | --- |
| Java | 25 LTS, sin características preview |
| Spring Boot | 3.5.16 |
| Maven | 3.9.11, mediante Maven Wrapper |
| Maven Wrapper | 3.3.4, modalidad `only-script` |
| HTTP | Spring Web / Spring MVC |
| Persistencia | Spring Data JPA y driver PostgreSQL |
| Validación | Bean Validation con `spring-boot-starter-validation` |
| Pruebas | `spring-boot-starter-test`: JUnit Jupiter, AssertJ, Mockito y Spring Test |

Spring Boot administra las versiones de sus dependencias. La [documentación oficial de compatibilidad](https://docs.spring.io/spring-boot/3.5/system-requirements.html) incluye Java 25. El [Maven Wrapper oficial](https://maven.apache.org/tools/wrapper/) descarga la versión fijada de Maven sin requerir una instalación global. No se incluye Spring Security en esta etapa.

## Requisitos

- JDK 25 LTS; configurar `JAVA_HOME` hacia la carpeta del JDK y añadir su directorio `bin` al `PATH`. No habilitar características preview.
- Acceso a Maven Central durante la primera construcción para descargar Maven y las dependencias.
- Para arrancar: PostgreSQL accesible, la base dedicada `barberia_db` ya creada y un usuario con acceso. La aplicación no crea la base ni sus tablas. La comprobación local se realizó con PostgreSQL 18.1.
- PowerShell en Windows o un shell compatible con POSIX en Linux/macOS.

## Compilación

Desde la raíz del proyecto, en PowerShell, seleccionar el JDK 25 para la sesión. La ruta siguiente corresponde al Temurin 25.0.1 instalado en el equipo de verificación; adaptarla si la instalación es distinta. No es necesario desinstalar el JDK 21:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\mvnw.cmd -version
.\mvnw.cmd clean verify
```

La salida de Maven debe indicar `Java version: 25...`. En el IDE, seleccionar JDK 25 como SDK del proyecto y como JDK de Maven (importación y ejecución); usar el nivel de lenguaje 25 sin preview y recargar el proyecto Maven. Estos ajustes del IDE son locales y no se incluyen en el repositorio.

En Linux/macOS, configurar primero `JAVA_HOME` con la ruta local del JDK 25:

```bash
export PATH="$JAVA_HOME/bin:$PATH"
java -version
sh ./mvnw -version
sh ./mvnw clean verify
```

La compilación no requiere PostgreSQL. Actualmente no hay pruebas automatizadas; `verify` compila y empaqueta el proyecto y ejecutará las pruebas que se añadan en tareas posteriores. El JAR ejecutable queda en `target/barberia-api-0.0.1-SNAPSHOT.jar`.

## Conexión a PostgreSQL y ejecución

Las siguientes variables son obligatorias para arrancar y no tienen credenciales predeterminadas:

| Variable | Contenido |
| --- | --- |
| `DB_URL` | URL JDBC, por ejemplo `jdbc:postgresql://localhost:5432/barberia_db` |
| `DB_USERNAME` | Usuario autorizado en esa base |
| `DB_PASSWORD` | Contraseña del usuario |

Establecerlas en la misma terminal desde la que se ejecute la aplicación. Ejemplo en PowerShell; la URL es ilustrativa y se debe adaptar:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/barberia_db'
$env:DB_USERNAME = Read-Host 'Usuario PostgreSQL'
if ([string]::IsNullOrEmpty($env:DB_PASSWORD)) {
    $dbSecret = Read-Host 'Contraseña PostgreSQL' -AsSecureString
    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
    Remove-Variable dbSecret
}
.\mvnw.cmd spring-boot:run
```

Ejemplo en Bash:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/barberia_db'
read -r -p 'Usuario PostgreSQL: ' DB_USERNAME
read -r -s -p 'Contraseña PostgreSQL: ' DB_PASSWORD
printf '\n'
export DB_USERNAME DB_PASSWORD
sh ./mvnw spring-boot:run
```

También se pueden proporcionar estas variables en la configuración de ejecución local del IDE. Spring Boot no carga archivos `.env` automáticamente. No guardar credenciales en archivos versionados.

Si las tres variables ya están definidas en el proceso que ejecuta Maven, ejecutar directamente `spring-boot:run` para reutilizarlas. No imprimir sus valores ni incluir la contraseña en los argumentos del comando.

Después de compilar, con las mismas variables disponibles, se puede ejecutar:

```text
java -jar target/barberia-api-0.0.1-SNAPSHOT.jar
```

El puerto HTTP predeterminado es `8080`; se puede cambiar con `SERVER_PORT`. El mensaje `Started BarberiaApiApplication` confirma que el proceso ha arrancado. No hay endpoints propios: una respuesta 404 en `/` es esperable. Detener con `Ctrl+C`.

La configuración utiliza `spring.jpa.hibernate.ddl-auto=none`, desactiva la inicialización SQL y desactiva Open Session in View. Las migraciones se incorporarán junto con el primer esquema. Sin PostgreSQL accesible y las variables requeridas no se espera un arranque correcto.

## Verificaciones realizadas

Estas verificaciones se realizaron durante la preparación de la base y su comprobación local, antes del primer commit:

| Comprobación | Resultado |
| --- | --- |
| Maven Wrapper y Java | Maven 3.9.11 utilizando Eclipse Temurin 25.0.1. |
| Compilación y empaquetado | `mvnw.cmd clean verify`: **BUILD SUCCESS**, con `release 25` y JAR ejecutable generado. |
| Pruebas automatizadas | **Ninguna ejecutada**: Maven informó `No tests to run`. Las dependencias están preparadas, pero aún no hay pruebas. |
| PostgreSQL | Conexión real confirmada a `barberia_db` en PostgreSQL 18.1, usando las variables de entorno. |
| Arranque | `mvnw.cmd spring-boot:run`: arranque completo de Spring Boot 3.5.16 y conexiones JDBC establecidas. |
| HTTP | Puerto efectivo **8080**, perteneciente al proceso iniciado; `GET /` respondió **404**, esperado al no haber controllers. |
| Esquema | `ddl-auto=none` conservado; cero tablas de usuario antes y después de la comprobación. |
| Finalización | Se detuvieron únicamente la aplicación y el árbol de procesos iniciado para verificar. |

El arranque local comprobado no sustituye las futuras pruebas de negocio e integración. La aplicación no se deja en ejecución tras la verificación.

## Organización

Se utiliza un monolito y un único artefacto Maven. El paquete raíz es `com.barberia.api`; solo contiene la clase de arranque en esta etapa.

Los módulos se crearán según la especificación, agrupando el código por negocio: `com.barberia.api.<modulo>`. Dentro de cada módulo se seguirá controller → service → repository:

- `controller`: contrato HTTP, validación de entrada mediante DTO y conversión de respuestas.
- `service`: casos de uso, reglas de negocio y límites transaccionales.
- `repository`: consultas y persistencia mediante Spring Data JPA.

Los controllers delegarán en servicios; los servicios usarán repositorios. No se crearán carpetas globales de capas, módulos vacíos ni entidades anticipadas. Los DTO, entidades y pruebas se añadirán cuando exista una funcionalidad confirmada que los necesite.

El alcance, las etapas y los pendientes se mantienen en [docs/alcance.md](docs/alcance.md). Las instrucciones para cambios posteriores están en [AGENTS.md](AGENTS.md).
