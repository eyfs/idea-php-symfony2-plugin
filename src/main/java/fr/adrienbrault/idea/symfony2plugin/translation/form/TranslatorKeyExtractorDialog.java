package fr.adrienbrault.idea.symfony2plugin.translation.form;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import fr.adrienbrault.idea.symfony2plugin.Symfony2Icons;
import fr.adrienbrault.idea.symfony2plugin.action.comparator.PsiWeightListComparator;
import fr.adrienbrault.idea.symfony2plugin.action.dict.TranslationFileModel;
import fr.adrienbrault.idea.symfony2plugin.translation.dict.TranslationUtil;
import fr.adrienbrault.idea.symfony2plugin.util.SymfonyBundleUtil;
import fr.adrienbrault.idea.symfony2plugin.util.dict.SymfonyBundle;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLFile;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class TranslatorKeyExtractorDialog extends JDialog {

    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField textTranslationKey;
    private JTextField translationNote;
    private JPanel panelTableView;
    private JCheckBox checkNavigateTo;
    private JLabel keyError;

    private final ListTableModel<TranslationFileModel> listTableModel;
    private final OnOkCallback okCallback;

    private final Project project;
    private final PsiFile fileContext;

    public TranslatorKeyExtractorDialog(@NotNull Project project, @NotNull PsiFile fileContext, @NotNull Collection<String> domains, @Nullable String defaultKey, @NotNull String defaultDomain, @NotNull OnOkCallback okCallback) {

        this.project = project;
        this.fileContext = fileContext;
        this.okCallback = okCallback;

        if(defaultKey != null) {
            textTranslationKey.setText(defaultKey);
        }

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(e -> onOK());

        buttonCancel.addActionListener(e -> onCancel());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


        listTableModel = new ListTableModel<>(
            new IconColumn(),
            new PathNameColumn(),
            new FileNameColumn(),
            new BooleanColumn("Create")
        );

        filterList(defaultDomain);

        TableView<TranslationFileModel> tableView = new TableView<>();
        tableView.setModelAndUpdateColumns(listTableModel);

        panelTableView.add(ToolbarDecorator.createDecorator(tableView)
            .disableAddAction()
            .disableDownAction()
            .disableRemoveAction()
            .disableUpDownActions()
            .createPanel()
        );

        textTranslationKey.selectAll();

        setupKeyFieldListener();
        checkForExistingKey();
    }

    private void filterList(String domainName) {

        // clear list no all*() method?
        while(this.listTableModel.getRowCount() > 0) {
            this.listTableModel.removeRow(0);
        }

        // we only support yaml files right now
        // filter on PsiFile instance
        Collection<PsiFile> domainPsiFilesYaml = TranslationUtil.getDomainPsiFiles(this.project, domainName).stream()
            .filter(domainPsiFile -> domainPsiFile instanceof YAMLFile || TranslationUtil.isSupportedXlfFile(domainPsiFile))
            .collect(Collectors.toCollection(ArrayList::new));

        this.listTableModel.addRows(this.getFormattedFileModelList(domainPsiFilesYaml));

        // only one domain; fine preselect it
        if(this.listTableModel.getRowCount() == 1) {
            this.listTableModel.getItem(0).setEnabled(true);
        }

    }

    private void onOK() {
        String text = textTranslationKey.getText();

        if (TranslationUtil.hasTranslationKey(this.project, text)) {
            // key already exists
            return;
        }

        if(StringUtils.isNotBlank(text)) {
            List<TranslationFileModel> psiFiles = new ArrayList<>();
            for(TranslationFileModel translationFileModel: listTableModel.getItems()) {
                if(translationFileModel.isEnabled()) {
                    psiFiles.add(translationFileModel);
                }
            }

            String domain = text.substring(0, text.indexOf('.'));

            if(psiFiles.size() > 0) {
                okCallback.onClick(psiFiles, text, domain, translationNote.getText(), checkNavigateTo.isSelected());
                dispose();
                return;
            }

        }

        dispose();
    }

    private void onCancel() {
        dispose();
    }

    private void setupKeyFieldListener() {
        textTranslationKey.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                checkForExistingKey();
            }
            public void removeUpdate(DocumentEvent e) {
                checkForExistingKey();
            }
            public void insertUpdate(DocumentEvent e) {
                checkForExistingKey();
            }
        });
    }

    private void checkForExistingKey() {
        String text = textTranslationKey.getText();

        if (TranslationUtil.hasTranslationKey(this.project, text)) {
            keyError.setText("Key already exists");
        } else {
            keyError.setText("");
        }
    }

    private class FileNameColumn extends ColumnInfo<TranslationFileModel, String> {

        public FileNameColumn() {
            super("Name");
        }

        @Nullable
        @Override
        public String valueOf(TranslationFileModel domainModel) {
            return domainModel.getPsiFile().getName();
        }

        public int getWidth(JTable table) {
            return 190;
        }

    }

    private class PathNameColumn extends ColumnInfo<TranslationFileModel, String> {

        public PathNameColumn() {
            super("Path");
        }

        @Nullable
        @Override
        public String valueOf(TranslationFileModel domainModel) {

            if(domainModel.getSymfonyBundle() != null) {
                return domainModel.getSymfonyBundle().getName();
            }

            String relative = domainModel.getRelativePath();
            if(relative != null) {
                return relative;
            }

            return domainModel.getPsiFile().getName();
        }

    }

    private static class BooleanColumn extends ColumnInfo<TranslationFileModel, Boolean>
    {
        public BooleanColumn(String name) {
            super(name);
        }

        @Nullable
        @Override
        public Boolean valueOf(TranslationFileModel domainModel) {
            return domainModel.isEnabled();
        }

        public boolean isCellEditable(TranslationFileModel groupItem)
        {
            return true;
        }

        public void setValue(TranslationFileModel domainModel, Boolean value){
            domainModel.setEnabled(value);
        }

        public Class getColumnClass()
        {
            return Boolean.class;
        }

        public int getWidth(JTable table) {
            return 50;
        }
    }

    private class IconColumn extends ColumnInfo<TranslationFileModel, Icon> {

        public IconColumn() {
            super("");
        }

        @Nullable
        @Override
        public Icon valueOf(TranslationFileModel modelParameter) {

            if(modelParameter.isBoldness()) {
                return Symfony2Icons.BUNDLE;
            }

            return modelParameter.getPsiFile().getIcon(0);
        }

        public java.lang.Class getColumnClass() {
            return ImageIcon.class;
        }

        @Override
        public int getWidth(JTable table) {
            return 32;
        }

    }

    private List<TranslationFileModel> getFormattedFileModelList(Collection<PsiFile> psiFiles) {

        SymfonyBundleUtil symfonyBundleUtil = new SymfonyBundleUtil(this.project);
        final SymfonyBundle symfonyBundle = symfonyBundleUtil.getContainingBundle(fileContext);

        List<TranslationFileModel> psiFilesSorted = new ArrayList<>();
        for(PsiFile psiFile: psiFiles) {
            TranslationFileModel psiWeightList = new TranslationFileModel(psiFile);

            if(symfonyBundle != null && symfonyBundle.isInBundle(psiFile)) {
                psiWeightList.setSymfonyBundle(symfonyBundle);
                psiWeightList.setBoldness(true);
                psiWeightList.addWeight(2);
            } else {
                psiWeightList.setSymfonyBundle(symfonyBundleUtil.getContainingBundle(psiFile));
            }

            String relativePath = psiWeightList.getRelativePath();
            if(relativePath != null && (relativePath.startsWith("src") || relativePath.startsWith("app"))) {
                psiWeightList.addWeight(1);
            }

            psiFilesSorted.add(psiWeightList);
        }

        psiFilesSorted.sort(new PsiWeightListComparator());

        return psiFilesSorted;
    }


    public interface OnOkCallback {
        void onClick(List<TranslationFileModel> files, String keyName, String domain, String note, boolean navigateTo);
    }

}
