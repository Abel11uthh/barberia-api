# Citas y operación de la agenda

El módulo `appointments` cubre reservas de clientes, atención presencial inmediata, agenda del personal, estados, cancelaciones y bloqueos individuales. El módulo `accounts` lleva las incidencias consecutivas de citas y el bloqueo comercial. Conserva Java 25, Spring Boot 3.5.16, JWT, DTOs y controller → service → repository. Una cita o atención completada no representa un cobro.

## Rutas y permisos

Todas requieren JWT válido y cuenta activa. Los roles se consultan actualmente en PostgreSQL; la identidad nunca procede del request. Un usuario con ADMIN y BARBER puede operar como administrador. CLIENT solo accede a sus citas; conocer un identificador no concede permisos.

| Método y ruta | Permiso y resultado |
| --- | --- |
| GET /api/v1/appointments/availability?branchId={id}&barberId={id}&date=2030-01-07 | Autenticado; 200 con bloques libres, sin datos de ocupantes |
| POST /api/v1/appointments | CLIENT; 201 y Location |
| GET /api/v1/appointments?page=0&size=20&sort=startsAt,desc | CLIENT; página exclusivamente propia |
| GET /api/v1/appointments/{id} | CLIENT propietario; 200 |
| POST /api/v1/appointments/{id}/cancel | CLIENT propietario; cancelación de CONFIRMED con al menos 60 minutos |
| GET /api/v1/users/me/booking-status | CLIENT; estado comercial propio, contador y advertencia |
| POST /api/v1/admin/clients/{id}/reactivate | ADMIN; motivo obligatorio, reinicia contador y conserva auditoría |
| POST /api/v1/staff/appointments/walk-ins | BARBER para sí mismo; ADMIN para cualquier barbero disponible; 201 y Location |
| GET /api/v1/staff/appointments?date=2030-01-07&status=CONFIRMED | BARBER: agenda propia; ADMIN: todas las sucursales |
| GET /api/v1/staff/appointments/{id} | BARBER propietario de la atención o ADMIN |
| POST /api/v1/staff/appointments/{id}/start | BARBER de la cita o ADMIN |
| POST /api/v1/staff/appointments/{id}/complete | BARBER de la cita o ADMIN |
| POST /api/v1/staff/appointments/{id}/no-show | BARBER de la cita o ADMIN |
| POST /api/v1/staff/appointments/{id}/cancel | BARBER de la cita o ADMIN; motivo obligatorio |
| POST /api/v1/admin/blocks | ADMIN; 201 con bloqueo |
| POST /api/v1/admin/blocks/{id}/cancel | ADMIN; conserva el bloqueo cancelado |
| GET /api/v1/staff/blocks?date=2030-01-07 | BARBER: bloqueos propios; ADMIN: todos |

Las agendas requieren `date` local y admiten `barberId` opcional. La agenda de citas también admite `branchId` y `status` (uno de los cinco estados documentados). BARBER no puede seleccionar otro perfil; el filtro de sucursal nunca amplía su alcance. Incluyen registros cuyo intervalo intersecta el día local, incluso si un bloqueo comenzó el día anterior. La respuesta del personal incluye `requiresAdministrativeReview`: `true` si la cita sigue CONFIRMED después de la ventana de NO_SHOW o IN_PROGRESS después del fin del bloque. ADMIN puede identificarlas consultando la fecha con `status=CONFIRMED` o `status=IN_PROGRESS`. Esta señal no cambia su estado ni su ocupación.

Paginación común: `page >= 0`, `size` entre 1 y 100, respuesta `{items, page, size, totalElements, totalPages}`. Agendas ordenadas por startsAt ascendente e id; listado de cliente permite únicamente startsAt/id y asc/desc, predeterminado startsAt descendente. Las consultas del personal permiten atender compromisos existentes aunque se haya desactivado un perfil; las nuevas reservas/presenciales/bloqueos requieren cuenta, perfil y sucursal del barbero activos.

## Reserva programada

```json
{
  "barberId": "30000000-0000-0000-0000-000000000001",
  "date": "2030-01-07",
  "time": "09:00",
  "primaryServiceId": "20000000-0000-0000-0000-000000000001",
  "addOnIds": ["20000000-0000-0000-0000-000000000004"]
}
```

