package com.pluginimporter;

import net.runelite.client.config.Keybind;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ProfilePanel extends PluginPanel {

    private final ProfilePlugin plugin;
    private final JList<String> profileList;
    private final DefaultListModel<String> listModel;

    ProfilePanel(ProfilePlugin plugin) {
        super(false);
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        listModel = new DefaultListModel<>();
        profileList = new JList<>(listModel);
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.setCellRenderer(new ActiveProfileCellRenderer());

        profileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = profileList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String selectedProfile = listModel.getElementAt(index);
                        plugin.loadProfile(selectedProfile);
                    }
                }
            }
            @Override public void mousePressed(MouseEvent e) { handlePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { handlePopup(e); }
            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = profileList.locationToIndex(e.getPoint());
                    if (index == -1) {
                        profileList.clearSelection();
                    } else {
                        profileList.setSelectedIndex(index);
                    }
                    showPopupMenu(e);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(profileList);
        add(scrollPane, BorderLayout.CENTER);
        JLabel instructions = new JLabel("<html><center>Double-click to load.<br>Right-click for options.</center></html>", SwingConstants.CENTER);
        instructions.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        add(instructions, BorderLayout.SOUTH);
    }

    private void showPopupMenu(MouseEvent e) {
        JPopupMenu popupMenu = new JPopupMenu();
        String selectedProfile = profileList.getSelectedValue();

        JMenuItem addMenuItem = new JMenuItem("Add New Profile");
        addMenuItem.addActionListener(ae -> {
            String name = JOptionPane.showInputDialog(this, "Enter new profile name:", "Add Profile", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                plugin.saveProfile(name);
            }
        });
        popupMenu.add(addMenuItem);

        if (selectedProfile != null) {
            popupMenu.addSeparator();

            JMenuItem updateMenuItem = new JMenuItem("Update '" + selectedProfile + "'...");
            updateMenuItem.addActionListener(ae -> SwingUtilities.invokeLater(() -> new ProfileConfigurationFrame(plugin, selectedProfile, true).setVisible(true)));
            popupMenu.add(updateMenuItem);

            JMenuItem configureMenuItem = new JMenuItem("Configure '" + selectedProfile + "'...");
            configureMenuItem.addActionListener(ae -> SwingUtilities.invokeLater(() -> new ProfileConfigurationFrame(plugin, selectedProfile, false).setVisible(true)));
            popupMenu.add(configureMenuItem);

            popupMenu.addSeparator();

            JMenuItem renameMenuItem = new JMenuItem("Rename '" + selectedProfile + "'...");
            renameMenuItem.addActionListener(ae -> {
                String newName = JOptionPane.showInputDialog(this, "Enter new name for profile:", selectedProfile);
                if (newName != null && !newName.trim().isEmpty()) {
                    plugin.renameProfile(selectedProfile, newName);
                }
            });
            popupMenu.add(renameMenuItem);

            JMenuItem duplicateMenuItem = new JMenuItem("Duplicate '" + selectedProfile + "'...");
            duplicateMenuItem.addActionListener(ae -> {
                String newName = JOptionPane.showInputDialog(this, "Enter name for duplicate profile:", selectedProfile + " Copy");
                if (newName != null && !newName.trim().isEmpty()) {
                    plugin.duplicateProfile(selectedProfile, newName);
                }
            });
            popupMenu.add(duplicateMenuItem);

            popupMenu.addSeparator();

            JMenuItem setHotkeyMenuItem = new JMenuItem("Set Hotkey for '" + selectedProfile + "'");
            setHotkeyMenuItem.addActionListener(ae -> plugin.openHotkeySetter(selectedProfile));
            popupMenu.add(setHotkeyMenuItem);

            JMenuItem deleteMenuItem = new JMenuItem("Delete '" + selectedProfile + "'");
            deleteMenuItem.addActionListener(ae -> {
                int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete '" + selectedProfile + "'?", "Delete Profile", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    plugin.deleteProfile(selectedProfile);
                }
            });

            // Disable certain actions for the Default profile
            if (ProfilePlugin.DEFAULT_PROFILE_NAME.equals(selectedProfile)) {
                deleteMenuItem.setEnabled(false);
                renameMenuItem.setEnabled(false);
            }
            popupMenu.add(deleteMenuItem);
        }

        popupMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    void updateProfiles() {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            plugin.getProfiles().keySet().stream().sorted().forEach(listModel::addElement);
        });
    }

    private class ActiveProfileCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String profileName = (String) value;
            String activeProfile = plugin.getActiveProfileName();
            Profile profile = plugin.getProfiles().get(profileName);
            String hotkeyText = "";
            if (profile != null && profile.getHotkey() != null && !profile.getHotkey().equals(Keybind.NOT_SET)) {
                hotkeyText = " [" + profile.getHotkey().toString() + "]";
            }
            if (activeProfile != null && activeProfile.equals(profileName)) {
                label.setText("▶ " + profileName + hotkeyText);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            } else {
                label.setText("    " + profileName + hotkeyText);
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
            }
            return label;
        }
    }
}
