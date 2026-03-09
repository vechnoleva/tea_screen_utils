package org.example.newscreentea

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.Messages

/**
 * Действие: «Переименовать экран (все файлы)»
 *
 * Вызывается из контекстного меню редактора (правый клик на любом артефакте экрана
 * в коде) или из контекстного меню дерева проекта (правый клик на XML-лейауте или
 * Kotlin-файле, принадлежащем экрану).
 *
 * Сценарий:
 *   1. Определяем имя текущего экрана по кликнутому PSI-элементу.
 *   2. Показываем [RenameScreenDialog] → пользователь вводит новое имя в PascalCase.
 *   3. [ScreenElementsFinder.findScreenElements] находит все связанные артефакты.
 *   4. [MultiRenameProcessor.queue] переименовывает их все как одну отменяемую команду.
 */
class RenameScreenAction : AnAction() {

    // PSI-поиск в update() только читает данные → безопасно на фоновом потоке.
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val screenName = element?.let { ScreenElementsFinder.resolveScreenName(it) }
        e.presentation.isEnabledAndVisible = screenName != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Защита: действие требует готового индекса проекта.
        if (DumbService.isDumb(project)) {
            DumbService.getInstance(project)
                .showDumbModeNotification("Переименование экрана требует завершения индексирования.")
            return
        }

        val element = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return
        val oldName = ScreenElementsFinder.resolveScreenName(element) ?: return

        // Показываем диалог переименования.
        val dialog = RenameScreenDialog(project, oldName)
        if (!dialog.showAndGet()) return   // пользователь отменил
        val newName = dialog.newScreenName

        // Находим все связанные артефакты (Fragment, Presenter, Contract, лейаут, …).
        val renames = ScreenElementsFinder.findScreenElements(project, oldName, newName)

        if (renames.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "Артефакты экрана '$oldName' не найдены.\n" +
                    "Убедитесь, что вы кликнули на Fragment, Presenter, Contract " +
                    "или файл лейаута экрана.",
                "Переименование экрана"
            )
            return
        }

        // Ищем использования на фоновом потоке, применяем переименования на EDT
        // в одном WriteCommandAction. Позволяет избежать вызова BaseRefactoringProcessor.run(),
        // который вызывает RefactoringEventListeners и провоцирует
        // DaemonMemorySettings.init → runBlockingCancellable на EDT.
        MultiRenameProcessor(project, renames, oldName, newName).queue()
    }
}
