# Disponibilidad y reserva de citas

Esta entrega añade el módulo `appointments`: disponibilidad diaria, reserva y consultas propias. Reutiliza JWT y roles actuales, Java 25, Spring Boot 3.5.16, Maven Wrapper y las respuestas de error existentes. No añade dependencias.

## Rutas y permisos

Todas requieren Bearer JWT válido y cuenta activa. Para reservar y consultar citas propias se requiere **CLIENT**, incluso si la cuenta también tiene ADMIN o BARBER. Tener solo ADMIN o BARBER no habilita esas operaciones.

| Método y ruta | Permiso | Resultado |
| --- | --- | --- |
| GET /api/v1/appointments/availability?branchId={id}&barberId={id}&date=2030-01-07 | Autenticado | 200, bloques libres de ese barbero, sucursal y fecha |
| POST /api/v1/appointments | CLIENT | 201, cita y cabecera Location |
| GET /api/v1/appointments?page=0&size=20&sort=startsAt,desc | CLIENT | Página de citas del cliente autenticado |
| GET /api/v1/appointments/{id} | CLIENT propietario | 200, detalle histórico |

El listado acepta `page >= 0`, `size` de 1 a 100 y ordenación por `startsAt` o `id`, asc/desc. El orden predeterminado es startsAt descendente con id como desempate. No acepta filtros por cliente ni incluye citas ajenas.

Ejemplo de cuerpo de reserva, con UUID ficticios que deben sustituirse por identificadores del catálogo activo:

```json
{
  "barberId": "30000000-0000-0000-0000-000000000001",
  "date": "2030-01-07",
  "time": "09:00",
  "primaryServiceId": "20000000-0000-0000-0000-000000000001",
  "addOnIds": ["20000000-0000-0000-0000-000000000004"]
}
```

`barberId` es el identificador del **perfil**, no el de la cuenta. Fecha y hora son locales. `primaryServiceId` es obligatorio, activo y HAIRCUT. Los complementos deben ser ADD_ON activos, sin duplicados ni valores nulos. `addOnIds` omitido, nulo o vacío significa sin complementos. No admite clientId, branchId, status, precio ni total: los campos desconocidos se rechazan. La sucursal se infiere del perfil y el cliente del principal autenticado.

Respuesta de disponibilidad (ejemplo parcial):

```json
{
  "branchId": "10000000-0000-0000-0000-000000000001",
  "barberId": "30000000-0000-0000-0000-000000000001",
  "date": "2030-01-07",
  "timeZone": "America/Mexico_City",
  "slots": [
    {"startsAt": "2030-01-07T15:00:00Z", "endsAt": "2030-01-07T16:00:00Z"}
  ]
}
```

La cita devuelve `id, barberId, branchId, startsAt, endsAt, timeZone, status, services, total, currency`. Cada elemento de services contiene `serviceId, name, type, price`, como copia histórica; el primero es el corte principal. Estado CONFIRMED, moneda MXN. Nunca devuelve datos de contacto, credenciales ni identidad de otros clientes. El listado usa el formato existente `{items, page, size, totalElements, totalPages}`.

Flujo: iniciar sesión → consultar sucursales/barberos/servicios activos → consultar disponibilidad → enviar reserva con el JWT → consultar lista o detalle propios. La disponibilidad no retiene ni garantiza el bloque; un competidor puede reservarlo antes.

## Reglas temporales

Se leen los horarios semanales de la sucursal en PostgreSQL. No hay horarios de atención duplicados en Java. Se construyen bloques de una hora a horas exactas que caben completos dentro de la apertura/cierre; se excluyen descansos, citas CONFIRMED y comienzos anteriores al Clock inyectado. Un inicio exactamente igual al instante actual se permite. Un día cerrado o pasado devuelve una lista vacía.

Todos los intervalos son **[inicio, fin)**. El final de una cita puede coincidir con el inicio de otra; el final del bloque puede coincidir con el cierre o inicio del descanso. Con los horarios iniciales, principal admite 09–10 y excluye 10–11; sucursal 2 admite 10–11 y excluye 11–12. Ambas terminan con 17–18 y cierran el domingo.

