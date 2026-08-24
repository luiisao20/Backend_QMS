# App móvil (Flutter) — endpoints que faltan

> ## ⚠️ ESTA COPIA ESTÁ DESACTUALIZADA — 2026-08-23
>
> **La versión al día es `docs/flutter-endpoints-faltantes.md`**, en la raíz del
> monorepo. Ese archivo tiene una tabla de estado sección por sección. Este es el
> resumen de una línea: **casi todo lo que este documento pide ya está hecho**, en
> el backend y en la app.
>
> El cuerpo se dejó intacto a propósito — el razonamiento de cada sección sigue
> siendo válido y es lo que explica *por qué* cada endpoint tiene la forma que
> tiene. Lo que cambió es el estado, no el análisis.
>
> Sigue sin hacer, y con motivo declarado en el doc canónico: `POST /auth/logout`
> (§2, no hace falta), y el historial clínico y la cobertura de §4 (no hay tablas
> y la recomendación es no inventarlas).

**Nota:** recuperación de contraseña ya está en curso, así que va listada solo para
completitud, sin plan.

---

## Resumen

| | Endpoints | Detalle |
| :--- | ---: | :--- |
| ✅ Funcionan hoy | **4** | Login, registro paso 1, registro paso 3, crear turno |
| ⛔ La app los declara y no existen | **4** | verify-otp, forgot-password, reset-password, logout |
| ⚠️ La app los necesita y nadie los declaró | **4** | patients/me (GET y PUT), turns/me, schedules con filtros |
| 🔴 Requieren tablas nuevas | **2 bloques** | Historial clínico y Cobertura |

**Con 3 endpoints nuevos y 1 arreglo de seguridad se conectan 3 de las 4 pestañas
del home, sin tocar el esquema de la base.**

---

## 1. Lo que ya funciona

Verificado leyendo los controllers y servicios, no asumido.

| Endpoint | Usado por | Nota |
| :--- | :--- | :--- |
| `POST /auth/mobile/login-patient` | Login | Devuelve el token en el header `Authorization`. Es la variante correcta para un cliente nativo. |
| `POST /auth/init-registration-patient` | Registro paso 1 | El endpoint existe y está bien. Hoy falla por el relay SMTP — ver §5. |
| `POST /auth/register-patient` | Registro paso 3 | Token de 24h por `Set-Cookie`. La app parsea la cookie a mano. |
| `POST /api/turns` | Agendar (confirmar) | **Sí funciona para un paciente.** Ver el detalle abajo. |

### Por qué `POST /api/turns` sí funciona

Vale escribirlo porque a primera vista parece roto. `TurnService.create` hace:

```java
UUID uuid = UUID.fromString(authName);   // authName = auth.getName()
```

Parece que va a explotar, porque el subject de un JWT suele ser el email. No explota:
`PatientService.loadUserByUsername` construye el `UserDetails` con
`.username(patient.getUuid().toString())`, así que `auth.getName()` **es un UUID**.
`JwtProvider` lo guarda en el claim `uuid` y `JwtValidator` lo reconstruye igual.

Los dos tipos de token llevan subjects distintos a propósito:

| Token | Claim | Subject | Lo consume |
| :--- | :--- | :--- | :--- |
| Login (24h) | `uuid` | UUID del paciente | `TurnService.create` |
| Flash de registro (300s) | `email` | Email | `AuthController.registerPatient` |

Es correcto, pero es sutil. **No lo unifiquen sin revisar los dos consumidores.**

---

## 2. Endpoints que la app declara y el servidor no tiene

Están en `lib/core/network/api_endpoints.dart`, documentados ahí como inexistentes.

| Endpoint | Estado |
| :--- | :--- |
| `POST /auth/verify-otp` | No existe. Ver §5 — son **tres** cosas, no una. |
| `POST /auth/forgot-password` | No existe. *Ya lo están haciendo.* |
| `POST /auth/reset-password` | No existe. *Ya lo están haciendo.* |
| `POST /auth/logout` | No existe, y **no hace falta**. Un JWT stateless se cierra borrándolo del dispositivo, que es lo que hace `AuthLocalDataSource`. No inviertan tiempo acá. |

---

## 3. Endpoints que la app necesita y nadie declaró

Esto es el trabajo real. Ordenado por costo.

### 3.1 `GET /api/patients/me` — el más barato de todos

**Desbloquea:** pestaña *Mi perfil* (nombre + cédula) y el bloque *Identidad* de
*Mi información*.

No existe `PatientController`. Pero `PatientService` **ya tiene el método escrito**:

```java
public PatientDTO getPatientById(UUID id)   // ya existe, hoy es código inalcanzable
```

Y como `auth.getName()` ya es el UUID del paciente, el controller es literalmente:

