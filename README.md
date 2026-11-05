# QMS Backend - Queue & Clinical Management System

Backend integral para la gestión de turnos (digiturno), agenda médica, historias clínicas, facturación, aseguradoras y visualización en tiempo real para centros de salud y clínicas médicas.

Desarrollado con **Java 17** y **Spring Boot**, enfocado en una arquitectura robusta, segura y desacoplada, con soporte para clientes Web (portal administrativo y pantallas de sala de espera) y aplicaciones móviles nativas (Flutter).

---

## 📌 Tabla de Contenidos

1. [Descripción General](#-descripción-general)
2. [Módulos y Funcionalidades](#-módulos-y-funcionalidades)
3. [Roles y Control de Acceso](#-roles-y-control-de-acceso)
4. [Stack Tecnológico](#-stack-tecnológico)
5. [Arquitectura y Comunicación en Tiempo Real](#-arquitectura-y-comunicación-en-tiempo-real)
6. [Estructura del Proyecto](#-estructura-del-proyecto)
7. [Variables de Entorno](#-variables-de-entorno)
8. [Instalación y Ejecución](#-instalación-y-ejecución)
9. [Endpoints Principales de la API](#-endpoints-principales-de-la-api)
10. [Ejecución de Pruebas](#-ejecución-de-pruebas)

---

## 📋 Descripción General

**QMS (Queue Management System)** centraliza todo el ciclo operativo de un centro médico o red de establecimientos de salud:

- **Recepción y Digiturno:** Gestión del flujo de pacientes desde su llegada a la sala de espera hasta la atención en consultorio mediante pantallas informativas en tiempo real.
- **Agenda y Citas Médicas:** Planificación de horarios fijos y plantillas recurrentes para médicos por especialidad y consultorio.
- **Gestión Clínica:** Registro seguro de atenciones (`Encounter`), recetas médicas (`Prescription`) y auditoría estricta de accesos a datos de pacientes (`ClinicalAccessLog`).
- **Administración Financiera:** Facturación, cobros multicanal, gestión de seguros/coberturas (`Insurers`, `CoveragePlans`) y liquidación de reclamos médicos (`Claims`).
- **Analítica y Métricas:** Tableros con conteos diarios, métricas de rendimiento por sede, profesionales y estadísticas de ocupación.

---

## 🚀 Módulos y Funcionalidades

### 1. Autenticación y Cuentas
- Autenticación stateless mediante **JWT**.
- Estrategia dual de sesión:
  - **Web:** Tokens transportados mediante Cookies seguras `HttpOnly`.
  - **Móvil (Flutter):** Tokens en cabecera `Authorization: Bearer <token>`.
- Flujo de registro en múltiples pasos y recuperación de contraseña vía **códigos OTP** por correo electrónico con plantillas HTML en Thymeleaf.

### 2. Gestión de Turnos y Sala de Espera (Digiturno)
- Estados del turno: `TURN_PENDING` → `TURN_WAITNG` (Check-in en counter) → `TURN_IN_TREATMENT` (Llamado a consultorio) → `TURN_TREATED` (Atendido) o `TURN_CANCELLED`.
- Generación automática de tickets de atención.
- Pantalla pública para Smart TVs colgadas en salas de espera (`/api/sala/{sedeId}/pantalla`) que omite PII (datos personales sensibles) para cumplimiento de privacidad.

### 3. Agendas y Horarios
- Plantillas de horarios semanales (`ScheduleTemplate`) y generación por lotes de slots de citas.
- Gestión de indisponibilidad médica (`TimeOff`), motivos de bloqueo (`BlockReason`) y días festivos (`Holiday`).

### 4. Módulo Clínico
- Atenciones y encuentros médicos (`Encounter`) vinculados al turno y al historial del paciente.
- Prescripción médica (`Prescription`) con desglose de medicamentos/indicaciones (`PrescriptionItem`).
- Guardia de seguridad clínica (`ClinicalAccessGuard`) que restringe el acceso al médico tratante del paciente.
- Registro inmutable de auditoría de consulta de expedientes (`ClinicalAccessLog`).

### 5. Facturación, Coberturas y Reclamos
- Generación de facturas (`Invoice`) con líneas de detalle (`InvoiceLineItem`).
- Anulación de facturas bajo control exclusivo de administradores.
- Cobranzas (`Payment`) mediante múltiples métodos de pago (Efectivo, Tarjeta, Transferencia, Seguro).
- Módulo de aseguradoras (`Insurer`), planes de cobertura (`CoveragePlan`) y gestión del ciclo de vida de reclamos (`ClaimStatus`: pendiente, aprobado, rechazado, pagado).
- Resúmenes contables y balances en `/api/accounting/**`.

### 6. Contenido Público y Landing Page
- Endpoints modulares `/api/landing/{seccion}` para alimentar dinámicamente la web institucional (especialidades, médicos, preguntas frecuentes, sedes, aseguradoras afiliadas).

---

## 👥 Roles y Control de Acceso

El sistema implementa 4 roles principales gobernados en `GlobalConfig.java`:

| Rol | Identificador | Alcance |
| :--- | :--- | :--- |
| **Administrador** | `ROLE_ADMIN` | Control total del sistema, configuración de sedes, médicos, servicios, anulación de facturas, auditoría y métricas. |
| **Operador / Recepción** | `ROLE_EMPLOYEE` | Check-in de pacientes, llamado a sala, agendamiento asistido, emisión de facturas y cobros. |
| **Médico** | `ROLE_DOCTOR` | Visualización de turnos asignados, llamado a su propio consultorio, registro de atenciones y recetas. |
| **Paciente** | `ROLE_PATIENT` | Consulta y reserva de sus turnos (`/me`), historial de atenciones, recetas y perfil. |

---

## 🛠 Stack Tecnológico

- **Lenguaje:** Java 17
- **Framework:** Spring Boot 4.1.0 / 3.x
  - Spring Web MVC
  - Spring Security (Filtro JWT personalizado, BCrypt)
  - Spring Data JPA
  - Spring WebSocket (STOMP + SockJS)
  - Spring Mail & Thymeleaf (Emails transaccionales)
  - Bean Validation
- **Base de Datos:** PostgreSQL
- **Persistencia:** Hibernate ORM
- **Utilidades:** Project Lombok, JJWT (`io.jsonwebtoken`)
- **Contenedores:** Docker (Dockerfile multi-stage)
- **Testing:** JUnit 5, Mockito, Spring Boot Test

---

## 📡 Arquitectura y Comunicación en Tiempo Real

El sistema utiliza **STOMP sobre SockJS** para notificar cambios de estado en salas de espera y pantallas de llamados:

- **Endpoint de Conexión:** `/ws-turns`
- **Prefijo de Destino de Aplicación:** `/app`
- **Broker de Mensajes:** `/topic`
- **Canales de Notificación:**
  - `/topic/stablishments/{id}`: Notificaciones para pantallas de sala de espera (llamado de turnos a consultorios específicos).
  - `/topic/doctors/{doctorId}`: Notificaciones en tiempo real para la bandeja de entrada del médico.

---

## 📁 Estructura del Proyecto

```text
backend-qms/
├── .github/                      # Workflows de CI/CD
├── src/
│   ├── main/
│   │   ├── java/com/devluis/
│   │   │   ├── config/           # Configuración de Seguridad, CORS y WebSockets
│   │   │   ├── controller/       # Controladores REST por dominio
│   │   │   ├── dto/              # Objetos de Transferencia de Datos
│   │   │   ├── entity/           # Entidades JPA / Modelo relacional
│   │   │   ├── exception/        # Manejador global de excepciones
│   │   │   ├── jwt/              # Filtro y utilidades de validación/generación JWT
│   │   │   ├── repository/       # Interfaces Spring Data JPA
│   │   │   ├── services/         # Lógica de negocio y guardias de acceso
│   │   │   ├── types/            # Enums de estado, roles y tipos de datos
│   │   │   └── utils/            # Generadores de tickets, cookies y formatos de dinero
│   │   └── resources/
│   │       ├── application.properties      # Configuración general
│   │       ├── application-dev.properties  # Configuración perfil dev
│   │       ├── landing/                    # Datos estáticos para la landing pública
│   │       └── templates/                  # Plantillas Thymeleaf para notificaciones por email
│   └── test/                               # Suite de pruebas unitarias e integración
├── websocket/                              # Cliente HTML/JS para prueba de STOMP
├── Dockerfile                              # Multi-stage build optimizado
└── pom.xml                                 # Dependencias Maven
```

---

## ⚙️ Variables de Entorno

Configurar las siguientes variables en un archivo `.env` o en el entorno del servidor:

| Variable | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `DATABASE_URL` | Cadena JDBC de conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/qms_db` |
| `DATABASE_USERNAME` | Usuario de base de datos | `postgres` |
| `DATABASE_PASSWORD` | Contraseña de base de datos | `tu_password` |
| `JWT_SECRET_KEY` | Clave secreta para firmar tokens JWT | `cadena_alfanumerica_segura` |
| `MAIL_SENDER_IP` | Host o IP del servidor de correos (Postfix/SMTP) | `127.0.0.1` |
| `CORS_ALLOWED_ORIGIN` | Orígenes permitidos separados por comas | `http://localhost:4200,http://localhost:3000` |

---

## 💻 Instalación y Ejecución

### Requisitos Previos
- Java JDK 17 o superior.
- PostgreSQL 14+ en ejecución.
- Maven 3.8+ (o utilizar el wrapper `./mvnw` incluido).

### 1. Clonar y configurar
```bash
git clone <url-del-repositorio>
cd backend-qms
```

Configura tu archivo `.env` o exporta las variables de entorno requeridas.

### 2. Ejecutar con Maven Wrapper
```bash
# En Linux/macOS
./mvnw spring-boot:run

# En Windows (PowerShell)
.\mvnw.cmd spring-boot:run
```
El servidor iniciará en el puerto `8080`.

### 3. Ejecutar con Docker
```bash
# Construir la imagen
docker build -t qms-backend:latest .

# Ejecutar el contenedor
docker run -d -p 8080:8080 --env-file .env --name qms-backend qms-backend:latest
```

---

## 🔌 Endpoints Principales de la API

### Autenticación (`/auth`)
- `POST /auth/login-patient` | `POST /auth/login-doctor` | `POST /auth/login-operator`: Inicio de sesión (Web/Cookie).
- `POST /auth/mobile/login-patient`: Inicio de sesión para Flutter (Bearer token).
- `POST /auth/init-registration-patient` & `POST /auth/register-patient`: Flujo de registro con código OTP.
- `POST /auth/recover-password/init` & `POST /auth/recover-password/change`: Recuperación de contraseña.

### Turnos y Pantallas (`/api/turns`, `/api/sala`)
- `POST /api/turns`: Creación de turno por paciente autenticado.
- `POST /api/turns/staff`: Creación de turno en ventanilla por personal.
- `GET /api/turns/me`: Listado de turnos del paciente en sesión.
- `PUT /api/turns/{id}/waiting`: Marcar llegada / check-in del paciente.
- `PUT /api/turns/{id}/in-treatment`: Llamar al paciente a un consultorio específico.
- `PUT /api/turns/{id}/treated`: Finalizar atención del turno.
- `GET /api/sala/{sedeId}/pantalla`: Carga inicial para pantalla pública de digiturno.

### Historia Clínica y Recetas (`/api/encounters`, `/api/prescriptions`)
- `POST /api/encounters`: Registro de consulta médica (`ROLE_DOCTOR`).
- `GET /api/encounters/me`: Historial de consultas del paciente autenticado.
- `POST /api/prescriptions`: Emisión de receta médica.
- `GET /api/prescriptions/me`: Recetas del paciente autenticado.

### Facturación y Finanzas (`/api/invoices`, `/api/accounting`)
- `POST /api/invoices`: Emisión de factura.
- `GET /api/invoices/me`: Facturas del paciente autenticado.
- `POST /api/invoices/{id}/payments`: Registro de pago a factura.
- `PUT /api/invoices/{id}/void`: Anulación de factura (`ROLE_ADMIN`).
- `GET /api/accounting/summary`: Resumen de ingresos y saldos contables.

### Métricas (`/api/metrics`)
- `GET /api/metrics/summary`: Resumen de la jornada actual (turnos atendidos, en espera, cancelados).
- `GET /api/metrics/turns`: Serie temporal de turnos con filtros de fecha y sede.

---

## 🧪 Ejecución de Pruebas

Para ejecutar la suite de pruebas unitarias y de integración:

```bash
# Ejecutar todos los tests
.\mvnw.cmd test

# Ejecutar una clase de test específica
.\mvnw.cmd test -Dtest=TurnControllerTest
```

---

*Proyecto desarrollado para ITSQMET - QMS.*
