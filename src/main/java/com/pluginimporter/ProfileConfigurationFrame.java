package com.pluginimporter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class ProfileConfigurationFrame extends JFrame {

    private final ProfilePlugin plugin;
    private final String sourceProfileName;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final boolean isUpdateMode;

    public ProfileConfigurationFrame(ProfilePlugin plugin, String profileName, boolean isUpdateMode) {
        this.plugin = plugin;
        this.sourceProfileName = profileName;
        this.isUpdateMode = isUpdateMode;

        setTitle(isUpdateMode ? "Update Profile: " + profileName : "Configure Profile: " + profileName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 10));
        setPreferredSize(new Dimension(800, 500));

        // --- Search Panel ---
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        JTextField searchField = new JTextField();
        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        add(searchPanel, BorderLayout.NORTH);

        // --- Table Setup ---
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                if (isUpdateMode) {
                    return column == 3; // "Apply to Profile" column
                }
                return column == 2; // "Value" column
            }
        };
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        populateTable();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        add(scrollPane, BorderLayout.CENTER);

        // --- Search Field Listener ---
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String text = searchField.getText();
                if (text.trim().length() == 0) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });

        // --- Bottom Panel (Buttons and Target Selector) ---
        add(createBottomPanel(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private void populateTable() {
        if (isUpdateMode) {
            tableModel.setColumnIdentifiers(new String[]{"Setting", "Saved Value", "Current Value", "Apply to Profile"});
            populateUpdateTable();
            // Set up the custom editor and renderer for the JComboBox column
            TableColumn applyToColumn = table.getColumnModel().getColumn(3);
            JComboBox<String> comboBox = new JComboBox<>(new Vector<>(plugin.getProfiles().keySet()));
            applyToColumn.setCellEditor(new DefaultCellEditor(comboBox));
            applyToColumn.setCellRenderer(new ComboBoxRenderer(new Vector<>(plugin.getProfiles().keySet())));

        } else {
            tableModel.setColumnIdentifiers(new String[]{"Plugin Group", "Setting", "Value"});
            populateConfigureTable();
        }
    }

    private void populateConfigureTable() {
        Profile profile = plugin.getProfiles().get(sourceProfileName);
        if (profile == null) return;
        profile.getConfig().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String[] parts = entry.getKey().split("\\.", 2);
                    if (parts.length == 2) {
                        tableModel.addRow(new Object[]{parts[0], parts[1], entry.getValue()});
                    }
                });
    }

    private void populateUpdateTable() {
        Profile defaultProfile = plugin.getProfiles().get(ProfilePlugin.DEFAULT_PROFILE_NAME);
        Map<String, String> defaultConfig = (defaultProfile != null) ? defaultProfile.getConfig() : new HashMap<>();

        Profile sourceProfile = plugin.getProfiles().get(sourceProfileName);
        Map<String, String> savedDiff = (sourceProfile != null) ? sourceProfile.getConfig() : new HashMap<>();

        Map<String, String> effectiveSavedConfig = new HashMap<>(defaultConfig);
        effectiveSavedConfig.putAll(savedDiff);

        Map<String, String> currentConfig = plugin.captureCurrentConfig();

        Set<String> allKeys = new HashSet<>(effectiveSavedConfig.keySet());
        allKeys.addAll(currentConfig.keySet());

        allKeys.stream().sorted().forEach(key -> {
            String defaultValue = defaultConfig.get(key);
            String savedValue = effectiveSavedConfig.get(key);
            String currentValue = currentConfig.get(key);

            boolean changedFromSaved = !String.valueOf(savedValue).equals(String.valueOf(currentValue));
            boolean changedFromDefault = !String.valueOf(defaultValue).equals(String.valueOf(currentValue));

            if (changedFromSaved && changedFromDefault) {
                String displaySaved = savedValue != null ? savedValue : "N/A (Default)";
                String displayCurrent = currentValue != null ? currentValue : "N/A (Default)";
                // The last object is the initially selected item for the combo box
                tableModel.addRow(new Object[]{key, displaySaved, displayCurrent, sourceProfileName});
            }
        });
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        // --- Save/Cancel Buttons ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JButton saveButton = new JButton("Save Changes");
        saveButton.addActionListener(e -> saveChanges());

        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        return bottomPanel;
    }

    private void saveChanges() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }

        if (isUpdateMode) {
            saveUpdateChanges();
        } else {
            saveConfigureChanges();
        }
        dispose();
    }

    private void saveConfigureChanges() {
        Map<String, String> newConfig = new HashMap<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String group = (String) tableModel.getValueAt(i, 0);
            String key = (String) tableModel.getValueAt(i, 1);
            String value = (String) tableModel.getValueAt(i, 2);
            newConfig.put(group + "." + key, value);
        }
        plugin.updateProfileConfig(sourceProfileName, newConfig);
    }

    private void saveUpdateChanges() {
        // Group changes by the target profile
        Map<String, Map<String, String>> changesByProfile = new HashMap<>();
        Map<String, String> currentLiveConfig = plugin.captureCurrentConfig();

        for (int i = 0; i < table.getRowCount(); i++) {
            String settingKey = (String) table.getValueAt(i, 0);
            String targetProfileName = (String) table.getValueAt(i, 3);

            changesByProfile.computeIfAbsent(targetProfileName, k -> new HashMap<>())
                    .put(settingKey, currentLiveConfig.get(settingKey));
        }

        // Apply the grouped changes to each target profile
        changesByProfile.forEach((profileName, changes) -> {
            Profile targetProfile = plugin.getProfiles().get(profileName);
            if (targetProfile != null) {
                Map<String, String> newConfig = new HashMap<>(targetProfile.getConfig());
                newConfig.putAll(changes);
                plugin.updateProfileConfig(profileName, newConfig);
            }
        });
    }

    // Custom renderer to display the JComboBox in the table cell
    private static class ComboBoxRenderer extends JComboBox<String> implements TableCellRenderer {
        public ComboBoxRenderer(Vector<String> items) {
            super(items);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setSelectedItem(value);
            return this;
        }
    }
}