Los UUID son ejemplos; sustituir por identificadores del catálogo. `barberId` identifica el perfil, no la cuenta. Cliente y creador proceden del JWT; sucursal del perfil; estado CONFIRMED y origen RESERVATION. El request no acepta cliente, sucursal, estado, origen ni importes. La consulta de disponibilidad no retiene un intervalo: la creación revalida dentro de su transacción.

Debe haber un HAIRCUT activo obligatorio y complementos ADD_ON activos sin duplicados ni elementos nulos. Complementos omitidos, nulos o vacíos significan ninguno. Se comparte esta validación con atención presencial.

## Atención inmediata

```json
{
  "barberId": "30000000-0000-0000-0000-000000000001",
  "guestName": "Invitado de ejemplo",
  "guestPhone": "0000000000",
  "primaryServiceId": "20000000-0000-0000-0000-000000000001",
  "addOnIds": []
}
```

Nombre obligatorio de hasta 120 caracteres y teléfono obligatorio de hasta 32. No se crea una cuenta ni se vincula por teléfono. Identificación, deduplicación y vinculación de invitados quedan pendientes.

El servidor obtiene el comienzo mediante Clock y lo trunca al minuto: 14:25:49 produce 14:25–15:25. Nunca acepta fecha/hora de inicio desde el request. Guarda creador autenticado, origen WALK_IN y estado IN_PROGRESS; startedAt registra el instante real de la operación. Se toma el reloj después de adquirir los bloqueos de catálogo, para evitar usar una hora anterior a una espera.

BARBER solo puede seleccionar su propio perfil y su sucursal; ADMIN cualquiera activo. La respuesta del personal contiene `appointment`, `customer: {name, phone, guest}`, `createdBy` y `cancelledBy`. Los clientes registrados se consultan por su cuenta explícita, nunca por coincidencia de teléfono. No se incluyen correo, contraseñas, hashes ni tokens.

## Tiempo, disponibilidad y precios

Zona fija **America/Mexico_City**, independiente del equipo. Fecha/hora local de reserva → Instant → TIMESTAMPTZ; respuestas ISO-8601 UTC. Ejemplo: 09:00 del 7 de enero de 2030 = 15:00Z. Bloqueos aceptan instantes ISO-8601 con offset o Z, no horas locales ambiguas.

Todas las citas duran una hora real. Reservas comienzan a horas exactas; presenciales a cualquier minuto desde el reloj del servidor. Los horarios semanales y descansos se leen de PostgreSQL. El intervalo completo debe caber en la apertura/cierre sin tocar el interior de descansos u ocupaciones. Se usa el mismo criterio **[inicio, fin)** para las tres clases de ocupación. Horas locales ambiguas o inexistentes no se reservan.

- Principal admite 09–10 y excluye 10–11; sucursal 2 admite 10–11 y excluye 11–12.
- Última reserva 17–18; presencial 17:01 no cabe si el cierre es 18:00.
- Domingo cerrado; disponibilidad pasada vacía.
- Una atención 14:25–15:25 impide otra a las 15:10.
- Una reserva a las 15:00 impide comenzar atención inmediata a las 14:25.
- Si hay reserva a las 16:00, una nueva atención a las 15:25 no cabe.
- 14:25–15:25 y 15:25–16:25 son adyacentes y válidas si respetan las demás reglas.
- COMPLETED y NO_SHOW siguen ocupando su intervalo original. No se registra COMPLETED antes del fin reservado.
- Solo CANCELLED libera la ocupación, sin borrar ninguna fila del historial.

Los servicios guardan copias de identificador, nombre, tipo y precio; total calculado con BigDecimal. Cambiar o desactivar el catálogo no altera una cita histórica. Se conserva el primer elemento como HAIRCUT y los siguientes como ADD_ON. No hay estados de pago.

