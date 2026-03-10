package org.example.tea_screen_utils

/**
 * Чистые строковые утилиты для преобразования между различными форматами имён,
 * используемыми артефактами экрана. Зависимостей от IntelliJ Platform нет.
 *
 * Пример — имя экрана "SomeScreen":
 *   pascalToSnake      → "some_screen"
 *   toFolderName       → "somescreen"
 *   toLayoutFileName   → "fragment_some_screen.xml"
 *   toBindingClassName → "FragmentSomeScreenBinding"
 */
object ScreenNameUtils {

    /** "SomeScreen" → "some_screen" */
    fun pascalToSnake(name: String): String =
        name.replace(Regex("([A-Z])")) { "_${it.value.lowercase()}" }
            .removePrefix("_")

    /** "some_screen" → "SomeScreen" */
    fun snakeToPascal(name: String): String =
        name.split("_").joinToString("") { part ->
            part.replaceFirstChar { it.uppercase() }
        }

    /** "SomeScreen" → "somescreen"  (используется для имени пакета/директории) */
    fun toFolderName(name: String): String = name.lowercase()

    /** "SomeScreen" → "fragment_some_screen.xml" */
    fun toLayoutFileName(name: String): String = "fragment_${pascalToSnake(name)}.xml"

    /** "SomeScreen" → "FragmentSomeScreenBinding" (сгенерированный класс View Binding) */
    fun toBindingClassName(name: String): String = "Fragment${name}Binding"

    /** Возвращает true, если [name] — корректный PascalCase (начинается с заглавной буквы, только буквы и цифры). */
    fun isValidPascalCase(name: String): Boolean =
        name.isNotEmpty() && name.matches(Regex("[A-Z][A-Za-z0-9]*"))
}
