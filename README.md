# Taller 1 NoSQL – Biblioteca con préstamos, reseñas y favoritos

**Estudiante:** Juan José Ospina Sánchez · **Curso:** Bases de datos NoSQL

Ampliación de la aplicación de biblioteca vista en clase (Spring Boot 4 + MongoDB + Redis). Se agregan **usuarios**, **favoritos**, **préstamos** y **reseñas**, usando **denormalización** para que las consultas más comunes salgan con una sola lectura, y **Redis** como caché de los endpoints.

## Cómo correrlo (ruta rápida, solo con Docker)

No hace falta instalar Java ni Gradle: la aplicación se compila y corre dentro de Docker.

1. Levantar todo (MongoDB, Redis y la aplicación):
   ```bash
   docker compose up -d --build
   ```
   Si un puerto ya está ocupado en el computador, se puede cambiar: `REDIS_PORT=6380 docker compose up -d --build` (también existen `MONGO_PORT` y `APP_PORT`).
2. Abrir Swagger para probar todos los endpoints: <http://localhost:8080/swagger-ui.html>
3. Probar todo automáticamente (necesita `curl` y `jq`):
   ```bash
   ./scripts/prueba-endpoints.sh
   ```
   Recorre todos los endpoints y reglas de negocio (préstamos, una sola copia con peticiones al mismo tiempo, vencidos, reseñas, favoritos, consistencia, Redis e índices) y muestra ✅ o ❌ en cada paso.
4. Apagar: `docker compose down` (con `-v` también se borran los datos de MongoDB).

### Con Java instalado (opcional)

Con JDK 17 o superior (si no hay JDK 17, Gradle lo descarga solo):

```bash
docker compose up -d mongodb redis
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

### Usar el MongoDB Atlas y el Redis (Upstash) de la clase

Las credenciales **no** están en el código (el repositorio es público). Se pasan con variables de entorno antes de arrancar:

```bash
export MONGODB_URI="<uri de Atlas que dio el profesor>"
export MONGODB_DATABASE="claseNoSQL"
export REDIS_URL="<url rediss:// de Upstash que dio el profesor>"
./gradlew bootRun
```

En Windows PowerShell: `$env:MONGODB_URI="..."` (igual para las otras dos).

### Pruebas

```bash
./gradlew test
```

Sin Java instalado, las mismas pruebas se pueden correr dentro de un contenedor (el proyecto se monta en solo lectura):

```bash
docker run --rm -v "$PWD":/src:ro eclipse-temurin:17-jdk \
  sh -c 'cp -r /src /workspace && cd /workspace && ./gradlew --no-daemon test'
