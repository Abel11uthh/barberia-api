# Instrucciones de trabajo

- Inspeccionar los archivos y el estado de Git antes de editar. Conservar el trabajo existente; no revertir cambios ajenos.
- Hacer cambios pequeños y centrados en la tarea solicitada. Evitar refactorizaciones, dependencias y archivos que no sean necesarios.
- Consultar `docs/alcance.md` y la especificación original cuando esté disponible. No inventar requisitos ni módulos para suplir un documento faltante.
- Mantener un monolito organizado por módulos de negocio bajo `com.barberia.api`. Crear cada módulo solo cuando se implemente una necesidad confirmada.
- Respetar controller → service → repository: HTTP y validación de entrada, reglas y transacciones, acceso a datos, respectivamente. No acceder a repositorios desde controllers.
- El alcance implementado es la base, cuentas/autenticación JWT, sucursales, catálogo de servicios y perfiles de barbero. No agregar citas, invitados, bloqueos individuales de horario, bloqueos comerciales, inventario, ventas, pagos, Docker ni despliegue sin una tarea posterior que los solicite.
- No añadir carpetas vacías o clases de ejemplo para módulos futuros.
- Mantener Java 25 LTS sin características preview y usar Maven Wrapper para verificar los cambios.
- Nunca incluir secretos, credenciales reales, tokens ni datos sensibles en código, documentación, pruebas, salidas o commits. Usar variables de entorno y ejemplos ficticios.
- No usar `ddl-auto=update` ni generación automática del esquema. Usar migraciones versionadas de Flyway y `ddl-auto=validate`; no editar migraciones aplicadas ni limpiar esquemas existentes. Las pruebas PostgreSQL usan exclusivamente `barberia_auth_test`, nunca `barberia_db`.
- Ejecutar verificaciones proporcionales al cambio. Informar los comandos ejecutados, sus resultados y cualquier limitación; no presentar una compilación como una prueba de arranque o integración.
- No hacer commit, push ni merge sin autorización explícita del usuario.