La respuesta de cliente incluye `id, barberId, branchId, startsAt, endsAt, timeZone, status, services, total, currency, origin, startedAt, completedAt, noShowAt, cancelledAt, cancellationReason, cancellationOrigin`. No incluye contactos de otras personas ni identificadores de actores del personal. Las fechas de acciones no realizadas son nulas. Disponibilidad devuelve únicamente `branchId, barberId, date, timeZone, slots: [{startsAt, endsAt}]`.

## Estados e idempotencia

| Operación | Estado inicial | Estado final | Condición |
| --- | --- | --- | --- |
| Reservar | — | CONFIRMED | CLIENT y una hora disponible |
| Registrar presencial | — | IN_PROGRESS | Personal autorizado y una hora disponible ahora |
| Comenzar | CONFIRMED | IN_PROGRESS | Ahora >= startsAt |
| Completar | IN_PROGRESS | COMPLETED | Atención iniciada y ahora >= endsAt |
| Inasistencia | CONFIRMED | NO_SHOW | Desde endsAt hasta endsAt + 15 minutos, inclusive; sin inicio previo |
| Cancelar | CONFIRMED | CANCELLED | Reglas del actor descritas abajo |

Las acciones no cambian startsAt/endsAt ni extienden la reserva. Comenzar tarde no desplaza compromisos posteriores. El flujo del cliente que llegó pasa por IN_PROGRESS y luego COMPLETED, que se admite desde el fin del bloque; una cita que llegó a IN_PROGRESS nunca puede ser NO_SHOW. El personal registra el resultado al terminar el bloque. Los límites se calculan con el `Clock` inyectado y los `Instant` persistidos; la agenda presenta la fecha en `America/Mexico_City`, sin depender de la zona del equipo.

Las filas se bloquean para actualizar estado. Repetir start mientras sigue IN_PROGRESS, complete sobre COMPLETED, no-show sobre NO_SHOW o cancel sobre CANCELLED devuelve el resultado existente: no sustituye actor, fecha ni motivo. Un NO_SHOW ya registrado dentro de la ventana sigue siendo idempotente aunque se repita después. Otra transición desde un estado final devuelve 409. Los permisos se comprueban antes de devolver una repetición. Crear una reserva/presencial no usa claves de idempotencia; una repetición que solape obtiene 409.

## Cancelaciones

El cliente envía `{}` o `{"reason":"Motivo opcional"}`. Solo cancela sus CONFIRMED; exactamente 60 minutos de anticipación se acepta, 59 no. Un usuario con varios roles puede usar esta ruta como cliente, manteniendo sus límites y atribución CLIENT.

El personal envía `{"reason":"Motivo de la cancelación"}`, no vacío y hasta 500 caracteres. BARBER solo sus citas, ADMIN cualquiera. Sin límite de anticipación para CONFIRMED; no se cancela una atención ya iniciada. La ruta de personal atribuye el origen STAFF, nunca al cliente.

Se guardan actor, instante, motivo y origen CLIENT/STAFF. Repetir una cancelación autorizada conserva la auditoría original. Cancelación, liberación de ocupación y, si procede, actualización de incidencias se realizan en una sola transacción. Sin reembolsos ni simulación de devoluciones.

## Incidencias consecutivas y bloqueo comercial

Una cancelación por la ruta del propio CLIENT suma una incidencia a su cuenta. NO_SHOW marcado por BARBER o ADMIN también suma; cancelar por la ruta del personal nunca suma, incluso si el cliente creó la cita. Los invitados no tienen cuenta ni contador. COMPLETED reinicia el contador a cero. El segundo evento consecutivo de incidencia produce una advertencia; el tercero bloquea nuevas reservas. Tras el bloqueo, una cita COMPLETED reinicia el contador pero **no** levanta el bloqueo: solo ADMIN puede reactivar. El contador futuro de pedidos es independiente y aún no existe.