La API convierte fecha/hora local usando `America/Mexico_City` a `Instant`; PostgreSQL guarda `TIMESTAMPTZ` y las respuestas son ISO-8601 UTC (`Z`). Por ejemplo 09:00 local del 7 de enero de 2030 equivale a 15:00Z. No se usa la zona del sistema operativo. Horas locales ambiguas/inexistentes se rechazan; disponibilidad omite bloques que atraviesen un cambio de zona y no duren exactamente una hora real. Las pruebas fijan el reloj; la aplicación usa el Clock UTC ya existente para JWT.

## Modelo, transacción y concurrencia

V3 añade:

- `appointments`: cliente, perfil y sucursal mediante claves foráneas, inicio/fin, estado CONFIRMED, total NUMERIC(19,2) y creación. Restricciones de duración exacta de una hora, inicio alineado a hora local y total positivo.
- `appointment_services`: copia del identificador, nombre, tipo y precio NUMERIC(12,2). Posición cero HAIRCUT; las siguientes ADD_ON. Un servicio no puede repetirse en una cita. La validación del servicio exige un corte y calcula el total con BigDecimal. Las claves foráneas conservan las referencias; cambiar o desactivar el catálogo no altera las copias.
- Índice único parcial `appointments_confirmed_slot(barber_id, starts_at) WHERE status = 'CONFIRMED'`. La duración y alineación fijas hacen que un mismo inicio sea el único empalme posible. PostgreSQL impide que dos transacciones creen ese bloque, incluso desde instancias distintas de la API.
- Índice `(client_id, starts_at, id)` para las consultas propias; el índice parcial sirve también para consultar ocupación de un barbero.

La transacción valida nuevamente cuenta, perfil, sucursal, horario, servicios y ocupación. Bloqueos compartidos de filas mantienen estables los estados y el catálogo mientras se guarda, sin impedir que dos reservas compitan por el índice único. Se bloquea primero la sucursal y el perfil; servicios en orden de UUID. La infracción de unicidad PostgreSQL 23505 devuelve 409 y revierte toda la reserva, incluidos sus servicios.

Para cancelar en una entrega futura se añadirá un estado mediante **otra migración** y una transición autorizada que cambie CONFIRMED a ese estado. La fila dejará de participar en el índice parcial, liberando el bloque sin borrar la cita ni sus precios históricos. No existe aún esa transición.

V1 y V2 permanecen intactas. Se conserva `ddl-auto=validate`, Flyway sin limpieza ni baseline automático. No se migra ni limpia barberia_db durante pruebas.

## Errores y privacidad

- 400: campos obligatorios ausentes, fecha/hora mal formada, inicio no exacto o pasado al reservar, tipos incorrectos, complementos duplicados/nulos, campos adicionales o paginación inválida.
- 401: JWT ausente/inválido/expirado o cuenta inactiva.
- 403: falta CLIENT en reserva/listado/detalle.
- 404: recurso inexistente/inactivo, barbero que no pertenece a la sucursal consultada o cita ajena. Cita ajena e inexistente tienen el mismo código y mensaje.
- 409: bloque ocupado, cerrado, fuera de atención o en descanso; también competencia resuelta por el índice único.

Se conserva el JSON común `timestamp, status, code, message`. La disponibilidad solo expone instantes, nunca datos del ocupante. Las consultas propias incluyen client_id en la consulta SQL; conocer un UUID no concede permiso.

## Límites de la entrega

Sin cancelaciones, reprogramación, inasistencias, invitados, bloqueos individuales/comerciales, agenda administrativa o del barbero, ni cobros. Una cita confirmada y su total no representan ingresos recibidos. **Mercado Pago** está elegido para la integración futura, sin código ni dependencias de pagos.

Las actualizaciones administrativas de horario no reprograman citas existentes; estas conservan sus instantes e historial. No se añade una política de modificación de compromisos existentes en esta entrega.
