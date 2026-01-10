RoadSensorRecorder
==================

Коротко
-------
Цей репозиторій містить Android-додаток, який записує дані з акселерометра/гіроскопа і (за наявності дозволів) локацію у CSV-файли.

Швидкий старт
------------
1) Відкрити проект у Android Studio або використовувати скрипт PowerShell у корені проекту.

2) Щоб зібрати debug APK (для відладки / швидкого встановлення):

```powershell
.\build-and-install.ps1 -Configuration debug
```

3) Щоб зібрати release APK і підписати його (за замовчуванням використовується debug keystore):

```powershell
.\build-and-install.ps1 -Configuration release
```

Після успішної збірки підписаний APK буде у папці `dist/` (наприклад `dist/app-release-signed-aligned.apk`).

Підпис власним keystore
-----------------------
Щоб підписати релізний APK власним keystore, передайте параметри до скрипту:

```powershell
.\build-and-install.ps1 -Configuration release -KeystorePath C:\path\to\keystore.jks -KeystorePassword <ksPass> -KeyAlias <alias> -KeyPassword <keyPass>
```

Як встановити APK на пристрій (через скрипт)
------------------------------------------
- Якщо підключено один пристрій, запустіть з `-Install`:

```powershell
.\build-and-install.ps1 -Configuration debug -Install
```

- Якщо декілька пристроїв, додайте `-DeviceSerial <serial>` (серійний номер можна побачити через `adb devices`).

Поради щодо дозволів на пристрої
--------------------------------
- App потребує location permissions (ACCESS_FINE_LOCATION, ACCESS_COARSE, ACCESS_BACKGROUND_LOCATION) для запису локації у фоні. При першому запуску додаток запитує ці дозволи.
- Якщо у вас на телефоні з'являється більше однієї іконки програми (кілька копій), це, ймовірно, через додаткові профілі/контейнери на пристрої (наприклад, Secure Folder або Dual App). Видаліть копію через інтерфейс Secure Folder/Dual App або спробуйте команду `adb shell pm uninstall --user <id> com.example.roadsensorrecorder` для відповідного user id (тільки якщо shell має доступ).
