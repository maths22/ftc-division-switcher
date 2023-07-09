package com.maths22.ftc;

import javax.swing.*;

public class ScoringLogin extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField usernameInput;
    private JPasswordField passwordInput;
    private JLabel eventName;

    public ScoringLogin(FtcScoringClient client) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        eventName.setText(client.getEvent());
        buttonCancel.addActionListener(e -> this.dispose());
        buttonOK.addActionListener(e -> {
            if(client.login(usernameInput.getText(), new String(passwordInput.getPassword()))) {
                this.dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Login failed", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