```java
@GetMapping("/me")
public ResponseEntity<PatientDTO> me(Authentication auth) {
  return ResponseEntity.ok(
      patientService.getPatientById(UUID.fromString(auth.getName())));
}
```

Cero migraciones. Un archivo nuevo. **Importante:** que el DTO de respuesta no lleve
`password`. `TurnService.mapToDTO` ya resuelve esto bien — copien ese criterio.

### 3.2 `PUT /api/patients/me` — mismo controller

**Desbloquea:** el botón "Editar datos de contacto" de *Mi información*, que hoy tiene
un `onPressed` vacío.

`PatientService.updatePatient(UUID id, PatientDTO dto)` también ya existe y también es
inalcanzable hoy.

Ojo con el alcance: *Mi información* separa a propósito **Identidad** (nombre, cédula,
fecha de nacimiento, sexo) de **Contacto** (correo, celular, dirección, contacto de
emergencia). Identidad es de solo lectura porque la historia clínica está archivada con
esos datos. El endpoint debería aceptar **solo los campos de contacto** e ignorar el
resto, no confiar en que el cliente se porte bien.

### 3.3 `GET /api/turns/me` — el que más desbloquea

**Desbloquea:** la pestaña *Mis citas* completa (próximas y pasadas), y alrededor del
**60% de *Historial*** — ver la nota abajo.

Hoy existe `GET /api/turns`, pero devuelve **todos los turnos del sistema** paginados,
sin filtro por paciente. La app tendría que recorrer todas las páginas y filtrar del
lado del cliente, que además de ser incorrecto expone datos de otros pacientes.

Hace falta filtrar por el paciente del token, y conviene aceptar además:

```
GET /api/turns/me?status=TURN_PENDING&from=2026-08-01&to=2026-12-31
```

`status` es lo que separa "Próximas" de "Pasadas" en la pestaña:
`TURN_PENDING`/`TURN_WAITNG` vs `TURN_TREATED`/`TURN_CANCELLED`.

> **Historial con este mismo endpoint.** Filtrando por `TURN_TREATED` ya tienen fecha,
> doctor, especialidad y sede — que es la mitad superior de cada tarjeta del historial.
> Lo que falta es el resumen del diagnóstico y las etiquetas de salida (receta,
> laboratorio, certificado), y eso sí necesita tablas nuevas (§4). Pero la pestaña deja
> de estar vacía con un solo endpoint.

### 3.4 `GET /api/schedules` con filtros — desbloquea *Agendar*

**Desbloquea:** los tres pasos de la pestaña *Agendar* (médico, día, hora).

El endpoint existe pero `getAll` recibe **solo `Pageable`** — no acepta ningún filtro.
Un buscador de disponibilidad necesita preguntar por rango y por estado:

```
GET /api/schedules?doctorId=&serviceId=&stablishmentId=&from=&to=&status=STATUS_FREE
```

Con eso la pantalla arma sus tres pasos:

| Paso de la UI | De dónde sale |
| :--- | :--- |
| 1 / Médico | `GET /api/doctors` — **ya existe** |
| Tipo de consulta | `GET /api/services` — **ya existe** |
| 2 / Día | fechas distintas de `schedules` filtrados |
| 3 / Hora | horas de esos `schedules`, con `status` para tachar los ocupados |
| Confirmar | `POST /api/turns` — **ya existe y funciona** |

O sea: **agregar filtros a un endpoint que ya existe es lo único que falta para que
Agendar funcione de punta a punta.** El precio con cobertura no, eso es §4.

---

## 4. Lo que necesita tablas nuevas

Estos dos bloques ya están detallados en `docs/panel-admin-analisis-brecha.md` — son
las mismas tablas que le faltan al panel administrativo. Conviene modelarlos una vez
para los dos clientes.

| Pantalla móvil | Qué le falta |
| :--- | :--- |
| *Historial* — resumen del diagnóstico y etiquetas de salida | `encounters`, `prescriptions` + `prescription_items` |
| *Mi información* — bloque **Cobertura** (aseguradora, plan, número de afiliado) | `insurers`, `coverage_plans`, `patient_coverage` |
| *Agendar* — el precio "Con tu plan" vs el precio de lista | las mismas tablas de cobertura. Hoy `services.price` es un precio único sin plan. |

**Recomendación:** dejar estos tres en datos de ejemplo etiquetados, como están ahora.
La UI ya dice "Datos de ejemplo." de forma honesta, y eso vale más que inventar tablas
a las apuradas para un dato de salud.

---

## 5. Dos arreglos que no son endpoints nuevos

### 5.1 El registro está bloqueado por el correo — y el arreglo ya está escrito en su propio código