```

Las pruebas de `LoanServiceTest`, `ReviewServiceTest` y `DenormalizationServiceTest` usan mocks y **no necesitan** MongoDB ni Redis. La prueba `contextLoads` del profesor levanta la aplicación completa: si MongoDB no está corriendo, se demora unos 30 segundos esperando la conexión, pero igual pasa.

## Modelo de datos

| Colección | Qué guarda | Datos copiados (denormalizados) | ¿Para qué? |
|-----------|------------|---------------------------------|------------|
| `books` | Libro (ya existía) | `loaned`, `currentLoan` (quién lo tiene y hasta cuándo), `totalLoans`, `averageRating`, `totalReviews` | Saber si está prestado, libros populares y mejor calificados sin recorrer otras colecciones |
| `users` | Datos personales | `activity`: préstamos activos, total de préstamos, total de reseñas, total de favoritos y los **últimos 5 préstamos** | Ver el perfil y la actividad del usuario con una sola consulta |
| `loans` | Cada préstamo con fechas (préstamo, entrega esperada, devolución real) y estado | Título, autor e ISBN del libro; nombre y email del usuario | Listar préstamos y sacar estadísticas sin `$lookup` |
| `reviews` | Calificación (1 a 5) y comentario | Título del libro y nombre del usuario | Mostrar reseñas por libro o por usuario sin `$lookup` |
| `favorites` | Una lista por usuario | Título y autor de cada libro | Leer toda la lista de favoritos con una sola consulta |

## Cómo se mantiene la consistencia

Toda la lógica de copias está en `DenormalizationService`.

| Cuando pasa esto | Se actualiza |
|------------------|--------------|
| Se presta un libro | El libro (`loaned`, `currentLoan`, `totalLoans`) y el resumen del usuario |
| Se devuelve un libro | El préstamo, el libro (queda disponible) y el resumen del usuario |
| Se crea, edita o borra una reseña | Se recalcula el promedio del libro con una agregación y se ajusta el contador del usuario |
| Cambia el título o el autor de un libro | Préstamos, reseñas, favoritos, últimos préstamos de los usuarios, autor, editorial y categorías |
| Cambia el nombre o el email de un usuario | Préstamos, reseñas, favoritos y el préstamo actual del libro |
| Se borra un libro o un usuario | Se borran sus reseñas y favoritos. El historial de préstamos se deja a propósito (ya tiene su copia de los datos) |

**Una sola copia física por libro:** se protege de tres formas: (1) se revisa en el código, (2) el libro se marca como prestado con una operación atómica que solo funciona si no estaba prestado, y (3) un índice único parcial en `loans` no deja guardar dos préstamos `ACTIVE` del mismo libro.

## Reglas de negocio

- Un libro solo puede tener un préstamo activo.
- Un usuario puede tener máximo **3** préstamos activos y no puede pedir más si tiene préstamos vencidos.
- El préstamo dura entre 1 y 30 días (14 si no se indica).
- Un préstamo no se puede devolver dos veces. Si se devuelve después de la fecha, queda marcado `returnedLate`.
- El email del usuario es único (se guarda en minúscula).
- No se puede borrar un usuario con préstamos activos ni un libro que está prestado.
- La calificación va de 1 a 5, cada usuario reseña un libro una sola vez y **solo puede reseñar quien ya pidió prestado el libro**.
- En favoritos no se repiten libros y hay máximo 50 por usuario.

Respuestas de error: `400` datos inválidos, `404` no existe, `409` se rompió una regla de negocio.

## Redis (caché)

- Todas las consultas `GET` se guardan en Redis por **60 segundos** (las estadísticas por 30).
- Cada escritura (crear, editar, borrar, prestar, devolver…) limpia las cachés afectadas, así no se muestran datos viejos.
- La llave incluye el nombre del método, por ejemplo `loans::getLoansByUser(66f1...)`.
- Si Redis se cae, la aplicación sigue funcionando: solo se escribe una advertencia en el log y se consulta MongoDB.

## Endpoints

**Libros** `/api/books`

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/books` | Crear libro |
| GET | `/api/books` | Todos los libros |
| GET | `/api/books/{id}` | Libro **con sus reseñas** |
| GET | `/api/books/search/author/{author}` · `/search/editorial/{editorial}` · `/search/category/{category}` | Búsquedas del taller anterior |
| GET | `/api/books/search/available` | Libros disponibles |
| GET | `/api/books/search/loaned` | Libros prestados |
| GET | `/api/books/popular?limit=5` | Más prestados |
| GET | `/api/books/top-rated?limit=5&minReviews=1` | Mejor calificados |
| PUT | `/api/books/{id}` | Editar libro (propaga el título y el autor) |
| DELETE | `/api/books/{id}` | Borrar libro |

**Usuarios** `/api/users`

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/users` | Crear usuario |
| GET | `/api/users` | Todos los usuarios |
| GET | `/api/users/{id}` | Un usuario |
| GET | `/api/users/search/email/{email}` | Buscar por email |
| GET | `/api/users/search/name/{name}` | Buscar por nombre (contiene) |
| GET | `/api/users/with-active-loans` | Usuarios con préstamos activos |
| GET | `/api/users/{id}/activity` | Resumen de actividad y últimos préstamos |
| PUT | `/api/users/{id}` | Editar usuario (propaga el nombre y el email) |
| DELETE | `/api/users/{id}` | Borrar usuario |

**Favoritos** `/api/users/{userId}/favorites`

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/users/{userId}/favorites` | Lista de favoritos |
| POST | `/api/users/{userId}/favorites/{bookId}` | Agregar libro |
| DELETE | `/api/users/{userId}/favorites/{bookId}` | Quitar libro |

