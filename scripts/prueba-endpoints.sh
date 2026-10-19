#!/usr/bin/env bash
# Prueba de punta a punta de todos los endpoints y reglas de negocio.
# Uso:   ./scripts/prueba-endpoints.sh [URL_BASE]      (por defecto http://localhost:8080)
# Necesita: curl, jq y docker (para revisar Redis y MongoDB y para simular un préstamo vencido).
# Cada ejecución usa datos nuevos (con un número distinto), así se puede correr varias veces.
set -u

BASE="${1:-http://localhost:8080}"
MONGO_CONTAINER="${MONGO_CONTAINER:-library-mongodb}"
MONGO_DB="${MONGO_DB:-library}"
REDIS_CONTAINER="${REDIS_CONTAINER:-library-redis}"
RUN="$(date +%s)"
PASS=0
FAIL=0
STATUS=""
BODY=""

# ---------- Funciones de ayuda ----------

# Hace una petición HTTP y guarda el código en STATUS y la respuesta en BODY
req() {
  local method="$1" path="$2" data="${3:-}" out
  if [ -n "$data" ]; then
    out=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE$path" -H 'Content-Type: application/json' -d "$data")
  else
    out=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE$path")
  fi
  STATUS="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
}

ok()      { PASS=$((PASS + 1)); echo "  ✅ $1"; }
fail()    { FAIL=$((FAIL + 1)); echo "  ❌ $1"; }
section() { echo; echo "== $1"; }
json()    { printf '%s' "$BODY" | jq -r "$1"; }

# Revisa el código HTTP de la última petición
expect_status() {
  if [ "$STATUS" = "$1" ]; then ok "$2 ($STATUS)"; else fail "$2: se esperaba $1 y llegó $STATUS -> $BODY"; fi
}

# Revisa que una expresión de jq sobre la última respuesta dé true
expect_true() {
  local actual
  actual=$(printf '%s' "$BODY" | jq -r "$1" 2>/dev/null)
  if [ "$actual" = "true" ]; then ok "$2"; else fail "$2: la condición [$1] dio '$actual' -> $(printf '%s' "$BODY" | cut -c1-300)"; fi
}

# Revisa que un texto contenga otro
expect_contains() {
  case "$1" in
    *"$2"*) ok "$3" ;;
    *) fail "$3: no se encontró '$2' en '$1'" ;;
  esac
}

mongo_eval() { docker exec "$MONGO_CONTAINER" mongosh "$MONGO_DB" --quiet --eval "$1"; }
redis_cli()  { docker exec "$REDIS_CONTAINER" redis-cli "$@"; }

create_book() { # $1 = sufijo del ISBN, $2 = título
  req POST /api/books "{\"uniqueIdentifier\":\"ISBN-$RUN-$1\",\"title\":\"$2\",\"synopsis\":\"Libro de prueba\",\"publicationYear\":2001,\"author\":{\"fullName\":\"Autor $RUN\",\"nationality\":\"Colombiana\"},\"editorial\":{\"name\":\"Editorial $RUN\",\"country\":\"Colombia\"},\"categories\":[{\"name\":\"Categoria $RUN\"}]}"
}
create_user() { # $1 = nombre, $2 = email
  req POST /api/users "{\"fullName\":\"$1\",\"email\":\"$2\",\"phone\":\"3001234567\"}"
}
create_loan() { # $1 = usuario, $2 = libro, $3 = días (opcional)
  if [ -n "${3:-}" ]; then
    req POST /api/loans "{\"userId\":\"$1\",\"bookId\":\"$2\",\"loanDays\":$3}"
  else
    req POST /api/loans "{\"userId\":\"$1\",\"bookId\":\"$2\"}"
  fi
}
create_review() { # $1 = usuario, $2 = libro, $3 = calificación
  req POST /api/reviews "{\"userId\":\"$1\",\"bookId\":\"$2\",\"rating\":$3,\"comment\":\"Comentario de prueba\"}"
}

# ---------- Esperar a que la app responda ----------

echo "Probando $BASE (ejecución $RUN)"
for _ in $(seq 1 60); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/books")" = "200" ] && break
  sleep 2
done

# ---------- 1. Libros ----------

