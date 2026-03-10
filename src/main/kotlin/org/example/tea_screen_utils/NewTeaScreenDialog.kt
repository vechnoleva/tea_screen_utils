package org.example.tea_screen_utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel

class NewTeaScreenDialog(project: Project) : DialogWrapper(project) {

    private val nameField = JBTextField(30)
    private val hasParamsCheck = JBCheckBox("Has params", false)
    private val hasRecyclerViewCheck = JBCheckBox("Has RecyclerView", false)
    private val isBottomSheetCheck = JBCheckBox("Is Bottom Sheet", false)

    val screenName: String get() = nameField.text.trim()
    val hasParams: Boolean get() = hasParamsCheck.isSelected
    val hasRecyclerView: Boolean get() = hasRecyclerViewCheck.isSelected
    val isBottomSheet: Boolean get() = isBottomSheetCheck.isSelected

    init {
        title = "New Tea Screen"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        var row = 0

        fun gbc(x: Int, weightX: Double = 0.0, gridWidth: Int = 1) = GridBagConstraints().apply {
            gridx = x; gridy = row
            gridwidth = gridWidth
            fill = GridBagConstraints.HORIZONTAL
            weightx = weightX
            insets = Insets(4, 8, 4, 8)
        }

        panel.add(JBLabel("Screen name (PascalCase):"), gbc(0))
        panel.add(nameField, gbc(1, 1.0))
        row++

        panel.add(hasParamsCheck, gbc(0, 1.0, 2))
        row++
        panel.add(hasRecyclerViewCheck, gbc(0, 1.0, 2))
        row++
        panel.add(isBottomSheetCheck, gbc(0, 1.0, 2))

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()
        return when {
            name.isEmpty() -> ValidationInfo("Screen name cannot be empty.", nameField)
            !ScreenNameUtils.isValidPascalCase(name) ->
                ValidationInfo("Screen name must be PascalCase (starts with uppercase letter, letters and digits only).", nameField)
            else -> null
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField
}
