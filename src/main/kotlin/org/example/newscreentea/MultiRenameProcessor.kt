package org.example.newscreentea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.impl.RefactoringTransaction
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.usageView.UsageInfo

/**
 * Переименовывает все элементы из [renames] как единую, отменяемую операцию.
 *
 * Модель потоков
 * --------------
 * В Android Studio 2023.2+ (IntelliJ 233+) BaseRefactoringProcessor.run() синхронно
 * вызывает RefactoringEventListeners на EDT. Интеграция Gradle в Android Studio слушает
 * их и вызывает runBlockingCancellable() с EDT, что запрещено в новой корутинной
 * инфраструктуре → IllegalStateException.
 *
 * Обход: не использовать BaseRefactoringProcessor вообще:
 *   1. [ProgressManager.run(Task.Backgroundable)] → поиск использований на фоновом потоке (без проблем с EDT).
 *   2. [WriteCommandAction.runWriteCommandAction] → применение переименований на EDT в ОДИН шаг отмены.
 *
 * [RenameProcessor] по-прежнему используется для каждого элемента, чтобы ссылки
 * по всему проекту обновлялись корректно (Kotlin, Java, XML, ресурсы и т.д.).
 */
class MultiRenameProcessor(
    private val project: Project,
    private val renames: Map<PsiNamedElement, String>,
    private val oldScreenName: String,
    private val newScreenName: String
) {

    /**
     * Расширяет видимость protected-методов PSI у [RenameProcessor], чтобы вызывать
     * их напрямую, минуя публичный поток [RenameProcessor.run]
     * (который вызвал бы RefactoringEventListeners на EDT).
     */
    private class AccessibleRenameProcessor(
        project: Project,
        element: PsiElement,
        newName: String
    ) : RenameProcessor(
        project, element, newName,
        /* isSearchInComments = */ false,
        /* isSearchTextOccurrences = */ false
    ) {
        // Исправление NPE: myTransaction равен null при обходе BaseRefactoringProcessor.doRefactoring().
        // getTransaction() вызывается внутри performRefactoring() для получения слушателей элементов;
        // возвращаем пустую транзакцию — слушатели пропускаются, но переименования выполняются.
        override fun getTransaction(): RefactoringTransaction = object : RefactoringTransaction {
            override fun getElementListener(element: PsiElement): RefactoringElementListener =
                RefactoringElementListener.DEAF

            override fun commit() {}
        }

        override fun findUsages(): Array<UsageInfo> = super.findUsages()

        override fun performRefactoring(usages: Array<UsageInfo>) =
            super.performRefactoring(usages)
    }

    /**
     * Ставит переименование в очередь:
     *  - Показывает модальный прогресс-бар во время поиска использований (фоновый поток).
     *  - По завершении применяет все переименования в одном WriteCommandAction на EDT.
     *
     * Вызывать с EDT (например, внутри [AnAction.actionPerformed]).
     */
    fun queue() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Переименование экрана '$oldScreenName' → '$newScreenName'",
            /* canBeCancelled = */ false
        ) {
            // Заполняется на фоновом потоке, потребляется на EDT.
            private var usagesPerProcessor:
                    List<Pair<AccessibleRenameProcessor, Array<UsageInfo>>> = emptyList()

            // RenameProcessor переименовывает объявление класса и все его ссылки, но НЕ
            // переименовывает физический .kt-файл с этим классом. Сохраняем ссылки VirtualFile
            // (стабильны при мутациях PSI) и переименовываем файлы после переименования классов.
            private var fileRenames: List<Pair<VirtualFile, String>> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Поиск использований…"

                // PSI-операции чтения (включая findUsages) должны быть обёрнуты
                // в read action при выполнении на фоновом потоке.
                ApplicationManager.getApplication().runReadAction {
                    usagesPerProcessor = renames.map { (element, newName) ->
                        val proc = AccessibleRenameProcessor(project, element, newName)
                        proc to proc.findUsages()
                    }

                    // Собираем переименования файлов через VirtualFile (стабильны при мутациях PSI).
                    // Охватывает как .kt-файлы исходников, так и .xml-файлы лейаутов.
                    // Файлы из build/ исключаются — они будут пересозданы AGP.
                    // Дедупликация по пути на случай, если несколько классов из одного файла.
                    val oldSnake = ScreenNameUtils.pascalToSnake(oldScreenName)
                    val newSnake = ScreenNameUtils.pascalToSnake(newScreenName)
                    val seen = mutableSetOf<String>()
                    fileRenames = renames.entries.mapNotNull { (element, _) ->
                        val vFile: VirtualFile = when (element) {
                            is PsiFile -> element.virtualFile
                            else -> element.containingFile?.virtualFile
                        } ?: return@mapNotNull null
                        // Пропускаем файлы из директории build/ (например, сгенерированные binding-классы).
                        if (vFile.path.contains("/build/")) return@mapNotNull null
                        if (!seen.add(vFile.path)) return@mapNotNull null  // уже в очереди
                        val newFileName = when {
                            vFile.name.endsWith(".kt") -> vFile.name.replace(oldScreenName, newScreenName)
                            vFile.name.endsWith(".xml") -> vFile.name.replace(oldSnake, newSnake)
                            else -> return@mapNotNull null
                        }
                        if (newFileName == vFile.name) return@mapNotNull null  // имя не изменилось
                        vFile to newFileName
                    }
                }
            }

            override fun onSuccess() {
                // onSuccess() всегда вызывается на EDT после завершения run().
                // Применяем все переименования в ОДНОЙ отменяемой команде → один шаг Ctrl+Z.
                WriteCommandAction.runWriteCommandAction(
                    project,
                    "Переименование экрана '$oldScreenName' → '$newScreenName'",
                    /* groupID = */ null,
                    {
                        // Шаг 1: переименовываем объявления классов и обновляем все их ссылки.
                        usagesPerProcessor.forEach { (proc, usages) ->
                            proc.performRefactoring(usages)
                        }
                        // Шаг 2: переименовываем физические .kt / .xml файлы через VirtualFile.
                        fileRenames.forEach { (vFile, newName) ->
                            try {
                                vFile.rename(null, newName)
                            } catch (_: java.io.IOException) { /* лучшее усилие */
                            }
                        }

                        // Шаг 3: исправляем имя ресурса лейаута и имя класса View Binding
                        // внутри файла Fragment.
                        //
                        // RenameProcessor НЕ обновляет их, потому что:
                        //  • R.layout.fragment_xxx — это ссылка на *сгенерированное* поле R,
                        //    а не на сам XML PsiFile — она невидима для поиска использований файла.
                        //  • FragmentXxxBinding может отсутствовать в PSI-индексе, если проект
                        //    не был синхронизирован после последнего изменения лейаута.
                        //
                        // После шагов 1+2 .kt-файл Fragment уже содержит новое имя класса,
                        // но в нём ещё остаются старые строки ресурса/binding — патчим их
                        // прямым редактированием документа (безопасно: оба паттерна очень специфичны).
                        val fragmentVFile = fileRenames
                            .firstOrNull { (_, n) -> n.endsWith("Fragment.kt") }?.first
                        val fdm = FileDocumentManager.getInstance()
                        val pdm = PsiDocumentManager.getInstance(project)

                        // Шаг 3а: исправляем имя ресурса лейаута и имя класса View Binding
                        // только внутри файла Fragment.
                        if (fragmentVFile != null) {
                            val doc = fdm.getDocument(fragmentVFile)
                            if (doc != null) {
                                // Разблокируем документ, заблокированный ожидающими PSI-операциями из шага 1.
                                pdm.doPostponedOperationsAndUnblockDocument(doc)
                                val oldLayout = ScreenNameUtils.toLayoutFileName(oldScreenName)
                                    .removeSuffix(".xml")          // "fragment_old_name"
                                val newLayout = ScreenNameUtils.toLayoutFileName(newScreenName)
                                    .removeSuffix(".xml")          // "fragment_new_name"
                                val oldBinding = ScreenNameUtils.toBindingClassName(oldScreenName)
                                val newBinding = ScreenNameUtils.toBindingClassName(newScreenName)

                                val oldText = doc.text
                                val newText = oldText
                                    .replace(oldBinding, newBinding)  // сначала более специфичное
                                    .replace(oldLayout, newLayout)
                                if (newText != oldText) doc.replaceString(0, doc.textLength, newText)
                            }
                        }

                        // Шаг 3б: исправляем объявление `package` в каждом переименованном .kt-файле.
                        // RenameProcessor для PsiDirectory должен это делать, но документы
                        // оказываются заблокированы после мутаций PSI из шага 1, и обновление
                        // молча пропускается. Патчим явно для каждого файла пакета экрана.
                        val oldFolder = ScreenNameUtils.toFolderName(oldScreenName) // "oldscreen"
                        val newFolder = ScreenNameUtils.toFolderName(newScreenName) // "newscreen"
                        if (oldFolder != newFolder) {
                            fileRenames
                                .filter { (_, n) -> n.endsWith(".kt") }
                                .forEach { (vFile, _) ->
                                    val doc = fdm.getDocument(vFile) ?: return@forEach
                                    pdm.doPostponedOperationsAndUnblockDocument(doc)
                                    val text = doc.text
                                    // ".$oldFolder" ограничивает замену сегментом пакета,
                                    // предотвращая случайные совпадения в других идентификаторах.
                                    val updated = text.replace(".$oldFolder", ".$newFolder")
                                    if (updated != text) doc.replaceString(0, doc.textLength, updated)
                                }
                        }
                    }
                )
            }

            override fun onThrowable(error: Throwable) {
                // Показываем ошибку как уведомление IDE вместо молчаливого сбоя.
                com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("Rename Screen")
                    ?.createNotification(
                        "Ошибка переименования экрана",
                        error.localizedMessage ?: error.javaClass.simpleName,
                        com.intellij.notification.NotificationType.ERROR
                    )
                    ?.notify(project)
                    ?: super.onThrowable(error) // запасной вариант обработки ошибки
            }
        })
    }
}
