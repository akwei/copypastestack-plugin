/*
 * Copyright 2014 Kay Stenschke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kstenschke.copypastestack;

import com.intellij.ide.UiActivity;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.kstenschke.copypastestack.Utils.UtilsArray;
import com.kstenschke.copypastestack.Utils.UtilsEnvironment;
import com.kstenschke.copypastestack.resources.ui.ToolWindowForm;
import org.apache.commons.lang.ArrayUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.Arrays;

public class ToolWindow extends SimpleToolWindowPanel {

    public final Project project;
    public ToolWindowForm form;

    /**
     * Constructor - initialize the tool window content
     *
     * @param   project    Idea project
     */
    public ToolWindow(Project project) {
        super(false);

        final ToolWindow toolWindow = this;
        this.project = project;

        form    = new ToolWindowForm();

        initItemsList();
        initToolbar();
        initWrap();
        initAdditionalOptions();

            // Add form into toolWindow
        add(form.getMainPanel(), BorderLayout.CENTER);
    }

    /**
     * @param   project     Idea Project
     * @return  Instance of AhnToolWindow
     */
    public static ToolWindow getInstance(Project project) {
        com.intellij.openapi.wm.ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CopyPasteStack");

        if( toolWindow != null ) {
            try {
                Content content = toolWindow.getContentManager().getContent(0);
                if( content != null ) {
                    JComponent toolWindowComponent = content.getComponent();
                    String canonicalName = toolWindowComponent.getClass().getCanonicalName();
                    if(canonicalName.endsWith("com.kstenschke.copypastestack.ToolWindow")) {
                        return (ToolWindow) toolWindowComponent;
                    }
                }
            } catch(Exception exception) {
                exception.printStackTrace();
            }
        }

        return null;
    }

    /**
     * @return  ToolWindow
     */
    public static ToolWindow getInstance() {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if( openProjects.length == 0 ) {
            return null;
        }

        Project project= openProjects[0];

        return getInstance(project);
    }

    private void initToolbar() {
        this.form.buttonRefresh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshClipboardList();
            }
        });
        this.form.buttonRefresh.addMouseListener( new MouseListenerBase(StaticTexts.INFO_REFRESH));

        this.form.buttonSortAlphabetical.addActionListener( new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sortClipboardListAlphabetical();
            }
        });
        this.form.buttonSortAlphabetical.addMouseListener(new MouseListenerBase(StaticTexts.INFO_SORT));

        this.form.buttonDelete.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onClickButtonDelete();
            }
        });
        this.form.buttonDelete.addMouseListener(new MouseListenerBase(StaticTexts.INFO_DELETE));

        this.form.buttonPaste.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onClickButtonPaste();
            }
        });
        this.form.buttonPaste.addMouseListener(new MouseListenerBase(StaticTexts.INFO_PASTE));
    }

    private void onClickButtonDelete() {
        Boolean hasSelection = ! this.form.clipItemsList.isSelectionEmpty();
        String[] items       = hasSelection ? getUnselectedItems() : new String[0];

        this.updateItemsList(items);
        Preferences.saveCopyItems(items);

        if( !hasSelection ) {
            this.refreshClipboardList();
        }
    }

    /**
     * @return  String[]
     */
    private String[] getUnselectedItems() {
        ListModel<String> listModel                     = this.form.clipItemsList.getModel();
        javax.swing.ListSelectionModel selectionModel   = this.form.clipItemsList.getSelectionModel();
        int[] selectedIndices                           = this.form.clipItemsList.getSelectedIndices();

        int amountItems             = listModel.getSize();
        String[] unselectedItems    = new String[ amountItems - selectedIndices.length ];
        int index = 0;
        for(int i=0; i< amountItems; i++ ) {
            if( !selectionModel.isSelectedIndex(i) ) {
                unselectedItems[ index ]    = listModel.getElementAt(i);
                index++;
            }
        }

        return unselectedItems;
    }

    private void onClickButtonPaste() {
        Boolean hasSelection = ! this.form.clipItemsList.isSelectionEmpty();
        int amountSelected   = ! hasSelection ? 0 : this.form.clipItemsList.getSelectedValuesList().size();
        Boolean focusEditor = this.form.checkBoxFocusEditor.isSelected();

        if( !hasSelection || amountSelected > 1 ) {
                // Insert multiple items
            String wrapBefore   = "";
            String wrapAfter    = "";
            String wrapDelimiter= "";
            if( this.form.checkBoxWrap.isSelected() ) {
                wrapBefore      = this.form.textFieldWrapBefore.getText();
                wrapAfter       = this.form.textFieldWrapBefore.getText();
                wrapDelimiter   = this.form.textFieldWrapDelimiter.getText();
            }

            ListModel<String> listModel                     = this.form.clipItemsList.getModel();
            javax.swing.ListSelectionModel selectionModel   = this.form.clipItemsList.getSelectionModel();

            int amountItems     = listModel.getSize();
            int amountInserted  = 0;
            for(int i=0; i< amountItems; i++ ) {
                if( selectionModel.isSelectedIndex(i) ) {
                    String currentItemText  = listModel.getElementAt(i);
                    UtilsEnvironment.insertInEditor(
                        wrapBefore + currentItemText + wrapAfter + (amountInserted + 1 < amountSelected ? wrapDelimiter : ""), focusEditor
                    );
                    amountInserted++;
                }
            }
        } else {
                // Insert selected item
            Object itemValue= this.form.clipItemsList.getSelectedValue();
            if( itemValue != null ) {
                String itemText = itemValue.toString();
                if( this.form.checkBoxWrap.isSelected() ) {
                    itemText    = this.form.textFieldWrapBefore.getText() + itemText + this.form.textFieldWrapBefore.getText();
                }
                UtilsEnvironment.insertInEditor(itemText, focusEditor);
            }
        }
    }

    private void sortClipboardListAlphabetical() {
        ListModel<String> listModel = this.form.clipItemsList.getModel();
        int amountItems = listModel.getSize();

        if( amountItems > 0 ) {
            String[] items   = new String[amountItems];
            int index = 0;
            for (int i = 0; i < amountItems; i++) {
                items[index] = listModel.getElementAt(i);
                index++;
            }
            if( items.length > 0 ) {
                Arrays.sort(items, String.CASE_INSENSITIVE_ORDER);
            }

            this.updateItemsList(items);
        }
    }

    /**
     * Refresh listed items from distinct merged sum of:
     * 1. previous clipboard items still in prefs
     * 2. current clipboard items
     */
    private void refreshClipboardList() {
        Transferable[] copiedItems = CopyPasteManager.getInstance().getAllContents();

        int amountItems     = getAmountStringItemsInTransferables(copiedItems);
        if( amountItems > 0 ) {
            String[] copyItemsList   = new String[amountItems];
            int index           = 0;
            for( Transferable currentItem : copiedItems) {
                if( currentItem.isDataFlavorSupported( DataFlavor.stringFlavor ) )  {
                    try {
                        String itemStr  = currentItem.getTransferData( DataFlavor.stringFlavor ).toString();
                        copyItemsList[index] = itemStr;
                        index++;
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            String[] copyItemsPref = Preferences.getItems();
            Object[] allItems       = ArrayUtils.addAll(copyItemsList, copyItemsPref);
            String[] itemsUnique    = UtilsArray.tidy(allItems, true, true);

            if( itemsUnique.length > 0 ) {
                this.updateItemsList(itemsUnique);
                Preferences.saveCopyItems(itemsUnique);
            }
        }
    }

    /**
     * @param items
     */
    private void updateItemsList(String[] items) {
        this.form.clipItemsList.setListData( items );
    }

    private void initItemsList() {
        Boolean isMac   = UtilsEnvironment.isMac();
        this.form.clipItemsList.setCellRenderer( new ListCellRendererCopyPasteStack(this.form.clipItemsList, false, isMac));
        this.updateItemsList( Preferences.getItems() );

        this.form.clipItemsList.addKeyListener( new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                Integer keyCode = e.getKeyCode();
                switch( keyCode ) {
                    case KeyEvent.VK_ENTER:
                    case KeyEvent.VK_SPACE:
                    onClickButtonPaste();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
        this.form.clipItemsList.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if( e.getClickCount() == 2) {
                    onClickButtonPaste();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
    }

    /**
     * @param   transferables
     * @return  int
     */
    private int getAmountStringItemsInTransferables(Transferable[] transferables) {
        int amount = 0;
        for( Transferable currentItem : transferables) {
            if( currentItem.isDataFlavorSupported( DataFlavor.stringFlavor ) )  {
                amount++;
            }
        }

        return amount;
    }

    private void initWrap() {
        Boolean isActiveWrap = Preferences.getIsActiveWrap();
        this.form.checkBoxWrap.setSelected( isActiveWrap );
        this.form.checkBoxWrap.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Boolean isActive = form.checkBoxWrap.isSelected();
                form.panelWrap.setVisible( isActive );
                if( isActive ) {
                    form.textFieldWrapBefore.requestFocusInWindow();
                }
                Preferences.saveIsActiveWrap(isActive);
            }
        });

        this.form.panelWrap.setVisible( isActiveWrap );

        this.form.textFieldWrapBefore.setText(Preferences.getWrapBefore());

        FocusListener focusListenerWrapComponents = new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {

            }

            @Override
            public void focusLost(FocusEvent e) {
                Preferences.saveWrapBefore( form.textFieldWrapBefore.getText() );
                Preferences.saveWrapAfter(form.textFieldWrapAfter.getText());
                Preferences.saveWrapDelimiter(form.textFieldWrapDelimiter.getText());
            }
        };

        this.form.textFieldWrapBefore.addFocusListener( focusListenerWrapComponents );

        this.form.textFieldWrapAfter.setText(Preferences.getWrapAfter());
        this.form.textFieldWrapAfter.addFocusListener(focusListenerWrapComponents);

        this.form.textFieldWrapDelimiter.setText(Preferences.getWrapDelimiter());
        this.form.textFieldWrapDelimiter.addFocusListener(focusListenerWrapComponents);
    }

    private void initAdditionalOptions() {
        Boolean isActiveFocusEditor = Preferences.getIsActiveFocusEditor();
        form.checkBoxFocusEditor.setSelected(isActiveFocusEditor);

        this.form.checkBoxFocusEditor.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Boolean isActive = form.checkBoxFocusEditor.isSelected();
                Preferences.saveIsActiveFocusEditor(isActive);
            }
        });
    }

}