import com.ronial.browser.ClientController;
import javafx.application.*;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.web.WebView;
import javafx.stage.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Calls the same controller actions as the buttons against a real HTTP server. */
public class BrowserFunctionalTest extends Application {
    static Stage window;
    static Parent root;
    static ClientController controller;
    static WebView browser;
    static TextField address;
    static String base;
    static int checks;
    static <T> T fx(Callable<T> code) throws Exception {
        FutureTask<T> task = new FutureTask<>(code); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); System.out.println("PASS " + name); checks++; }
    static void until(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!fx(condition)) { if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for page"); Thread.sleep(70); }
    }
    static String text() { return String.valueOf(browser.getEngine().executeScript("document.body ? document.body.innerText : ''")); }
    static void shot(String filename) throws Exception {
        Thread.sleep(250);
        fx(() -> {
            javafx.scene.image.WritableImage image = window.getScene().snapshot(null);
            int width = (int) image.getWidth(), height = (int) image.getHeight();
            BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for(int y = 0; y < height; y++) for(int x = 0; x < width; x++) buffer.setRGB(x, y, image.getPixelReader().getArgb(x,y));
            ImageIO.write(buffer, "png", Path.of(".qa", filename).toFile()); return null;
        });
    }
    static void navigate(String path) throws Exception {
        fx(() -> { address.setText(base + path); controller.loadPage(); return null; });
        until(() -> browser.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED && browser.getEngine().getLocation().equals(base + path));
    }
    public void start(Stage stage) throws Exception {
        base = System.getProperty("lab4.home").replaceAll("/$", "");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        root = loader.load(); controller = loader.getController();
        browser = (WebView) root.lookup("#webView"); address = (TextField)root.lookup("#textField");
        window = stage; stage.setScene(new Scene(root,1200,780)); stage.setTitle("Lab4 functional HTTP test"); stage.show();
        new Thread(() -> { int result = 0; try { runChecks(); } catch(Throwable e) { e.printStackTrace(); result = 1; }
            final int exit = result;
            Platform.runLater(() -> { for (Window w : java.util.List.copyOf(Window.getWindows())) w.hide(); Platform.exit(); });
            if (exit != 0) System.exit(exit);
        }, "browser-tests").start();
    }
    static void runChecks() throws Exception {
        until(() -> text().contains("Lab4 Web services"));
        check(fx(() -> text().contains("JSON time service")), "GUI GET renders actual localhost HTML");
        shot("lab4-get.png");
        navigate("/index.html");
        fx(() -> { controller.back(); return null; });
        until(() -> browser.getEngine().getLocation().equals(base + "/"));
        fx(() -> { controller.forward(); return null; });
        until(() -> browser.getEngine().getLocation().equals(base + "/index.html"));
        check(true, "GUI history back/forward navigation");
        fx(() -> { controller.displayHistory(); return null; });
        check(fx(() -> Window.getWindows().stream().filter(Window::isShowing).count()) >= 2, "GUI History opens actual visited entries");
        fx(() -> { for (Window w : java.util.List.copyOf(Window.getWindows())) if(w != window) w.hide(); return null; });
        fx(() -> { address.setText(base + "/api/time"); controller.headRequest(); return null; });
        until(() -> text().contains("HEAD · HTTP 200") && text().contains("Content-Length"));
        check(true, "GUI HEAD displays actual HTTP response headers"); shot("lab4-head.png");
        fx(() -> { address.setText(base + "/api/echo"); controller.postRequest(); return null; });
        until(() -> text().contains("POST · HTTP 200") && text().contains("Lab4 POST request"));
        check(true, "GUI POST sends form and renders echo response"); shot("lab4-functional.png");
        fx(() -> { address.setText(base + "/download/sample.txt"); return null; });
        CompletableFuture<Path> download = fx(() -> controller.downloadTo(Path.of(".qa", "browser-downloads")));
        Path saved = download.get(20, TimeUnit.SECONDS);
        check(Files.readString(saved).equals(Files.readString(Path.of("public", "sample.txt"))), "GUI download action saves identical bytes");
        fx(() -> { controller.zoomIn(); return null; });
        check(fx(() -> browser.getZoom()) == 1.25, "GUI zoom in");
        fx(() -> { controller.zoomOut(); return null; });
        check(fx(() -> browser.getZoom()) == 1, "GUI zoom out");
        System.out.println("Lab4 JavaFX functional checks passed: " + checks);
    }
    public static void main(String[] args) { launch(args); }
}
