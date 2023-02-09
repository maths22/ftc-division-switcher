package com.maths22.ftc;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ScoringLogin extends JDialog {
    private final FtcScoringClient client;
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
        this.client = client;
        eventName.setText(client.getEvent());
        buttonCancel.addActionListener(e -> {
          this.dispose();
        });
        buttonOK.addActionListener(e -> {
            if(client.login(usernameInput.getText(), new String(passwordInput.getPassword()))) {
                this.dispose();
            } else {
//                TODO show error
            }
        });
    }
}
