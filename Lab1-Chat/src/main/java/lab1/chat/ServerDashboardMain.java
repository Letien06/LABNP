package lab1.chat;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

/** Starts the server with the Swing operations dashboard. */
public final class ServerDashboardMain {
  private ServerDashboardMain() {}
  public static void main(String[] args) throws Exception {
    int port=ChatServer.getInt(args,"--port",5555), udp=ChatServer.getInt(args,"--udp-port",5556);
    String group=ChatServer.get(args,"--multicast","239.255.42.99"), bc=ChatServer.get(args,"--broadcast","255.255.255.255");
    Path data=Path.of(ChatServer.get(args,"--data","data"));
    if (GraphicsEnvironment.isHeadless()) { ChatServer.main(args); return; }
    try {
      ChatServer server = new ChatServer(port,udp,group,bc,data);
      Runtime.getRuntime().addShutdownHook(new Thread(server::close,"chat-server-shutdown"));
      SwingUtilities.invokeLater(() -> {
        ServerDashboard dashboard = new ServerDashboard(server,data);
        dashboard.setVisible(true);
        Thread worker = new Thread(() -> {
          try { server.start(); }
          catch(Exception e) {
            server.close();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dashboard,
              "Không thể khởi động máy chủ trên cổng " + port + ".\n" + e.getMessage(),
              "Máy chủ chưa khởi động", JOptionPane.ERROR_MESSAGE));
          }
        },"lab1-server");
        worker.setDaemon(true);
        worker.start();
      });
    } catch (Exception e) {
      SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
        "Không thể khởi tạo máy chủ.\n" + e.getMessage(), "Lỗi máy chủ", JOptionPane.ERROR_MESSAGE));
    }
  }
}
