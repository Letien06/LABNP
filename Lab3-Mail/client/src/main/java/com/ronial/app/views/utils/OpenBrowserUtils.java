package com.ronial.app.views.utils;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class OpenBrowserUtils  {
    public static void openWebPage(String url) {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(new URI(url));
            }
        } catch (IOException | URISyntaxException ex) {
            ex.printStackTrace();
        }
    }
}
