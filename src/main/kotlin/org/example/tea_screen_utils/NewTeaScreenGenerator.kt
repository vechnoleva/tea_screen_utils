package org.example.tea_screen_utils

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory

class NewTeaScreenGenerator(
    private val project: Project,
    private val selectedDir: PsiDirectory,
    private val screenName: String,
    private val hasParams: Boolean,
    private val hasRecyclerView: Boolean,
    private val isBottomSheet: Boolean,
    private val isClosable: Boolean,
    private val hasTitledToolbar: Boolean
) {
    private val screenNameLower = screenName.lowercase()
    private val screenNameSnake = ScreenNameUtils.pascalToSnake(screenName)
    private val needsParams = hasParams || (isBottomSheet && !isClosable)
    private val packageFromFolder: String

    init {
        val parentPkg = JavaDirectoryService.getInstance()
            .getPackage(selectedDir)?.qualifiedName ?: ""
        packageFromFolder = if (parentPkg.isNotEmpty()) "$parentPkg.$screenNameLower" else screenNameLower
    }

    fun generate() {
        WriteCommandAction.runWriteCommandAction(
            project,
            "New Tea Screen '$screenName'",
            null,
            {
                // Create screen directory inside selected dir
                val screenDir = selectedDir.createSubdirectory(screenNameLower)

                // Always-created files
                createFile(screenDir, "${screenName}Fragment.kt", fragmentContent())
                createFile(screenDir, "${screenName}Contract.kt", contractContent())
                createFile(screenDir, "${screenName}Presenter.kt", presenterContent())
                createFile(screenDir, "${screenName}DI.kt", diContent())

                // RecyclerView optional files
                if (hasRecyclerView) {
                    val adapterDir = screenDir.createSubdirectory("adapter")
                    createFile(adapterDir, "${screenName}Adapter.kt", adapterContent())
                    val mapperDir = screenDir.createSubdirectory("mapper")
                    createFile(mapperDir, "${screenName}Mapper.kt", mapperContent())
                    createFile(mapperDir, "${screenName}MapperImpl.kt", mapperImplContent())
                }

                // Params optional file
                if (needsParams) {
                    val modelDir = screenDir.createSubdirectory("model")
                    createFile(modelDir, "${screenName}Params.kt", paramsContent())
                }

                // Layout XML
                createLayoutFile()

                // Modify existing project files
                modifyAppComponent()
                modifyScreens()

                // Open Fragment in editor
                screenDir.findFile("${screenName}Fragment.kt")?.virtualFile?.let { vf ->
                    FileEditorManager.getInstance(project).openFile(vf, true)
                }
            }
        )
    }

    // ─── File creation ────────────────────────────────────────────────────────

    private fun createFile(dir: PsiDirectory, name: String, content: String) {
        val vf = dir.virtualFile.createChildData(this, name)
        VfsUtil.saveText(vf, content)
    }

    private fun createLayoutFile() {
        val layoutPath = "${project.basePath}/app/src/main/res/layout"
        val layoutDir = LocalFileSystem.getInstance().findFileByPath(layoutPath)
            ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(layoutPath)
            ?: return
        val vf = layoutDir.createChildData(this, "fragment_${screenNameSnake}.xml")
        VfsUtil.saveText(vf, layoutContent())
    }

    // ─── Existing file modifiers ──────────────────────────────────────────────

    private fun modifyAppComponent() {
        val path = "${project.basePath}/app/src/main/java/ru/may24/app/di/AppComponent.kt"
        val vf = LocalFileSystem.getInstance().findFileByPath(path) ?: return
        val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return

        val imports = listOf(
            "import $packageFromFolder.${screenName}Component",
            "import $packageFromFolder.${screenName}Module"
        )
        val method = "    fun plus(module: ${screenName}Module): ${screenName}Component"

        var text = doc.text
        text = addImports(text, imports)
        text = insertBeforeLastBrace(text, "\n$method\n")
        doc.replaceString(0, doc.textLength, text)
        FileDocumentManager.getInstance().saveDocument(doc)
    }

    private fun modifyScreens() {
        val path = "${project.basePath}/app/src/main/java/ru/may24/app/ui/navigation/Screens.kt"
        val vf = LocalFileSystem.getInstance().findFileByPath(path) ?: return
        val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return

        val imports = mutableListOf("import $packageFromFolder.${screenName}Fragment")
        if (needsParams) imports += "import $packageFromFolder.model.${screenName}Params"

        var text = doc.text
        text = addImports(text, imports)
        text = insertBeforeLastBrace(text, "\n${buildScreenEntry()}\n")
        doc.replaceString(0, doc.textLength, text)
        FileDocumentManager.getInstance().saveDocument(doc)
    }

    // ─── Template generators ──────────────────────────────────────────────────

    private fun fragmentContent(): String = buildString {
        appendLine("package $packageFromFolder")
        appendLine()

        // Imports
        appendLine("import android.os.Bundle")
        if (isBottomSheet) appendLine("import android.app.Dialog")
        appendLine("import android.view.LayoutInflater")
        appendLine("import android.view.View")
        appendLine("import android.view.ViewGroup")
        if (needsParams) {
            appendLine("import androidx.core.os.bundleOf")
            appendLine("import ru.may24.app.core.extensions.parcelable")
        }
        if (hasRecyclerView) {
            appendLine("import androidx.recyclerview.widget.LinearLayoutManager")
            appendLine("import androidx.recyclerview.widget.LinearLayoutManager.VERTICAL")
            appendLine("import ru.may24.uikit.ui.adapter.ListViewModel")
            appendLine("import ru.may24.uikit.ui.adapter.listener.ListItemClickListener")
        }
        appendLine("import by.kirich1409.viewbindingdelegate.viewBinding")
        if (hasTitledToolbar) appendLine("import ru.may24.uikit.R as UiKitR")
        if (isBottomSheet) {
            appendLine("import com.google.android.material.bottomsheet.BottomSheetBehavior")
            appendLine("import com.google.android.material.bottomsheet.BottomSheetDialog")
        }
        appendLine("import moxy.presenter.InjectPresenter")
        appendLine("import moxy.presenter.ProvidePresenter")
        appendLine("import ru.may24.app.R")
        appendLine("import ru.may24.app.databinding.Fragment${screenName}Binding")
        if (isBottomSheet) {
            appendLine("import ru.may24.app.ui.fragment.base.BaseBottomSheetFragment")
            appendLine("import ru.may24.app.ui.navigation.Screens")
        } else {
            appendLine("import ru.may24.app.ui.fragment.base.BaseFragment")
        }
        if (hasParams) appendLine("import $packageFromFolder.model.${screenName}Params")
        if (hasRecyclerView) appendLine("import $packageFromFolder.adapter.${screenName}Adapter")
        if (isBottomSheet) appendLine("import ru.terrakok.cicerone.Screen")
        appendLine("import javax.inject.Inject")
        appendLine("import javax.inject.Provider")
        appendLine()

        // Class declaration
        val base = if (isBottomSheet) "BaseBottomSheetFragment" else "BaseFragment"
        appendLine("class ${screenName}Fragment : $base(), ${screenName}Contract.View {")
        appendLine()
        appendLine("    @InjectPresenter")
        appendLine("    lateinit var presenter: ${screenName}Contract.Presenter")
        appendLine()
        appendLine("    @Inject")
        appendLine("    lateinit var presenterProvider: Provider<${screenName}Contract.Presenter>")
        appendLine()
        appendLine("    private val binding by viewBinding(Fragment${screenName}Binding::bind)")
        if (isBottomSheet) {
            appendLine()
            appendLine("    override var screen: Screen? = null")
        }
        if (hasRecyclerView) {
            appendLine()
            appendLine("    @Inject")
            appendLine("    lateinit var adapter: ${screenName}Adapter")
        }
        appendLine()

        // companion object
        appendLine("    //region ==================== Fragment creation ====================")
        appendLine()
        appendLine("    companion object {")
        when {
            isBottomSheet && needsParams -> {
                appendLine("        private const val KEY_PARAMS = \"KEY_PARAMS\"")
                appendLine()
                appendLine("        fun newInstance(screen: Screens.${screenName}Screen, params: ${screenName}Params): ${screenName}Fragment {")
                appendLine("            val fragment = ${screenName}Fragment().apply {")
                appendLine("                this.screen = screen")
                appendLine("            }")
                appendLine("            fragment.arguments = bundleOf(KEY_PARAMS to params)")
                appendLine("            return fragment")
                appendLine("        }")
            }
            isBottomSheet -> {
                appendLine("        fun newInstance(screen: Screens.${screenName}Screen): ${screenName}Fragment {")
                appendLine("            return ${screenName}Fragment().apply {")
                appendLine("                this.screen = screen")
                appendLine("            }")
                appendLine("        }")
            }
            hasParams -> {
                appendLine("        private const val KEY_PARAMS = \"KEY_PARAMS\"")
                appendLine()
                appendLine("        fun newInstance(params: ${screenName}Params): ${screenName}Fragment {")
                appendLine("            val fragment = ${screenName}Fragment()")
                appendLine("            fragment.arguments = bundleOf(KEY_PARAMS to params)")
                appendLine("            return fragment")
                appendLine("        }")
            }
            else -> {
                appendLine("        fun newInstance(): ${screenName}Fragment {")
                appendLine("            return ${screenName}Fragment()")
                appendLine("        }")
            }
        }
        appendLine("    }")
        appendLine()
        appendLine("    //endregion")
        appendLine()

        // Lifecycle
        appendLine("    //region ==================== Lifecycle ====================")
        appendLine()
        appendLine("    override fun onCreate(savedInstanceState: Bundle?) {")
        appendLine("        configureDI()")
        appendLine("        super.onCreate(savedInstanceState)")
        appendLine("    }")
        appendLine()
        appendLine("    override fun onCreateView(")
        appendLine("        inflater: LayoutInflater,")
        appendLine("        container: ViewGroup?,")
        appendLine("        savedInstanceState: Bundle?")
        // Bottom Sheet returns View? (nullable) per spec
        if (isBottomSheet) {
            appendLine("    ): View? {")
        } else {
            appendLine("    ): View {")
        }
        appendLine("        return inflater.inflate(R.layout.fragment_${screenNameSnake}, container, false)")
        appendLine("    }")
        appendLine()
        appendLine("    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {")
        appendLine("        super.onViewCreated(view, savedInstanceState)")
        appendLine("        initUI()")
        appendLine("    }")
        if (isBottomSheet) {
            appendLine()
            appendLine("    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {")
            if (isClosable) {
                appendLine("        val dialog = super.onCreateDialog(savedInstanceState)")
                appendLine("        if (dialog is BottomSheetDialog) {")
                appendLine("            dialog.behavior.skipCollapsed = true")
                appendLine("            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED")
                appendLine("        }")
                appendLine("        return dialog")
            } else {
                appendLine("        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog")
                appendLine("        val isClosable = requireNotNull(")
                appendLine("            requireArguments().parcelable<${screenName}Params>(KEY_PARAMS)")
                appendLine("        ).isClosable")
                appendLine("        if (!isClosable) {")
                appendLine("            lockDialog(dialog)")
                appendLine("        }")
                appendLine("        return dialog")
            }
            appendLine("    }")
            appendLine()
            appendLine("    override fun onDestroy() {")
            appendLine("        screen?.let { presenter.removeBottomSheetFromQueue(it) }")
            appendLine("        super.onDestroy()")
            appendLine("    }")
        }
        appendLine()
        appendLine("    //endregion")
        appendLine()

        // UI handlers
        if (hasRecyclerView || hasTitledToolbar) {
            appendLine("    //region ==================== UI handlers ====================")
            appendLine()
            if (hasRecyclerView) {
                appendLine("    private val itemClickListener = object : ListItemClickListener {")
                appendLine("        override fun onListItemClicked(delegateViewModel: ListViewModel) {")
                appendLine("            presenter.onItemClicked(delegateViewModel)")
                appendLine("        }")
                appendLine("    }")
                if (hasTitledToolbar) appendLine()
            }
            if (hasTitledToolbar) {
                appendLine("    val btnBackClickListener = View.OnClickListener { presenter.onBackButtonClicked() }")
            }
            appendLine()
            appendLine("    //endregion")
            appendLine()
        }

        // Contract.View
        appendLine("    //region ==================== Contract.View ====================")
        appendLine()
        if (hasRecyclerView) {
            appendLine("    override fun showItemList(list: List<ListViewModel>) {")
            appendLine("        adapter.swapItems(list)")
            appendLine("    }")
            appendLine()
        }
        appendLine("    //endregion")
        appendLine()

        // DI
        appendLine("    //region ==================== DI ====================")
        appendLine()
        appendLine("    private fun configureDI() {")
        if (hasParams) {
            appendLine("        val params = requireNotNull(requireArguments().parcelable<${screenName}Params>(KEY_PARAMS))")
        }
        val moduleArgs = mutableListOf<String>()
        if (hasParams) moduleArgs += "params"
        if (hasRecyclerView) moduleArgs += "itemClickListener"
        appendLine("        val component = getAppComponent().plus(${screenName}Module(${moduleArgs.joinToString(", ")}))")
        appendLine("        component.inject(this)")
        appendLine("    }")
        appendLine()
        appendLine("    @ProvidePresenter")
        appendLine("    internal fun providePresenter() = presenterProvider.get()")
        appendLine()
        appendLine("    //endregion")
        appendLine()

        // UI
        appendLine("    //region ==================== UI ====================")
        appendLine()
        appendLine("    private fun initUI() = with(binding) {")
        if (hasTitledToolbar) {
            appendLine("        setupToolbar(")
            appendLine("            binding.root,")
            appendLine("            \"$screenName\",")
            appendLine("            UiKitR.drawable.ic_back_grey_32,")
            appendLine("            backButtonEnabled = true,")
            appendLine("            btnBackClickListener")
            appendLine("        )")
        }
        if (hasRecyclerView) {
            appendLine("        recyclerView.layoutManager = LinearLayoutManager(context, VERTICAL, false)")
            appendLine("        recyclerView.adapter = adapter")
        }
        appendLine("    }")
        appendLine()
        appendLine("    //endregion")
        append("}")
    }

    private fun contractContent(): String = buildString {
        appendLine("package $packageFromFolder")
        appendLine()
        appendLine("import moxy.MvpView")
        appendLine("import moxy.viewstate.strategy.AddToEndSingleStrategy")
        appendLine("import moxy.viewstate.strategy.StateStrategyType")
        appendLine("import ru.may24.app.core.ui.fragment.base.BaseDisposablePresenter")
        if (hasRecyclerView) appendLine("import ru.may24.uikit.ui.adapter.ListViewModel")
        appendLine()
        appendLine("interface ${screenName}Contract {")
        appendLine()
        appendLine("    @StateStrategyType(value = AddToEndSingleStrategy::class)")
        appendLine("    interface View : MvpView {")
        if (hasRecyclerView) {
            appendLine("        fun showItemList(list: List<ListViewModel>)")
        }
        appendLine("    }")
        appendLine()
        if (hasRecyclerView || hasTitledToolbar) {
            appendLine("    abstract class Presenter : BaseDisposablePresenter<View>() {")
            if (hasRecyclerView) appendLine("        abstract fun onItemClicked(viewModel: ListViewModel)")
            if (hasTitledToolbar) appendLine("        abstract fun onBackButtonClicked()")
            appendLine("    }")
        } else {
            appendLine("    abstract class Presenter : BaseDisposablePresenter<View>()")
        }
        append("}")
    }

    private fun presenterContent(): String = buildString {
        appendLine("package $packageFromFolder")
        appendLine()
        appendLine("import ru.terrakok.cicerone.Router")
        if (hasParams) appendLine("import $packageFromFolder.model.${screenName}Params")
        if (hasRecyclerView) {
            appendLine("import ru.may24.uikit.ui.adapter.ListViewModel")
            appendLine("import $packageFromFolder.mapper.${screenName}Mapper")
        }
        appendLine("import javax.inject.Inject")
        appendLine()
        appendLine("class ${screenName}Presenter @Inject constructor(")
        appendLine("    private val router: Router,")
        if (hasParams) appendLine("    private val params: ${screenName}Params,")
        if (hasRecyclerView) appendLine("    private val mapper: ${screenName}Mapper,")
        appendLine(") : ${screenName}Contract.Presenter() {")
        appendLine()
        appendLine("    //region ==================== MVP Presenter ====================")
        appendLine()
        appendLine("    //endregion")
        appendLine()
        appendLine("    //region ==================== ${screenName}Contract.Presenter ====================")
        appendLine()
        if (hasRecyclerView) {
            appendLine("    override fun onItemClicked(viewModel: ListViewModel) = Unit")
            appendLine()
        }
        if (hasTitledToolbar) {
            appendLine("    override fun onBackButtonClicked() = router.exit()")
            appendLine()
        }
        appendLine("    //endregion")
        append("}")
    }

    private fun diContent(): String = buildString {
        appendLine("package $packageFromFolder")
        appendLine()
        appendLine("import dagger.Module")
        appendLine("import dagger.Provides")
        appendLine("import dagger.Subcomponent")
        if (hasParams) appendLine("import $packageFromFolder.model.${screenName}Params")
        if (hasRecyclerView) {
            appendLine("import ru.may24.uikit.ui.adapter.listener.ListItemClickListener")
            appendLine("import $packageFromFolder.mapper.${screenName}Mapper")
            appendLine("import $packageFromFolder.mapper.${screenName}MapperImpl")
        }
        appendLine()
        appendLine("@Subcomponent(modules = [${screenName}Module::class])")
        appendLine("interface ${screenName}Component {")
        appendLine("    fun inject(fragment: ${screenName}Fragment)")
        appendLine("}")
        appendLine()
        appendLine("@Module")

        val ctorLines = mutableListOf<String>()
        if (hasParams) ctorLines += "    private val params: ${screenName}Params,"
        if (hasRecyclerView) ctorLines += "    private val listItemClickListener: ListItemClickListener,"

        when (ctorLines.size) {
            0 -> appendLine("class ${screenName}Module {")
            1 -> appendLine("class ${screenName}Module(${ctorLines[0].trim().removeSuffix(",")}) {")
            else -> {
                appendLine("class ${screenName}Module(")
                ctorLines.forEach { appendLine(it) }
                appendLine(") {")
            }
        }
        appendLine()
        appendLine("    @Provides")
        appendLine("    fun presenter(presenter: ${screenName}Presenter): ${screenName}Contract.Presenter {")
        appendLine("        return presenter")
        appendLine("    }")
        if (hasParams) {
            appendLine()
            appendLine("    @Provides")
            appendLine("    fun provideParams() = params")
        }
        if (hasRecyclerView) {
            appendLine()
            appendLine("    @Provides")
            appendLine("    fun provideListItemClickListener() = listItemClickListener")
            appendLine()
            appendLine("    @Provides")
            appendLine("    fun provideMapper(mapperImpl: ${screenName}MapperImpl): ${screenName}Mapper = mapperImpl")
        }
        append("}")
    }

    private fun adapterContent(): String = buildString {
        appendLine("package $packageFromFolder.adapter")
        appendLine()
        appendLine("import ru.may24.uikit.ui.adapter.DiffAdapter")
        appendLine("import javax.inject.Inject")
        appendLine()
        appendLine("class ${screenName}Adapter @Inject constructor() : DiffAdapter() {")
        appendLine("    init {")
        appendLine("        delegatesManager")
        appendLine("    }")
        append("}")
    }

    private fun mapperContent(): String = buildString {
        appendLine("package $packageFromFolder.mapper")
        appendLine()
        appendLine("import ru.may24.uikit.ui.adapter.ListViewModel")
        appendLine()
        appendLine("interface ${screenName}Mapper {")
        appendLine()
        appendLine("    fun mapToUI(): List<ListViewModel>")
        append("}")
    }

    private fun mapperImplContent(): String = buildString {
        appendLine("package $packageFromFolder.mapper")
        appendLine()
        appendLine("import ru.may24.uikit.ui.adapter.ListViewModel")
        appendLine("import javax.inject.Inject")
        appendLine()
        appendLine("class ${screenName}MapperImpl @Inject constructor() : ${screenName}Mapper {")
        appendLine()
        appendLine("    override fun mapToUI(): List<ListViewModel> {")
        appendLine("        val viewModels = mutableListOf<ListViewModel>()")
        appendLine("        return viewModels")
        appendLine("    }")
        append("}")
    }

    private fun paramsContent(): String = buildString {
        appendLine("package $packageFromFolder.model")
        appendLine()
        appendLine("import android.os.Parcelable")
        appendLine("import kotlinx.parcelize.Parcelize")
        appendLine()
        appendLine("@Parcelize")
        appendLine("data class ${screenName}Params(")
        if (hasParams) appendLine("    val someId: String = \"\",")
        if (isBottomSheet && !isClosable) appendLine("    val isClosable: Boolean,")
        append(") : Parcelable")
    }

    private fun layoutContent(): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        appendLine("<androidx.constraintlayout.widget.ConstraintLayout")
        appendLine("    xmlns:android=\"http://schemas.android.com/apk/res/android\"")
        appendLine("    xmlns:app=\"http://schemas.android.com/apk/res-auto\"")
        appendLine("    android:layout_width=\"match_parent\"")
        appendLine("    android:layout_height=\"match_parent\">")
        appendLine()
        if (hasTitledToolbar) {
            appendLine("    <include")
            appendLine("        android:id=\"@+id/toolbar\"")
            appendLine("        layout=\"@layout/titled_toolbar\"")
            appendLine("        android:layout_width=\"match_parent\"")
            appendLine("        android:layout_height=\"wrap_content\"")
            appendLine("        app:layout_constraintTop_toTopOf=\"parent\" />")
            appendLine()
        }
        if (hasRecyclerView) {
            val rvTopConstraint = if (hasTitledToolbar) "@id/toolbar" else "parent"
            val rvTopAttr = if (hasTitledToolbar) "toBottomOf" else "toTopOf"
            appendLine("    <androidx.recyclerview.widget.RecyclerView")
            appendLine("        android:id=\"@+id/recyclerView\"")
            appendLine("        android:layout_width=\"match_parent\"")
            appendLine("        android:layout_height=\"0dp\"")
            appendLine("        app:layout_constraintTop_${rvTopAttr}=\"$rvTopConstraint\"")
            appendLine("        app:layout_constraintBottom_toBottomOf=\"parent\"")
            appendLine("        app:layout_constraintStart_toStartOf=\"parent\"")
            appendLine("        app:layout_constraintEnd_toEndOf=\"parent\" />")
            appendLine()
        }
        append("</androidx.constraintlayout.widget.ConstraintLayout>")
    }

    private fun buildScreenEntry(): String = buildString {
        when {
            !isBottomSheet && !hasParams -> {
                appendLine("    class ${screenName}Screen : SupportAppScreen() {")
                appendLine("        override fun getFragment() = ${screenName}Fragment.newInstance()")
                append("    }")
            }
            !isBottomSheet && hasParams -> {
                appendLine("    @Parcelize")
                appendLine("    class ${screenName}Screen(val params: ${screenName}Params) : SupportAppScreen(), Parcelable {")
                appendLine("        override fun getFragment() = ${screenName}Fragment.newInstance(params)")
                append("    }")
            }
            isBottomSheet && !needsParams -> {
                appendLine("    class ${screenName}Screen : BottomSheetScreen() {")
                appendLine("        override fun getFragment(): Fragment {")
                appendLine("            return ${screenName}Fragment.newInstance(this)")
                appendLine("        }")
                append("    }")
            }
            else -> { // isBottomSheet && needsParams
                appendLine("    @Parcelize")
                appendLine("    class ${screenName}Screen(val params: ${screenName}Params) : BottomSheetScreen(), Parcelable {")
                appendLine("        override fun getFragment(): Fragment {")
                appendLine("            return ${screenName}Fragment.newInstance(this, params)")
                appendLine("        }")
                append("    }")
            }
        }
    }

    // ─── Text manipulation helpers ────────────────────────────────────────────

    private fun addImports(text: String, imports: List<String>): String {
        val newImports = imports.filter { !text.contains(it) }
        if (newImports.isEmpty()) return text
        val lines = text.lines().toMutableList()
        val lastImportIdx = lines.indexOfLast { it.startsWith("import ") }
        return if (lastImportIdx >= 0) {
            lines.addAll(lastImportIdx + 1, newImports)
            lines.joinToString("\n")
        } else {
            val pkgIdx = lines.indexOfFirst { it.startsWith("package ") }
            if (pkgIdx >= 0) {
                lines.add(pkgIdx + 1, "")
                lines.addAll(pkgIdx + 2, newImports)
                lines.joinToString("\n")
            } else {
                newImports.joinToString("\n") + "\n" + text
            }
        }
    }

    private fun insertBeforeLastBrace(text: String, content: String): String {
        val idx = text.lastIndexOf('}')
        return if (idx >= 0) text.substring(0, idx) + content + text.substring(idx)
        else text + content
    }
}