**Préstamos** `/api/loans`

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/loans` | Prestar un libro |
| PUT | `/api/loans/{id}/return` | Devolver un libro |
| GET | `/api/loans` · `/api/loans/{id}` | Todos / uno |
| GET | `/api/loans/user/{userId}?status=ACTIVE` | Préstamos de un usuario (el `status` es opcional: `ACTIVE` o `RETURNED`) |
| GET | `/api/loans/user/{userId}/late` | Devoluciones tarde de un usuario |
| GET | `/api/loans/status/{status}` | Préstamos por estado |
| GET | `/api/loans/book/{bookId}` | Historial de un libro |
| GET | `/api/loans/overdue` | Préstamos vencidos |
| GET | `/api/loans/stats` | Estadísticas (totales, vencidos, libros más prestados, usuarios que más piden) |

**Reseñas** `/api/reviews`

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/reviews` | Crear reseña |
| GET | `/api/reviews` · `/api/reviews/{id}` | Todas / una |
| GET | `/api/reviews/book/{bookId}?minRating=4` | Reseñas de un libro (`minRating` opcional) |
| GET | `/api/reviews/user/{userId}` | Reseñas de un usuario |
| PUT | `/api/reviews/{id}` | Editar calificación y comentario |
| DELETE | `/api/reviews/{id}` | Borrar reseña |

## Prueba completa con curl

Reemplazar `<BOOK_ID>`, `<USER_ID>` y `<LOAN_ID>` por los `id` que van devolviendo las respuestas.

```bash
# 1. Crear un libro
curl -X POST localhost:8080/api/books -H "Content-Type: application/json" -d '{
  "uniqueIdentifier": "ISBN-0001",
  "title": "Cien años de soledad",
  "synopsis": "La historia de la familia Buendía",
  "publicationYear": 1967,
  "author": { "fullName": "Gabriel García Márquez", "nationality": "Colombiana" },
  "editorial": { "name": "Sudamericana", "country": "Argentina" },
  "categories": [ { "name": "Novela" } ]
}'

# 2. Crear un usuario
curl -X POST localhost:8080/api/users -H "Content-Type: application/json" -d '{
  "fullName": "Ana Pérez", "email": "ana@correo.com", "phone": "3001234567"
}'

# 3. Prestar el libro (pedirlo otra vez debe dar 409 porque solo hay una copia)
curl -X POST localhost:8080/api/loans -H "Content-Type: application/json" -d '{
  "userId": "<USER_ID>", "bookId": "<BOOK_ID>", "loanDays": 7
}'

# 4. Ver el resumen del usuario y los libros prestados
curl localhost:8080/api/users/<USER_ID>/activity
curl localhost:8080/api/books/search/loaned

# 5. Devolver el libro
curl -X PUT localhost:8080/api/loans/<LOAN_ID>/return

# 6. Reseñar el libro (solo se puede porque ya lo pidió prestado)
curl -X POST localhost:8080/api/reviews -H "Content-Type: application/json" -d '{
  "userId": "<USER_ID>", "bookId": "<BOOK_ID>", "rating": 5, "comment": "Excelente"
}'

# 7. Consultar el libro: trae el promedio actualizado y sus reseñas
curl localhost:8080/api/books/<BOOK_ID>

# 8. Favoritos y estadísticas
curl -X POST localhost:8080/api/users/<USER_ID>/favorites/<BOOK_ID>
curl localhost:8080/api/users/<USER_ID>/favorites
curl localhost:8080/api/loans/stats
```

## Notas

- `available` ya no se envía al crear o editar un libro: lo maneja la lógica de préstamos y siempre es lo contrario de `loaned`.
- Los libros creados antes de este taller no tienen los campos nuevos. Las consultas los tratan como disponibles y los campos se llenan solos con el primer préstamo o la primera reseña.
- Para generar el `.zip` de entrega en el Escritorio (`nosqltallerJuanJoseOspinaSanchez.zip`, sin archivos de compilación):
  ```bash
  ./scripts/generar-zip.sh
  ```
