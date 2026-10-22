# API HTTP

Base: `http://localhost:8088` (o tu dominio con HTTPS).

Autenticación:

```bash
TOKEN=tu-token
curl -H "Authorization: Bearer $TOKEN" http://localhost:8088/api/library
# equivalente:
curl "http://localhost:8088/api/library?token=$TOKEN"
```

Errores:

| Código | Significado |
|---|---|
| `401` | Falta el token o no es válido. |
| `403` | Token válido pero la ruta requiere admin. |
| `404` | Fichero o recurso inexistente. |
| `409` | El usuario o token ya existe. |
| `500` | Error del servidor (ver logs). |

---

## Estado

### `GET /api/health`

No requiere token.

```json
{ "ok": true, "name": "espotifai-server", "version": "1.0.0", "totalTracks": 2525 }
```

---

## Biblioteca

### `GET /api/library`

Devuelve artistas → álbumes → pistas.

```json
{
  "library": [
    {
      "id": "QXJ0aXN0YQ",
      "name": "Artista",
      "albums": [
        {
          "id": "QXJ0aXN0YS9EaXNjbw",
          "name": "Disco",
          "cover": "QXJ0aXN0YS9EaXNjbw/cover.jpg",
          "tracks": [
            {
              "id": "QXJ0aXN0YS9EaXNjbw/MDEtQ2FuY2lvbi5tcDM",
              "title": "Canción",
              "artist": "Artista",
              "album": "Disco",
              "track": 1,
              "duration": 245,
              "bitrate": 320000
            }
          ]
        }
      ]
    }
  ],
  "totalTracks": 1
}
```

### `GET /api/artists`

Lista ligera de artistas:

```json
[{ "id": "QXJ0aXN0YQ", "name": "Artista", "albumsCount": 3 }]
```

### `POST /api/rescan`

Vuelve a escanear la carpeta. Puede tardar minutos.

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8088/api/rescan
```

```json
{ "ok": true, "totalTracks": 2525 }
```

---

## Audio y carátulas

### `GET /api/stream/:id`

Audio con soporte de `Range`:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  -H "Range: bytes=0-1023" \
  http://localhost:8088/api/stream/QXJ0aXN0YS9EaXNjbw/...
```

`206` con `Content-Range` si se pide un rango; `200` con el fichero completo si no.

### `GET /api/cover/:id`

Imagen del álbum (JPEG/PNG) con `Cache-Control: public, max-age=86400`.

### `GET /api/album/:id/zip`

Descarga el álbum completo en un ZIP (pistas + portada si existe). El `id` es el del álbum en `/api/library`.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  -o album.zip \
  "http://localhost:8088/api/album/QXJ0aXN0YS9EaXNjbw/zip"
```

La respuesta lleva `Content-Type: application/zip` y un `Content-Disposition` con el nombre `Artista - Álbum.zip`. El ZIP se genera en streaming; álbumes grandes pueden tardar unos segundos.

> La app web usa este endpoint para el botón **Descargar ZIP**. No es un modo offline: no se guarda audio en el navegador.

---

## Usuarios (solo admin)

### `GET /api/users`

```json
{
  "count": 2,
  "users": [
    { "name": "salon", "created": "2026-01-01T00:00:00.000Z", "token": "abcd…wxyz" }
  ]
}
```

El token se devuelve enmascarado.

### `POST /api/users`

Crea un usuario. Si no pasas `token`, se genera uno libre.

```bash
curl -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"name":"salon"}' http://localhost:8088/api/users
```

```json
{ "ok": true, "name": "salon", "token": "..." }
```

### `PUT /api/users/:name`

Cambia el token. Sin `token` genera uno nuevo; con `{"token":"..."}` fija uno concreto (si no está en uso).

```bash
curl -X PUT -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{}' http://localhost:8088/api/users/salon
```

### `DELETE /api/users/:name`

Borra el usuario y revoca su token.

```bash
curl -X DELETE -H "Authorization: Bearer $ADMIN" \
  http://localhost:8088/api/users/salon
```

---

## Log de fallos de la app

### `POST /api/clientlog`

La app envía la traza en el cuerpo (texto). Límite 256 KB.

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: text/plain' \
  --data-binary @crash.txt \
  http://localhost:8088/api/clientlog
```

### `GET /api/clientlog`

Devuelve el fichero `data/clientlog.txt` (se recorta a ~512 KB).

---

## Alias

Por compatibilidad existen también `/library`, `/artists`, `/api/v1/library`, `/api/v1/artists`, `/api/all` y `/api/artists/list`. Todos requieren token y devuelven lo mismo que sus equivalentes.