La consecutividad sigue el **orden de confirmación de las transiciones válidas**, no la fecha programada de la cita. PostgreSQL bloquea la fila de la cuenta al asignar una secuencia creciente a cada evento; esto serializa cambios de distintas citas sin perder incrementos. Solo se permite registrar NO_SHOW entre el fin de la cita y 15 minutos después, inclusive. Por ejemplo, si la cita del lunes queda CONFIRMED sin resultado, otra cita del martes llega a COMPLETED y el miércoles alguien intenta marcar la del lunes NO_SHOW, el intento devuelve 409: la cita del lunes queda identificable para revisión administrativa, sin evento de incidencia ni cambio de racha. La marca horaria se registra al tomar el bloqueo de cuenta; para empates de la migración de eventos anteriores a V5 se ordena por hora de transición e identificador de cita. Las mismas transiciones repetidas no insertan otro evento; hay además unicidad por cita en el historial.

`GET /api/v1/users/me/booking-status` devuelve solo `{bookingBlocked, consecutiveAppointmentIncidents, warning}` de la cuenta autenticada. `warning` tiene texto cuando el contador llega a 2 y aún no está bloqueada; en los demás casos es `null`. Una cancelación propia devuelve ese mismo objeto en `bookingStatus` dentro de la respuesta de la cita, de modo que la segunda cancelación comunica la advertencia inmediatamente. Si la segunda incidencia es NO_SHOW, el cliente la verá al consultar su estado. No se envían correo, SMS ni notificaciones externas.

Una cuenta bloqueada conserva `active=true`, login, JWT, consulta de citas y cancelación de sus citas confirmadas con al menos 60 minutos. No se cancelan compromisos existentes. `POST /api/v1/appointments` devuelve 409 con el mensaje `La cuenta está bloqueada para nuevas reservas y compras.`; la comprobación comercial es central y podrá ser usada por futuras compras cuando se implementen. Las peticiones de reserva toman un bloqueo compartido de la fila de cuenta, compatible con otras reservas y excluyente con actualizaciones del contador o reactivación.

Para reactivar, ADMIN envía `POST /api/v1/admin/clients/{id}/reactivate` con `{"reason":"Revisión administrativa"}` (1–500 caracteres no blancos). Recibe el estado con contador cero y bloqueo desactivado. La operación exige una cuenta CLIENT bloqueada; repetirla cuando ya no lo está devuelve 409. Se conserva el historial de incidencias y se añade un evento con actor, fecha y motivo. El propio cliente y BARBER reciben 403; ni siquiera una cuenta con ADMIN y CLIENT puede reactivarse a sí misma.

V5 añade columnas a `accounts` y la tabla `appointment_client_events`. Una secuencia por cuenta preserva el orden y `appointment_id` único protege frente a recuentos dobles; cada evento conserva cita, tipo, actor, instante y resultado. V5 incorpora al historial estados terminales de citas existentes por su hora de transición. V4 no almacenaba el actor de NO_SHOW o COMPLETED; ese dato se conserva como desconocido solo para eventos anteriores a V5. V1–V4 no cambian. No se modifican las ocupaciones de citas ni se crean tablas de pedidos.

Una cita CONFIRMED después de `endsAt + 15 minutos` no se convierte automáticamente en NO_SHOW. `POST .../no-show` devuelve 409 con un mensaje de ventana vencida y no altera contador ni ocupación. Su consulta de personal muestra `requiresAdministrativeReview=true`; no se incorporó una transición tardía o una penalización administrativa alternativa. A las 15:00 para una cita 14:00–15:00 se admite NO_SHOW; a las 15:15 aún se admite; a las 15:15:01 se rechaza.

## Bloqueos individuales

```json
{
  "barberId": "30000000-0000-0000-0000-000000000001",
  "startsAt": "2030-01-07T20:25:00Z",
  "endsAt": "2030-01-07T21:25:00Z",
  "reason": "Ausencia de ejemplo"
}
```

Solo ADMIN crea/cancela. Inicio anterior a fin y no anterior al instante actual; motivo obligatorio de hasta 500 caracteres. Pueden cubrir intervalos no alineados a horas exactas y más de una hora. Son indisponibilidades administrativas: pueden cubrir días completos o períodos fuera del horario de atención; no amplían los horarios en que se admiten citas.

No se cancelan ni desplazan citas. Solapar una ocupación activa, incluido otro bloqueo, devuelve 409. Se conservan creador/fecha, estado ACTIVE/CANCELLED y actor/fecha de cancelación. Cancelar repetidamente es idempotente. La consulta del personal mantiene bloqueos cancelados para el historial.

