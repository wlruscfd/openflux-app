# Android-приложение OpenFlux

[English](README.md) | **Русский**

Форк [p1neappleXpress/OpenFlux](https://github.com/p1neappleXpress/OpenFlux). Отдельное
Android-приложение с несколькими переключаемыми профилями, построенное поверх того же Go-ядра
(`transport`, `tunnel`), что и десктопный клиент/exit-node — через
[`gomobile bind`](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile) вместо запуска CLI-бинарника
отдельным процессом. См. `mobile/` (точка входа gomobile) и `gateway/` (превращает TUN-дескриптор
Android `VpnService` в TCP-соединения через туннель — аналог `socks5/` того же репозитория, но для
системного перехвата трафика) в
[openflux-server](https://github.com/wlruscfd/openflux-server).

## Единоразовая настройка

Нужны Android SDK и NDK (версия NDK 27.x; точную версию см. в README
[openflux-server](https://github.com/wlruscfd/openflux-server)).

```bash
export ANDROID_HOME="/путь/до/вашего/Android/sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/<версия>"
```

## Сборка

```bash
# 1. В вашей копии openflux-server: собирает mobile/ в .aar.
#    Если openflux-server и этот репозиторий лежат рядом друг с другом
#    именно с такими именами папок, путь по умолчанию сработает сам; иначе
#    укажите папку app/libs этого репозитория явно:
#      ./build_android_aar.sh /путь/до/openflux-app/app/libs
cd ../openflux-server && ./build_android_aar.sh

# 2. Отсюда (корень этого репозитория): собирает APK
cd ../openflux-app
gradle assembleDebug        # или ./gradlew, если получится скачать его wrapper-архив
```

Debug-APK окажется в `app/build/outputs/apk/debug/app-debug.apk`.

## Что пока не поддерживается

- Только транспорт Yandex Docs (транспорт MAX/"oneme" тянет за собой зависимость —
  `github.com/wlynxg/anet` — чей хак `go:linkname` во внутренности стандартной библиотеки не
  собирается с любым тулчейном Go; новой версии, которая бы это чинила, нет, а managed-режим
  controlplane и так работает только через Yandex, так что здесь это не потеря).
- Универсальный проброс UDP/QUIC — ретранслируется только TCP и DNS-over-TCP (для UDP/53).
  Большая часть софта всё равно работает, так как откатывается на TCP, но HTTP/3 и приложения/игры,
  которым нужен именно UDP, через туннель пока не подключатся.
- IPv6 — трафик IPv6 заворачивается в туннель (чтобы не утекать мимо него), но затем просто
  отбрасывается, а не ретранслируется.

## Раздельное туннелирование

Оба вида настраиваются глобально (Настройки) и применяются ко всем профилям:

- **По приложениям** — системный VPN фильтрует целые приложения (маршрутизация per-app через
  `VpnService`).
- **По сайтам** — gateway маршрутизирует по сайтам: исключите домен из туннеля или туннелируйте
  только перечисленные домены. Сайты определяются по TLS-SNI, а кэш DNS gateway покрывает
  соединения без SNI.

## Профили

Каждый профиль — это один из двух вариантов:
- **Ключ доступа** — базовый URL [controlplane](https://github.com/wlruscfd/openflux-server/tree/main/controlplane)
  + токен ключа. Приложение резолвит его в ссылку на Yandex Docs через `POST /v1/resolve` (вызовом
  `mobile.Mobile.resolveKey`) при каждом подключении, поэтому сама ссылка на документ никогда не
  хранится на устройстве.
- **Вручную** — сырая ссылка на Yandex Docs, для тестирования на собственной exit-ноде — точно так
  же, как работает флаг `--url` у десктопного CLI.

Одновременно может быть подключён только один профиль (как и у устройства — только одно активное
VPN-соединение). Секреты (токен ключа / ссылка на документ) хранятся в
`EncryptedSharedPreferences` на базе Android Keystore — никогда в обычной базе Room вместе с
остальными настройками профиля.

## Импорт профиля по ссылке

`ProfileDeepLink.kt` реализует `openflux://import?data=<base64url-json>` — переход по такой ссылке
(из браузера, другого приложения или QR-кода с такой ссылкой) открывает приложение сразу на экране
редактирования профиля, предзаполненном из ссылки, чтобы пользователь проверил и сохранил его
сам. Ничего не сохраняется и не подключается автоматически, так как ссылка BROWSABLE и может прийти
из недоверенного источника. JSON-payload (до base64url-кодирования, без паддинга):

```json
{
  "name": "Мой профиль",
  "mode": "key",
  "control_url": "https://control.example.com",
  "key_token": "key_...",
  "transport": "yandex",
  "doc_url": "",
  "max_token": "",
  "max_uid": 0,
  "mtu": 1400,
  "dns_upstream": "77.88.8.8",
  "auto_reconnect": true
}
```

`transport` (`"yandex"` или `"max"`) и `max_token`/`max_uid` имеют значение только при
`mode: "manual"` — см. `ManualTransport` в `data/Profile.kt`. У профиля с `mode: "key"` транспорт
приходит от самого controlplane, а не из ссылки.

`ProfileDeepLink.buildUri(profile)` строит такую же ссылку из `Profile` — для всего, что должно их
генерировать (например, панель управления controlplane или ответ ingest API).

## Контрибьюторы

- [FLAT447](https://github.com/FLAT447) — починил раздельное туннелирование, которое показывало
  только системные приложения ([#1](https://github.com/wlruscfd/openflux-app/pull/1)); добавил
  динамические цвета Material You и переработал UI главного экрана
  ([#2](https://github.com/wlruscfd/openflux-app/pull/2)).
