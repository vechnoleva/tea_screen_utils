# Rename Screen (All Files)

Плагин для **Android Studio**, который переименовывает все артефакты MVP-экрана одной операцией — вместо того чтобы переименовывать каждый файл вручную.

> Совместим с Android Studio Hedgehog (AI-233) и выше.

---

## Зачем нужен плагин

У каждого Android-экрана обычно есть несколько связанных файлов: фрагмент, презентер, контракт, параметры, маппер, адаптер, DI-модуль, XML-layout и запись в реестре экранов. Стандартный рефакторинг IDE переименовывает только один класс и ссылки на него, но не трогает остальные файлы экрана, имена файлов и объявления пакетов.

Плагин делает всё это в одно действие с поддержкой отмены (`Ctrl+Z`).

---

## Что переименовывается

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

Все ссылки на переименованные классы (Kotlin, Java, XML) обновляются автоматически.

---

## Установка

1. В Android Studio откройте **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. Выберите скачанный `.zip`-файл.
3. Перезапустите IDE.

---

## Использование

1. В редакторе или дереве проекта **кликните правой кнопкой** на любом файле экрана:
   - `SomeScreenFragment.kt`, `SomeScreenPresenter.kt`, `fragment_some_screen.xml` и т.д.
2. Выберите **«Rename Screen (All Files)»** в контекстном меню.
3. В появившемся диалоге введите новое имя в формате **PascalCase** (например, `NewScreen`).
   - Предпросмотр под полем показывает производные имена в реальном времени.
4. Нажмите **OK** — IDE покажет прогресс-бар, затем переименует все файлы.

Всё переименование выполняется как единая команда — её можно отменить одним `Ctrl+Z`.

> **Совет.** Перед запуском рефакторинга убедитесь, что IDE завершила индексирование проекта (индикатор в правом нижнем углу не крутится).

---

## Сборка из исходников

**Требования:**
- JDK 21+
- Android Studio Narwhal (или другая версия — скорректируйте путь в `build.gradle.kts`)

```bash
# Собрать плагин
./gradlew buildPlugin

# Готовый zip:
# build/distributions/tea_rename_screen-1.0-SNAPSHOT.zip
```

Установите полученный `.zip` через **Settings → Plugins → ⚙ → Install Plugin from Disk…**

---

## Структура проекта

```
src/main/kotlin/org/example/tea_rename_screen/
├── ScreenNameUtils.kt        # Преобразования строк (PascalCase ↔ snake_case, имена файлов)
├── ScreenElementsFinder.kt   # Поиск PSI-элементов экрана по индексу IDE
├── MultiRenameProcessor.kt   # Пакетное переименование через RenameProcessor + VirtualFile
├── RenameScreenDialog.kt     # Диалог ввода нового имени с предпросмотром
└── RenameScreenAction.kt     # AnAction — точка входа из контекстного меню
```

---