# Rastreadores (versión de prueba)

App de Android que muestra a qué servidores intenta conectarse cada app del teléfono y marca los de publicidad y análisis conocidos.

## Descargar

La versión más reciente está en **Releases → Rastreadores.apk**:
https://github.com/cmitjans/Rastreadores/releases/latest

## Cómo funciona

Antes de conectarse a un servidor, cada app pregunta su dirección ("¿dónde está graph.facebook.com?"). Esa pregunta es una **consulta DNS**.

Rastreadores crea una **VPN local** que captura solo esas consultas:

1. Android envía la consulta a la VPN, que vive dentro del teléfono.
2. La app anota qué app preguntó y por qué dominio (Android le dice qué app abrió cada conexión).
3. Reenvía la consulta, sin cambios, al servidor DNS de tu red (el de tu Wi‑Fi o tu compañía móvil) y devuelve la respuesta.

El resto del tráfico **no pasa por la app**: va directo a internet como siempre. La app nunca ve el contenido de lo que envían las apps, solo los nombres de los servidores.

Lo registrado se guarda solo en la memoria del teléfono y se borra al cerrar la app o al tocar "Borrar registro".

## Permisos

| Permiso | Para qué |
| --- | --- |
| Internet | Reenviar cada consulta DNS al servidor DNS de tu red. Es la única conexión que hace. |
| Ver estado de la red | Saber qué servidores DNS usa tu red (Wi‑Fi o datos). |
| Ver apps instaladas | Mostrar el nombre de la app que hizo cada consulta. |
| Servicio en primer plano | Mantener el registro activo en segundo plano. |
| Notificaciones | El aviso "Registrando conexiones" con el botón Detener. |

La VPN no es un permiso de la app: Android te pide confirmarla cada vez que se inicia por primera vez.

Si tu red no informa sus servidores DNS, la app usa de respaldo los de Cloudflare (1.1.1.1) y Google (8.8.8.8).

## Limitaciones de esta versión

- La lista de rastreadores (`Trackers.java`) tiene unos 100 dominios conocidos; las listas completas tienen miles.
- No ve las apps que usan su propio DNS cifrado, ni nada si tienes **DNS privado** configurado con un proveedor específico en Ajustes.
- Android solo permite una VPN a la vez.
- No bloquea nada todavía; solo registra.

## Compilar

`build.sh` compila con las herramientas del SDK de Android, sin Gradle. GitHub lo ejecuta con cada cambio (`.github/workflows/compilar.yml`) y publica el APK en Releases. La clave de firma (`firma/`) está en el repositorio para que cada versión se instale encima de la anterior.
