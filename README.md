# Check-Out / Cash-Out

Backend de una aplicación de educación financiera. El usuario registra sus
ingresos y gastos reales, se fija metas de ahorro, y aprende a invertir en un
simulador donde el riesgo es de fichas de juego y no de su dinero.

La idea que sostiene el diseño es la separación entre esas dos economías:

- **Soles**, que es dinero real del usuario: ingresos, gastos, saldo de ahorro y
  metas. Aquí el backend solo lleva la cuenta; nunca mueve dinero.
- **Fichas**, que es moneda del juego: se ganan cumpliendo metas y jugando
  minijuegos, y se gastan en la cartera de inversión simulada. Ninguna API puede
  acreditarlas directamente, y eso es deliberado (ver
  [Decisiones de diseño](#decisiones-de-diseño)).

Proyecto 1 del curso Desarrollo Basado en Plataformas (DBP), 2026-2, UTEC.

---

## Stack

| Pieza | Versión | Por qué |
|---|---|---|
| Java | 21 | LTS; `record`, pattern matching y text blocks se usan en el código |
| Spring Boot | 4.1.1 | versión estable, sin artefactos pre-release |
| Spring Security | 7 | `SecurityFilterChain` y `@EnableMethodSecurity` |
| jjwt | 0.13 | firma y verificación de los JWT |
| PostgreSQL | 16 | base de datos de desarrollo y producción |
| H2 | en memoria, modo PostgreSQL | base de los tests: la suite no necesita Docker |
| Hibernate / JPA | la del BOM de Boot | persistencia |
| ModelMapper | 3.2 | Entity ↔ DTO, en estrategia STRICT |
| SpringDoc OpenAPI | 2.x | documentación navegable de la API |
| Maven | wrapper incluido (`./mvnw`) | no hace falta instalar Maven |

---

## Cómo levantarlo

### Requisitos

Docker y Docker Compose. Nada más: el JDK y Maven viven dentro de las imágenes.

### 1. Configurar el entorno

```bash
cp .env.example .env
```

Abrir `.env` y rellenar lo que falta. Las dos que no tienen valor por defecto y
sin las cuales la aplicación **no arranca** son:

```bash
DB_PASSWORD=          # la que quieras; el contenedor de Postgres la usa igual
JWT_SECRET=           # mínimo 32 caracteres
```

Para generar el secreto:

```bash
openssl rand -base64 48
```

El arranque falla con un mensaje explícito si el secreto mide menos de 32
caracteres, porque HS256 necesita 256 bits. Es a propósito: un secreto con valor
por defecto termina usándose en producción sin que nadie lo note, y con él
cualquiera que haya leído el repositorio puede emitirse un token válido.

`.env` está en `.gitignore` y no se commitea nunca.

### 2. Levantar todo

```bash
docker compose up --build
```

Eso arranca PostgreSQL y la aplicación. El backend espera a que la base acepte
conexiones de verdad antes de arrancar, no solo a que el contenedor exista.

- API: `http://localhost:8080/api/v1`
- Swagger: `http://localhost:8080/swagger-ui.html`

El servicio `app` del compose activa el perfil `dev`, que regenera el esquema en
cada arranque y publica Swagger. Ninguna de las dos cosas es el comportamiento
por defecto, justamente para que un despliegue no las herede sin pedirlas.

### Alternativa: solo la base, y la aplicación desde el IDE

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Ojo con el puerto: el contenedor publica **5433** en la máquina, no 5432, para no
chocar con un PostgreSQL instalado localmente. Por eso `DB_PORT=5433` en `.env`.

### 3. Correr los tests

```bash
./mvnw test
```

No necesitan Docker ni base de datos: corren sobre H2 en modo PostgreSQL, con
Hibernate generando el esquema desde las entidades, así que un mapeo inválido
rompe el build en vez de aparecer en producción.

---

## Perfiles y variables

El archivo base es seguro por omisión, y hay que pedir explícitamente lo
contrario. Antes era al revés, y olvidarse era silencioso.

| Variable | Default | Qué hace |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | — | conexión a PostgreSQL; obligatorias |
| `JWT_SECRET` | — | firma de los tokens, mínimo 32 caracteres; obligatoria |
| `JWT_EXPIRATION_ACCESS` | — | duración del access token, formato ISO-8601 (`PT15M`) |
| `JWT_EXPIRATION_REFRESH` | — | duración del refresh token (`P30D`) |
| `DDL_AUTO` | `validate` | qué hace Hibernate con el esquema al arrancar |
| `SHOW_SQL` | `false` | imprime cada consulta en el log |
| `SWAGGER_ENABLED` | `false` | publica `/swagger-ui.html` y `/v3/api-docs` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*` | orígenes del front, separados por coma |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | vacío | credenciales SMTP; sin ellas el envío falla al usarse |

El perfil `dev` (`SPRING_PROFILES_ACTIVE=dev`) pone `DDL_AUTO=create-drop`,
`SHOW_SQL=true` y `SWAGGER_ENABLED=true`.

**En producción:** dejar `DDL_AUTO` en `validate`, `SHOW_SQL` y
`SWAGGER_ENABLED` en `false`, y fijar `CORS_ALLOWED_ORIGINS` al dominio real del
front. No usar `*`: con credenciales el navegador lo rechaza, y abre la API a
cualquier página.

Un test del build (`ConfigurationContractTest`) falla si una variable sin valor
por defecto no está documentada en `.env.example`, si la plantilla nombra
variables que ya nadie lee, o si alguna credencial queda escrita en
`application.properties`.

---

## Estructura

Los paquetes están organizados por dominio, no por capa técnica: todo lo de una
meta de ahorro —entidad, DTOs, repositorio, servicio, controller— vive junto.
Cambiar una regla de negocio se hace en una carpeta y no en cinco.

```
com.checkout.backend
├── user/                    usuarios, autenticación, registro y login
├── savings/                 ahorro en soles
│   ├── income/              ingresos
│   ├── expense/             gastos
│   └── goal/                metas de ahorro
│       └── contribution/    aportes a una meta
├── token_wallet/            monedero de fichas
│   ├── tktransaction/       libro de movimientos de fichas
│   └── refresh_token/       tokens de refresco
├── investment_portfolio/    cartera simulada
│   ├── asset/               catálogo de activos, cotización e histórico
│   ├── position/            posiciones abiertas
│   └── trade_order/         órdenes de compra y venta
├── minigame/                catálogo de minijuegos
│   └── session/             partidas jugadas
├── projection/              proyecciones de interés simple y compuesto
├── email/                   envío de correo, asíncrono
├── notification/            listeners que reaccionan a eventos de dominio
├── security/                filtro JWT, cadena de seguridad, roles
├── exceptions/              excepciones propias y handler global
├── config/                  ModelMapper, executor async
└── web/                     versionado de la API
```

Dentro de cada dominio: `model/`, `dto/`, `repository/`, `service/`,
`controller/`, y `event/` donde hay eventos.

---

## La API

Todo cuelga de `/api/v1`, aplicado de forma centralizada en
`web/ApiVersioningConfig`. Ningún controller repite el prefijo a mano.

### Autenticación — público

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/auth/register` | registra y devuelve los tokens · `201` |
| `POST` | `/auth/login` | devuelve los tokens · `200` |
| `POST` | `/auth/refresh` | rota el refresh token · `200` |
| `POST` | `/auth/logout` | revoca el refresh token · `204` |

`logout` es público a propósito: con un access token ya expirado hay que poder
cerrar sesión igualmente.

### Usuario

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/users/me` | el propio perfil |
| `PATCH` | `/users/me` | cambia nombre y fecha de nacimiento |
| `PUT` | `/users/me/password` | cambia la contraseña; revoca las demás sesiones |
| `DELETE` | `/users/me` | desactiva la cuenta (borrado lógico) · `204` |
| `GET` | `/users` | lista todos · **ADMIN** |

### Ahorro en soles

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/savings` | saldo, comprometido y disponible |
| `GET` `POST` `PUT` `DELETE` | `/incomes`, `/incomes/{id}` | ingresos; el listado acepta `from`, `to`, `page`, `size` |
| `GET` `POST` `PUT` `DELETE` | `/expenses`, `/expenses/{id}` | gastos; mismos filtros más `category` |
| `GET` `POST` `PUT` `DELETE` | `/savings-goals`, `/savings-goals/{id}` | metas |
| `GET` `POST` | `/savings-goals/{goalId}/contributions` | aportes a una meta |

### Fichas

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/token-wallet` | saldo de fichas |
| `GET` | `/token-wallet/transactions` | libro de movimientos, paginado |

**No hay escritura.** Ver [Decisiones de diseño](#decisiones-de-diseño).

### Inversión simulada

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/portfolio` | cartera con valor de mercado y PnL |
| `GET` | `/portfolio/positions` | posiciones abiertas |
| `GET` | `/assets` | catálogo activo |
| `GET` | `/assets?includeInactive=true` | incluye los desactivados · **ADMIN** |
| `POST` `DELETE` | `/assets`, `/assets/{id}` | alta y baja lógica · **ADMIN** |
| `GET` | `/assets/{id}/quote` | cotización vigente |
| `PUT` | `/assets/{id}/quote` | fija el precio · **ADMIN** |
| `GET` | `/assets/{id}/price-history` | histórico de cierres, con `from` y `to` |
| `GET` `POST` | `/orders` | historial y colocación de órdenes |
| `POST` | `/orders/{id}/cancel` | cancela una orden pendiente |

### Minijuegos

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/minigames` | catálogo publicado |
| `GET` | `/minigames?includeUnpublished=true` | incluye borradores y archivados · **ADMIN** |
| `POST` `PUT` `DELETE` | `/minigames`, `/minigames/{id}` | gestión del catálogo · **ADMIN** |
| `GET` `POST` | `/minigame-sessions` | partidas jugadas |

### Proyecciones

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` `POST` `DELETE` | `/projections`, `/projections/{id}` | interés simple vs compuesto |

### Correo

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/emails` | envía al usuario autenticado · `202` |
| `POST` | `/emails/with-attachment` | igual, con un archivo subido por multipart · `202` |

El destinatario **no** viaja en el cuerpo: sale del token.

### Errores

Todos los endpoints devuelven el mismo formato:

```json
{
  "timestamp": "2026-09-25T14:03:31.758",
  "status": 400,
  "error": "Bad Request",
  "message": "El aporte supera tu saldo disponible de 250.00.",
  "path": "/api/v1/savings-goals/3/contributions",
  "fieldErrors": [
    { "field": "amount", "message": "El monto debe ser mayor que cero." }
  ]
}
```

`fieldErrors` solo aparece cuando hay errores de validación por campo.

| Código | Cuándo |
|---|---|
| `400` | validación, o una regla de negocio que depende del estado guardado |
| `401` | sin token, token inválido o expirado |
| `403` | autenticado pero sin el rol necesario |
| `404` | no existe, **o es de otro usuario** |
| `409` | duplicado, o conflicto de concurrencia |
| `413` | adjunto demasiado grande |
| `422` | no se usa |
| `500` | error interno; el mensaje nunca revela el detalle |

Pedir un recurso de otro usuario devuelve **404 y no 403**: un 403 confirmaría
que ese ID existe y es de alguien.

---

## Decisiones de diseño

### El monedero de fichas no tiene API de escritura

No existe ningún endpoint que acredite o descuente fichas, y en el proyecto no
hay ningún `TokenWalletRequest`. Las fichas se mueven solo como consecuencia de
un hecho —una meta cumplida, una partida jugada, una orden ejecutada— y siempre
desde el servicio que gobierna ese hecho.

Si un usuario pudiera llamar a un endpoint para sumarse fichas, el saldo dejaría
de significar nada, y con él los minijuegos y la cartera.

Cada movimiento queda en `token_transactions` con el saldo resultante, así que el
libro se puede auditar en cualquier punto y una desviación se detecta leyendo una
sola fila.

### El sobre virtual

Un aporte a una meta no mueve dinero: **compromete** dinero que ya estaba en el
saldo.

```
disponible = saldo actual − comprometido en metas en progreso
```

Un gasto se valida contra el **disponible**, no contra el saldo total, así que el
dinero apartado para una meta no se puede gastar sin sacarlo de la meta primero.
Esto evita la doble contabilización, que es el error habitual cuando "ahorro" y
"saldo" son dos números que se actualizan por separado.

### Idempotencia

Dos sitios donde un reintento costaría dinero:

- **Órdenes.** El cliente manda un `clientOrderId`, con `UNIQUE` en la tabla. Si
  reintenta porque no le llegó la respuesta, la segunda llamada devuelve la orden
  que ya se ejecutó en vez de comprar otra vez.
- **Movimientos de fichas.** El par `(motivo, referencia)` es `UNIQUE`. La misma
  meta no puede pagar recompensa dos veces ni con un reintento.

### Concurrencia

`@Version` en `Savings`, `TokenWallet`, `SavingsGoal` e `InvestmentPortfolio`.
Dos operaciones simultáneas sobre el mismo saldo leen la misma versión; la
segunda en escribir falla y la operación no se aplica, en vez de pisar a la
primera.

El cliente recibe un **409**, no un 500: la integridad está a salvo y hay algo
que hacer, que es reintentar.

Las anotaciones de Bean Validation **no** protegen contra esto. Un
`@DecimalMin("0")` no impide que dos peticiones lean 100 y gasten 80 cada una.
Lo que lo impide es la versión. Está verificado con hilos reales en
`WalletConcurrencyTest`.

### Una orden rechazada se guarda

Fichas insuficientes o posición insuficiente no son errores del cliente: son el
resultado de la orden. Se guarda como `REJECTED` con su motivo y se devuelve
`201`. El usuario ve en su historial que lo intentó y por qué no salió.

Eso obliga a decidir el rechazo **preguntando antes de escribir**, no lanzando una
excepción y atrapándola: una excepción que escapa de un método `@Transactional`
marca la transacción como rollback-only aunque quien llama la atrape, y el commit
posterior falla. Está explicado en el Javadoc de `TradeOrderService.place`.

### Eventos de dominio

Cumplir una meta y ejecutar una orden publican un evento. Los listeners son
`@TransactionalEventListener(phase = AFTER_COMMIT)`, así que el correo sale solo
si el commit ocurrió de verdad.

Un `@EventListener` normal correría dentro de la transacción, y si esta acabara en
rollback el usuario tendría en su buzón la felicitación por una meta que la base
nunca registró. Un correo no se deshace con la transacción.

### La puntuación de un minijuego no se puede verificar

El juego corre en el cliente, y el cliente es código que el usuario controla, así
que nada impide enviar la puntuación que se quiera.

Lo que sí se hace es acotar el daño: la recompensa nunca supera el
`maxTokenReward` que el catálogo declara, y el catálogo solo lo edita un ADMIN.
La peor manipulación posible equivale a jugar perfecto, no a imprimir fichas. La
partida además se cobra por adelantado, así que repetir tiene un coste real.

La solución completa es resolver la partida en el servidor. Es un módulo entero y
no entra en este alcance; mientras tanto, este tope es lo que sostiene la
economía.

### Cotización vigente e histórico son dos tablas

`asset_quotes` tiene `UNIQUE(asset_id)`: guarda el precio actual y se sobrescribe.
El histórico para el gráfico vive en `asset_price_history` con
`UNIQUE(asset_id, date)`, y las dos se escriben en la misma transacción. Si el
cierre no se guardara en ese momento, el precio anterior se perdería.

Una cotización de más de siete días no sirve para operar: se rechaza la orden en
vez de ejecutarla contra un precio que ya no existe.

---

## Seguridad

- **JWT** con `sub` (email), `uid` (id de usuario), `roles`, `iat` y `exp`. El
  secreto viene del entorno y se valida al arrancar.
- **Refresh tokens** guardados como hash SHA-256, nunca en claro. Rotan en cada
  uso, y si se detecta la reutilización de uno ya revocado se revoca la familia
  completa del usuario: es la defensa contra el replay de un token robado.
- **BCrypt** para las contraseñas, que deben tener 8 caracteres como mínimo con
  minúscula, mayúscula, dígito y símbolo.
- **Roles** con `@PreAuthorize` sobre las escrituras de catálogo y el listado de
  usuarios.
- **Ownership** resuelto en la consulta: `findByIdAndUserId(...)`, no
  `findById(...)` más una comprobación después. El filtro está en el `WHERE`, así
  que no se puede olvidar.
- **CORS** por variable de entorno, sin comodín.
- **Sin estado**: no hay sesión de servidor.

---

## Base de datos

19 tablas. Las relaciones 1:1 (`savings`, `token_wallets`,
`investment_portfolios`) tienen `UNIQUE(user_id)`, así que la unicidad la
garantiza el esquema y no el código. Las claves foráneas están nombradas, y hay
índices en las columnas por las que se filtra de verdad.

Desactivar un usuario es un borrado lógico (`users.status`): su historial
financiero no se destruye.

---

## Desarrollo

```bash
./mvnw test                          # toda la suite
./mvnw test -Dtest=PortfolioPnlTest  # una clase
./mvnw package                       # el jar
```

El CI corre compilación y tests en cada pull request y en cada push a `main`.

Los tests de flujos financieros de escritura **no** llevan `@Transactional` de
clase, y es deliberado: con esa anotación el método de test es el dueño de la
transacción externa y los servicios se le unen como participantes, de modo que el
commit que la aplicación hace en producción nunca ocurre en el test. Eso ya
escondió un fallo real. La limpieza se hace en `@AfterEach` con
`support/DatabaseCleaner`.