section "1. Libros"
create_book A "Libro A $RUN"
expect_status 201 "Crear libro A"
BOOK_A=$(json .id)
expect_true '.loaned == false and .available == true' "Un libro nuevo empieza disponible"
expect_true '.averageRating == 0 and .totalReviews == 0 and .totalLoans == 0' "Un libro nuevo empieza sin reseñas ni préstamos"

req POST /api/books "{\"uniqueIdentifier\":\"ISBN-$RUN-OLD\",\"title\":\"Libro JSON viejo $RUN\",\"available\":false,\"author\":{\"fullName\":\"Autor $RUN\"},\"editorial\":{\"name\":\"Editorial $RUN\"},\"categories\":[]}"
expect_status 201 "Crear libro mandando el campo viejo 'available' (se ignora)"
expect_true '.available == true' "El campo 'available' lo decide la lógica de préstamos"

req GET "/api/books/$BOOK_A"
expect_status 200 "Consultar libro A"
expect_true ".book.id == \"$BOOK_A\" and (.reviews | length) == 0" "El detalle trae el libro y su lista de reseñas"

req GET /api/books/000000000000000000000000
expect_status 404 "Libro que no existe"

# ---------- 2. Usuarios ----------

section "2. Usuarios"
create_user "Ana $RUN" "ana.$RUN@correo.com"
expect_status 201 "Crear usuario 1"
USER_1=$(json .id)
expect_true '.activity.activeLoans == 0 and (.activity.recentLoans | length) == 0' "El usuario empieza con el resumen de actividad vacío"

create_user "Otra Ana" "ANA.$RUN@CORREO.COM"
expect_status 409 "Email repetido (aunque cambien las mayúsculas)"

create_user "" "no-es-un-email"
expect_status 400 "Datos inválidos"
expect_true '(.details | length) >= 2' "La respuesta de error lista los campos inválidos"

create_user "Luis $RUN" "luis.$RUN@correo.com"
expect_status 201 "Crear usuario 2"
USER_2=$(json .id)

req GET "/api/users/$USER_1"
expect_status 200 "Consultar usuario por id"
req GET "/api/users/search/email/ANA.$RUN@correo.com"
expect_true ".id == \"$USER_1\"" "Buscar por email sin importar mayúsculas"
req GET "/api/users/search/name/$RUN"
expect_true '(. | length) >= 2' "Buscar por parte del nombre"

# ---------- 3. Préstamos ----------

section "3. Préstamos"
create_loan "$USER_1" "$BOOK_A" 7
expect_status 201 "Usuario 1 pide prestado el libro A"
LOAN_A1=$(json .id)
expect_true ".status == \"ACTIVE\" and .bookTitle == \"Libro A $RUN\" and .userFullName == \"Ana $RUN\"" "El préstamo guarda copia del título y del nombre"
expect_true '((.dueDate | strptime("%Y-%m-%d") | mktime) - (.loanDate | strptime("%Y-%m-%d") | mktime)) == 604800' "La fecha de entrega es 7 días después"

create_loan "$USER_2" "$BOOK_A"
expect_status 409 "Nadie más puede pedir el libro A (una sola copia)"

req GET "/api/books/$BOOK_A"
expect_true '.book.loaned == true and .book.available == false and .book.totalLoans == 1' "El libro A queda prestado"
expect_true ".book.currentLoan.userFullName == \"Ana $RUN\" and .book.currentLoan.loanId == \"$LOAN_A1\"" "El libro guarda quién lo tiene (currentLoan)"

req GET /api/books/search/loaned
expect_true "any(.[]; .id == \"$BOOK_A\")" "El libro A sale en prestados"
req GET /api/books/search/available
expect_true "all(.[]; .id != \"$BOOK_A\")" "El libro A no sale en disponibles"

req GET "/api/users/$USER_1/activity"
expect_true ".activeLoans == 1 and .totalLoans == 1 and .recentLoans[0].loanId == \"$LOAN_A1\"" "El resumen del usuario se actualiza"

create_loan "$USER_1" "$BOOK_A" 31
expect_status 400 "Préstamo de más de 30 días"
create_loan "$USER_1" 000000000000000000000000
expect_status 404 "Préstamo de un libro que no existe"
req POST /api/loans "{\"bookId\":\"$BOOK_A\"}"
expect_status 400 "Préstamo sin usuario"

