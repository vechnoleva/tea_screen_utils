package org.example.tea_screen_utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Диалог, запрашивающий у пользователя новое имя экрана в PascalCase и показывающий
 * живой предпросмотр производных имён артефактов (папка, лейаут, binding-класс).
 *
 * Использование:
 *   val dialog = RenameScreenDialog(project, "SomeScreen")
 *   if (dialog.showAndGet()) {
 *       val newName = dialog.newScreenName   // например, "OtherScreen"
 *   }
 */
class RenameScreenDialog(
    project: Project,
    private val oldScreenName: String
) : DialogWrapper(project) {

    private val nameField = JBTextField(oldScreenName, 30)

    /** Проверенное новое имя экрана, введённое пользователем. */
    val newScreenName: String
        get() = nameField.text.trim()

    init {
        title = "Переименовать экран"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        var row = 0

        fun gbc(x: Int, weightX: Double = 0.0) = GridBagConstraints().apply {
            gridx = x; gridy = row
            fill = GridBagConstraints.HORIZONTAL
            weightx = weightX
            insets = Insets(4, 8, 4, 8)
        }

        // ── Строка ввода ───────────────────────────────────────────────────
        panel.add(JBLabel("Новое имя экрана (PascalCase):"), gbc(0))
        panel.add(nameField, gbc(1, 1.0))
        row++

        // ── Строки предпросмотра ───────────────────────────────────────────
        fun addPreview(label: String, compute: (String) -> String) {
            val valueLabel = JBLabel(safeCompute(compute, oldScreenName))
            panel.add(JBLabel(label), gbc(0))
            panel.add(valueLabel, gbc(1, 1.0))
            row++

            // Обновляем предпросмотр при каждом нажатии клавиши.
            nameField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = refresh()
                override fun removeUpdate(e: DocumentEvent?) = refresh()
                override fun changedUpdate(e: DocumentEvent?) = refresh()
                private fun refresh() {
                    valueLabel.text = safeCompute(compute, newScreenName)
                }
            })
        }

        panel.add(JBLabel("Предпросмотр:"), gbc(0))
        row++
        addPreview("  Папка / пакет:")    { ScreenNameUtils.toFolderName(it) }
        addPreview("  Файл лейаута:")     { ScreenNameUtils.toLayoutFileName(it) }
        addPreview("  Binding-класс:")    { ScreenNameUtils.toBindingClassName(it) }
        row++
        row++
        panel.add(JBLabel("Created by Levan Davityan ©"), gbc(0))

        return panel
    }

    private fun safeCompute(compute: (String) -> String, name: String): String =
        try { compute(name) } catch (e: Exception) { "…" }

    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()
        return when {
            name.isEmpty() ->
                ValidationInfo("Имя не может быть пустым.", nameField)
            !ScreenNameUtils.isValidPascalCase(name) ->
                ValidationInfo(
                    "Имя должно быть в PascalCase: начинаться с заглавной буквы, только буквы и цифры.",
                    nameField
                )
            name == oldScreenName ->
                ValidationInfo("Новое имя должно отличаться от старого.", nameField)
            else -> null
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField
}