`AuthService.initRegistration` línea 145 llama a `mailService.sendTestEmail(...)` **sin
try/catch**, y el `return AuthResult.ok(...)` está en la línea 148. Cuando el mail falla
(`454 Relay access denied`), la excepción sube, el return nunca corre, el flash token
nunca se emite, y **nadie puede registrarse**.

Lo importante: **el patrón correcto ya existe en este repo.**
`TurnService.sendTurnEmail` (líneas 253–274) hace exactamente la misma operación
envuelta en `try { ... } catch (Exception e) { ... }`, así que un fallo de correo
degrada en vez de tumbar el flujo. Es el mismo arreglo, copiado de un archivo al otro.

### 5.2 El guard de seguridad de turnos no cubre la ruta real

`GlobalConfig.securityFilterChain` declara:

```java
.requestMatchers("/turns/**").authenticated()
.requestMatchers("/ws-turns/**").authenticated()
.anyRequest().permitAll()
```

Pero `TurnController` está en `@RequestMapping("/api/turns")`. **`/api/turns` no matchea
`/turns/**`**, así que cae en `.anyRequest().permitAll()` y queda público.

Un `GET /api/turns` sin autenticar devuelve, por cada turno: **correo, nombre, apellido
y cédula** del paciente, más fecha, hora, especialidad, doctor y sede de su cita.

Para ser justos: `TurnService.mapToDTO` **sí oculta el uuid y el password** — eso está
bien hecho y verificado. El problema no es el DTO, es que el guard no dispara.

El arreglo es una línea: `/api/turns/**`. Y vale revisar si `/ws-turns/**` tiene el
mismo desfase con la ruta real del WebSocket.

> Esto no parece haber sido una decisión: alguien escribió el matcher con la intención
> de proteger los turnos y la ruta quedó mal. Es distinto de "lo dejamos abierto a
> propósito".

### 5.3 El OTP: son tres cosas, no una

Si solo crean la ruta `/auth/verify-otp`, el código sigue sin validarse:

1. Crear la ruta `POST /auth/verify-otp`.
2. Hacer que `AuthService.initRegistration` **llame a `OtpService.saveOtp`** — hoy no lo
   llama nunca. `otpStore` está permanentemente vacío, así que `validate` devolvería
   `false` para cualquier código, incluso el correcto.
3. Hacer que `RegistrationBloc._onCodeSubmitted` (Flutter) llame al endpoint en vez de
   avanzar localmente.

Verificado con un grep sobre todo el backend: `generateOtp()` se llama una sola vez
(`AuthService:138`) y **`saveOtp` no tiene ni un call site**.

---

## 6. Plan sugerido

### Etapa 1 — 3 endpoints + 1 línea de seguridad, cero tablas

- `GET /api/patients/me` y `PUT /api/patients/me` (los métodos de servicio ya existen)
- `GET /api/turns/me` con filtro por `status`
- Corregir el matcher a `/api/turns/**`

**Resultado:** *Mi perfil*, *Mi información* (Identidad + Contacto) y *Mis citas*
quedan conectadas. *Historial* deja de estar vacío.

### Etapa 2 — filtros sobre un endpoint existente, cero tablas

- `GET /api/schedules` con `doctorId`, `serviceId`, `stablishmentId`, `from`, `to`, `status`

**Resultado:** *Agendar* funciona de punta a punta. Crear el turno ya funciona.

### Etapa 3 — desbloquear el registro

- try/catch en `AuthService:145`, copiando el patrón de `TurnService.sendTurnEmail`
- Mailpit o MailHog en desarrollo (`MAIL_SENDER_IP`), que captura sin relayar

### Etapa 4 — con tablas nuevas, coordinado con el panel administrativo

- `encounters` + `prescriptions` → *Historial* completo
- Tablas de cobertura → bloque *Cobertura* y precios reales en *Agendar*

---

## Trazabilidad

**clinicore-flutter**
- `lib/core/network/api_endpoints.dart` — los endpoints declarados, con las notas de cuáles no existen
- `lib/features/auth/data/datasources/auth_remote_data_source.dart` — las 3 llamadas reales
- `lib/features/home/presentation/screens/` — booking, appointments, history, profile, personal_info
- `lib/core/config/app_config.dart` — `API_BASE_URL` es la única variable de entorno que lee la app

**Backend_QMS**
- `controller/` — los 8 controllers (no hay `PatientController`)
- `services/PatientService.java` — `getPatientById` y `updatePatient` ya escritos, hoy inalcanzables
- `services/TurnService.java` — `create` (líneas 37–69), `mapToDTO` (169+), `sendTurnEmail` (252–274)
- `services/AuthService.java` — `initRegistration` (126–149), el mail sin guardar en la 145
- `config/GlobalConfig.java` — el matcher desfasado
- `jwt/JwtProvider.java` y `jwt/JwtValidator.java` — los dos subjects de token