## PostgreSQL: V4 y garantía contra solapamientos

V1, V2 y V3 permanecen intactas. **V4** adapta las citas existentes a RESERVATION y atribuye su creación a su cliente. Amplía estados y datos de invitado/auditoría; conserva duración de una hora y aplica precisión por origen. Añade barber_blocks y agenda_occupations, claves foráneas, restricciones de estados y auditoría, e índices para las agendas por barbero/sucursal y fecha.

El índice V3 de igualdad de inicio ya no basta y V4 lo sustituye por una restricción de exclusión en la tabla común:

```sql
EXCLUDE USING gist (
  barber_id WITH =,
  tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (active)
```

Cada ocupación tiene exactamente una fuente: appointment_id o block_id, únicos y con clave foránea. Se migran las ocupaciones existentes sin eliminar datos. Si existieran históricos incompatibles, la migración falla; no los limpia ni los oculta.

Triggers PostgreSQL sincronizan las ocupaciones al insertar o cambiar el estado/intervalo de una cita o bloqueo, dentro de la misma transacción. CONFIRMED, IN_PROGRESS, COMPLETED y NO_SHOW producen active=true; CANCELLED produce active=false. Nunca se borra la ocupación al cancelar. Esto también protege escrituras SQL sobre las tablas fuente. No se debe escribir directamente la tabla derivada agenda_occupations ni deshabilitar sus triggers.

Dos transacciones independientes que intenten ocupar intervalos solapados para el mismo barbero no pueden confirmar ambas. La aplicación realiza una comprobación previa para responder pronto, pero la garantía final es PostgreSQL. SQLSTATE **23P01** (exclusión), **40P01** (víctima de deadlock en el INSERT especulativo de la ocupación) y 23505 (unicidad) se traducen a 409 en esa escritura. El rollback incluye cita/bloqueo, servicios y ocupación. No hay synchronized ni locks Java de producción.

La disponibilidad consulta la tabla común, usando el mismo predicado de solapamiento. Bloqueos compartidos de catálogo conservan horarios, estados y precios durante la creación; actualizaciones de citas/bloqueos usan bloqueo de fila para evitar transiciones incompatibles o efectos duplicados.

### Extensión y permisos

V4 ejecuta `CREATE EXTENSION IF NOT EXISTS btree_gist` **en la base conectada**, para la igualdad UUID en GiST. PostgreSQL distribuye btree_gist como extensión trusted: puede instalarla un usuario con CREATE sobre esa base, o puede preinstalarla el administrador de la base. No se alteran roles ni privilegios globales. Referencias oficiales: [extensiones adicionales](https://www.postgresql.org/docs/current/contrib.html) y [CREATE EXTENSION](https://www.postgresql.org/docs/current/sql-createextension.html).

Si faltan archivos de extensión, el administrador del servidor debe instalar el paquete contrib correspondiente a su versión de PostgreSQL. Si faltan permisos, solicitar que un administrador se conecte **a la base destino** y ejecute:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
```

Después se vuelve a ejecutar Flyway normalmente, sin editar migraciones ni usar clean o baseline. El usuario de la aplicación necesita acceso normal a sus tablas; el usuario que migra necesita permisos de DDL. Las pruebas usan exclusivamente barberia_auth_test y no modifican barberia_db.

## Errores y pendientes

400 para entrada inválida, motivo obligatorio ausente, fechas/horas mal formadas, tipos de servicio incorrectos, campos desconocidos o paginación inválida; 401 para autenticación inválida; 403 por rol insuficiente; 404 para cita/perfil ajeno o recurso no disponible; 409 para intervalo ocupado, fuera de atención o transición incompatible. Se mantiene `timestamp, status, code, message` sin detalles SQL.

Pendientes: incidencias y contador independiente de pedidos, identificación/vinculación de invitados, reprogramación, inventario, ventas, cobros, pagos y reembolsos. Mercado Pago permanece elegido para una integración futura sin dependencias ni código en esta entrega. Cambiar horarios administrativos no reprograma citas existentes.
