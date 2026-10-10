package be.reeks;

import java.awt.*;
import java.net.URI;
import javax.swing.*;

/** Klein venster voor de Windows-app: opent de pagina in de browser en laat je afsluiten. */
final class Gui {
    private Gui() { }

    static void show(String url) {
        if (GraphicsEnvironment.isHeadless()) return;     // bv. tests of containers: gewoon blijven draaien
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Reeksoptimalisatie");
            f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JLabel l = new JLabel("<html><body style='width:300px'><b>De reeksoptimalisatie draait.</b><br><br>"
                    + "Je browser opent vanzelf. Lukt dat niet, klik dan op \"Open in browser\" "
                    + "of ga naar <i>" + url + "</i>.<br><br>Laat dit venster open terwijl je werkt. "
                    + "Klik op \"Afsluiten\" als je klaar bent.</body></html>");
            l.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
            JButton open = new JButton("Open in browser");
            JButton quit = new JButton("Afsluiten");
            open.addActionListener(e -> browse(url));
            quit.addActionListener(e -> System.exit(0));
            f.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) { System.exit(0); }
            });
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            p.add(open); p.add(quit);
            f.getContentPane().add(l, BorderLayout.CENTER);
            f.getContentPane().add(p, BorderLayout.SOUTH);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            browse(url);
        });
    }

    private static void browse(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ignored) { }
    }
}
