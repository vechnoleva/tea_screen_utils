# Tea Screen Utils

Плагин для **Android Studio** — набор инструментов для работы с MVP-экранами. Позволяет создавать новые экраны и переименовывать существующие одним действием.

> Совместим с Android Studio Hedgehog (AI-233) и выше.

---

## Инструменты

### 1. New Tea Screen

Генерирует полный boilerplate MVP-экрана по заданному имени и набору опций — вместо того чтобы создавать каждый файл вручную.

**Что создаётся:**

| Артефакт              | Пример                      |
|-----------------------|-----------------------------|
| Fragment              | `HomeFragment.kt`           |
| Contract              | `HomeContract.kt`           |
| Presenter             | `HomePresenter.kt`          |
| DI (Module, Component)| `HomeDI.kt`                 |
| XML-лейаут            | `fragment_home.xml`         |
| Adapter *(опционально)* | `HomeAdapter.kt`          |
| Mapper / MapperImpl *(опционально)* | `HomeMapper.kt` |
| Params *(опционально)*| `HomeParams.kt`             |

Также автоматически добавляет записи в `AppComponent.kt` и `Screens.kt`.

**Опции диалога:**

- **Screen name** — имя экрана в формате PascalCase
- **Has params** — добавить `Params`-класс и передачу аргументов через `Bundle`
- **Has RecyclerView** — добавить `Adapter`, `Mapper`, `MapperImpl` и настройку списка
- **Is Bottom Sheet** — сгенерировать фрагмент на основе `BaseBottomSheetFragment`
- **Toolbar** — `No Toolbar` / `Titled Toolbar` (добавляет `<include toolbar>` в лейаут и `setupToolbar()` в `initUI`)

**Как использовать:**

1. В дереве проекта **кликните правой кнопкой** на папке, в которой нужно создать экран.
2. Выберите **New → New Tea Screen**.
3. Заполните диалог и нажмите **OK**.

---

### 2. Rename Screen (All Files)

Переименовывает все артефакты MVP-экрана одной операцией — вместо того чтобы переименовывать каждый файл вручную.

**Что переименовывается:**

| Артефакт               | Пример до                | Пример после             |
|------------------------|--------------------------|--------------------------|
| Fragment               | `OldNameFragment.kt`     | `NewNameFragment.kt`     |
| Presenter              | `OldNamePresenter.kt`    | `NewNamePresenter.kt`    |
| Contract               | `OldNameContract.kt`     | `NewNameContract.kt`     |
| Params                 | `OldNameParams.kt`       | `NewNameParams.kt`       |
| Mapper / MapperImpl    | `OldNameMapper.kt`       | `NewNameMapper.kt`       |
| Adapter                | `OldNameAdapter.kt`      | `NewNameAdapter.kt`      |
| DI (Module, Component) | `OldNameDI.kt`           | `NewNameDI.kt`           |
| XML-лейаут             | `fragment_old_name.xml`  | `fragment_new_name.xml`  |
| View Binding           | `FragmentOldNameBinding` | `FragmentNewNameBinding` |
| Запись в Screens       | `Screens.OldName`        | `Screens.NewName`        |
| Пакет/директория       | `…/oldname/`             | `…/newname/`             |
| Объявление `package`   | `package …oldname`       | `package …newname`       |

Все ссылки на переименованные классы (Kotlin, Java, XML) обновляются автоматически. Переименование выполняется как единая команда и отменяется одним `Ctrl+Z`.

**Как использовать:**

1. В редакторе или дереве проекта **кликните правой кнопкой** на любом файле экрана.
2. Выберите **Rename Screen (All Files)** в контекстном меню.
3. Введите новое имя в формате PascalCase — предпросмотр покажет производные имена в реальном времени.
4. Нажмите **OK**.

> **Совет.** Перед запуском убедитесь, что IDE завершила индексирование проекта.

---

## Установка

1. В Android Studio откройте **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. Выберите скачанный `.zip`-файл.
3. Перезапустите IDE.

---

## Сборка из исходников

**Требования:**
- JDK 21+
- Android Studio Narwhal (или другая версия — скорректируйте путь в `build.gradle.kts`)

```bash
./gradlew buildPlugin
# Готовый zip: build/distributions/tea_screen_utils-1.0.zip
```

---

## Структура проекта

```
src/main/kotlin/org/example/tea_screen_utils/
├── ScreenNameUtils.kt          # Преобразования строк (PascalCase ↔ snake_case, имена файлов)
├── ScreenElementsFinder.kt     # Поиск PSI-элементов экрана по индексу IDE
├── MultiRenameProcessor.kt     # Пакетное переименование через RenameProcessor + VirtualFile
├── RenameScreenDialog.kt       # Диалог ввода нового имени с предпросмотром
├── RenameScreenAction.kt       # AnAction — точка входа для Rename Screen
├── NewTeaScreenAction.kt       # AnAction — точка входа для New Tea Screen
├── NewTeaScreenDialog.kt       # Диалог создания экрана с опциями
└── NewTeaScreenGenerator.kt    # Генерация файлов и модификация AppComponent/Screens
```

---

*Автор: Levan Davityan*