section "3.1 Una sola copia: 5 usuarios piden el mismo libro al mismo tiempo"
create_book R "Libro carrera $RUN"
BOOK_R=$(json .id)
RACE_USERS=""
for i in 1 2 3 4 5; do
  create_user "Corredor $i $RUN" "corredor$i.$RUN@correo.com"
  RACE_USERS="$RACE_USERS $(json .id)"
done
RACE_FILE=$(mktemp)
for u in $RACE_USERS; do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/loans" -H 'Content-Type: application/json' \
    -d "{\"userId\":\"$u\",\"bookId\":\"$BOOK_R\"}" >> "$RACE_FILE" &
done
wait
CREATED=$(grep -c '^201$' "$RACE_FILE")
CONFLICTS=$(grep -c '^409$' "$RACE_FILE")
rm -f "$RACE_FILE"
if [ "$CREATED" = "1" ] && [ "$CONFLICTS" = "4" ]; then ok "Solo 1 préstamo creado y 4 rechazados"; else fail "Se crearon $CREATED préstamos y se rechazaron $CONFLICTS"; fi
ACTIVE_R=$(mongo_eval "db.loans.countDocuments({bookId: '$BOOK_R', status: 'ACTIVE'})")
if [ "$ACTIVE_R" = "1" ]; then ok "En MongoDB hay un solo préstamo activo de ese libro"; else fail "En MongoDB hay $ACTIVE_R préstamos activos"; fi

section "3.2 Máximo 3 préstamos activos por usuario"
create_book B "Libro B $RUN"; BOOK_B=$(json .id)
create_book C "Libro C $RUN"; BOOK_C=$(json .id)
create_book D "Libro D $RUN"; BOOK_D=$(json .id)
create_loan "$USER_1" "$BOOK_B"; LOAN_B=$(json .id)
expect_status 201 "Usuario 1 pide el libro B"
create_loan "$USER_1" "$BOOK_C"; LOAN_C=$(json .id)
expect_status 201 "Usuario 1 pide el libro C"
create_loan "$USER_1" "$BOOK_D"
expect_status 409 "El cuarto préstamo activo se rechaza"
req GET "/api/loans/user/$USER_1?status=ACTIVE"
expect_true '(. | length) == 3' "Préstamos activos del usuario 1"
req GET /api/users/with-active-loans
expect_true "any(.[]; .id == \"$USER_1\")" "El usuario 1 sale en 'con préstamos activos'"

section "3.3 Devoluciones"
req PUT "/api/loans/$LOAN_A1/return"
expect_status 200 "Devolver el libro A"
expect_true '.status == "RETURNED" and .returnedLate == false and .returnDate != null' "El préstamo queda devuelto a tiempo"
req PUT "/api/loans/$LOAN_A1/return"
expect_status 409 "No se puede devolver dos veces"
req GET "/api/books/$BOOK_A"
expect_true '.book.loaned == false and .book.available == true and .book.currentLoan == null' "El libro A vuelve a estar disponible"
req GET "/api/users/$USER_1/activity"
expect_true ".activeLoans == 2 and ([.recentLoans[] | select(.loanId == \"$LOAN_A1\") | .status][0] == \"RETURNED\")" "El resumen del usuario marca la devolución"
req GET /api/loans/status/RETURNED
expect_true "any(.[]; .id == \"$LOAN_A1\")" "Consultar préstamos por estado"
req GET /api/loans/status/FOO
expect_status 400 "Estado que no existe"
req GET "/api/loans/book/$BOOK_A"
expect_true '(. | length) == 1' "Historial de préstamos del libro A"

section "3.4 Préstamos vencidos"
create_user "Pedro $RUN" "pedro.$RUN@correo.com"; USER_3=$(json .id)
create_loan "$USER_3" "$BOOK_D"; LOAN_D=$(json .id)
expect_status 201 "Usuario 3 pide el libro D"
# Se cambia la fecha de entrega directamente en MongoDB para simular que ya pasó
MODIFIED=$(mongo_eval "db.loans.updateOne({\$or: [{_id: ObjectId('$LOAN_D')}, {_id: '$LOAN_D'}]}, {\$set: {dueDate: new Date(Date.now() - 5 * 86400000)}}).modifiedCount")
if [ "$MODIFIED" = "1" ]; then ok "Fecha de entrega movida 5 días atrás"; else fail "No se pudo cambiar la fecha ($MODIFIED)"; fi
req GET /api/loans/overdue
expect_true "any(.[]; .id == \"$LOAN_D\")" "El préstamo sale en vencidos"
create_book E "Libro E $RUN"; BOOK_E=$(json .id)
create_loan "$USER_3" "$BOOK_E"
expect_status 409 "Un usuario con préstamos vencidos no puede pedir más"
req PUT "/api/loans/$LOAN_D/return"
expect_true '.returnedLate == true' "La devolución queda marcada como tardía"
req GET "/api/loans/user/$USER_3/late"
expect_true "any(.[]; .id == \"$LOAN_D\")" "Devoluciones tardías del usuario"

# ---------- 4. Reseñas ----------

section "4. Reseñas"
create_review "$USER_2" "$BOOK_A" 5
expect_status 409 "No puede reseñar quien nunca pidió prestado el libro"
create_review "$USER_1" "$BOOK_A" 5
expect_status 201 "Usuario 1 reseña el libro A"
REVIEW_1=$(json .id)
expect_true ".bookTitle == \"Libro A $RUN\" and .userFullName == \"Ana $RUN\"" "La reseña guarda copia del título y del nombre"
create_review "$USER_1" "$BOOK_A" 4
expect_status 409 "Un usuario no puede reseñar dos veces el mismo libro"
create_review "$USER_1" "$BOOK_A" 6
expect_status 400 "Calificación fuera de 1 a 5"

create_loan "$USER_2" "$BOOK_A"; LOAN_A2=$(json .id)
req PUT "/api/loans/$LOAN_A2/return"
create_review "$USER_2" "$BOOK_A" 4
expect_status 201 "Usuario 2 reseña el libro A después de pedirlo"
REVIEW_2=$(json .id)

req GET "/api/books/$BOOK_A"
expect_true '.book.averageRating == 4.5 and .book.totalReviews == 2 and (.reviews | length) == 2' "Promedio 4.5 con 2 reseñas, y el libro trae sus reseñas"
req GET "/api/reviews/book/$BOOK_A?minRating=5"
expect_true '(. | length) == 1' "Reseñas del libro con 5 estrellas o más"
req GET "/api/reviews/user/$USER_1"
expect_true "(. | length) == 1 and .[0].id == \"$REVIEW_1\"" "Reseñas del usuario 1"

req PUT "/api/reviews/$REVIEW_2" '{"rating":2,"comment":"Cambié de opinión"}'
expect_status 200 "Editar la reseña del usuario 2"
req GET "/api/books/$BOOK_A"
expect_true '.book.averageRating == 3.5' "El promedio se recalcula a 3.5"
req GET "/api/users/$USER_1/activity"
expect_true '.totalReviews == 1' "El contador de reseñas del usuario sube"

section "4.1 Libros populares y mejor calificados"
req GET "/api/books/popular?limit=50"
expect_true "any(.[]; .id == \"$BOOK_A\") and ([.[].totalLoans] == ([.[].totalLoans] | sort | reverse))" "Populares ordenados por cantidad de préstamos"
req GET "/api/books/top-rated?limit=50&minReviews=2"
expect_true "any(.[]; .id == \"$BOOK_A\") and all(.[]; .totalReviews >= 2)" "Mejor calificados con mínimo 2 reseñas"

# ---------- 5. Favoritos ----------

section "5. Favoritos"
req POST "/api/users/$USER_1/favorites/$BOOK_A"
expect_status 201 "Agregar el libro A a favoritos"
expect_true ".userId == \"$USER_1\" and (.books | length) == 1 and .books[0].title == \"Libro A $RUN\" and .books[0].authorName == \"Autor $RUN\"" "La lista guarda copia del título y del autor"
req POST "/api/users/$USER_1/favorites/$BOOK_A"
expect_status 409 "No se repiten libros en favoritos"
req POST "/api/users/$USER_1/favorites/000000000000000000000000"
expect_status 404 "Favorito de un libro que no existe"
req POST "/api/users/$USER_1/favorites/$BOOK_E"
req DELETE "/api/users/$USER_1/favorites/$BOOK_E"
expect_status 200 "Quitar un libro de favoritos"
req DELETE "/api/users/$USER_1/favorites/$BOOK_E"
expect_status 404 "Quitar un libro que ya no está"
req GET "/api/users/$USER_1/favorites"
expect_true '(.books | length) == 1' "Consultar la lista de favoritos"
req GET "/api/users/$USER_1/activity"
expect_true '.favoritesCount == 1' "El contador de favoritos del usuario"

# ---------- 6. Consistencia de datos denormalizados ----------

section "6. Consistencia: se cambia el título y el ISBN del libro A"
req GET "/api/books/$BOOK_A"
UPDATE_BODY=$(printf '%s' "$BODY" | jq -c --arg t "Libro A editado $RUN" --arg i "ISBN-$RUN-A2" \
  '{uniqueIdentifier: $i, title: $t, synopsis: .book.synopsis, publicationYear: .book.publicationYear, author: .book.author, editorial: .book.editorial, categories: .book.categories}')
AUTHOR_ID=$(json .book.author.id)
req PUT "/api/books/$BOOK_A" "$UPDATE_BODY"
expect_status 200 "Editar el libro A"
expect_true '.averageRating == 3.5 and .totalLoans == 2 and .loaned == false' "Editar el libro no borra el promedio ni los préstamos"
NEW_TITLE="Libro A editado $RUN"
req GET "/api/loans/book/$BOOK_A"
expect_true "all(.[]; .bookTitle == \"$NEW_TITLE\" and .bookUniqueIdentifier == \"ISBN-$RUN-A2\")" "Préstamos con el título y el ISBN nuevos"
req GET "/api/reviews/book/$BOOK_A"
expect_true "all(.[]; .bookTitle == \"$NEW_TITLE\")" "Reseñas con el título nuevo"
req GET "/api/users/$USER_1/favorites"
expect_true ".books[0].title == \"$NEW_TITLE\"" "Favoritos con el título nuevo"
req GET "/api/users/$USER_1/activity"
expect_true "all(.recentLoans[] | select(.bookId == \"$BOOK_A\"); .bookTitle == \"$NEW_TITLE\")" "Últimos préstamos del usuario con el título nuevo"
req GET "/api/authors/$AUTHOR_ID/books"
expect_true "any(.[]; .bookId == \"$BOOK_A\" and .title == \"$NEW_TITLE\")" "La lista de libros del autor con el título nuevo"

section "6.1 Consistencia: se cambia el nombre del usuario 1"
req PUT "/api/users/$USER_1" "{\"fullName\":\"Ana María $RUN\",\"email\":\"ana.$RUN@correo.com\",\"phone\":\"3110000000\"}"
expect_status 200 "Editar el usuario 1"
expect_true '.activity.activeLoans == 2 and .activity.totalReviews == 1' "Editar el usuario no borra su resumen de actividad"
req GET "/api/loans/user/$USER_1"
expect_true "all(.[]; .userFullName == \"Ana María $RUN\")" "Préstamos con el nombre nuevo"
req GET "/api/reviews/user/$USER_1"
expect_true "all(.[]; .userFullName == \"Ana María $RUN\")" "Reseñas con el nombre nuevo"
req GET "/api/users/$USER_1/favorites"
expect_true ".userFullName == \"Ana María $RUN\"" "Favoritos con el nombre nuevo"
req GET "/api/books/$BOOK_B"
expect_true ".book.currentLoan.userFullName == \"Ana María $RUN\"" "El libro prestado muestra el nombre nuevo"

# ---------- 7. Redis ----------

section "7. Redis (caché)"
req GET /api/books
req GET /api/books
expect_status 200 "Consultar todos los libros dos veces"
expect_contains "$(redis_cli --scan --pattern 'books::*')" "books::getAllBooks()" "La lista de libros quedó guardada en Redis"
expect_contains "$(redis_cli GET 'books::getAllBooks()')" "@class" "El valor guardado es JSON con el tipo de cada objeto"
TTL=$(redis_cli TTL 'books::getAllBooks()')
if [ "$TTL" -gt 0 ] && [ "$TTL" -le 60 ]; then ok "La entrada vence en $TTL segundos (máximo 60)"; else fail "TTL inesperado: $TTL"; fi
create_book F "Libro F $RUN"
BOOK_F=$(json .id)
if [ "$(redis_cli EXISTS 'books::getAllBooks()')" = "0" ]; then ok "Al crear un libro se borra la caché de la lista"; else fail "La caché de la lista no se borró"; fi
req GET /api/books
expect_true "any(.[]; .id == \"$BOOK_F\")" "La lista no queda desactualizada (trae el libro nuevo)"
req GET /api/loans/stats
expect_status 200 "Estadísticas de préstamos"
expect_true '.totalLoans >= 1 and .activeLoans >= 1 and .overdueLoans >= 0 and (.mostLoanedBooks | length) >= 1 and (.topBorrowers | length) >= 1 and .availableBooks == (.totalBooks - .booksOnLoan)' "Las estadísticas traen totales y rankings"
STATS_TTL=$(redis_cli TTL 'loanStats::getLoanStats()')
if [ "$STATS_TTL" -gt 0 ] && [ "$STATS_TTL" -le 30 ]; then ok "Las estadísticas duran $STATS_TTL segundos en caché (máximo 30)"; else fail "TTL de estadísticas inesperado: $STATS_TTL"; fi
req GET "/api/users/$USER_1"
expect_contains "$(redis_cli --scan --pattern 'users::*')" "users::getUserById($USER_1)" "El usuario quedó guardado en Redis"
req GET /api/authors
expect_contains "$(redis_cli --scan --pattern 'authors::*')" "authors::getAllAuthors()" "Los autores (taller anterior) también usan Redis"

# ---------- 8. Reglas al borrar ----------

section "8. Reglas al borrar"
req DELETE "/api/users/$USER_1"
expect_status 409 "No se puede borrar un usuario con préstamos activos"
req DELETE "/api/books/$BOOK_B"
expect_status 409 "No se puede borrar un libro prestado"
req PUT "/api/loans/$LOAN_B/return"
req PUT "/api/loans/$LOAN_C/return"
create_review "$USER_1" "$BOOK_C" 3
REVIEW_C=$(json .id)
req POST "/api/users/$USER_1/favorites/$BOOK_C"
req DELETE "/api/books/$BOOK_C"
expect_status 204 "Borrar el libro C (ya devuelto)"
req GET "/api/reviews/$REVIEW_C"
expect_status 404 "Las reseñas del libro borrado se eliminan"
req GET "/api/users/$USER_1/favorites"
expect_true "all(.books[]; .bookId != \"$BOOK_C\")" "El libro borrado sale de favoritos"
req GET "/api/users/$USER_1/activity"
expect_true '.favoritesCount == 1 and .totalReviews == 1' "Los contadores del usuario se ajustan"
req GET "/api/loans/book/$BOOK_C"
expect_true '(. | length) == 1' "El historial de préstamos del libro borrado se conserva"

req DELETE "/api/users/$USER_2"
expect_status 204 "Borrar el usuario 2 (sin préstamos activos)"
req GET "/api/reviews/$REVIEW_2"
expect_status 404 "Las reseñas del usuario borrado se eliminan"
req GET "/api/books/$BOOK_A"
expect_true '.book.averageRating == 5 and .book.totalReviews == 1' "El promedio del libro A se recalcula sin esa reseña"
req DELETE "/api/users/$USER_2"
expect_status 404 "Borrar un usuario que ya no existe"

# ---------- 9. Índices y endpoints del taller anterior ----------

section "9. Índices en MongoDB"
expect_contains "$(mongo_eval "db.loans.getIndexes().map(i => i.name).join(',')")" "unique_active_loan_per_book" "Índice único parcial: un préstamo activo por libro"
expect_contains "$(mongo_eval "db.users.getIndexes().filter(i => i.unique).map(i => i.name).join(',')")" "email_1" "Índice único de email"
expect_contains "$(mongo_eval "db.reviews.getIndexes().filter(i => i.unique).map(i => i.name).join(',')")" "bookId_1_userId_1" "Índice único de reseña por usuario y libro"
expect_contains "$(mongo_eval "db.favorites.getIndexes().filter(i => i.unique).map(i => i.name).join(',')")" "userId_1" "Índice único de favoritos por usuario"

section "10. Endpoints del taller anterior y Swagger"
req GET /api/authors;     expect_status 200 "Autores"
req GET /api/editorials;  expect_status 200 "Editoriales"
req GET /api/categories;  expect_status 200 "Categorías"
req GET "/api/books/search/author/Autor%20$RUN"
expect_true '(. | length) >= 5' "Buscar libros por autor"
req GET /v3/api-docs;     expect_status 200 "Documentación OpenAPI"
req GET /swagger-ui/index.html; expect_status 200 "Swagger UI"

# ---------- Resumen ----------

echo
echo "Resultado: $PASS pruebas bien, $FAIL con error"
[ "$FAIL" -eq 0 ]
